package org.zstack.header.core.convert;

import org.zstack.header.core.encrypt.EncryptEntityState;
import org.zstack.header.core.encrypt.EncryptedFieldBundle;
import org.zstack.header.errorcode.ErrorableValue;

import java.util.List;

/**
 * Header-resident interface so header-bound entities (e.g.
 * {@code PhysicalServerAO}) can reference the JPA
 * {@link org.zstack.header.core.convert.PasswordConverter} without pulling in
 * the {@code core} module (header is the upstream of core).
 *
 * <p>Originally lived in {@code org.zstack.core.encrypt}; moved with the
 * {@code PasswordConverter} relocation in ZSTAC-85182.</p>
 */
public interface EncryptFacade {
    String encrypt(String decryptString);

    String decrypt(String encryptString);

    ErrorableValue<String> encrypt(String data, String algType);

    ErrorableValue<String> decrypt(String data, String algType);

    void updateEncryptDataStateIfExists(String entity, String column, EncryptEntityState state);

    List<EncryptedFieldBundle> getIntegrityEncryptionBundle();

    List<EncryptedFieldBundle> getConfidentialityEncryptionBundle();

    /**
     * @return {@code true} when the global password-encrypt toggle is set to
     * {@code None}; the {@link PasswordConverter} treats this as pass-through
     * so legacy unencrypted columns stay readable. Centralised here so the
     * converter does not need to depend on {@code core.EncryptGlobalConfig}.
     */
    boolean isEncryptionDisabled();
}
