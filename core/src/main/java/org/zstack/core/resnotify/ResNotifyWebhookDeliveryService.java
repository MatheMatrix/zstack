package org.zstack.core.resnotify;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.EntityEvent;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.header.zwatch.resnotify.ResNotifyWebhookRefVO;
import org.zstack.utils.Utils;
import org.zstack.utils.gson.JSONObjectUtil;
import org.zstack.utils.logging.CLogger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ResNotifyWebhookDeliveryService {
    private static final CLogger logger = Utils.getLogger(ResNotifyWebhookDeliveryService.class);
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SIGNATURE_HEADER = "X-ZStack-Signature";
    private static final String TIMESTAMP_HEADER = "X-ZStack-Timestamp";
    private static final String EVENT_HEADER = "X-ZStack-Event";

    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private ThreadFacade thdf;

    public void deliverAsync(String subscriptionUuid, EntityEvent event, Object entity) {
        String payload = buildPayloadSnapshot(event, entity, subscriptionUuid);
        scheduleDeliveryAttempt(subscriptionUuid, event.name(), payload, 1);
    }

    private void scheduleDeliveryAttempt(String subscriptionUuid, String eventName,
                                         String payload, int attempt) {
        int maxRetries = ResNotifyGlobalConfig.WEBHOOK_MAX_RETRIES.value(Integer.class);
        int baseInterval = ResNotifyGlobalConfig.WEBHOOK_RETRY_INTERVAL_SECS.value(Integer.class);
        long delaySecs = attempt == 1 ? 0 : (long) (baseInterval * Math.pow(2, attempt - 2));

        thdf.submitTimeoutTask(() -> {
            ResNotifyWebhookRefVO webhookRef = dbf.findByUuid(subscriptionUuid, ResNotifyWebhookRefVO.class);
            if (webhookRef == null) {
                logger.warn(String.format("webhook ref for subscription[uuid:%s] not found, skipping",
                        subscriptionUuid));
                return;
            }

            try {
                deliver(webhookRef.getWebhookUrl(), payload, webhookRef.getSecret(),
                        webhookRef.getCustomHeaders(), eventName);
                logger.debug(String.format(
                        "webhook delivered for subscription[uuid:%s] event[%s]",
                        subscriptionUuid, eventName));
            } catch (Exception e) {
                if (attempt >= maxRetries) {
                    logger.error(String.format(
                            "webhook delivery failed after %d attempts for subscription[uuid:%s]: %s",
                            attempt, subscriptionUuid, e.getMessage()));
                    return;
                }
                logger.warn(String.format(
                        "webhook delivery attempt %d/%d failed for subscription[uuid:%s]: %s",
                        attempt, maxRetries, subscriptionUuid, e.getMessage()));
                scheduleDeliveryAttempt(subscriptionUuid, eventName, payload, attempt + 1);
            }
        }, TimeUnit.SECONDS, delaySecs);
    }

    private String buildPayloadSnapshot(EntityEvent event, Object entity, String subscriptionUuid) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("subscriptionUuid", subscriptionUuid);
        payload.put("eventType", event.name());
        payload.put("resourceType", entity.getClass().getSimpleName());
        payload.put("timestamp", new Timestamp(System.currentTimeMillis()).toString());

        try {
            String entityJson = JSONObjectUtil.toJsonString(entity);
            payload.put("data", JSONObjectUtil.toObject(entityJson, Map.class));
        } catch (Exception e) {
            payload.put("data", entity.toString());
            logger.warn(String.format("failed to serialize entity to JSON: %s", e.getMessage()));
        }

        return JSONObjectUtil.toJsonString(payload);
    }

    private static final Set<String> RESERVED_HEADERS = new HashSet<>(Arrays.asList(
            SIGNATURE_HEADER.toLowerCase(), TIMESTAMP_HEADER.toLowerCase(),
            EVENT_HEADER.toLowerCase(), "host", "content-type", "content-length"
    ));

    private void deliver(String webhookUrl, String payload, String secret,
                         String customHeadersJson, String eventType) throws Exception {
        int timeout = ResNotifyGlobalConfig.WEBHOOK_DELIVERY_TIMEOUT_SECS.value(Integer.class) * 1000;

        URL url = new URL(webhookUrl);
        validateWebhookUrl(url);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            // Apply custom headers first, before security headers
            if (customHeadersJson != null && !customHeadersJson.isEmpty()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, String> headers = JSONObjectUtil.toObject(customHeadersJson,
                            java.util.LinkedHashMap.class);
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        if (!RESERVED_HEADERS.contains(entry.getKey().toLowerCase())) {
                            conn.setRequestProperty(entry.getKey(), entry.getValue());
                        }
                    }
                } catch (Exception e) {
                    logger.warn(String.format("failed to parse custom headers: %s", e.getMessage()));
                }
            }

            // Security headers applied after custom headers to prevent override
            conn.setRequestProperty(EVENT_HEADER, eventType);
            conn.setRequestProperty(TIMESTAMP_HEADER, String.valueOf(System.currentTimeMillis()));

            if (secret != null && !secret.isEmpty()) {
                String signature = computeHmacSha256(payload, secret);
                conn.setRequestProperty(SIGNATURE_HEADER, "sha256=" + signature);
            }

            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new RuntimeException(String.format(
                        "webhook delivery failed with HTTP %d for url: %s",
                        responseCode, webhookUrl));
            }
        } finally {
            conn.disconnect();
        }
    }

    private void validateWebhookUrl(URL url) throws Exception {
        String protocol = url.getProtocol();
        if (!"http".equals(protocol) && !"https".equals(protocol)) {
            throw new IllegalArgumentException(String.format(
                    "webhook URL must use http or https protocol, got: %s", protocol));
        }

        InetAddress address = InetAddress.getByName(url.getHost());
        if (address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isAnyLocalAddress()) {
            throw new IllegalArgumentException(String.format(
                    "webhook URL must not point to private/local address: %s", url.getHost()));
        }
    }

    private String computeHmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec keySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
        mac.init(keySpec);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
