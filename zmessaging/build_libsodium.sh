#!/bin/bash
set -e

if [ "$1" != -f ]; then
  missing=0
  for arch in armeabi-v7a arm64-v8a x86 x86_64; do
    if [ ! -e src/main/jni/$arch/libsodium.so ]; then
      (( ++missing ))
    fi
  done
  if (( missing == 0 )); then
    echo 'no .so files missing, skipping libsodium build; use -f to force'
    exit 0
  fi
fi

vsn=1.0.18
sha512sum=17e8638e46d8f6f7d024fe5559eccf2b8baf23e143fadd472a7d29d228b186d86686a5e6920385fe2020729119a5f12f989c3a782afbd05a8db4819bb18666ef
url=https://github.com/jedisct1/libsodium/releases/download/$vsn-RELEASE/libsodium-$vsn.tar.gz

# download
wget -O libsodium-$vsn.tar.gz -- "$url"
printf '%s  libsodium-%s.tar.gz\n' "$sha512sum" "$vsn" | sha512sum -c

# extract
tar xzf libsodium-$vsn.tar.gz

# build
cd libsodium-$vsn
for arch in armv7-a armv8-a x86 x86_64; do
  ./dist-build/android-$arch.sh
  rm -fr android-toolchain-$arch
done
cd ..

# move
mkdir -p src/main/jni/{arm64-v8a,armeabi-v7a,x86,x86_64}
mv libsodium-$vsn/libsodium-android-armv7-a/lib/libsodium.so  src/main/jni/armeabi-v7a/
mv libsodium-$vsn/libsodium-android-armv8-a/lib/libsodium.so  src/main/jni/arm64-v8a/
mv libsodium-$vsn/libsodium-android-i686/lib/libsodium.so     src/main/jni/x86/
mv libsodium-$vsn/libsodium-android-westmere/lib/libsodium.so src/main/jni/x86_64/

# cleanup
rm -fr libsodium-$vsn.tar.gz libsodium-$vsn
