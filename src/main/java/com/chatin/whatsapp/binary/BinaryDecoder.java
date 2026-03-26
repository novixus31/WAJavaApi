package com.chatin.whatsapp.binary;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Inflater;

/**
 * Binary decoder - Java port of baileys WABinary/decode.js
 * Decodes WhatsApp's binary protocol format into BinaryNode objects
 */
public class BinaryDecoder {

    /**
     * Decode a binary buffer into a BinaryNode (with decompression if needed)
     */
    public static BinaryNode decode(byte[] buffer) throws Exception {
        byte[] decompressed = decompressIfRequired(buffer);
        int[] indexRef = {0};
        return decodeNode(decompressed, indexRef);
    }

    /**
     * Decompress buffer if zlib compressed
     */
    private static byte[] decompressIfRequired(byte[] buffer) throws Exception {
        if (buffer.length == 0) return buffer;

        if ((buffer[0] & 2) != 0) {
            // Zlib compressed
            Inflater inflater = new Inflater();
            inflater.setInput(buffer, 1, buffer.length - 1);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] tmp = new byte[4096];
            while (!inflater.finished()) {
                int count = inflater.inflate(tmp);
                outputStream.write(tmp, 0, count);
            }
            inflater.end();
            return outputStream.toByteArray();
        } else {
            // No compression, skip first byte
            byte[] result = new byte[buffer.length - 1];
            System.arraycopy(buffer, 1, result, 0, result.length);
            return result;
        }
    }

    /**
     * Decode a single node from the buffer
     */
    private static BinaryNode decodeNode(byte[] buffer, int[] indexRef) throws Exception {
        int listSize = readListSize(readByte(buffer, indexRef), buffer, indexRef);
        String tag = readString(readByte(buffer, indexRef), buffer, indexRef);

        if (listSize == 0 || tag == null || tag.isEmpty()) {
            throw new RuntimeException("Invalid node");
        }

        // Read attributes
        Map<String, String> attrs = new HashMap<>();
        int attributesLength = (listSize - 1) >> 1;
        for (int i = 0; i < attributesLength; i++) {
            String key = readString(readByte(buffer, indexRef), buffer, indexRef);
            String value = readString(readByte(buffer, indexRef), buffer, indexRef);
            attrs.put(key, value);
        }

        // Read content if list size is even
        Object content = null;
        if (listSize % 2 == 0) {
            int tag2 = readByte(buffer, indexRef);
            if (isListTag(tag2)) {
                content = readList(tag2, buffer, indexRef);
            } else {
                switch (tag2) {
                    case BinaryConstants.TAGS.BINARY_8:
                        content = readBytes(readByte(buffer, indexRef), buffer, indexRef);
                        break;
                    case BinaryConstants.TAGS.BINARY_20:
                        content = readBytes(readInt20(buffer, indexRef), buffer, indexRef);
                        break;
                    case BinaryConstants.TAGS.BINARY_32:
                        content = readBytes(readInt(4, buffer, indexRef), buffer, indexRef);
                        break;
                    default:
                        content = readString(tag2, buffer, indexRef);
                        break;
                }
            }
        }

        return new BinaryNode(tag, attrs, content);
    }

    private static int readByte(byte[] buffer, int[] indexRef) {
        return buffer[indexRef[0]++] & 0xFF;
    }

    private static byte[] readBytes(int n, byte[] buffer, int[] indexRef) {
        byte[] result = new byte[n];
        System.arraycopy(buffer, indexRef[0], result, 0, n);
        indexRef[0] += n;
        return result;
    }

    private static int readInt(int n, byte[] buffer, int[] indexRef) {
        int val = 0;
        for (int i = 0; i < n; i++) {
            val = (val << 8) | (buffer[indexRef[0]++] & 0xFF);
        }
        return val;
    }

    private static int readInt20(byte[] buffer, int[] indexRef) {
        return ((buffer[indexRef[0]++] & 0x0F) << 16) |
               ((buffer[indexRef[0]++] & 0xFF) << 8) |
               (buffer[indexRef[0]++] & 0xFF);
    }

    private static boolean isListTag(int tag) {
        return tag == BinaryConstants.TAGS.LIST_EMPTY ||
               tag == BinaryConstants.TAGS.LIST_8 ||
               tag == BinaryConstants.TAGS.LIST_16;
    }

    private static int readListSize(int tag, byte[] buffer, int[] indexRef) {
        return switch (tag) {
            case BinaryConstants.TAGS.LIST_EMPTY -> 0;
            case BinaryConstants.TAGS.LIST_8 -> readByte(buffer, indexRef);
            case BinaryConstants.TAGS.LIST_16 -> readInt(2, buffer, indexRef);
            default -> throw new RuntimeException("Invalid list size tag: " + tag);
        };
    }

    private static List<BinaryNode> readList(int tag, byte[] buffer, int[] indexRef) throws Exception {
        int size = readListSize(tag, buffer, indexRef);
        List<BinaryNode> items = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            items.add(decodeNode(buffer, indexRef));
        }
        return items;
    }

    private static String readString(int tag, byte[] buffer, int[] indexRef) throws Exception {
        if (tag >= 1 && tag < BinaryConstants.SINGLE_BYTE_TOKENS.length) {
            return BinaryConstants.SINGLE_BYTE_TOKENS[tag];
        }

        return switch (tag) {
            case BinaryConstants.TAGS.DICTIONARY_0, BinaryConstants.TAGS.DICTIONARY_1,
                 BinaryConstants.TAGS.DICTIONARY_2, BinaryConstants.TAGS.DICTIONARY_3 -> {
                int dictIndex = tag - BinaryConstants.TAGS.DICTIONARY_0;
                int tokenIndex = readByte(buffer, indexRef);
                yield BinaryConstants.getDoubleToken(dictIndex, tokenIndex);
            }
            case BinaryConstants.TAGS.LIST_EMPTY -> "";
            case BinaryConstants.TAGS.BINARY_8 -> {
                int len = readByte(buffer, indexRef);
                yield new String(readBytes(len, buffer, indexRef), StandardCharsets.UTF_8);
            }
            case BinaryConstants.TAGS.BINARY_20 -> {
                int len = readInt20(buffer, indexRef);
                yield new String(readBytes(len, buffer, indexRef), StandardCharsets.UTF_8);
            }
            case BinaryConstants.TAGS.BINARY_32 -> {
                int len = readInt(4, buffer, indexRef);
                yield new String(readBytes(len, buffer, indexRef), StandardCharsets.UTF_8);
            }
            case BinaryConstants.TAGS.JID_PAIR -> readJidPair(buffer, indexRef);
            case BinaryConstants.TAGS.AD_JID -> readAdJid(buffer, indexRef);
            case BinaryConstants.TAGS.NIBBLE_8, BinaryConstants.TAGS.HEX_8 -> readPacked8(tag, buffer, indexRef);
            default -> throw new RuntimeException("Invalid string tag: " + tag);
        };
    }

    private static String readJidPair(byte[] buffer, int[] indexRef) throws Exception {
        String user = readString(readByte(buffer, indexRef), buffer, indexRef);
        String server = readString(readByte(buffer, indexRef), buffer, indexRef);
        if (server != null && !server.isEmpty()) {
            return (user != null ? user : "") + "@" + server;
        }
        throw new RuntimeException("Invalid JID pair");
    }

    private static String readAdJid(byte[] buffer, int[] indexRef) throws Exception {
        int domainType = readByte(buffer, indexRef);
        int device = readByte(buffer, indexRef);
        String user = readString(readByte(buffer, indexRef), buffer, indexRef);

        String server = JidUtils.S_WHATSAPP_NET;
        if (domainType == JidUtils.DOMAIN_LID) server = JidUtils.LID;
        else if (domainType == JidUtils.DOMAIN_HOSTED) server = "hosted";
        else if (domainType == JidUtils.DOMAIN_HOSTED_LID) server = "hosted.lid";

        return JidUtils.jidEncode(user, server, device);
    }

    private static String readPacked8(int tag, byte[] buffer, int[] indexRef) {
        int startByte = readByte(buffer, indexRef);
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < (startByte & 127); i++) {
            int curByte = readByte(buffer, indexRef);
            value.append((char) unpackByte(tag, (curByte & 0xF0) >> 4));
            value.append((char) unpackByte(tag, curByte & 0x0F));
        }
        if ((startByte >> 7) != 0) {
            return value.substring(0, value.length() - 1);
        }
        return value.toString();
    }

    private static int unpackByte(int tag, int value) {
        if (tag == BinaryConstants.TAGS.NIBBLE_8) {
            return unpackNibble(value);
        } else {
            return unpackHex(value);
        }
    }

    private static int unpackNibble(int value) {
        if (value >= 0 && value <= 9) return '0' + value;
        return switch (value) {
            case 10 -> '-';
            case 11 -> '.';
            case 15 -> '\0';
            default -> throw new RuntimeException("Invalid nibble: " + value);
        };
    }

    private static int unpackHex(int value) {
        if (value >= 0 && value < 16) {
            return value < 10 ? '0' + value : 'A' + value - 10;
        }
        throw new RuntimeException("Invalid hex: " + value);
    }
}
