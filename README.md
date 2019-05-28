# Acorn UI Gradle Plugins
Build multiplatform, multi-module Acorn UI projects with ease.

This project is a part of a series of projects around Acorn UI and ease of use commonly referred to in Acorn UI documentation as _Acorn UI Build Support_, _Acorn UI Build_, or various shorter forms (_Acorn UI BS_, or _aUI-BS_).  These projects are aimed to help do things like setup new projects, build them, or use asset pipeline tools.

# Why

### Gradle is the Only Way  
_Gradles is the only way to build Kotlin multiplatform libraries that take advantage of language level features at this time._

### Reduces Boilerplate
_Official Gradle plugins for Kotlin multiplatform projects and other standard plugins still require verbose boilerplate for most needs._

### Missing out-of-the-box multi-module support  
_Building multiplatform, multi-module projects with common configuration in Gradle still requires a lot of instrumentation within Gradle and can be done in brittle ways even though it's a common paradigm to have multiple modules in a single project._

### Basic CI + Dev environment support  
_Building a common pipeline for different build environments like CI and development also requires a fair amount of instrumentation._

### Adding modules to Kotlin projects is buggy  
_There are different avenues to adding new modules in Intellij IDEA and none of them seem to reliably cover the common case 100% of the time.  Using our plugins minimizes the cognitive load needed to navigate unstable feature minefields or the requirement to understand all things Gradle to navigate simple tasks._

# Approach

The plugins are currently shipped as pre-compiled script plugins.  This is allowing us to take a series of scripts written in the DSL and migrate them over time to a fully non-DSL plugin development environment.

This means that plugins take on a layered approach, where some plugins apply other plugins.

# Current Usage Limitations

Snapshots and release plugin artifact(s) are not yet published anywhere and so users of it must build their own plugin. <!-- TODO | Generate direct or linkable documentation for this --> 

# Plugins

Given the layered approach of plugins, it's useful to be able to understand which plugins apply which plugins at a glance.

The documentation below uses nested bullet points to indicate which plugins directly apply which plugins where a top-level node is a given plugin and it's one line summary, plus its direct children which indicate the plugins it applies.

> Note, the documentation below only covers plugins that these plugins directly apply, not all dependencies.

<!-- TODO | After publishing to plugins.gradle.com, link each top level node to it's plugins.gradle.com entry -->
<!-- TODO | Make summaries read as broad intentions and use it as display label for a url to source documentation.  Provide guidance on this or good tooltips? -->

#### Plugins Acorn UI Uses

- **com.polyforest.acornui.root** - provides non-opinionated configuration for all subprojects/modules  
\
_(i.e. Making sure all modules/subprojects use the same Gradle version)_

- **com.polyforest.acornui.basic** - provides slightly opinionated basic configuration and defaults for a single Kotlin MPP module  
\
_(i.e. Will create targets that Acorn UI supports and apply standard required dependencies)_

#### Plugins Acorn UI App Projects Use

- **com.polyforest.acornui.app-root** - provides app-oriented opinionated configuration
  - _applies_ **acornui.root**

- **com.polyforest.acornui.app-basic** - provides app-oriented opionated configuration for run of the mill modules  
\
_(i.e. makes it so that all Acorn UI module dependencies use the same version as every other module)_

  - _applies_ **acornui.app**
  
- **com.polyforest.acornui.app** - provides app-entrypoint-oriented configuration and pipeline for delivering supported targets  
\
_(i.e. an app entrypoint module; typically named `app` by convention.  Responsible for setting up JS code post-processing like minification)_

  - _applies_ **acornui.app-basic**
  - _applies_ **kotlin-dce-js**

- **com.polyforest.acornui.builder** - provides configuration for a special module that allows access to some legacy build features*
  - _applies_ **acornui.app-basic**

\* will be deprecated in an upcoming iteration of Acorn UI Build Support
