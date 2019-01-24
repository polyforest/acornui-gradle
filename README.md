# Acorn UI Gradle Plugins
Build multiplatform, multi-module Acorn UI projects with ease.

## Why
Building multiplatform, multi-module projects with common configuration in Gradle still requires a lot of instrumentation within Gradle.

Building a common pipeline for different build environments like CI and development also requires a fair amount of instrumentation.

And even adding a new Kotlin multiplatform module requires a smidgeon of Gradle knowledge to include in a vanilla build.

## Approach

To solve various common problems, this repo provides multiple plugins, taking the layered cake approach.

Base functionality is provided in plugins that are applied by later plugins, layering in functionality, with opinionated default configuration being at the tail end, representing a the easiest setup using convention a la:

    Base (defines task types) <-- Opinionated A (sets up task pipeline) <-- Opinionated B (etc)

In the example above, `Opinionated B` plugin applies `Opinionated A` which applies a `Base` plugin.

This allows users to select which tier of functionality they want to interface with in their downstream build by applying whichever plugin they want.

---

## Plugins
Plugins found in this repo...

*though more are TBPlanned*

### KMP Boilerplate Base Plugin
(KMP --> Kotlin Multiplatform)
Define custom presets that can configure a target and its default source set together for a single\* multiplatform Kotlin module using centrally defined properties.  The base plugin does not implicitly apply custom presets, but provides the mechanisms for defining them and applying them explicitly.

\* Future updates will provide better multi-module projects (a la multiprojects in Gradle lingo).

**id* | com.polyforest.kmp-boilerplate-base
**Applies**  | Kotlin Multiplatform Plugin

#### How To Use It
*TBD*

#### Features
*TBDeveloped*

#### Planned Features - Alpha
* **KMP Presets** - mechanism for defining presets that tie target configuration and sets of dependencies together (*see KMP Dependency Set*).
* **KMP Dependency Set** - a set of dependencies to be applied to a target.  KMP Presets have a default set of dependencies they will apply to their corresponding default source set (provided by Kotlin Multiplatform Plugin).
* **KMP Preset Extensions** - runtime safe extensions for applying presets to a specific Kotlin Multiplatform Plugin target and its default source set or apply to all Kotlin Multiplatform Plugin targets of a specific type

#### Planned Features - Beta
* **Multi-Module Support Integration** - a different base plugin that provides extensions for generally desired multi-module project functionality and sane property management for multiprojects (Gradle).
* **KMP Preset Application By Type** - apply a KMP Preset to all targets (and their corresponding default source sets)

#### Planned Features - 1.0
* **Multiple Dependency Sets** - KMP Preset can hold multiple dependency sets in addition to the default in order to be able to support easily declaring common dependencies for additional source sets for a target.
* **Sourceset Support Extensions** - extensions that allow builds to apply a defined dependency set in its KMP Preset with runtime safety.
