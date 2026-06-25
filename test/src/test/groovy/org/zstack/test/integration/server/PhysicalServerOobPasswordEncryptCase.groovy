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

    void testOobPasswordFieldRegisteredForEncryption() {
        EncryptEntityState state = Q.New(EncryptEntityMetadataVO.class)
                .select(EncryptEntityMetadataVO_.state)
                .eq(EncryptEntityMetadataVO_.entityName, PhysicalServerVO.class.getSimpleName())
                .eq(EncryptEntityMetadataVO_.columnName, "oobPassword")
                .findValue() as EncryptEntityState
        assert state != null : "PhysicalServerVO.oobPassword should be registered in EncryptEntityMetadataVO; missing @Convert(PasswordConverter.class)?"
    }

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

        String roundTrip = Q.New(PhysicalServerVO.class)
                .select(PhysicalServerAO_.oobPassword)
                .eq(PhysicalServerAO_.uuid, server.uuid)
                .findValue() as String
        assert roundTrip == plaintext : "oobPassword should round-trip back to plaintext via the converter"

        deletePhysicalServer { uuid = server.uuid }
        deleteServerPool { uuid = pool.uuid }
    }

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
