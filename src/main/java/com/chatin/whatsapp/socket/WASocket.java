package com.chatin.whatsapp.socket;

import com.chatin.whatsapp.binary.*;
import com.chatin.whatsapp.crypto.CryptoUtils;
import com.chatin.whatsapp.crypto.CurveUtils;
import com.chatin.whatsapp.noise.NoiseHandler;
import com.chatin.whatsapp.proto.WAProto;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * WASocket - Real WhatsApp Multi-Device WebSocket connection.
 * Implements Noise_XX_25519_AESGCM_SHA256 handshake, QR code generation,
 * keep-alive, and encrypted binary node messaging.
 */
public class WASocket {

    private static final Logger logger = LoggerFactory.getLogger(WASocket.class);

    public enum ConnectionState { CONNECTING, OPEN, CLOSING, CLOSED }

    private WAWebSocketClient ws;
    private NoiseHandler noise;
    private CurveUtils.KeyPair ephemeralKeyPair;

    private final AtomicInteger epoch = new AtomicInteger(1);
    private final String tagPrefix;
    private final Map<String, CompletableFuture<BinaryNode>> pendingQueries = new ConcurrentHashMap<>();

    // Handshake synchronization: raw bytes arrive here during handshake
    private final BlockingQueue<byte[]> handshakeQueue = new LinkedBlockingQueue<>();
    private volatile boolean handshakeComplete = false;

    private ScheduledExecutorService keepAliveExecutor;
    private ScheduledFuture<?> keepAliveFuture;
    private ScheduledExecutorService qrExecutor;
    private ScheduledFuture<?> qrRefreshFuture;
    private volatile ConnectionState state = ConnectionState.CLOSED;
    private volatile long lastDateRecv = System.currentTimeMillis();

    private AuthState authState;

    // QR state
    private int qrRetryCount = 0;
    private static final int MAX_QR_RETRIES = 5;
    private static final int QR_TIMEOUT_MS = 20000;

    // Timeouts
    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int KEEP_ALIVE_INTERVAL_MS = 30000;
    private static final int DEFAULT_QUERY_TIMEOUT_MS = 60000;

    // Event consumers
    private Consumer<String> onQrCode;
    private Consumer<Map<String, Object>> onConnectionUpdate;
    private Consumer<Map<String, Object>> onCredsUpdate;
    private Consumer<BinaryNode> onMessage;

    public WASocket() {
        this.tagPrefix = generateTagPrefix();
    }

    // =========== Auth Data Classes ===========

    public static class AuthState {
        public AuthCreds creds;
        public AuthKeys keys;
    }

    public static class AuthCreds {
        public CurveUtils.KeyPair noiseKey;
        public CurveUtils.KeyPair signedIdentityKey;
        public CurveUtils.KeyPair pairingEphemeralKeyPair;
        public String advSecretKey;
        public int registrationId;
        public int nextPreKeyId;
        public int firstUnuploadedPreKeyId;
        public CurveUtils.SignedKeyPair signedPreKey;
        public UserInfo me;
        public byte[] routingInfo;
        public String pairingCode;
        public String platform;

        public static class UserInfo {
            public String id;
            public String name;
            public String lid;
        }
    }

    public interface AuthKeys {
        Map<String, Object> get(String type, List<String> ids);
        void set(Map<String, Object> data);
    }

    // =========== Connection ===========

