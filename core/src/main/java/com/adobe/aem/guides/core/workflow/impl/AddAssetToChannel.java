package com.adobe.aem.guides.screens.demo.core.workflow.impl;

import static com.day.cq.wcm.api.NameConstants.PN_PAGE_LAST_MOD;
import static com.day.cq.wcm.api.NameConstants.PN_PAGE_LAST_MOD_BY;
import com.day.cq.dam.api.Rendition;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.AssetManager;
import com.day.cq.dam.commons.util.DamUtil;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.WorkItem;
import com.day.cq.workflow.exec.WorkflowData;
import com.day.cq.workflow.exec.WorkflowProcess;
import com.day.cq.workflow.metadata.MetaDataMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * This is a Custom Workflow process that performs the following checks:
 * 
 * 1. Decides if the item under workflow is an Asset 2. If it is an Asset:
 * Checks the Folder metadata to see if a Screens channel has been assigned 3.
 * If a Screens channel path can be found it writes the path to the Workflow
 * Metadata
 */
@Component(service = { WorkflowProcess.class }, property = { "process.label=Screens Add Asset to Channel",
        Constants.SERVICE_DESCRIPTION + "=AEM Screens Process Step to add to channel" })
public class AddAssetToChannel implements WorkflowProcess {
    private static final Logger log = LoggerFactory.getLogger(AddAssetToChannel.class);

    @Reference
    ResourceResolverFactory resourceResolverFactory;

    /***
     * WF metadata representing the Screens Channel
     */
    private static final String WF_SCREENS_CHANNEL_PATH = "screen-channel";

    /***
     * WF Metadata representing the Asset path
     */
    private static final String WF_ASSET_PATH = "asset-path";

    private static final String CHANNEL_PARSYS = "par";

    private static final String SCREENS_IMAGE_COMPONENT_RT = "screens/core/components/content/image";
    private static final String SCREENS_VIDEO_COMPONENT_RT = "screens/core/components/content/video";
    private static final String SEQUENCE_CHANNEL_RT = "screens/core/components/sequencechannel/sequence";
    private static final String SCREENS_TRANSITION_COMPONENT_RT = "screens/core/components/content/transition";

    private static final String WORKFLOW_SERVICE_USER = "workflow-service";

    private static final String PN_FILE_REFERENCE = "fileReference";
    private static final String PN_TRANSITION_DURATION = "duration";
    private static final String PN_TRANSITION_TYPE = "type";

    @Override
    public final void execute(final WorkItem workItem, final WorkflowSession workflowSession, final MetaDataMap args)
            throws WorkflowException {

        /* Get data set in prior Workflow Steps */
        final String screensChannelPath = this.getPersistedData(workItem, WF_SCREENS_CHANNEL_PATH, String.class);
        final String assetPath = this.getPersistedData(workItem, WF_ASSET_PATH, String.class);

        // If either paths are not set exit
        if (StringUtils.isBlank(screensChannelPath) || StringUtils.isBlank(assetPath)) {
            return;
        }

        ResourceResolver resourceResolver = null;
        PageManager pageManager = null;

        try {

            resourceResolver = getResourceResolver(workflowSession.getSession());
            pageManager = resourceResolver.adaptTo(PageManager.class);

            final Resource assetResource = resourceResolver.getResource(assetPath);
            final Page screensChannel = pageManager.getPage(screensChannelPath);

            log.debug("Asset Path: {}", assetPath);
            log.info("Channel path: {}", screensChannelPath);

            // Assume Workflow Item is the original rendition for the DAM Asset
            if (DamUtil.isAsset(assetResource) && screensChannel != null) {
                final Resource channelParsys = screensChannel.getContentResource(CHANNEL_PARSYS);

                if (shouldAssetBeAdded(assetPath, channelParsys)) {
                    addAssetToParsys(assetResource.adaptTo(Asset.class), channelParsys);
                    addTransitionToParsys("600", "fade", channelParsys);
                    updateChannelPageMetadata(screensChannel);
                }

            } else {
                log.debug("Could not resolve the asset or screens channel.");
                throw new WorkflowException("Could not resolve the asset or screens channel.");
            }

            if (resourceResolver.isLive() && resourceResolver.hasChanges()) {
                resourceResolver.commit();
            }

        } catch (final Exception e) {

            log.error("Unable to complete processing the Workflow Process step", e);

            throw new WorkflowException("Unable to complete processing the Workflow Process step", e);
        }
    }

