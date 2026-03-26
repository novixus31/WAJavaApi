package com.chatin.whatsapp.binary;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Binary encoder - Java port of baileys WABinary/encode.js
 * Encodes BinaryNode objects into WhatsApp's binary protocol format
 */
public class BinaryEncoder {

    /**
     * Encode a BinaryNode to byte array
     */
    public static byte[] encode(BinaryNode node) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        buffer.write(0); // first byte is always 0
        encodeNode(node, buffer);
        return buffer.toByteArray();
    }

    private static void encodeNode(BinaryNode node, ByteArrayOutputStream buffer) {
        Map<String, String> attrs = node.getAttrs();
        Object content = node.getContent();

        int validAttrsCount = 0;
        if (attrs != null) {
            for (Map.Entry<String, String> entry : attrs.entrySet()) {
                if (entry.getValue() != null) validAttrsCount++;
            }
        }

        int listSize = 2 * validAttrsCount + 1 + (content != null ? 1 : 0);
        writeListStart(listSize, buffer);
        writeString(node.getTag(), buffer);

        if (attrs != null) {
            for (Map.Entry<String, String> entry : attrs.entrySet()) {
                if (entry.getValue() != null) {
                    writeString(entry.getKey(), buffer);
                    writeString(entry.getValue(), buffer);
                }
            }
        }

        if (content instanceof String str) {
            writeString(str, buffer);
        } else if (content instanceof byte[] bytes) {
            writeByteLength(bytes.length, buffer);
            buffer.writeBytes(bytes);
        } else if (content instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<BinaryNode> children = (List<BinaryNode>) content;
            writeListStart(children.size(), buffer);
            for (BinaryNode child : children) {
                encodeNode(child, buffer);
            }
        }
    }

    private static void writeListStart(int listSize, ByteArrayOutputStream buffer) {
        if (listSize == 0) {
            buffer.write(BinaryConstants.TAGS.LIST_EMPTY);
        } else if (listSize < 256) {
            buffer.write(BinaryConstants.TAGS.LIST_8);
            buffer.write(listSize);
        } else {
            buffer.write(BinaryConstants.TAGS.LIST_16);
            buffer.write((listSize >> 8) & 0xFF);
            buffer.write(listSize & 0xFF);
        }
    }

    private static void writeString(String str, ByteArrayOutputStream buffer) {
        if (str == null || str.isEmpty()) {
            buffer.write(BinaryConstants.TAGS.LIST_EMPTY);
            return;
        }

        // Check token map
        Integer tokenIndex = BinaryConstants.getTokenIndex(str);
        if (tokenIndex != null) {
            buffer.write(tokenIndex);
            return;
        }

        // Check if it's a JID
        JidUtils.DecodedJid decoded = JidUtils.jidDecode(str);
        if (decoded != null) {
            writeJid(decoded, buffer);
            return;
        }

        // Check if nibble-encodable
        if (isNibble(str)) {
            writePackedBytes(str, true, buffer);
            return;
        }

        // Check if hex-encodable
        if (isHex(str)) {
            writePackedBytes(str, false, buffer);
            return;
        }

        // Write as raw string
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        writeByteLength(bytes.length, buffer);
        buffer.writeBytes(bytes);
    }

    private static void writeJid(JidUtils.DecodedJid jid, ByteArrayOutputStream buffer) {
        if (jid.device != null) {
            buffer.write(BinaryConstants.TAGS.AD_JID);
            buffer.write(jid.domainType != null ? jid.domainType : 0);
            buffer.write(jid.device);
            writeString(jid.user, buffer);
        } else {
            buffer.write(BinaryConstants.TAGS.JID_PAIR);
            if (jid.user != null && !jid.user.isEmpty()) {
                writeString(jid.user, buffer);
            } else {
                buffer.write(BinaryConstants.TAGS.LIST_EMPTY);
            }
            writeString(jid.server, buffer);
        }
    }

    private static void writeByteLength(int length, ByteArrayOutputStream buffer) {
        if (length >= 1 << 20) {
            buffer.write(BinaryConstants.TAGS.BINARY_32);
            buffer.write((length >> 24) & 0xFF);
            buffer.write((length >> 16) & 0xFF);
            buffer.write((length >> 8) & 0xFF);
            buffer.write(length & 0xFF);
        } else if (length >= 256) {
            buffer.write(BinaryConstants.TAGS.BINARY_20);
            buffer.write((length >> 16) & 0x0F);
            buffer.write((length >> 8) & 0xFF);
            buffer.write(length & 0xFF);
        } else {
            buffer.write(BinaryConstants.TAGS.BINARY_8);
            buffer.write(length);
        }
    }

    private static boolean isNibble(String str) {
        if (str == null || str.length() > BinaryConstants.TAGS.PACKED_MAX) return false;
        for (char c : str.toCharArray()) {
            if (!((c >= '0' && c <= '9') || c == '-' || c == '.')) return false;
        }
        return true;
    }

    private static boolean isHex(String str) {
        if (str == null || str.length() > BinaryConstants.TAGS.PACKED_MAX) return false;
        for (char c : str.toCharArray()) {
            if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }

    private static void writePackedBytes(String str, boolean isNibble, ByteArrayOutputStream buffer) {
        buffer.write(isNibble ? BinaryConstants.TAGS.NIBBLE_8 : BinaryConstants.TAGS.HEX_8);
        int roundedLength = (int) Math.ceil(str.length() / 2.0);
        if (str.length() % 2 != 0) {
            roundedLength |= 128;
        }
        buffer.write(roundedLength);

        for (int i = 0; i < str.length() / 2; i++) {
            int high = isNibble ? packNibble(str.charAt(2 * i)) : packHex(str.charAt(2 * i));
            int low = isNibble ? packNibble(str.charAt(2 * i + 1)) : packHex(str.charAt(2 * i + 1));
            buffer.write((high << 4) | low);
        }
        if (str.length() % 2 != 0) {
            int high = isNibble ? packNibble(str.charAt(str.length() - 1)) : packHex(str.charAt(str.length() - 1));
            buffer.write((high << 4) | 15); // pad with 0xF
        }
    }

    private static int packNibble(char c) {
        return switch (c) {
            case '-' -> 10;
            case '.' -> 11;
            default -> {
                if (c >= '0' && c <= '9') yield c - '0';
                throw new RuntimeException("Invalid nibble character: " + c);
            }
        };
    }

    private static int packHex(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'A' && c <= 'F') return 10 + c - 'A';
        if (c >= 'a' && c <= 'f') return 10 + c - 'a';
        throw new RuntimeException("Invalid hex character: " + c);
    }
}
