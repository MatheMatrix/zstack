package org.zstack.header.core.convert;

import org.zstack.header.core.encrypt.EncryptEntityState;
import org.zstack.header.core.encrypt.EncryptedFieldBundle;
import org.zstack.header.errorcode.ErrorableValue;

import java.util.List;

public interface EncryptFacade {
    String encrypt(String decryptString);

    String decrypt(String encryptString);

    ErrorableValue<String> encrypt(String data, String algType);

    ErrorableValue<String> decrypt(String data, String algType);

    void updateEncryptDataStateIfExists(String entity, String column, EncryptEntityState state);

    List<EncryptedFieldBundle> getIntegrityEncryptionBundle();

    List<EncryptedFieldBundle> getConfidentialityEncryptionBundle();

    boolean isEncryptionDisabled();
}
