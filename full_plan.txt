# S10e Companion Implementation Plan

> **For agentic workers:** Arbeite genau eine Phase pro Session ab. Schritte nutzen Checkboxen (`- [ ]`). Stelle keine Rückfragen: Halte Annahmen in `docs/DECISIONS.md` fest und mach weiter.

**Goal:** Native, vollständig offline laufende Android-APK für das Galaxy S10e mit LOOI-Augen, Kindermodus-Sperre, lokalem LLM und Apollo-Terminal.

**Architecture:** Eine Kotlin/Compose-App mit reiner Logik in JVM-testbaren Klassen und dünnen Android-Adaptern. `llama-server` läuft als Kindprozess eines Foreground Service auf `127.0.0.1:8080`. Sprache läuft über Vosk (Eingabe) und Piper via sherpa-onnx (Ausgabe). Modelle liegen nie im Repo, sondern werden per `adb` plus Import auf das Gerät gebracht.

**Tech Stack:** AGP 9.5.0, Gradle 9.6, Kotlin 2.4.21, Compose BOM 2026.09.00, CameraX 1.6.2, Roborazzi 1.77.0, NDK 29.0.14206865, llama.cpp Tag `b12032`, Vosk Android 0.3.75, sherpa-onnx 1.13.x.

**Spec:** `docs/SPEC.md`. Belege und Grenzen: `docs/research/`.

## Global Constraints

- Paket `dev.s10e.companion`, App-Name „S10e Companion“, alle UI-Texte Deutsch, Code und Bezeichner Englisch.
- `minSdk 31`, `targetSdk 31`, `compileSdk 37`, nur ABI `arm64-v8a`. Gradle läuft auf JDK 21, Bytecode-Ziel 17.
- Nach dem Einspielen der Modelle führt kein Codepfad Netzwerkzugriffe nach außen aus. Die App deklariert `INTERNET` nur für Loopback, `android:usesCleartextTraffic` gilt per Network-Security-Config nur für `127.0.0.1`.
- Keine Modelle, APKs, Keystores oder `local.properties` im Repo. Dateien über 50 MiB sind verboten.
- Repo und APK-Releases bleiben privat, weil sherpa-onnx 1.13.x das GPL-lizenzierte espeak-ng enthält.
- llama.cpp: keine handgesetzten `-march`-Flags, kein i8mm, kein Vulkan (`-ngl` nie setzen), kein `-snapdragon`-Build.
- Der Agent bekommt niemals eine rohe Shell. Aktionen laufen nur über die Allowlist aus Phase 8.
- `.github/workflows/` nur ändern, wenn eine Phase es verlangt.
- Instrumentierte Tests sind kein Abnahmekriterium, weil auf Jules kein Emulator verfügbar ist. Abnahme ist JVM-Tests plus `assembleDebug`.

## Review Focus

1. Das Betriebssystem beendet `llama-server` wegen Speichermangels mitten in einer Antwort. Erwartet: Die UI friert nicht ein, zeigt eine verständliche Meldung und der Server startet mit Backoff neu. Test: Phase 4.
2. Ein Modell-Import bricht ab oder die Prüfsumme stimmt nicht. Erwartet: keine halbe Datei am Zielort, klare Fehlermeldung, erneuter Import möglich. Test: Phase 4.
3. Das Mikrofon ist per Systemschalter aus, oder die Erkennung versteht die Entsperrphrase nicht. Erwartet: Das Kind bleibt gesperrt, die Eltern kommen trotzdem über PIN heraus. Test: Phase 6.
4. Die App stürzt im Kindermodus wiederholt ab. Erwartet: keine Endlosschleife, nach 3 Abstürzen in 60 s ein sicherer Bildschirm ohne Agentenfunktionen, die Sperre bleibt aktiv. Test: Phase 6.
5. Obsidian ändert `Kanban.md`, während die App eine ältere Fassung speichern will. Erwartet: Nichts geht verloren, die fremde Änderung wird nicht überschrieben. Test: Phase 7.

