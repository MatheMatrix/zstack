package org.zstack.testlib

import org.springframework.http.HttpEntity
import org.zstack.compute.host.HostSystemTags
import org.zstack.core.Platform
import org.zstack.core.db.Q
import org.zstack.header.Constants
import org.zstack.header.host.HostVO
import org.zstack.header.host.HostVO_
import org.zstack.header.storage.snapshot.VolumeSnapshotVO
import org.zstack.header.storage.snapshot.VolumeSnapshotVO_
import org.zstack.header.tag.SystemTagVO
import org.zstack.header.tag.SystemTagVO_
import org.zstack.header.volume.VolumeVO
import org.zstack.header.volume.VolumeVO_
import org.zstack.sdk.PrimaryStorageInventory
import org.zstack.storage.primary.local.LocalStorageKvmBackend
import org.zstack.storage.primary.local.LocalStorageKvmMigrateVmFlow
import org.zstack.storage.primary.local.LocalStorageKvmSftpBackupStorageMediatorImpl
import org.zstack.testlib.vfs.Qcow2
import org.zstack.testlib.vfs.VFS
import org.zstack.testlib.vfs.VFSFile
import org.zstack.testlib.vfs.Volume
import org.zstack.utils.gson.JSONObjectUtil

import java.nio.file.Path
/**
 * Created by xing5 on 2017/2/20.
 */
class LocalStorageSpec extends PrimaryStorageSpec {

    LocalStorageSpec(EnvSpec envSpec) {
        super(envSpec)
    }

    static String hostUuidFromHTTPHeaders(HttpEntity<String> e) {
        return e.getHeaders().getFirst(Constants.AGENT_HTTP_HEADER_RESOURCE_UUID)
    }

    static VFS vfs(HttpEntity<String> e, LocalStorageKvmBackend.AgentCommand cmd, EnvSpec spec) {
        return vfs(hostUuidFromHTTPHeaders(e), cmd.storagePath, spec)
    }

    static VFS vfs(String hostUuid, String storagePath, EnvSpec spec, boolean errorOnNotExisting=false) {
        return spec.getVirtualFileSystem("${hostUuid}${storagePath}", errorOnNotExisting)
    }

    static class Simulators implements Simulator {
        @Override
        void registerSimulators(EnvSpec espec) {
            def simulator = { arg1, arg2 ->
                espec.simulator(arg1, arg2)
            }
            
            simulator(LocalStorageKvmBackend.GET_QCOW2_REFERENCE) { HttpEntity<String> e, EnvSpec spec ->
                return new LocalStorageKvmBackend.GetQCOW2ReferenceRsp(referencePaths: [])
            }

            VFS.vfsHook(LocalStorageKvmBackend.GET_QCOW2_REFERENCE, espec) {rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.GetQCOW2ReferenceCmd.class)

                vfs(e, cmd, spec).walkFileSystem { f ->
                    if (!(f instanceof Qcow2)) {
                        return
                    }

                    if (f.backingFile != null && f.backingFile.toAbsolutePath().toString() == cmd.path) {
                        rsp.referencePaths.add(f.pathString())
                    }
                }

                return rsp
            }

            simulator(LocalStorageKvmBackend.GET_BASE_IMAGE_PATH) {
                def rsp = new LocalStorageKvmBackend.GetVolumeBaseImagePathRsp()
                return rsp
            }

            VFS.vfsHook(LocalStorageKvmBackend.GET_BASE_IMAGE_PATH, espec) { LocalStorageKvmBackend.GetVolumeBaseImagePathRsp rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.GetVolumeBaseImagePathCmd.class)

                VFS vfs = vfs(e, cmd, spec)

                Qcow2 qfile = vfs.getFile(cmd.volumeInstallPath)
                if (qfile == null) {
                    logger.debug("Dump of whole VFS:\\n${vfs.dumpAsString()}")
                }
                assert qfile != null : "cannot find file[${cmd.volumeInstallPath}]"
                while (qfile.backingFile != null) {
                    if (qfile.backingFile.toAbsolutePath().toString().startsWith(cmd.imageCacheDir)) {
                        rsp.path = qfile.backingFile.toAbsolutePath().toString()
                        rsp.size = qfile.backingQcow2().actualSize
                        break
                    }

                    qfile = qfile.backingQcow2()
                }

                rsp.otherPaths = []
                vfs.walkFileSystem { vfile ->
                    if (vfile.pathString().contains(cmd.volumeInstallDir)) {
                        qfile = vfile as Qcow2
                        if (qfile.backingFile != null && qfile.backingFile.toAbsolutePath().toString().startsWith(cmd.imageCacheDir)) {
                            rsp.otherPaths.add(qfile.backingFile.toAbsolutePath().toString())
                        }
                    }
                }

                rsp.otherPaths.remove(rsp.path)
                return rsp
            }

            simulator(LocalStorageKvmBackend.GET_BACKING_FILE_PATH) { HttpEntity<String> e, EnvSpec spec ->
                return new LocalStorageKvmBackend.GetBackingFileRsp()
            }

            VFS.vfsHook(LocalStorageKvmBackend.GET_BACKING_FILE_PATH, espec) { rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.GetBackingFileCmd.class)

                Qcow2 file = vfs(e, cmd, spec).findFile { it.pathString() == cmd.path }
                assert file : "cannot find file[${cmd.path}]"

