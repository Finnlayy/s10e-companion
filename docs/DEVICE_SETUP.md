# Device Setup (Phase 6)

1. PIN in den Android-Einstellungen setzen.
2. `adb shell dpm set-device-owner dev.s10e.companion/.kid.CompanionAdminReceiver` (nur ohne Konten auf dem Gerät).
3. Rückweg: Über die App (5 Sekunden oben links) PIN eingeben und `clearDeviceOwnerApp()` auslösen, falls nötig.
4. Akku-Ausnahmen in der Gerätewartung hinzufügen, damit die App nicht gekillt wird.
