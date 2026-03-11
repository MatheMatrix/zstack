package org.zstack.header.zwatch.resnotify;

import javax.persistence.*;

@Entity
@Table
public class ResNotifyWebhookRefVO {
    @Id
    @Column
    private String uuid;

    @Column
    private String webhookUrl;

    @Column
    private String secret;

    @Column
    private String customHeaders;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getCustomHeaders() {
        return customHeaders;
    }

    public void setCustomHeaders(String customHeaders) {
        this.customHeaders = customHeaders;
    }
}
