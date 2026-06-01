package org.zstack.kvm;

import org.apache.commons.lang.StringUtils;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.SimpleQuery;
import org.zstack.header.host.HostKeyIdentityVO;
import org.zstack.header.host.HostKeyIdentityVO_;
import org.zstack.header.secret.SecretHostDefineReply;
import org.zstack.utils.ExceptionDSL;
import org.zstack.utils.logging.CLogger;
import org.zstack.utils.logging.CLoggerImpl;

import javax.persistence.EntityExistsException;
import javax.persistence.PersistenceException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.util.Base64;

public final class HostKeyIdentityHelper {
    private static final CLogger logger = CLoggerImpl.getLogger(HostKeyIdentityHelper.class);

    private HostKeyIdentityHelper() {
    }

    public static String fingerprintFromPublicKey(String publicKeyBase64) {
        if (StringUtils.isBlank(publicKeyBase64)) {
            return "";
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64.trim());
            if (keyBytes.length == 0) {
                return "";
            }
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(keyBytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (IllegalArgumentException | NoSuchAlgorithmException e) {
            return "";
        }
    }

    public static HostKeyIdentityVO getHostKeyIdentity(DatabaseFacade dbf, String hostUuid) {
        SimpleQuery<HostKeyIdentityVO> q = dbf.createQuery(HostKeyIdentityVO.class);
        q.add(HostKeyIdentityVO_.hostUuid, SimpleQuery.Op.EQ, hostUuid);
        return q.find();
    }

    public static void saveOrUpdateHostKeyIdentity(DatabaseFacade dbf, String hostUuid, String publicKey, boolean verified) {
        if (StringUtils.isBlank(publicKey)) {
            return;
        }

        String keyToSave = publicKey.trim();
        byte[] decodedKey;
        try {
            decodedKey = Base64.getDecoder().decode(keyToSave);
        } catch (IllegalArgumentException e) {
            logger.warn("host " + hostUuid + " returned an invalid envelope public key");
            setVerified(dbf, hostUuid, false);
            return;
        }
        if (decodedKey.length != 32) {
            logger.warn("host " + hostUuid + " returned an envelope public key with unexpected length: " + decodedKey.length);
            setVerified(dbf, hostUuid, false);
            return;
        }

        String fingerprint = fingerprintFromPublicKey(keyToSave);
        if (StringUtils.isBlank(fingerprint)) {
            logger.warn("host " + hostUuid + " returned an envelope public key with empty fingerprint");
            setVerified(dbf, hostUuid, false);
            return;
        }

        HostKeyIdentityVO vo = getHostKeyIdentity(dbf, hostUuid);
        if (vo == null) {
            vo = new HostKeyIdentityVO();
            vo.setHostUuid(hostUuid);
            vo.setPublicKey(keyToSave);
            vo.setFingerprint(fingerprint);
            vo.setVerified(verified);
            vo.setCreateDate(new Timestamp(System.currentTimeMillis()));
            try {
                dbf.persist(vo);
                return;
            } catch (PersistenceException e) {
                if (!ExceptionDSL.isCausedBy(e, EntityExistsException.class)
                        && !ExceptionDSL.isCausedBy(e, SQLIntegrityConstraintViolationException.class, "Duplicate entry")) {
                    throw e;
                }
                vo = getHostKeyIdentity(dbf, hostUuid);
                if (vo == null) {
                    throw e;
                }
            }
        }

        vo.setPublicKey(keyToSave);
        vo.setFingerprint(fingerprint);
        vo.setVerified(verified);
        dbf.update(vo);
    }

    public static void setVerified(DatabaseFacade dbf, String hostUuid, boolean verified) {
        HostKeyIdentityVO vo = getHostKeyIdentity(dbf, hostUuid);
        if (vo != null) {
            vo.setVerified(verified);
            dbf.update(vo);
        }
    }

    public static boolean isRotateNeededGetError(String errorCode) {
        return SecretHostDefineReply.ERROR_CODE_KEYS_NOT_ON_DISK.equals(errorCode)
                || SecretHostDefineReply.ERROR_CODE_KEY_FILES_INTEGRITY_MISMATCH.equals(errorCode);
    }
}
