# llama.cpp integration and model choice for a native Android app on the Galaxy S10e (SM-G970F, Exynos 9820, 6 GB, Android 12)

State as of 2026-11-01. Repo files, release metadata and Hugging Face hashes were read directly on that date. No code was run on the phone: `adb` is not installed on this host and no device was attached, so every statement about this specific S10e is unmeasured.

## Decisions at a glance (planner summary)

| Topic | Decision | Confidence |
|---|---|---|
| Repo | `github.com/ggml-org/llama.cpp` (the `ggerganov` URL 301-redirects there) | High |
| Pin | Prebuilt `b12032` (arm64 tarball, SHA256 below), or source tag `v0.8.0` | High that they exist; not device-tested |
| Build | `GGML_BACKEND_DL=ON` + `GGML_CPU_ALL_VARIANTS=ON` + `GGML_NATIVE=OFF`, `ANDROID_PLATFORM=android-28`. No hand-set `-march`, no i8mm | High |
| Do not copy | `examples/llama.android` unchanged (`minSdk = 33` will not install on Android 12); the CI flag `armv8.5-a+fp16+i8mm`; the `-snapdragon` tarball | High |
| Runtime | `llama-server` as a child of a Foreground Service if tool calling or an OpenAI API is needed; own JNI in a separate `:llm` process if plain chat is enough | Medium (my judgement) |
| Primary model | Qwen3.5-2B Q4_K_M (1.28 GB, Apache-2.0) | Medium, pending a German test and a tool-calling test |
| Fast fallback | Qwen3.5-0.8B Q4_K_M (0.53 GB, Apache-2.0) | Medium |
| Tool-calling fallback | Hermes-3-Llama-3.2-3B Q4_K_M (2.02 GB), the only candidate confirmed as a native tool format | Medium |
| Stretch test only | Gemma 4 E2B: its GGUF is 2.8-3.1 GB, not the 1.3 GB quoted in blogs | Medium |
| Model delivery | `adb push` first, because the phone is offline. Three routes below; test route A first | Medium |

## How should llama.cpp be built for Android, and which CPU flags fit the Exynos 9820?

### Takeaway
Use the upstream portable path: `GGML_CPU_ALL_VARIANTS` plus dynamic backends, which picks a kernel library at runtime from HWCAP bits. The expected pick on the Exynos 9820 is `android_armv8.2_2` (dotprod + fp16, no i8mm). The official Android example has `minSdk = 33` and must be patched for Android 12 (API 31).

