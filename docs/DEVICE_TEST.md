# Device Tests

Diese Gerätetests können nicht von Jules (im Sandbox-Environment) ausgeführt werden und müssen manuell auf dem echten Gerät (Samsung Galaxy S10e) geprüft werden:

- CPU-Variante im Log
- Tokens pro Sekunde
- RSS
- Hitze nach 30 Minuten
- Phrase im Vosk-Vokabular
- Piper-Latenz
- Fehlauslösungen pro Stunde
- Akkuverbrauch
- Sperre gegen Home, Zurück, Seitentaste und Bixby-Taste

## Zusätzliche Tests

- Sprechen von "agenten modus aktivieren" entsperrt das PIN-Pad.
- Sprechen von "kinder modus aktivieren" betritt den Kindermodus.
- Piper liest eine Antwort laut vor.
- Schütteln zeigt DIZZY, dann ANGRY.
- Der Not-Aus (Benachrichtigungsaktion) stoppt den Agenten.
- Aktivierung des Accessibility Service funktioniert fehlerfrei.

**Hinweis:** Die App benötigt die Berechtigung `MANAGE_EXTERNAL_STORAGE` (in den Systemeinstellungen gewährt), um auf einen existierenden Obsidian Vault zugreifen zu können.
