package org.zstack.header.console;

import org.springframework.http.HttpMethod;
import org.zstack.header.identity.Action;
import org.zstack.header.message.APIMessage;
import org.zstack.header.message.APIParam;
import org.zstack.header.rest.RestRequest;
import org.zstack.header.vm.VmInstanceVO;

@Action(category = ConsoleConstants.ACTION_CATEGORY)
@RestRequest(
        path = "/consoles/certfilepath/actions",
        method = HttpMethod.PUT,
        isAction = true,
        responseClass = APIUpdateCertFilePathEvent.class
)
public class APIUpdateCertFilePathMsg extends APIMessage {
    @APIParam
    private String certFilePath;

    public String getCertFilePath() {
        return certFilePath;
    }

    public void setCertFilePath(String certFilePath) {
        this.certFilePath = certFilePath;
    }

    public static APIUpdateCertFilePathMsg __example__() {
        APIUpdateCertFilePathMsg msg = new APIUpdateCertFilePathMsg();
        msg.setCertFilePath("/usr/local/zstack/zstack-ui/ui.keystore.pem");
        return msg;
    }
}
