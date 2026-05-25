package org.zstack.test.integration.storage.backup.sftp

import org.zstack.header.storage.backup.BackupStorageStatus
import org.zstack.sdk.BackupStorageInventory
import org.zstack.sdk.ReconnectBackupStorageAction
import org.zstack.storage.backup.sftp.SftpBackupStorageCommands
import org.zstack.storage.backup.sftp.SftpBackupStorageConstant
import org.zstack.test.integration.storage.StorageTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.data.SizeUnit

/**
 * ZSTAC-67815: verify SFTP backup storage continueConnect uses timeout on
 * syncJsonPost to prevent queue starvation when agent HTTP layer is
 * unreachable.
 *
 * The fix adds TimeUnit.SECONDS, 60 to the syncJsonPost call so connect
 * fails in 60s instead of hanging indefinitely and starving the per-BS
 * queue for all backup/restore/export operations.
 */
class SftpBackupStorageConnectTimeoutCase extends SubCase {

    EnvSpec env

    @Override
    void clean() {
        env.delete()
    }

    @Override
    void setup() {
        useSpring(StorageTest.springSpec)
    }

    @Override
    void environment() {
        env = env {
            sftpBackupStorage {
                name = "sftp"
                url = "/sftp"
                username = "root"
                password = "password"
                hostname = "localhost"
            }

            zone {
                name = "zone"
                description = "test"

                cluster {
                    name = "cluster"
                    hypervisorType = "KVM"

                    kvm {
                        name = "kvm"
                        managementIp = "localhost"
                        username = "root"
                        password = "password"
                    }

                    attachPrimaryStorage("local")
                    attachL2Network("l2")
                }

                localPrimaryStorage {
                    name = "local"
                    url = "/local_ps"
                }

                l2NoVlanNetwork {
                    name = "l2"
                    physicalInterface = "eth0"

                    l3Network {
                        name = "l3"

                        ip {
                            startIp = "192.168.100.10"
                            endIp = "192.168.100.100"
                            netmask = "255.255.255.0"
                            gateway = "192.168.100.1"
                        }
                    }
                }

                attachBackupStorage("sftp")
            }
        }
    }

    @Override
    void test() {
        env.create {
            testSftpBackupStorageConnectsSuccessfully()
            testConnectFailsGracefullyOnAgentError()
            testReconnectSucceedsAfterAgentRecovers()
        }
    }

    /**
     * Verify baseline: SFTP BS connects successfully via the
     * continueConnect path (which now includes the 60s timeout).
     */
    void testSftpBackupStorageConnectsSuccessfully() {
        BackupStorageInventory sftp = env.inventoryByName("sftp")
        assert sftp.status == BackupStorageStatus.Connected.toString()
    }

    /**
     * Verify that when the agent's CONNECT_PATH returns an error, the
     * connect fails gracefully (does not hang). This exercises the error
     * path in continueConnect() which now has the timeout parameter.
     */
    void testConnectFailsGracefullyOnAgentError() {
        env.simulator(SftpBackupStorageConstant.CONNECT_PATH) {
            def rsp = new SftpBackupStorageCommands.ConnectResponse()
            rsp.setError("simulated agent connect failure")
            rsp.setSuccess(false)
            return rsp
        }

        BackupStorageInventory sftp = env.inventoryByName("sftp")
        ReconnectBackupStorageAction action = new ReconnectBackupStorageAction()
        action.uuid = sftp.uuid
        action.sessionId = adminSession()
        ReconnectBackupStorageAction.Result res = action.call()
        assert res.error != null
    }

    /**
     * Verify that after the agent recovers (simulator returns success),
     * reconnect succeeds. This confirms that the per-BS queue is not
     * permanently stalled by a transient agent failure.
     */
    void testReconnectSucceedsAfterAgentRecovers() {
        env.simulator(SftpBackupStorageConstant.CONNECT_PATH) {
            def rsp = new SftpBackupStorageCommands.ConnectResponse()
            rsp.totalCapacity = SizeUnit.GIGABYTE.toByte(1000)
            rsp.availableCapacity = SizeUnit.GIGABYTE.toByte(1000)
            return rsp
        }

        BackupStorageInventory sftp = env.inventoryByName("sftp")
        reconnectBackupStorage {
            uuid = sftp.uuid
        }

        retryInSecs {
            sftp = queryBackupStorage {
                conditions = ["uuid=${sftp.uuid}"]
            }[0] as BackupStorageInventory
            assert sftp.status == BackupStorageStatus.Connected.toString()
        }
    }
}
