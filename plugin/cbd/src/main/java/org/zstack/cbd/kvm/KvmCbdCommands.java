package org.zstack.cbd.kvm;

import org.zstack.cbd.Mds;
import org.zstack.kvm.KVMAgentCommands;

import java.util.List;

/**
 * @author Xingwei Yu
 * @date 2024/4/10 15:40
 */
public class KvmCbdCommands {
    public static final String CBD_CONFIGURE_CLIENT_PATH = "/cbd/configure/client";
    public static final String CBD_SETUP_SELF_FENCER_PATH = "/ha/cbd/setupselffencer";

    public static class AgentRsp {
        public boolean success = true;
        public String error;
    }

    public static class KvmUpdateClientConfCmd extends AgentCmd {
        private List<Mds> mdsInfos;

        public List<Mds> getMdsInfos() {
            return mdsInfos;
        }

        public void setMdsInfos(List<Mds> mdsInfos) {
            this.mdsInfos = mdsInfos;
        }
    }

    public static class KvmSetupSelfFencerCmd extends AgentCmd {
        public long interval;
        public int maxAttempts;
        public List<String> coveringPaths;
        public String heartbeatUrl;
        public int storageCheckerTimeout;
        public String hostUuid;
        public Integer hostId;
        public Long heartbeatRequiredSpace;
        public String strategy;
        public List<String> fencers;
    }

    public static class AgentCmd extends KVMAgentCommands.AgentCommand {
        public String uuid;
    }
}
