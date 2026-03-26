package com.chatin.service;

import com.chatin.model.WhatsAppAuth;
import com.chatin.repository.WhatsAppAuthRepository;
import com.chatin.whatsapp.crypto.CurveUtils;
import com.chatin.whatsapp.socket.WASocket;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.*;

/**
 * MongoDB-based authentication state manager
 * Java port of useMongoAuthState.ts
 * Stores and retrieves WhatsApp session credentials from MongoDB
 */
@Service
public class MongoAuthState {

    private static final Logger logger = LoggerFactory.getLogger(MongoAuthState.class);

    private final WhatsAppAuthRepository authRepository;
    private final ObjectMapper objectMapper;

    public MongoAuthState(WhatsAppAuthRepository authRepository, ObjectMapper objectMapper) {
        this.authRepository = authRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Get or create auth state for an account
     */
    public WASocket.AuthState getAuthState(String accountId) {
        WASocket.AuthState authState = new WASocket.AuthState();

        // Try to load existing credentials
        Optional<WhatsAppAuth> credsDoc = authRepository.findByAccountIdAndType(accountId, "creds");

        if (credsDoc.isPresent()) {
            try {
                authState.creds = deserializeCreds(credsDoc.get().getData());
                logger.info("Loaded existing credentials for account: {}", accountId);
            } catch (Exception e) {
                logger.warn("Failed to load credentials, creating new: {}", e.getMessage());
                authState.creds = initAuthCreds();
            }
        } else {
            authState.creds = initAuthCreds();
            logger.info("Created new credentials for account: {}", accountId);
        }

        // Create keys handler
        authState.keys = createKeysHandler(accountId);

        return authState;
    }

    /**
     * Save credentials to MongoDB
     */
    public void saveCreds(String accountId, WASocket.AuthCreds creds) {
        try {
            WhatsAppAuth auth = authRepository.findByAccountIdAndType(accountId, "creds")
                    .orElse(new WhatsAppAuth());
            auth.setAccountId(accountId);
            auth.setType("creds");
            auth.setData(serializeCreds(creds));
            authRepository.save(auth);
            logger.debug("Saved credentials for account: {}", accountId);
        } catch (Exception e) {
            logger.error("Failed to save credentials for account {}: {}", accountId, e.getMessage());
        }
    }

    /**
     * Remove all auth data for an account
     */
    public void removeAuthState(String accountId) {
        authRepository.deleteByAccountId(accountId);
        logger.info("Removed auth state for account: {}", accountId);
    }

    /**
     * Initialize new auth credentials
     */
    public WASocket.AuthCreds initAuthCreds() {
        WASocket.AuthCreds creds = new WASocket.AuthCreds();

        try {
            creds.noiseKey = CurveUtils.generateKeyPair();
            creds.signedIdentityKey = CurveUtils.generateKeyPair();
            creds.pairingEphemeralKeyPair = CurveUtils.generateKeyPair();
            creds.signedPreKey = CurveUtils.signedKeyPair(creds.signedIdentityKey, 1);
            creds.registrationId = new SecureRandom().nextInt(16383) + 1;
            creds.advSecretKey = Base64.getEncoder().encodeToString(
                    com.chatin.whatsapp.crypto.CryptoUtils.randomBytes(32));
            creds.nextPreKeyId = 1;
            creds.firstUnuploadedPreKeyId = 1;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize auth credentials", e);
        }

        return creds;
    }

    /**
     * Create a keys handler backed by MongoDB
     */
    private WASocket.AuthKeys createKeysHandler(String accountId) {
        return new WASocket.AuthKeys() {
            @Override
            public Map<String, Object> get(String type, List<String> ids) {
                Map<String, Object> result = new HashMap<>();
                for (String id : ids) {
                    try {
                        Optional<WhatsAppAuth> doc = authRepository.findByAccountIdAndTypeAndKeyId(
                                accountId, type, id);
                        if (doc.isPresent()) {
                            result.put(id, doc.get().getData());
                        }
                    } catch (Exception e) {
                        logger.error("Error getting key {}/{}: {}", type, id, e.getMessage());
                    }
                }
                return result;
            }

            @Override
            public void set(Map<String, Object> data) {
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    String type = entry.getKey();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> keys = (Map<String, Object>) entry.getValue();
                    if (keys == null) continue;

                    for (Map.Entry<String, Object> keyEntry : keys.entrySet()) {
                        String keyId = keyEntry.getKey();
                        Object value = keyEntry.getValue();

                        try {
                            if (value == null) {
                                // Delete key
                                Optional<WhatsAppAuth> doc = authRepository.findByAccountIdAndTypeAndKeyId(
                                        accountId, type, keyId);
                                doc.ifPresent(authRepository::delete);
                            } else {
                                // Save key
                                WhatsAppAuth auth = authRepository.findByAccountIdAndTypeAndKeyId(
                                        accountId, type, keyId).orElse(new WhatsAppAuth());
                                auth.setAccountId(accountId);
                                auth.setType(type);
                                auth.setKeyId(keyId);
                                auth.setData(value);
                                authRepository.save(auth);
                            }
                        } catch (Exception e) {
                            logger.error("Error setting key {}/{}: {}", type, keyId, e.getMessage());
                        }
                    }
                }
            }
        };
    }