### Cited Findings
- **Repo location:** `github.com/ggerganov/llama.cpp` returns HTTP 301 to `github.com/ggml-org/llama.cpp` (checked with `curl -I` on 2026-11-01) — [redirect target](https://github.com/ggml-org/llama.cpp).
- **Tags:** newest build tag is `b12032` (2026-11-01 09:00 UTC, commit `dde879bc4f954b809892fd0d660515f42536394a`) — [releases](https://github.com/ggml-org/llama.cpp/releases). The repo also has semantic tags: `v0.8.0` was published 2026-10-28 and is the GitHub "latest" release — [v0.8.0](https://github.com/ggml-org/llama.cpp/releases/tag/v0.8.0). `b12032` is 72 commits ahead of `v0.8.0` (GitHub compare API).
- **`v0.8.0` has no Android binary:** its only asset is `nightly-tag.txt` — [v0.8.0](https://github.com/ggml-org/llama.cpp/releases/tag/v0.8.0). Android tarballs are attached to `bNNNN` releases — [releases](https://github.com/ggml-org/llama.cpp/releases).
- **Prebuilt for pinning:** `llama-b12032-bin-android-arm64.tar.gz`, 79,002,991 bytes, sha256 `25393a12f1adce8fb44fa313453ea1c23786a81c2b41815c809056ac58bfe802`. GitHub's digest matched a local `sha256sum` — [b12032](https://github.com/ggml-org/llama.cpp/releases/tag/b12032).
- **How that tarball is built:** NDK `29.0.14206865`, `ANDROID_PLATFORM=android-28`, `GGML_BACKEND_DL=ON`, `GGML_NATIVE=OFF`, `GGML_CPU_ALL_VARIANTS=ON`, `GGML_OPENMP=OFF`, `LLAMA_BUILD_BORINGSSL=ON` — [release.yml](https://github.com/ggml-org/llama.cpp/blob/master/.github/workflows/release.yml).
- **Official cross-compile command** uses `ANDROID_PLATFORM=android-28`, `GGML_NATIVE=OFF`, `GGML_OPENMP=OFF`, `GGML_LLAMAFILE=OFF`, `LLAMA_OPENSSL=OFF`, and warns against a global `-march`. It names no NDK version — [docs/android.md](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md).
- **KleidiAI:** on arm64-v8a pass `-DGGML_CPU_KLEIDIAI=ON`. Older CPUs "fall back automatically to compatible kernels" — [docs/build.md](https://github.com/ggml-org/llama.cpp/blob/master/docs/build.md).
- **Official example `examples/llama.android`** (`lib/build.gradle.kts`): `ndkVersion 29.0.13113456`, `compileSdk 36`, `minSdk 33`, ABIs `arm64-v8a` and `x86_64`, CMake 3.31.6, `BUILD_SHARED_LIBS=ON`, `LLAMA_BUILD_APP=OFF`, `LLAMA_BUILD_COMMON=ON`, `LLAMA_OPENSSL=OFF`, `GGML_NATIVE=OFF`, `GGML_BACKEND_DL=ON`, `GGML_CPU_ALL_VARIANTS=ON`, `GGML_LLAMAFILE=OFF` — [build.gradle.kts](https://github.com/ggml-org/llama.cpp/blob/master/examples/llama.android/lib/build.gradle.kts). The `app` module also has `minSdk = 33`, `targetSdk = 36` — [app/build.gradle.kts](https://github.com/ggml-org/llama.cpp/blob/master/examples/llama.android/app/build.gradle.kts).
- **OpenMP conflict:** the example's CMake sets `GGML_OPENMP` ON and `GGML_CPU_KLEIDIAI` ON for arm64 — [lib CMakeLists](https://github.com/ggml-org/llama.cpp/blob/master/examples/llama.android/lib/src/main/cpp/CMakeLists.txt) — while docs and release CI use OpenMP OFF — [docs/android.md](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md).
- **Example runtime defaults:** threads `max(2, min(4, online_cpus - 2))`, `DEFAULT_CONTEXT_SIZE = 8192`, `BATCH_SIZE = 512`, default model params, and `ggml_backend_load_all_from_path(nativeLibraryDir)` — [ai_chat.cpp](https://github.com/ggml-org/llama.cpp/blob/master/examples/llama.android/lib/src/main/cpp/ai_chat.cpp).
- **Variant list:** `android_armv8.0_1` (none), `android_armv8.2_1` (DOTPROD), `android_armv8.2_2` (DOTPROD, FP16_VECTOR_ARITHMETIC), `android_armv8.6_1` (adds MATMUL_INT8), `android_armv9.0_1` (adds SVE2), `android_armv9.2_1`, `android_armv9.2_2` — [ggml/src/CMakeLists.txt](https://github.com/ggml-org/llama.cpp/blob/master/ggml/src/CMakeLists.txt). `GGML_CPU_ALL_VARIANTS` needs `GGML_BACKEND_DL`, which needs `BUILD_SHARED_LIBS`; it cannot be combined with `GGML_CPU_ARM_ARCH` — same file.
- **Runtime detection on Linux/Android:** `getauxval(AT_HWCAP)` / `AT_HWCAP2`. dotprod = `HWCAP_ASIMDDP`; fp16 needs both `HWCAP_FPHP` and `HWCAP_ASIMDHP` — [ggml-feats.h](https://github.com/ggml-org/llama.cpp/blob/master/ggml/src/ggml-feats.h). Any compiled-in feature missing at runtime makes a variant score 0, otherwise the score rises with each feature — [cpu-feats.cpp](https://github.com/ggml-org/llama.cpp/blob/master/ggml/src/ggml-cpu/arch/arm/cpu-feats.cpp).
- **Exynos 9820 ISA:** M4 "finally introduces ARMv8.2 support" including FP16 scalar instructions and the integer dot product extension; Cortex-A75 and A55 also support dotprod — [WikiChip Fuse](https://fuse.wikichip.org/?p=2051). Layout is 2x M4 @ 2.73 GHz, 2x A75, 4x A55 — [SamMobile](https://www.sammobile.com/samsung/exynos-9820/).
- **Kernel view:** feature registers expose "system wide safe" values, so a feature missing on one CPU is reported absent; MIDR_EL1 is the per-CPU exception and is racy on heterogeneous systems — [kernel docs](https://docs.kernel.org/arch/arm64/cpu-feature-registers.html).
- **Crash reports:** I found no llama.cpp issue reporting a SIGILL or mis-detection on Exynos. A GitHub issue search for "exynos" returned 13 hits, none of them this bug. The nearest are Termux `-mcpu=native+...+noi8mm` build failures — [#11428](https://github.com/ggml-org/llama.cpp/issues/11428) — and a third-party report of i8mm probe failure with the workaround `-march=armv8.2-a+fp16+dotprod` — [COG-GTM #49](https://github.com/COG-GTM/llama.cpp/issues/49).
- **Known historic big.LITTLE bug (different class, 2016):** Mono crashed because big and little cores had different I-cache line sizes (128 vs 64 bytes) — [Mono blog](https://www.mono-project.com/news/2016/09/12/arm64-icache/). That affected JIT cache flushing, not feature detection.
- **Trap in CI:** the `ndk` job in `build-android.yml` uses `-DGGML_CPU_ARM_ARCH=armv8.5-a+fp16+i8mm` with `ANDROID_PLATFORM=android-31` — [build-android.yml](https://github.com/ggml-org/llama.cpp/blob/master/.github/workflows/build-android.yml).

### Inferences
- **Expected variant:** `libggml-cpu-android_armv8.2_2.so`, score 7 (1 + dotprod 2 + fp16 4). The i8mm and SVE variants score 0 and are skipped, assuming the Samsung kernel exposes `asimddp`, `fphp` and `asimdhp`.
- **Mis-detection risk is low.** Upstream uses kernel-sanitised HWCAPs rather than per-core register reads, so a big.LITTLE mismatch should not produce an illegal instruction. If the kernel hid a feature, ggml falls back to a lower variant instead of crashing. Hand-set arch flags are the real SIGILL risk.
- **Never copy the CI `armv8.5-a+fp16+i8mm` flag.** The Exynos 9820 is ARMv8.2, so that build would likely SIGILL.
- **Avoid the `-snapdragon` tarball:** it targets Adreno and Hexagon, and this phone is an Exynos with a Mali GPU.
- **Recommended own build** (merged from docs and CI, not test-built here):

```bash
cmake -S . -B build-android -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
  -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON \
  -DGGML_NATIVE=OFF -DGGML_BACKEND_DL=ON -DGGML_CPU_ALL_VARIANTS=ON \
  -DGGML_OPENMP=OFF -DGGML_LLAMAFILE=OFF -DGGML_CPU_KLEIDIAI=ON \
  -DLLAMA_OPENSSL=OFF -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF
```

- **NDK:** use `29.0.14206865` (matches release CI).
- **Patching the example:** set `minSdk` to 31 in `lib` and `app`, and lower `DEFAULT_CONTEXT_SIZE`, which is also used as a hard-coded limit in the overflow checks. A grep of `InferenceEngineImpl.kt` found no SDK-version guard, and the native code builds at android-28, so 33 looks like a project default rather than a hard need. Confirm this by building.
- **Threads:** the example's formula gives 4. On a 2+2+4 layout, use the four fast cores and leave the A55s out. llama-server offers `-C/--cpu-mask`, `-Cr/--cpu-range`, `--cpu-strict` — [server README](https://github.com/ggml-org/llama.cpp/blob/master/tools/server/README.md). Benchmark `-t 2`, `3`, `4`, since mismatched cores may pace each other.

### Gaps
- **`/proc/cpuinfo` on the 9820 was not found in any source.** Run `adb shell cat /proc/cpuinfo` and check for `asimddp asimdhp fphp`, and absence of `i8mm sve`. Confirm via the llama.cpp load log which variant was chosen.
- **Core numbering is unverified.** Read `/sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_max_freq` before setting a mask. Prior knowledge suggests cpu0-3 A55, cpu4-5 A75, cpu6-7 M4.
- **Whether a Foreground Service may use the big cores** under Android's cpusets is unverified, so test it.
- **Whether a newer stable NDK than r29 exists** was not checked. r29 is what upstream CI pins.
- **Whether v0.8.0 maps to a specific `bNNNN`** is unknown, so a prebuilt exactly matching v0.8.0 could not be named.
- **KleidiAI benefit on dotprod-only CPUs** has no published number.

## JNI binding or llama-server on 127.0.0.1?

### Takeaway
Android 12 can exec a bundled binary if it is packaged as `lib*.so` and extracted at install time. The server provides OpenAI-compatible chat and tool calling today. Choose `llama-server` under a Foreground Service if tool calling matters, or JNI in a dedicated `:llm` process if plain chat is enough. Note the default port is now **9931**, not 8080, so pass `--port` explicitly.

### Cited Findings
- **W^X:** apps targeting API 29+ "cannot invoke `execve()` directly on files within the app's home directory"; apps "should load only the binary code that's embedded within an app's APK file" — [Android 10 behavior changes](https://developer.android.com/about/versions/10/behavior-changes-10).
- **jniLibs route:** put the binary in `jniLibs/<abi>/lib___.so`, have it extracted at install, run it from `applicationInfo.nativeLibraryDir` — [Stack Overflow mirror, 2020](https://kiwix.lema.org/stackoverflow.com_en_all_2023-11/questions/63946068/how-do-i-embed-a-native-code-library-in-an-android-app-sdk-29). A 2025 write-up reports the same route working on Android 16, while exec from private storage is denied — [u1f383](https://u1f383.github.io/android/2025/06/15/run-native-binary-on-android.html). This is community evidence, not an official Android statement.
- **Packaging:** since AGP 4.2 `useLegacyPackaging` replaces `extractNativeLibs`; with `false`, libraries stay uncompressed in the APK and load directly from it; the default depends on `minSdkVersion` and AGP — [application element](https://developer.android.com/guide/topics/manifest/application-element).
- **Tarball contents (`b12032`, inspected, not executed):** 44 entries, 290 MiB unpacked. `llama-server` is a 7 KB launcher that NEEDs `libllama-server-impl.so` (68 MB), `libllama-common.so` (89 MB), `libmtmd.so`, `libllama.so`, `libggml.so`, `libggml-base.so`, and has no RPATH/RUNPATH. `libggml-base.so` still contains `.debug_info` and `.symtab`. Seven `libggml-cpu-android_*.so` variants ship alongside. — [b12032](https://github.com/ggml-org/llama.cpp/releases/tag/b12032)
- **Library path:** docs set `LD_LIBRARY_PATH=lib` because Android does not find the lib path by itself — [docs/android.md](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md).
- **Server API:** `/v1/chat/completions`, `/v1/completions`, `/v1/models`; `--host` default `127.0.0.1`, **`--port` default `9931`** (older docs and blog posts use 8080); `--jinja` default enabled; `--api-key`, `--api-key-file`; `--no-webui`; `GET /health` is 503 while loading and 200 when ready — [server README](https://github.com/ggml-org/llama.cpp/blob/master/tools/server/README.md).
- **Flags that changed or have bad phone defaults:** `--no-mmap`/`--mlock` are replaced by `-lm/--load-mode` (`auto`, `none`, `mmap`, `mlock`, `mmap+mlock`, `dio`). `-c` default 0 means "from model"; `-np` default auto; `-cram` default 8192 MiB; `-ctxcp` default 32; `--sleep-idle-seconds` default disabled — [server README](https://github.com/ggml-org/llama.cpp/blob/master/tools/server/README.md).
- **Tool calling:** OpenAI-style function calling works with `--jinja`, and "may require a `--chat-template-file` override" — [server README](https://github.com/ggml-org/llama.cpp/blob/master/tools/server/README.md). Native formats listed include "Hermes 2/3, Qwen 2.5"; unrecognised templates fall back to Generic, which "may consume more tokens" — [function-calling.md](https://github.com/ggml-org/llama.cpp/blob/master/docs/function-calling.md).
- **Android 12 phantom processes:** any process forked from the app counts; the limit is 32 **system-wide**; children of Foreground Service apps are not exempt but are killed after those of less important apps; CPU checks run every 5 minutes and kill a child using too much CPU "while its parent app process are also in the background". On Android 12, unlike 12L+, there is no switch to disable excessive-CPU killing — [agnostic-apollo docs](https://github.com/agnostic-apollo/Android-Docs/blob/master/en/docs/apps/processes/phantom-cached-and-empty-processes.md) (community write-up). Samsung forum users report the 12L toggle was not carried into One UI — [Samsung community](https://eu.community.samsung.com/t5/galaxy-s20-series/s20-fe-android-12-phantom-background-process-problem/td-p/4582212).
- **Official JNI binding:** Kotlin `AiChat`/`InferenceEngine` facade, `GgufMetadataReader`, tokens as a `Flow` — [docs/android.md](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md). Its described feature set does not include tool calling.

### Inferences
- **Android 12 exec works** for a `targetSdk` of 29+ on this route. It requires `packaging { jniLibs { useLegacyPackaging = true } }`, otherwise there may be no file on disk to exec.
- **Comparison (my judgement; no source compares them directly):**

| Criterion | llama-server child of Foreground Service | JNI in `:llm` process |
|---|---|---|
| Tool calling, templates, streaming | Included | Must be written on top of `llama-common`, or by hand |
| Crash or OOM isolation | Yes, UI survives | Yes, if in a separate process |
| Phantom-process exposure | Yes, but parent stays non-background | None |
| Exec dependency | Yes | None |
| APK size | About 290 MiB unstripped | Smaller, only needed libs |
| Local security | Needs loopback plus API key | No socket |

- **Recommendation:** server under a Foreground Service for v1 if tool calling is a requirement, since parsing is the hard part. Otherwise JNI in `:llm`.
- **Launch sketch (flag values are starting points to tune):**

```text
LD_LIBRARY_PATH=$NATIVE $NATIVE/libllama-server.so -m $MODEL \
  --host 127.0.0.1 --port 8080 --api-key-file $KEYFILE --no-webui \
  -c 4096 -np 1 -t 4 -tb 4 -cram 256 -ctxcp 2 --no-mmproj --jinja
```

- **Packaging steps:** rename `llama-server` to `libllama-server.so`, copy the `lib*.so` files it needs plus the `libggml-cpu-android_*.so` variants to `jniLibs/arm64-v8a/`, strip them, and drop unneeded tool libraries. The backend search should find the variants next to the executable, but confirm this in the log.
- **Lifecycle:** poll `/health`, and on process exit restart with a capped backoff. Kill any orphan server left on the port at app start.
- **Loopback is reachable by other local apps**, so a random per-install API key is worthwhile.
- **Vulkan:** use CPU only (no `-ngl`). The Mali-G76 path is unproven: a recent Android report saw Vulkan crash on a much newer phone — [r/LocalLLaMA snapshot](https://reddit.sentinel-team.org/posts/1t0k6fj/snapshots/2026-05-02T03%3A06%3A21.60641Z).

### Gaps
- **No primary source confirms the jniLibs exec route on an Android 12 Samsung build.** Test first: it is a 30-minute spike.
- **Whether `libllama-server-impl.so` can be called in-process** (embedded server without exec) was not established.
- **Cleartext HTTP to 127.0.0.1:** from prior knowledge the app needs `INTERNET` and a network security config allowing cleartext for `127.0.0.1` on modern `targetSdk`. Not verified here.
- **Which `foregroundServiceType` fits** at the chosen `targetSdk` was not researched. On Android 12 itself no type is enforced as far as I recall, but a `targetSdk` of 34+ changes this.
- **Whether a child inherits the parent's LMK priority** was not established.
- **Meaning of `--sleep-idle-seconds`:** the name suggests it frees the model when idle, which would help here. Confirm before relying on it.
- **Qwen3.5, Gemma 4, LFM2.5 tool-call parsing:** `function-calling.md` does not list these in what I read. Test with a real tool schema and check the `Chat format:` log line.

## Which GGUF models are realistic on 6 GB RAM?

### Takeaway
Target models of 2 GB or less. Use Qwen3.5-2B Q4_K_M as primary, Qwen3.5-0.8B as the fast fallback, and Hermes-3 3B when reliable tool calling outweighs size. Gemma 4 E2B GGUF is about twice the size commonly quoted and is a stretch test only. No head-to-head German evidence exists for these models, so run a short German test before freezing the choice.

### Cited Findings

**File facts** are from the Hugging Face tree API on 2026-11-01. `lfs.oid` is the file's SHA256. Pin URLs with the repo revision: `https://huggingface.co/<repo>/resolve/<rev>/<file>`. Licences are from the base model's card metadata.

| Model / file | Bytes | SHA256 | Repo @ revision | Licence |
|---|---|---|---|---|
| `Qwen3.5-0.8B-Q4_K_M.gguf` | 532,517,120 | `bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517` | [unsloth/Qwen3.5-0.8B-GGUF](https://huggingface.co/api/models/unsloth/Qwen3.5-0.8B-GGUF/tree/main) @ `6ab461498e2023f6e3c1baea90a8f0fe38ab64d0` | Apache-2.0 |
| `LFM2.5-1.2B-Instruct-Q4_K_M.gguf` | 730,895,168 | `b1b3de114215d9507409a662a501a631095a479a419584e8a2ded6304b19b4f5` | [LiquidAI/LFM2.5-1.2B-Instruct-GGUF](https://huggingface.co/api/models/LiquidAI/LFM2.5-1.2B-Instruct-GGUF/tree/main) @ `8ed288026e23958ad9dfa92d53ed773a8eee7125` | `lfm1.0` (custom) |
| `google_gemma-3-1b-it-Q4_K_M.gguf` | 806,058,496 | `12bf0fff8815d5f73a3c9b586bd8fee8e7b248c935de70dec367679873d0f29d` | [bartowski/google_gemma-3-1b-it-GGUF](https://huggingface.co/api/models/bartowski/google_gemma-3-1b-it-GGUF/tree/main) @ `116f76234503685a98f572982177b11d44ec8ff1` | Gemma terms (base repo manually gated) |
| `DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf` | 1,117,320,800 | `1741e5b2d062b07acf048bf0d2c514dadf2a48f94e2b4aa0cfe069af3838ee2f` | [bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF](https://huggingface.co/api/models/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/tree/main) | MIT |
| **`Qwen3.5-2B-Q4_K_M.gguf`** | 1,280,835,840 | `aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223` | [unsloth/Qwen3.5-2B-GGUF](https://huggingface.co/api/models/unsloth/Qwen3.5-2B-GGUF/tree/main) @ `f6d5376be1edb4d416d56da11e5397a961aca8ae` | Apache-2.0 |
| `SmolLM3-3B-Q4_K_M.gguf` | 1,915,306,528 | `4de907d2d388a5508fb7cb443a06effe14cce3518b0a78d3bdd9e74d9edce989` | [unsloth/SmolLM3-3B-GGUF](https://huggingface.co/api/models/unsloth/SmolLM3-3B-GGUF/tree/main) | Apache-2.0 |
| `Hermes-3-Llama-3.2-3B-Q4_K_M.gguf` | 2,019,374,176 | `2e220a14ba4328fee38cf36c2c068261560f999fadb5725ce5c6d977cb5126b5` | [bartowski/Hermes-3-Llama-3.2-3B-GGUF](https://huggingface.co/api/models/bartowski/Hermes-3-Llama-3.2-3B-GGUF/tree/main) @ `aa0b91e1dd59f2fb0d9bf8cda7e167b86b8655a2` | `llama3` tag |
| `granite-4.0-micro-Q4_K_M.gguf` | 2,099,502,848 | `3ed0a9f397bab63eb93231449086ce5dc3f309598d007825fc01cfd32c4b93e1` | [unsloth/granite-4.0-micro-GGUF](https://huggingface.co/api/models/unsloth/granite-4.0-micro-GGUF/tree/main) | Apache-2.0 |
| `Phi-4-mini-instruct-Q4_K_M.gguf` | 2,491,874,272 | `88c00229914083cd112853aab84ed51b87bdf6b9ce42f532d8c85c7c63b1730a` | [unsloth/Phi-4-mini-instruct-GGUF](https://huggingface.co/api/models/unsloth/Phi-4-mini-instruct-GGUF/tree/main) | MIT |
| `gemma-4-E2B-it-Q4_0.gguf` | 2,841,481,184 | `8e30dff3ac4c8434c49a7036fa15564bdbb6044e42bf04550bf1a096ad7e6a52` | [ggml-org/gemma-4-E2B-it-GGUF](https://huggingface.co/api/models/ggml-org/gemma-4-E2B-it-GGUF/tree/main) | Apache-2.0 |
| `gemma-4-E2B-it-Q4_K_M.gguf` | 3,106,738,272 | `740185b21d22ceb83a11c3aa62ad5842ef32c70f6096d756bbee85a1e4ec34b8` | [unsloth/gemma-4-E2B-it-GGUF](https://huggingface.co/api/models/unsloth/gemma-4-E2B-it-GGUF/tree/main) @ `0314792d7f1f7e229411f620751375812bb9faf2` | Apache-2.0 |
| `gemma-3n-E2B-it-Q4_K_M.gguf` | 3,026,881,888 | `189d42b4303cb1078ea8d00963f437cd6d884069b7ba2ba80b38cd09585dc415` | [unsloth/gemma-3n-E2B-it-GGUF](https://huggingface.co/api/models/unsloth/gemma-3n-E2B-it-GGUF/tree/main) | Not retrieved |

- **Hashes differ between uploaders.** `bartowski/Qwen_Qwen3.5-2B-GGUF` Q4_K_M is 1,396,198,496 bytes with a different hash — [tree](https://huggingface.co/api/models/bartowski/Qwen_Qwen3.5-2B-GGUF/tree/main). Pin one repo.
- **Licences:** [Gemma 4 E2B](https://huggingface.co/api/models/google/gemma-4-E2B-it), [Qwen3.5-2B](https://huggingface.co/api/models/Qwen/Qwen3.5-2B), [Qwen3.5-0.8B](https://huggingface.co/api/models/Qwen/Qwen3.5-0.8B), [SmolLM3](https://huggingface.co/api/models/HuggingFaceTB/SmolLM3-3B) and [Granite micro](https://huggingface.co/api/models/ibm-granite/granite-4.0-micro) are tagged apache-2.0. [DeepSeek distill](https://huggingface.co/api/models/deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B) and [Phi-4-mini](https://huggingface.co/api/models/microsoft/Phi-4-mini-instruct) are MIT. [Hermes-3 3B](https://huggingface.co/api/models/NousResearch/Hermes-3-Llama-3.2-3B) is tagged `llama3`. [LFM2.5](https://huggingface.co/api/models/LiquidAI/LFM2.5-1.2B-Instruct) is `lfm1.0`.
- **Contradiction on Gemma 4 E2B size:** a blog claims about 1.3 GB — [MindStudio](https://www.mindstudio.ai/blog/gemma-4-edge-deployment-e2b-e4b-models); the actual GGUF files are 2.84-3.11 GB (table), and the official LiteRT file is 2.58 GB — [Google](https://developers.google.com/edge/litert-lm/models/gemma-4). Trust the file listings.
- **The closest speed datapoint is one anecdote:** Qwen3.5-0.8B at 12 tok/s on a Galaxy S10E in Termux with llama.cpp, quantization not stated. A commenter calls it Snapdragon 855, i.e. likely not the Exynos variant — [r/LocalLLaMA snapshot](https://reddit.sentinel-team.org/posts/1rj5ngc/snapshots/2026-03-04T15%3A10%3A50.627029Z).
- **Other speed anchors:**
  - Gemma 4 E2B Q4 at 5-6 tok/s on a Poco M6 Pro with `llama-server` in Termux, with other containers running (SoC and RAM not stated) — [XDA](https://www.xda-developers.com/i-turned-my-old-phone-into-a-local-llm-server/).
  - Gemma 4 E2B on Raspberry Pi 5: 8 decode tok/s, 7.8 s TTFT, 1546 MB peak on LiteRT-LM — [Google](https://developers.google.com/edge/litert-lm/models/gemma-4).
  - Qwen3.5-4B at 11-12 tok/s on a OnePlus 13 with llama.cpp, from memory — [r/LocalLLaMA snapshot](https://reddit.sentinel-team.org/posts/1t0k6fj/snapshots/2026-05-02T03%3A06%3A21.60641Z).
- **Vendor-blog speed claims conflict:** E2B at 20-35 tok/s on 8 Gen 3 — [MindStudio](https://www.mindstudio.ai/blog/gemma-4-edge-deployment-e2b-e4b-models) — versus 10-25 on Pixel 9 Pro — [MindStudio](https://www.mindstudio.ai/blog/gemma-4-e2b-e4b-edge-models-phone-local). Neither shows measurements, so I discount both.
- **German:**
  - In the MÖVE German public-sector benchmark, older tiny models stayed in German poorly: a 1.7B SmolLM about 40%, Gemma 2 2B 42.1%, EuroLLM 1.7B 45.8%. Language adherence correlated strongly with summary quality — [arXiv 2606.13111](https://arxiv.org/pdf/2606.13111). These are older generations.
  - Model cards listing `de`: [LFM2.5-1.2B](https://huggingface.co/api/models/LiquidAI/LFM2.5-1.2B-Instruct) and [Phi-4-mini](https://huggingface.co/api/models/microsoft/Phi-4-mini-instruct). [SmolLM3](https://huggingface.co/api/models/HuggingFaceTB/SmolLM3-3B) lists en, fr, es, it, pt, zh, ar, ru, **not** de. [Hermes-3](https://huggingface.co/api/models/NousResearch/Hermes-3-Llama-3.2-3B) lists only en.
  - Gemma 4 evidence on German is anecdotal and about much larger variants — [Reddit snapshot](https://reddit.sentinel-team.org/posts/1seo2rq/snapshots/2026-04-09T16%3A11%3A00.655487Z).

### Inferences
- **Speed estimates for the Exynos 9820 (my extrapolation, not measurements; 4 threads, cool device):**

| Model | Estimated decode tok/s | Basis |
|---|---|---|
| Qwen3.5-0.8B Q4_K_M | 8-12 | S10E anecdote, assuming Exynos is no faster |
| LFM2.5-1.2B Q4_K_M | 6-10 | Scaled by file size |
| Qwen3.5-2B Q4_K_M | 4-7 | Scaled by file size |
| Hermes-3 3B Q4_K_M | 3-5 | Scaled by file size |
| Gemma 4 E2B Q4 | 2-5 | Poco M6 Pro and Pi 5 anchors, reduced |

- **Candidate ranking:**
  1. **Qwen3.5-2B:** best balance of size, licence and recency.
  2. **Qwen3.5-0.8B:** reliable speed fallback.
  3. **LFM2.5-1.2B:** worth a German test because `de` is declared, but read `lfm1.0` first if the app could ever be commercial.
  4. **Hermes-3 3B:** older (GGUF from December 2024) and English-only on its card, but it is the one candidate with a confirmed native tool format.
  5. **DeepSeek-R1-Distill:** low priority, as long reasoning output is costly at these speeds.
  6. **SmolLM3, Granite micro, Phi-4-mini:** no advantage worth the size for German on this phone.
  7. **Gemma 4 E2B and Gemma 3n E2B:** too large for comfort; test only if the smaller ones fail on German quality.
- **Dropping to Q3_K_M** (Qwen3.5-2B is 1,107,149,056 bytes) saves little, so prefer switching to a smaller model.
- **Reasoning:** try `--reasoning off` where a model thinks by default, as it saves tokens at low speed.
- **German test:** ten fixed prompts covering Sie/du, a summary, a correction and a tool call, scored for language adherence and coherence, on the three finalists.

### Gaps
- **No measured tok/s or RSS for any listed model on an Exynos 9820 or Snapdragon 855**, except the single anecdote above. Run `llama-bench` on the phone before freezing.
- **No head-to-head German evaluation** of Qwen3.5, LFM2.5, Gemma 4 or Hermes-3 was found.
- **Qwen3.5 minimum llama.cpp version and text-only behaviour** (no mmproj) were not confirmed, though b12032 is far newer than the March 2026 upload.
- **`lfm1.0` terms and Gemma 3n's licence** were not read. Also not verified: whether the Llama community licence terms that apply to Hermes-3 suit this project.
- **Download URLs were not test-fetched**; they are built from the API data. Hashes come from the API.
- **Qwen3-1.5B repos** tried returned HTTP 401 (wrong repo names or restricted), so they are absent. Phi-3.5 is omitted as superseded.

## How do Android memory limits, mmap and thermals affect this, and how do peer apps cope?

### Takeaway
Java heap settings are irrelevant to GGUF loading. What matters is resident memory versus lmkd, and clean mmap pages are the only part the system can drop. Keep context small and run a single slot. Sourced data on S10e thermals and on peer-app internals was thin, so both need on-device measurement.

### Cited Findings
- **Paging:** modified memory "remains resident in RAM and cannot be paged out", with one exception: "any files mmapped in without modification ... can be paged out". PSS is "what the system considers to be your physical memory footprint" — [Android memory overview](https://developer.android.com/topic/performance/memory-overview).
- **`largeHeap`** concerns "a large Dalvik heap" and "doesn't guarantee a fixed increase in available memory" — [application element](https://developer.android.com/guide/topics/manifest/application-element).
- **LMK:** lmkd reacts to memory pressure by killing the least essential processes — [lmkd reference](https://source.android.com/docs/core/perf/lmkd). LMKs while displaying an activity or running a foreground service are counted as "user-perceived" — [Android vitals](https://developer.android.com/topic/performance/vitals/lmk). That page does not say a foreground service is protected.
- **Load modes:** default `auto` means mmap; `mlock` forces the model into RAM — [server README](https://github.com/ggml-org/llama.cpp/blob/master/tools/server/README.md).
- **Repacking:** `GGML_CPU_REPACK` ("runtime weight conversion of Q4_0 to Q4_X_X") is ON by default — [ggml/CMakeLists.txt](https://github.com/ggml-org/llama.cpp/blob/master/ggml/CMakeLists.txt); runtime flag `-nr/--no-repack` — [server README](https://github.com/ggml-org/llama.cpp/blob/master/tools/server/README.md).
- **Context guidance:** start around 4096, as a larger value "can spike memory and kill the terminal" — [docs/android.md](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md).
- **Metadata pitfall:** a Gemma 4 Android developer saw a mismatch between a model's trained context and the 2048 used at init, and fixed it by reading the GGUF header first — [Solace blog](https://henshinlabs.github.io/solace-gemma4good/blog.html). The same blog selects a library variant at startup from `/proc/cpuinfo`, which upstream now handles itself.
- **PocketPal:** uses llama.rn; its README mentions CPU/GPU/NPU backend fallback with partial offload, and a Benchmark page tracking tok/s and memory — [repo](https://github.com/a-ghorbani/pocketpal-ai). A secondary source says it offloads models automatically to manage memory — [SourcePulse](https://www.sourcepulse.org/projects/1842293). The README gives no mmap or context defaults.
- **ChatterUI:** uses the `cui-llama.rn` adapter, and "Use External Model" reads a model from storage without importing a copy, with a slight load delay — [repo fork](https://github.com/RejectVanity/ChatterUI). One issue shows an out-of-buffer memory access attributed to llama.rn — [#20](https://github.com/Vali-98/ChatterUI/issues/20).
- **SmolChat lineage:** OfflineLLM adapted its native wrapper from SmolChat-Android and lists NEON/SVE/i8mm kernels — [OfflineLLM](https://p.codekk.com/detail/Android/jegly/OfflineLLM).
- **Arm AI Chat** is upstream's pointer for a production app with model management and benchmarks — [docs/android.md](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md).

### Inferences
- **Budget (unsourced estimate):** assume 2.5-3.5 GB is realistically available to the model process on a 6 GB One UI phone. Measure with `adb shell dumpsys meminfo` and `/proc/meminfo` under typical app load.
- **Rule of thumb:** model file + KV cache + about 300 MB overhead should stay under that budget. This is why models of 2 GB or less are favoured.
- **Repacking may undo mmap's advantage.** If repacked tensors live in anonymous memory, they are not droppable clean pages. Compare `VmRSS` and `RssAnon` in `/proc/<pid>/status`, with and without `-nr`, and weigh against the speed loss.
- **Practical rules:**
  - Set `-c 2048-4096` explicitly, because `-c 0` takes the model's trained context.
  - Set `-np 1`.
  - Lower `-cram` and `-ctxcp`, since the defaults assume far more RAM.
  - Avoid `mlock`, which pins memory on a tight device.
  - Stop generation and unload on `onTrimMemory` critical levels.
  - Store GGUFs in internal `filesDir`, not on the FUSE path, to keep page-in fast.
- **Thermal design:** cap output length, stream tokens, keep 3-4 threads, and log tok/s over a 10-minute run to find the sustained rate. Report sustained, not peak, in the UI.

### Gaps
- **Exynos 9820 sustained throttling figures:** the AnandTech review mirror refused connection, and I found no other measured data. Measure with a 10-minute `llama-bench` loop and `/sys/class/thermal/`.
- **Samsung lmkd thresholds, and how much a Foreground Service is shielded,** were not sourced. Samsung's memory management may also differ from AOSP.
- **MLC Chat, and SmolChat's actual flags and context defaults:** not retrieved. Its CMake and loader code are the place to look.
- **Whether llama.rn apps enable mmap by default** was not confirmed.
- **`PowerManager.getCurrentThermalStatus()` and `onTrimMemory` levels** are suggested from prior knowledge, not sourced here.

## Are LiteRT-LM, MediaPipe LLM or MNN better than llama.cpp on this device?

### Takeaway
Not provably. Newer-phone anecdotes suggest MNN or LiteRT-LM can be faster on CPU, but there is no data for this SoC class. Stay on llama.cpp for its broad GGUF choice and server. If speed disappoints, run a one-day LiteRT-LM spike with Gemma 4 E2B.

### Cited Findings
- **LiteRT-LM, Gemma 4 E2B:** 2.58 GB model. S26 Ultra CPU: 557 prefill tok/s, 47 decode tok/s, 1.8 s TTFT, 1733 MB peak. Raspberry Pi 5 CPU: 133 prefill, 8 decode, 7.8 s TTFT, 1546 MB — [Google](https://developers.google.com/edge/litert-lm/models/gemma-4). No older phones are listed. Files: `gemma-4-E2B-it.litertlm` 2,588,147,712 bytes; `gemma-4-E2B-it-gpu.litertlm` 2,008,432,640 bytes — [HF tree](https://huggingface.co/api/models/litert-community/gemma-4-E2B-it-litert-lm/tree/main).
- **LiteRT-LM on mid-range CPUs:** blog claims conflict at 2-5 tok/s — [DEV](https://dev.to/samdude/gemma-4-on-android-tricks-for-faster-on-device-inference-3kj5) — versus 8-12 — [DEV](https://dev.to/rahul_patwa_f99f19cd1519b/gemma-4-on-android-complete-developer-guide-to-on-device-ai-f5b). Neither cites a measurement. A "6 GB minimum" claim for E2B is unverified.
- **LiteRT vs llama.cpp anecdote:** about 18 tok/s against 11-12 with Qwen3.5-4B on a OnePlus 13 — [r/LocalLLaMA snapshot](https://reddit.sentinel-team.org/posts/1t0k6fj/snapshots/2026-05-02T03%3A06%3A21.60641Z).
- **MNN vs llama.cpp:** a benchmark of Llama 3.2 1B at 4-bit gives MNN 25.70 tok/s vs llama.cpp 3.29 on a OnePlus 12 — surfaced by search; likely [BestHub](https://www.besthub.dev/articles/edge-deployment-and-performance-optimization-of-large-language-models-with-mnn-6746fb6cfa51), attribution not confirmed. Elsewhere MNN and llama.cpp tie at 6-7 tok/s with Qwen3.5 9B on a OnePlus 13 — [r/LocalLLaMA snapshot](https://reddit.sentinel-team.org/posts/1t0k6fj/snapshots/2026-05-02T03%3A06%3A21.60641Z). The sources conflict, and the 3.29 figure looks implausibly low for that phone.
- **MNN stability:** user reports split on whether MNN or llama.cpp crashes on large models — surfaced by search; likely this [r/LocalLLaMA thread](https://lr.us.psf.lt/r/LocalLLaMA/comments/1kdqibi/qwen3_8b_on_android_its_not_half_bad), attribution not confirmed.

### Inferences
- **llama.cpp stays the default:** it offers many GGUFs, a ready tool-calling server, published hashes, and a CPU path that fits an old non-Snapdragon phone. Any LiteRT-LM adoption would largely narrow the model choice.
- **LiteRT-LM spike trigger:** if Qwen3.5-2B sustains under 3 tok/s, or German quality is inadequate.
- **MNN needs model conversion**, so it is a poor fit unless benchmarks on this phone show a clear gain.

### Gaps
- **No benchmark of LiteRT-LM or MNN on Exynos 9820 or Snapdragon 855** was found.
- **Whether LiteRT-LM's GPU path runs on Mali-G76** is unknown.
- **MediaPipe LLM Inference status** in November 2026 was not researched. Prior knowledge suggests it is superseded by LiteRT-LM; verify before dismissing it.
- **Tool-calling support in LiteRT-LM and MNN** was not checked.

## How should the model reach the phone: first-run download and adb push?

### Takeaway
The adb route must come first because the phone is offline. Build one import path that ends with SHA-256 verification and an atomic rename, shared by adb and network delivery. UIDT is unavailable on Android 12, so a later network download uses WorkManager's foreground path.

### Cited Findings
- **UIDT requires API 34;** on older Android use WorkManager's foreground-service path — [UIDT guide](https://developer.android.com/develop/background-work/background-tasks/uidt).
- **WorkManager:** "jobs taking longer than 10 minutes will be rescheduled"; for longer work it can use a foreground service, and tasks must tolerate being interrupted — [data transfer options](https://developer.android.com/develop/background-work/background-tasks/data-transfer-options). That page does not cover DownloadManager.
- **Sources conflict on plain `adb push` to app-specific external storage.** It failed with "couldn't create file: Permission denied" on a Pixel 5 running Android 13 — [Mozilla bug 1795592](https://bugzilla.mozilla.org/show_bug.cgi?id=1795592). A testing guide claims shell access still works on API 30+ — [android-testing-skills](https://raw.githubusercontent.com/skydoves/android-testing-skills/8665ed59643c90eb1056a256a6ddec161aa1cfa1/adb/transfer/extracting-test-artifacts/SKILL.md). No primary doc settles Android 12 on Samsung.
- **`run-as` route:** push to a shell-writable location, then `adb shell run-as <pkg> cp ...`. It works only for `android:debuggable="true"` builds — [same guide](https://raw.githubusercontent.com/skydoves/android-testing-skills/8665ed59643c90eb1056a256a6ddec161aa1cfa1/adb/transfer/extracting-test-artifacts/SKILL.md).
- **Large-file failure seen in the wild:** "adb: error: 65544-byte write failed" on one tablet, with no clear fix — surfaced by search; exact source not confirmed.
- **Integrity data:** LFS SHA256 is published per file — table above.

### Inferences
- **Route A, try first:**
  ```bash
  # after launching the app once so getExternalFilesDir() creates the folder
  adb shell mkdir -p /sdcard/Android/data/<pkg>/files/models
  adb push Qwen3.5-2B-Q4_K_M.gguf /sdcard/Android/data/<pkg>/files/models/model.gguf.part
  ```
  Works without `debuggable`, if Android 12 on this build permits it.
- **Route B, debug builds:**
  ```bash
  adb push model.gguf /data/local/tmp/model.gguf
  adb shell run-as <pkg> sh -c 'mkdir -p files/models && cp /data/local/tmp/model.gguf files/models/model.gguf.part'
  adb shell rm /data/local/tmp/model.gguf
  ```
- **Route C, works for any build:** `adb push model.gguf /sdcard/Download/`, then the app imports via the system file picker (`ACTION_OPEN_DOCUMENT`) and copies into `filesDir/models/`. No permission or `debuggable` needed. Keep this as the guaranteed fallback.
- **Shared import pipeline:** write to `*.part`, stream SHA-256 against the pinned value, then atomic `rename()` to the final name. On mismatch, delete the file and report. The app ships a small manifest of allowed models (name, bytes, SHA-256).
- **Later online download:** a WorkManager `CoroutineWorker` running as a foreground service with HTTP `Range` resume from the `.part` length. DownloadManager is simpler, but gives less control over staged verification.
- **Offline guarantee:** once a model is verified, no network code path should run. Use `--no-webui`, avoid `-hf`/`-mu`, and always pass a local `-m` path.
- **Host prerequisite:** `adb` is not on this Linux host, so install Android platform-tools first. USB debugging must be enabled on the phone, and USB adb works with no network.

### Gaps
- **Routes A, B and C are untested on the S10e.** Route A is the uncertain one.
- **HTTP `Range` support on Hugging Face's CDN** and DownloadManager's redirect handling were not verified. Prior knowledge says Range works.
- **Whether apps can read `/data/local/tmp`:** prior knowledge says generally not, which is why route B copies via `run-as`. Do not point the app there directly, even though `docs/android.md` uses it for shell-run binaries.
- **The official docs on DownloadManager vs WorkManager** for multi-gigabyte files were not found in what I fetched.
