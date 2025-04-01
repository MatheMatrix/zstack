package org.zstack.utils.codec;

public class CodecFactory {
    public static final String CODEC_TYPE_BASE64 = "base64";
    public static final String CODEC_TYPE_PLAINTEXT = "plaintext";

    public static Codec getCodec(String encodeType) {
        if (encodeType == null) {
            throw new IllegalArgumentException("Encode type cannot be null");
        }
        switch (encodeType.toLowerCase()) {
            case CODEC_TYPE_BASE64:
                return new Base64Codec();
            case CODEC_TYPE_PLAINTEXT:
                return new PlainTextCodec();
            default:
                throw new IllegalArgumentException("Unsupported encode type: " + encodeType);
        }
    }
}
