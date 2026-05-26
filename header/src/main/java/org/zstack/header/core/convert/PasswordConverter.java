package org.zstack.header.core.convert;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.stereotype.Component;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

/**
 * Created by kayo on 2018/9/7.
 *
 * <p>Relocated from {@code org.zstack.core.convert} to header in ZSTAC-85182 so
 * header-resident entities (e.g. {@code PhysicalServerAO.oobPassword}) can
 * apply {@code @Convert(converter = PasswordConverter.class)} directly. The
 * gating against the global {@code enable.password.encrypt} toggle moved to
 * {@link EncryptFacade#isEncryptionDisabled()} to keep this class free of any
 * {@code core} import.</p>
 */
@Component
@Converter
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class PasswordConverter implements AttributeConverter<String, String> {
    private static final CLogger logger = Utils.getLogger(PasswordConverter.class);

    private static EncryptFacade encryptFacade;

    @Autowired
    public void initEncryptFacade(EncryptFacade encryptFacade){
        PasswordConverter.encryptFacade = encryptFacade;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (encryptFacade == null || encryptFacade.isEncryptionDisabled()) {
            return attribute;
        }
        if (StringUtils.isEmpty(attribute)) {
            return attribute;
        }
        return encryptFacade.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (encryptFacade == null || encryptFacade.isEncryptionDisabled()) {
            return dbData;
        }

        if (StringUtils.isEmpty(dbData)) {
            return dbData ;
        }

        return encryptFacade.decrypt(dbData);
    }
}
