# Spezifikation: S10e Companion

Vorgabe des Auftraggebers vom 2026-11-01. Der Web-Ansatz (`bot.html`, FastAPI-Backend) ist verworfen.

## Ziel

Eine vollwertige, native Android-APK für das Samsung Galaxy S10e (SM-G970F, Exynos 9820, 6 GB RAM,
Android 12, ohne Root). Das System läuft zu 100 % lokal und offline auf dem Smartphone, ohne Cloud-Zwang.

## Anforderungen

### 1. Native lokale LLM-Integration (GGUF on-device)
- `llama.cpp` wird von GitHub eingebunden und für `arm64-v8a` kompiliert.
- Ein kompaktes quantisiertes Modell (z. B. `Hermes-3-Llama-3.2-3B.Q4_K_M.gguf`) liegt im App-Speicher
  und wird über einen nativen Localhost-HTTP-Server auf Port 8080 bereitgestellt.
- Das Modell kommt per ADB-Push oder Download aufs Gerät. Das Handy hat derzeit kein Netz.

### 2. UI und Dual-Modus
- Native UI mit Kotlin und Jetpack Compose.
- **Passiver Modus (LOOI-Augen):** nativer Canvas-Renderer mit 60 FPS, reagiert auf Schütteln
  (`SensorManager`) und Touch. Nach 30 Sekunden Inaktivität schaltet die App automatisch in diesen
  AMOLED-Vollbildmodus. Stimmungen wie im Prototyp: neutral, happy, angry, listening, thinking, dizzy, sleepy.
  Frontkamera-Präsenz weckt den Bot, die Augen folgen der Bewegung.
- **Kindermodus Hard-Lock:** Sobald aktiviert, sperrt die App alle Terminal- und Agenten-Funktionen.
  Nur das LOOI-Gesicht bleibt aktiv, bis der Sprachbefehl „Agentenmodus aktivieren“ erkannt wird.
- **Aktiver Modus (Apollo Terminal):** Entwickler- und Agenten-Dashboard mit Obsidian-Markdown-Kanban,
  integriertem OpenCode-Patching und Handysteuerung.

### 3. Build
- Ein automatisiertes Build-Skript klont die nötigen Repositories, kompiliert den C++-Inferenz-Code für
  `arm64-v8a`, baut und signiert die App und gibt eine `.apk` aus.
- Die fertige APK wird per `adb install -r` auf dem angeschlossenen S10e installiert und gestartet.

### 4. Obsidian
- Vault unter `/sdcard/Documents/ObsidianVault/` mit `Inbox.md`, `Kanban.md`, `Daily/`.
- Das Kanban-Board liest und schreibt diese Markdown-Dateien direkt.
