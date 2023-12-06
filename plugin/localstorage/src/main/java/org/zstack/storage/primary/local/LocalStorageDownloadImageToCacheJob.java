package org.zstack.storage.primary.local;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.SimpleQuery;
import org.zstack.core.job.Job;
import org.zstack.core.job.JobContext;
import org.zstack.core.thread.ChainTask;
import org.zstack.core.thread.SyncTaskChain;
import org.zstack.core.thread.ThreadFacade;
import org.zstack.core.workflow.FlowChainBuilder;
import org.zstack.core.workflow.ShareFlow;
import org.zstack.header.core.Completion;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.core.workflow.*;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.host.HostConstant;
import org.zstack.header.image.ImageConstant;
import org.zstack.header.image.ImageInventory;
import org.zstack.header.message.MessageReply;
import org.zstack.header.storage.backup.BackupStorageInventory;
import org.zstack.header.storage.primary.*;
import org.zstack.kvm.KVMConstant;
import org.zstack.kvm.KVMHostAsyncHttpCallMsg;
import org.zstack.kvm.KVMHostAsyncHttpCallReply;
import org.zstack.utils.DebugUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.List;
import java.util.Map;

import static org.zstack.core.progress.ProgressReportService.taskProgress;
import static org.zstack.storage.primary.local.LocalStorageKvmBackend.*;

