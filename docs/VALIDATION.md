# Validation

Date: 2026-11-14. The initial hardware workflow and version 0.1.2-test were confirmed by the user. Version 0.1.3-test translates the interface and documentation.

## Original baseline checks (0.1.0-test)

- Compiled with JDK 21 and Android API 35; DEX targets minimum API 33.
- APK generated and signed with a temporary RSA-3072 test key.
- APK Signature Scheme v3 verified with apksigner; ZIP alignment passed.
- Manifest inspected: no requested permissions, USB Host feature, backup disabled and no Internet permission.
- 154 host checks passed using an independent Python simulator:
  - PIN protocols 1 and 2, including ECDH, PIN hash encryption and authentication parameters.
  - PRF salt / hmac-secret, two assertions and correct GetNextAssertion ordering.
  - HID request/response fragmentation and keepalive handling.
  - Fragmented largeBlob reads and selection by AES-GCM authentication.
  - T2VLT decryption and JSON formats 1/2 against independently generated data.
  - Incorrect PIN without automatic retry; low retry count blocks further attempts.
  - Missing UV flag, incorrect checksum, tampered ciphertext and incorrect hmac-secret rejected.
  - CBOR limits, duplicate keys, invalid data and CTAP write commands rejected.
  - Supplied PIN buffer cleared after success and failure.

## Hardware feedback

On 2026-11-14 the user explicitly confirmed that the initial app worked with the existing key over USB. The user later confirmed successful testing of 0.1.2-test on both Samsung S24 and tablet. Android versions and individual lifecycle checks were not separately reported.

## Not separately verified

- Clipboard cleanup and automatic locking on backgrounding, unplugging and inactivity on Samsung Android.
- Every individual Show/Hide and Copy/Paste checklist case.
- Formal security review or complete memory zeroization.
- Device behavior and layout of the English 0.1.3-test build.

Simulator checks do not replace these tests. Use a dummy password for outstanding device checks; a working unlock flow is not a complete security review.

## Version 0.1.1-test

- Build, APK signature and ZIP alignment passed.
- Version code 2; same signing certificate SHA-256 as 0.1.0-test.
- UI review: password text starts empty/hidden; Hide clears it; Lock clears all password views before removal. FLAG_SECURE remains enabled.
- USB/CTAP/crypto implementation unchanged. The 154 original protocol checks cover that unchanged baseline.
- The new Show/Hide flow was not executed on Android at this stage.

## Version 0.1.2-test

- Resource compilation/linking, Java compilation, DEX and APK build passed.
- APK Signature Scheme v3 and ZIP alignment verified; same signing certificate, version code 3.
- Manifest output confirmed **FidoParole Reader** and adaptive launcher icon.
- Icon copied unchanged from the inspected upstream commit.
- Diff review: no changes to USB transport, CTAP or crypto core.
- UI retains FLAG_SECURE, clear-on-lock and disabled autofill for PIN/password.
- User confirmed successful use of the new version on Samsung S24 and tablet; individual lifecycle checks were not separately confirmed.

## Version 0.1.3-test

- English UI, accessibility labels, status messages, errors and documentation.
- Version code 4; package ID unchanged.
- Only human-readable strings and version metadata changed in the application; protocol constants and control flow are unchanged.
- Resource/Java/DEX/APK build passed; all 154 simulator checks passed.
- APK Signature Scheme v3 and ZIP alignment verified. The signing certificate matches the previous test builds.
- Source comparison confirms that application changes are limited to string literals.
- Repository scan found no remaining German prose in tracked source or documentation.
- A fresh device check remains pending.

## Version 0.1.4-test

On 2026-11-16, GitHub Actions rebuilt the reader from source commit `d3afbdae317d49e3a0d8f33af014aa071ada7f89` and verified the APK signature against the existing Reader certificate. The run verified the package `com.ordanexa.fidoreader`, version code 5 and version name `0.1.4-test`. SHA-256 verification passed for all APKs in the archive. [Build run](https://github.com/ordanexa/fidoparole-android-reader/actions/runs/43957380767) · [SHA-256 checksums](../apks/SHA256SUMS)

On-device USB and biometric checks for this build are pending. Follow the [device checklist](DEVICE_TEST.md#saved-pin-and-biometric-unlock-014-test).

## Version 0.1.5-test

The simplified login introduces automatic biometric prompting and an explicit PIN fallback. Existing USB protocol tests run during the signed build. On 2026-11-19, [GitHub Actions run 44599361586](https://github.com/ordanexa/fidoparole-android-reader/actions/runs/44599361586) passed all 154 protocol checks, compiled the Android app and verified the package, version code 6, version name 0.1.5-test, preserved signing certificate and APK alignment. The downloaded artifact checksum was independently recomputed before archiving.

Physical USB, Android biometric lifecycle and layout checks remain pending. Follow the [0.1.5 device checklist](DEVICE_TEST.md#simplified-login-015-test). The user confirmed 0.1.4 fingerprint unlock followed by token touch on the tablet on 2026-11-16.

## Version 0.1.6-test

The Token2 serial reader sends one fixed read-only GET_INFO APDU over unnumbered HID feature reports. New JVM tests verify the exact request bytes, vendor serial response decoding, fragmented responses, polling, sequence checks, malformed frames, status failures and invalid serial identifiers. Existing protocol tests cover PIN error handling without automatic retries.

Identification runs off the UI thread with bounded control-transfer timeouts. Backgrounding or unplugging closes the handle and invalidates pending results. Unknown legacy PIN records are never silently rebound to the connected key. A successful manual unlock can save a new serial-bound encrypted record.

Physical Token2 serial-query and Android lifecycle testing remains pending. Follow the [0.1.6 checklist](DEVICE_TEST.md#token2-serial-identification-016-test).
