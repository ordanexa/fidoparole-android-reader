# Reference sources

FidoParole Reader is an unofficial Android community companion by [Ordanexa](https://github.com/ordanexa). The original project is [Token2 FidoParole](https://github.com/token2/fidoparole). This reader does not bundle the desktop app, its Rust implementation or libfido2.

The [MIT License](LICENSE) covers the original Ordanexa reader code and documentation. Token2 artwork and any other third-party material remain subject to their respective rights and terms.

Compatibility was developed by inspecting:

- Token2 FidoParole, commit `ee1bd5e47d706b1ca63197d8f1dd68cdc26ca534`: https://github.com/token2/fidoparole
  - `src-tauri/src/vault.rs`: T2VLT format, fixed PRF salt, HKDF label, compression and JSON versions.
  - `src-tauri/src/fido.rs` / `keyops.rs`: RP identifier and unlock procedure.
- Yubico python-fido2, commit `9d5991d6785e20bd487c8b6c996df6ce2226642a`: https://github.com/Yubico/python-fido2
  - CTAP PIN protocols, hmac-secret, largeBlob packing and HID framing used as implementation references.
  - Imported by the independent test simulator only, not bundled in the APK.
- Android USB Host API: https://developer.android.com/develop/connectivity/usb/host
- CTAP 2.1: https://fidoalliance.org/specs/fido-v2.1-ps-20210615/fido-client-to-authenticator-protocol-v2.1-ps-20210615.html

The fixed RP identifier, salt and key-derivation labels are compatibility requirements and must not be renamed.

## Yubico reference notice

Copyright (c) 2018 Yubico AB. All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

- Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
- Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

## Artwork and visual reference

`app/src/main/res/drawable-nodpi/fidoparole_mark.png` is an unchanged copy of `src-tauri/icons/icon.png` from the Token2 commit above. The light palette follows the desktop `ui/style.css` design tokens. The icon is Token2 artwork.
