package org.zstack.test.integration.server

import org.zstack.core.db.Q
import org.zstack.core.encrypt.EncryptGlobalConfig
import org.zstack.header.core.encrypt.EncryptEntityMetadataVO
import org.zstack.header.core.encrypt.EncryptEntityMetadataVO_
import org.zstack.header.core.encrypt.EncryptEntityState
import org.zstack.header.server.PhysicalServerAO_
import org.zstack.header.server.PhysicalServerVO
import org.zstack.sdk.PhysicalServerInventory
import org.zstack.sdk.ServerPoolInventory
import org.zstack.sdk.ZoneInventory
import org.zstack.test.integration.kvm.KvmTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase

/**
 * ZSTAC-85182: PhysicalServerVO.oobPassword must be encrypt-at-rest.
 *
 * <p>The fix relocates {@code PasswordConverter} and {@code EncryptFacade} to
 * {@code header.core.encrypt} so {@link org.zstack.header.server.PhysicalServerAO}
 * can wire {@code @Convert(PasswordConverter.class)} directly — same mechanism
 * KVMHostVO uses. This case exercises the same surface that
 * {@code HostPasswordEncryptCase} uses for KVMHostVO.</p>
 */
class PhysicalServerOobPasswordEncryptCase extends SubCase {
    EnvSpec env

    @Override
    void clean() {
        env.delete()
    }

    @Override
    void setup() {
        useSpring(KvmTest.springSpec)
        spring {
            include("encrypt.xml")
        }
    }

    @Override
    void environment() {
        env = makeEnv {
            zone {
                name = "zone"
            }
        }
    }

    @Override
    void test() {
        env.create {
            testOobPasswordFieldRegisteredForEncryption()
            testOobPasswordRoundTripsWhenEncryptionEnabled()
            testEncryptionDisabledStaysPlaintext()
        }
    }

    private ServerPoolInventory createPool(String poolName) {
        def zone = env.inventoryByName("zone") as ZoneInventory
        return createServerPool {
            name = poolName
            zoneUuid = zone.uuid
        } as ServerPoolInventory
    }

    private void enableEncryption() {
        updateGlobalConfig {
            category = EncryptGlobalConfig.CATEGORY
            name = EncryptGlobalConfig.ENABLE_PASSWORD_ENCRYPT.name
            value = "LocalEncryption"
        }
    }

    private void disableEncryption() {
        updateGlobalConfig {
            category = EncryptGlobalConfig.CATEGORY
            name = EncryptGlobalConfig.ENABLE_PASSWORD_ENCRYPT.name
            value = "None"
        }
    }

    /**
     * The startup scanner in {@code EncryptFacadeImpl.collectAllEncryptPassword} registers
     * every {@code @Convert(PasswordConverter.class)} field into {@code EncryptEntityMetadataVO}.
     * If the @Convert annotation on PhysicalServerAO.oobPassword is missing, this row will
     * not exist and the assert is the RED→GREEN signal for ZSTAC-85182.
     */
    void testOobPasswordFieldRegisteredForEncryption() {
        EncryptEntityState state = Q.New(EncryptEntityMetadataVO.class)
                .select(EncryptEntityMetadataVO_.state)
                .eq(EncryptEntityMetadataVO_.entityName, PhysicalServerVO.class.getSimpleName())
                .eq(EncryptEntityMetadataVO_.columnName, "oobPassword")
                .findValue() as EncryptEntityState
        assert state != null : "PhysicalServerVO.oobPassword should be registered in EncryptEntityMetadataVO; missing @Convert(PasswordConverter.class)?"
    }

    /**
     * With encryption enabled, the read-through-the-getter must return the original plaintext.
     * After toggling encryption, the metadata row must be flipped to Encrypted so encryption
     * actually runs on subsequent writes (mirrors HostPasswordEncryptCase).
     */
    void testOobPasswordRoundTripsWhenEncryptionEnabled() {
        enableEncryption()
        retryInSecs {
            assert Q.New(EncryptEntityMetadataVO.class)
                    .select(EncryptEntityMetadataVO_.state)
                    .eq(EncryptEntityMetadataVO_.entityName, PhysicalServerVO.class.getSimpleName())
                    .eq(EncryptEntityMetadataVO_.columnName, "oobPassword")
                    .findValue() == EncryptEntityState.Encrypted
        }

        def zone = env.inventoryByName("zone") as ZoneInventory
        def pool = createPool("pool-encrypt")
        def plaintext = "topSecret-OOB!"

        def server = createPhysicalServer {
            name = "ps-encrypt"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "192.168.71.1"
            oobManagementType = "IPMI"
            oobAddress = "192.168.100.1"
            oobPort = 623
            oobUsername = "admin"
            oobPassword = plaintext
        } as PhysicalServerInventory

        // Q.findValue applies the converter; getter returns plaintext. The actual ciphertext
        // is hidden behind the converter, the metadata row above is what proves the column
        // is going through the encrypt path on write.
        String roundTrip = Q.New(PhysicalServerVO.class)
                .select(PhysicalServerAO_.oobPassword)
                .eq(PhysicalServerAO_.uuid, server.uuid)
                .findValue() as String
        assert roundTrip == plaintext : "oobPassword should round-trip back to plaintext via the converter"

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

    /**
     * When encryption is globally disabled (legacy / opt-out), the converter is a pass-through
     * — matches the rest of the platform's behaviour for PasswordConverter-annotated fields.
     */
    void testEncryptionDisabledStaysPlaintext() {
        disableEncryption()
        retryInSecs {
            assert Q.New(EncryptEntityMetadataVO.class)
                    .select(EncryptEntityMetadataVO_.state)
                    .eq(EncryptEntityMetadataVO_.entityName, PhysicalServerVO.class.getSimpleName())
                    .eq(EncryptEntityMetadataVO_.columnName, "oobPassword")
                    .findValue() == EncryptEntityState.NewAdded
        }

        def zone = env.inventoryByName("zone") as ZoneInventory
        def pool = createPool("pool-disabled")
        def plaintext = "no-encrypt-mode"

        def server = createPhysicalServer {
            name = "ps-plain"
            zoneUuid = zone.uuid
            poolUuid = pool.uuid
            managementIp = "192.168.71.3"
            oobManagementType = "IPMI"
            oobAddress = "192.168.100.3"
            oobUsername = "admin"
            oobPassword = plaintext
        } as PhysicalServerInventory

        String roundTrip = Q.New(PhysicalServerVO.class)
                .select(PhysicalServerAO_.oobPassword)
                .eq(PhysicalServerAO_.uuid, server.uuid)
                .findValue() as String
        assert roundTrip == plaintext : "with encryption disabled, the value must be unchanged"

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }
}
