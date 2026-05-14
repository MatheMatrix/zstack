package org.zstack.test.integration.kvm

import org.zstack.core.ansible.SshFileMd5Checker
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase

class KvmAgentPackageMd5Case extends SubCase {
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
        env = makeEnv {}
    }

    @Override
    void test() {
        env.create {
            assert SshFileMd5Checker.isMd5ContentMatched("abc123\n", "abc123")
            assert !SshFileMd5Checker.isMd5ContentMatched("old-md5", "abc123")
            assert !SshFileMd5Checker.isMd5ContentMatched(null, "abc123")
        }
    }
}
