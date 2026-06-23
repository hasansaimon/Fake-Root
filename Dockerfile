FROM ubuntu:22.04

RUN apt-get update && apt-get install -y \
    openjdk-17-jdk \
    wget \
    unzip \
    git \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /android/sdk
WORKDIR /android/sdk

RUN wget -q https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip && \
    unzip -q commandlinetools-linux-9477386_latest.zip && \
    rm commandlinetools-linux-9477386_latest.zip

ENV ANDROID_SDK_ROOT=/android/sdk
ENV ANDROID_HOME=/android/sdk
ENV PATH=${PATH}:/android/sdk/cmdline-tools/latest/bin:/android/sdk/platform-tools

RUN mkdir -p cmdline-tools/latest && \
    mv cmdline-tools/* cmdline-tools/latest/ 2>/dev/null || true

RUN yes | sdkmanager --licenses
RUN sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"

WORKDIR /app
COPY . .

RUN chmod +x gradlew

CMD ["./gradlew", "assembleRelease"]
