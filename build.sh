#!/usr/bin/env bash
# Builds the custom APK inside a docker container.
#
#   ./build.sh                 -> assembleRelease, APK copied into dist/
#   ./build.sh assembleDebug   -> any gradle task(s) you like
#   ./build.sh --shell         -> interactive shell in the build container
#
# Everything heavy (Android SDK, Gradle cache, build output) lives in docker
# volumes. The only build artefacts that touch the repository are the signing
# key in .signing/ and the finished APKs in dist/.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

IMAGE=vktc-builder:1
VOL_SDK=vktc-android-sdk
VOL_GRADLE=vktc-gradle-home
VOL_OUT=vktc-build-out

SDK_PACKAGES=("platform-tools" "platforms;android-37.0" "build-tools;37.0.0")

log() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }

# --- image ------------------------------------------------------------------
if ! docker image inspect "$IMAGE" > /dev/null 2>&1; then
    log "building image $IMAGE"
    docker build -t "$IMAGE" "$ROOT/docker"
fi

# --- volumes ----------------------------------------------------------------
for vol in "$VOL_SDK" "$VOL_GRADLE" "$VOL_OUT"; do
    if ! docker volume inspect "$vol" > /dev/null 2>&1; then
        log "creating volume $vol"
        docker volume create "$vol" > /dev/null
    fi
done

# The sandbox reaches the network directly; the host proxy variables point at an
# address the container cannot resolve, so they are explicitly cleared.
docker_run() {
    docker run --rm -i \
        ${TTY_FLAG:-} \
        -v "$ROOT:/workspace" \
        -v "$VOL_SDK:/opt/android-sdk" \
        -v "$VOL_GRADLE:/home/builder/.gradle" \
        -v "$VOL_OUT:/out" \
        -e HTTP_PROXY= -e HTTPS_PROXY= -e http_proxy= -e https_proxy= \
        -e JAVA_TOOL_OPTIONS= \
        -w /workspace \
        "$IMAGE" "$@"
}

# --- sdk --------------------------------------------------------------------
if ! docker_run test -d /opt/android-sdk/platforms; then
    log "bootstrapping Android SDK (one-off, a few hundred MB)"
    docker_run sh docker/setup-sdk.sh "${SDK_PACKAGES[@]}"
fi

# --- signing key ------------------------------------------------------------
if [[ ! -f "$ROOT/.signing/keystore.jks" ]]; then
    log "generating signing key in .signing/ (keep this directory, it is your identity)"
    mkdir -p "$ROOT/.signing"
    docker_run keytool -genkeypair \
        -keystore /workspace/.signing/keystore.jks \
        -storepass volumekey -keypass volumekey \
        -alias custom -keyalg RSA -keysize 4096 -validity 10000 \
        -dname "CN=VolumeKeyTrackControl Custom, OU=Sandbox, O=Local, C=US"
    cat > "$ROOT/.signing/keystore.properties" <<'EOF'
storeFile=keystore.jks
storePassword=volumekey
keyAlias=custom
keyPassword=volumekey
EOF
fi

# --- interactive shell ------------------------------------------------------
if [[ "${1:-}" == "--shell" ]]; then
    TTY_FLAG=-t docker_run bash
    exit 0
fi

# --- build ------------------------------------------------------------------
TASKS=("$@")
[[ ${#TASKS[@]} -eq 0 ]] && TASKS=(assembleRelease)

log "gradle ${TASKS[*]}"
docker_run ./gradlew \
    --project-cache-dir=/out/project-cache \
    --init-script=docker/build-dirs.init.gradle.kts \
    "${TASKS[@]}"

# --- collect apks -----------------------------------------------------------
mkdir -p "$ROOT/dist"
if docker_run sh -c 'ls /out/build/app/outputs/apk/*/*.apk > /dev/null 2>&1'; then
    docker_run sh -c 'cp /out/build/app/outputs/apk/*/*.apk /workspace/dist/'
    log "APK(s) in dist/:"
    ls -la "$ROOT/dist"
fi
