# S10e Companion

Native Android-App für das Samsung Galaxy S10e: LOOI-Augen, Kindermodus, lokales LLM und Apollo-Terminal, vollständig offline.

- Vorgabe: [docs/SPEC.md](docs/SPEC.md)
- Umsetzungsplan: [docs/PLAN.md](docs/PLAN.md)
- Recherche: [docs/research/](docs/research/)

## Bauen und installieren

```bash
bash scripts/build-all.sh
bash scripts/install.sh
bash scripts/push-models.sh ../models
```

`install.sh` nimmt optional den Pfad einer APK, etwa aus dem CI-Artefakt. `push-models.sh` prüft Größe und SHA-256 jedes Modells, bevor es auf das Gerät geht. Die App importiert die Modelle beim nächsten Start.

## Fester Debug-Schlüssel

Jeder CI-Lauf signiert sonst mit einem neuen Schlüssel, Updates scheitern dann mit `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, und nur eine Neuinstallation hilft, die die Modelle löscht. Einmalig den Keystore außerhalb des Repos als Secret hinterlegen:

```bash
base64 -w0 ../keystore/debug.keystore | gh secret set DEBUG_KEYSTORE_B64 --repo Finnlayy/s10e-companion
```

## Einrichtung am Gerät

Siehe [docs/DEVICE_SETUP.md](docs/DEVICE_SETUP.md) für Sperre und Device Owner und [docs/DEVICE_TEST.md](docs/DEVICE_TEST.md) für die Gerätetests.
