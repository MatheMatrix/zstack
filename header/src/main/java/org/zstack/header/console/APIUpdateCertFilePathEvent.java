package org.zstack.header.console;

import org.zstack.header.message.APIEvent;
import org.zstack.header.rest.RestResponse;

/**
 * Created with IntelliJ IDEA.
 * User: frank
 * Time: 11:27 PM
 * To change this template use File | Settings | File Templates.
 */
@RestResponse(allTo = "certFilePath")
public class APIUpdateCertFilePathEvent extends APIEvent {
    private String certFilePath;

    public APIUpdateCertFilePathEvent() {
        super(null);
    }

    public APIUpdateCertFilePathEvent(String apiId) {
        super(apiId);
    }

    public String getCertFilePath() {
        return certFilePath;
    }

    public void setCertFilePath(String certFilePath) {
        this.certFilePath = certFilePath;
    }

    public static APIUpdateCertFilePathEvent __example__() {
        APIUpdateCertFilePathEvent event = new APIUpdateCertFilePathEvent();
        event.setCertFilePath("/usr/local/zstack/zstack-ui/ui.keystore.pem");
        return event;
    }

}