    /***
     * 1. Determine if the resource representing the channel parsys is a sequence
     * channel 2. Determine if the asset (fileReference) already exists in the
     * channel
     * 
     * @param channelParsys
     * @return boolean decision if the Asset should be added to the channel
     */
    private boolean shouldAssetBeAdded(final String assetPath, final Resource channelParsys) {
        if (channelParsys != null && SEQUENCE_CHANNEL_RT.equals(channelParsys.getResourceType())) {
            for (final Resource child : channelParsys.getChildren()) {
                final ValueMap vm = child.getValueMap();
                if (assetPath.equals(vm.get(PN_FILE_REFERENCE, String.class))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * 
     * @param assetPath
     * @param channelParsys
     * @return the path to the newly created
     */
    private Resource addAssetToParsys(final Asset asset, final Resource channelParsys) throws Exception {

        final String uniqueResourceName = ResourceUtil.createUniqueChildName(channelParsys, "cover");

        // Set properties to the asset path and image resource type by default
        Map<String, Object> resourceProperties = new HashMap<String, Object>();
        resourceProperties.put(PN_FILE_REFERENCE, asset.getPath());
        resourceProperties.put(PN_PAGE_LAST_MOD, Calendar.getInstance());
        resourceProperties.put(PN_PAGE_LAST_MOD_BY, WORKFLOW_SERVICE_USER);
        resourceProperties.put("sling:resourceType", SCREENS_IMAGE_COMPONENT_RT);

        if (DamUtil.isVideo(asset)) {
            resourceProperties.put("sling:resourceType", SCREENS_VIDEO_COMPONENT_RT);
        }

        final Resource coverResource = ResourceUtil.getOrCreateResource(channelParsys.getResourceResolver(),
                channelParsys.getPath() + "/" + uniqueResourceName, resourceProperties, "nt:unstructured", false);
        return coverResource;
    }

    private Resource addTransitionToParsys(String duration, String transitionType, final Resource channelParsys)
            throws Exception {

        final String uniqueResourceName = ResourceUtil.createUniqueChildName(channelParsys, "transition");

        // Set properties to the asset path and image resource type by default
        final Map<String, Object> resourceProperties = new HashMap<String, Object>();
        resourceProperties.put(PN_TRANSITION_DURATION, duration);
        resourceProperties.put(PN_TRANSITION_TYPE, transitionType);
        resourceProperties.put(PN_PAGE_LAST_MOD, Calendar.getInstance());
        resourceProperties.put(PN_PAGE_LAST_MOD_BY, WORKFLOW_SERVICE_USER);
        resourceProperties.put("sling:resourceType", SCREENS_TRANSITION_COMPONENT_RT);

        final Resource transitionResource = ResourceUtil.getOrCreateResource(channelParsys.getResourceResolver(),
                channelParsys.getPath() + "/" + uniqueResourceName, resourceProperties, "nt:unstructured", false);

        return transitionResource;
    }

    private boolean updateChannelPageMetadata(Page channelPage) throws RepositoryException {
        Resource jcrResource = channelPage.getContentResource();
        if(jcrResource != null) {
            final Map<String, Object> jcrProperties = jcrResource.adaptTo(ModifiableValueMap.class);
            jcrProperties.put(PN_PAGE_LAST_MOD, Calendar.getInstance());
            jcrProperties.put(PN_PAGE_LAST_MOD_BY, WORKFLOW_SERVICE_USER);
            return true;
        }
        
        return false;
       
    }



    private <T> T getPersistedData(final WorkItem workItem, final String key, final Class<T> type) {
        final MetaDataMap map = workItem.getWorkflow().getWorkflowData().getMetaDataMap();
        return map.get(key, type);
    }

    private ResourceResolver getResourceResolver(final Session session) throws LoginException {
            return resourceResolverFactory.getResourceResolver(Collections.<String, Object>singletonMap(JcrResourceConstants.AUTHENTICATION_INFO_SESSION,
                    session));
    }
}