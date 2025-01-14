---
layout: developer-doc
title: Release Policy
category: distribution
tags: [distribution, release, release-policy, policy]
order: 3
---

# Release Policy

As an open-source project and programming language, it is incredibly important
that we have a well-defined release policy. This document defines said policy.

> **Once a release has been made it is immutable. The release should only ever
> be edited to mark it as broken. Nothing else should ever be changed.**
>
> **No two release workflows can be running at once, to avoid race conditions
> since releases
> [must update files in S3](fallback-launcher-release-infrastructure.md#updating-the-release-list).
> Make sure that tags which trigger release builds are pushed sequentially, only
> pushing the next one after the previous build has finished.**

<!-- MarkdownTOC levels="2,3" autolink="true" -->

- [Versioning](#versioning)
  - [Launcher Versioning](#launcher-versioning)
- [Release Branches](#release-branches)
- [Release Workflow](#release-workflow)
  - [Breaking Release Workflow](#breaking-release-workflow)
  - [Tag Naming](#tag-naming)
  - [Manifest Files](#manifest-files)
  - [Breaking Changes to Launcher Upgrade](#breaking-changes-to-launcher-upgrade)
  - [GitHub Releases](#github-releases)
  - [Release Notes](#release-notes)
- [Version Support](#version-support)
- [Working on the Current Release](#working-on-the-current-release)
- [Backporting Fixes](#backporting-fixes)

<!-- /MarkdownTOC -->

## Versioning

Releases of Enso are versioned using [semantic versioning](https://semver.org).
Where `a.b.c-tag` is the version string, `a` is the major version, `b`, is the
minor version, `c` is the patch version, and `tag` is additional metadata, the
following hold:

- major version `a` represents the year of the release, e.g. `2020.1.1` is the
  first release of 2020.
- Breaking changes to language behaviour or the public API will result in a
  minor version increase.
- Addition of functionality in a backwards-compatible manner will result in a
  minor version increase.
- Backwards-compatible bug fixes will result in a patch version increase.
- The tag will indicate pre-release versions, and will increase when any
  pre-release change is made. These are not intended to be stable.

### Launcher Versioning

The launcher is released alongside Enso releases, so the launcher version is
tied to the Enso version that it is released with.

## Release Workflow

Enso does not use release branches, but instead uses tags to mark releases. The
same commit may be tagged multiple times, once for each release that it is a
part of.

Cutting a release for Enso proceeds as follows:

1. Ensure that the release notes are up to date and that the top header refers
   to the version that is being released.
2. Invoke the "Promote Release" workflow, either by:

   - Triggering it using
     [web interface](https://github.com/enso-org/enso/actions/workflows/promote.yml);
   - Triggering it using [GitHub CLI](https://cli.github.com/). The following
     command should be issued from the root of the repository:
     ```bash
     gh workflow run promote.yml -f designator=<designator>
     ```
     where `<designator>` is denotes what kind of release is being made. It can
     be one of:
     - `stable` - a stable release (bump to minor version);
     - `patch` - a patch release (stable release with a bump to patch version);
     - `rc` - a release candidate for the next stable release;
     - `nightly` - a nightly release.

   The `promote` workflow acts in the following steps:

   - generate a new version string for the release;
   - create a release draft on GitHub;
   - build and upload assets for the release on all platforms;
   - publish the release on GitHub.

   The final step also tags the released commit with the version string.

3. If the release was stable or patch, immediately update the
   [changelog](../CHANGELOG.md) by adding a new header for the next release, and
   marking the released one with the version generated.

### Tag Naming

Tags for releases are named as follows `version`, where `version` is the semver
string (see [versioning](#versioning)) representing the version being released.

### Manifest Files

Manifest files are used to describe metadata about various releases for use by
the Enso tooling.

#### Engine Manifest

Each GitHub release contains an asset named `manifest.yaml` which is a YAML file
containing metadata regarding the release. The manifest is also included in the
root of an Enso version package. It has at least the following fields:

- `minimum-launcher-version` - specifies the minimum version of the launcher
  that should be used with this release of Enso,
- `minimum-project-manager-version` - specifies the minimum version of the
  project manager that should be used with this release of Enso; currently it is
  the same as the launcher version but this may change in the future,
- `graal-vm-version` - specifies the exact version of GraalVM that should be
  used with this release of Enso,
- `graal-java-version` - as GraalVM versions may have different variants for
  different Java versions, this specifies which variant to use.

The minimum launcher and project manager versions are kept as separate fields,
because at some point the same runtime version management logic may be
associated with different versions of these components.

It can also contain the following additional fields:

- `jvm-options` - specifies a list of options that should be passed to the JVM
  running the engine. These options can be used to fine-tune version specific
  optimization settings etc. Each option must have a key called `value` which
  specifies what option should be passed. That value can include a variable
  `$enginePackagePath` which is substituted with the absolute path to the root
  of the engine package that is being launched. Optionally, the option may
  define `os` which will restrict this option only to the provided operating
  system. Possible `os` values are `linux`, `macos` and `windows`.
- `broken` - can be set to `true` to mark this release as broken. This field is
  never set in a release. Instead, when the launcher is installing a release
  marked as broken using the `broken` file, it adds this property to the
  manifest to preserve that information.

For example:

```yaml
minimum-launcher-version: 0.0.1
minimum-project-manager-version: 0.0.1
jvm-options:
  - value: "-Dpolyglot.engine.IterativePartialEscape=true"
  - value: "-Dtruffle.class.path.append=$enginePackagePath\\component\\runtime.jar"
    os: "windows"
  - value: "-Dtruffle.class.path.append=$enginePackagePath/component/runtime.jar"
    os: "linux"
  - value: "-Dtruffle.class.path.append=$enginePackagePath/component/runtime.jar"
    os: "macos"
graal-vm-version: 20.2.0
graal-java-version: 11
```

The `minimum-launcher-version` should be updated whenever a new version of Enso
introduces changes that require a more recent launcher version. This value is
stored in
[`distribution/manifest.template.yaml`](../../distribution/manifest.template.yaml)
and other values are added to this template at build time.

#### Launcher Manifest

Additionally, each release should contain an asset named
`launcher-manifest.yaml` which contains launcher-specific release metadata.

It contains the following fields:

- `minimum-version-for-upgrade` - specifies the minimum version of the launcher
  that is allowed to upgrade to this launcher version. If a launcher is older
  than the version specified here it must perform the upgrade in steps, first
  upgrading to an older version newer than `minimum-version-for-upgrade` and
  only then, using that version, to the target version. This logic ensures that
  if a newer launcher version required custom upgrade logic not present in older
  versions, the upgrade can still be performed by first upgrading to a newer
  version that does not require the new logic but knows about it and continuing
  the upgrade with that knowledge.
- `files-to-copy` - a list of files that should be copied into the
  distribution's data root. This may include the `README` and similar files, so
  that after the upgrade these additional files are also up-to-date. These files
  are treated as non-essential, i.e. an error when copying them will not cancel
  the upgrade (but it should be reported).
- `directories-to-copy` - a list of directories that should be copied into the
  distribution's data root. Acts similarly to `files-to-copy`.

A template manifest file, located in
[`distribution/launcher-manifest.yaml`](../../distribution/launcher-manifest.yaml),
is automatically copied to the release. If any new files or directories are
added or a breaking change to the upgrade mechanism is being made, this manifest
template must be updated accordingly.

### Breaking Changes to Launcher Upgrade

If at any point the launcher's upgrade mechanism needs an update, i.e.
additional logic must be added that was not present before, special action is
required.

First, the additional logic has to be implemented and a new launcher version
should be released which includes this additional logic, but does not require it
yet. Then, another version can be released that can depend on this new logic and
its `minimum-version-for-upgrade` has to be bumped to that previous version
which already includes new logic but does not depend on it.

This way, old launcher versions can first upgrade to a version that contains the
new logic (as it does not depend on it yet, the upgrade is possible) and using
that new version, upgrade to the target version that depends on that logic.

### GitHub Releases

A release is considered _official_ once it has been made into a release on
[GitHub](https://github.com/enso-org/enso/releases). Once official, a release
may not be changed in any way, except to mark it as broken.

#### Release Assets Structure

Each release contains a build of the Enso engine and native launcher binaries
for each supported platform. Moreover, for convenience, it should include
bundles containing native launcher binaries and the latest engine build for each
platform. So each release should contain the following assets:

- `enso-bundle-<version>-linux-amd64.tar.gz`
- `enso-bundle-<version>-macos-amd64.tar.gz`
- `enso-bundle-<version>-windows-amd64.zip`
- `enso-engine-<version>-linux-amd64.tar.gz`
- `enso-engine-<version>-macos-amd64.tar.gz`
- `enso-engine-<version>-windows-amd64.zip`
- `enso-launcher-<version>-linux-amd64.tar.gz`
- `enso-launcher-<version>-macos-amd64.tar.gz`
- `enso-launcher-<version>-windows-amd64.zip`
- `manifest.yaml`

#### Marking a Release as Broken

We intend to _never_ delete a release from GitHub, as users may have projects
that depend on specific versions of Enso. Instead, we provide a mechanism for
marking releases as broken that works as follows:

- An empty file named `broken` is uploaded to the release.
- The release description is edited to visibly mark the release as broken.

A broken release is one that _must not_ be downloaded by the launcher unless a
project specifies _an exact version match_, and it _must not_ be used in new
projects by the launcher unless _explicitly_ specified by the user as an exact
version match.

When the release is marked as broken at GitHub, a GitHub Actions
[Workflow](fallback-launcher-release-infrastructure.md#marking-the-release-as-broken)
is triggered that also updates the release in the fallback mechanism. Given its
current implementation is prone to race conditions when updating releases, the
`broken` file should be added to releases one by one, making sure that only one
update workflow is running at the same time and that no release workflows are
running in parallel with it.

In an unusual situation in which you want to upload a release that is marked as
broken from the start, you should first publish it in a non-broken state and
only mark it as broken after publishing. That is because the GitHub Workflow
that will persist the broken mark to S3 is not triggered for release drafts.

> **When marking the release as broken, you should make sure that the workflow
> persisting the broken mark to Se has succeeded and re-run it if necessary.**

## Version Support

We aim to support a given major version for some period of time after the
release of the next major version. For a detailed breakdown of the major
versions that are currently supported, please see the [security](./security.md)
document.
