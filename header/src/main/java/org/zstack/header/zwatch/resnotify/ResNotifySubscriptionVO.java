package org.zstack.header.zwatch.resnotify;

import org.zstack.header.vo.NoView;
import org.zstack.header.vo.ResourceVO;
import org.zstack.header.vo.ToInventory;

import javax.persistence.*;
import java.sql.Timestamp;

@Entity
@Table
public class ResNotifySubscriptionVO extends ResourceVO implements ToInventory {
    @Column
    private String name;

    @Column
    private String description;

    @Column
    private String resourceTypes;

    @Column
    private String eventTypes;

    @Column
    @Enumerated(EnumType.STRING)
    private ResNotifyType type;

    @Column
    @Enumerated(EnumType.STRING)
    private ResNotifySubscriptionState state;

    @Column
    private String accountUuid;

    @Column
    private Timestamp createDate;

    @Column
    private Timestamp lastOpDate;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "uuid")
    @NoView
    private ResNotifyWebhookRefVO webhookRef;

    @PreUpdate
    private void preUpdate() {
        lastOpDate = null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getResourceTypes() {
        return resourceTypes;
    }

    public void setResourceTypes(String resourceTypes) {
        this.resourceTypes = resourceTypes;
    }

    public String getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(String eventTypes) {
        this.eventTypes = eventTypes;
    }

    public ResNotifyType getType() {
        return type;
    }

    public void setType(ResNotifyType type) {
        this.type = type;
    }

    public ResNotifySubscriptionState getState() {
        return state;
    }

    public void setState(ResNotifySubscriptionState state) {
        this.state = state;
    }

    public String getAccountUuid() {
        return accountUuid;
    }

    public void setAccountUuid(String accountUuid) {
        this.accountUuid = accountUuid;
    }

    public Timestamp getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Timestamp createDate) {
        this.createDate = createDate;
    }

    public Timestamp getLastOpDate() {
        return lastOpDate;
    }

    public void setLastOpDate(Timestamp lastOpDate) {
        this.lastOpDate = lastOpDate;
    }

    public ResNotifyWebhookRefVO getWebhookRef() {
        return webhookRef;
    }

    public void setWebhookRef(ResNotifyWebhookRefVO webhookRef) {
        this.webhookRef = webhookRef;
    }
}
