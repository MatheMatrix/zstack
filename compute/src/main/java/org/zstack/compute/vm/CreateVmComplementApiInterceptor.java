package org.zstack.compute.vm;

import org.zstack.core.db.Q;
import org.zstack.header.apimediator.ApiMessageInterceptionException;
import org.zstack.header.apimediator.GlobalApiMessageInterceptor;
import org.zstack.header.configuration.DiskOfferingVO;
import org.zstack.header.configuration.DiskOfferingVO_;
import org.zstack.header.image.ImageVO;
import org.zstack.header.image.ImageVO_;
import org.zstack.header.message.APIMessage;
import org.zstack.header.vm.APICreateVmInstanceMsg;
import org.zstack.header.vm.DiskAO;
import org.zstack.header.vo.ResourceVO;
import org.zstack.header.vo.ResourceVO_;

import javax.persistence.Tuple;
import java.util.ArrayList;
import java.util.List;

import static org.zstack.core.Platform.argerr;
import static org.zstack.utils.CollectionDSL.list;
import static org.zstack.utils.CollectionUtils.findOneOrNull;
import static org.zstack.utils.CollectionUtils.isEmpty;

public class CreateVmComplementApiInterceptor implements GlobalApiMessageInterceptor {

    private static final List<String> VALID_DISK_SOURCE_TYPES = list(
            "LunVO",
            "IscsiLunVO",
            "FiberChannelLunVO",
            "VolumeVO"
    );

    @Override
    public APIMessage intercept(APIMessage msg) throws ApiMessageInterceptionException {
        if (msg instanceof APICreateVmInstanceMsg) {
            validate((APICreateVmInstanceMsg) msg);
        }
        return msg;
    }

    private void validate(APICreateVmInstanceMsg msg) {
        if (isEmpty(msg.getDiskAOs())) {
            buildDiskAOList(msg);
        }

        completeDiskAOs(msg);
    }

    private void buildDiskAOList(APICreateVmInstanceMsg msg) {
        int diskNum = isEmpty(msg.getDataDiskOfferingUuids()) ? 0 : msg.getDataDiskOfferingUuids().size();
        diskNum += isEmpty(msg.getDataDiskSizes()) ? 0 : msg.getDataDiskSizes().size();
        // include root volume
        diskNum ++;

        final ArrayList<DiskAO> disks = new ArrayList<>();
        msg.setDiskAOs(disks);
        for (int i = 0; i < diskNum; i++) {
            disks.add(new DiskAO());
            disks.get(i).setSystemTags(new ArrayList<>());
        }

        disks.get(0).setBoot(true);

        // replace rootDiskSize by diskAOs[0].size
        if (msg.getRootDiskSize() != null) {
            disks.get(0).setSize(msg.getRootDiskSize());
            msg.setRootDiskSize(null);
        }

        // replace rootDiskOfferingUuid by diskAOs[0].size
        if (msg.getRootDiskOfferingUuid() != null) {
            // skip account checker: diskOffering will be deprecated soon
            List<Long> diskSizeList = Q.New(DiskOfferingVO.class)
                    .eq(DiskOfferingVO_.uuid, msg.getRootDiskOfferingUuid())
                    .select(DiskOfferingVO_.diskSize)
                    .listValues();
            if (diskSizeList.isEmpty()) {
                throw new ApiMessageInterceptionException(
                        argerr("rootDiskOfferingUuid[%s] is invalid, it must be a valid disk offering uuid",
                        msg.getRootDiskOfferingUuid()));
            }
            disks.get(0).setSize(diskSizeList.get(0));
            msg.setRootDiskOfferingUuid(null);
        }

        // replace dataDiskOfferingUuids by diskAOs[1..].size
        if (!isEmpty(msg.getDataDiskOfferingUuids())) {
            // skip account checker: diskOffering will be deprecated soon
            List<Tuple> diskSizeTuples = Q.New(DiskOfferingVO.class)
                    .in(DiskOfferingVO_.uuid, msg.getDataDiskOfferingUuids())
                    .select(DiskOfferingVO_.uuid, DiskOfferingVO_.diskSize)
                    .listTuple();

            int index = 1;
            for (String offeringUuid : msg.getDataDiskOfferingUuids()) {
                Tuple tuple = findOneOrNull(diskSizeTuples, t -> t.get(0).equals(offeringUuid));
                if (tuple == null) {
                    throw new ApiMessageInterceptionException(
                            argerr("dataDiskOfferingUuid[%s] is invalid, it must be a valid disk offering uuid",
                            offeringUuid));
                }

                disks.get(index).setSize((long) tuple.get(1));
                index++;
            }

            msg.setDataDiskOfferingUuids(null);
        }

        // replace dataDiskSizes by diskAOs[1..].size
        if (!isEmpty(msg.getDataDiskSizes())) {
            int index = disks.size() - msg.getDataDiskSizes().size();
            for (Long size : msg.getDataDiskSizes()) {
                disks.get(index).setSize(size);
                index++;
            }

            msg.setDataDiskSizes(null);
        }

        // move rootVolumeSystemTags to diskAOs[0].systemTags
        if (!isEmpty(msg.getRootVolumeSystemTags())) {
            disks.get(0).getSystemTags().addAll(msg.getRootVolumeSystemTags());
            msg.setRootVolumeSystemTags(null);
        }

        // move dataVolumeSystemTags to diskAOs[1..].systemTags
        if (!isEmpty(msg.getDataVolumeSystemTags())) {
            for (int i = 1; i < diskNum; i++) {
                disks.get(i).getSystemTags().addAll(msg.getDataVolumeSystemTags());
            }
            msg.setDataVolumeSystemTags(null);
        }

        // move dataVolumeSystemTagsOnIndex to diskAOs[1..].systemTags
        if (msg.getDataVolumeSystemTagsOnIndex() != null && msg.getDataVolumeSystemTagsOnIndex().isEmpty()) {
            msg.getDataVolumeSystemTagsOnIndex().forEach((dataVolumeIndex, tags) -> {
                if (isEmpty(tags)) {
                    return;
                }

                int diskIndex = Integer.parseInt(dataVolumeIndex) + 1;
                if (diskIndex >= disks.size() || diskIndex < 1) {
                    throw new ApiMessageInterceptionException(
                            argerr("dataVolumeSystemTagsOnIndex[%s] is invalid, it must be between 1 and %s",
                            dataVolumeIndex, disks.size() - 1));
                }

                disks.get(diskIndex).getSystemTags().addAll(tags);
            });
            msg.setDataVolumeSystemTagsOnIndex(null);
        }

        // mark diskAOs[0].primaryStorageUuid by primaryStorageUuidForRootVolume
        if (msg.getPrimaryStorageUuidForRootVolume() != null) {
            disks.get(0).setPrimaryStorageUuid(msg.getPrimaryStorageUuidForRootVolume());
        }

        // mark diskAOs[0].platform by platform
        if (msg.getPlatform() != null) {
            disks.get(0).setPlatform(msg.getPlatform());
        }

        // mark diskAOs[0].guestOsType by guestOsType
        if (msg.getGuestOsType() != null) {
            disks.get(0).setGuestOsType(msg.getGuestOsType());
        }

        // mark diskAOs[0].architecture by architecture
        if (msg.getArchitecture() != null) {
            disks.get(0).setArchitecture(msg.getArchitecture());
        }

        // mark diskAOs[0].platform / guestOsType / architecture by imageUuid
        if (msg.getImageUuid() != null) {
            DiskAO bootDisk = disks.get(0);

            ImageVO image = Q.New(ImageVO.class)
                    .eq(ImageVO_.uuid, msg.getImageUuid())
                    .find();
            if (image == null) {
                throw new ApiMessageInterceptionException(
                        argerr("imageUuid[%s] is invalid, it must be a valid image uuid", msg.getImageUuid()));
            }

            bootDisk.withImage(msg.getImageUuid());
            if (image.getPlatform() != null && bootDisk.getPlatform() == null) {
                bootDisk.setPlatform(image.getPlatform().toString());
            }
            if (image.getGuestOsType() != null && bootDisk.getGuestOsType() == null) {
                bootDisk.setGuestOsType(image.getGuestOsType());
            }
            if (image.getArchitecture() != null && bootDisk.getArchitecture() == null) {
                bootDisk.setArchitecture(image.getArchitecture());
            }
        }
    }

