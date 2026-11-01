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
- Versionen im Plan sind am 2026-11-01 geprüft. Du darfst sie nicht wegen deines Trainingswissens als nicht existent einstufen oder herabsetzen. Prüfe im Zweifel die Registry mit curl.

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
