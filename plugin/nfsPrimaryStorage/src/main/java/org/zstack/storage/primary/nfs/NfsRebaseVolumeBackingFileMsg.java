package org.zstack.storage.primary.nfs;

import org.zstack.header.message.NeedReplyMessage;
import org.zstack.header.storage.primary.PrimaryStorageMessage;

import java.util.List;

/**
 * Created by GuoYi on 12/1/17.
 */
public class NfsRebaseVolumeBackingFileMsg extends NeedReplyMessage implements PrimaryStorageMessage {
    private String hostUuid;
    private String srcPsUuid;
    private String dstPsUuid;
    private String dstVolumeFolderPath;
    private List<String> dstImageCacheTemplateFoldersPath;
    private String primaryStorageUuid;

    public String getHostUuid() {
        return hostUuid;
    }

    public void setHostUuid(String hostUuid) {
        this.hostUuid = hostUuid;
    }

    public String getSrcPsUuid() {
        return srcPsUuid;
    }

    public void setSrcPsUuid(String srcPsUuid) {
        this.srcPsUuid = srcPsUuid;
    }

    public String getDstPsUuid() {
        return dstPsUuid;
    }

    public void setDstPsUuid(String dstPsUuid) {
        this.dstPsUuid = dstPsUuid;
    }

    public String getDstVolumeFolderPath() {
        return dstVolumeFolderPath;
    }

    public void setDstVolumeFolderPath(String dstVolumeFolderPath) {
        this.dstVolumeFolderPath = dstVolumeFolderPath;
    }

    @Override
    public String getPrimaryStorageUuid() {
        return primaryStorageUuid;
    }

    public void setPrimaryStorageUuid(String primaryStorageUuid) {
        this.primaryStorageUuid = primaryStorageUuid;
    }

    public List<String> getDstImageCacheTemplateFoldersPath() {
        return dstImageCacheTemplateFoldersPath;
    }

    public void setDstImageCacheTemplateFoldersPath(List<String> dstImageCacheTemplateFoldersPath) {
        this.dstImageCacheTemplateFoldersPath = dstImageCacheTemplateFoldersPath;
    }
}
