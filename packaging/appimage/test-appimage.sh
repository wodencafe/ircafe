#!/usr/bin/env bash
set -euo pipefail
trap 'echo "AppImage smoke test failed at line $LINENO" >&2' ERR

# Run as root only in a disposable Ubuntu/Fedora container.
appimage=$(realpath "${1:?AppImage path is required}")
test -f "$appimage"
test "$(id -u)" = 0
if command -v apt-get >/dev/null; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update
  apt-get install -y --no-install-recommends xvfb xauth xdotool desktop-file-utils \
    fontconfig libasound2t64 libx11-6 libxext6 libxi6 libxrender1 libxtst6
else
  dnf install -y xorg-x11-server-Xvfb xorg-x11-xauth xdotool desktop-file-utils \
    shadow-utils util-linux fontconfig alsa-lib libX11 libXext libXi libXrender libXtst
fi

useradd --create-home appimage-smoke
profile=/home/appimage-smoke
mkdir -p "$profile/Downloads/Path With Spaces" "$profile/inspect" "$profile/.config/ircafe"
cp "$appimage" "$profile/Downloads/Path With Spaces/IRCafe.AppImage"
chmod +x "$profile/Downloads/Path With Spaces/IRCafe.AppImage"
cat > "$profile/.config/ircafe/ircafe.yml" <<'EOF'
irc:
  servers: []
ircafe:
  ui:
    autoConnectOnStart: false
    updateNotifier:
      enabled: false
    tray:
      enabled: false
EOF
chown -R appimage-smoke:appimage-smoke "$profile"

runuser -u appimage-smoke -- bash <<'EXTRACT'
  cd "$HOME/inspect"
  "$HOME/Downloads/Path With Spaces/IRCafe.AppImage" --appimage-extract >/dev/null
EXTRACT
app_dir="$profile/inspect/squashfs-root"
desktop-file-validate "$app_dir/IRCafe.desktop"
test -x "$app_dir/AppRun"
test -f "$app_dir/IRCafe.png"
test -f "$app_dir/usr/lib/ircafe/lib/runtime/lib/server/libjvm.so"
test ! -e "$app_dir/usr/lib/ircafe/install.sh"
test ! -e "$app_dir/usr/lib/ircafe/uninstall.sh"

# Launch the actual single-file artifact from a relocated path as a regular user.
# Containers lack /dev/fuse, so exercise the runtime's supported extract-and-run mode.
if ! runuser -u appimage-smoke -- xvfb-run -a bash > /tmp/ircafe-appimage-launch.log 2>&1 <<'LAUNCH'
  cd /tmp
  timeout --kill-after=5s 90s "$HOME/Downloads/Path With Spaces/IRCafe.AppImage" --appimage-extract-and-run &
  app_pid=$!
  trap 'kill "$app_pid" 2>/dev/null || true; wait "$app_pid" 2>/dev/null || true' EXIT
  for attempt in $(seq 1 75); do
    kill -0 "$app_pid" 2>/dev/null || exit 1
    if xdotool search --onlyvisible --name IRCafe >/dev/null 2>&1; then
      exit 0
    fi
    sleep 1
  done
  exit 1
LAUNCH
then
  cat /tmp/ircafe-appimage-launch.log >&2
  exit 1
fi
echo "AppImage extraction, metadata, and GUI launch checks passed: $appimage"
