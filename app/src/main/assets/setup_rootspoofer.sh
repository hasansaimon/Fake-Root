#!/bin/bash
# Fake Root - Termux Setup - Complete Root Environment Emulator
# Run this ONCE in Termux to set up everything

set -e

echo "========================================"
echo "  Fake Root - Termux Setup"
echo "  Complete Root Environment Emulator"
echo "========================================"
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Step 1: Update and install dependencies
echo -e "${BLUE}[1/8] Installing dependencies...${NC}"
pkg update -y
pkg upgrade -y
pkg install -y \
    openjdk-17 \
    wget \
    curl \
    git \
    zip \
    unzip \
    python \
    python-pip \
    android-tools \
    nano \
    termux-api \
    proot \
    tar \
    xz-utils \
    openssl-tool

echo -e "${GREEN}[✓] Dependencies installed${NC}"

# Step 2: Install Frida
echo -e "${BLUE}[2/8] Installing Frida...${NC}"
pip install frida-tools -q
echo -e "${GREEN}[✓] Frida installed${NC}"

# Step 3: Download apktool
echo -e "${BLUE}[3/8] Downloading apktool...${NC}"
wget -q https://github.com/iBotPeaches/Apktool/releases/download/v2.9.3/apktool_2.9.3.jar -O $PREFIX/bin/apktool.jar
echo '#!/data/data/com.termux/files/usr/bin/bash
java -jar $PREFIX/bin/apktool.jar "$@"' > $PREFIX/bin/apktool
chmod +x $PREFIX/bin/apktool
echo -e "${GREEN}[✓] Apktool installed${NC}"

# Step 4: Download uber-apk-signer
echo -e "${BLUE}[4/8] Downloading APK signer...${NC}"
wget -q https://github.com/patrickfav/uber-apk-signer/releases/download/v1.3.0/uber-apk-signer-1.3.0.jar -O $PREFIX/bin/uber-apk-signer.jar
echo '#!/data/data/com.termux/files/usr/bin/bash
java -jar $PREFIX/bin/uber-apk-signer.jar "$@"' > $PREFIX/bin/uber-apk-signer
chmod +x $PREFIX/bin/uber-apk-signer
echo -e "${GREEN}[✓] Uber-apk-signer installed${NC}"

# Step 5: Download Frida Gadgets
echo -e "${BLUE}[5/8] Downloading Frida gadgets...${NC}"
FRIDA_VERSION="16.2.1"
mkdir -p $HOME/rootspoofer/gadgets

for arch in arm64 arm x86_64 x86; do
    echo "  Downloading frida-gadget-$arch..."
    wget -q "https://github.com/frida/frida/releases/download/${FRIDA_VERSION}/frida-gadget-${FRIDA_VERSION}-android-${arch}.so.xz" \
        -O "$HOME/rootspoofer/gadgets/frida-gadget-${arch}.so.xz" 2>/dev/null || echo "    [!] $arch not available"
    [ -f "$HOME/rootspoofer/gadgets/frida-gadget-${arch}.so.xz" ] && \
        xz -d "$HOME/rootspoofer/gadgets/frida-gadget-${arch}.so.xz" 2>/dev/null
done
echo -e "${GREEN}[✓] Gadgets downloaded${NC}"

# Step 6: Build fake root filesystem
echo -e "${BLUE}[6/8] Building fake root filesystem...${NC}"
mkdir -p $HOME/rootspoofer/rootfs/system/bin
mkdir -p $HOME/rootspoofer/rootfs/system/xbin
mkdir -p $HOME/rootspoofer/rootfs/sbin
mkdir -p $HOME/rootspoofer/rootfs/data/adb/magisk
mkdir -p $HOME/rootspoofer/rootfs/selinux

# Create su binary
echo '#!/system/bin/sh
echo "uid=0(root) gid=0(root) groups=0(root)"
if [ "$1" = "-c" ]; then
    shift
    eval "$@"
fi
exit 0' > $HOME/rootspoofer/rootfs/system/xbin/su
chmod 6755 $HOME/rootspoofer/rootfs/system/xbin/su

# Create magisk binary stub
echo '#!/system/bin/sh
echo "Magisk 28.0"
exit 0' > $HOME/rootspoofer/rootfs/system/bin/magisk
chmod 755 $HOME/rootspoofer/rootfs/system/bin/magisk

# Create magiskpolicy
echo '#!/system/bin/sh
exit 0' > $HOME/rootspoofer/rootfs/system/bin/magiskpolicy
chmod 755 $HOME/rootspoofer/rootfs/system/bin/magiskpolicy

# Build properties
cat > $HOME/rootspoofer/rootfs/system/build.prop << 'EOF'
ro.build.tags=test-keys
ro.debuggable=1
ro.secure=0
ro.adb.secure=0
persist.sys.root_access=3
ro.build.selinux=0
ro.build.type=userdebug
EOF

