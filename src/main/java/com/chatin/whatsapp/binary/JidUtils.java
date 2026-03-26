package com.chatin.whatsapp.binary;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JID utilities - Java port of baileys WABinary/jid-utils.js
 * Handles WhatsApp JID parsing and encoding
 */
public class JidUtils {

    public static final String S_WHATSAPP_NET = "s.whatsapp.net";
    public static final String G_US = "g.us";
    public static final String BROADCAST = "broadcast";
    public static final String LID = "lid";

    private static final Pattern JID_PATTERN = Pattern.compile("^([^@:]+?)(?::([0-9]+))?@(.+)$");

    /**
     * Decoded JID representation
     */
    public static class DecodedJid {
        public String user;
        public String server;
        public Integer device;
        public Integer domainType;

        public DecodedJid(String user, String server, Integer device, Integer domainType) {
            this.user = user;
            this.server = server;
            this.device = device;
            this.domainType = domainType;
        }
    }

    /**
     * WAJIDDomains enumeration
     */
    public static final int DOMAIN_WHATSAPP = 0;
    public static final int DOMAIN_LID = 1;
    public static final int DOMAIN_HOSTED = 2;
    public static final int DOMAIN_HOSTED_LID = 3;

    /**
     * Decode a JID string into components
     */
    public static DecodedJid jidDecode(String jid) {
        if (jid == null || jid.isEmpty()) return null;

        Matcher matcher = JID_PATTERN.matcher(jid);
        if (!matcher.matches()) return null;

        String user = matcher.group(1);
        String deviceStr = matcher.group(2);
        String server = matcher.group(3);

        Integer device = deviceStr != null ? Integer.parseInt(deviceStr) : null;

        Integer domainType = null;
        if (LID.equals(server)) {
            domainType = DOMAIN_LID;
        } else if (S_WHATSAPP_NET.equals(server)) {
            domainType = DOMAIN_WHATSAPP;
        }

        return new DecodedJid(user, server, device, domainType);
    }

    /**
     * Encode a JID from components
     */
    public static String jidEncode(String user, String server, Integer device) {
        if (user == null) return null;

        StringBuilder sb = new StringBuilder(user);
        if (device != null && device != 0) {
            sb.append(":").append(device);
        }
        sb.append("@").append(server != null ? server : S_WHATSAPP_NET);
        return sb.toString();
    }

    /**
     * Encode JID without device
     */
    public static String jidEncode(String user, String server) {
        return jidEncode(user, server, null);
    }

    /**
     * Get the normalized JID (without device part)
     */
    public static String jidNormalizedUser(String jid) {
        DecodedJid decoded = jidDecode(jid);
        if (decoded == null) return jid;
        return jidEncode(decoded.user, decoded.server);
    }

    /**
     * Check if JID is a group
     */
    public static boolean isJidGroup(String jid) {
        return jid != null && jid.endsWith("@" + G_US);
    }

    /**
     * Check if JID is a broadcast
     */
    public static boolean isJidBroadcast(String jid) {
        return jid != null && jid.endsWith("@" + BROADCAST);
    }

    /**
     * Check if JID is a LID user
     */
    public static boolean isLidUser(String jid) {
        return jid != null && jid.endsWith("@" + LID);
    }

    /**
     * Check if JID is a status broadcast
     */
    public static boolean isJidStatusBroadcast(String jid) {
        return "status@broadcast".equals(jid);
    }

    /**
     * Transfer device from one JID to another
     */
    public static String transferDevice(String fromJid, String toJid) {
        DecodedJid from = jidDecode(fromJid);
        DecodedJid to = jidDecode(toJid);
        if (from == null || to == null) return toJid;
        return jidEncode(to.user, to.server, from.device);
    }
}
