package org.zstack.storage.encrypt;

import org.apache.commons.lang.StringUtils;
import org.zstack.core.db.Q;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.volume.VolumeStatus;
import org.zstack.header.volume.VolumeVO;
import org.zstack.header.volume.VolumeVO_;

import java.util.List;

import static org.zstack.core.Platform.operr;

public class VolumeEncryptionConversionGuard {
    public static ErrorCode validate(String vmUuid, String operation) {
        if (StringUtils.isBlank(vmUuid)) {
            return null;
        }

        List<String> volumeUuids = Q.New(VolumeVO.class)
                .select(VolumeVO_.uuid)
                .eq(VolumeVO_.vmInstanceUuid, vmUuid)
                .eq(VolumeVO_.status, VolumeStatus.Converting)
                .listValues();
        return volumeUuids.isEmpty() ? null : operr(
                "cannot %s VM[uuid:%s] because volume[uuid:%s] is being converted",
                operation, vmUuid, StringUtils.join(volumeUuids, ","));
    }

    public static void check(String vmUuid, String operation) {
        ErrorCode error = validate(vmUuid, operation);
        if (error != null) {
            throw new OperationFailureException(error);
        }
    }

    private VolumeEncryptionConversionGuard() {
    }
}
