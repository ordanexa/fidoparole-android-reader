# FidoParole Reader

A small native Android app for **reading existing FidoParole vaults over USB-C**, revealing passwords and copying them. This is the final intended scope, not the beginning of a full password manager.

An independent, unofficial community reader. Vault management and backups remain in Token2 FidoParole Desktop.

## Status: 0.1.3-test (2026-11-14)

- Signed, installable APK; package `com.ordanexa.fidoreader`.
- Android 13+ (API 33). Version 0.1.2-test was successfully tested by Pascal on a Samsung S24 and tablet.
- Version 0.1.3-test changes the app UI, accessibility labels, status/error messages and repository documentation to English. Device verification of this build is still pending.
- Initially limited to Token2 USB device `349e:0024`.
- No Internet, storage, NFC or accessibility permission.
- No vault, PIN or master password persisted to disk; Android backup is disabled.
- Experimental software; no external security audit.

## Fixed feature scope

1. Connect the key over USB and grant Android USB permission.
2. Enter the FIDO2 PIN; touch the key when prompted.
3. Read existing vaults and entries.
4. Show/hide or copy a password. Passwords start hidden after every unlock.
5. Lock when the app enters the background, the key is unplugged, or the 60-second inactivity timer expires.

No create, edit, delete, reset, PIN change, backup import/export, NFC, sync or autofill.

**Read-only refers to vault contents and credentials.** Authentication can change internal authenticator counters and PIN retry state. An incorrect PIN consumes a real attempt. The app never retries a PIN automatically and stops when two or fewer attempts remain.

## Implementation

Native Java Activity and Android USB Host API; no WebView, Rust runtime or native libraries. Android/Java cryptographic providers supply P-256 ECDH, AES-CBC, HMAC/HKDF-SHA256 and AES-256-GCM.

- CTAPHID INIT/CBOR, fragmentation, keepalive and timeout handling.
- PIN/UV protocols 1 and 2; scoped GetAssertion permission where supported.
- Discoverable GetAssertion for `fido2-vault.token2`, `hmac-secret` and `largeBlobKey`.
- GetNextAssertion before any largeBlob reads.
- largeBlob checksum verification and AES-GCM/DEFLATE outer layer, followed by T2VLT v1 and JSON formats 1/2.
- Explicit CTAP allowlist blocks largeBlob SET and credential/PIN write operations.

Protocol identifiers, salts, key-derivation labels and stored vault data are compatibility requirements and are not translated.

## Build (Linux / WSL)

Requirements: JDK 21, Android command-line tools, `zip`; Python for tests only.

```bash
sdkmanager 'platforms;android-35' 'build-tools;35.0.0'
export ANDROID_HOME=/path/to/android-sdk
./build.sh
```

Output: `dist/fidoparole-android-reader-0.1.3-test.apk` and `dist/SHA256SUMS`.

Without custom signing variables, the build creates a **temporary test key** at `build/test-signing.jks`. It is excluded from Git and source archives. The known test keystore password does not protect release signing. For durable private releases, manage and back up a dedicated signing key locally:

```bash
export READER_KEYSTORE=/secure/path/private-reader.jks
export READER_KEY_ALIAS=reader
read -rs -p 'Keystore password: ' READER_STORE_PASS; export READER_STORE_PASS
read -rs -p 'Key password: ' READER_KEY_PASS; export READER_KEY_PASS
./build.sh
```

An update requires the same signing identity. A different key requires uninstalling the previous test build; the app has no persisted vault data. Never place private signing keys in Git or the handoff folder.

## Tests

The JVM tests use an **independent Python CTAP simulator** based on Yubico's `python-fido2` and Python `cryptography`. They check actual request/response bytes, HID fragmentation and decryption independently of the Java implementation. They do not replace Android USB tests.

```bash
python3 -m venv .venv
. .venv/bin/activate
pip install -r tests/requirements.txt
./test.sh
```

See the [validation report](docs/VALIDATION.md), [device checklist](docs/DEVICE_TEST.md) and [reference notices](NOTICE.md).

## Limitations

- Successful use of 0.1.2-test on both Android devices is user-confirmed. Individual clipboard and automatic-lock checks are not separately documented; 0.1.3-test still needs a device check.
- Clipboard content is marked sensitive. The app requests deletion of its own clip after 20 seconds and checks again on return. Android may restrict background execution or clipboard access, so **there is no guaranteed deletion deadline**. Other apps may retain pasted passwords.
- FLAG_SECURE protects screenshots and recent-app previews. It does not protect a compromised Android device.
- Byte/character arrays are cleared where possible. Java, Android, JSON and the clipboard also use immutable strings, so complete memory zeroization is not guaranteed.
- The USB transport closes after reading. Android's detach broadcast locks the displayed entries when the key is unplugged.
- The app does not log PINs, passwords or CTAP dumps.

## Repository and handoff

Private repository: [pascalep/fidoparole-android-reader](https://github.com/pascalep/fidoparole-android-reader).

The tested 0.1.2 source was imported as the baseline, together with tests and documentation. Version 0.1.3 adds the English interface and documentation. The separate infrastructure-toolbox repository is unchanged.

Raw source handoff files are maintained under `outbox/fidoparole-android-reader/`. Generated test artifacts belong under `artifacts/` and are excluded from the source repository.

Before production use: complete the outstanding device/lifecycle checks, establish a dedicated signing key and retain independent recovery copies.

## Changes in 0.1.1-test

- Added Show/Hide per entry, separate from Copy.
- Hiding or locking removes visible password text from the view.
- Version code 2; same signing identity as 0.1.0-test, intended for an in-place update.

## Changes in 0.1.2-test

- App name **FidoParole Reader**; unchanged package ID and signing identity.
- Original FidoParole icon in the launcher and header; origin recorded in NOTICE.md.
- Light theme inspired by the Windows app: brand `#f23058`, text `#16181d`, borders `#e4e7eb`, subtle gray surfaces.
- Readable model label **Token2 PIN+ Dual** instead of a technical USB identifier; this is not an individual key name.
- Separate connection/unlock and entry views; distinct vault name, entry title and username.
- Show/Hide and Copy side by side; phone/tablet layout with a maximum content width of 700 dp.
- Build now processes Android resources, adaptive launcher icons and the app theme.
- Clearly labelled unofficial; USB-only, read-only scope unchanged.

## Changes in 0.1.3-test

- English UI, accessibility labels, status messages, errors and documentation.
- Version code 4; same package ID and signing identity for an in-place update.
- No changes to protocol behavior or vault format.