    /**
     * Start WhatsApp connection and perform Noise handshake.
     */
    public void connect(AuthState authState) throws Exception {
        this.authState = authState;
        this.state = ConnectionState.CONNECTING;
        this.handshakeComplete = false;
        this.qrRetryCount = 0;

        // Generate ephemeral key pair for this session
        ephemeralKeyPair = CurveUtils.generateKeyPair();

        // Initialize Noise handler
        noise = new NoiseHandler(
                ephemeralKeyPair.getPrivateKey(),
                ephemeralKeyPair.getPublicKey(),
                BinaryConstants.NOISE_WA_HEADER,
                authState.creds != null ? authState.creds.routingInfo : null
        );

        // Build WebSocket URL
        String wsUrl = BinaryConstants.WA_WEB_SOCKET_URL;
        if (authState.creds != null && authState.creds.routingInfo != null) {
            wsUrl += "?ED=" + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(authState.creds.routingInfo);
        }

        ws = new WAWebSocketClient(new URI(wsUrl));

        // Single unified message handler - routes based on handshake state
        ws.on("message", data -> {
            lastDateRecv = System.currentTimeMillis();
            byte[] bytes = (byte[]) data;

            if (!handshakeComplete) {
                // During handshake: put raw bytes into the queue
                handshakeQueue.offer(bytes);
            } else {
                // After handshake: decrypt and process as BinaryNode
                try {
                    noise.decodeFrame(bytes, frame -> {
                        if (frame instanceof BinaryNode node) {
                            handleDecryptedNode(node);
                        }
                    });
                } catch (Exception e) {
                    logger.error("Error decrypting message: {}", e.getMessage());
                }
            }
        });

        ws.on("open", data -> {
            // Run handshake in a separate thread so we can block-wait for responses
            CompletableFuture.runAsync(() -> {
                try {
                    performNoiseHandshake();
                } catch (Exception e) {
                    logger.error("Noise handshake failed: {}", e.getMessage(), e);
                    end(e);
                }
            });
        });

        ws.on("close", data -> end(new RuntimeException("WebSocket closed")));
        ws.on("error", data -> end((Exception) data));

        logger.info("Connecting to WhatsApp WebSocket...");
        ws.connectBlocking(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    // =========== Noise XX Handshake (3-way) ===========

    /**
     * Full Noise_XX_25519_AESGCM_SHA256 handshake:
     *   1. Client -> Server: ClientHello {ephemeral public key}
     *   2. Server -> Client: ServerHello {ephemeral, static(enc), payload(enc)}
     *   3. Client -> Server: ClientFinish {static(enc noise key), payload(enc ClientPayload)}
     */
    private void performNoiseHandshake() throws Exception {
        logger.info("Starting Noise XX handshake...");

        // ---- Step 1: Send ClientHello ----
        WAProto.HandshakeMessage clientHello = WAProto.HandshakeMessage.newBuilder()
                .setClientHello(
                        WAProto.ClientHello.newBuilder()
                                .setEphemeral(ByteString.copyFrom(ephemeralKeyPair.getPublicKey()))
                                .build()
                )
                .build();

        byte[] encodedHello = noise.encodeFrame(clientHello.toByteArray());
        ws.sendBinary(encodedHello);
        logger.info("ClientHello sent, waiting for ServerHello...");

        // ---- Step 2: Wait for and process ServerHello ----
        byte[] serverHelloRawFrame = handshakeQueue.poll(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (serverHelloRawFrame == null) {
            throw new RuntimeException("Timeout waiting for ServerHello");
        }

        // Decode the frame through noise (at this stage noise strips the 3-byte length header)
        CompletableFuture<byte[]> decodedFuture = new CompletableFuture<>();
        noise.decodeFrame(serverHelloRawFrame, frame -> {
            if (frame instanceof byte[] rawBytes) {
                decodedFuture.complete(rawBytes);
            }
        });

        byte[] serverHelloBytes = decodedFuture.get(5, TimeUnit.SECONDS);

        WAProto.HandshakeMessage serverHelloMsg = WAProto.HandshakeMessage.parseFrom(serverHelloBytes);
        WAProto.ServerHello serverHello = serverHelloMsg.getServerHello();

        if (serverHello == null) {
            throw new RuntimeException("No ServerHello in handshake response");
        }

        byte[] serverEphemeral = serverHello.getEphemeral().toByteArray();
        byte[] serverStaticCiphertext = serverHello.getStaticData().toByteArray();
        byte[] serverPayloadCiphertext = serverHello.getPayload().toByteArray();

        logger.info("ServerHello received (ephemeral: {} bytes, static: {} bytes, payload: {} bytes)",
                serverEphemeral.length, serverStaticCiphertext.length, serverPayloadCiphertext.length);

        // Process handshake: DH exchanges, decrypt server static & payload
        byte[] encryptedNoiseKey = noise.processHandshake(
                serverEphemeral,
                serverStaticCiphertext,
                serverPayloadCiphertext,
                authState.creds.noiseKey.getPrivateKey(),
                authState.creds.noiseKey.getPublicKey()
        );

        // ---- Step 3: Send ClientFinish ----
        byte[] clientPayloadBytes = buildClientPayload();
        byte[] encryptedPayload = noise.encrypt(clientPayloadBytes);

        WAProto.HandshakeMessage clientFinish = WAProto.HandshakeMessage.newBuilder()
                .setClientFinish(
                        WAProto.ClientFinish.newBuilder()
                                .setStaticData(ByteString.copyFrom(encryptedNoiseKey))
                                .setPayload(ByteString.copyFrom(encryptedPayload))
                                .build()
                )
                .build();

        byte[] encodedFinish = noise.encodeFrame(clientFinish.toByteArray());
        ws.sendBinary(encodedFinish);

        // Finalize noise state: split into separate read/write keys
        noise.finishInit();
        handshakeComplete = true;

        logger.info("Noise handshake completed. Encryption established.");

        // Start keep-alive
        startKeepAlive();

        // If not yet paired (no existing session), start QR code generation
        if (authState.creds.me == null) {
            logger.info("No existing session, starting QR code generation...");
            startQrCodeLoop();
        } else {
            logger.info("Resuming session for: {}", authState.creds.me.id);
            state = ConnectionState.OPEN;
            if (onConnectionUpdate != null) {
                onConnectionUpdate.accept(Map.of("connection", "open"));
            }
        }
    }

    // =========== ClientPayload Builder ===========

    /**
     * Build the ClientPayload protobuf (device registration info for WhatsApp).
     */
    private byte[] buildClientPayload() throws Exception {
        AuthCreds creds = authState.creds;

        WAProto.CompanionProps companionProps = WAProto.CompanionProps.newBuilder()
                .setOs("Chatin")
                .setPlatformType(WAProto.CompanionProps.CompanionPropsPlatformType.CHROME)
                .build();

        byte[] registrationIdBytes = ByteBuffer.allocate(4).putInt(creds.registrationId).array();

        WAProto.CompanionRegData.Builder regData = WAProto.CompanionRegData.newBuilder()
                .setERegid(ByteString.copyFrom(registrationIdBytes))
                .setEKeytype(ByteString.copyFrom(BinaryConstants.KEY_BUNDLE_TYPE))
                .setEIdent(ByteString.copyFrom(
                        CurveUtils.generateSignalPubKey(creds.signedIdentityKey.getPublicKey())))
                .setESkeyId(ByteString.copyFrom(encodeSignedPreKeyId(creds.signedPreKey.getKeyId())))
                .setESkeyVal(ByteString.copyFrom(
                        CurveUtils.generateSignalPubKey(creds.signedPreKey.getKeyPair().getPublicKey())))
                .setESkeySig(ByteString.copyFrom(creds.signedPreKey.getSignature()))
                .setCompanionProps(ByteString.copyFrom(companionProps.toByteArray()));

        WAProto.UserAgent userAgent = WAProto.UserAgent.newBuilder()
                .setPlatform(WAProto.UserAgent.UserAgentPlatform.WEB)
                .setAppVersion(WAProto.AppVersion.newBuilder()
                        .setPrimary(BinaryConstants.WA_VERSION[0])
                        .setSecondary(BinaryConstants.WA_VERSION[1])
                        .setTertiary(BinaryConstants.WA_VERSION[2])
                        .build())
                .setReleaseChannel(WAProto.UserAgent.UserAgentReleaseChannel.RELEASE)
                .setOsVersion("0.1")
                .setManufacturer("")
                .setDevice("Desktop")
                .setLocaleLanguageIso6391("en")
                .setLocaleCountryIso31661Alpha2("US")
                .build();

        WAProto.WebInfo webInfo = WAProto.WebInfo.newBuilder()
                .setWebSubPlatform(WAProto.WebInfo.WebInfoWebSubPlatform.WEB_BROWSER)
                .build();

        WAProto.ClientPayload.Builder payload = WAProto.ClientPayload.newBuilder()
                .setPassive(creds.me != null)
                .setUserAgent(userAgent)
                .setWebInfo(webInfo)
                .setSessionId(new SecureRandom().nextInt())
                .setShortConnect(true)
                .setConnectType(WAProto.ClientPayload.ConnectType.WIFI_UNKNOWN)
                .setConnectReason(WAProto.ClientPayload.ConnectReason.USER_ACTIVATED)
                .setRegData(regData.build());

        if (creds.me != null) {
            String jid = creds.me.id;
            String username = jid.contains("@") ? jid.split("@")[0] : jid;
            username = username.contains(":") ? username.split(":")[0] : username;
            payload.setUsername(Long.parseLong(username));
        }

        return payload.build().toByteArray();
    }

    private byte[] encodeSignedPreKeyId(int id) {
        return new byte[]{
                (byte) (id & 0xFF),
                (byte) ((id >> 8) & 0xFF),
                (byte) ((id >> 16) & 0xFF)
        };
    }

    // =========== QR Code Generation Loop ===========

    /**
     * Start the QR code generation loop.
     * Generates a fresh QR every ~20 seconds, max MAX_QR_RETRIES times.
     * QR format: ref,base64(noisePublicKey),base64(identityPublicKey),advSecretKey
     */
    private void startQrCodeLoop() {
        qrRetryCount = 0;
        generateAndEmitQr();

        qrExecutor = Executors.newSingleThreadScheduledExecutor();
        qrRefreshFuture = qrExecutor.scheduleAtFixedRate(() -> {
            qrRetryCount++;
            if (qrRetryCount >= MAX_QR_RETRIES) {
                logger.warn("QR retries exhausted ({}/{}), closing connection", qrRetryCount, MAX_QR_RETRIES);
                end(new RuntimeException("QR code scan timeout"));
                return;
            }
            generateAndEmitQr();
        }, QR_TIMEOUT_MS, QR_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private void generateAndEmitQr() {
        try {
            AuthCreds creds = authState.creds;

            // Unique reference for this particular QR
            String ref = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(CryptoUtils.randomBytes(16));

            String noiseKeyB64 = Base64.getEncoder().encodeToString(creds.noiseKey.getPublicKey());
            String identityKeyB64 = Base64.getEncoder().encodeToString(creds.signedIdentityKey.getPublicKey());
            String advSecret = creds.advSecretKey;

            String qrString = ref + "," + noiseKeyB64 + "," + identityKeyB64 + "," + advSecret;

            logger.info("QR code generated (attempt {}/{})", qrRetryCount + 1, MAX_QR_RETRIES);

            if (onQrCode != null) {
                onQrCode.accept(qrString);
            }
        } catch (Exception e) {
            logger.error("Failed to generate QR code: {}", e.getMessage());
        }
    }

    private void stopQrLoop() {
        if (qrRefreshFuture != null) qrRefreshFuture.cancel(true);
        if (qrExecutor != null) qrExecutor.shutdown();
    }

    // =========== Decrypted Message Handling ===========

    private void handleDecryptedNode(BinaryNode node) {
        String msgId = node.getAttr("id");

        // Response to pending query?
        if (msgId != null) {
            CompletableFuture<BinaryNode> pending = pendingQueries.get(msgId);
            if (pending != null) {
                pending.complete(node);
                return;
            }
        }

        String tag = node.getTag();

        // Pairing success (phone scanned QR)
        if ("success".equals(tag)) {
            handlePairingSuccess(node);
            return;
        }

        // Failure
        if ("failure".equals(tag)) {
            String reason = node.getAttr("reason");
            logger.error("Server failure: {}", reason);
            end(new RuntimeException("Server failure: " + reason));
            return;
        }

        // Route to generic handler
        if (onMessage != null) {
            onMessage.accept(node);
        }
    }

    private void handlePairingSuccess(BinaryNode node) {
        logger.info("Pairing successful!");
        stopQrLoop();

        String jid = node.getAttr("lid");
        if (jid == null) jid = node.getAttr("jid");

        if (jid != null && authState.creds != null) {
            authState.creds.me = new AuthCreds.UserInfo();
            authState.creds.me.id = jid;
            logger.info("Paired as: {}", jid);

            if (onCredsUpdate != null) {
                onCredsUpdate.accept(Map.of("creds", authState.creds));
            }
        }

        state = ConnectionState.OPEN;
        if (onConnectionUpdate != null) {
            onConnectionUpdate.accept(Map.of("connection", "open"));
        }
    }

    // =========== Sending ===========

    public BinaryNode query(BinaryNode node) throws Exception {
        return query(node, DEFAULT_QUERY_TIMEOUT_MS);
    }

    public BinaryNode query(BinaryNode node, int timeoutMs) throws Exception {
        if (node.getAttrs() == null) node.setAttrs(new HashMap<>());
        if (node.getAttr("id") == null) node.getAttrs().put("id", generateMessageTag());

        String msgId = node.getAttr("id");
        CompletableFuture<BinaryNode> future = new CompletableFuture<>();
        pendingQueries.put(msgId, future);

        try {
            sendNode(node);
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            logger.warn("Query timed out: {}", msgId);
            return null;
        } finally {
            pendingQueries.remove(msgId);
        }
    }

    public void sendNode(BinaryNode node) throws Exception {
        byte[] encoded = BinaryEncoder.encode(node);
        sendRaw(encoded);
    }

    private void sendRaw(byte[] data) throws Exception {
        if (ws == null || !ws.isConnected()) throw new RuntimeException("Connection closed");
        byte[] encrypted = noise.encodeFrame(data);
        ws.sendBinary(encrypted);
    }

    // =========== Keep-Alive ===========

    private void startKeepAlive() {
        keepAliveExecutor = Executors.newSingleThreadScheduledExecutor();
        keepAliveFuture = keepAliveExecutor.scheduleAtFixedRate(() -> {
            long diff = System.currentTimeMillis() - lastDateRecv;
            if (diff > KEEP_ALIVE_INTERVAL_MS + 5000) {
                end(new RuntimeException("Connection lost - no response"));
                return;
            }

            if (ws != null && ws.isConnected()) {
                try {
                    BinaryNode ping = new BinaryNode("iq", Map.of(
                            "id", generateMessageTag(),
                            "to", JidUtils.S_WHATSAPP_NET,
                            "type", "get",
                            "xmlns", "w:p"
                    ), List.of(new BinaryNode("ping", Map.of())));
                    query(ping);
                } catch (Exception e) {
                    logger.error("Keep-alive error: {}", e.getMessage());
                }
            }
        }, KEEP_ALIVE_INTERVAL_MS, KEEP_ALIVE_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    // =========== Lifecycle ===========

    public void end(Exception error) {
        if (state == ConnectionState.CLOSED) return;
        state = ConnectionState.CLOSED;

        if (error != null) logger.info("Connection ended: {}", error.getMessage());

        stopQrLoop();
        if (keepAliveFuture != null) keepAliveFuture.cancel(true);
        if (keepAliveExecutor != null) keepAliveExecutor.shutdown();
        if (ws != null) { try { ws.close(); } catch (Exception ignored) {} }

        if (onConnectionUpdate != null) {
            Map<String, Object> update = new HashMap<>();
            update.put("connection", "close");
            if (error != null) {
                update.put("lastDisconnect", Map.of("error", error.getMessage(), "date", new Date()));
            }
            onConnectionUpdate.accept(update);
        }

        pendingQueries.values().forEach(f -> f.completeExceptionally(new RuntimeException("Connection closed")));
        pendingQueries.clear();
    }

    public void logout() throws Exception {
        if (authState.creds != null && authState.creds.me != null) {
            sendNode(new BinaryNode("iq", Map.of(
                    "to", JidUtils.S_WHATSAPP_NET,
                    "type", "set",
                    "id", generateMessageTag(),
                    "xmlns", "md"
            ), List.of(new BinaryNode("remove-companion-device", Map.of(
                    "jid", authState.creds.me.id,
                    "reason", "user_initiated"
            )))));
        }
        end(new RuntimeException("Intentional Logout"));
    }

    // =========== Utilities ===========

    public String generateMessageTag() { return tagPrefix + epoch.getAndIncrement(); }
    private String generateTagPrefix() { return Long.toHexString(System.currentTimeMillis() / 1000) + "."; }

    // =========== Event Setters & Getters ===========

    public void setOnQrCode(Consumer<String> h) { this.onQrCode = h; }
    public void setOnConnectionUpdate(Consumer<Map<String, Object>> h) { this.onConnectionUpdate = h; }
    public void setOnCredsUpdate(Consumer<Map<String, Object>> h) { this.onCredsUpdate = h; }
    public void setOnMessage(Consumer<BinaryNode> h) { this.onMessage = h; }

    public ConnectionState getState() { return state; }
    public boolean isOpen() { return state == ConnectionState.OPEN; }
    public AuthState getAuthState() { return authState; }
}