    private void completeDiskAOs(APICreateVmInstanceMsg msg) {
        for (DiskAO disk : msg.getDiskAOs()) {
            if (disk.getSourceType() == null && disk.getSourceUuid() != null) {
                String resourceType = Q.New(ResourceVO.class)
                        .eq(ResourceVO_.uuid, disk.getSourceUuid())
                        .select(ResourceVO_.resourceType)
                        .findValue();
                if (resourceType == null) {
                    throw new ApiMessageInterceptionException(
                            argerr("sourceUuid[%s] is invalid, it must be a valid resource uuid", disk.getSourceUuid()));
                }

                if (!VALID_DISK_SOURCE_TYPES.contains(resourceType)) {
                    throw new ApiMessageInterceptionException(
                            argerr("sourceUuid[%s] is invalid, it must be a valid %s uuid", disk.getSourceUuid(),
                                    String.join(" or ", VALID_DISK_SOURCE_TYPES)));
                }
                disk.setSourceType(resourceType);
            }
        }

        final DiskAO bootDisk = msg.findBootDisk();
        if (bootDisk == null) {
            throw new ApiMessageInterceptionException(argerr("missing root disk"));
        }

        String platform = msg.getPlatform() == null ? bootDisk.getPlatform() : msg.getPlatform();
        msg.setPlatform(platform);
        bootDisk.setPlatform(platform);

        String guestOsType = msg.getGuestOsType() == null ? bootDisk.getGuestOsType() : msg.getGuestOsType();
        msg.setGuestOsType(guestOsType);
        bootDisk.setGuestOsType(guestOsType);

        String architecture = msg.getArchitecture() == null ? bootDisk.getArchitecture() : msg.getArchitecture();
        msg.setArchitecture(architecture);
        bootDisk.setArchitecture(architecture);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List<Class> getMessageClassToIntercept() {
        return list(APICreateVmInstanceMsg.class);
    }

    @Override
    public InterceptorPosition getPosition() {
        return InterceptorPosition.SYSTEM;
    }

    @Override
    public int getPriority() {
        // before ApiParamValidator
        return -2;
    }
}
