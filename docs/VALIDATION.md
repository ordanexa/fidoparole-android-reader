# Validation – 0.1.2-test

Stand: 14.11.2026. Basisfunktion von 0.1.0-test auf Hardware vom Nutzer bestätigt. 0.1.1-test ergänzt Anzeigen/Ausblenden.

## Bereits geprüfte Basis (0.1.0-test)

- Java-Compile mit JDK 21; Android API 35; DEX für min API 33.
- APK erzeugt und mit temporärem RSA-3072 Test-Key signiert.
- `apksigner verify`: gültige APK Signature Scheme v3 Signatur.
- `zipalign -c`: bestanden.
- Manifest geprüft: keine angeforderten Permissions, USB Host Feature, Backup aus, keine Internet-Permission.
- 154 Host-Prüfungen mit unabhängigem Python-Simulator bestanden:
  - PIN Protocol 1 und 2 einschließlich ECDH, PIN-Hash-Verschlüsselung und Auth-Parameter.
  - PRF-Salt / hmac-secret, zwei Assertions und korrekte GetNextAssertion-Reihenfolge.
  - HID-Request-/Response-Fragmentierung und Keepalive-Verarbeitung.
  - largeBlob-Reads in mehreren Fragmenten und Auswahl anhand AES-GCM-Authentifizierung.
  - T2VLT-Entschlüsselung und JSON Format 1 / 2 gegen separat erzeugte Daten.
  - Falsche PIN ohne automatische Wiederholung; niedriger Retry-Counter blockiert.
  - Fehlendes UV-Flag, falsche Checksum, manipuliertes Vault-Ciphertext und falsches hmac-secret werden abgewiesen.
  - CBOR-Limits, doppelte Keys, unzulässige Daten und CTAP-Schreibbefehle werden abgewiesen.
  - Übergebener PIN-Buffer nach Erfolg und Fehler geleert.

## Hardware-Rückmeldung

Der Nutzer bestätigt am 14.11.2026 ausdrücklich, dass die App funktioniert. Der Basisablauf über USB mit dem vorhandenen Token ist damit nutzerseitig bestätigt. Für 0.1.2-test bestätigt der Nutzer erfolgreiche Tests auf Samsung S24 und Tablet. Android-Version und einzelne Lifecycle-Prüfungen wurden nicht separat benannt.

## Noch nicht einzeln nachgewiesen
- Copy/Paste, Sperre bei Hintergrundwechsel/Abziehen, Clipboard-Cleanup auf Samsung Android.
- Formelles Security Review oder vollständige Speicherauslöschung.

Die Simulator-Prüfungen ersetzen diese offenen Tests nicht. Die offenen Lifecycle-Prüfungen mit einem Dummy-Passwort durchführen; kein vollständiges Security Review aus dem funktionierenden Basisablauf ableiten.

## Prüfung 0.1.1-test

- Build erfolgreich; APK-Signatur und ZIP-Alignment geprüft.
- Version Code 2 und dieselbe Signing-Zertifikat-SHA256 wie 0.1.0-test.
- UI-Code geprüft: Text initial leer/verborgen; Ausblenden leert Text; Lock leert alle Passwort-Views vor deren Entfernung. FLAG_SECURE bleibt aktiv.
- USB-/CTAP-/Crypto-Code unverändert. Die 154 bisherigen Protokoll-Prüfungen beziehen sich auf diese unveränderte Basis.
- Neuer Show/Hide-Ablauf noch nicht auf Android ausgeführt.

## Prüfung 0.1.2-test

- Resource-Compile/Link, Java-Compile, DEX und APK-Build erfolgreich.
- APK Signature Scheme v3 und ZIP-Alignment geprüft; identisches Signing-Zertifikat, Version Code 3.
- Manifest-Ausgabe bestätigt Label **FidoParole Reader** und adaptives Launcher-Icon.
- Icon-Datei unverändert aus dem geprüften Upstream-Commit übernommen.
- Diff geprüft: keine Änderungen an USB-Transport, CTAP- oder Crypto-Kern.
- UI-Code enthält weiterhin FLAG_SECURE, Clear-on-Lock und deaktiviertes Autofill für PIN/Passwort.
- Nutzer bestätigt am 14.11.2026 erfolgreiche Tests der neuen Version auf Samsung S24 und Tablet. Keine separate Bestätigung aller Lifecycle-Testfälle.
