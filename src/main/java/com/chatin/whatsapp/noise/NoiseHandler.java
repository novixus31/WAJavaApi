package com.chatin.whatsapp.noise;

import com.chatin.whatsapp.binary.BinaryDecoder;
import com.chatin.whatsapp.binary.BinaryNode;
import com.chatin.whatsapp.crypto.CryptoUtils;
import com.chatin.whatsapp.crypto.CurveUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * Noise Protocol handler - Java port of baileys noise-handler.js
 * Implements Noise_XX_25519_AESGCM_SHA256 protocol for WhatsApp
 */
public class NoiseHandler {

    private static final Logger logger = LoggerFactory.getLogger(NoiseHandler.class);

    // Noise_XX_25519_AESGCM_SHA256\0\0\0\0
    private static final String NOISE_MODE = "Noise_XX_25519_AESGCM_SHA256\0\0\0\0";

    private final byte[] privateKey;
    private final byte[] publicKey;
    private final byte[] noiseHeader;
    private final byte[] routingInfo;

    private byte[] hash;
    private byte[] salt;
    private byte[] encKey;
    private byte[] decKey;
    private int readCounter = 0;
    private int writeCounter = 0;
    private boolean isFinished = false;
    private boolean sentIntro = false;
    private byte[] inBytes = new byte[0];

    public NoiseHandler(byte[] privateKey, byte[] publicKey, byte[] noiseHeader, byte[] routingInfo) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.noiseHeader = noiseHeader;
        this.routingInfo = routingInfo;

        // Initialize hash
        byte[] data = NOISE_MODE.getBytes();
        try {
            this.hash = data.length == 32 ? data : CryptoUtils.sha256(data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize noise handler", e);
        }
        this.salt = this.hash.clone();
        this.encKey = this.hash.clone();
        this.decKey = this.hash.clone();

        // Authenticate initial data
        authenticate(noiseHeader);
        authenticate(publicKey);
    }

    /**
     * Update hash with new data
     */
    public void authenticate(byte[] data) {
        if (!isFinished) {
            try {
                hash = CryptoUtils.sha256(CryptoUtils.concat(hash, data));
            } catch (Exception e) {
                throw new RuntimeException("Failed to authenticate", e);
            }
        }
    }

    /**
     * Encrypt data using AES-256-GCM with noise counter
     */
    public byte[] encrypt(byte[] plaintext) throws Exception {
        byte[] iv = CryptoUtils.generateIV(writeCounter);
        byte[] result = CryptoUtils.aesEncryptGCM(plaintext, encKey, iv, hash);
        writeCounter++;
        authenticate(result);
        return result;
    }

    /**
     * Decrypt data using AES-256-GCM with noise counter
     */
    public byte[] decrypt(byte[] ciphertext) throws Exception {
        byte[] iv = CryptoUtils.generateIV(isFinished ? readCounter : writeCounter);
        byte[] result = CryptoUtils.aesDecryptGCM(ciphertext, decKey, iv, hash);
        if (isFinished) {
            readCounter++;
        } else {
            writeCounter++;
        }
        authenticate(ciphertext);
        return result;
    }

    /**
     * HKDF-based key mixing
     */
    private void localHKDF(byte[] data) throws Exception {
        byte[] key = CryptoUtils.hkdf(data, 64, salt, "");
        byte[] write = new byte[32];
        byte[] read = new byte[32];
        System.arraycopy(key, 0, write, 0, 32);
        System.arraycopy(key, 32, read, 0, 32);
        salt = write;
        encKey = read;
        decKey = read;
        readCounter = 0;
        writeCounter = 0;
    }

    /**
     * Mix data into key schedule
     */
    public void mixIntoKey(byte[] data) throws Exception {
        localHKDF(data);
    }

