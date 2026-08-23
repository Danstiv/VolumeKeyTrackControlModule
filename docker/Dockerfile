# Build image for the custom sandbox build of the module.
# Nothing heavy lives here: the Android SDK, the Gradle cache and all build
# outputs sit in docker volumes mounted by build.sh.
FROM eclipse-temurin:21-jdk-noble

RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip zip curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# The noble base already ships an `ubuntu` user on uid 1000; we need that uid for
# the bind-mounted repository, so replace it with our own.
RUN userdel -r ubuntu 2>/dev/null || true \
    && groupadd -g 1000 builder \
    && useradd -m -u 1000 -g 1000 -s /bin/bash builder

# Named volumes inherit ownership from these directories when first created.
RUN mkdir -p /opt/android-sdk /home/builder/.gradle /out \
    && chown -R builder:builder /opt/android-sdk /home/builder /out

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/home/builder/.gradle \
    PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:$PATH

USER builder
WORKDIR /workspace
