#!/bin/bash
set -e

cmake -S third_party/llama.cpp -B build/llama -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
  -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON \
  -DGGML_NATIVE=OFF -DGGML_BACKEND_DL=ON -DGGML_CPU_ALL_VARIANTS=ON \
  -DGGML_OPENMP=OFF -DGGML_LLAMAFILE=OFF -DGGML_CPU_KLEIDIAI=ON \
  -DLLAMA_OPENSSL=OFF -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF

cmake --build build/llama --config Release -t llama-server

mkdir -p app/src/main/jniLibs/arm64-v8a/

cp build/llama/bin/llama-server app/src/main/jniLibs/arm64-v8a/libllama-server.so
cp build/llama/bin/lib*.so app/src/main/jniLibs/arm64-v8a/ 2>/dev/null || true

for f in app/src/main/jniLibs/arm64-v8a/*.so; do
    $ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip "$f"
done