    /**
     * Finish initialization - split read/write keys
     */
    public void finishInit() throws Exception {
        byte[] key = CryptoUtils.hkdf(new byte[0], 64, salt, "");
        byte[] write = new byte[32];
        byte[] read = new byte[32];
        System.arraycopy(key, 0, write, 0, 32);
        System.arraycopy(key, 32, read, 0, 32);
        encKey = write;
        decKey = read;
        hash = new byte[0];
        readCounter = 0;
        writeCounter = 0;
        isFinished = true;
    }

    /**
     * Process the server handshake
     * Returns the encrypted noise key
     */
    public byte[] processHandshake(byte[] serverEphemeral, byte[] serverStatic, byte[] serverPayload,
                                    byte[] noisePrivateKey, byte[] noisePublicKey) throws Exception {
        // Authenticate server ephemeral
        authenticate(serverEphemeral);

        // DH with server ephemeral
        mixIntoKey(CurveUtils.sharedKey(privateKey, serverEphemeral));

        // Decrypt server static
        byte[] decStaticContent = decrypt(serverStatic);

        // DH with decrypted server static
        mixIntoKey(CurveUtils.sharedKey(privateKey, decStaticContent));

        // Decrypt server payload (cert chain)
        byte[] certDecoded = decrypt(serverPayload);

        // TODO: Validate certificate serial

        // Encrypt our noise key
        byte[] keyEnc = encrypt(noisePublicKey);

        // DH noise key with server ephemeral
        mixIntoKey(CurveUtils.sharedKey(noisePrivateKey, serverEphemeral));

        return keyEnc;
    }

    /**
     * Encode a frame for sending over WebSocket
     */
    public byte[] encodeFrame(byte[] data) throws Exception {
        if (isFinished) {
            data = encrypt(data);
        }

        byte[] header;
        if (routingInfo != null && routingInfo.length > 0) {
            ByteBuffer headerBuf = ByteBuffer.allocate(7 + routingInfo.length + noiseHeader.length);
            headerBuf.put((byte) 'E');
            headerBuf.put((byte) 'D');
            headerBuf.put((byte) 0);
            headerBuf.put((byte) 1);
            headerBuf.put((byte) (routingInfo.length >> 16));
            headerBuf.putShort((short) (routingInfo.length & 0xFFFF));
            headerBuf.put(routingInfo);
            headerBuf.put(noiseHeader);
            header = headerBuf.array();
        } else {
            header = noiseHeader.clone();
        }

        int introSize = sentIntro ? 0 : header.length;
        byte[] frame = new byte[introSize + 3 + data.length];

        if (!sentIntro) {
            System.arraycopy(header, 0, frame, 0, header.length);
            sentIntro = true;
        }

        frame[introSize] = (byte) (data.length >> 16);
        frame[introSize + 1] = (byte) ((data.length >> 8) & 0xFF);
        frame[introSize + 2] = (byte) (data.length & 0xFF);
        System.arraycopy(data, 0, frame, introSize + 3, data.length);

        return frame;
    }

    /**
     * Decode incoming frames from WebSocket
     */
    public void decodeFrame(byte[] newData, Consumer<Object> onFrame) throws Exception {
        inBytes = CryptoUtils.concat(inBytes, newData);

        while (inBytes.length >= 3) {
            int size = ((inBytes[0] & 0xFF) << 16) | ((inBytes[1] & 0xFF) << 8) | (inBytes[2] & 0xFF);

            if (inBytes.length < size + 3) {
                break; // Wait for more data
            }

            byte[] frame = new byte[size];
            System.arraycopy(inBytes, 3, frame, 0, size);

            byte[] remaining = new byte[inBytes.length - size - 3];
            System.arraycopy(inBytes, size + 3, remaining, 0, remaining.length);
            inBytes = remaining;

            if (isFinished) {
                byte[] decrypted = decrypt(frame);
                BinaryNode node = BinaryDecoder.decode(decrypted);
                onFrame.accept(node);
            } else {
                onFrame.accept(frame);
            }
        }
    }

    public boolean isFinished() { return isFinished; }
}
