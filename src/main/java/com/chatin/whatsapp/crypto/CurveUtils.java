package com.chatin.whatsapp.crypto;

import org.signal.libsignal.protocol.ecc.Curve;
import org.signal.libsignal.protocol.ecc.ECKeyPair;
import org.signal.libsignal.protocol.ecc.ECPrivateKey;
import org.signal.libsignal.protocol.ecc.ECPublicKey;

/**
 * Curve25519 utilities - Java port of baileys Curve operations
 * Uses libsignal-client for Curve25519 operations
 */
public class CurveUtils {

    /**
     * Key pair container
     */
    public static class KeyPair {
        private final byte[] publicKey;
        private final byte[] privateKey;

        public KeyPair(byte[] publicKey, byte[] privateKey) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }

        public byte[] getPublicKey() { return publicKey; }
        public byte[] getPrivateKey() { return privateKey; }
    }

    /**
     * Generate a Curve25519 key pair
     */
    public static KeyPair generateKeyPair() {
        ECKeyPair keyPair = Curve.generateKeyPair();
        byte[] publicKey = keyPair.getPublicKey().serialize();
        byte[] privateKey = keyPair.getPrivateKey().serialize();

        // Remove the version byte prefix (0x05) from public key if present
        if (publicKey.length == 33 && publicKey[0] == 0x05) {
            byte[] trimmed = new byte[32];
            System.arraycopy(publicKey, 1, trimmed, 0, 32);
            publicKey = trimmed;
        }

        return new KeyPair(publicKey, privateKey);
    }

    /**
     * Calculate ECDH shared secret
     */
    public static byte[] sharedKey(byte[] privateKey, byte[] publicKey) throws Exception {
        ECPrivateKey privKey = Curve.decodePrivatePoint(privateKey);
        ECPublicKey pubKey = Curve.decodePoint(generateSignalPubKey(publicKey), 0);
        return Curve.calculateAgreement(pubKey, privKey);
    }

    /**
     * Sign data with private key
     */
    public static byte[] sign(byte[] privateKey, byte[] data) throws Exception {
        ECPrivateKey privKey = Curve.decodePrivatePoint(privateKey);
        return Curve.calculateSignature(privKey, data);
    }

    /**
     * Verify signature
     */
    public static boolean verify(byte[] publicKey, byte[] message, byte[] signature) {
        try {
            ECPublicKey pubKey = Curve.decodePoint(generateSignalPubKey(publicKey), 0);
            return Curve.verifySignature(pubKey, message, signature);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Prefix version byte (0x05) to pub keys if not already present
     */
    public static byte[] generateSignalPubKey(byte[] pubKey) {
        if (pubKey.length == 33) {
            return pubKey;
        }
        byte[] result = new byte[33];
        result[0] = 0x05;
        System.arraycopy(pubKey, 0, result, 1, pubKey.length);
        return result;
    }

    /**
     * Generate a signed key pair
     */
    public static SignedKeyPair signedKeyPair(KeyPair identityKeyPair, int keyId) throws Exception {
        KeyPair preKey = generateKeyPair();
        byte[] pubKey = generateSignalPubKey(preKey.getPublicKey());
        byte[] signature = sign(identityKeyPair.getPrivateKey(), pubKey);
        return new SignedKeyPair(preKey, signature, keyId);
    }

    /**
     * Signed key pair container
     */
    public static class SignedKeyPair {
        private final KeyPair keyPair;
        private final byte[] signature;
        private final int keyId;

        public SignedKeyPair(KeyPair keyPair, byte[] signature, int keyId) {
            this.keyPair = keyPair;
            this.signature = signature;
            this.keyId = keyId;
        }

        public KeyPair getKeyPair() { return keyPair; }
        public byte[] getSignature() { return signature; }
        public int getKeyId() { return keyId; }
    }
}
