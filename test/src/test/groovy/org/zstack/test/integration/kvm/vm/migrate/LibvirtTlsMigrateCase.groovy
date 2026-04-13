package org.zstack.test.integration.kvm.vm.migrate

import org.springframework.http.HttpEntity
import org.zstack.compute.host.HostSystemTags
import org.zstack.core.jsonlabel.JsonLabel
import org.zstack.header.host.HostVO
import org.zstack.kvm.KVMAgentCommands
import org.zstack.kvm.KVMConstant
import org.zstack.kvm.KVMGlobalConfig
import org.zstack.sdk.HostInventory
import org.zstack.sdk.UpdateGlobalConfigAction
import org.zstack.sdk.VmInstanceInventory
import org.zstack.test.integration.kvm.KvmTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.testlib.Test
import org.zstack.utils.data.SizeUnit
import org.zstack.utils.gson.JSONObjectUtil

/**
 * Verify that the libvirt TLS configuration (ZSTAC-81343) is correctly
 * propagated in the MigrateVmCmd sent to kvmagent.
 *
 * Also verify that TLS certificates are updated on host reconnect
 * when migration network IPs change (ZSTAC-83696).
 *
 * Key logic under test (KVMHost.java):
 *   cmd.setUseTls(LIBVIRT_TLS_ENABLED && RECONNECT_HOST_RESTART_LIBVIRTD_SERVICE)
 *   cmd.setSrcHostManagementIp(srcHostMnIp)
 *   reconnect flow: "update-tls-certs-if-needed" sends UpdateTlsCertCmd
 */
class LibvirtTlsMigrateCase extends SubCase {
    EnvSpec env

    @Override
    void clean() {
        env.delete()
    }

    @Override
    void setup() {
        useSpring(KvmTest.springSpec)
    }

    @Override
    void environment() {
        env = env {
            instanceOffering {
                name = "instanceOffering"
                memory = SizeUnit.GIGABYTE.toByte(8)
                cpu = 4
            }

            zone {
                name = "zone"
                cluster {
                    name = "cluster"
                    hypervisorType = "KVM"

                    kvm {
                        name = "kvm1"
                        managementIp = "127.0.0.1"
                        username = "root"
                        password = "password"
                        usedMem = 1000
                        totalCpu = 10
                    }
                    kvm {
                        name = "kvm2"
                        managementIp = "127.0.0.2"
                        username = "root"
                        password = "password"
                        usedMem = 1000
                        totalCpu = 10
                    }

                    attachPrimaryStorage("ps")
                    attachL2Network("l2")
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

                cephPrimaryStorage {
                    name = "ps"
                    totalCapacity = SizeUnit.GIGABYTE.toByte(100)
                    availableCapacity = SizeUnit.GIGABYTE.toByte(100)
                    url = "ceph://pri"
                    fsid = "7ff218d9-f525-435f-8a40-3618d1772a64"
                    monUrls = ["root:password@localhost/?monPort=7777"]
                }

                attachBackupStorage("bs")
            }

            cephBackupStorage {
                name = "bs"
                totalCapacity = SizeUnit.GIGABYTE.toByte(100)
                availableCapacity = SizeUnit.GIGABYTE.toByte(100)
                url = "/bk"
                fsid = "7ff218d9-f525-435f-8a40-3618d1772a64"
                monUrls = ["root:password@localhost/?monPort=7777"]

                image {
                    name = "image"
                    url = "http://zstack.org/download/image.qcow2"
                }
            }

            vm {
                name = "vm"
                useCluster("cluster")
                useHost("kvm1")
                useL3Networks("l3")
                useInstanceOffering("instanceOffering")
                useImage("image")
            }
        }
    }

    @Override
    void test() {
        env.create {
            testMigrateWithTlsEnabled()
            testMigrateWithTlsDisabled()
            testMigrateWithRestartLibvirtdDisabled()
            testGlobalConfigValidation()

            // ZSTAC-83696: TLS cert update on host reconnect
            testReconnectUpdatesTlsCertWithExtraIps()
            testReconnectTlsCertOnlyManagementIp()
            testReconnectSkipsTlsCertWhenTlsDisabled()
            testReconnectSkipsTlsCertWhenCaMissing()
            testReconnectNotBlockedByTlsCertFailure()
            testReconnectUsesResourceConfigNotGlobalConfig()
        }
    }

    /**
     * Case 1: Both LIBVIRT_TLS_ENABLED=true and RECONNECT_HOST_RESTART_LIBVIRTD_SERVICE=true
     *         => useTls should be true, srcHostManagementIp should be set
     */
    void testMigrateWithTlsEnabled() {
        def vm = env.inventoryByName("vm") as VmInstanceInventory
        def host1 = env.inventoryByName("kvm1") as HostInventory
        def host2 = env.inventoryByName("kvm2") as HostInventory

        // Ensure TLS is enabled (default is true)
        KVMGlobalConfig.LIBVIRT_TLS_ENABLED.updateValue("true")
        KVMGlobalConfig.RECONNECT_HOST_RESTART_LIBVIRTD_SERVICE.updateValue("true")

        KVMAgentCommands.MigrateVmCmd cmd = null
        env.afterSimulator(KVMConstant.KVM_MIGRATE_VM_PATH) { rsp, HttpEntity<String> e ->
            cmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.MigrateVmCmd.class)
            return rsp
        }

        // Migrate vm from kvm1 to kvm2
        migrateVm {
            vmInstanceUuid = vm.uuid
            hostUuid = host2.uuid
        }

        assert cmd != null : "MigrateVmCmd should have been captured"
        assert cmd.useTls : "useTls should be true when both TLS and restartLibvirtd are enabled"
        assert cmd.srcHostManagementIp == host1.managementIp :
                "srcHostManagementIp should be source host management IP"
        assert cmd.destHostManagementIp == host2.managementIp :
                "destHostManagementIp should be dest host management IP"
    }

