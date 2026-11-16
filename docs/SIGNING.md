# APK signing

The [archived test APKs](../apks/) for 0.1.0–0.1.3 use the same signing certificate. Its SHA-256 fingerprint is:

`707f6d453412ec20afbf0aa498d6c63d07c3d4548688030b45f5cd7b04c8b485`

For local builds, `build.sh` uses a disposable test key by default. To sign with your own key, set `READER_KEYSTORE` and `READER_KEY_ALIAS`, then provide `READER_STORE_PASS` and `READER_KEY_PASS` through your local environment or secret store.

Android requires the same signing identity for an update. To install a build signed with a different key under the same package ID, first uninstall the current reader. The reader stores no vault data on disk.
