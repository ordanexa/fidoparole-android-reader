# FidoParole Reader

Privates Ordanexa-Projekt. Kleine native Android-App zum **Lesen bestehender FidoParole-Vaults über USB-C** und zum Anzeigen und Kopieren der Passwörter. Dieser Umfang ist das endgültige Produktziel; kein Ausbau zu einem vollständigen Password Manager.

## Status: 0.1.2-test (14.11.2026)

- Installierbare, signierte APK gebaut; Paket `com.ordanexa.fidoreader`.
- Android 13+ (API 33); 0.1.2-test vom Nutzer auf Samsung S24 und Tablet erfolgreich getestet.
- Unterstütztes Gerät zunächst ausschließlich Token2 USB `349e:0024`.
- Keine Internet-, Storage-, NFC- oder Accessibility-Permission.
- App speichert weder Vault noch PIN oder Master Passwords auf Disk; Android Backup deaktiviert.
- Nutzer bestätigt am 14.11.2026 auch den erfolgreichen Test von 0.1.2-test auf beiden Geräten. Kein externes Audit.

## Fester Funktionsumfang

1. Token per USB anschließen und Android USB Permission erteilen.
2. FIDO2 PIN eingeben; bei Aufforderung Token berühren.
3. Bestehende Vaults und Einträge lesen.
4. Passwort per Button anzeigen/ausblenden oder kopieren. Nach jedem Unlock zunächst verborgen.
5. Vault bei Hintergrundwechsel, Abziehen des Tokens oder nach 60 Sekunden sperren.

Kein Create, Edit, Delete, Reset, PIN Change, Backup Import/Export, NFC, Sync oder Autofill. Verwaltung und Backup erfolgen weiterhin in Token2 FidoParole unter Windows.

**Read only bezieht sich auf Vault-Inhalte und Credentials.** Authentifizierung kann authenticator-interne Counter und PIN-Retry-Zustände verändern. Eine falsche PIN ist ein echter Fehlversuch. Die App wiederholt PIN-Versuche nicht automatisch und stoppt bei höchstens zwei verbleibenden Versuchen.

## Technik

Native Java Activity und Android USB Host API, ohne WebView, Rust-Runtime oder native Libraries. Android-/Java-Crypto-Provider liefern P-256 ECDH, AES-CBC, HMAC/HKDF-SHA256 und AES-256-GCM. Die App implementiert:

- CTAPHID INIT/CBOR, Fragmentierung, Keepalive und Timeout.
- PIN/UV Protocol 1 und 2; scoped GetAssertion-Permission sofern vom Token unterstützt.
- Discoverable GetAssertion für `fido2-vault.token2`, `hmac-secret` und `largeBlobKey`.
- GetNextAssertion vor allen largeBlob-Lesezugriffen.
- largeBlob-Checksum, AES-GCM/DEFLATE äußere Schicht, anschließend T2VLT v1 und JSON Format 1/2.
- Explizite CTAP-Allowlist; largeBlob SET und Credential-/PIN-Schreibbefehle werden blockiert.

## Build (Linux / WSL)

Voraussetzungen: JDK 21, Android command-line tools, `zip`, Python nur für Tests.

```bash
sdkmanager 'platforms;android-35' 'build-tools;35.0.0'
export ANDROID_HOME=/pfad/zum/android-sdk
./build.sh
```

Ergebnis: `dist/fidoparole-android-reader-0.1.2-test.apk` und `dist/SHA256SUMS`.

Ohne eigene Signing-Variablen erzeugt der Build einen **temporären Test-Key** unter `build/test-signing.jks`. Dieser ist nicht im Repo oder Source-Archiv. Der bekannte Test-Key-Passwortwert ist kein Schutz für Releases. Für dauerhafte private Releases einen eigenen Signing-Key lokal verwalten und sichern:

```bash
export READER_KEYSTORE=/sicherer/pfad/private-reader.jks
export READER_KEY_ALIAS=reader
read -rs -p 'Keystore password: ' READER_STORE_PASS; export READER_STORE_PASS
read -rs -p 'Key password: ' READER_KEY_PASS; export READER_KEY_PASS
./build.sh
```

