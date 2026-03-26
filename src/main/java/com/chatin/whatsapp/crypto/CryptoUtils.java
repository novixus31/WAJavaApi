package com.chatin.whatsapp.crypto;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Crypto utilities - Java port of baileys Utils/crypto.js
 * Implements AES-GCM, AES-CBC, AES-CTR, HMAC, SHA256, HKDF
 */
public class CryptoUtils {

    private static final int GCM_TAG_LENGTH = 128; // bits

    /**
     * Encrypt AES-256-GCM with auth tag appended to ciphertext
     */
    public static byte[] aesEncryptGCM(byte[] plaintext, byte[] key, byte[] iv, byte[] additionalData) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
        if (additionalData != null) {
            cipher.updateAAD(additionalData);
        }
        return cipher.doFinal(plaintext);
    }

    /**
     * Decrypt AES-256-GCM where auth tag is appended to ciphertext
     */
    public static byte[] aesDecryptGCM(byte[] ciphertext, byte[] key, byte[] iv, byte[] additionalData) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
        if (additionalData != null) {
            cipher.updateAAD(additionalData);
        }
        return cipher.doFinal(ciphertext);
    }

    /**
     * Encrypt AES-256-CTR
     */
    public static byte[] aesEncryptCTR(byte[] plaintext, byte[] key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(plaintext);
    }

    /**
     * Decrypt AES-256-CTR
     */
    public static byte[] aesDecryptCTR(byte[] ciphertext, byte[] key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(ciphertext);
    }

    /**
     * Decrypt AES-256-CBC where IV is prepended to the buffer
     */
    public static byte[] aesDecrypt(byte[] buffer, byte[] key) throws Exception {
        byte[] iv = new byte[16];
        System.arraycopy(buffer, 0, iv, 0, 16);
        byte[] data = new byte[buffer.length - 16];
        System.arraycopy(buffer, 16, data, 0, data.length);
        return aesDecryptWithIV(data, key, iv);
    }

    /**
     * Decrypt AES-256-CBC with given IV
     */
    public static byte[] aesDecryptWithIV(byte[] buffer, byte[] key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(buffer);
    }

    /**
     * Encrypt AES-256-CBC with random IV prepended
     */
    public static byte[] aesEncrypt(byte[] buffer, byte[] key) throws Exception {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = cipher.doFinal(buffer);
        byte[] result = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
        return result;
    }

    /**
     * Encrypt AES-256-CBC with given IV
     */
    public static byte[] aesEncryptWithIV(byte[] buffer, byte[] key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(buffer);
    }

    /**
     * HMAC-SHA256 sign
     */
    public static byte[] hmacSign(byte[] buffer, byte[] key) throws Exception {
        return hmacSign(buffer, key, "HmacSHA256");
    }

    /**
     * HMAC sign with custom algorithm
     */
    public static byte[] hmacSign(byte[] buffer, byte[] key, String algorithm) throws Exception {
        Mac mac = Mac.getInstance(algorithm);
        SecretKeySpec keySpec = new SecretKeySpec(key, algorithm);
        mac.init(keySpec);
        return mac.doFinal(buffer);
    }

    /**
     * SHA-256 hash
     */
    public static byte[] sha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(data);
    }

    /**
     * MD5 hash
     */
    public static byte[] md5(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        return digest.digest(data);
    }

    /**
     * HKDF key expansion (RFC 5869)
     */
    public static byte[] hkdf(byte[] inputKeyMaterial, int expandedLength, byte[] salt, String info) throws Exception {
        if (salt == null || salt.length == 0) {
            salt = new byte[32]; // empty salt
        }

        // Extract
        byte[] prk = hmacSign(inputKeyMaterial, salt);

        // Expand
        byte[] infoBytes = info != null ? info.getBytes(StandardCharsets.UTF_8) : new byte[0];
        int hashLen = 32; // SHA-256
        int n = (int) Math.ceil((double) expandedLength / hashLen);
        byte[] okm = new byte[expandedLength];
        byte[] t = new byte[0];

        for (int i = 0; i < n; i++) {
            byte[] input = new byte[t.length + infoBytes.length + 1];
            System.arraycopy(t, 0, input, 0, t.length);
            System.arraycopy(infoBytes, 0, input, t.length, infoBytes.length);
            input[input.length - 1] = (byte) (i + 1);

            t = hmacSign(input, prk);

            int copyLen = Math.min(hashLen, expandedLength - i * hashLen);
            System.arraycopy(t, 0, okm, i * hashLen, copyLen);
        }

        return okm;
    }

    /**
     * PBKDF2 key derivation (for pairing code)
     */
    public static byte[] derivePairingCodeKey(String pairingCode, byte[] salt) throws Exception {
        javax.crypto.SecretKeyFactory factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                pairingCode.toCharArray(), salt, 2 << 16, 256
        );
        return factory.generateSecret(spec).getEncoded();
    }

    /**
     * Generate an IV for noise protocol counter
     */
    public static byte[] generateIV(int counter) {
        ByteBuffer buffer = ByteBuffer.allocate(12);
        buffer.putInt(8, counter);
        return buffer.array();
    }

    /**
     * Generate random bytes
     */
    public static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    /**
     * Concatenate byte arrays
     */
    public static byte[] concat(byte[]... arrays) {
        int totalLength = 0;
        for (byte[] array : arrays) {
            totalLength += array.length;
        }
        byte[] result = new byte[totalLength];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }
}
