package org.zstack.kvm;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.asyncbatch.While;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.cloudbus.ResourceDestinationMaker;
import org.zstack.core.config.GlobalConfig;
import org.zstack.core.config.GlobalConfigUpdateExtensionPoint;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.core.WhileDoneCompletion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.ErrorCodeList;
import org.zstack.header.host.HostConstant;
import org.zstack.header.message.MessageReply;
import org.zstack.utils.CollectionUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.*;
import java.util.stream.Collectors;

@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public abstract class GlobalConfigUpdateOnKvmHostExtension implements GlobalConfigUpdateExtensionPoint {
    private static final CLogger logger = Utils.getLogger(GlobalConfigUpdateOnKvmHostExtension.class);
    @Autowired
    private CloudBus bus;
    @Autowired
    private ResourceDestinationMaker destinationMaker;

    private volatile String currentValue;

    protected int getMaximumFailureNum() {
        return 3;
    }

    protected abstract String getAgentPath();

    protected abstract List<String> getHostUuids();

    protected LinkedHashMap<String, Object> buildConfig(GlobalConfig oldConfig, GlobalConfig newConfig, String hostUuid) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put(newConfig.getName(), newConfig.value());
        return m;
    }

    private boolean configChanged(GlobalConfig newConfig) {
        return !newConfig.value().equals(currentValue);
    }

    @Override
    public void updateGlobalConfig(GlobalConfig oldConfig, GlobalConfig newConfig) {
        logger.debug("wwwwwwwssssss1");
        currentValue = newConfig.value();

        List<String> targetHostUuids = getHostUuids().stream()
                .filter(hostUuid -> destinationMaker.isManagedByUs(hostUuid)).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(targetHostUuids)) {
            logger.debug("wwwwwwwssssss2");
            return;
        }

        int maximumFailureNum = getMaximumFailureNum();
        final List<Integer> stepCount = new ArrayList<>();
        for (int i = 1; i <= maximumFailureNum; i++) {
            stepCount.add(i);
        }
        new While<>(stepCount).each((idx, comp) -> {
            execute(targetHostUuids, oldConfig, newConfig, new ReturnValueCompletion<Map<String, ErrorCode>>(comp) {
                @Override
                public void success(Map<String, ErrorCode> failedHostUuids) {
                    if (failedHostUuids.isEmpty()) {
                        comp.allDone();
                        return;
                    }
                    logger.debug(String.format("GlobalConfig[name:%s, category:%s] update failed (%d:%d) on host %s",
                            newConfig.getName(), newConfig.getCategory(), idx, maximumFailureNum, failedHostUuids));
                    if (idx == maximumFailureNum) {
                        comp.addError(new ArrayList<>(failedHostUuids.values()).get(0));
                        comp.allDone();
                        return;
                    }
                    targetHostUuids.clear();
                    targetHostUuids.addAll(new ArrayList<>(failedHostUuids.keySet()));
                    comp.done();
                }

                @Override
                public void fail(ErrorCode errorCode) {
                    logger.debug(String.format("GlobalConfig[name:%s, category:%s] update failed (%d:%d):%s",
                            newConfig.getName(), newConfig.getCategory(), idx, maximumFailureNum, errorCode.getDetails()));
                    if (idx == maximumFailureNum) {
                        comp.addError(errorCode);
                        comp.allDone();
                        return;
                    }
                    comp.done();
                }
            });

        }).run(new WhileDoneCompletion(null) {
            @Override
            public void done(ErrorCodeList errorCodeList) {
                if (errorCodeList.getCauses().isEmpty()) {
                    logger.debug(String.format("GlobalConfig[name:%s, category:%s] update to %s completed",
                            newConfig.getName(), newConfig.getCategory(), newConfig.value()));
                }
            }
        });
    }

    private void execute(List<String> executionHostUuids, GlobalConfig oldConfig, GlobalConfig newConfig, ReturnValueCompletion<Map<String, ErrorCode>> completion) {
        if (CollectionUtils.isEmpty(executionHostUuids)) {
            completion.success(Collections.emptyMap());
            return;
        }

        Map<String, ErrorCode> failedHosts = Collections.synchronizedMap(new HashMap<>());
        new While<>(executionHostUuids).step((hostUuid, comp) -> {
            if (configChanged(newConfig)) {
                logger.debug("wwwwwwwssssss3");
                logger.debug(String.format("GlobalConfig[name:%s, category:%s] update canceled because the value has changed",
                        newConfig.getName(), newConfig.getCategory()));
                failedHosts.clear();
                comp.allDone();
                return;
            }
            GlobalConfigUpdateOnKvmHostMsg msg = new GlobalConfigUpdateOnKvmHostMsg();
            msg.setHostUuid(hostUuid);
            msg.setGlobalConfigs(buildConfig(oldConfig, newConfig, hostUuid));
            msg.setAgentPath(getAgentPath());
            bus.makeTargetServiceIdByResourceUuid(msg, HostConstant.SERVICE_ID, hostUuid);
            bus.send(msg, new CloudBusCallBack(comp) {
                @Override
                public void run(MessageReply reply) {
                    if (!reply.isSuccess()) {
                        failedHosts.put(hostUuid, reply.getError());
                    }
                    comp.done();
                }
            });
        }, 10).run(new WhileDoneCompletion(completion) {
            @Override
            public void done(ErrorCodeList errorCodeList) {
                completion.success(failedHosts);
            }
        });
    }
}
