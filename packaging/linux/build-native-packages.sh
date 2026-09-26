#!/usr/bin/env bash
set -euo pipefail

# Build an older package revision for upgrade testing, then the distributable revision.
# Invoked from the repository root by the Linux release/manual workflows.
version_args=()
if [[ -n "${1:-}" ]]; then
  version_args+=("-Pversion=$1")
fi
./gradlew --no-daemon jpackageLinux "${version_args[@]}" -PlinuxPackageRelease=0 -x test
mkdir -p build/installer-test
cp build/installer/deb/*.deb build/installer-test/previous.deb
cp build/installer/rpm/*.rpm build/installer-test/previous.rpm
./gradlew --no-daemon jpackageLinux "${version_args[@]}" -PlinuxPackageRelease=1 -x test
