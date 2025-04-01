package org.zstack.utils.codec;

public interface Codec {
    String encode(String data);
    String decode(String data);
}