/**
 * @ Author : yh.w
 * @ Date   : Created in 13:22 2023/12/7
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class LocalStorageDownloadImageToCacheJob implements Job {
    private static final CLogger logger = Utils.getLogger(LocalStorageDownloadImageToCacheJob.class);
    @JobContext
    ImageInventory image;
    @JobContext
    PrimaryStorageInventory ps;
    @JobContext
    BackupStorageInventory backupStorage;
    @JobContext
    String volumeResourceInstallPath;
    @JobContext
    boolean incremental;
    @JobContext
    String hostUuid;
    @JobContext
    String primaryStorageInstallPath;
    @JobContext
    String backupStorageInstallPath;

    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private ThreadFacade thdf;
    @Autowired
    private CloudBus bus;
    @Autowired
    private PluginRegistry pluginRgty;
    @Autowired
    private LocalStorageFactory localStorageFactory;

    String getQueueName() {
        return String.format("download-image-%s-to-localstorage-%s-cache-host-%s", image.getUuid(), ps.getUuid(), hostUuid);
    }

    protected <T extends AgentResponse> void httpCall(String path, final String hostUuid, AgentCommand cmd, final Class<T> rspType, final ReturnValueCompletion<T> completion) {
        cmd.uuid = ps.getUuid();
        cmd.storagePath = ps.getUrl();
        cmd.primaryStorageUuid = cmd.uuid;

        KVMHostAsyncHttpCallMsg msg = new KVMHostAsyncHttpCallMsg();
        msg.setHostUuid(hostUuid);
        msg.setPath(path);
        msg.setCommand(cmd);
        bus.makeTargetServiceIdByResourceUuid(msg, HostConstant.SERVICE_ID, hostUuid);
        bus.send(msg, new CloudBusCallBack(completion) {
            @Override
            public void run(MessageReply reply) {
                if (!reply.isSuccess()) {
                    completion.fail(reply.getError());
                    return;
                }

                KVMHostAsyncHttpCallReply r = reply.castReply();
                T rsp = r.toResponse(rspType);
                ErrorCode errorCode = rsp.buildErrorCode();
                if (errorCode != null) {
                    completion.fail(errorCode);
                    return;
                }

                if (rsp.getTotalCapacity() != null && rsp.getAvailableCapacity() != null) {
                    new LocalStorageCapacityUpdater().updatePhysicalCapacityByKvmAgentResponse(ps.getUuid(), hostUuid, rsp);
                }

                completion.success(rsp);
            }
        });
    }

    void download(final ReturnValueCompletion<Object> completion) {
        DebugUtils.Assert(image != null, "image cannot be null");
        DebugUtils.Assert(hostUuid != null, "host uuid cannot be null");
        DebugUtils.Assert(primaryStorageInstallPath != null, "primaryStorageInstallPath cannot be null");

        thdf.chainSubmit(new ChainTask(completion) {
            @Override
            public String getSyncSignature() {
                return getQueueName();
            }

            private void doDownload(final SyncTaskChain chain) {
                if (volumeResourceInstallPath == null) {
                    DebugUtils.Assert(backupStorage != null, "backup storage cannot be null");
                    DebugUtils.Assert(backupStorageInstallPath != null, "backupStorageInstallPath cannot be null");
                }

                taskProgress("Download the image[%s] to the image cache", image.getName());

                FlowChain fchain = FlowChainBuilder.newShareFlowChain();
                fchain.setName(String.format("download-image-%s-to-local-storage-%s-cache-host-%s",
                        image.getUuid(), ps.getUuid(), hostUuid));
                fchain.then(new ShareFlow() {
                    String psUuid;
                    long actualSize = image.getActualSize();
                    String allocatedInstallUrl;

                    @Override
                    public void setup() {
                        flow(new Flow() {
                            String __name__ = "allocate-primary-storage";

                            boolean s = false;

                            @Override
                            public void run(final FlowTrigger trigger, Map data) {
                                AllocatePrimaryStorageSpaceMsg amsg = new AllocatePrimaryStorageSpaceMsg();
                                amsg.setRequiredPrimaryStorageUuid(ps.getUuid());
                                amsg.setRequiredHostUuid(hostUuid);
                                amsg.setSize(image.getActualSize());
                                amsg.setPurpose(PrimaryStorageAllocationPurpose.DownloadImage.toString());
                                amsg.setNoOverProvisioning(true);
                                amsg.setImageUuid(image.getUuid());
                                bus.makeLocalServiceId(amsg, PrimaryStorageConstant.SERVICE_ID);
                                bus.send(amsg, new CloudBusCallBack(trigger) {
                                    @Override
                                    public void run(MessageReply reply) {
                                        if (!reply.isSuccess()) {
                                            trigger.fail(reply.getError());
                                            return;
                                        }
                                        s = true;
                                        AllocatePrimaryStorageSpaceReply ar = (AllocatePrimaryStorageSpaceReply) reply;
                                        allocatedInstallUrl = ar.getAllocatedInstallUrl();
                                        psUuid = ar.getPrimaryStorageInventory().getUuid();
                                        trigger.next();
                                    }
                                });
                            }

                            @Override
                            public void rollback(FlowRollback trigger, Map data) {
                                if (s) {
                                    ReleasePrimaryStorageSpaceMsg rmsg = new ReleasePrimaryStorageSpaceMsg();
                                    rmsg.setAllocatedInstallUrl(allocatedInstallUrl);
                                    rmsg.setDiskSize(image.getActualSize());
                                    rmsg.setNoOverProvisioning(true);
                                    rmsg.setPrimaryStorageUuid(ps.getUuid());
                                    bus.makeLocalServiceId(rmsg, PrimaryStorageConstant.SERVICE_ID);
                                    bus.send(rmsg);
                                }

                                trigger.rollback();
                            }
                        });
                        flow(new NoRollbackFlow() {
                            String __name__ = "download";

                            @Override
                            public void run(final FlowTrigger trigger, Map data) {
                                if (volumeResourceInstallPath != null) {
                                    if (incremental) {
                                        incrementalDownloadFromVolume(trigger);
                                    } else {
                                        downloadFromVolume(trigger);
                                    }
                                } else {
                                    downloadFromBackupStorage(trigger);
                                }
                            }

                            private void incrementalDownloadFromVolume(FlowTrigger trigger) {
                                LocalStorageKvmBackend.CreateVolumeWithBackingCmd cmd = new LocalStorageKvmBackend.CreateVolumeWithBackingCmd();
                                cmd.installPath = primaryStorageInstallPath;
                                cmd.templatePathInCache = volumeResourceInstallPath;

                                httpCall(CREATE_VOLUME_WITH_BACKING_PATH, hostUuid, cmd,
                                        LocalStorageKvmBackend.CreateVolumeWithBackingRsp.class,
                                        new ReturnValueCompletion<LocalStorageKvmBackend.CreateVolumeWithBackingRsp>(trigger) {
                                            @Override
                                            public void success(LocalStorageKvmBackend.CreateVolumeWithBackingRsp rsp) {
                                                actualSize = rsp.actualSize;
                                                trigger.next();
                                            }

                                            @Override
                                            public void fail(ErrorCode errorCode) {
                                                trigger.fail(errorCode);
                                            }
                                        });
                            }

                            private void downloadFromVolume(FlowTrigger trigger) {
                                LocalStorageKvmBackend.CreateTemplateFromVolumeCmd cmd = new LocalStorageKvmBackend.CreateTemplateFromVolumeCmd();
                                cmd.setInstallPath(primaryStorageInstallPath);
                                cmd.setVolumePath(volumeResourceInstallPath);

                                httpCall(CREATE_TEMPLATE_FROM_VOLUME, hostUuid, cmd,
                                        LocalStorageKvmBackend.CreateTemplateFromVolumeRsp.class,
                                        new ReturnValueCompletion<LocalStorageKvmBackend.CreateTemplateFromVolumeRsp>(trigger) {
                                            @Override
                                            public void success(LocalStorageKvmBackend.CreateTemplateFromVolumeRsp rsp) {
                                                actualSize = rsp.getActualSize();
                                                trigger.next();
                                            }

                                            @Override
                                            public void fail(ErrorCode errorCode) {
                                                trigger.fail(errorCode);
                                            }
                                        });
                            }

                            private void downloadFromBackupStorage(FlowTrigger trigger) {
                                LocalStorageBackupStorageMediator m = localStorageFactory.getBackupStorageMediator(KVMConstant.KVM_HYPERVISOR_TYPE, backupStorage.getType());
                                m.downloadBits(ps, backupStorage,
                                        backupStorageInstallPath, primaryStorageInstallPath,
                                        hostUuid, false, new Completion(trigger) {
                                            @Override
                                            public void success() {
                                                trigger.next();
                                            }

                                            @Override
                                            public void fail(ErrorCode errorCode) {
                                                trigger.fail(errorCode);
                                            }
                                        });
                            }
                        });

                        done(new FlowDoneHandler(completion, chain) {
                            @Override
                            public void handle(Map data) {
                                ImageCacheVO vo = new ImageCacheVO();
                                vo.setState(ImageCacheState.ready);
                                vo.setMediaType(ImageConstant.ImageMediaType.valueOf(image.getMediaType()));
                                vo.setImageUuid(image.getUuid());
                                vo.setPrimaryStorageUuid(ps.getUuid());
                                vo.setSize(actualSize);
                                vo.setMd5sum("not calculated");

                                LocalStorageUtils.InstallPath path = new LocalStorageUtils.InstallPath();
                                path.installPath = primaryStorageInstallPath;
                                path.hostUuid = hostUuid;
                                vo.setInstallUrl(path.makeFullPath());
                                dbf.persist(vo);

                                logger.debug(String.format("downloaded image[uuid:%s, name:%s] to the image cache of local primary storage[uuid: %s, installPath: %s] on host[uuid: %s]",
                                        image.getUuid(), image.getName(), ps.getUuid(), primaryStorageInstallPath, hostUuid));

                                ImageCacheInventory inv = ImageCacheInventory.valueOf(vo);
                                inv.setInstallUrl(primaryStorageInstallPath);

                                pluginRgty.getExtensionList(AfterCreateImageCacheExtensionPoint.class)
                                        .forEach(exp -> exp.saveEncryptAfterCreateImageCache(hostUuid, inv));

                                completion.success(inv);
                                chain.next();
                            }
                        });

                        error(new FlowErrorHandler(completion, chain) {
                            @Override
                            public void handle(ErrorCode errCode, Map data) {
                                completion.fail(errCode);
                                chain.next();
                            }
                        });
                    }
                }).start();
            }

            private void checkEncryptImageCache(ImageCacheInventory inventory, final SyncTaskChain chain) {
                List<AfterCreateImageCacheExtensionPoint> extensionList = pluginRgty.getExtensionList(AfterCreateImageCacheExtensionPoint.class);

                if (extensionList.isEmpty()) {
                    completion.success(inventory);
                    chain.next();
                    return;
                }

                extensionList.forEach(ext -> ext.checkEncryptImageCache(hostUuid, inventory, new Completion(chain) {
                    @Override
                    public void success() {
                        completion.success(inventory);
                        chain.next();
                    }

                    @Override
                    public void fail(ErrorCode errorCode) {
                        completion.fail(errorCode);
                        chain.next();
                    }
                }));
            }

            @Override
            public void run(final SyncTaskChain chain) {
                SimpleQuery<ImageCacheVO> q = dbf.createQuery(ImageCacheVO.class);
                q.add(ImageCacheVO_.primaryStorageUuid, SimpleQuery.Op.EQ, ps.getUuid());
                q.add(ImageCacheVO_.imageUuid, SimpleQuery.Op.EQ, image.getUuid());
                q.add(ImageCacheVO_.installUrl, SimpleQuery.Op.LIKE, String.format("%%hostUuid://%s%%", hostUuid));
                ImageCacheVO cache = q.find();
                if (cache == null) {
                    doDownload(chain);
                    return;
                }

                LocalStorageUtils.InstallPath path = new LocalStorageUtils.InstallPath();
                path.fullPath = cache.getInstallUrl();
                final String installPath = path.disassemble().installPath;
                LocalStorageKvmBackend.CheckBitsCmd cmd = new LocalStorageKvmBackend.CheckBitsCmd();
                cmd.path = installPath;

                httpCall(CHECK_BITS_PATH, hostUuid, cmd, LocalStorageKvmBackend.CheckBitsRsp.class, new ReturnValueCompletion<LocalStorageKvmBackend.CheckBitsRsp>(completion, chain) {
                    @Override
                    public void success(LocalStorageKvmBackend.CheckBitsRsp rsp) {
                        if (rsp.existing) {
                            logger.debug(String.format("found image[uuid: %s, name: %s] in the image cache of local primary storage[uuid:%s, installPath: %s]",
                                    image.getUuid(), image.getName(), ps.getUuid(), installPath));

                            ImageCacheInventory inv = ImageCacheInventory.valueOf(cache);
                            inv.setInstallUrl(installPath);

                            checkEncryptImageCache(inv, chain);
                            return;
                        }

                        // the image is removed on the host
                        // delete the cache object and re-download it
                        SimpleQuery<ImageCacheVO> q = dbf.createQuery(ImageCacheVO.class);
                        q.add(ImageCacheVO_.primaryStorageUuid, SimpleQuery.Op.EQ, ps.getUuid());
                        q.add(ImageCacheVO_.imageUuid, SimpleQuery.Op.EQ, image.getUuid());
                        q.add(ImageCacheVO_.installUrl, SimpleQuery.Op.LIKE, String.format("%%hostUuid://%s%%", hostUuid));
                        ImageCacheVO cvo = q.find();

                        ReleasePrimaryStorageSpaceMsg rmsg = new ReleasePrimaryStorageSpaceMsg();
                        rmsg.setDiskSize(cvo.getSize());
                        rmsg.setPrimaryStorageUuid(cvo.getPrimaryStorageUuid());
                        rmsg.setAllocatedInstallUrl(cvo.getInstallUrl());
                        bus.makeTargetServiceIdByResourceUuid(rmsg, PrimaryStorageConstant.SERVICE_ID, cvo.getPrimaryStorageUuid());
                        bus.send(rmsg);

                        dbf.remove(cvo);
                        doDownload(chain);
                    }

                    @Override
                    public void fail(ErrorCode errorCode) {
                        completion.fail(errorCode);
                        chain.next();
                    }
                });
            }

            @Override
            public String getName() {
                return getSyncSignature();
            }
        });
    }

    @Override
    public void run(ReturnValueCompletion<Object> completion) {
        download(completion);
    }
}