## Bewusste Abweichungen von der Spezifikation

Diese Punkte sind aus der Recherche begründet und gelten verbindlich. Details stehen in `docs/research/`.

| Vorgabe | Umsetzung | Grund |
|---|---|---|
| Entsperren nur per Sprache | Phrase öffnet die PIN-Eingabe, reine Sprachentsperrung ist ein Schalter in den Einstellungen (Standard aus) | Jedes Kind kann die Phrase nachsprechen |
| ADB-Shell-Steuerung | Allowlist über AccessibilityService | Normale Apps dürfen `input` nicht aufrufen, und ein LLM darf keine rohe Shell bekommen |
| OpenCode-Patching | Patches auf einen Plugin-Ordner im WebView mit Snapshot-Rollback, Anbindung an ein optionales `opencode serve` in Termux | Eine kompilierte APK kann sich nicht selbst umschreiben |

---

## Phase 0: Toolchain-Smoke

**Files:** Create: `gradlew`, `gradle/wrapper/*`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/dev/s10e/companion/MainActivity.kt`, `app/src/test/java/dev/s10e/companion/SmokeTest.kt`, `docs/DECISIONS.md`

- [x] **Step 1:** `bash scripts/jules-setup.sh` ausführen. Falls ein SDK-Paketname nicht existiert, das Skript korrigieren und den Grund in `docs/DECISIONS.md` notieren.
- [x] **Step 2:** `gradle wrapper --gradle-version 9.6` ausführen und das Projekt mit den Versionen aus dem Tech Stack anlegen. `MainActivity` zeigt schwarzen Hintergrund und den Text „S10e Companion“.
- [x] **Step 3:** Test `SmokeTest.appName_isSet` schreiben: `assertEquals("S10e Companion", APP_NAME)`.
- [x] **Step 4:** `./gradlew --no-daemon testDebugUnitTest assembleDebug` ausführen. Erwartet: `BUILD SUCCESSFUL` und `app/build/outputs/apk/debug/app-debug.apk`.
- [x] **Step 5:** Ausgaben von `free -h`, `nproc`, `df -h`, `ls -l /dev/kvm` und die Build-Dauer in `docs/DECISIONS.md` und die PR-Beschreibung schreiben.
- [x] **Step 6:** Commit `chore: Gradle-Grundgerüst und Toolchain-Prüfung`.

## Phase 1: Modus-Logik und App-Shell

**Files:** Create: `app/src/main/java/dev/s10e/companion/core/ModeMachine.kt`, `.../ui/AppRoot.kt`, Test: `app/src/test/java/dev/s10e/companion/core/ModeMachineTest.kt`

**Interfaces (Produces):**
```kotlin
enum class AppMode { PASSIVE, AGENT, KID_LOCKED }
sealed interface ModeEvent {
  data class Interaction(val nowMs: Long) : ModeEvent
  data class Tick(val nowMs: Long) : ModeEvent
  object EnterAgent : ModeEvent
  object EnterKidMode : ModeEvent
  object UnlockConfirmed : ModeEvent
}
class ModeMachine(initial: AppMode, idleTimeoutMs: Long = 30_000) {
  val mode: AppMode
  fun onEvent(event: ModeEvent): AppMode
}
```

- [ ] **Step 1:** Tests schreiben: `idle30s_switchesAgentToPassive` (AGENT, `Tick` bei 30.000 ms liefert PASSIVE; bei 29.999 ms bleibt AGENT), `interactionResetsIdle`, `kidLocked_ignoresEnterAgent` (bleibt KID_LOCKED), `kidLocked_leavesOnlyViaUnlockConfirmed` (ergibt PASSIVE).
- [ ] **Step 2:** `./gradlew --no-daemon testDebugUnitTest` ausführen. Erwartet: FAIL, Klasse fehlt.
- [ ] **Step 3:** `ModeMachine` als reine Kotlin-Klasse ohne Android-Importe implementieren. `AppRoot` schaltet zwischen Platzhaltern für Augen und Terminal, Touch und Tastatur senden `Interaction`, ein 1-Sekunden-Takt sendet `Tick`. Immersives Vollbild und `FLAG_KEEP_SCREEN_ON` setzen.
- [ ] **Step 4:** Tests ausführen. Erwartet: PASS.
- [ ] **Step 5:** Commit `feat: Modus-Logik und App-Shell`.

## Phase 2: LOOI-Augen

**Files:** Create: `.../eyes/EyeModel.kt`, `.../eyes/EyesCanvas.kt`, `.../eyes/ShakeDetector.kt`, `.../eyes/ShakeSensor.kt`, Test: `.../eyes/EyeModelTest.kt`, `.../eyes/ShakeDetectorTest.kt`

**Interfaces (Produces):**
```kotlin
enum class Mood { NEUTRAL, HAPPY, ANGRY, LISTENING, THINKING, DIZZY, SLEEPY }
data class EyeFrame(val left: EyeShape, val right: EyeShape, val offsetX: Float, val offsetY: Float)
data class EyeShape(val width: Float, val height: Float, val corner: Float, val rotationDeg: Float, val argb: Long)
fun eyeFrame(mood: Mood, timeNanos: Long, lookX: Float = 0f, lookY: Float = 0f): EyeFrame
class ShakeDetector(val threshold: Float = 23f, val trigger: Float = 48f, val decay: Float = 5f) {
  fun onSample(x: Float, y: Float, z: Float): Boolean
}
```
Maße und Farben stammen aus dem Prototyp: Auge 108×148 dp, Ecke 40 dp, Abstand 58 dp; NEUTRAL `0xFF00F0FF`, HAPPY `0xFF00FF88` (Höhe 44), ANGRY `0xFFFF2A4B` (±24°, Skalierung Y 0,7), LISTENING `0xFFB84DFF` (Skalierung 1,1), THINKING `0xFFFFAA00` (Höhe 26), DIZZY `0xFFFFE600` (95 und 75 dp, Drehung), SLEEPY `0xFF005577` (Höhe 10).

- [ ] **Step 1:** Tests schreiben: `eyeFrame_angry_mirrorsRotation` (links +24, rechts −24), `eyeFrame_isPureFunctionOfTime` (gleiche Eingabe, gleiches Ergebnis), `eyeFrame_blinkLastsAbout130ms`, `eyeFrame_pixelShiftStaysWithin4dp` (Summe aus Drift bleibt über 24 h Simulationszeit in ±4 dp), `shake_triggersOnlyAboveAccumulator`, `shake_decaysBetweenBumps`.
- [ ] **Step 2:** Tests ausführen. Erwartet: FAIL.
- [ ] **Step 3:** `eyeFrame` als reine Funktion implementieren. `EyesCanvas` schreibt die Zeit in einer `withFrameNanos`-Schleife in einen State und liest ihn nur im Draw-Lambda, damit keine Recomposition pro Frame entsteht. `ShakeSensor` nutzt `TYPE_LINEAR_ACCELERATION` und ist an den Compose-Lifecycle gebunden. Schütteln löst DIZZY für 3,6 s aus, dann ANGRY für 2,2 s, plus Vibration.
- [ ] **Step 4:** Roborazzi-Screenshot-Tests für alle sieben Stimmungen anlegen. Ohne Vergleichsbilder nur als Aufnahme-Task.
- [ ] **Step 5:** Tests ausführen. Erwartet: PASS.
- [ ] **Step 6:** Commit `feat: LOOI-Augen und Schütteln`.

## Phase 3: llama.cpp-Build

**Files:** Create: `scripts/build-llama.sh`, `third_party/llama.cpp` (Submodul, Tag `b12032`, Commit `dde879bc4f954b809892fd0d660515f42536394a`). Modify: `.gitignore`, `app/build.gradle.kts`, `.github/workflows/android.yml` nur falls der Cache-Schlüssel nicht passt.

- [ ] **Step 1:** `scripts/build-llama.sh` schreiben. Es baut mit diesen Flags und nur dem Target `llama-server`:
```bash
cmake -S third_party/llama.cpp -B build/llama -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
  -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON \
  -DGGML_NATIVE=OFF -DGGML_BACKEND_DL=ON -DGGML_CPU_ALL_VARIANTS=ON \
  -DGGML_OPENMP=OFF -DGGML_LLAMAFILE=OFF -DGGML_CPU_KLEIDIAI=ON \
  -DLLAMA_OPENSSL=OFF -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF
