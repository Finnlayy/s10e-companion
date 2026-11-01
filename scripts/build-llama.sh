#!/usr/bin/env bash
# Baut llama-server für arm64-v8a und legt die Bibliotheken für die APK ab.
set -euo pipefail

ANDROID_NDK="${ANDROID_NDK:-$ANDROID_HOME/ndk/29.0.14206865}"
OUT=app/src/main/jniLibs/arm64-v8a

cmake -S third_party/llama.cpp -B build/llama -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
  -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON \
  -DGGML_NATIVE=OFF -DGGML_BACKEND_DL=ON -DGGML_CPU_ALL_VARIANTS=ON \
  -DGGML_OPENMP=OFF -DGGML_LLAMAFILE=OFF -DGGML_CPU_KLEIDIAI=ON \
  -DLLAMA_OPENSSL=OFF -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF

cmake --build build/llama --config Release -t llama-server ggml-cpu

rm -rf "$OUT"
mkdir -p "$OUT"
cp build/llama/bin/llama-server "$OUT/libllama-server.so"
find build/llama -type f -name '*.so' -exec cp -t "$OUT" {} +

# Ohne diese Dateien startet der Server auf dem Gerät nicht, der Gradle-Build merkt das aber nicht.
for lib in libllama-server.so libllama-server-impl.so libllama.so libggml.so libggml-base.so; do
  test -s "$OUT/$lib" || { echo "Fehlt: $lib" >&2; exit 1; }
done
ls "$OUT"/libggml-cpu-android_armv8.2_2.so >/dev/null

"$ANDROID_NDK"/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip "$OUT"/*.so
ls -l "$OUT"
