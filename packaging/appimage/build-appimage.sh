#!/usr/bin/env bash
set -euo pipefail

input=$(realpath "${1:?jpackage image is required}")
work_dir=$(realpath -m "${2:?build directory is required}")
output=$(realpath -m "${3:?output file is required}")
version=${4:?version is required}
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
test -x "$input/bin/IRCafe"
for dependency in curl file; do
  command -v "$dependency" >/dev/null || { echo "Install $dependency to build AppImages" >&2; exit 1; }
done

# Pin both the packager and the embedded runtime to immutable releases, not continuous assets.
# Digests are published by the upstream GitHub Releases API.
tool_version=1.9.1
runtime_version=20251108
arch=$(uname -m)
case "$arch" in
  x86_64)
    tool_sha=ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0
    runtime_sha=2fca8b443c92510f1483a883f60061ad09b46b978b2631c807cd873a47ec260d
    ;;
  aarch64)
    tool_sha=f0837e7448a0c1e4e650a93bb3e85802546e60654ef287576f46c71c126a9158
    runtime_sha=00cbdfcf917cc6c0ff6d3347d59e0ca1f7f45a6df1a428a0d6d8a78664d87444
    ;;
  *) echo "Unsupported AppImage architecture: $arch" >&2; exit 1 ;;
esac

mkdir -p "$work_dir/tools" "$(dirname "$output")"
download() {
  local url=$1 destination=$2 checksum=$3
  if [[ ! -f "$destination" ]]; then
    curl --fail --location --retry 3 "$url" --output "$destination.download"
    printf '%s  %s\n' "$checksum" "$destination.download" | sha256sum --check
    mv "$destination.download" "$destination"
  fi
  printf '%s  %s\n' "$checksum" "$destination" | sha256sum --check
}
tool="$work_dir/tools/appimagetool-${tool_version}-${arch}.AppImage"
runtime="$work_dir/tools/runtime-${runtime_version}-${arch}"
download "https://github.com/AppImage/appimagetool/releases/download/${tool_version}/appimagetool-${arch}.AppImage" "$tool" "$tool_sha"
download "https://github.com/AppImage/type2-runtime/releases/download/${runtime_version}/runtime-${arch}" "$runtime" "$runtime_sha"
chmod +x "$tool"

app_dir="$work_dir/IRCafe.AppDir"
rm -rf "$app_dir"
mkdir -p "$app_dir/usr/lib/ircafe" "$app_dir/usr/bin"
cp -a "$input/." "$app_dir/usr/lib/ircafe/"
cp "$script_dir/AppRun" "$script_dir/IRCafe.desktop" "$app_dir/"
cp "$input/lib/IRCafe.png" "$app_dir/IRCafe.png"
cp "$script_dir/../../LICENSE" "$app_dir/usr/lib/ircafe/LICENSE"
chmod +x "$app_dir/AppRun"
ln -s IRCafe.png "$app_dir/.DirIcon"
ln -s ../lib/ircafe/bin/IRCafe "$app_dir/usr/bin/IRCafe"

# Build without requiring FUSE or elevated container privileges.
APPIMAGE_EXTRACT_AND_RUN=1 ARCH="$arch" VERSION="$version" \
  "$tool" --runtime-file "$runtime" --no-appstream "$app_dir" "$output.tmp"
chmod +x "$output.tmp"
mv "$output.tmp" "$output"
echo "Built $output"
