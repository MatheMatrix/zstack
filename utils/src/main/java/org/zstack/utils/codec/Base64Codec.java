package org.zstack.utils.codec;

public class Base64Codec implements Codec {
    @Override
    public String encode(String data) {
        return java.util.Base64.getEncoder().encodeToString(data.getBytes());
    }

    @Override
    public String decode(String data) {
        return new String(java.util.Base64.getDecoder().decode(data));
    }
}