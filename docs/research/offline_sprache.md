# Offline German speech input and output for a native Android app on Galaxy S10e (state: 1 Nov 2026)

Target: Exynos 9820, 6 GB RAM, Android 12 (API 31), no network, no root, local LLM on the same phone. Nothing here was tested on an S10e. No hardware test was run, and no model file was downloaded.

## Which speech stack should the app use?

### Takeaway
Use Vosk `vosk-model-small-de-0.15` for the wake phrase and for commands, and Piper `de_DE-thorsten-medium` through sherpa-onnx for speech output. Do not use Android SpeechRecognizer, Porcupine, whisper.cpp or sherpa-onnx KWS as the base. Two points must pass an on-device test before the stack is final: whether Vosk's small German model contains the words of the phrase, and how Piper German works with sherpa-onnx 2.x.

### Cited Findings
- Fixed-phrase recognition is available in Vosk through a JSON phrase list, and a `[unk]` entry gives a fallback for speech outside the list — [Vosk API header](https://hex.pm/packages/vosk_ex/0.1.1/files/c_src/include/vosk_api.h); [Rust grammar example](https://docs.rs/crate/vosk/latest/source/examples/grammar.rs)
- No pretrained German sherpa-onnx KWS model is listed: the docs show zh-en, Chinese and English models only — [sherpa-onnx KWS docs](https://k2-fsa.github.io/sherpa/onnx/kws/index.html)
- Dicio, an Android assistant with German among its languages, combines Vosk STT (about 50 MB models) with openWakeWord — [Dicio README](https://github.com/Stypox/dicio-android)
- `de_DE-thorsten-medium` is 63.2 MB and CC0 — [Hugging Face file list](https://huggingface.co/rhasspy/piper-voices/tree/main/de/de_DE/thorsten/medium); [model card](https://huggingface.co/rhasspy/piper-voices/raw/main/de/de_DE/thorsten/medium/MODEL_CARD)

### Inferences
Recommended architecture (my synthesis):

| Function | Primary | Fallback | Rejected |
|---|---|---|---|
| Wake phrase | Vosk grammar `["agentenmodus aktivieren", "[unk]"]`, ideally gated by a VAD | Self-trained openWakeWord German model | Porcupine, sherpa-onnx KWS (no German model) |
| Commands | Vosk grammar per app state (same model instance) | Vosk full small-model vocabulary | SpeechRecognizer, Parakeet v3 (640 MB) |
| Free dictation, if needed | Vosk full vocabulary | Whisper tiny through sherpa-onnx; `primeline/whisper-tiny-german-1224` after conversion | whisper.cpp (3.52 RTF on this SoC) |
| Speech output | Piper thorsten-medium through sherpa-onnx | Android TextToSpeech, only if a German voice is already installed | Kokoro (no official German) |

- One Vosk model can serve wake phrase and commands, so only one ASR model occupies RAM. This matters more than peak accuracy, because the LLM competes for the same memory.
- Avoiding whisper.cpp also avoids the ggml clash with llama.cpp. Vosk (Kaldi) and sherpa-onnx (onnxruntime) do not vendor ggml. That is my reading of their stacks, not something a source states as a compatibility guarantee.
- Probe order on the real phone: (1) check `graph/words.txt` or run a grammar test for the phrase, (2) measure Vosk RSS and CPU with the LLM loaded, (3) measure Piper time-to-first-audio, (4) run a 1-hour false-trigger test and a battery-drain test, (5) check the TTS voice list in airplane mode.
- A spoken phrase is not access control. Anyone who says it, or a recording of it, unlocks the mode, because a grammar match has no speaker verification. If "unlock" needs to resist the child, add a second factor. openWakeWord's custom verifier is voice-specific but aims at reducing false activations and is not a security feature ([openWakeWord README](https://github.com/dscripka/openWakeWord)).

### Gaps
- Combined RAM of Vosk, Piper and the LLM on 6 GB is unmeasured. No source covers this combination.

## Android SpeechRecognizer on-device mode: available on Android 12 Samsung, and without Google account or network?

### Takeaway
The API exists on Android 12, but availability depends on the ROM and on Google components, and I found no evidence for the S10e or for German without a Google account. With no network and no way to fetch a language pack, it is not a safe foundation. Run a short probe, but do not design around it.

### Cited Findings
- `createOnDeviceSpeechRecognizer` and `isOnDeviceRecognitionAvailable` were added in API 31, along with `ERROR_LANGUAGE_UNAVAILABLE` and related constants. The API 31 diff lists only those two methods, so `checkRecognitionSupport` and `triggerModelDownload` are not part of API 31 — [API 31 diff](https://developer.android.com/sdk/api_diff/31/changes/android.speech.SpeechRecognizer)
- `createOnDeviceSpeechRecognizer` throws `UnsupportedOperationException` when no on-device recognizer exists — [Android reference via search](https://developer.android.com/reference/android/speech/SpeechRecognizer); a OnePlus 9R on Android 12 crashed this way — [HackerNoon field report](https://hackernoon.com/testing-androids-on-device-speech-recognition-with-hindi-english-code-switching)
- The availability check reportedly only tests whether the ROM config `config_defaultOnDeviceSpeechRecognitionService` is non-empty, so `true` does not prove that German works — [Librechat-Mobile issue #421](https://github.com/garfiec/Librechat-Mobile/issues/421) (single developer report)
- Samsung reportedly points that config at Android System Intelligence (`com.google.android.as`). This was seen on S21 and Fold5, not S10e, and comes from one PR — [Funput PR #532](https://github.com/Funput/Funput/pull/532)
- `EXTRA_PREFER_OFFLINE` is optional, and depending on the recognizer it may have no effect — [RecognizerIntent docs](https://developer.android.com/reference/android/speech/RecognizerIntent)
- A plain SpeechRecognizer can send audio to the cloud when connected, which is a privacy issue, though not a functional one on this offline phone — [Sotto issue #36](https://github.com/triton1502oc/Sotto/issues/36)
- Offline recognition failed in one sample project until the language was installed through Google voice input's offline menu — [SpeechRecognizerSample](https://github.com/cryptocat-miner/SpeechRecognizerSample). Older report: offline mode worked on a Samsung Tab A with Android 8.1 — [MIT App Inventor thread](https://community.appinventor.mit.edu/t/speech-recognizer/1930)

### Inferences
- On API 31 there is no documented way to query or trigger a German pack, so the app can only try and handle `ERROR_LANGUAGE_UNAVAILABLE`.
- This probably repeats the prototype's failure in a different form: a dependency on Google components that the app cannot control offline.

### Gaps
- I found no reliable source on an offline German pack with no signed-in Google account, or on S10e with Android 12.
- The exact API levels of `checkRecognitionSupport` and `triggerModelDownload` were not verified. The SpeechRecognizer reference did not render usefully.
- ML Kit's on-device recognizer has a model-download step ([ML Kit reference](https://developers.google.com/android/reference/kotlin/com/google/mlkit/genai/speechrecognition/SpeechRecognizer)). Whether it can be prepackaged for a phone without network is unverified.

## Vosk Android: version, German small model, RAM, latency, grammar, licence, maven

### Takeaway
Vosk is the best-fitting ASR here: 45 MB, Apache 2.0, runtime grammars, and a working Android record. It uses about 300 MB RAM by the only available figure, and it is less accurate on free speech. Its project activity looks slow, so pin the version.

### Cited Findings
- Maven: `com.alphacephei:vosk-android:0.3.75`, AAR, Apache 2.0, published "10 months ago" at time of reading (so roughly early 2026) — [Maven Central](https://central.sonatype.com/artifact/com.alphacephei/vosk-android)
- GitHub's releases page shows v0.3.50 as its latest tagged release, older than the Maven artifact. The Maven version is the one to use — [vosk-api releases](https://github.com/alphacep/vosk-api/releases)
- `vosk-model-small-de-0.15`: 45 MB, Apache 2.0, WER 13.75 on Tuda-de and 30.67 on podcast, listed as lightweight for Android — [Vosk models](https://alphacephei.com/vosk/models)
- `vosk-model-small-de-zamia-0.3`: 49 MB, LGPL-3.0, WER 14.81, not recommended by the page — [Vosk models](https://alphacephei.com/vosk/models)
- Small models need about 300 MB RAM at runtime. This is a general figure, not specific to German or phones — [Vosk models](https://alphacephei.com/vosk/models)
- Small models support online vocabulary changes, and big static-graph models do not — [Vosk adaptation](https://alphacephei.com/vosk/adaptation)
- Without `[unk]`, the best match is returned even when it is probably wrong — [Rust grammar example](https://docs.rs/crate/vosk/latest/source/examples/grammar.rs). Grammar swaps work on a running recognizer, and `[]` restores the default graph — [Vosk API header](https://hex.pm/packages/vosk_ex/0.1.1/files/c_src/include/vosk_api.h)
- An old Android demo report on Samsung S7 and S8 saw memory grow about 40 MB per recognition, and a maintainer said it was fixed — [vosk-android-demo issue #14](https://github.com/alphacep/vosk-android-demo/issues/14) (older, version unclear)
- Latency: Vosk claims a zero-latency streaming API, which is marketing and not a measurement — [Vosk description](https://beta.pkg.go.dev/github.com/Ferrany1/vosk-api)

### Inferences
- Compound words are the main risk. "Agentenmodus" may be missing from a 45 MB model's vocabulary. If so, test `agenten modus aktivieren` as separate words, or match the sequence in partial results.
- The phrase is about seven syllables, and long phrases should produce fewer accidental matches than two-syllable wake words. This is reasoning, not measured data.

### Gaps
- Whether "agentenmodus", "agenten", "modus" and "aktivieren" are in the model is unverified. I did not download the model, and the docs do not state how out-of-vocabulary grammar words behave.
- No measured phone latency or S10e RAM figure was found.

## whisper.cpp on Android: German accuracy, real-time factor, shared ggml with llama.cpp

### Takeaway
Stock tiny and base are poor on German and whisper.cpp was slower than real time on this exact SoC in the one benchmark found. Do not ship it. If Whisper is ever needed, use sherpa-onnx, which also avoids the ggml clash.

### Cited Findings
- Whisper.cpp is at v1.9.5 (6 Oct 2026, year inferred from the page), bundling ggml v0.26.0 — [whisper.cpp releases](https://github.com/ggml-org/whisper.cpp/releases)
- On a Samsung Galaxy S10 (Exynos 9820, 8 GB, Android 12), 30 s of audio with Whisper Tiny: whisper.cpp 105,596 ms (RTF 3.52), sherpa-onnx 2,068 ms (RTF 0.07). Whisper Base with sherpa-onnx: 4,038 ms (RTF 0.13). The authors say the 51× gap describes complete configurations, not the library alone — [VoicePing benchmark](https://voiceping-landingpage-prod.pages.dev/en/blog/research-offline-speech-transcription-benchmark/). This is the same SoC as the S10e.
- A search snippet of that page suggested RTF 0.55 for whisper.cpp Tiny, but the fetched table says 3.52, and 105.6 s over 30 s confirms 3.52 — [VoicePing benchmark](https://voiceping-landingpage-prod.pages.dev/en/blog/research-offline-speech-transcription-benchmark/)
- German WER on Tuda-De: Whisper tiny 47.33, base 33.58, small 15.94, and the fine-tuned `primeline/whisper-tiny-german-1224` 9.62, calculated December 2024. The model is Apache-2.0, in Safetensors and ONNX — [model card](https://huggingface.co/primeline/whisper-tiny-german-1224). Scores shift with the text normalizer — [repo discussion](https://huggingface.co/primeline/whisper-large-v3-turbo-german/discussions/1)
- Linking both libraries produced hundreds of duplicate ggml symbols on iOS/Xcode in February 2024, because each project vendors ggml. No Android report matching this was found — [whisper.cpp issue #1887](https://github.1485827954.workers.dev/ggml-org/whisper.cpp/issues/1887)
- whisper.cpp ships a `talk-llama` example with llama sources synced alongside ggml — [whisper.cpp commit](https://huggingface.co/spaces/Xenobd/whisper.cpp/commit/ad9dd7be778aeb1d675b621bd7db008d45cc692f)
- llama.cpp's Android binding (merged 17 Dec 2025) uses dynamic loading of CPU-variant `.so` files, which increases the number of native libraries to manage — [llama.cpp PR #17413](https://github.com/ggml-org/llama.cpp/pull/17413)

### Inferences
- The WER numbers for Vosk and Whisper come from different authors and likely different normalizers, so comparing 13.75 with 9.62 directly is unsafe.
- Stock Whisper tiny at 47 on Tuda-De is not usable for commands. Only the fine-tuned model would be competitive.
- If the two ggml users ever had to share an APK, the options are one shared ggml build at a pinned revision, or separate `.so` files with hidden symbols. The second is my suggestion and is unverified on Android.

### Gaps
- No German accuracy measured on the phone, and no RTF for base on whisper.cpp on Android.
- The 3.52 result may reflect build settings. The page does not clearly attribute the cause.
- Whether the primeline ONNX export is compatible with sherpa-onnx's Whisper loader is unverified.

## sherpa-onnx: German ASR, KWS, TTS, Android API, model sizes

### Takeaway
Use sherpa-onnx for German TTS. It has no German KWS model and no German streaming ASR model that I could find. The 2.0 release changed how Piper voices get phonemes, so pin the version deliberately.

### Cited Findings
- Licence Apache-2.0, Android is a supported target, and prebuilt APKs exist for streaming ASR, TTS and KWS. It describes itself as running without internet — [sherpa-onnx repo](https://github.com/k2-fsa/sherpa-onnx)
- Latest release v2.0.1 (26 Oct 2026), with an Android AAR of 47.2 MB — [sherpa-onnx releases](https://github.com/k2-fsa/sherpa-onnx/releases)
- KWS is open-vocabulary through token files, with no retraining, but models are zh-en, Chinese and English — [KWS docs](https://k2-fsa.github.io/sherpa/onnx/kws/index.html)
- Streaming ASR examples listed are Chinese/English, English, Korean and French. No German streaming model was found — [sherpa-onnx package docs](https://pub.dev/documentation/sherpa_onnx/latest/)
- `sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8` covers 25 European languages and is about 640 MB unpacked — [pretrained models](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html); [NeMo transducer docs](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-transducer/nemo-transducer-models.html). NVIDIA lists German — [NVIDIA model card](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3)
- Whisper tiny.en int8: encoder about 12 MB, decoder about 105 MB — [sherpa-onnx docs](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/whisper/tiny.en.html). The multilingual `tiny` uses the same commands, and its sizes are not stated there.
- PR #4055 in v2.0.0 removed espeak-ng and piper-phonemize. Text now reaches TTS by built-in lexicon or by phonemes produced externally, `data_dir` is "deprecated and ignored", and new `phoneme_codepoints` and `tokens` fields exist. The stated reason is that sherpa-onnx is GPL-free after the PR — [PR #4055](https://github.com/k2-fsa/sherpa-onnx/pull/4055)
- **Conflict:** the German Thorsten docs page still configures `espeak-ng-data` as `data_dir` and points to prebuilt v2.0.1 TTS engine APKs — [thorsten-medium docs](https://k2-fsa.github.io/sherpa/onnx/tts/all/German/vits-piper-de_DE-thorsten-medium.html). PR #4055's file list contains German `vits-de-phonemize` examples, which suggests an external phonemize step — [PR #4055](https://github.com/k2-fsa/sherpa-onnx/pull/4055)

### Inferences
- The docs may lag behind the code. The safest route for a private app is to pin a 1.13.x AAR such as v1.13.8, the version named on the release page, with built-in espeak-ng. GPL does not matter for a private build that is not distributed.
- Using 2.x means bundling a German phonemizer yourself or building a lexicon, which is extra native work with no benefit for this app.
- Parakeet v3's size is unsuitable next to an LLM on 6 GB.

### Gaps
- It is unverified whether 2.0.1 Kotlin on Android can speak German Piper from plain text. Test this before choosing the version.
- It is unverified whether v1.13.8 is the final pre-2.0 release.
- Training a German KWS model is not documented as a supported user path in what I read.

## Wake phrase: which option works for a custom German phrase with no cloud and acceptable battery?

### Takeaway
Start with a Vosk grammar, because it needs no training and is free, then add a VAD gate. Train an openWakeWord German model only if battery or false triggers fail the test. Porcupine is out.

### Cited Findings
- **Porcupine:** German is supported and custom words come from Picovoice Console — [Porcupine FAQ](https://picovoice.ai/docs/faq/porcupine/). Standard model about 1 MB RAM, under 4% of a Raspberry Pi 3 core — [Porcupine FAQ](https://picovoice.ai/docs/faq/porcupine/).
- **Porcupine licence:** A Home Assistant user relays Picovoice's email: Free Tier ends 30 June 2026, keys disabled, no non-commercial tier planned; the key is checked at engine start and "The SDK does not run without a valid key" — [HA forum, 4 Jun 2026](https://community.home-assistant.io/t/fyi-picovoice-confirmed-free-tier-accesskeys-will-stop-working-after-june-30-2026/1012744). Picovoice's FAQ says there are no dedicated plans for personal or non-commercial use and describes a one-time Free Trial for enterprise developers — [Picovoice FAQ](https://picovoice.ai/docs/faq/general). A search snippet of the pricing page still showed "$0 /forever", and the live page returned no content, so that is unresolved — [Pricing](https://picovoice.ai/pricing). The AccessKey requires internet for validation — [pvporcupine PyPI](https://pypi.org/project/pvporcupine).
- **openWakeWord:** code Apache 2.0; pretrained models CC BY-NC-SA 4.0; officially English only; last listed update v0.6.0 (11 Feb 2024); a single Raspberry Pi 3 core runs 15–20 models in real time; several thousand synthetic examples are recommended — [openWakeWord README](https://github.com/dscripka/openWakeWord)
- Community notebooks train openWakeWord in any language Piper supports — [HA forum](https://community.home-assistant.io/t/colab-notebooks-to-train-your-custom-openwakeword-or-microwakeword/1026635). `openwakeword-train` includes German, but its author says this is not fully validated in real conditions — [HA forum](https://community.home-assistant.io/t/tool-openwakeword-train-train-your-own-wake-words-locally-tts-examples-real-mic-negatives-onnx-tflite-export/1026289)
- **onnx-wakeword:** Apache 2.0 runtime for Linux, Android and web; about 100 KB models; self-reported 90%+ recall including German; a custom model costs $4.90; no false-activation figure given — [HA forum](https://community.home-assistant.io/t/custom-non-english-wake-words-for-home-assistant-93-recall-fully-offline-onnx-100-kb/1021320)
- **Vosk grammar:** A desktop project reported fewer false positives after constraining Vosk to a command vocabulary — [talk-spotter v1.0.0](https://github.com/EvanBoyar/talk-spotter/releases/tag/v1.0.0)
- openWakeWord already runs on Android inside Dicio — [Dicio README](https://github.com/Stypox/dicio-android)

### Inferences
- Comparison for this app:

| Option | Custom German | Offline after install | Cost | Battery | Verdict |
|---|---|---|---|---|---|
| Vosk grammar | Yes, if words are in vocabulary | Yes | Free | Highest (full decoder), reduce with VAD | Start here |
| openWakeWord custom | Yes, training on a PC with Piper German | Yes | Free | Designed for low CPU | Fallback |
| sherpa-onnx KWS | No German model | Yes | Free | Unknown | Not now |
| onnx-wakeword | Yes, claimed | Runtime yes; training is a paid service | $4.90 | Unknown | Optional, unproven |
| Porcupine | Yes | Key validation blocks it | No personal plan | Very low | Rejected |

- Check the openWakeWord licence if you ever distribute: a model you train yourself may still depend on shared feature models whose licence I did not confirm. Private use is the stated case here, so this is low risk.
- For a phone with no network, Porcupine's validation is a hard blocker regardless of price.

### Gaps
- I found no measured Android battery data for any option, including Vosk always-on. This needs a drain test.
- No German false-accept rate for any option was found.

## Offline German TTS: Android engine, Piper voices, Kokoro

### Takeaway
Bundle Piper thorsten-medium inside the app, which removes all dependence on Samsung or Google voice data. Kokoro has no official German. Android TextToSpeech is only a fallback, because I could not confirm that a German voice is available on a phone without an account.

### Cited Findings
- **Thorsten:** `de_DE-thorsten-medium` 63.2 MB, CC0, 22,050 Hz — [files](https://huggingface.co/rhasspy/piper-voices/tree/main/de/de_DE/thorsten/medium); [model card](https://huggingface.co/rhasspy/piper-voices/raw/main/de/de_DE/thorsten/medium/MODEL_CARD)
- **Kerstin:** `de_DE-kerstin-low` 63.1 MB, CC0, 16,000 Hz — [files](https://huggingface.co/rhasspy/piper-voices/tree/main/de/de_DE/kerstin/low); [model card](https://huggingface.co/rhasspy/piper-voices/raw/main/de/de_DE/kerstin/low/MODEL_CARD)
- sherpa-onnx lists 18 German entries, including Piper thorsten, kerstin, ramona, pavoque, karlsson, eva_k and miro, plus `supertonic-3-de`. Page text said 19, while the list showed 18 — [German TTS list](https://k2-fsa.github.io/sherpa/onnx/tts/all/German/index.html)
- Prebuilt sherpa-onnx TTS engine APKs exist for thorsten-medium, which also gives a quick way to hear the voice on the phone — [thorsten-medium docs](https://k2-fsa.github.io/sherpa/onnx/tts/all/German/vits-piper-de_DE-thorsten-medium.html)
- Piper's active repo is `OHF-Voice/piper1-gpl`, GPL-3.0, which embeds espeak-ng, and the project is seeking maintainers — [piper1-gpl](https://github.com/OHF-Voice/piper1-gpl)
- **Kokoro:** no official hexgrad German voice is confirmed, and an issue asks for funding to add it — [kokoro issue #290](https://github.com/hexgrad/kokoro/issues/290). Community German voices exist, and df_eva and dm_bernd came from a deleted repo's LFS, while df_victoria and dm_martin are from kikiri-tts (Apache-2.0) — [voices bundle](https://huggingface.co/cstr/kokoro-voices-GGUF). A community German base model trained on HUI exists — [kokoro-de-hui-base-GGUF](https://huggingface.co/cstr/kokoro-de-hui-base-GGUF)
- **Samsung:** German voices are separate APK packages — [APKMirror listing](https://www.apkmirror.com/apk/samsung-electronics-co-ltd/samsungtts-german-male). One forum user says voice data updates need a Samsung account, and a reply says TTS needs a Samsung or Google connection — [Samsung Community](https://us.community.samsung.com/t5/Galaxy-S24/use-tts-without-samsung-account/td-p/3204961) (S24, anecdotal)

### Inferences
- Kerstin-low is no smaller on disk than thorsten-medium, so "low" does not save space across these two voices, and thorsten-medium has the higher sample rate. Thorsten-low's own size was not checked.
- Speak sentence by sentence as the LLM streams, because CPU contention with the LLM is likely the main latency risk.
- Sideloading Samsung voice APKs from third-party mirrors is not recommended: the source is untrusted, and bundling a CC0 voice gives a cleaner result.
- For the fallback path, list voices with `TextToSpeech.getVoices()` in airplane mode and accept only German voices that do not need network and have data installed. This is from my API knowledge, not checked in this run.

### Gaps
- No RTF or time-to-first-audio figure for Piper German on Exynos 9820 was found. Must be measured.
- I could not establish whether German voice data is preinstalled on an S10e without a Google account. Check the device.
- Supertonic-3-de size, licence and German quality were not checked.

## Foreground service, privacy indicator, and audio focus and echo

### Takeaway
Run capture in a `microphone`-type foreground service that the user starts from a visible activity, and do not expect auto-start at boot. Use half-duplex audio: stop feeding the recognizer while TTS plays. Do not rely on echo cancellation.

### Cited Findings
- Apps targeting API 30+ that use the mic in a foreground service must declare `android:foregroundServiceType="microphone"` — [Android 11 docs](https://developer.android.com/about/versions/11/privacy/foreground-services)
- A foreground service started while the app is in the background cannot access the mic — [Android 11 docs](https://developer.android.com/about/versions/11/privacy/foreground-services)
- Apps targeting API 31+ cannot start foreground services from the background without an exemption, otherwise `ForegroundServiceStartNotAllowedException` is thrown. Exemptions include `ACTION_BOOT_COMPLETED` and disabled battery optimization — [background start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- `FOREGROUND_SERVICE_MICROPHONE` is required from Android 14. `RECORD_AUDIO` must be granted first, and a `microphone` service cannot be launched from `BOOT_COMPLETED` except in narrow cases — [FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- **Doc nuance:** the restrictions page labels the while-in-use `SecurityException` as Android 14+, while the Android 11 page already blocks mic access from background-started services. The behaviour on Android 12 is therefore loss of mic access, with the exception style varying by version. While-in-use exemptions include notification and widget interaction — [restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start); [Android 11 docs](https://developer.android.com/about/versions/11/privacy/foreground-services)
- Android 12 shows a status-bar icon when an app uses the mic, and supported devices have a global mic toggle — [Android 12 behaviour changes](https://developer.android.com/about/versions/12/behavior-changes-all)
- `VOICE_COMMUNICATION` capture expects an AEC on the path. `VOICE_RECOGNITION` is meant to have flat response and no AGC by default, and AEC there is only recommended — [AOSP pre-processing](https://source.android.com/docs/core/audio/implement-pre-processing); [CDD 5.4](https://android.googlesource.com/platform/compatibility/cdd/+show/refs/tags/android-platform-12.1.0_r10/5_multimedia/5_4_audio-recording.md)
- One Stack Overflow answer says hardware AEC does not work on most devices — [archived answer](https://kaweah.freedombox.rocks/kiwix/content/stackoverflow.com_en_all_2023-11/a/38021196) (old and anecdotal)

### Inferences
- Manifest for this phone: `FOREGROUND_SERVICE`, `RECORD_AUDIO`, `foregroundServiceType="microphone"`. Declare `FOREGROUND_SERVICE_MICROPHONE` too for forward compatibility.
- Auto-listening after reboot is not reliable. Make the app open to a visible activity after reboot, or require one tap on the notification.
- The green indicator stays on for as long as the wake listener runs, so a parent or child will always see it. That is fine and appropriate here, and the persistent notification should state that listening is active.
- Suggested turn logic: stop feeding AudioRecord frames to Vosk (or reset the recognizer) just before TTS starts, resume 200–300 ms after playback ends, and discard the tail buffer. That delay is an estimate to tune.
- Barge-in during speech would need working AEC and is best left out of the first version.
- Use `VOICE_RECOGNITION` first, because Vosk expects unprocessed speech, and only test `VOICE_COMMUNICATION` if echo remains.

### Gaps
- Audio focus specifics with TTS and recording were not sourced in this run. Request transient focus for TTS as standard practice, and verify it on the device.
- It is unverified what an app receives when the Android 12 mic toggle is off (likely silent buffers), and whether the S10e exposes that toggle. Add a silent-input watchdog.
- No Samsung-specific AEC or One UI data was found.
