# Archived test APKs

This directory contains test versions 0.1.0–0.1.6. [SHA256SUMS](SHA256SUMS) lists their checksums. All signatures were verified against the same signing certificate:

`707f6d453412ec20afbf0aa498d6c63d07c3d4548688030b45f5cd7b04c8b485`

0.1.2 was user-tested on Samsung S24 and tablet. 0.1.3 provides the English interface; its device test is not yet recorded. 0.1.4 adds optional biometric unlock for a locally saved token PIN. On-device testing of 0.1.4 is pending. These are experimental test builds, not audited production releases.

See [APK signing](../docs/SIGNING.md) for local builds and update compatibility.

0.1.5 simplifies login and starts biometric authentication automatically when the connected key has a saved PIN. Device testing of 0.1.5 is pending.

0.1.6 adds the read-only Token2 serial-number query and binds saved PINs to that identifier. Pins saved previously without a serial number need a one-time manual re-save. Device testing is pending.