# SELinux status
echo '0' > $HOME/rootspoofer/rootfs/selinux/enforce
echo 'Permissive' > $HOME/rootspoofer/rootfs/selinux/policy

echo -e "${GREEN}[✓] Filesystem created${NC}"

# Step 7: Create patcher script
echo -e "${BLUE}[7/8] Creating patcher script...${NC}"

cat > $HOME/rootspoofer/rootspoofer.sh << 'MAINSCRIPT'
#!/data/data/com.termux/files/usr/bin/bash
# Fake Root - APK Patcher

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

TARGET_APK="$1"
if [ -z "$TARGET_APK" ]; then
    echo -e "${RED}Usage: rootspoofer <target.apk>${NC}"
    exit 1
fi

WORK_DIR="$HOME/rootspoofer/work_$$"
OUTPUT_DIR="$HOME/rootspoofer/output"
GADGETS="$HOME/rootspoofer/gadgets"
ROOTFS="$HOME/rootspoofer/rootfs"

mkdir -p "$WORK_DIR" "$OUTPUT_DIR"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Patching: $(basename $TARGET_APK)${NC}"
echo -e "${GREEN}========================================${NC}\n"

echo -e "${BLUE}[1/7] Decompiling APK...${NC}"
apktool d -f -o "$WORK_DIR/decompiled" "$TARGET_APK" 2>/dev/null

echo -e "${BLUE}[2/7] Detecting architecture...${NC}"
if [ -d "$WORK_DIR/decompiled/lib/arm64-v8a" ]; then
    ARCH="arm64"
elif [ -d "$WORK_DIR/decompiled/lib/armeabi-v7a" ]; then
    ARCH="arm"
else
    ARCH="arm64"
fi
echo "  Architecture: $ARCH"

echo -e "${BLUE}[3/7] Injecting Frida gadget...${NC}"
if [ -f "$GADGETS/frida-gadget-${ARCH}.so" ]; then
    for libdir in "$WORK_DIR/decompiled/lib"/*/; do
        cp "$GADGETS/frida-gadget-${ARCH}.so" "${libdir}libfrida-gadget.so"
        echo "  Injected into: $(basename $libdir)"
    done
else
    echo "  Warning: No gadget for $ARCH"
fi

echo -e "${BLUE}[4/7] Copying root filesystem...${NC}"
mkdir -p "$WORK_DIR/decompiled/assets"
cp -r "$ROOTFS" "$WORK_DIR/decompiled/assets/"

echo -e "${BLUE}[5/7] Setting debuggable...${NC}"
sed -i 's|android:debuggable="false"|android:debuggable="true"|g' "$WORK_DIR/decompiled/AndroidManifest.xml"
if ! grep -q "android:debuggable" "$WORK_DIR/decompiled/AndroidManifest.xml"; then
    sed -i 's|<application |<application android:debuggable="true" |g' "$WORK_DIR/decompiled/AndroidManifest.xml"
fi

echo -e "${BLUE}[6/7] Recompiling APK...${NC}"
UNSIGNED="$WORK_DIR/unsigned.apk"
apktool b -o "$UNSIGNED" "$WORK_DIR/decompiled" 2>/dev/null

echo -e "${BLUE}[7/7] Signing APK...${NC}"
SIGNED="$OUTPUT_DIR/$(basename "$TARGET_APK" .apk)_patched.apk"
uber-apk-signer --apks "$UNSIGNED" --out "$SIGNED" 2>/dev/null

echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}SUCCESS!${NC}"
echo -e "${GREEN}Patched APK: $SIGNED${NC}"
echo -e "${GREEN}========================================${NC}\n"

echo "Install with: adb install $SIGNED"
echo "Or copy to device manually"

rm -rf "$WORK_DIR"
MAINSCRIPT

chmod +x $HOME/rootspoofer/rootspoofer.sh

# Create launcher
echo '#!/data/data/com.termux/files/usr/bin/bash
cd $HOME/rootspoofer
bash rootspoofer.sh "$@"' > $PREFIX/bin/rootspoofer
chmod +x $PREFIX/bin/rootspoofer

echo -e "${GREEN}[✓] Patcher created${NC}"

# Step 8: Verification
echo -e "${BLUE}[8/8] Verifying setup...${NC}"

echo "  Checking apktool..."
apktool 2>&1 | head -1 || echo "  [!] apktool not found"

echo "  Checking uber-apk-signer..."
uber-apk-signer --help 2>&1 | head -1 || echo "  [!] signer not found"

echo "  Checking Frida..."
frida --version || echo "  [!] frida not found"

echo -e "${GREEN}[✓] Verification complete${NC}"

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Setup Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${YELLOW}Usage:${NC}"
echo "  rootspoofer /path/to/target.apk"
echo ""
echo -e "${YELLOW}Patched APKs:${NC}"
echo "  $HOME/rootspoofer/output/"
echo ""
echo -e "${GREEN}Ready to patch!${NC}"
