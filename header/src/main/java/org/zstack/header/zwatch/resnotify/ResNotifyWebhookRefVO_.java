package org.zstack.header.zwatch.resnotify;

import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@StaticMetamodel(ResNotifyWebhookRefVO.class)
public class ResNotifyWebhookRefVO_ {
    public static volatile SingularAttribute<ResNotifyWebhookRefVO, String> uuid;
    public static volatile SingularAttribute<ResNotifyWebhookRefVO, String> webhookUrl;
    public static volatile SingularAttribute<ResNotifyWebhookRefVO, String> secret;
    public static volatile SingularAttribute<ResNotifyWebhookRefVO, String> customHeaders;
}
