#!/bin/bash

# export ANDROID_HOME=/home/dusted/Android/Sdk/
export ANDROID_HOME=/home/dusted/AndroidSDK
export BUILD_TYPE=release
#export BUILD_TYPE=debug



if [ "$BUILD_TYPE" == "release" ]
then
    export GRADLEW_TYPE="assembleRelease"
elif [ "$BUILD_TYPE" == "debug" ]
then
    export GRADLEW_TYPE="assembleRelease"
else
    echo "No BUILD_TYPE set, should be release or debug"
    exit 1
fi

export APK_DIR="app/build/outputs/apk/$BUILD_TYPE/"

if [ -z "ANDROID_HOME" ]
then
    echo "Missing environment variable: ANDROID_HOME"
    echo "You can modify $0 to fix this.. or export it yourself before running it.."
    exit 1
fi

cd "./dstreamandroid"
pwd

echo "Running ./gradlew tasks"
./gradlew tasks
S="$?"
if [ "$S" != "0" ]
then
    echo "./gradlew tasks failed with code $S"
    exit "$S"
fi

if [ ! -z "$CLEAN" ]
then
    echo "Running ./gradlew clean"
    ./gradlew clean
    S="$?"
    if [ "$S" != "0" ]
    then
        echo "./gradlew clean failed with code $S"
    fi
    exit "$S"
fi

echo "Running ./gradlew $GRADLEW_TYPE"
./gradlew --stacktrace $GRADLEW_TYPE 
S="$?"
if [ "$S" != "0" ]
then
    echo "./gradlew $GRADLEW_TYPE failed with code $S"
    exit "$S"
else
    if cp "app/build/outputs/apk/$BUILD_TYPE/"*.apk ..
    then
        cd "$APK_DIR"
        echo "Successfully built $BUILD_TYPE apk: `ls *.apk`"
    else
        echo "Build succeeded, but couldn't copy the apk to `pwd`..."
        echo "Here are the APKs I could find:"
        find -iname '*.apk'
    fi
fi
