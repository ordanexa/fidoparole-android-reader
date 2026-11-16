# Gerätetest – 0.1.2-test

## Vorbereitung

- Einen bereits unter Windows lesbaren FidoParole-Test-Vault mit Dummy-Passwort verwenden.
- Backup / zweiter Token vorhanden; PIN ist bekannt. Kein Reset und keine PIN-Änderung für diesen Test.
- Windows/RDP darf den Token während des Tests nicht geöffnet haben.

## Ablauf

1. APK auf Android installieren. Falls Android nachfragt, Installation für die verwendete Download-App erlauben; Berechtigung danach wieder deaktivieren.
2. Token2 direkt per USB-C anschließen und **FidoParole Reader** öffnen.
3. **Security Key verbinden** wählen; Android USB Permission bestätigen.
4. FIDO2 PIN eingeben und **Entsperren** wählen.
5. Token berühren, wenn er blinkt. Erwartet: Vault-/Eintragsnamen, keine angezeigten Passwörter.
6. **Passwort kopieren**, in eine geeignete lokale Test-App einfügen und mit dem bekannten Dummy-Passwort vergleichen. Keine echten Secrets in Chat senden.
7. Reader erneut öffnen: Er muss gesperrt sein. Nach Ablauf der Frist beim Rückkehren prüfen, ob der eigene Clipboard-Eintrag gelöscht wurde.
8. Erneut entsperren, Token abziehen: Einträge müssen verschwinden.
9. Erneut entsperren und 60 Sekunden warten: automatische Sperre prüfen.
10. Nach dem Test unter Windows öffnen und prüfen, dass Einträge unverändert sind.

## Rückmeldung

- Gerät: S24 / Tab S9; Android-Version.
- USB Permission angeboten und bestätigt?
- Letzter Status in der App und exakter Fehlercode (z. B. CTAP 0x33).
- Blinkt der Token, und wurde er berührt?
- Bei Erfolg: Anzahl Vaults/Einträge und ob Dummy-Passwort korrekt kopiert wurde.

PIN, Master Passwords und sensible Eintragsnamen nicht übermitteln. Bei PIN-Fehler nicht mehrfach raten; zuerst mit dem bekannten funktionierenden Windows-Weg prüfen.

## Update-Test: Passwort anzeigen

0.1.0-test wurde vom Nutzer am 14.11.2026 als auf Hardware funktionierend bestätigt. Für 0.1.1-test:

- APK über die bestehende Installation installieren (gleicher Signing-Key).
- Nach Unlock bleibt das Passwort verborgen. **Passwort anzeigen** zeigt den Dummy-Wert, **Passwort ausblenden** entfernt ihn wieder.
- Kopieren weiterhin separat prüfen.
- Bei sichtbarem Passwort zur anderen App wechseln bzw. Token abziehen: Reader muss sperren. Nach erneutem Unlock bleibt die Anzeige verborgen.

## UI-Test 0.1.2-test

- Update über 0.1.0/0.1.1 installieren: Launcher-Name FidoParole Reader und originales pinkes Icon prüfen.
- Helle Ansicht, lesbarer Token-Name, keine abgeschnittenen Buttons auf Telefon/Tablet.
- Nach Unlock: PIN-Karte wird ausgeblendet, Einträge mit **Anzeigen** und **Kopieren** erscheinen; **Sperren** stellt die Unlock-Ansicht wieder her.
- Tastatur/Statusbar dürfen Inhalte nicht verdecken; Hoch-/Querformat prüfen.
- Anzeigen/Ausblenden, Kopieren und Sperren mit Dummy-Passwort prüfen.