    /**
     * Case 2: LIBVIRT_TLS_ENABLED=false => useTls should be false regardless of restartLibvirtd
     */
    void testMigrateWithTlsDisabled() {
        def vm = env.inventoryByName("vm") as VmInstanceInventory
        def host1 = env.inventoryByName("kvm1") as HostInventory

        // Disable TLS
        KVMGlobalConfig.LIBVIRT_TLS_ENABLED.updateValue("false")
        KVMGlobalConfig.RECONNECT_HOST_RESTART_LIBVIRTD_SERVICE.updateValue("true")

        KVMAgentCommands.MigrateVmCmd cmd = null
        env.afterSimulator(KVMConstant.KVM_MIGRATE_VM_PATH) { rsp, HttpEntity<String> e ->
            cmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.MigrateVmCmd.class)
            return rsp
        }

        // Migrate back to kvm1
        migrateVm {
            vmInstanceUuid = vm.uuid
            hostUuid = host1.uuid
        }

        assert cmd != null : "MigrateVmCmd should have been captured"
        assert !cmd.useTls : "useTls should be false when TLS config is disabled"

        // Restore default
        KVMGlobalConfig.LIBVIRT_TLS_ENABLED.updateValue("true")
    }

    /**
     * Case 3: LIBVIRT_TLS_ENABLED=true but RECONNECT_HOST_RESTART_LIBVIRTD_SERVICE=false
     *         => useTls should be false (AND logic: both must be true)
     *
     * This is a critical boundary: TLS config is on, but libvirtd was not restarted
     * with TLS certs deployed, so we must NOT tell kvmagent to use TLS.
     */
    void testMigrateWithRestartLibvirtdDisabled() {
        def vm = env.inventoryByName("vm") as VmInstanceInventory
        def host2 = env.inventoryByName("kvm2") as HostInventory

        KVMGlobalConfig.LIBVIRT_TLS_ENABLED.updateValue("true")
        KVMGlobalConfig.RECONNECT_HOST_RESTART_LIBVIRTD_SERVICE.updateValue("false")

        KVMAgentCommands.MigrateVmCmd cmd = null
        env.afterSimulator(KVMConstant.KVM_MIGRATE_VM_PATH) { rsp, HttpEntity<String> e ->
            cmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.MigrateVmCmd.class)
            return rsp
        }

        migrateVm {
            vmInstanceUuid = vm.uuid
            hostUuid = host2.uuid
        }

        assert cmd != null : "MigrateVmCmd should have been captured"
        assert !cmd.useTls :
                "useTls should be false when restartLibvirtd is disabled (TLS certs not deployed)"

        // Restore default
        KVMGlobalConfig.RECONNECT_HOST_RESTART_LIBVIRTD_SERVICE.updateValue("true")
    }

    /**
     * Case 4: Validate that libvirt.tls.enabled GlobalConfig only accepts true/false
     */
    void testGlobalConfigValidation() {
        // Valid values via SDK action
        updateGlobalConfig {
            category = "kvm"
            name = "libvirt.tls.enabled"
            value = "true"
        }
        assert KVMGlobalConfig.LIBVIRT_TLS_ENABLED.value(Boolean.class) == true

        updateGlobalConfig {
            category = "kvm"
            name = "libvirt.tls.enabled"
            value = "false"
        }
        assert KVMGlobalConfig.LIBVIRT_TLS_ENABLED.value(Boolean.class) == false

        // Invalid value should be rejected
        def action = new UpdateGlobalConfigAction()
        action.category = "kvm"
        action.name = "libvirt.tls.enabled"
        action.value = "invalid"
        action.sessionId = Test.currentEnvSpec.session.uuid
        UpdateGlobalConfigAction.Result res = action.call()
        assert res.error != null : "Setting an invalid value for libvirt.tls.enabled should fail"

        // Restore default
        updateGlobalConfig {
            category = "kvm"
            name = "libvirt.tls.enabled"
            value = "true"
        }
    }

    /**
     * ZSTAC-83696 Case 5: TLS enabled + host has extra IPs (migration network)
     *   => reconnect should send UpdateTlsCertCmd with managementIp + extraIps
     */
    void testReconnectUpdatesTlsCertWithExtraIps() {
        def host1 = env.inventoryByName("kvm1") as HostInventory

        KVMGlobalConfig.LIBVIRT_TLS_ENABLED.updateValue("true")

        // Seed CA cert/key into JsonLabel (simulating what ansible deploy stores)
        new JsonLabel().create("libvirtTLSCA", "-----BEGIN CERTIFICATE-----\nFAKE_CA\n-----END CERTIFICATE-----")
        new JsonLabel().create("libvirtTLSPrivateKey", "-----BEGIN RSA PRIVATE KEY-----\nFAKE_KEY\n-----END RSA PRIVATE KEY-----")

        // Add extra IPs (migration network) via system tag
        createSystemTag {
            resourceType = HostVO.class.simpleName
            resourceUuid = host1.uuid
            tag = "extraips::10.0.100.1,10.0.100.2"
        }

        KVMAgentCommands.UpdateTlsCertCmd cmd = null
        env.afterSimulator(KVMConstant.KVM_UPDATE_TLS_CERT_PATH) { rsp, HttpEntity<String> e ->
            cmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.UpdateTlsCertCmd.class)
            return rsp
        }

        reconnectHost {
            uuid = host1.uuid
        }

        assert cmd != null : "UpdateTlsCertCmd should have been sent on reconnect"
        assert cmd.caCert != null : "caCert should be populated"
        assert cmd.caKey != null : "caKey should be populated"
        assert cmd.certIps.contains(host1.managementIp) :
                "certIps should contain management IP"
        assert cmd.certIps.contains("10.0.100.1") :
                "certIps should contain extra migration IP 10.0.100.1"
        assert cmd.certIps.contains("10.0.100.2") :
                "certIps should contain extra migration IP 10.0.100.2"

        // Cleanup
        HostSystemTags.EXTRA_IPS.delete(host1.uuid)
        new JsonLabel().delete("libvirtTLSCA")
        new JsonLabel().delete("libvirtTLSPrivateKey")
    }

    /**
     * ZSTAC-83696 Case 6: TLS enabled + no extra IPs
     *   => certIps should only contain management IP
     */
    void testReconnectTlsCertOnlyManagementIp() {
        def host1 = env.inventoryByName("kvm1") as HostInventory

        KVMGlobalConfig.LIBVIRT_TLS_ENABLED.updateValue("true")

        new JsonLabel().create("libvirtTLSCA", "-----BEGIN CERTIFICATE-----\nFAKE_CA\n-----END CERTIFICATE-----")
        new JsonLabel().create("libvirtTLSPrivateKey", "-----BEGIN RSA PRIVATE KEY-----\nFAKE_KEY\n-----END RSA PRIVATE KEY-----")

        // Ensure no EXTRA_IPS tag exists
        HostSystemTags.EXTRA_IPS.delete(host1.uuid)

        KVMAgentCommands.UpdateTlsCertCmd cmd = null
        env.afterSimulator(KVMConstant.KVM_UPDATE_TLS_CERT_PATH) { rsp, HttpEntity<String> e ->
            cmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.UpdateTlsCertCmd.class)
            return rsp
        }

        reconnectHost {
            uuid = host1.uuid
        }

        assert cmd != null : "UpdateTlsCertCmd should have been sent"
        assert cmd.certIps == host1.managementIp :
                "certIps should only contain management IP when no extra IPs exist"

        // Cleanup
        new JsonLabel().delete("libvirtTLSCA")
        new JsonLabel().delete("libvirtTLSPrivateKey")
    }

    /**
     * ZSTAC-83696 Case 7: TLS disabled => UpdateTlsCertCmd should NOT be sent
     */
    void testReconnectSkipsTlsCertWhenTlsDisabled() {
        def host1 = env.inventoryByName("kvm1") as HostInventory

        KVMGlobalConfig.LIBVIRT_TLS_ENABLED.updateValue("false")

        boolean updateTlsCertCalled = false
        env.afterSimulator(KVMConstant.KVM_UPDATE_TLS_CERT_PATH) { rsp, HttpEntity<String> e ->
            updateTlsCertCalled = true
            return rsp
        }

        reconnectHost {
            uuid = host1.uuid
        }

        assert !updateTlsCertCalled :
                "UpdateTlsCertCmd should NOT be sent when TLS is disabled"

        // Restore default
        KVMGlobalConfig.LIBVIRT_TLS_ENABLED.updateValue("true")
    }

    /**
     * ZSTAC-83696 Case 8: TLS enabled but CA cert/key missing in DB
     *   => should skip gracefully, not block reconnect
     */
    void testReconnectSkipsTlsCertWhenCaMissing() {
        def host1 = env.inventoryByName("kvm1") as HostInventory

        KVMGlobalConfig.LIBVIRT_TLS_ENABLED.updateValue("true")

        // Ensure no CA cert/key in database
        new JsonLabel().delete("libvirtTLSCA")
        new JsonLabel().delete("libvirtTLSPrivateKey")

        boolean updateTlsCertCalled = false
        env.afterSimulator(KVMConstant.KVM_UPDATE_TLS_CERT_PATH) { rsp, HttpEntity<String> e ->
            updateTlsCertCalled = true
            return rsp
        }

        // Reconnect should succeed even without CA
        reconnectHost {
            uuid = host1.uuid
        }

        assert !updateTlsCertCalled :
                "UpdateTlsCertCmd should NOT be sent when CA cert/key is missing"
    }

    /**
     * ZSTAC-83696 Case 9: UpdateTlsCertCmd returns failure
     *   => reconnect should still succeed (cert update is non-blocking)
     */
    void testReconnectNotBlockedByTlsCertFailure() {
        def host1 = env.inventoryByName("kvm1") as HostInventory

        KVMGlobalConfig.LIBVIRT_TLS_ENABLED.updateValue("true")

        new JsonLabel().create("libvirtTLSCA", "-----BEGIN CERTIFICATE-----\nFAKE_CA\n-----END CERTIFICATE-----")
        new JsonLabel().create("libvirtTLSPrivateKey", "-----BEGIN RSA PRIVATE KEY-----\nFAKE_KEY\n-----END RSA PRIVATE KEY-----")

        // Simulate kvmagent returning a failure for cert update
        env.afterSimulator(KVMConstant.KVM_UPDATE_TLS_CERT_PATH) { rsp, HttpEntity<String> e ->
            def r = new KVMAgentCommands.UpdateTlsCertResponse()
            r.success = false
            r.error = "simulated cert generation failure"
            return r
        }

        // Reconnect should still succeed despite cert update failure
        reconnectHost {
            uuid = host1.uuid
        }

        // If we reach here, reconnect was not blocked — test passes

        // Cleanup
        new JsonLabel().delete("libvirtTLSCA")
        new JsonLabel().delete("libvirtTLSPrivateKey")
    }

    /**
     * ZSTAC-83696 Case 10: Ensure the "update-tls-certs-if-needed" flow skip condition
     * is consistent with the migrate useTls parameter across all global/resource config combos.
     *
     * Invariant: whenever migrate would set useTls=true (meaning TLS certs are needed),
     * the reconnect cert-update flow must NOT be skipped. Conversely, when useTls=false,
     * the flow should be skipped (certs are not needed).
     *
     * The original bug: migrate used rcf.getResourceConfigValue() (resource-level, true),
     * but skip() used GlobalConfig.value() (global, false). This caused useTls=true
     * but cert update skipped → "Certificate does not match the hostname" on migration.
     */
    void testReconnectUsesResourceConfigNotGlobalConfig() {
        def host1 = env.inventoryByName("kvm1") as HostInventory
        def host2 = env.inventoryByName("kvm2") as HostInventory
        def vm = env.inventoryByName("vm") as VmInstanceInventory

        KVMGlobalConfig.LIBVIRT_TLS_ENABLED.updateValue("true")

        new JsonLabel().create("libvirtTLSCA", "-----BEGIN CERTIFICATE-----\nFAKE_CA\n-----END CERTIFICATE-----")
        new JsonLabel().create("libvirtTLSPrivateKey", "-----BEGIN RSA PRIVATE KEY-----\nFAKE_KEY\n-----END RSA PRIVATE KEY-----")

        // ---- Scenario A: global=false, host-level=true ----
        // This is the exact production bug scenario.
        // Migrate should use TLS (resource=true), cert update must NOT be skipped.
        KVMGlobalConfig.RECONNECT_HOST_RESTART_LIBVIRTD_SERVICE.updateValue("false")
        updateResourceConfig {
            category = KVMGlobalConfig.CATEGORY
            name = KVMGlobalConfig.RECONNECT_HOST_RESTART_LIBVIRTD_SERVICE.name
            value = "true"
            resourceUuid = host1.uuid
        }

        // Verify migrate useTls=true (resource-level wins)
        KVMAgentCommands.MigrateVmCmd migrateCmd = null
        env.afterSimulator(KVMConstant.KVM_MIGRATE_VM_PATH) { rsp, HttpEntity<String> e ->
            migrateCmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.MigrateVmCmd.class)
            return rsp
        }
        migrateVm {
            vmInstanceUuid = vm.uuid
            hostUuid = host2.uuid
        }
        assert migrateCmd.useTls :
                "Scenario A: useTls should be true (resource-level restartLibvirtd=true)"

        // Verify cert update flow is NOT skipped on reconnect
        KVMAgentCommands.UpdateTlsCertCmd certCmd = null
        env.afterSimulator(KVMConstant.KVM_UPDATE_TLS_CERT_PATH) { rsp, HttpEntity<String> e ->
            certCmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.UpdateTlsCertCmd.class)
            return rsp
        }
        reconnectHost {
            uuid = host1.uuid
        }
        assert certCmd != null :
                "Scenario A: cert update must NOT be skipped when host-level restartLibvirtd=true (useTls=true)"

        // ---- Scenario B: global=false, host-level=false (no override) ----
        // Migrate should NOT use TLS, cert update should be skipped. Both consistent.
        KVMGlobalConfig.RECONNECT_HOST_RESTART_LIBVIRTD_SERVICE.updateValue("false")
        updateResourceConfig {
            category = KVMGlobalConfig.CATEGORY
            name = KVMGlobalConfig.RECONNECT_HOST_RESTART_LIBVIRTD_SERVICE.name
            value = "false"
            resourceUuid = host1.uuid
        }

        migrateCmd = null
        env.afterSimulator(KVMConstant.KVM_MIGRATE_VM_PATH) { rsp, HttpEntity<String> e ->
            migrateCmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.MigrateVmCmd.class)
            return rsp
        }
        migrateVm {
            vmInstanceUuid = vm.uuid
            hostUuid = host1.uuid
        }
        assert !migrateCmd.useTls :
                "Scenario B: useTls should be false (both global and resource=false)"

        boolean certUpdateCalled = false
        env.afterSimulator(KVMConstant.KVM_UPDATE_TLS_CERT_PATH) { rsp, HttpEntity<String> e ->
            certUpdateCalled = true
            return rsp
        }
        reconnectHost {
            uuid = host1.uuid
        }
        assert !certUpdateCalled :
                "Scenario B: cert update should be skipped when restartLibvirtd=false (useTls=false)"

        // ---- Scenario C: global=true, host-level=true ----
        // Both true, both should agree: useTls=true, cert update runs.
        KVMGlobalConfig.RECONNECT_HOST_RESTART_LIBVIRTD_SERVICE.updateValue("true")
        updateResourceConfig {
            category = KVMGlobalConfig.CATEGORY
            name = KVMGlobalConfig.RECONNECT_HOST_RESTART_LIBVIRTD_SERVICE.name
            value = "true"
            resourceUuid = host1.uuid
        }

        migrateCmd = null
        env.afterSimulator(KVMConstant.KVM_MIGRATE_VM_PATH) { rsp, HttpEntity<String> e ->
            migrateCmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.MigrateVmCmd.class)
            return rsp
        }
        migrateVm {
            vmInstanceUuid = vm.uuid
            hostUuid = host2.uuid
        }
        assert migrateCmd.useTls :
                "Scenario C: useTls should be true (both global and resource=true)"

        certCmd = null
        env.afterSimulator(KVMConstant.KVM_UPDATE_TLS_CERT_PATH) { rsp, HttpEntity<String> e ->
            certCmd = JSONObjectUtil.toObject(e.body, KVMAgentCommands.UpdateTlsCertCmd.class)
            return rsp
        }
        reconnectHost {
            uuid = host1.uuid
        }
        assert certCmd != null :
                "Scenario C: cert update must run when restartLibvirtd=true (useTls=true)"

        // Cleanup
        KVMGlobalConfig.RECONNECT_HOST_RESTART_LIBVIRTD_SERVICE.updateValue("true")
        new JsonLabel().delete("libvirtTLSCA")
        new JsonLabel().delete("libvirtTLSPrivateKey")
    }
}
