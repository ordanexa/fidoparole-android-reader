# FidoParole Reader

An unofficial Android community companion by [Ordanexa](https://github.com/ordanexa). It reads existing FidoParole vaults over USB-C and lets you show or copy passwords. Use [Token2 FidoParole Desktop](https://github.com/token2/fidoparole) to manage and back up vaults.

## Status

**0.1.4-test** adds optional biometric unlock with an app-local saved token PIN. Connect a USB key, opt in to **Save PIN on this device**, and authenticate to protect the PIN. The encrypted PIN is kept only after the vault opens successfully. For later access, choose **Unlock with biometrics** or enter the PIN manually. **Forget saved PIN** removes the local copy and its Keystore key. Saving for another token replaces the current saved PIN.

The reader matches the saved PIN to the USB serial number when the key exposes one. Otherwise, explicitly confirm that the same physical key is connected before using the saved PIN. A rejected saved PIN is not retried automatically. The physical token and its normal touch prompt remain part of unlock.


The previous build is **0.1.3-test** (2026-11-14). There is no planned development schedule. This build supports USB-C only.

- Signed, installable APK; package `com.ordanexa.fidoreader`.
- Android 13+ (API 33). Version 0.1.2-test was successfully tested by Pascal on a Samsung S24 and tablet.
- Version 0.1.3-test changes the app UI, accessibility labels, status/error messages and repository documentation to English. Device verification of this build is still pending.
- Initially limited to Token2 USB device `349e:0024`.
- Uses the Android biometric permission. No Internet, storage, NFC or accessibility permission.
- No vault or master password is persisted to disk. Only an opt-in encrypted token PIN is saved locally; Android backup is disabled.
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

Output: `dist/fidoparole-android-reader-0.1.4-test.apk` and `dist/SHA256SUMS`.

By default, the build creates a local test key at `build/test-signing.jks`. See [APK signing](docs/SIGNING.md) for custom signing and update compatibility.

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

## Downloads and related projects

- [Download FidoParole Reader 0.1.4-test](apks/fidoparole-android-reader-0.1.4-test.apk?raw=true)
- [APK archive](apks/) and [SHA-256 checksums](apks/SHA256SUMS)
- [Token2 FidoParole](https://github.com/token2/fidoparole), the original project and desktop vault manager.
- [Ordanexa KeePassDX-FIDO2](https://github.com/ordanexa/KeePassDX-FIDO2), an experimental fork that unlocks KeePass databases on Android with an enrolled Token2 USB security key and its PIN.

## License

The original Ordanexa reader code and documentation are available under the [MIT License](LICENSE). See [NOTICE.md](NOTICE.md) for Token2 artwork and third-party notices.

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