    /**
     * Serialize credentials to a storable format
     */
    private Object serializeCreds(WASocket.AuthCreds creds) {
        Map<String, Object> map = new HashMap<>();
        if (creds.noiseKey != null) {
            map.put("noiseKey", Map.of(
                    "public", Base64.getEncoder().encodeToString(creds.noiseKey.getPublicKey()),
                    "private", Base64.getEncoder().encodeToString(creds.noiseKey.getPrivateKey())
            ));
        }
        if (creds.signedIdentityKey != null) {
            map.put("signedIdentityKey", Map.of(
                    "public", Base64.getEncoder().encodeToString(creds.signedIdentityKey.getPublicKey()),
                    "private", Base64.getEncoder().encodeToString(creds.signedIdentityKey.getPrivateKey())
            ));
        }
        map.put("registrationId", creds.registrationId);
        map.put("advSecretKey", creds.advSecretKey);
        map.put("nextPreKeyId", creds.nextPreKeyId);
        map.put("firstUnuploadedPreKeyId", creds.firstUnuploadedPreKeyId);
        if (creds.signedPreKey != null) {
            map.put("signedPreKey", Map.of(
                    "keyId", creds.signedPreKey.getKeyId(),
                    "public", Base64.getEncoder().encodeToString(creds.signedPreKey.getKeyPair().getPublicKey()),
                    "private", Base64.getEncoder().encodeToString(creds.signedPreKey.getKeyPair().getPrivateKey()),
                    "signature", Base64.getEncoder().encodeToString(creds.signedPreKey.getSignature())
            ));
        }
        if (creds.me != null) {
            map.put("me", Map.of("id", creds.me.id, "name", creds.me.name != null ? creds.me.name : ""));
        }
        if (creds.routingInfo != null) {
            map.put("routingInfo", Base64.getEncoder().encodeToString(creds.routingInfo));
        }
        return map;
    }

    /**
     * Deserialize credentials from MongoDB
     */
    @SuppressWarnings("unchecked")
    private WASocket.AuthCreds deserializeCreds(Object data) throws Exception {
        Map<String, Object> map = (Map<String, Object>) data;
        WASocket.AuthCreds creds = new WASocket.AuthCreds();

        Map<String, String> noiseKeyMap = (Map<String, String>) map.get("noiseKey");
        if (noiseKeyMap != null) {
            creds.noiseKey = new CurveUtils.KeyPair(
                    Base64.getDecoder().decode(noiseKeyMap.get("public")),
                    Base64.getDecoder().decode(noiseKeyMap.get("private"))
            );
        }

        Map<String, String> idKeyMap = (Map<String, String>) map.get("signedIdentityKey");
        if (idKeyMap != null) {
            creds.signedIdentityKey = new CurveUtils.KeyPair(
                    Base64.getDecoder().decode(idKeyMap.get("public")),
                    Base64.getDecoder().decode(idKeyMap.get("private"))
            );
        }

        creds.registrationId = ((Number) map.getOrDefault("registrationId", 0)).intValue();
        creds.advSecretKey = (String) map.get("advSecretKey");
        creds.nextPreKeyId = ((Number) map.getOrDefault("nextPreKeyId", 1)).intValue();
        creds.firstUnuploadedPreKeyId = ((Number) map.getOrDefault("firstUnuploadedPreKeyId", 1)).intValue();

        Map<String, Object> spkMap = (Map<String, Object>) map.get("signedPreKey");
        if (spkMap != null) {
            CurveUtils.KeyPair spkKeyPair = new CurveUtils.KeyPair(
                    Base64.getDecoder().decode((String) spkMap.get("public")),
                    Base64.getDecoder().decode((String) spkMap.get("private"))
            );
            byte[] spkSig = Base64.getDecoder().decode((String) spkMap.get("signature"));
            int spkId = ((Number) spkMap.get("keyId")).intValue();
            creds.signedPreKey = new CurveUtils.SignedKeyPair(spkKeyPair, spkSig, spkId);
        } else {
            // Re-generate if missing
            creds.signedPreKey = CurveUtils.signedKeyPair(creds.signedIdentityKey, 1);
        }

        Map<String, String> meMap = (Map<String, String>) map.get("me");
        if (meMap != null) {
            creds.me = new WASocket.AuthCreds.UserInfo();
            creds.me.id = meMap.get("id");
            creds.me.name = meMap.get("name");
        }

        String routingInfoB64 = (String) map.get("routingInfo");
        if (routingInfoB64 != null) {
            creds.routingInfo = Base64.getDecoder().decode(routingInfoB64);
        }

        return creds;
    }
}
