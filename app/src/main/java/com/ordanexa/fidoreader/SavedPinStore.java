package com.ordanexa.fidoreader;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** App-local PIN storage. The caller supplies a cipher authorized by BiometricPrompt. */
final class SavedPinStore {
    private static final String KEY_ALIAS = "fido-token-saved-pin-v1";
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private final SharedPreferences preferences;

    SavedPinStore(Context context) {
        preferences = context.getSharedPreferences("fido_saved_pin", Context.MODE_PRIVATE);
    }

    boolean hasPin() {
        return preferences.contains("ciphertext");
    }

    boolean matches(String tokenId) {
        return hasPin() && tokenId.equals(preferences.getString("token", ""));
    }

    Cipher encryptCipher() throws Exception {
        KeyStore store = keyStore();
        if (!store.containsAlias(KEY_ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                    .setInvalidatedByBiometricEnrollment(true)
                    .build());
            generator.generateKey();
        }
        Cipher cipher = Cipher.getInstance(TRANSFORM);
        cipher.init(Cipher.ENCRYPT_MODE, (SecretKey) store.getKey(KEY_ALIAS, null));
        return cipher;
    }

    Cipher decryptCipher(String tokenId) throws Exception {
        if (!matches(tokenId)) throw new GeneralSecurityException("No PIN for this token");
        Cipher cipher = Cipher.getInstance(TRANSFORM);
        cipher.init(Cipher.DECRYPT_MODE, (SecretKey) keyStore().getKey(KEY_ALIAS, null),
                new GCMParameterSpec(128, decode("iv")));
        return cipher;
    }

    static final class PendingPin {
        final String tokenId;
        final String iv;
        final String ciphertext;

        PendingPin(String tokenId, String iv, String ciphertext) {
            this.tokenId = tokenId;
            this.iv = iv;
            this.ciphertext = ciphertext;
        }
    }

    PendingPin seal(Cipher cipher, String tokenId, byte[] pin) throws GeneralSecurityException {
        cipher.updateAAD(tokenId.getBytes(StandardCharsets.UTF_8));
        byte[] encrypted = cipher.doFinal(pin);
        try {
            return new PendingPin(tokenId,
                    Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP),
                    Base64.encodeToString(encrypted, Base64.NO_WRAP));
        } finally {
            Crypto.wipe(encrypted);
        }
    }

    boolean save(PendingPin pending) {
        return preferences.edit().putString("token", pending.tokenId)
                .putString("iv", pending.iv).putString("ciphertext", pending.ciphertext).commit();
    }

    byte[] read(Cipher cipher, String tokenId) throws GeneralSecurityException {
        cipher.updateAAD(tokenId.getBytes(StandardCharsets.UTF_8));
        return cipher.doFinal(decode("ciphertext"));
    }

    void forget() throws Exception {
        keyStore().deleteEntry(KEY_ALIAS);
        if (!preferences.edit().clear().commit()) throw new GeneralSecurityException("Could not clear PIN metadata");
    }

    private byte[] decode(String field) {
        return Base64.decode(preferences.getString(field, ""), Base64.NO_WRAP);
    }

    private static KeyStore keyStore() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return store;
    }
}
