package org.zstack.header.vm;

import org.zstack.header.message.APIReply;
import org.zstack.header.rest.RestResponse;

import java.util.List;

@RestResponse(fieldsTo = {"all"})
public class APIReadVmInstanceMetadataReply extends APIReply {
    private String metadataContent;
    private String schemaVersion;
    private String readStatus;
    private String repairAction;
    private List<String> warnings;

    public String getMetadataContent() {
        return metadataContent;
    }

    public void setMetadataContent(String metadataContent) {
        this.metadataContent = metadataContent;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getReadStatus() {
        return readStatus;
    }

    public void setReadStatus(String readStatus) {
        this.readStatus = readStatus;
    }

    public String getRepairAction() {
        return repairAction;
    }

    public void setRepairAction(String repairAction) {
        this.repairAction = repairAction;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public static APIReadVmInstanceMetadataReply __example__() {
        APIReadVmInstanceMetadataReply reply = new APIReadVmInstanceMetadataReply();
        reply.metadataContent = "{\"schemaVersion\":\"1.0\",\"vmUuid\":\"...\"}";
        reply.schemaVersion = "1.0";
        reply.readStatus = "OK";
        reply.repairAction = "NONE";
        reply.warnings = java.util.Collections.emptyList();
        return reply;
    }
}
