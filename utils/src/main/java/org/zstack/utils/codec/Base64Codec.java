package org.zstack.utils.codec;

public class Base64Codec implements Codec {
    @Override
    public String encode(String data) {
        return java.util.Base64.getEncoder().encodeToString(data.getBytes());
    }

    @Override
    public String decode(String data) {
        try {
            return new String(java.util.Base64.getDecoder().decode(data), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Illegal Base64 String: " + e.getMessage(), e);
        }
    }
}