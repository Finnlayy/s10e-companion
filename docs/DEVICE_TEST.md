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

## Stand der Messung am 2026-11-02

Gemessen auf SM-G970F mit Android 12, Hermes-3 3B Q4 und `--no-repack`:

| Punkt | Ergebnis |
|---|---|
| Modellstart | 4 s, 2,5 GB RSS, 2,5 GB frei mit Sprache und Kamera |
| Tempo | 5,5 Token/s, Prompt 10 Token/s |
| Kurze Frage | etwa 7 s bis zur vollständigen Antwort |
| Werkzeugwahl per Schema | etwa 20 s |
| Kaltstarts | 8 von 8 ohne Absturz |

Noch nicht geprüft sind alle Punkte, die Sprache, Lautsprecher, Schütteln, Dauerlast, Device Owner oder die aktivierte Bedienungshilfe brauchen.
