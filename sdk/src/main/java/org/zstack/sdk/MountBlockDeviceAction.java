package org.zstack.sdk;

import java.util.HashMap;
import java.util.Map;
import org.zstack.sdk.*;

public class MountBlockDeviceAction extends AbstractAction {

    private static final HashMap<String, Parameter> parameterMap = new HashMap<>();

    private static final HashMap<String, Parameter> nonAPIParameterMap = new HashMap<>();

    public static class Result {
        public ErrorCode error;
        public org.zstack.sdk.MountBlockDeviceResult value;

        public Result throwExceptionIfError() {
            if (error != null) {
                throw new ApiException(
                    String.format("error[code: %s, description: %s, details: %s]", error.code, error.description, error.details)
                );
            }
            
            return this;
        }
    }

    @Param(required = true, maxLength = 255, nonempty = false, nullElements = false, emptyString = true, noTrim = false)
    public java.lang.String username;

    @Param(required = true, maxLength = 255, nonempty = false, nullElements = false, emptyString = true, noTrim = false)
    public java.lang.String password;

    @Param(required = true, nonempty = false, nullElements = false, emptyString = true, numberRange = {1L,65535L}, noTrim = false)
    public java.lang.Integer sshPort;

    @Param(required = true, maxLength = 255, nonempty = false, nullElements = false, emptyString = true, noTrim = false)
    public java.lang.String hostName;

    @Param(required = true, maxLength = 255, nonempty = false, nullElements = false, emptyString = true, noTrim = false)
    public java.lang.String blockDevicePath;

    @Param(required = true, maxLength = 2048, nonempty = false, nullElements = false, emptyString = true, noTrim = false)
    public java.lang.String mountPoint;

    @Param(required = false, validValues = {"btrfs","cramfs","ext2","ext3","ext4","fat","minix","msdos","ntfs","vfat","xfs"}, maxLength = 255, nonempty = false, nullElements = false, emptyString = true, noTrim = false)
    public java.lang.String filesystemType = "xfs";

    @Param(required = false)
    public java.util.List systemTags;

    @Param(required = false)
    public java.util.List userTags;

    @Param(required = false)
    public String sessionId;

    @Param(required = false)
    public String accessKeyId;

    @Param(required = false)
    public String accessKeySecret;

    @Param(required = false)
    public String requestIp;

    @NonAPIParam
    public long timeout = -1;

    @NonAPIParam
    public long pollingInterval = -1;


    private Result makeResult(ApiResult res) {
        Result ret = new Result();
        if (res.error != null) {
            ret.error = res.error;
            return ret;
        }
        
        org.zstack.sdk.MountBlockDeviceResult value = res.getResult(org.zstack.sdk.MountBlockDeviceResult.class);
        ret.value = value == null ? new org.zstack.sdk.MountBlockDeviceResult() : value; 

        return ret;
    }

    public Result call() {
        ApiResult res = ZSClient.call(this);
        return makeResult(res);
    }

    public void call(final Completion<Result> completion) {
        ZSClient.call(this, new InternalCompletion() {
            @Override
            public void complete(ApiResult res) {
                completion.complete(makeResult(res));
            }
        });
    }

    protected Map<String, Parameter> getParameterMap() {
        return parameterMap;
    }

    protected Map<String, Parameter> getNonAPIParameterMap() {
        return nonAPIParameterMap;
    }

    protected RestInfo getRestInfo() {
        RestInfo info = new RestInfo();
        info.httpMethod = "POST";
        info.path = "/host/mount-block-device";
        info.needSession = true;
        info.needPoll = true;
        info.parameterName = "params";
        return info;
    }

}
