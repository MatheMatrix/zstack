package org.zstack.core.ansible

import org.junit.Test

class SshYumRepoCheckerCase {
    private static final String ZSTACK_REPO = "/etc/yum.repos.d/zstack-mn.repo"
    private static final String QEMU_REPO = "/etc/yum.repos.d/qemu-kvm-ev-mn.repo"

    @Test
    void testRepoEndpoints() {
        assert SshYumRepoChecker.repoReadCommand().startsWith("grep ")
        assert !SshYumRepoChecker.repoReadCommand().contains("sed -i")

        assert SshYumRepoChecker.repoOutputUsesEndpoint(repoOutput(
                "http://[2001:db8::10]:8080/zstack/static/zstack-repo/x86_64",
                "http://[2001:db8::10]:8080/zstack/static/qemu-kvm-ev/x86_64"),
                "2001:db8::10", 8080)

        assert SshYumRepoChecker.repoOutputUsesEndpoint(repoOutput(
                "http://192.168.1.10:8080/zstack/static/zstack-repo/x86_64",
                "http://192.168.1.10:8080/zstack/static/qemu-kvm-ev/x86_64"),
                "192.168.1.10", 8080)

        assert SshYumRepoChecker.repoOutputUsesEndpoint(repoOutput(
                "http://mn.example.com:8080/zstack/static/zstack-repo/x86_64",
                "http://mn.example.com:8080/zstack/static/qemu-kvm-ev/x86_64"),
                "mn.example.com", 8080)
    }

    @Test
    void testMismatchMissingAndMalformedRepoEndpoints() {
        assert !SshYumRepoChecker.repoOutputUsesEndpoint(repoOutput(
                "http://[2001:db8::10]:8080/zstack/static/zstack-repo/x86_64",
                "http://192.168.1.10:8080/zstack/static/qemu-kvm-ev/x86_64"),
                "2001:db8::10", 8080)

        assert !SshYumRepoChecker.repoOutputUsesEndpoint(
                "${ZSTACK_REPO}:baseurl=http://[2001:db8::10]:8080/zstack/static/zstack-repo/x86_64\n",
                "2001:db8::10", 8080)

        assert !SshYumRepoChecker.repoOutputUsesEndpoint(
                "${ZSTACK_REPO}:baseurl=not-a-url\n${QEMU_REPO}:baseurl=http://[2001:db8::10]:8080/repo\n",
                "2001:db8::10", 8080)
    }

    private static String repoOutput(String zstackBaseUrl, String qemuBaseUrl) {
        return "${ZSTACK_REPO}:baseurl=${zstackBaseUrl}\n${QEMU_REPO}:baseurl=${qemuBaseUrl}\n"
    }
}
