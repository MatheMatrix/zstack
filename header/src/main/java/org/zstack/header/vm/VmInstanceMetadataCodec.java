package org.zstack.header.vm;

import org.zstack.utils.gson.JSONObjectUtil;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 虚拟机元数据编解码器。
 *
 * <p>负责 {@link VmInstanceMetadataDTO} 与存储介质之间的编解码：
 * <pre>
 *   序列化流程：DTO → JSON String → Base64 String → byte[]（写入存储）
 *   反序列化流程：byte[]（读取存储） → Base64 String → JSON String → DTO
 * </pre>
 *
 * <p>单层 Base64 编码策略：DTO 内部所有字段为明文 JSON，
 * 仅在写入存储时做一次 Base64 编码。</p>
 */
public class VmInstanceMetadataCodec {

    private VmInstanceMetadataCodec() {
    }

    /**
     * 将 DTO 编码为可写入存储的字节数组。
     *
     * @param dto 元数据 DTO
     * @return Base64 编码后的字节数组
     */
    public static byte[] encode(VmInstanceMetadataDTO dto) {
        String json = JSONObjectUtil.toJsonString(dto);
        return Base64.getEncoder().encode(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 将 DTO 编码为 Base64 字符串。
     *
     * @param dto 元数据 DTO
     * @return Base64 编码后的字符串
     */
    public static String encodeToString(VmInstanceMetadataDTO dto) {
        String json = JSONObjectUtil.toJsonString(dto);
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 从存储读取的字节数组解码为 DTO。
     *
     * @param data Base64 编码的字节数组
     * @return 元数据 DTO
     * @throws IllegalArgumentException 如果 Base64 解码失败或 JSON 格式错误
     */
    public static VmInstanceMetadataDTO decode(byte[] data) {
        byte[] jsonBytes = Base64.getDecoder().decode(data);
        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        return JSONObjectUtil.toObject(json, VmInstanceMetadataDTO.class);
    }

    /**
     * 从 Base64 字符串解码为 DTO。
     *
     * @param base64 Base64 编码的字符串
     * @return 元数据 DTO
     * @throws IllegalArgumentException 如果 Base64 解码失败或 JSON 格式错误
     */
    public static VmInstanceMetadataDTO decodeFromString(String base64) {
        byte[] jsonBytes = Base64.getDecoder().decode(base64);
        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        return JSONObjectUtil.toObject(json, VmInstanceMetadataDTO.class);
    }

    /**
     * 将 DTO 序列化为 JSON 字符串（不做 Base64 编码）。
     *
     * <p>用于调试、日志、一致性检查等场景。</p>
     *
     * @param dto 元数据 DTO
     * @return JSON 字符串
     */
    public static String toJson(VmInstanceMetadataDTO dto) {
        return JSONObjectUtil.toJsonString(dto);
    }

    /**
     * 从 JSON 字符串反序列化为 DTO（不做 Base64 解码）。
     *
     * <p>用于调试、测试等场景。</p>
     *
     * @param json JSON 字符串
     * @return 元数据 DTO
     */
    public static VmInstanceMetadataDTO fromJson(String json) {
        return JSONObjectUtil.toObject(json, VmInstanceMetadataDTO.class);
    }
}