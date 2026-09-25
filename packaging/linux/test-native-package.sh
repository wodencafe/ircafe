#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Package smoke test failed at line $LINENO" >&2' ERR

# Run as root inside a disposable Ubuntu/Fedora container, never on a user's system.
# Usage: bash test-native-package.sh CURRENT_PACKAGE PREVIOUS_PACKAGE
current_package=$(realpath "${1:?current package is required}")
previous_package=$(realpath "${2:?previous package is required}")
test -f "$current_package"
test -f "$previous_package"
test "$(id -u)" = 0

case "$current_package" in
  *.deb)
    export DEBIAN_FRONTEND=noninteractive
    apt-get update
    apt-get install -y --no-install-recommends xvfb xauth xdotool desktop-file-utils
    install_package() { apt-get install -y --no-install-recommends "$1"; }
    installed_version() { dpkg-query -W -f='${Version}' ircafe; }
    package_version() { dpkg-deb -f "$1" Version; }
    remove_package() { apt-get purge -y ircafe; }
    test "$(dpkg-deb -f "$current_package" Package)" = ircafe
    ;;
  *.rpm)
    dnf install -y xorg-x11-server-Xvfb xorg-x11-xauth xdotool desktop-file-utils shadow-utils util-linux
    install_package() { dnf install -y "$1"; }
    installed_version() { rpm -q --qf '%{VERSION}-%{RELEASE}' ircafe; }
    package_version() { rpm -qp --qf '%{VERSION}-%{RELEASE}' "$1"; }
    remove_package() { dnf remove -y ircafe; }
    test "$(rpm -qp --qf '%{NAME}' "$current_package")" = ircafe
    ;;
  *) echo "Expected a DEB or RPM package" >&2; exit 1 ;;
esac

# Minimal container images lack the menu directories normally supplied by a desktop session.
mkdir -p /usr/share/desktop-directories /usr/share/applications
useradd --create-home ircafe-smoke
config=/home/ircafe-smoke/.config/ircafe/ircafe.yml
mkdir -p "$(dirname "$config")"
cat > "$config" <<'EOF'
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
chown -R ircafe-smoke:ircafe-smoke /home/ircafe-smoke
config_before=$(sha256sum "$config")

install_package "$previous_package"
test "$(installed_version)" = "$(package_version "$previous_package")"
test "$(package_version "$previous_package")" != "$(package_version "$current_package")"
install_package "$current_package"
test "$(installed_version)" = "$(package_version "$current_package")"
test "$(sha256sum "$config")" = "$config_before"

test -x /opt/ircafe/bin/IRCafe
test -f /opt/ircafe/lib/app/IRCafe.cfg
test -f /opt/ircafe/lib/runtime/lib/server/libjvm.so
test ! -e /opt/ircafe/install.sh
test ! -e /opt/ircafe/uninstall.sh
# xdg-utils prefers /usr/local/share on distributions that provide that menu directory.
desktop_entry=$(find /usr/share/applications /usr/local/share/applications \
  -iname '*ircafe*.desktop' -print -quit 2>/dev/null || true)
test -n "$desktop_entry"
desktop-file-validate "$desktop_entry"

# Exercise the installed launcher and its bundled runtime as an ordinary desktop user.
# A real visible window catches missing native dependencies that payload checks cannot.
if ! runuser -u ircafe-smoke -- xvfb-run -a bash > /tmp/ircafe-launch.log 2>&1 <<'LAUNCH'
  /opt/ircafe/bin/IRCafe &
  app_pid=$!
  trap 'kill "$app_pid" 2>/dev/null || true; wait "$app_pid" 2>/dev/null || true' EXIT
  for attempt in $(seq 1 60); do
    kill -0 "$app_pid" 2>/dev/null || exit 1
    if xdotool search --onlyvisible --name IRCafe >/dev/null 2>&1; then
      exit 0
    fi
    sleep 1
  done
  exit 1
LAUNCH
then
  cat /tmp/ircafe-launch.log >&2
  exit 1
fi

# Startup may legitimately write preferences. Removal must preserve the resulting profile.
config_before=$(sha256sum "$config")
remove_package
test ! -e /opt/ircafe/bin/IRCafe
test ! -e "$desktop_entry"
test "$(sha256sum "$config")" = "$config_before"
echo "Package install, upgrade, launcher, and removal checks passed: $current_package"