                if (file.backingFile != null) {
                    rsp.backingFilePath = file.backingFile.toAbsolutePath().toString()
                }
                rsp.size = 0
                return rsp
            }

            simulator(LocalStorageKvmBackend.GET_BACKING_CHAIN_PATH) { HttpEntity<String> e, EnvSpec spec ->
                return new LocalStorageKvmBackend.GetBackingChainRsp()
            }

            VFS.vfsHook(LocalStorageKvmBackend.GET_BACKING_CHAIN_PATH, espec) { rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.GetBackingChainCmd.class)

                List<String> chain = []
                VFS vfs = vfs(e, cmd, spec)
                Qcow2 file = vfs.getFile(cmd.installPath)
                if (file == null) {
                    logger.debug("Dump of whole VFS:\\n${vfs.dumpAsString()}")
                }
                assert file != null : "cannot find file[${cmd.installPath}]"
                while (file.backingQcow2() != null) {
                    chain.add(file.backingQcow2().pathString())
                    file = file.backingQcow2()
                }

                rsp.backingChain = chain
                return rsp
            }

            simulator(LocalStorageKvmBackend.GET_MD5_PATH) {HttpEntity<String> e ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.GetMd5Cmd.class)
                def rsp = new LocalStorageKvmBackend.GetMd5Rsp()
                rsp.md5s = []
                cmd.md5s.forEach{it ->
                    def t = new LocalStorageKvmBackend.Md5TO()
                    t.resourceUuid = it.resourceUuid
                    t.path = it .path
                    t.md5 = "mockmd5" + it.resourceUuid.substring(7)
                    rsp.md5s.add(t)
                }
                return rsp
            }

            simulator(LocalStorageKvmBackend.CHECK_MD5_PATH) {
                return new LocalStorageKvmBackend.AgentResponse()
            }

            simulator(LocalStorageKvmMigrateVmFlow.COPY_TO_REMOTE_BITS_PATH) { HttpEntity<String> e, EnvSpec spec ->
                return new LocalStorageKvmBackend.AgentResponse()
            }

            VFS.vfsHook(LocalStorageKvmMigrateVmFlow.COPY_TO_REMOTE_BITS_PATH, espec) { rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmMigrateVmFlow.CopyBitsFromRemoteCmd.class)
                String srcHostUuid = hostUuidFromHTTPHeaders(e)
                String storagePath = cmd.storagePath
                String dstHostUuid = Q.New(HostVO.class).select(HostVO_.uuid).eq(HostVO_.managementIp, cmd.dstIp).findValue()
                if (dstHostUuid == null) {
                    // try finding in HostSystemTags.EXTRA_IPS_TOKEN, this happens if migration network used
                    String tag = HostSystemTags.EXTRA_IPS.instantiateTag([(HostSystemTags.EXTRA_IPS_TOKEN) : cmd.dstIp])
                    SystemTagVO tvo = Q.New(SystemTagVO.class).eq(SystemTagVO_.tag, tag).find()
                    if (tvo != null) {
                        dstHostUuid = tvo.resourceUuid
                    }
                }

                assert dstHostUuid : "cannot find host[ip:${cmd.dstIp}]"

                VFS srcVFS = vfs(srcHostUuid, storagePath, spec)
                List<VFSFile> files = []
                cmd.paths.each {
                    VFSFile f = srcVFS.getFile(it, true)
                    files.add(f)
                }

                VFS dstVFS = vfs(dstHostUuid, storagePath, spec)
                files.each {
                    dstVFS.createFileFrom(it)
                }

                return rsp
            }

            simulator(LocalStorageKvmMigrateVmFlow.REBASE_SNAPSHOT_BACKING_FILES_PATH) { HttpEntity<String> e, EnvSpec spec ->
                return new LocalStorageKvmBackend.AgentResponse()
            }

            VFS.vfsHook(LocalStorageKvmMigrateVmFlow.REBASE_SNAPSHOT_BACKING_FILES_PATH, espec) { rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmMigrateVmFlow.RebaseSnapshotBackingFilesCmd.class)
                VFS vfs = vfs(e, cmd, spec)
                cmd.snapshots.each {
                    Qcow2 f = vfs.getFile(it.path)
                    assert f : "cannot find file[${it.path}]"
                    if (it.parentPath != null) {
                        // the python localstorage agent does this, if parentPath is null, no rebase
                        f.rebase(it.parentPath)
                    }
                }

                return rsp
            }

            simulator(LocalStorageKvmMigrateVmFlow.VERIFY_SNAPSHOT_CHAIN_PATH) { HttpEntity<String> e, EnvSpec spec ->
                return new LocalStorageKvmBackend.AgentResponse()
            }

            VFS.vfsHook(LocalStorageKvmMigrateVmFlow.VERIFY_SNAPSHOT_CHAIN_PATH, espec) { rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmMigrateVmFlow.VerifySnapshotChainCmd.class)
                VFS vfs = vfs(e, cmd, spec)
                cmd.snapshots.each {
                    Qcow2 f = vfs.getFile(it.path)
                    assert f : "cannot find file[${f.pathString()}]"

                    if (it.parentPath != null) {
                        Qcow2 p = vfs.getFile(it.parentPath)
                        assert p : "cannot find backing file[${it.parentPath}]"
                        assert f.backingFile == p.path : "epxect qcow2[${it.path}]'s backign file is ${it.parentPath}, but got ${f.backingFile.toAbsolutePath().toString()}"
                    }
                }

                return rsp
            }

            simulator(LocalStorageKvmBackend.INIT_PATH) { HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.InitCmd.class)

                def rsp = new LocalStorageKvmBackend.InitRsp()
                rsp.localStorageUsedCapacity = 0L

                LocalStorageSpec lspec = spec.specByUuid(cmd.uuid)
                if (lspec != null) {
                    rsp.totalCapacity = lspec.totalCapacity
                    rsp.availableCapacity = lspec.availableCapacity
                }

                return rsp
            }

            VFS.vfsHook(LocalStorageKvmBackend.INIT_PATH, espec) { rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.InitCmd.class)

                // init operation should use path to initialize vfs
                VFS vfs = vfs(hostUuidFromHTTPHeaders(e), cmd.path, spec)
                vfs.createDirectories(cmd.path)

                return rsp
            }

            simulator(LocalStorageKvmBackend.CHECK_BITS_PATH) { HttpEntity<String> e, EnvSpec spec ->
                return new LocalStorageKvmBackend.CheckBitsRsp()
            }

            VFS.vfsHook(LocalStorageKvmBackend.CHECK_BITS_PATH, espec) { rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.CheckBitsCmd.class)
                VFS vfs = vfs(e, cmd, spec)
                rsp.existing = vfs.getFile(cmd.path) != null
                return rsp
            }

            simulator(LocalStorageKvmBackend.GET_PHYSICAL_CAPACITY_PATH) { HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.GetPhysicalCapacityCmd.class)
                LocalStorageSpec lspec = spec.specByUuid(cmd.uuid)
                assert lspec != null: "cannot find local storage[uuid:${cmd.uuid}]"

                def rsp = new LocalStorageKvmBackend.AgentResponse()
                rsp.totalCapacity = lspec.totalCapacity
                rsp.availableCapacity = lspec.availableCapacity
                return rsp
            }

            simulator(LocalStorageKvmBackend.CREATE_VOLUME_WITH_BACKING_PATH) {
                return new LocalStorageKvmBackend.CreateVolumeWithBackingRsp()
            }

            VFS.vfsHook(LocalStorageKvmBackend.CREATE_VOLUME_WITH_BACKING_PATH, espec) { rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.CreateVolumeWithBackingCmd.class)
                VFS vfs = vfs(e, cmd, spec)

                Volume image = vfs.getFile(cmd.templatePathInCache, true)
                vfs.createQcow2(cmd.installPath, image.actualSize, image.virtualSize, cmd.templatePathInCache)
                return rsp
            }

            simulator(LocalStorageKvmBackend.CREATE_EMPTY_VOLUME_PATH) { HttpEntity<String> e, EnvSpec spec ->
                return new LocalStorageKvmBackend.CreateEmptyVolumeRsp()
            }

            simulator(LocalStorageKvmBackend.CREATE_FOLDER_PATH) {
                return new LocalStorageKvmBackend.AgentResponse()
            }

            VFS.vfsHook(LocalStorageKvmBackend.CREATE_FOLDER_PATH, espec) { rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.CreateFolderCmd.class)
                VFS vfs = vfs(e, cmd, spec)
                vfs.createDirectories(cmd.installUrl)
                return rsp
            }

            VFS.vfsHook(LocalStorageKvmBackend.CREATE_EMPTY_VOLUME_PATH, espec) { rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.CreateEmptyVolumeCmd.class)
                VFS vfs = vfs(e, cmd, spec)
                vfs.createQcow2(cmd.installUrl, cmd.size, cmd.size, cmd.backingFile)
                return rsp
            }

            simulator(LocalStorageKvmBackend.CREATE_VOLUME_FROM_CACHE_PATH) { HttpEntity<String> e, EnvSpec spec ->
                return new LocalStorageKvmBackend.CreateVolumeFromCacheRsp()
            }


            VFS.vfsHook(LocalStorageKvmBackend.CREATE_VOLUME_FROM_CACHE_PATH, espec) { rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.CreateVolumeFromCacheCmd.class)
                VFS vfs = vfs(e, cmd, spec)
                Volume image = vfs.getFile(cmd.templatePathInCache, true)
                vfs.createQcow2(cmd.installUrl, image.actualSize, image.virtualSize, image.pathString())
                return rsp
            }

            simulator(LocalStorageKvmBackend.DELETE_BITS_PATH) { HttpEntity<String> e, EnvSpec spec ->
                return new LocalStorageKvmBackend.DeleteBitsRsp()
            }

            VFS.vfsHook(LocalStorageKvmBackend.DELETE_BITS_PATH, espec) { rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.DeleteBitsCmd.class)
                VFS vfs = vfs(e, cmd, spec)
                VFSFile file = vfs.getFile(cmd.path)
                assert file != null : "cannot find file[${cmd.path}]"
                file.delete()
                return rsp
            }

            simulator(LocalStorageKvmBackend.DELETE_DIR_PATH) { HttpEntity<String> e, EnvSpec spec ->
                return new LocalStorageKvmBackend.DeleteBitsRsp()
            }

            VFS.vfsHook(LocalStorageKvmBackend.DELETE_DIR_PATH, espec) { rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.DeleteBitsCmd.class)
                VFS vfs = vfs(e, cmd, spec)
                assert vfs.isDir(cmd.path) : "${cmd.path} is not a directory"
                vfs.delete(cmd.path)
                return rsp
            }

            simulator(LocalStorageKvmBackend.UNLINK_BITS_PATH) {
                return new LocalStorageKvmBackend.UnlinkBitsRsp()
            }

            VFS.vfsHook(LocalStorageKvmBackend.UNLINK_BITS_PATH, espec) { rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.UnlinkBitsCmd.class)
                VFS vfs = vfs(e, cmd, spec)
                assert vfs.exists(cmd.installPath)
                vfs.unlink(cmd.installPath, cmd.onlyLinkedFile)
                return rsp
            }