```
- [ ] **Step 2:** Das Skript kopiert `llama-server` als `libllama-server.so`, die benötigten `lib*.so` und alle `libggml-cpu-android_*.so` nach `app/src/main/jniLibs/arm64-v8a/` und strippt sie mit `llvm-strip` aus dem NDK. `jniLibs/` steht in `.gitignore`.
- [ ] **Step 3:** In `app/build.gradle.kts` `packaging { jniLibs { useLegacyPackaging = true } }` setzen.
- [ ] **Step 4:** `bash scripts/build-llama.sh && ./gradlew --no-daemon assembleDebug` ausführen. Erwartet: APK enthält `lib/arm64-v8a/libllama-server.so` (Prüfung mit `unzip -l`). Scheitert der Build auf der Jules-VM an Zeit oder Speicher, ist GitHub Actions die maßgebliche Prüfung. Notiere das in `docs/DECISIONS.md`.
- [ ] **Step 5:** Falls der Quellbau auch auf CI scheitert: Prebuilt `llama-b12032-bin-android-arm64.tar.gz` mit SHA-256 `25393a12f1adce8fb44fa313453ea1c23786a81c2b41815c809056ac58bfe802` im Skript laden und prüfen.
- [ ] **Step 6:** Commit `build: llama-server für arm64-v8a`.

## Phase 4: Modell-Import und LLM-Dienst

**Files:** Create: `app/src/main/assets/models.json`, `.../llm/ModelCatalog.kt`, `.../llm/ModelImporter.kt`, `.../llm/LlmClient.kt`, `.../llm/LlmServerService.kt`, `.../llm/ServerSupervisor.kt`, `app/src/main/res/xml/network_security_config.xml`, Tests zu jeder Klasse unter `app/src/test/.../llm/`

**Interfaces (Produces):**
```kotlin
data class ModelEntry(val id: String, val file: String, val bytes: Long, val sha256: String)
sealed interface ImportResult { data class Ok(val path: String) : ImportResult; data class Failed(val reason: String) : ImportResult }
class ModelImporter(private val targetDir: File) {
  suspend fun import(source: InputStream, entry: ModelEntry): ImportResult
}
class ServerSupervisor(private val launch: () -> Process, private val sleep: suspend (Long) -> Unit) {
  fun nextBackoffMs(failures: Int): Long
}
interface LlmClient {
  fun chat(messages: List<ChatMessage>, tools: List<ToolSpec> = emptyList()): Flow<LlmEvent>
}
sealed interface LlmEvent {
  data class Token(val text: String) : LlmEvent
  data class ToolCall(val name: String, val argumentsJson: String) : LlmEvent
  data class Failed(val message: String) : LlmEvent
  object Done : LlmEvent
}
```

`models.json` enthält genau diese Einträge. Standard ist Hermes-3, weil nur dessen natives Tool-Format belegt ist.

| id | Datei | Bytes | SHA-256 |
|---|---|---|---|
| `hermes3` (Standard) | `Hermes-3-Llama-3.2-3B-Q4_K_M.gguf` | 2019374176 | `2e220a14ba4328fee38cf36c2c068261560f999fadb5725ce5c6d977cb5126b5` |
| `qwen35-2b` | `Qwen3.5-2B-Q4_K_M.gguf` | 1280835840 | `aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223` |
| `qwen35-08b` | `Qwen3.5-0.8B-Q4_K_M.gguf` | 532517120 | `bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517` |

- [ ] **Step 1:** Tests schreiben: `import_writesPartThenRenames`, `import_shaMismatch_deletesPartAndFails` (kein Zielfile, `Failed`), `import_truncatedStream_leavesNoTarget`, `import_retryAfterFailure_succeeds`, `backoff_is1_2_4_8_thenCapped30s`, `client_parsesStreamedTokens`, `client_parsesToolCall`, `client_stripsThinkBlocks`, `client_serverDiesMidStream_emitsFailedNotHang` (Review Focus 1), `networkConfig_allowsOnlyLoopback`.
- [ ] **Step 2:** Tests ausführen. Erwartet: FAIL.
- [ ] **Step 3:** `ModelImporter` schreibt nach `*.part`, prüft SHA-256 beim Streamen, benennt atomar um. Quellen sind der System-Dateiwähler (`ACTION_OPEN_DOCUMENT`, immer verfügbar) und `getExternalFilesDir("models")` für `adb push`.
- [ ] **Step 4:** `LlmServerService` ist ein Foreground Service vom Typ `dataSync`, gestartet aus der sichtbaren Activity. Es startet `applicationInfo.nativeLibraryDir/libllama-server.so` mit `LD_LIBRARY_PATH=nativeLibraryDir` und diesen Argumenten:
```text
-m <modell> --host 127.0.0.1 --port 8080 --api-key-file <datei> --no-webui
-c 4096 -np 1 -t 4 -tb 4 -cram 256 -ctxcp 2 --no-mmproj --jinja
```
Der API-Key wird pro Installation zufällig erzeugt. Der Dienst fragt `/health` ab, beendet beim Start verwaiste Server und startet nach einem Absturz mit Backoff neu.
- [ ] **Step 5:** Tests ausführen. Erwartet: PASS.
- [ ] **Step 6:** Commit `feat: Modell-Import und lokaler LLM-Dienst`.

## Phase 5: Offline-Sprache

**Files:** Create: `.../speech/SpeechController.kt`, `.../speech/VoskRecognizer.kt`, `.../speech/PiperSpeaker.kt`, `.../speech/IntentGrammar.kt`, `app/src/main/assets/speech.json`, Tests unter `app/src/test/.../speech/`

**Interfaces (Produces):**
```kotlin
interface Recognizer { fun start(grammar: List<String>?); fun stop(); val results: Flow<String> }
interface Speaker { suspend fun speak(text: String) }
sealed interface VoiceIntent { object UnlockPhrase : VoiceIntent; object EnterKidMode : VoiceIntent; data class Free(val text: String) : VoiceIntent; object Ignored : VoiceIntent }
class SpeechController(private val rec: Recognizer, private val tts: Speaker) {
  suspend fun say(text: String)
}
fun classify(transcript: String, kidMode: Boolean): VoiceIntent
```

Dependencies: `com.alphacephei:vosk-android:0.3.75`, sherpa-onnx-AAR der neuesten 1.13.x-Version aus den GitHub-Releases nach `app/libs/` per Skript `scripts/fetch-speech-aar.sh`. Modelle: `vosk-model-small-de-0.15`, `vits-piper-de_DE-thorsten-medium`, beide über denselben Import wie Phase 4. Ihre Größen und SHA-256 stehen noch nicht fest: Lade beide Archive einmalig, trage die Werte in `speech.json` ein und committe die Archive nicht.

- [ ] **Step 1:** Tests schreiben: `classify_kidMode_onlyUnlockPhraseCounts`, `classify_unlock_matchesSeparatedWords` („agenten modus aktivieren“ zählt ebenfalls), `classify_unk_isIgnored`, `say_pausesRecognizerAndResumes` (Mikro ist während der Ausgabe aus), `say_resumesRecognizerWhenTtsThrows`.
- [ ] **Step 2:** Tests ausführen. Erwartet: FAIL.
- [ ] **Step 3:** `VoskRecognizer` nutzt eine Modellinstanz mit Grammatik `["agentenmodus aktivieren", "agenten modus aktivieren", "[unk]"]` im Kindermodus und freiem Vokabular im Agentenmodus. Das Mikro läuft nur bei sichtbarer Activity. `PiperSpeaker` nutzt sherpa-onnx mit `espeak-ng-data`. Die Ausgabe ist halbduplex, Unterbrechen beim Sprechen gibt es nicht.
- [ ] **Step 4:** Tests ausführen. Erwartet: PASS.
- [ ] **Step 5:** Commit `feat: Offline-Sprache auf Deutsch`.

## Phase 6: Kindermodus-Sperre

**Files:** Create: `.../kid/KidLockPolicy.kt`, `.../kid/PinStore.kt`, `.../kid/CrashGuard.kt`, `.../kid/CompanionAdminReceiver.kt`, `.../kid/LockController.kt`, `.../ui/PinPad.kt`, `app/src/main/res/xml/device_admin.xml`, `docs/DEVICE_SETUP.md`, Tests unter `app/src/test/.../kid/`

**Interfaces (Produces):**
```kotlin
class PinStore(private val prefs: KeyValue) { fun set(pin: String); fun verify(pin: String, nowMs: Long): PinResult }
sealed interface PinResult { object Ok : PinResult; object Wrong : PinResult; data class Locked(val untilMs: Long) : PinResult }
class CrashGuard(private val prefs: KeyValue, val maxCrashes: Int = 3, val windowMs: Long = 60_000) {
  fun recordStart(nowMs: Long); fun inSafeMode(nowMs: Long): Boolean
}
class KidLockPolicy(private val pinRequiredAfterPhrase: Boolean = true) {
  fun onPhrase(): UnlockStep
  fun toolsAllowed(mode: AppMode): Boolean
}
enum class UnlockStep { SHOW_PIN, UNLOCK }
```

- [ ] **Step 1:** Tests schreiben: `pin_storedAsSaltedHash`, `pin_lockoutAfter5Wrong_30s`, `policy_phraseShowsPinByDefault`, `policy_voiceOnlyWhenEnabled`, `policy_kidLocked_blocksAllTools`, `crashGuard_entersSafeModeAfter3In60s`, `crashGuard_safeMode_keepsLockActive`, `policy_pinWorksWithoutRecognizer` (Review Focus 3 und 4).
- [ ] **Step 2:** Tests ausführen. Erwartet: FAIL.
- [ ] **Step 3:** `LockController` wählt die Stufe: Mit Device Owner ruft er `setLockTaskPackages` und `startLockTask` auf, die Activity nutzt `android:lockTaskMode="if_whitelisted"` und ist HOME-Activity. Ohne Device Owner nutzt er `startLockTask` als Bildschirmfixierung und zeigt im Setup den Hinweis auf „Zum Lösen Bildschirmsperre verlangen“.
- [ ] **Step 4:** Ausweg umsetzen: Eine versteckte Geste (5 Sekunden in die obere linke Ecke halten) öffnet die PIN-Eingabe, darüber auch `clearDeviceOwnerApp()`. `DISALLOW_DEBUGGING_FEATURES` darf nie gesetzt werden. Einstieg in den Kindermodus nur mit gesetzter PIN.
- [ ] **Step 5:** `docs/DEVICE_SETUP.md` schreiben: PIN setzen, `adb shell dpm set-device-owner dev.s10e.companion/.kid.CompanionAdminReceiver` (nur ohne Konten auf dem Gerät), Rückweg, Akku-Ausnahmen in der Gerätewartung.
- [ ] **Step 6:** Tests ausführen. Erwartet: PASS.
- [ ] **Step 7:** Commit `feat: Kindermodus-Sperre mit PIN`.

## Phase 7: Apollo-Terminal und Obsidian-Kanban

**Files:** Create: `.../vault/KanbanCodec.kt`, `.../vault/VaultStore.kt`, `.../ui/ApolloTerminal.kt`, `.../ui/KanbanBoard.kt`, Tests unter `app/src/test/.../vault/`

**Interfaces (Produces):**
```kotlin
typealias Board = LinkedHashMap<String, List<String>>
object KanbanCodec { fun parse(md: String): Board; fun serialize(board: Board): String }
sealed interface SaveResult { object Saved : SaveResult; data class Conflict(val merged: Board) : SaveResult }
class VaultStore(private val root: File) {
  fun load(): Pair<Board, Long>
  fun save(board: Board, baseMtimeMs: Long): SaveResult
  fun appendInbox(text: String, nowMs: Long)
  fun ensureLayout()
}
```
Vault-Pfad: `/sdcard/Documents/ObsidianVault/` mit `Inbox.md`, `Kanban.md`, `Daily/`. Zugriff über `MANAGE_EXTERNAL_STORAGE`, die App führt zur Systemeinstellung.

- [ ] **Step 1:** Tests schreiben: `codec_roundTrip`, `codec_doneColumnsUseCheckedBoxes` (Spalten „done“, „completed“, „erledigt“), `codec_keepsUnknownLinesOutsideTasks`, `save_detectsExternalChange_returnsConflictAndKeepsTheirs` (Review Focus 5), `save_writesAtomically`, `ensureLayout_doesNotOverwriteExisting`.
- [ ] **Step 2:** Tests ausführen. Erwartet: FAIL.
- [ ] **Step 3:** Implementieren. Bei Konflikt lädt die App neu und führt die neuen Karten zusammen. Sie überschreibt die fremde Fassung nie. Das Terminal zeigt Board, Eingabezeile, Spracheingabe und den LLM-Stream aus Phase 4. „Notiz …“ und „Aufgabe …“ landen in `Inbox.md` und in der ersten Spalte.
- [ ] **Step 4:** Tests ausführen. Erwartet: PASS.
- [ ] **Step 5:** Commit `feat: Apollo-Terminal und Obsidian-Kanban`.

## Phase 8: Agenten-Werkzeuge und Handysteuerung

**Files:** Create: `.../agent/ToolDispatcher.kt`, `.../agent/AgentLoop.kt`, `.../agent/PhoneControlService.kt`, `.../agent/AuditLog.kt`, `app/src/main/res/xml/accessibility_service.xml`, Tests unter `app/src/test/.../agent/`

**Interfaces (Produces):**
```kotlin
enum class Risk { READ, ACT }
data class Tool(val name: String, val risk: Risk, val spec: ToolSpec, val run: suspend (JsonObject) -> String)
class ToolDispatcher(tools: List<Tool>, private val confirm: suspend (String) -> Boolean, private val allowed: () -> Boolean) {
  suspend fun dispatch(name: String, argumentsJson: String): ToolResult
}
```
Werkzeuge: `read_screen`, `tap`, `swipe`, `type_text`, `open_app`, `add_card`, `move_card`, `add_note`. `read_screen` und die Vault-Werkzeuge sind nach Freigabe des Dienstes direkt erlaubt, alles andere (`ACT`) braucht eine Bestätigung pro Aktion.

- [ ] **Step 1:** Tests schreiben: `dispatch_unknownTool_rejected`, `dispatch_kidMode_rejectsEverything`, `dispatch_actNeedsConfirmation`, `dispatch_badJson_returnsErrorToModel`, `dispatch_killSwitch_stopsLoop`, `agentLoop_stopsAfter8Steps`, `audit_recordsEveryCall`.
- [ ] **Step 2:** Tests ausführen. Erwartet: FAIL.
- [ ] **Step 3:** `PhoneControlService` ist ein AccessibilityService mit `canRetrieveWindowContent` und `canPerformGestures`. Der Not-Aus liegt außerhalb der LLM-Logik: ein dauerhaft sichtbarer Button im Terminal plus Benachrichtigungsaktion, beide stoppen die Schleife direkt im Dispatcher.
- [ ] **Step 4:** Tests ausführen. Erwartet: PASS.
- [ ] **Step 5:** Commit `feat: Agenten-Werkzeuge mit Allowlist und Not-Aus`.

## Phase 9: Präsenz und Plugins

**Files:** Create: `.../presence/MotionDetector.kt`, `.../presence/CameraPresence.kt`, `.../plugins/PluginStore.kt`, `.../plugins/PluginPatcher.kt`, `.../plugins/OpenCodeClient.kt`, `.../ui/PluginPanel.kt`, Tests daneben unter `app/src/test/`

**Interfaces (Produces):**
```kotlin
data class Motion(val centerX: Float, val centerY: Float, val pixels: Int)
class MotionDetector(val pixelDelta: Int = 55, val minPixels: Int = 18) { fun onFrame(luma: ByteArray, w: Int, h: Int): Motion? }
class PluginPatcher(private val store: PluginStore) {
  fun patch(file: String, search: String, replace: String): PatchResult
  fun rollback(): PatchResult
}
```

- [ ] **Step 1:** Tests schreiben: `motion_firstFrameReturnsNull`, `motion_belowThreshold_null`, `motion_centerMirrorsForFrontCamera`, `patch_missingSearchBlock_fails`, `patch_snapshotsBeforeWrite`, `rollback_restoresPrevious`, `patch_rejectsPathOutsidePluginDir`, `patch_blockedInKidMode`.
- [ ] **Step 2:** Tests ausführen. Erwartet: FAIL.
- [ ] **Step 3:** `CameraPresence` nutzt CameraX `ImageAnalysis` mit 160×120, `STRATEGY_KEEP_ONLY_LATEST` und etwa 4,5 Analysen pro Sekunde, Bilder werden weder gespeichert noch gesendet. Das Display geht nach 10 Minuten ohne Präsenz auf ein fast schwarzes Schlafgesicht, weil Pixel-Shift allein Einbrennen bei großen hellen Flächen nicht verhindert.
- [ ] **Step 4:** Plugins liegen in `filesDir/plugins/` und laufen im WebView ohne Netz (`setBlockNetworkLoads(true)`). `OpenCodeClient` spricht ein optionales `opencode serve` auf Loopback mit Passwort an und zeigt ohne Server nur „nicht verbunden“.
- [ ] **Step 5:** Tests ausführen. Erwartet: PASS.
- [ ] **Step 6:** Commit `feat: Kamera-Präsenz und Plugin-Patching`.

## Phase 10: Release und Installation

**Files:** Create: `scripts/build-all.sh`, `scripts/install.sh`, `scripts/push-models.sh`, `docs/DEVICE_TEST.md`. Modify: `app/build.gradle.kts`, `README.md`, `.github/workflows/android.yml`

- [ ] **Step 1:** `scripts/build-all.sh` führt Submodul-Init, `build-llama.sh`, Tests und `assembleRelease` aus. Signiert wird mit einem Keystore außerhalb des Repos über `S10E_KEYSTORE`, `S10E_KEYSTORE_PASSWORD`, `S10E_KEY_ALIAS`. Fehlen die Variablen, nutzt das Skript den Debug-Key und sagt das deutlich.
- [ ] **Step 2:** `scripts/install.sh` prüft `adb devices`, führt `adb install -r` aus und startet `dev.s10e.companion/.MainActivity`. `scripts/push-models.sh` schiebt Modelle nach `/sdcard/Android/data/dev.s10e.companion/files/models/` und fällt auf `/sdcard/Download/` plus Import zurück.
- [ ] **Step 3:** `docs/DEVICE_TEST.md` listet die Gerätetests, die Jules nicht ausführen kann: CPU-Variante im Log, Tokens pro Sekunde, RSS, Hitze nach 30 Minuten, Phrase im Vosk-Vokabular, Piper-Latenz, Fehlauslösungen pro Stunde, Akkuverbrauch, Sperre gegen Home, Zurück, Seitentaste und Bixby-Taste.
- [ ] **Step 4:** `bash -n scripts/*.sh` und `./gradlew --no-daemon testDebugUnitTest assembleDebug` ausführen. Erwartet: keine Fehler, `BUILD SUCCESSFUL`.
- [ ] **Step 5:** Commit `build: Release-Skripte und Gerätetest-Liste`.
