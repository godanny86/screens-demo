package com.adobe.aem.guides.screens.demo.core.workflow.impl;

import com.day.cq.dam.api.Rendition;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.AssetManager;
import com.day.cq.dam.commons.util.DamUtil;
import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.WorkItem;
import com.day.cq.workflow.exec.WorkflowData;
import com.day.cq.workflow.exec.WorkflowProcess;
import com.day.cq.workflow.metadata.MetaDataMap;
import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.jcr.Session;
import java.util.Arrays;
import java.util.Collections;

/**
 * This is a Custom Workflow process that performs the following checks:
 * 
 * 1. Decides if the item under workflow is an Asset
 * 2. If it is an Asset: Checks the Folder metadata to see if a Screens channel has been assigned
 * 3. If a Screens channel path can be found it writes the path to the Workflow Metadata
 */
@Component(
        service = {WorkflowProcess.class},
        property = {
                "process.label=Screens Asset Processing Check",
                Constants.SERVICE_DESCRIPTION + "=AEM Screens Check to determine if the asset should be processed and added to a screens channel"
        }
)
public class AssetProcessingCheck implements WorkflowProcess {
    private static final Logger log = LoggerFactory.getLogger(AssetProcessingCheck.class);

    @Reference
    ResourceResolverFactory resourceResolverFactory;

    /***
     * Property to inspect on an asset's folder metadata
     */
    private static final String PN_SCREENS_CHANNEL_PATH = "screen-channel";

    /***
     * WF Metadata representing the Asset path
     */
    private static final String WF_ASSET_PATH = "asset-path";

    @Override
    public final void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap args) throws
            WorkflowException {

        // Get the Workflow data (the data that is being passed through for this work item)

        final WorkflowData workflowData = workItem.getWorkflowData();
        final String type = workflowData.getPayloadType();

        // Check if the payload is a path in the JCR; The other (less common) type is JCR_UUID
        if (!StringUtils.equals(type, "JCR_PATH")) {
            return;
        }
        // Get the path to the JCR resource from the payload
        final String path = workflowData.getPayload().toString();

        /* Get data set in prior Workflow Steps */
        String previouslySetData = this.getPersistedData(workItem, "set-in-previous-workflow-step", String.class);

        ResourceResolver resourceResolver = null;
        try {

            resourceResolver = getResourceResolver(workflowSession.getSession());

            Resource resource = resourceResolver.getResource(path);
           
            //Assume Workflow Item is the original rendition for the DAM Asset
            if(DamUtil.isRendition(resource)) {
                Asset wfAsset = resource.adaptTo(Rendition.class).getAsset();
                String assetPath = wfAsset.getPath();

                // Get the screens channel path from the folder (asset's parent)
                Resource assetResource = resourceResolver.getResource(assetPath);
                String screensChannelPath = getChannelPath(assetResource.getParent());

                if(StringUtils.isNotBlank(screensChannelPath)) {
                    
                    // Save data for use in a subsequent Workflow step
                    log.info("Screens Channel: {}", screensChannelPath);
                    persistData(workItem, workflowSession, PN_SCREENS_CHANNEL_PATH, screensChannelPath.trim());
                    
                    log.info("Asset Path: {}", assetPath);
                    persistData(workItem, workflowSession, WF_ASSET_PATH, assetPath);
                } else {
                    log.debug("Could not find a screens channel set on folder metadata");
                }
            } else {
                log.debug("Workflow item not expected Asset original rendition ", path);
            }

        } catch (Exception e) {
            // If an error occurs that prevents the Workflow from completing/continuing - Throw a WorkflowException
            // and the WF engine will retry the Workflow later (based on the AEM Workflow Engine configuration).

            log.error("Unable to complete processing the Workflow Process step", e);

            throw new WorkflowException("Unable to complete processing the Workflow Process step", e);
        }
    }

    /***
     * Based on a Folder resource returns the metadata path to a screens channel. Null if not found.
     * @param folderResource
     * @return Path to a screens channel
     */
    private String getChannelPath(Resource folderResource) {
        if(folderResource != null) {
            Resource metadataResource = folderResource.getChild("jcr:content/metadata");
            if(metadataResource != null) {
                ValueMap vm = metadataResource.adaptTo(ValueMap.class);
                return vm.get(PN_SCREENS_CHANNEL_PATH, String.class);
            }
        }
        return null;
    } 
    /**
     * Helper methods.
     */

    private <T> boolean persistData(WorkItem workItem, WorkflowSession workflowSession, String key, T val) {
        WorkflowData data = workItem.getWorkflow().getWorkflowData();
        if (data.getMetaDataMap() == null) {
            return false;
        }

        data.getMetaDataMap().put(key, val);
        workflowSession.updateWorkflowData(workItem.getWorkflow(), data);

        return true;
    }

    private <T> T getPersistedData(WorkItem workItem, String key, Class<T> type) {
        MetaDataMap map = workItem.getWorkflow().getWorkflowData().getMetaDataMap();
        return map.get(key, type);
    }

    private <T> T getPersistedData(WorkItem workItem, String key, T defaultValue) {
        MetaDataMap map = workItem.getWorkflow().getWorkflowData().getMetaDataMap();
        return map.get(key, defaultValue);
    }

    private ResourceResolver getResourceResolver(Session session) throws LoginException {
            return resourceResolverFactory.getResourceResolver(Collections.<String, Object>singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION,
                    session));
    }
}