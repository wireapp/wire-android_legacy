#!/bin/bash
# build native libs for osx - used in tests
cd "$(dirname "$0")"
java_home="${JAVA_HOME-`/usr/libexec/java_home -v 1.8`}"
g++ -Wall -pedantic -Wno-variadic-macros -I $java_home/include/ -I $java_home/include/darwin/ -dynamiclib -o ../../../target/android/intermediates/ndk/jni/osx/liblzw-decoder.dylib LzwDecoder.cpp || echo "OSX build failed"
gcc -Wall -O2 -pedantic -Wno-variadic-macros -arch x86_64 $(pwd)/osx/libsodium.dylib -I $java_home/include/ -I $java_home/include/darwin/ -I $(pwd)/ -dynamiclib -o ../../../target/android/intermediates/ndk/jni/osx/librandombytes.dylib randombytes.c || echo "OSX build failed"
cp osx/libsodium.dylib ../../../target/android/intermediates/ndk/jni/osx/