Ein Update benötigt dieselbe Signing-Identität. Bei einem anderen Key muss der alte Test-Build deinstalliert werden; die App hat keine gespeicherten Vault-Daten. Private Signing-Keys niemals in Git oder outbox ablegen.

## Tests

Der JVM-Test verwendet einen **separaten Python-CTAP-Simulator** mit Yubicos `python-fido2` und Python `cryptography`. Er prüft reale Request-/Response-Bytes einschließlich HID-Fragmentierung und Entschlüsselung unabhängig von der Java-Implementierung. Er ersetzt keinen Android-USB-Test.

```bash
python3 -m venv .venv
. .venv/bin/activate
pip install -r tests/requirements.txt
./test.sh
```

Details: [Testbericht](docs/VALIDATION.md), [Gerätetest](docs/DEVICE_TEST.md), [Referenzen](NOTICE.md).

## Grenzen

- Der Nutzer bestätigt die Basisfunktion auf Hardware. Detaillierte Einzeltests zu Clipboard und automatischer Sperre sind nicht separat dokumentiert.
- Das Clipboard erhält eine Sensitive-Markierung. Die App fordert nach 20 Sekunden eine Löschung ihres eigenen Clips an und prüft bei Rückkehr erneut. Android kann Hintergrundausführung oder Clipboard-Zugriff einschränken; **keine garantierte Löschfrist**. Andere Apps können eingefügte Passwörter behalten.
- Screenshots/Recents werden mit FLAG_SECURE geschützt. Ein kompromittiertes Android-Gerät ist dadurch nicht abgesichert.
- Byte-/Char-Arrays werden soweit möglich geleert. Java/Android/JSON/Clipboard verwenden auch immutable Strings; vollständiges Zeroization ist nicht garantierbar.
- Die Session schließt den USB-Transport nach dem Lesen; Abziehen des Tokens sperrt die angezeigten Einträge über den Android-Broadcast.
- Die App enthält keine Logs mit PIN, Passwort oder CTAP-Dumps.

## Repository und Übergabe

Privates GitHub-Repo: [pascalep/fidoparole-android-reader](https://github.com/pascalep/fidoparole-android-reader). Der getestete Source-Stand 0.1.2-test wird mit Tests und Dokumentation als Baseline übernommen. `infrastructure-toolbox` bleibt unverändert. Raw-Source wird unter `outbox/fidoparole-android-reader/` bereitgestellt; generierte Testartefakte unter `artifacts/` nicht ins Source-Repo übernehmen.

Vor produktiver Verwendung: offene Lifecycle-Tests durchführen, eigenen Signing-Key festlegen und bestehende unabhängige Recovery-Kopien beibehalten.

## Änderung 0.1.1-test

- Pro Eintrag Anzeigen/Ausblenden ergänzt; Kopieren bleibt separat.
- Sichtbarer Passworttext wird beim Ausblenden und beim Sperren aus der View entfernt.
- Version Code 2, gleiche Signing-Identität wie 0.1.0-test: Installation als Update vorgesehen.

## Änderung 0.1.2-test

- App-Name **FidoParole Reader**, gleiche Paket-ID und Signing-Identität (Update ohne Neuinstallation).
- Original-FidoParole-Icon aus dem Token2-Repository, als Launcher-Icon und im Header; Herkunft in NOTICE.md.
- Helles Theme nach der Windows-Vorlage: Brand `#f23058`, Text `#16181d`, Rahmen `#e4e7eb`, dezente graue Flächen.
- Lesbarer Modellname **Token2 PIN+ Dual** statt technischer USB-Kennung; keine individuelle Token-ID wird suggeriert.
- Getrennte Verbindung/Unlock-Ansicht und Eintragsansicht; Vault-Name, Titel und Benutzername klar getrennt.
- Anzeigen/Ausblenden und Kopieren nebeneinander; Layout auf Telefon und Tablet bis 700 dp Inhaltsbreite.
- Build verarbeitet jetzt Android Resources; adaptive Launcher-Icons und Theme enthalten.
- Inoffizieller Community-Reader, kein offizielles Token2-Release. Funktionsumfang bleibt read only über USB.
