# Device Setup (Phase 6)

1. Eine Bildschirmsperre in den Android-Einstellungen setzen und unter „App-Fixierung“ das Entsperren beim Lösen einschalten. Die App-PIN entsteht beim ersten Einstieg in den Kindermodus.
2. `adb shell dpm set-device-owner dev.s10e.companion/.kid.CompanionAdminReceiver` (nur ohne Konten auf dem Gerät).
3. Rückweg: In der App 5 Sekunden oben links halten, PIN eingeben und „Gerätebesitz aufheben“ wählen. Ohne diesen Schritt lässt sich die App als Device Owner nicht deinstallieren.
4. Akku-Ausnahmen in der Gerätewartung hinzufügen, damit die App nicht gekillt wird.
