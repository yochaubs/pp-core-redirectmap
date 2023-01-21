# Global AEM Configuration

This project contains all global configurations and overlayed files.

## Modules

The main parts of the template are:

* core: This should contain global Java Classes used either by all projects or by the system.
* ui.apps: contains the /apps part of the project, includes configuration files etc.

## Configuration files contained

This project should contain all global configuration files:
- Config for Adobe Services 
- Overlays (of dialogs, error handling etc.)
- Service users
- Rewriter Config (versioned clientlibs, etc.)

## How to build

To build all the modules run in the project root directory the following command with Maven 3:

    mvn clean install

## Maven settings

The project comes with the auto-public repository configured. To setup the repository in your Maven settings, refer to:

    http://helpx.adobe.com/experience-manager/kb/SetUpTheAdobeMavenRepository.html
