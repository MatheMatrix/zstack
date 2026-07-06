package org.zstack.test.kvm;

import org.junit.Assert;
import org.junit.Test;
import org.zstack.kvm.KvmHostConfigChecker;
import org.zstack.utils.ssh.SshResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

public class KvmHostConfigCheckerTest {
    private static class TestChecker extends KvmHostConfigChecker {
        private final Queue<SshResult> results = new ArrayDeque<>();
        private final List<String> commands = new ArrayList<>();

        TestChecker(SshResult... results) {
            this.results.addAll(Arrays.asList(results));
        }

        @Override
        protected SshResult runSshCommand(String command) {
            commands.add(command);
            return results.remove();
        }
    }

    @Test
    public void skipHygonQemuConfCheckWhenNotRequiredByHygonLogic() {
        TestChecker checker = new TestChecker();
        checker.setRequireHygonQemuConfAclCheck("false");

        Assert.assertFalse(checker.needDeployHygonQemuConf());
        Assert.assertTrue(checker.commands.isEmpty());
    }

    @Test
    public void deployWhenHygonQemuConfAclIsMissing() {
        TestChecker checker = new TestChecker(sshResult(1));
        checker.setRequireHygonQemuConfAclCheck("true");

        Assert.assertTrue(checker.needDeployHygonQemuConf());
        Assert.assertEquals(1, checker.commands.size());
    }

    @Test
    public void skipDeployWhenHygonQemuConfAclIsComplete() {
        TestChecker checker = new TestChecker(sshResult(0));
        checker.setRequireHygonQemuConfAclCheck("true");

        Assert.assertFalse(checker.needDeployHygonQemuConf());
        Assert.assertEquals(1, checker.commands.size());
    }

    @Test
    public void deployWhenHygonQemuConfCheckSshFails() {
        TestChecker checker = new TestChecker(sshFailure());
        checker.setRequireHygonQemuConfAclCheck("true");

        Assert.assertTrue(checker.needDeployHygonQemuConf());
        Assert.assertEquals(1, checker.commands.size());
    }

    private static SshResult sshResult(int returnCode) {
        SshResult result = new SshResult();
        result.setReturnCode(returnCode);
        return result;
    }

    private static SshResult sshFailure() {
        SshResult result = sshResult(255);
        result.setSshFailure(true);
        result.setExitErrorMessage("ssh failed");
        return result;
    }
}
