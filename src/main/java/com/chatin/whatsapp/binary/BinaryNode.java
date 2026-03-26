package com.chatin.whatsapp.binary;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Binary node - Java representation of WhatsApp binary XML nodes
 * Equivalent to { tag, attrs, content } in baileys
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BinaryNode {

    private String tag;
    private Map<String, String> attrs;
    private Object content; // Can be: String, byte[], List<BinaryNode>, or null

    public BinaryNode(String tag, Map<String, String> attrs) {
        this.tag = tag;
        this.attrs = attrs;
    }

    /**
     * Get a child node by tag
     */
    public BinaryNode getChild(String childTag) {
        if (content instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<BinaryNode> children = (List<BinaryNode>) content;
            for (BinaryNode child : children) {
                if (childTag.equals(child.getTag())) {
                    return child;
                }
            }
        }
        return null;
    }

    /**
     * Get all child nodes with a given tag
     */
    @SuppressWarnings("unchecked")
    public List<BinaryNode> getChildren(String childTag) {
        if (content instanceof List<?>) {
            List<BinaryNode> children = (List<BinaryNode>) content;
            return children.stream()
                    .filter(c -> childTag.equals(c.getTag()))
                    .toList();
        }
        return List.of();
    }

    /**
     * Get content as byte array
     */
    public byte[] getContentBytes() {
        if (content instanceof byte[]) {
            return (byte[]) content;
        }
        if (content instanceof String) {
            return ((String) content).getBytes();
        }
        return null;
    }

    /**
     * Get content as string
     */
    public String getContentString() {
        if (content instanceof String) {
            return (String) content;
        }
        if (content instanceof byte[]) {
            return new String((byte[]) content);
        }
        return null;
    }

    /**
     * Get attribute value
     */
    public String getAttr(String key) {
        if (attrs != null) {
            return attrs.get(key);
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("<").append(tag);
        if (attrs != null) {
            attrs.forEach((k, v) -> sb.append(" ").append(k).append("=\"").append(v).append("\""));
        }
        if (content == null) {
            sb.append("/>");
        } else if (content instanceof List<?>) {
            sb.append(">");
            @SuppressWarnings("unchecked")
            List<BinaryNode> children = (List<BinaryNode>) content;
            for (BinaryNode child : children) {
                sb.append(child.toString());
            }
            sb.append("</").append(tag).append(">");
        } else {
            sb.append(">").append(content instanceof byte[] ? "[binary]" : content).append("</").append(tag).append(">");
        }
        return sb.toString();
    }
}
