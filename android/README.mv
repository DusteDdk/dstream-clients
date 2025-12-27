# Android building
For building on android, you need to download the android sdk and tools and put them somewhere you know, then export the environment variable ANDROID_HOME to that directory.

Here's a minimum guide for setting up the build system, version 36 at the time of writing.
Make a directory for the Android SDK (in my example: /home/dusted/AndroidSDK/)

```bash
# Go home
cd ~
# Set variables
export ANDROID_HOME=`pwd`/AndroidSDK
export ANDROID_SDK_ROOT="$ANDROID_HOME"
# Create directory for SDK
mkdir "$ANDROID_HOME"

# Enter the SDK directory
cd "$ANDROID_HOME"

# Now download the "command line tools" from https://developer.android.com/studio and extract it into the AndroidSDK directory (so there's a directory called "cmdline-tools" inside AndroidSDK)

# Move it around for.. reasons
mkdir cmdline-tools/latest
mv cmdline-tools/* cmdline-tools/latest

# Accept all the licenses
./cmdline-tools/latest/bin/sdkmanager --licenses

# Install the needed packages
./cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.1.0"
# That's enough, you can now build by running ./build.sh from the android/ directory.

# These are "nice to haves" for hacking on the app:
# The sources for better code completion (I guess ?)
./cmdline-tools/latest/bin/sdkmanager "sources;android-36"
```

### Android emulation
To run the app in the emulator, also install the emulator and image (you need ANDROID_HOME and ANDROID_SDK_ROOT set as previously shown)
```bash
# Install emulator and system image (for it to be fast, you need to sudo apt install qemu-kvm and add yourself to the kvm group)
./cmdline-tools/latest/bin/sdkmanager "emulator" "system-images;android-36;google_apis;x86_64"

# Create an android virtual device (avd) (note the -n "android36" that's the name)
./cmdline-tools/latest/bin/avdmanager create avd -n android36 -k "system-images;android-36;google_apis;x86_64" -d pixel_7

# Start emulator
./emulator/emulator -avd android36 -no-snapshot -no-boot-anim -gpu auto

# Now you can push the app, open another terminal, set the ANDROID_HOME and run
 $ANDROID_HOME/platform-tools/adb install dstream-release-v5.1-github.apk
```

### Android installation
You can use the precompiled APK if you don't want to mess around with compiling it yourself, but you'll still need the platform tools installed.

Pair your phone (see https://developer.android.com/tools/adb#connect-to-a-device-over-wi-fi )

Then when adb is connected to the phone do:
```bash
$ANDROID_HOME/platform-tools/adb -s ID_NUMBER_FROM_ADB_DEVICES_HERE install dstream-release-v5.1-github.apk
```