//            simulator(LocalStorageKvmBackend.GET_LIST_PATH) { HttpEntity<String> e, EnvSpec spec ->
//                return new LocalStorageKvmBackend.ListPathRsp(paths: [])
//            }
//
//            VFS.vfsHook(LocalStorageKvmBackend.GET_LIST_PATH, espec) { rsp, HttpEntity<String> e, EnvSpec spec ->
//                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.ListPathCmd.class)
//                VFS vfs = vfs(e, cmd, spec)
//                Path path = vfs.getPath(cmd.path)
//                Files.list(path).forEach {
//                    rsp.paths.add(it.toAbsolutePath().toString())
//                }
//
//                return rsp
//            }

            simulator(LocalStorageKvmSftpBackupStorageMediatorImpl.DOWNLOAD_BIT_PATH) { HttpEntity<String> e, EnvSpec spec ->
                return new LocalStorageKvmSftpBackupStorageMediatorImpl.SftpDownloadBitsRsp()
            }

            VFS.vfsHook(LocalStorageKvmSftpBackupStorageMediatorImpl.DOWNLOAD_BIT_PATH, espec) { rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmSftpBackupStorageMediatorImpl.SftpDownloadBitsCmd.class)
                VFS vfs = vfs(e, cmd, spec)
                vfs.createQcow2(cmd.primaryStorageInstallPath, 0L, 0L, null)
                return rsp
            }

            simulator(LocalStorageKvmSftpBackupStorageMediatorImpl.UPLOAD_BIT_PATH) {
                return new LocalStorageKvmSftpBackupStorageMediatorImpl.SftpUploadBitsRsp()
            }

            simulator(LocalStorageKvmBackend.CREATE_TEMPLATE_FROM_VOLUME) { HttpEntity<String> e, EnvSpec spec ->
                return new LocalStorageKvmBackend.CreateTemplateFromVolumeRsp()
            }

            VFS.vfsHook(LocalStorageKvmBackend.CREATE_TEMPLATE_FROM_VOLUME, espec) { rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.CreateTemplateFromVolumeCmd.class)
                VFS vfs = vfs(e, cmd, spec)
                Qcow2 volume = vfs.getFile(cmd.volumePath)
                assert volume : "cannot find volume[${cmd.volumePath}]"
                volume.copyWithoutBackingFile(cmd.installPath)
                return rsp
            }

            simulator(LocalStorageKvmBackend.ESTIMATE_TEMPLATE_SIZE_PATH) {
                def rsp = new LocalStorageKvmBackend.EstimateTemplateSizeRsp()
                rsp.size = 0
                rsp.actualSize = 0
                return rsp
            }

            VFS.vfsHook(LocalStorageKvmBackend.ESTIMATE_TEMPLATE_SIZE_PATH, espec) { rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.EstimateTemplateSizeCmd.class)
                VFS srcVFS = vfs(e, cmd, spec)
                Qcow2 qcow2 = srcVFS.getFile(cmd.volumePath)
                rsp.size = qcow2.virtualSize
                rsp.actualSize = qcow2.actualSize
                return rsp
            }

            simulator(LocalStorageKvmBackend.REVERT_SNAPSHOT_PATH) { HttpEntity<String> e ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.RevertVolumeFromSnapshotCmd.class)
                def rsp = new LocalStorageKvmBackend.RevertVolumeFromSnapshotRsp()
                rsp.newVolumeInstallPath = cmd.snapshotInstallPath + "/${Platform.uuid}".toString()
                rsp.newVolumeInstallPath = cmd.snapshotInstallPath + "/newpath"
            }

            simulator(LocalStorageKvmBackend.REINIT_IMAGE_PATH) {  HttpEntity<String> e, EnvSpec spec ->
                return new LocalStorageKvmBackend.ReinitImageRsp()
            }

            VFS.vfsHook(LocalStorageKvmBackend.REINIT_IMAGE_PATH, espec) { rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.ReinitImageCmd.class)
                VFS vfs = vfs(e, cmd, spec)
                Qcow2 image = vfs.getFile(cmd.imagePath)
                assert image : "cannot find image[${cmd.imagePath}]"
                Path newVolumePath = vfs.getPath(cmd.volumePath).getParent().resolve("${Platform.uuid}.qcow2")
                vfs.createQcow2(newVolumePath.toAbsolutePath().toString(), image.actualSize, image.virtualSize, image.pathString())

                rsp.newVolumeInstallPath = newVolumePath.toAbsolutePath().toString()
                return rsp
            }

            simulator(LocalStorageKvmBackend.REVERT_SNAPSHOT_PATH) { HttpEntity<String> e, EnvSpec spec ->
                return new LocalStorageKvmBackend.RevertVolumeFromSnapshotRsp()
            }

            VFS.vfsHook(LocalStorageKvmBackend.REVERT_SNAPSHOT_PATH, espec) { rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.RevertVolumeFromSnapshotCmd.class)
                VFS vfs = vfs(e, cmd, spec)
                Qcow2 snapshot = vfs.getFile(cmd.snapshotInstallPath)
                assert snapshot : "cannot find snapshot[${cmd.snapshotInstallPath}]"
                Path newPath = snapshot.path.getParent().resolve("${Platform.uuid}.qcow2")
                vfs.createQcow2(newPath.toAbsolutePath().toString(), 0L, 0L, snapshot.pathString())

                rsp.newVolumeInstallPath = newPath.toAbsolutePath().toString()
                return rsp
            }

            simulator(LocalStorageKvmBackend.MERGE_SNAPSHOT_PATH) { HttpEntity<String> e, EnvSpec spec ->
                return new LocalStorageKvmBackend.MergeSnapshotRsp()
            }

            simulator(LocalStorageKvmBackend.GET_VOLUME_SIZE) { HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.GetVolumeSizeCmd.class)
                LocalStorageKvmBackend.GetVolumeSizeRsp rsp = new LocalStorageKvmBackend.GetVolumeSizeRsp()
                return rsp
            }

            VFS.vfsHook(LocalStorageKvmBackend.GET_VOLUME_SIZE, espec) { LocalStorageKvmBackend.GetVolumeSizeRsp rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.GetVolumeSizeCmd.class)
                VFS vfs = vfs(e, cmd, spec)
                Qcow2 volume = vfs.getFile(cmd.installPath)
                rsp.size = volume.virtualSize
                rsp.actualSize = volume.actualSize

                boolean isSnapshotExist = Q.New(VolumeSnapshotVO.class)
                        .eq(VolumeSnapshotVO_.volumeUuid, cmd.volumeUuid)
                        .exists
                if (!isSnapshotExist) {
                    rsp.actualSize = Q.New(VolumeVO.class).select(VolumeVO_.actualSize).eq(VolumeVO_.uuid, cmd.volumeUuid).findValue()
                } else {
                    rsp.actualSize = 1L
                }

                return rsp
            }

            VFS.vfsHook(LocalStorageKvmBackend.MERGE_SNAPSHOT_PATH, espec) { rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.MergeSnapshotCmd.class)
                VFS vfs = vfs(e, cmd, spec)
                Qcow2 snapshot = vfs.getFile(cmd.snapshotInstallPath)
                assert snapshot : "cannot find snapshot[${cmd.snapshotInstallPath}]"
                vfs.createQcow2(cmd.workspaceInstallPath, 0L, 0L, null)
                rsp.actualSize = 1
                return rsp
            }

            simulator(LocalStorageKvmBackend.OFFLINE_MERGE_PATH) { HttpEntity<String> e, EnvSpec spec ->
                def rsp = new LocalStorageKvmBackend.OfflineMergeSnapshotRsp()
                rsp.actualSize = 1
                return rsp
            }

            VFS.vfsHook(LocalStorageKvmBackend.OFFLINE_MERGE_PATH, espec) { LocalStorageKvmBackend.OfflineMergeSnapshotRsp rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.OfflineMergeSnapshotCmd.class)
                VFS vfs = vfs(e, cmd, spec)
                Qcow2 dst = vfs.getFile(cmd.destPath, true)
                if (cmd.fullRebase) {
                    dst.rebase((String) null)
                } else {
                    dst.rebase(cmd.srcPath)
                }
                rsp.actualSize = 1
                return rsp
            }

            simulator(LocalStorageKvmBackend.OFFLINE_COMMIT_PATH) { HttpEntity<String> e, EnvSpec spec ->
                def rsp = new LocalStorageKvmBackend.OfflineCommitSnapshotRsp()
                rsp.actualSize = 1
                return rsp
            }

            VFS.vfsHook(LocalStorageKvmBackend.OFFLINE_COMMIT_PATH, espec) { rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.OfflineCommitSnapshotCmd.class)
                VFS vfs = vfs(e, cmd, spec)
                Qcow2 src = vfs.getFile(cmd.top)
                Qcow2 dst = vfs.getFile(cmd.base)
                Qcow2 qcow2 = Qcow2.commit(vfs, src, dst)
                rsp.actualSize = qcow2.actualSize == 0 ? 1 : qcow2.actualSize
                return rsp
            }

            simulator(LocalStorageKvmBackend.CHECK_INITIALIZED_FILE) {
                return new LocalStorageKvmBackend.CheckInitializedFileRsp()
            }

            simulator(LocalStorageKvmBackend.CREATE_INITIALIZED_FILE) {
                return new LocalStorageKvmBackend.AgentResponse()
            }

            simulator(LocalStorageKvmBackend.DOWNLOAD_BITS_FROM_KVM_HOST_PATH) {
                def rsp = new LocalStorageKvmBackend.DownloadBitsFromKVMHostRsp()
                rsp.format = "qcow2"
                return new LocalStorageKvmBackend.AgentResponse()
            }

            simulator(LocalStorageKvmBackend.CANCEL_DOWNLOAD_BITS_FROM_KVM_HOST_PATH) {
                return new LocalStorageKvmBackend.AgentResponse()
            }


            simulator(LocalStorageKvmBackend.HARD_LINK_VOLUME) {
                return new LocalStorageKvmBackend.LinkVolumeNewDirRsp()
            }

            VFS.vfsHook(LocalStorageKvmBackend.HARD_LINK_VOLUME, espec) { rsp, HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.LinkVolumeNewDirCmd.class)
                VFS vfs = vfs(e, cmd, spec)
                def links = vfs.link(cmd.dstDir, cmd.srcDir)
                for (link in links) {
                    Qcow2 qf = vfs.getFile(link, true)
                    if (qf.backingFile != null) {
                        qf.rebase(qf.backingFile.toString().replace(cmd.srcDir, cmd.dstDir))
                    }
                    // TODO multi paths
                    qf.path = link
                    qf.update()
                }

                return rsp
            }

            simulator(LocalStorageKvmBackend.GET_DOWNLOAD_BITS_FROM_KVM_HOST_PROGRESS_PATH) {
                LocalStorageKvmBackend.GetDownloadBitsFromKVMHostProgressRsp rsp = new LocalStorageKvmBackend.GetDownloadBitsFromKVMHostProgressRsp()
                rsp.totalSize = 1L
                return rsp
            }

            simulator(LocalStorageKvmBackend.GET_QCOW2_HASH_VALUE_PATH) { HttpEntity<String> e, EnvSpec spec ->
                def cmd = JSONObjectUtil.toObject(e.body, LocalStorageKvmBackend.GetQcow2HashValueCmd.class)
                LocalStorageKvmBackend.GetQcow2HashValueRsp rsp = new LocalStorageKvmBackend.GetQcow2HashValueRsp()
                rsp.hashValue = cmd.installPath
                return rsp
            }

            simulator(LocalStorageKvmBackend.WRITE_VM_METADATA_PATH) {
                return new LocalStorageKvmBackend.WriteVmMetadataRsp()
            }

            simulator(LocalStorageKvmBackend.GET_VM_INSTANCE_METADATA_PATH) {
                def rsp = new LocalStorageKvmBackend.GetVmInstanceMetadataRsp()
                rsp.metadata = "{\"vmInstanceVO\":\"{\\\"vmNics\\\":[{\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"l3NetworkUuid\\\":\\\"28d3a9c8e54c48f290ab4f9e52bbb006\\\",\\\"mac\\\":\\\"fa:81:16:b2:32:00\\\",\\\"hypervisorType\\\":\\\"KVM\\\",\\\"deviceId\\\":0,\\\"internalName\\\":\\\"vnic1.0\\\",\\\"driverType\\\":\\\"virtio\\\",\\\"type\\\":\\\"VNIC\\\",\\\"state\\\":\\\"enable\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:46 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:46 AM\\\",\\\"usedIps\\\":[],\\\"uuid\\\":\\\"a77234a5a45a4a7caca46d01d746f41f\\\",\\\"resourceType\\\":\\\"VmNicVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.vm.VmNicVO\\\"}],\\\"allVolumes\\\":[{\\\"name\\\":\\\"volumeName1null\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/7832daf63d9b41d68bd1460c20ed0e0a\\\",\\\"type\\\":\\\"Data\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":2,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"isShareable\\\":false,\\\"shadow\\\":{\\\"name\\\":\\\"volumeName1null\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/7832daf63d9b41d68bd1460c20ed0e0a\\\",\\\"type\\\":\\\"Data\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":2,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"isShareable\\\":false},\\\"uuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"resourceName\\\":\\\"volumeName1null\\\",\\\"resourceType\\\":\\\"VolumeVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.volume.VolumeVO\\\"},{\\\"name\\\":\\\"ROOT-for-vmName\\\",\\\"description\\\":\\\"Root volume for VM[uuid:77bc3074f5f4438c836ce6c56bc5a4aa]\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"rootImageUuid\\\":\\\"575591e021b446e4b465e981da3a8d1b\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/bc5ab54cb3d04635923a2a9d0b5fc73f\\\",\\\"type\\\":\\\"Root\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":0,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:46 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:47 AM\\\",\\\"isShareable\\\":false,\\\"shadow\\\":{\\\"name\\\":\\\"ROOT-for-vmName\\\",\\\"description\\\":\\\"Root volume for VM[uuid:77bc3074f5f4438c836ce6c56bc5a4aa]\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"rootImageUuid\\\":\\\"575591e021b446e4b465e981da3a8d1b\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/bc5ab54cb3d04635923a2a9d0b5fc73f\\\",\\\"type\\\":\\\"Root\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":0,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:46 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:47 AM\\\",\\\"isShareable\\\":false},\\\"uuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"resourceName\\\":\\\"ROOT-for-vmName\\\",\\\"resourceType\\\":\\\"VolumeVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.volume.VolumeVO\\\"},{\\\"name\\\":\\\"volumeName1null\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/43436624dc714282913e0a141246629e\\\",\\\"type\\\":\\\"Data\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":1,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"isShareable\\\":false,\\\"shadow\\\":{\\\"name\\\":\\\"volumeName1null\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/43436624dc714282913e0a141246629e\\\",\\\"type\\\":\\\"Data\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":1,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"isShareable\\\":false},\\\"uuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"resourceName\\\":\\\"volumeName1null\\\",\\\"resourceType\\\":\\\"VolumeVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.volume.VolumeVO\\\"},{\\\"name\\\":\\\"volumeName1null\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/b711f22ad5c045b6ad1d770d4f301d05\\\",\\\"type\\\":\\\"Data\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":3,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"isShareable\\\":false,\\\"shadow\\\":{\\\"name\\\":\\\"volumeName1null\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/b711f22ad5c045b6ad1d770d4f301d05\\\",\\\"type\\\":\\\"Data\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":3,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"isShareable\\\":false},\\\"uuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"resourceName\\\":\\\"volumeName1null\\\",\\\"resourceType\\\":\\\"VolumeVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.volume.VolumeVO\\\"}],\\\"vmCdRoms\\\":[{\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"deviceId\\\":0,\\\"name\\\":\\\"vm-77bc3074f5f4438c836ce6c56bc5a4aa-cdRom\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:46 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:46 AM\\\",\\\"uuid\\\":\\\"e8a57f5b8c834573b4da822b672740e4\\\",\\\"resourceName\\\":\\\"vm-77bc3074f5f4438c836ce6c56bc5a4aa-cdRom\\\",\\\"resourceType\\\":\\\"VmCdRomVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.vm.cdrom.VmCdRomVO\\\"}],\\\"name\\\":\\\"vmName\\\",\\\"zoneUuid\\\":\\\"d71de3f6981d46c9a2be43e5fcf31021\\\",\\\"clusterUuid\\\":\\\"29f13acb820d4f7f8cd3593b79b742e5\\\",\\\"imageUuid\\\":\\\"575591e021b446e4b465e981da3a8d1b\\\",\\\"hostUuid\\\":\\\"e99debc09c5845fb8ed682320117f4ce\\\",\\\"internalId\\\":1,\\\"lastHostUuid\\\":\\\"e99debc09c5845fb8ed682320117f4ce\\\",\\\"rootVolumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"defaultL3NetworkUuid\\\":\\\"28d3a9c8e54c48f290ab4f9e52bbb006\\\",\\\"type\\\":\\\"UserVm\\\",\\\"hypervisorType\\\":\\\"KVM\\\",\\\"cpuNum\\\":1,\\\"cpuSpeed\\\":0,\\\"memorySize\\\":1073741824,\\\"reservedMemorySize\\\":0,\\\"platform\\\":\\\"Linux\\\",\\\"architecture\\\":\\\"x86_64\\\",\\\"guestOsType\\\":\\\"CentOS\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:45 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\",\\\"state\\\":\\\"Running\\\",\\\"uuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"resourceName\\\":\\\"vmName\\\",\\\"resourceType\\\":\\\"VmInstanceVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.vm.VmInstanceVO\\\"}\",\"vmSystemTags\":[\"{\\\"inherent\\\":false,\\\"uuid\\\":\\\"38a9b4bd1b8b3dfa829d582aafb2ec25\\\",\\\"resourceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"resourceType\\\":\\\"VmInstanceVO\\\",\\\"tag\\\":\\\"syncPorts::77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"type\\\":\\\"System\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:45 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:45 AM\\\"}\",\"{\\\"inherent\\\":true,\\\"uuid\\\":\\\"3e984cdb5edb47559a3f907e1d49bfcc\\\",\\\"resourceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"resourceType\\\":\\\"VmInstanceVO\\\",\\\"tag\\\":\\\"additionalQmp\\\",\\\"type\\\":\\\"System\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\"}\",\"{\\\"inherent\\\":false,\\\"uuid\\\":\\\"85237d3a06133523bd84669349040ec5\\\",\\\"resourceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"resourceType\\\":\\\"VmInstanceVO\\\",\\\"tag\\\":\\\"vmPriority::Normal\\\",\\\"type\\\":\\\"System\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\"}\",\"{\\\"inherent\\\":true,\\\"uuid\\\":\\\"b7c5d5e94ba13159ab2c8c65c1d7bc29\\\",\\\"resourceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"resourceType\\\":\\\"VmInstanceVO\\\",\\\"tag\\\":\\\"vmSystemSerialNumber::8ed14f00-50bb-4e9e-9448-e92c0f67e1e1\\\",\\\"type\\\":\\\"System\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\"}\",\"{\\\"inherent\\\":false,\\\"uuid\\\":\\\"d5019730aeba3e57b2f1a3e8d74d0cbc\\\",\\\"resourceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"resourceType\\\":\\\"VmInstanceVO\\\",\\\"tag\\\":\\\"ha::None\\\",\\\"type\\\":\\\"System\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\"}\"],\"vmResourceConfigs\":[\"{\\\"uuid\\\":\\\"8d2f9937a28846aba03fded826c10c73\\\",\\\"resourceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"resourceType\\\":\\\"VmInstanceVO\\\",\\\"name\\\":\\\"nicMultiQueueNum\\\",\\\"description\\\":\\\"default num of queues on virtio nic\\\",\\\"category\\\":\\\"vm\\\",\\\"value\\\":\\\"1\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\"}\"],\"volumeVOs\":[\"{\\\"name\\\":\\\"ROOT-for-vmName\\\",\\\"description\\\":\\\"Root volume for VM[uuid:77bc3074f5f4438c836ce6c56bc5a4aa]\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"rootImageUuid\\\":\\\"575591e021b446e4b465e981da3a8d1b\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/bc5ab54cb3d04635923a2a9d0b5fc73f\\\",\\\"type\\\":\\\"Root\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":0,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:46 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:47 AM\\\",\\\"isShareable\\\":false,\\\"shadow\\\":{\\\"name\\\":\\\"ROOT-for-vmName\\\",\\\"description\\\":\\\"Root volume for VM[uuid:77bc3074f5f4438c836ce6c56bc5a4aa]\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"rootImageUuid\\\":\\\"575591e021b446e4b465e981da3a8d1b\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/bc5ab54cb3d04635923a2a9d0b5fc73f\\\",\\\"type\\\":\\\"Root\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":0,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:46 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:47 AM\\\",\\\"isShareable\\\":false},\\\"uuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"resourceName\\\":\\\"ROOT-for-vmName\\\",\\\"resourceType\\\":\\\"VolumeVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.volume.VolumeVO\\\"}\",\"{\\\"name\\\":\\\"volumeName1null\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/b711f22ad5c045b6ad1d770d4f301d05\\\",\\\"type\\\":\\\"Data\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":3,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"isShareable\\\":false,\\\"shadow\\\":{\\\"name\\\":\\\"volumeName1null\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/b711f22ad5c045b6ad1d770d4f301d05\\\",\\\"type\\\":\\\"Data\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":3,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"isShareable\\\":false},\\\"uuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"resourceName\\\":\\\"volumeName1null\\\",\\\"resourceType\\\":\\\"VolumeVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.volume.VolumeVO\\\"}\",\"{\\\"name\\\":\\\"volumeName1null\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/7832daf63d9b41d68bd1460c20ed0e0a\\\",\\\"type\\\":\\\"Data\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":2,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"isShareable\\\":false,\\\"shadow\\\":{\\\"name\\\":\\\"volumeName1null\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/7832daf63d9b41d68bd1460c20ed0e0a\\\",\\\"type\\\":\\\"Data\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":2,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"isShareable\\\":false},\\\"uuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"resourceName\\\":\\\"volumeName1null\\\",\\\"resourceType\\\":\\\"VolumeVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.volume.VolumeVO\\\"}\",\"{\\\"name\\\":\\\"volumeName1null\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/43436624dc714282913e0a141246629e\\\",\\\"type\\\":\\\"Data\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":1,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"isShareable\\\":false,\\\"shadow\\\":{\\\"name\\\":\\\"volumeName1null\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"installPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/43436624dc714282913e0a141246629e\\\",\\\"type\\\":\\\"Data\\\",\\\"status\\\":\\\"Ready\\\",\\\"size\\\":1073741824,\\\"actualSize\\\":0,\\\"deviceId\\\":1,\\\"format\\\":\\\"qcow2\\\",\\\"state\\\":\\\"Enabled\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastAttachDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"isShareable\\\":false},\\\"uuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"resourceName\\\":\\\"volumeName1null\\\",\\\"resourceType\\\":\\\"VolumeVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.volume.VolumeVO\\\"}\"],\"volumeSystemTags\":{\"b7290c15276b4700af2c1b108b2b62e1\":[\"{\\\"inherent\\\":true,\\\"uuid\\\":\\\"b9874ec02b583538a5603e7eec8c5b69\\\",\\\"resourceUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"resourceType\\\":\\\"VolumeVO\\\",\\\"tag\\\":\\\"kvm::volume::0x000f59f934d14a68\\\",\\\"type\\\":\\\"System\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}\"],\"8d1e76eca52647f5a4544b9ff2d370de\":[\"{\\\"inherent\\\":true,\\\"uuid\\\":\\\"96cb4b006708387b8318f0fd6ae6ab8b\\\",\\\"resourceUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"resourceType\\\":\\\"VolumeVO\\\",\\\"tag\\\":\\\"kvm::volume::0x000faad0c9ca4231\\\",\\\"type\\\":\\\"System\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:48 AM\\\"}\"],\"ae9f28cb5055498e8661793d204208ba\":[\"{\\\"inherent\\\":true,\\\"uuid\\\":\\\"5ceacd06bf753b0c8abe5bcef9b5a894\\\",\\\"resourceUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"resourceType\\\":\\\"VolumeVO\\\",\\\"tag\\\":\\\"kvm::volume::0x000fc4ffeaab6e71\\\",\\\"type\\\":\\\"System\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}\"],\"db8251e870b14d60ace863a7598cce8b\":[\"{\\\"inherent\\\":true,\\\"uuid\\\":\\\"d53865baa675373a9bf07a6f501eab41\\\",\\\"resourceUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"resourceType\\\":\\\"VolumeVO\\\",\\\"tag\\\":\\\"kvm::volume::0x000fad154165d205\\\",\\\"type\\\":\\\"System\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\"}\"]},\"volumeResourceConfigs\":{\"b7290c15276b4700af2c1b108b2b62e1\":[],\"8d1e76eca52647f5a4544b9ff2d370de\":[],\"ae9f28cb5055498e8661793d204208ba\":[],\"db8251e870b14d60ace863a7598cce8b\":[]},\"vmNicVOs\":[\"{\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"l3NetworkUuid\\\":\\\"28d3a9c8e54c48f290ab4f9e52bbb006\\\",\\\"mac\\\":\\\"fa:81:16:b2:32:00\\\",\\\"hypervisorType\\\":\\\"KVM\\\",\\\"deviceId\\\":0,\\\"internalName\\\":\\\"vnic1.0\\\",\\\"driverType\\\":\\\"virtio\\\",\\\"type\\\":\\\"VNIC\\\",\\\"state\\\":\\\"enable\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:46 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:46 AM\\\",\\\"usedIps\\\":[],\\\"uuid\\\":\\\"a77234a5a45a4a7caca46d01d746f41f\\\",\\\"resourceType\\\":\\\"VmNicVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.vm.VmNicVO\\\"}\"],\"vmNicSystemTags\":{\"a77234a5a45a4a7caca46d01d746f41f\":[]},\"vmNicResourceConfigs\":{\"a77234a5a45a4a7caca46d01d746f41f\":[]},\"volumeSnapshots\":{\"b7290c15276b4700af2c1b108b2b62e1\":[\"{\\\"uuid\\\":\\\"7832daf63d9b41d68bd1460c20ed0e0a\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"treeUuid\\\":\\\"f8042fb57bb04ebcb0f01bab2abeb5dd\\\",\\\"parentUuid\\\":\\\"a31c3de68ce246538e982e0e5c7d2d73\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/a31c3de68ce246538e982e0e5c7d2d73\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":true,\\\"size\\\":1,\\\"distance\\\":4,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\"}\",\"{\\\"uuid\\\":\\\"a31c3de68ce246538e982e0e5c7d2d73\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"treeUuid\\\":\\\"f8042fb57bb04ebcb0f01bab2abeb5dd\\\",\\\"parentUuid\\\":\\\"b5d771aa83584c9c88d9b84147dfc9ad\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/b5d771aa83584c9c88d9b84147dfc9ad\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":false,\\\"size\\\":1,\\\"distance\\\":3,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\"}\",\"{\\\"uuid\\\":\\\"b5d771aa83584c9c88d9b84147dfc9ad\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"treeUuid\\\":\\\"f8042fb57bb04ebcb0f01bab2abeb5dd\\\",\\\"parentUuid\\\":\\\"bcc3ed8070984cd691c62f421aeaa44d\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/bcc3ed8070984cd691c62f421aeaa44d\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":false,\\\"size\\\":1,\\\"distance\\\":2,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:53 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\"}\",\"{\\\"uuid\\\":\\\"bcc3ed8070984cd691c62f421aeaa44d\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"treeUuid\\\":\\\"f8042fb57bb04ebcb0f01bab2abeb5dd\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/b7290c15276b4700af2c1b108b2b62e1\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":false,\\\"size\\\":1,\\\"distance\\\":1,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:51 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:53 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\"}\"],\"8d1e76eca52647f5a4544b9ff2d370de\":[\"{\\\"uuid\\\":\\\"04ef6d31675f4ba5816b104920dc3e2c\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-ROOT-for-vmName\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"treeUuid\\\":\\\"d4a030087ed3407894c393ee81f0bc3b\\\",\\\"parentUuid\\\":\\\"79caace79a1048d58ea7c0b38815bbd0\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/79caace79a1048d58ea7c0b38815bbd0\\\",\\\"volumeType\\\":\\\"Root\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":false,\\\"size\\\":0,\\\"distance\\\":3,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\"}\",\"{\\\"uuid\\\":\\\"61e2ada0170142bb8b303910a27690aa\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-ROOT-for-vmName\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"treeUuid\\\":\\\"d4a030087ed3407894c393ee81f0bc3b\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"volumeType\\\":\\\"Root\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":false,\\\"size\\\":0,\\\"distance\\\":1,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:51 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:53 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\"}\",\"{\\\"uuid\\\":\\\"79caace79a1048d58ea7c0b38815bbd0\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-ROOT-for-vmName\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"treeUuid\\\":\\\"d4a030087ed3407894c393ee81f0bc3b\\\",\\\"parentUuid\\\":\\\"61e2ada0170142bb8b303910a27690aa\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/61e2ada0170142bb8b303910a27690aa\\\",\\\"volumeType\\\":\\\"Root\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":false,\\\"size\\\":0,\\\"distance\\\":2,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:53 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\"}\",\"{\\\"uuid\\\":\\\"bc5ab54cb3d04635923a2a9d0b5fc73f\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-ROOT-for-vmName\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"treeUuid\\\":\\\"d4a030087ed3407894c393ee81f0bc3b\\\",\\\"parentUuid\\\":\\\"04ef6d31675f4ba5816b104920dc3e2c\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/04ef6d31675f4ba5816b104920dc3e2c\\\",\\\"volumeType\\\":\\\"Root\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":true,\\\"size\\\":0,\\\"distance\\\":4,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\"}\"],\"ae9f28cb5055498e8661793d204208ba\":[\"{\\\"uuid\\\":\\\"1aaa92b2d1eb4c36bb8951b8e1521b34\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"treeUuid\\\":\\\"055b80b0727e4117b246b1b29f2d58b6\\\",\\\"parentUuid\\\":\\\"aefbe47465c047d1b118321c34425869\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/aefbe47465c047d1b118321c34425869\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":false,\\\"size\\\":1,\\\"distance\\\":3,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\"}\",\"{\\\"uuid\\\":\\\"69e85ac72fea4263a55cbcd21785006e\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"treeUuid\\\":\\\"055b80b0727e4117b246b1b29f2d58b6\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/ae9f28cb5055498e8661793d204208ba\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":false,\\\"size\\\":1,\\\"distance\\\":1,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:51 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:53 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\"}\",\"{\\\"uuid\\\":\\\"aefbe47465c047d1b118321c34425869\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"treeUuid\\\":\\\"055b80b0727e4117b246b1b29f2d58b6\\\",\\\"parentUuid\\\":\\\"69e85ac72fea4263a55cbcd21785006e\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/69e85ac72fea4263a55cbcd21785006e\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":false,\\\"size\\\":1,\\\"distance\\\":2,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:53 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\"}\",\"{\\\"uuid\\\":\\\"b711f22ad5c045b6ad1d770d4f301d05\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"treeUuid\\\":\\\"055b80b0727e4117b246b1b29f2d58b6\\\",\\\"parentUuid\\\":\\\"1aaa92b2d1eb4c36bb8951b8e1521b34\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/1aaa92b2d1eb4c36bb8951b8e1521b34\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":true,\\\"size\\\":1,\\\"distance\\\":4,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\"}\"],\"db8251e870b14d60ace863a7598cce8b\":[\"{\\\"uuid\\\":\\\"43436624dc714282913e0a141246629e\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"treeUuid\\\":\\\"1c0773fa98f4465b8e535ba3c00dc039\\\",\\\"parentUuid\\\":\\\"a70bb2be871644b6ad12ac8d6e9524d0\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/a70bb2be871644b6ad12ac8d6e9524d0\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":true,\\\"size\\\":1,\\\"distance\\\":4,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\"}\",\"{\\\"uuid\\\":\\\"791f523bc99a4f08bd70b5a59d8ed5c8\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"treeUuid\\\":\\\"1c0773fa98f4465b8e535ba3c00dc039\\\",\\\"parentUuid\\\":\\\"a35b7ae1616a4974b2f80654c5527fbb\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/a35b7ae1616a4974b2f80654c5527fbb\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":false,\\\"size\\\":1,\\\"distance\\\":2,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:53 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\"}\",\"{\\\"uuid\\\":\\\"a35b7ae1616a4974b2f80654c5527fbb\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"treeUuid\\\":\\\"1c0773fa98f4465b8e535ba3c00dc039\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/db8251e870b14d60ace863a7598cce8b\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":false,\\\"size\\\":1,\\\"distance\\\":1,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:51 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:53 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\"}\",\"{\\\"uuid\\\":\\\"a70bb2be871644b6ad12ac8d6e9524d0\\\",\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"type\\\":\\\"Hypervisor\\\",\\\"volumeUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"treeUuid\\\":\\\"1c0773fa98f4465b8e535ba3c00dc039\\\",\\\"parentUuid\\\":\\\"791f523bc99a4f08bd70b5a59d8ed5c8\\\",\\\"primaryStorageUuid\\\":\\\"e121a11157bb4746ad3c8d56c3760a3e\\\",\\\"primaryStorageInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/791f523bc99a4f08bd70b5a59d8ed5c8\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"format\\\":\\\"qcow2\\\",\\\"latest\\\":false,\\\"size\\\":1,\\\"distance\\\":3,\\\"state\\\":\\\"Enabled\\\",\\\"status\\\":\\\"Ready\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"backupStorageRefs\\\":[],\\\"groupUuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\"}\"]},\"volumeSnapshotGroupVO\":[\"{\\\"snapshotCount\\\":4,\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"volumeSnapshotRefs\\\":[{\\\"volumeSnapshotUuid\\\":\\\"a35b7ae1616a4974b2f80654c5527fbb\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":1,\\\"volumeUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/db8251e870b14d60ace863a7598cce8b\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\"},{\\\"volumeSnapshotUuid\\\":\\\"69e85ac72fea4263a55cbcd21785006e\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":3,\\\"volumeUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/ae9f28cb5055498e8661793d204208ba\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"},{\\\"volumeSnapshotUuid\\\":\\\"61e2ada0170142bb8b303910a27690aa\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":0,\\\"volumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"volumeName\\\":\\\"ROOT-for-vmName\\\",\\\"volumeType\\\":\\\"Root\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-ROOT-for-vmName\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:47 AM\\\"},{\\\"volumeSnapshotUuid\\\":\\\"bcc3ed8070984cd691c62f421aeaa44d\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":2,\\\"volumeUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/b7290c15276b4700af2c1b108b2b62e1\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}],\\\"uuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\",\\\"resourceName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"resourceType\\\":\\\"VolumeSnapshotGroupVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupVO\\\"}\",\"{\\\"snapshotCount\\\":4,\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"volumeSnapshotRefs\\\":[{\\\"volumeSnapshotUuid\\\":\\\"b5d771aa83584c9c88d9b84147dfc9ad\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":2,\\\"volumeUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/bcc3ed8070984cd691c62f421aeaa44d\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"},{\\\"volumeSnapshotUuid\\\":\\\"79caace79a1048d58ea7c0b38815bbd0\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":0,\\\"volumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"volumeName\\\":\\\"ROOT-for-vmName\\\",\\\"volumeType\\\":\\\"Root\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/61e2ada0170142bb8b303910a27690aa\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-ROOT-for-vmName\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:47 AM\\\"},{\\\"volumeSnapshotUuid\\\":\\\"aefbe47465c047d1b118321c34425869\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":3,\\\"volumeUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/69e85ac72fea4263a55cbcd21785006e\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"},{\\\"volumeSnapshotUuid\\\":\\\"791f523bc99a4f08bd70b5a59d8ed5c8\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":1,\\\"volumeUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/a35b7ae1616a4974b2f80654c5527fbb\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\"}],\\\"uuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\",\\\"resourceName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"resourceType\\\":\\\"VolumeSnapshotGroupVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupVO\\\"}\",\"{\\\"snapshotCount\\\":4,\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"volumeSnapshotRefs\\\":[{\\\"volumeSnapshotUuid\\\":\\\"1aaa92b2d1eb4c36bb8951b8e1521b34\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":3,\\\"volumeUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/aefbe47465c047d1b118321c34425869\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"},{\\\"volumeSnapshotUuid\\\":\\\"a70bb2be871644b6ad12ac8d6e9524d0\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":1,\\\"volumeUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/791f523bc99a4f08bd70b5a59d8ed5c8\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\"},{\\\"volumeSnapshotUuid\\\":\\\"04ef6d31675f4ba5816b104920dc3e2c\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":0,\\\"volumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"volumeName\\\":\\\"ROOT-for-vmName\\\",\\\"volumeType\\\":\\\"Root\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/79caace79a1048d58ea7c0b38815bbd0\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-ROOT-for-vmName\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:47 AM\\\"},{\\\"volumeSnapshotUuid\\\":\\\"a31c3de68ce246538e982e0e5c7d2d73\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":2,\\\"volumeUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/b5d771aa83584c9c88d9b84147dfc9ad\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}],\\\"uuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\",\\\"resourceName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"resourceType\\\":\\\"VolumeSnapshotGroupVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupVO\\\"}\",\"{\\\"snapshotCount\\\":4,\\\"name\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"vmInstanceUuid\\\":\\\"77bc3074f5f4438c836ce6c56bc5a4aa\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"volumeSnapshotRefs\\\":[{\\\"volumeSnapshotUuid\\\":\\\"43436624dc714282913e0a141246629e\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":1,\\\"volumeUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/a70bb2be871644b6ad12ac8d6e9524d0\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\"},{\\\"volumeSnapshotUuid\\\":\\\"bc5ab54cb3d04635923a2a9d0b5fc73f\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":0,\\\"volumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"volumeName\\\":\\\"ROOT-for-vmName\\\",\\\"volumeType\\\":\\\"Root\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/04ef6d31675f4ba5816b104920dc3e2c\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-ROOT-for-vmName\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:47 AM\\\"},{\\\"volumeSnapshotUuid\\\":\\\"b711f22ad5c045b6ad1d770d4f301d05\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":3,\\\"volumeUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/1aaa92b2d1eb4c36bb8951b8e1521b34\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"},{\\\"volumeSnapshotUuid\\\":\\\"7832daf63d9b41d68bd1460c20ed0e0a\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":2,\\\"volumeUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/a31c3de68ce246538e982e0e5c7d2d73\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}],\\\"uuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\",\\\"resourceName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"resourceType\\\":\\\"VolumeSnapshotGroupVO\\\",\\\"concreteResourceType\\\":\\\"org.zstack.header.storage.snapshot.group.VolumeSnapshotGroupVO\\\"}\"],\"volumeSnapshotGroupRefVO\":[\"{\\\"volumeSnapshotUuid\\\":\\\"04ef6d31675f4ba5816b104920dc3e2c\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":0,\\\"volumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"volumeName\\\":\\\"ROOT-for-vmName\\\",\\\"volumeType\\\":\\\"Root\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/79caace79a1048d58ea7c0b38815bbd0\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-ROOT-for-vmName\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:47 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"1aaa92b2d1eb4c36bb8951b8e1521b34\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":3,\\\"volumeUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/aefbe47465c047d1b118321c34425869\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"43436624dc714282913e0a141246629e\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":1,\\\"volumeUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/a70bb2be871644b6ad12ac8d6e9524d0\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"61e2ada0170142bb8b303910a27690aa\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":0,\\\"volumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"volumeName\\\":\\\"ROOT-for-vmName\\\",\\\"volumeType\\\":\\\"Root\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-ROOT-for-vmName\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:47 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"69e85ac72fea4263a55cbcd21785006e\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":3,\\\"volumeUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/ae9f28cb5055498e8661793d204208ba\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"7832daf63d9b41d68bd1460c20ed0e0a\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":2,\\\"volumeUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/a31c3de68ce246538e982e0e5c7d2d73\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"791f523bc99a4f08bd70b5a59d8ed5c8\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":1,\\\"volumeUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/a35b7ae1616a4974b2f80654c5527fbb\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"79caace79a1048d58ea7c0b38815bbd0\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":0,\\\"volumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"volumeName\\\":\\\"ROOT-for-vmName\\\",\\\"volumeType\\\":\\\"Root\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/61e2ada0170142bb8b303910a27690aa\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-ROOT-for-vmName\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:47 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"a31c3de68ce246538e982e0e5c7d2d73\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":2,\\\"volumeUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/b5d771aa83584c9c88d9b84147dfc9ad\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"a35b7ae1616a4974b2f80654c5527fbb\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":1,\\\"volumeUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/db8251e870b14d60ace863a7598cce8b\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"a70bb2be871644b6ad12ac8d6e9524d0\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"7648a93930db473785b0abc0e0716c1a\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":1,\\\"volumeUuid\\\":\\\"db8251e870b14d60ace863a7598cce8b\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/791f523bc99a4f08bd70b5a59d8ed5c8\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:56 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:49 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"aefbe47465c047d1b118321c34425869\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":3,\\\"volumeUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/69e85ac72fea4263a55cbcd21785006e\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"b5d771aa83584c9c88d9b84147dfc9ad\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"6db066c890d141008e8ff18bd5940d77\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":2,\\\"volumeUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/bcc3ed8070984cd691c62f421aeaa44d\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:54 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"b711f22ad5c045b6ad1d770d4f301d05\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":3,\\\"volumeUuid\\\":\\\"ae9f28cb5055498e8661793d204208ba\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/1aaa92b2d1eb4c36bb8951b8e1521b34\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"bc5ab54cb3d04635923a2a9d0b5fc73f\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"d2d486455d6c472cbb7391958edebea5\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":0,\\\"volumeUuid\\\":\\\"8d1e76eca52647f5a4544b9ff2d370de\\\",\\\"volumeName\\\":\\\"ROOT-for-vmName\\\",\\\"volumeType\\\":\\\"Root\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/04ef6d31675f4ba5816b104920dc3e2c\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-ROOT-for-vmName\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:57 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:47 AM\\\"}\",\"{\\\"volumeSnapshotUuid\\\":\\\"bcc3ed8070984cd691c62f421aeaa44d\\\",\\\"volumeSnapshotGroupUuid\\\":\\\"01171364e6704dbe83c36167de52d719\\\",\\\"snapshotDeleted\\\":false,\\\"deviceId\\\":2,\\\"volumeUuid\\\":\\\"b7290c15276b4700af2c1b108b2b62e1\\\",\\\"volumeName\\\":\\\"volumeName1null\\\",\\\"volumeType\\\":\\\"Data\\\",\\\"volumeSnapshotInstallPath\\\":\\\"sharedblock://e121a11157bb4746ad3c8d56c3760a3e/b7290c15276b4700af2c1b108b2b62e1\\\",\\\"volumeSnapshotName\\\":\\\"group-8d1e76eca52647f5a4544b9ff2d370de-volumeName1null\\\",\\\"createDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"lastOpDate\\\":\\\"Jan 28, 2026 4:15:52 AM\\\",\\\"volumeLastAttachDate\\\":\\\"Jan 28, 2026 4:15:50 AM\\\"}\"],\"volumeSnapshotReferenceVO\":{},\"volumeSnapshotReferenceTreeVO\":{},\"EncryptedResourceKeyRefVO\":{}}"
                return new LocalStorageKvmBackend.GetVmInstanceMetadataRsp()
            }

            simulator(LocalStorageKvmBackend.SCAN_VM_METADATA_PATH) {
                return new LocalStorageKvmBackend.ScanVmMetadataRsp()
            }

            simulator(LocalStorageKvmBackend.CLEANUP_VM_METADATA_PATH) {
                return new LocalStorageKvmBackend.CleanupVmMetadataRsp()
            }
        }
    }

    SpecID create(String uuid, String sessionId) {
        inventory = addLocalPrimaryStorage {
            delegate.resourceUuid = uuid
            delegate.name = name
            delegate.description = description
            delegate.url = url
            delegate.sessionId = sessionId
            delegate.zoneUuid = (parent as ZoneSpec).inventory.uuid
            delegate.userTags = userTags
            delegate.systemTags = systemTags
        } as PrimaryStorageInventory

        postCreate {
            inventory = queryPrimaryStorage {
                conditions=["uuid=${inventory.uuid}".toString()]
            }[0]
        }

        return id(name, inventory.uuid)
    }
}
