# Fake Root - Root Environment Emulator for Android

Make unrooted Android devices appear rooted to applications through Termux-based environment simulation.

## Features

✅ Complete fake root filesystem
✅ Native hooks via Frida
✅ Java layer interception
✅ Magisk & SuperSU spoofing
✅ System property manipulation
✅ Seamless APK patching
✅ One-click Termux integration

## Quick Start

### Prerequisites
- Android 5.0+ (API 21+)
- Termux from F-Droid (required)
- 2GB free storage

### Installation

1. **Install Fake Root APK**
   - Download from Releases or build locally

2. **Setup Environment**
   - Open app → Tap "Setup Termux Environment"
   - Runs for 10-30 minutes (one-time)

3. **Patch APK**
   - Open app → Tap "Patch APK"
   - Select target APK
   - Done! APK appears rooted

4. **Install Patched APK**
   - Install from `/sdcard/Download/`

## Building the APK

### Android Studio
```bash
clone repo
open in Android Studio
Build → Build APK(s)
```

### Gradle (CLI)
```bash
./gradlew assembleDebug       # Debug APK
./gradlew assembleRelease     # Release APK
./gradlew installDebug        # Build & Install
```

### Docker
```bash
docker build -t fakeroot .
docker run -v $(pwd):/app fakeroot ./gradlew assembleRelease
```

## Project Structure

```
Fake-Root/
├── app/
│   ├── src/main/
│   │   ├── java/com/hackerai/fakeroot/
│   │   │   └── MainActivity.java         # UI & Termux bridge
│   │   ├── res/                          # Resources
│   │   ├── assets/
│   │   │   └── setup_rootspoofer.sh     # Termux setup
│   │   └── AndroidManifest.xml
│   ├── build.gradle
│   └── proguard-rules.pro
├── build.gradle
├── settings.gradle
└── Dockerfile
```

## How It Works

### Phase 1: Environment Setup (Termux)
- Installs Java, Python, apktool, uber-apk-signer
- Downloads Frida gadgets for all architectures
- Creates fake root filesystem with su/magisk binaries
- Prepares patching infrastructure

### Phase 2: APK Patching
1. Decompiles target APK
2. Injects Frida gadget (native library)
3. Copies fake filesystem to assets
4. Patches AndroidManifest for debuggable=true
5. Recompiles & signs with debug key

### Phase 3: Runtime
- Frida gadget loads on app startup
- JavaScript hooks intercept root checks:
  - File access (access, stat, fopen)
  - Process execution (Runtime.exec, ProcessBuilder)
  - System properties
  - Package queries (hides root apps)
  - SELinux status

## Supported Checks

✅ **Bypassed:**
- su/magisk binary detection
- File existence checks
- System properties (ro.build.tags, ro.secure, etc.)
- Process execution checks
- Package manager queries
- SELinux enforcement
- Build.prop modifications

⚠️ **NOT Bypassed:**
- Hardware attestation (SafetyNet, Play Integrity)
- Kernel checks (requires real root)
- Code injection detection (complex apps)

## Usage

### Via App UI
```
Setup → Patch APK → Install
```

### Via Termux (Manual)
```bash
# One-time setup
cd ~
bash setup_rootspoofer.sh

# Patch APK
rootspoofer /sdcard/Download/target.apk

# Output
ls ~/rootspoofer/output/
```

## Timing

- **Setup:** 10-30 minutes (first time, includes downloads)
- **Patch:** 5-15 minutes per APK
- **App startup:** +2-5 seconds (Frida loading)

## Troubleshooting

### Termux setup fails
- Install Termux from F-Droid (not Play Store)
- Check internet connection
- Verify storage write permissions

### APK won't patch
- Ensure Termux setup completed
- Check APK is valid
- Try different APK version

### Patched APK crashes
- Verify Frida loaded (check logcat)
- Target app may have anti-tampering
- Try disabling ProGuard in original app

## Security Notice

⚠️ **For testing purposes only.** Misuse violates app ToS.

## License

MIT
