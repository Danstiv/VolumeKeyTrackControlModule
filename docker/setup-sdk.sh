#!/bin/sh
# Idempotent Android SDK bootstrap. Runs inside the build container and writes
# only into the SDK volume, so re-running it is cheap and safe.
set -eu

SDK="${ANDROID_HOME:-/opt/android-sdk}"
CMDLINE_TOOLS_VERSION=16111833
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
    echo ">> installing cmdline-tools ${CMDLINE_TOOLS_VERSION}"
    curl -fsSL -o /tmp/cmdline-tools.zip "$CMDLINE_TOOLS_URL"
    rm -rf /tmp/cmdline-tools-unpacked
    unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-unpacked
    mkdir -p "$SDK/cmdline-tools"
    rm -rf "$SDK/cmdline-tools/latest"
    mv /tmp/cmdline-tools-unpacked/cmdline-tools "$SDK/cmdline-tools/latest"
    rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-tools-unpacked
fi

SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"

echo ">> accepting licenses"
yes | "$SDKMANAGER" --licenses > /dev/null

echo ">> installing SDK packages"
"$SDKMANAGER" "$@"

echo ">> SDK ready"
