# Device checklist

## Preparation

- Use an existing FidoParole test vault that opens under Windows and contains a dummy password.
- Have a backup or second key available and know the PIN. Do not reset the key or change its PIN for this test.
- Windows/RDP must not have the key open during testing.

## Procedure

1. Install the APK on Android. If prompted, permit installation from the download app, then revoke that permission afterward.
2. Connect Token2 directly over USB-C and open **FidoParole Reader**.
3. Select **Connect security key** and grant Android USB permission.
4. Enter the FIDO2 PIN and select **Unlock**.
5. Touch the key when it blinks. Expect vault/entry names with passwords hidden.
6. Select **Copy**, paste into a suitable local test app and compare with the known dummy password. Never send real secrets in chat.
7. Return to the reader: it must be locked. After the clipboard timeout, check on return whether the app's clip was removed.
8. Unlock again and unplug the key: entries must disappear.
9. Unlock again and wait 60 seconds without interaction: check automatic locking.
10. Reopen the vault under Windows and verify that its entries are unchanged.

## Feedback

Report only non-sensitive details:

- Device: S24 / tablet; Android version.
- Was USB permission offered and granted?
- Last app status and exact error code, if any (for example CTAP 0x33).
- Did the key blink, and was it touched?
- On success: number of vaults/entries and whether the dummy password copied correctly.

Do not share PINs, master passwords or sensitive entry names. After a PIN error, do not repeatedly guess; verify it using the known working Windows workflow.

## Password display update check

- Install over the existing app using the same signing key.
- Passwords remain hidden after unlock. **Show** reveals the dummy value; **Hide** removes it.
- Check **Copy** separately.
- While a password is visible, switch apps or unplug the key: the reader must lock. After another unlock, passwords must be hidden.

## Layout and English interface check

- Confirm launcher name **FidoParole Reader** and the original pink icon.
- Check the light interface, readable key label and unclipped buttons on phone and tablet.
- After unlock, the PIN card disappears and entries show **Show** and **Copy**. **Lock** restores the unlock view.
- Check portrait/landscape layouts and ensure the keyboard and system bars do not obscure content.
- Confirm English labels, connection/lock messages and accessibility descriptions.
- Check empty/default entry titles if available; user-created vault and entry names must remain unchanged.
- Do not deliberately consume PIN attempts just to inspect translated errors.

## Saved PIN and biometric unlock (0.1.4-test)

1. Update the installed test build with the archived 0.1.4-test APK. Connect the same test key and confirm manual PIN unlock still works.
2. Lock the app. Select **Save PIN on this device**, enter the known PIN and complete the biometric prompt and normal token touch. Use a device with strong biometrics enrolled.
3. Lock and reopen the app with the same key. Select **Unlock with biometrics** and verify that the vault opens after the token touch. If Android reports no USB serial number, confirm the same physical key is connected when prompted.
4. Cancel a biometric prompt and confirm you can still enter the PIN manually.
5. Choose **Forget saved PIN** and confirm biometric unlock is no longer offered. Confirm manual unlock still works.

## Recorded result

On 2026-11-14 Pascal confirmed successful use of **0.1.2-test** on both a Samsung S24 and tablet. This is not a separate confirmation of every checklist item. Device checks for **0.1.3-test** and **0.1.4-test** are pending.
