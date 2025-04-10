package org.zstack.utils.codec;

public class PlainTextCodec implements Codec {
    @Override
    public String encode(String data) {
        return data; // 明文编码：直接返回原始数据
    }

    @Override
    public String decode(String data) {
        return data; // 明文解码：直接返回原始数据
    }
}