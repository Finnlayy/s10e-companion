# AGENTS.md

Native Android-App für das Samsung Galaxy S10e. Offline, Kotlin, Jetpack Compose, llama.cpp.

## Arbeitsweise

- Lies `docs/PLAN.md` und `docs/SPEC.md`. Belege stehen in `docs/research/`.
- Bearbeite in einer Session genau die genannte Phase, nicht die nächste.
- Führe zu Beginn jeder Session `bash scripts/jules-setup.sh` aus, falls `$HOME/android-sdk` fehlt.
- Stelle keine Rückfragen und warte nicht auf Antworten. Halte Annahmen in `docs/DECISIONS.md` fest und mach weiter.
- Arbeite testgetrieben: erst der fehlschlagende Test, dann der Code.
- Reine Logik bleibt frei von Android-Importen, damit sie als JVM-Test läuft.
- Ändere keine Versionen aus dem Plan ohne Eintrag in `docs/DECISIONS.md`.

## Befehle

```bash
export ANDROID_HOME="$HOME/android-sdk"
./gradlew --no-daemon testDebugUnitTest assembleDebug
bash scripts/build-llama.sh
```

## Verbote

- Keine Modelle, APKs, Keystores, `local.properties` oder Dateien über 50 MiB committen.
- `.github/workflows/` nur ändern, wenn die Phase es verlangt.
- Keine Netzwerkaufrufe außer an `127.0.0.1`.
- Keine rohe Shell für den Agenten, kein `DISALLOW_DEBUGGING_FEATURES`.
- Keine Emulator- oder instrumentierten Tests als Abnahmekriterium.

<!-- UNIVERSAL_JULES_AGENT_PACK_START -->
## Universal Jules Agent Pack

- **Code Reviewer:** Review code for bugs, logic errors, adherence to conventions, and performance. Ensure tests cover edge cases. Provide actionable feedback.
- **Minimal Change Engineer:** Implement requested features or fixes with the absolute minimum number of changes required. Avoid refactoring unrelated code.
- **Git Workflow Master:** Manage branches, commits, and pull requests according to best practices. Write clear, conventional commit messages.
- **Backend Architect:** Design scalable, secure, and maintainable backend systems. Focus on data modeling, API design, and system architecture.
- **DevOps Automator:** Automate build, test, and deployment pipelines. Manage infrastructure as code and ensure CI/CD workflows run smoothly.
- **Reality Checker:** Verify assumptions, check for edge cases, and ensure the proposed solution is practical and feasible within the constraints.
- **Test Results Analyzer:** Analyze test failures, identify root causes, and provide clear explanations and fixes for failing tests.
- **Performance Benchmarker:** Profile code, identify performance bottlenecks, and optimize resource usage (CPU, memory, network).
<!-- UNIVERSAL_JULES_AGENT_PACK_END -->
