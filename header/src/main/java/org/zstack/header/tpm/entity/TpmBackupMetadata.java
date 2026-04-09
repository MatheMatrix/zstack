package org.zstack.header.tpm.entity;

import org.zstack.header.storage.backup.AbstractBackupMetadata;
import org.zstack.header.vm.additions.VmHostFileContentVO;
import com.google.gson.annotations.SerializedName;

public class TpmBackupMetadata extends AbstractBackupMetadata {
    /**
     * from {@link VmHostFileContentVO#getContent()}
     */
    @SerializedName("tpm-state-content-b64")
    private String tpmStateBase64;

    /**
     * from {@link VmHostFileContentVO#getFormat()}
     */
    @SerializedName("tpm-state-format")
    private String tpmStateFormat;

    /**
     * from EncryptedResourceKeyRefVO.providerName
     */
    @SerializedName("tpm-key-provider-name")
    private String keyProviderName;

    /**
     * from EncryptedResourceKeyRefVO.keyVersion
     */
    @SerializedName("tpm-key-provider-name")
    private String keyVersion;

    /**
     * from EncryptedResourceKeyRefVO.kekRef
     */
    @SerializedName("tpm-kek-ref")
    private String kekRef;

    /**
     * from EncryptedResourceKeyRefVO.wrappedDek
     */
    @SerializedName("tpm-key-wrapped-dek")
    private String wrappedDek;

    /**
     * from EncryptedResourceKeyRefVO.wrappedDek
     */
    @SerializedName("tpm-key-algorithm")
    private String algorithm;

    public String getTpmStateBase64() {
        return tpmStateBase64;
    }

    public void setTpmStateBase64(String tpmStateBase64) {
        this.tpmStateBase64 = tpmStateBase64;
    }

    public String getTpmStateFormat() {
        return tpmStateFormat;
    }

    public void setTpmStateFormat(String tpmStateFormat) {
        this.tpmStateFormat = tpmStateFormat;
    }

    public String getKeyProviderName() {
        return keyProviderName;
    }

    public void setKeyProviderName(String keyProviderName) {
        this.keyProviderName = keyProviderName;
    }

    public String getKeyVersion() {
        return keyVersion;
    }

    public void setKeyVersion(String keyVersion) {
        this.keyVersion = keyVersion;
    }

    public String getKekRef() {
        return kekRef;
    }

    public void setKekRef(String kekRef) {
        this.kekRef = kekRef;
    }

    public String getWrappedDek() {
        return wrappedDek;
    }

    public void setWrappedDek(String wrappedDek) {
        this.wrappedDek = wrappedDek;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    @Override
    public String getMetadataType() {
        return METADATA_TYPE_TPM_STATE;
    }
}
