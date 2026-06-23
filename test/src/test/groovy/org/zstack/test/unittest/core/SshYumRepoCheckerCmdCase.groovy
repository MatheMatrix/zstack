package org.zstack.test.unittest.core

import org.junit.Test
import org.zstack.core.ansible.SshYumRepoChecker

class SshYumRepoCheckerCmdCase {
    private static final String VALID_ZSTACK_MN_REPO =
            "[zstack-mn]\nname=zstack-mn\nbaseurl=http://172.24.242.88:8080/zstack/static/zstack-repo/\$basearch/\$YUM0/\ngpgcheck=0\nenabled=0\n"
    private static final String VALID_QEMU_KVM_EV_MN_REPO =
            "[qemu-kvm-ev-mn]\nname=qemu-kvm-ev-mn\nbaseurl=http://172.24.242.88:8080/zstack/static/zstack-repo/\$basearch/\$YUM0/Extra/qemu-kvm-ev/\ngpgcheck=0\nenabled=0\n"

    private int runValidation(String zstackMnContent, String qemuKvmEvContent) {
        File dir = File.createTempDir()
        try {
            writeRepo(dir, "zstack-mn.repo", zstackMnContent)
            writeRepo(dir, "qemu-kvm-ev-mn.repo", qemuKvmEvContent)

            String cmd = SshYumRepoChecker.VALIDATE_REPO_CONTENT_CMD
                    .replace("/etc/yum.repos.d/", dir.absolutePath + "/")

            Process p = ["bash", "-c", cmd].execute()
            p.waitFor()
            return p.exitValue()
        } finally {
            dir.deleteDir()
        }
    }

    private void writeRepo(File dir, String name, String content) {
        if (content == null) {
            return
        }
        new File(dir, name).text = content
    }

    @Test
    void validRepoFilesPassValidation() {
        assert runValidation(VALID_ZSTACK_MN_REPO, VALID_QEMU_KVM_EV_MN_REPO) == 0:
                "intact zstack-mn.repo and qemu-kvm-ev-mn.repo must not trigger redeploy"
    }

    @Test
    void emptyZstackMnRepoTriggersDeploy() {
        assert runValidation("", VALID_QEMU_KVM_EV_MN_REPO) != 0:
                "zstack-mn.repo truncated to 0 bytes by cold reboot must trigger redeploy"
    }

    @Test
    void emptyQemuKvmEvRepoTriggersDeploy() {
        assert runValidation(VALID_ZSTACK_MN_REPO, "") != 0:
                "qemu-kvm-ev-mn.repo truncated to 0 bytes must trigger redeploy"
    }

    @Test
    void absentQemuKvmEvRepoDoesNotTriggerDeploy() {
        assert runValidation(VALID_ZSTACK_MN_REPO, null) == 0:
                "legitimately absent qemu-kvm-ev-mn.repo (e.g. CentOS 6) must not trigger redeploy"
    }

    @Test
    void bothReposAbsentDoesNotTriggerDeploy() {
        assert runValidation(null, null) == 0:
                "both repos absent (first-ever connect before ansible) must not trigger redeploy"
    }

    @Test
    void repoWithoutBaseurlTriggersDeploy() {
        assert runValidation("[zstack-mn]\nname=zstack-mn\ngpgcheck=0\nenabled=0\n", VALID_QEMU_KVM_EV_MN_REPO) != 0:
                "zstack-mn.repo missing baseurl line must trigger redeploy"
    }
}
