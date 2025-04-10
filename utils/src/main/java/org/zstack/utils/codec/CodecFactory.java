package org.zstack.utils.codec;

public class CodecFactory {
    public static Codec getCodec(String encodeType) {
        switch (encodeType.toLowerCase()) {
            case "base64":
                return new Base64Codec();
            case "plaintext": // 支持明文处理
                return new PlainTextCodec();
            default:
                throw new IllegalArgumentException("Unsupported encode type: " + encodeType);
        }
    }
}
