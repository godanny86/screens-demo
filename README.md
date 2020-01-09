# Sample AEM project template

This is a project template for AEM-based applications. It is intended as a best-practice set of examples as well as a potential starting point to develop your own functionality.

## Modules

The main parts of the template are:

* core: Java bundle containing all core functionality like OSGi services, listeners or schedulers, as well as component-related Java code such as servlets or request filters.
* ui.apps: contains the /apps (and /etc) parts of the project, ie JS&CSS clientlibs, components, templates, runmode specific configs as well as Hobbes-tests
* ui.content: contains sample content using the components from the ui.apps
* ui.tests: Java bundle containing JUnit tests that are executed server-side. This bundle is not to be deployed onto production.
* ui.launcher: contains glue code that deploys the ui.tests bundle (and dependent bundles) to the server and triggers the remote JUnit execution

## How to build

To build all the modules run in the project root directory the following command with Maven 3:

    mvn clean install

If you have a running AEM instance you can build and package the whole project and deploy into AEM with

    mvn clean install -PautoInstallSinglePackage

Or to deploy it to a publish instance, run

    mvn clean install -PautoInstallSinglePackagePublish

Or alternatively

    mvn clean install -PautoInstallPackage -Daem.port=4503

Or to deploy only the bundle to the author, run

    mvn clean install -PautoInstallBundle

## Screens - Automatic Watermark Asset Processing Workflow

### Prerequisites

1. AEM 6.5 + SP1 (at least)
2. Screens 6.5 FP 1.4+

### Installation

1. Upload and install `screens-demo.all-1.0-SNAPSHOT.zip` via CRX Package manager.

### Workflow

There are two workflows that are used:

* **DAM Update Asset**: http://localhost:4502/editor.html/conf/global/settings/workflow/models/dam/update_asset.html
* **Screens Asset Processing**: http://localhost:4502/editor.html/conf/global/settings/workflow/models/screens-demo-asset-processing.html

1. The OOTB **DAM Update Asset** workflow is modified to perform a call to the **Screens Asset Processing** workflow as a sub-workflow. This first step could also be achieved using a workflow launcher configuration.
2. The **Screens Asset Processing** workflow first checks to see if the folder the asset was uploaded in has a screens channel specified in the folder metadata. If it does:
    1. Apply a watermark to the original image
    2. Add a reference and transition of the asset to the Screens Channel specified by the folder.

### Demo Folders

The folder: http://localhost:4502/assets.html/content/dam/screens-demo is set up to add images to the screens channel /content/screens/we-retail/channels/idle

To modify the Screens channel you can update the **Folder Metadata Properties** > **Screens** > **Channel to Assign** at: http://localhost:4502/mnt/overlay/dam/gui/content/assets/v2/foldersharewizard.html/content/dam/screens-demo


