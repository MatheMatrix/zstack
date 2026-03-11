package org.zstack.header.zwatch.resnotify;

import org.zstack.header.rest.APINoSee;
import org.zstack.header.search.Inventory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Inventory(mappingVOClass = ResNotifyWebhookRefVO.class)
public class ResNotifyWebhookRefInventory implements Serializable {
    private String uuid;
    private String webhookUrl;
    @APINoSee
    private String secret;
    private String customHeaders;

    public static ResNotifyWebhookRefInventory valueOf(ResNotifyWebhookRefVO vo) {
        ResNotifyWebhookRefInventory inv = new ResNotifyWebhookRefInventory();
        inv.setUuid(vo.getUuid());
        inv.setWebhookUrl(vo.getWebhookUrl());
        inv.setCustomHeaders(vo.getCustomHeaders());
        return inv;
    }

    public static List<ResNotifyWebhookRefInventory> valueOf(Collection<ResNotifyWebhookRefVO> vos) {
        List<ResNotifyWebhookRefInventory> invs = new ArrayList<>();
        for (ResNotifyWebhookRefVO vo : vos) {
            invs.add(valueOf(vo));
        }
        return invs;
    }

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
