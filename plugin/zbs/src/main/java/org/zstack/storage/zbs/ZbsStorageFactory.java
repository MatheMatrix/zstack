package org.zstack.storage.zbs;

import org.zstack.cbd.Config;
import org.zstack.cbd.MdsUri;
import org.zstack.core.db.Q;
import org.zstack.header.apimediator.ApiMessageInterceptionException;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.storage.addon.primary.*;
import org.zstack.utils.CollectionUtils;
import org.zstack.utils.function.Function;
import org.zstack.utils.gson.JSONObjectUtil;

import java.util.LinkedHashMap;
import java.util.List;

import static org.zstack.core.Platform.argerr;

/**
 * @author Xingwei Yu
 * @date 2024/3/21 11:56
 */
public class ZbsStorageFactory implements ExternalPrimaryStorageSvcBuilder {
    @Override
    public PrimaryStorageControllerSvc buildControllerSvc(ExternalPrimaryStorageVO vo) {
        return new ZbsStorageController(vo);
    }

    @Override
    public PrimaryStorageNodeSvc buildNodeSvc(ExternalPrimaryStorageVO vo) {
        return new ZbsStorageController(vo);
    }

    @Override
    public void discover(String url, String config, ReturnValueCompletion<LinkedHashMap> completion) {

    }

    @Override
    public String getIdentity() {
        return ZbsConstants.IDENTITY;
    }

    @Override
    public void validate(String config) {
        checkExistingPrimaryStorage(config);
    }

    private void checkExistingPrimaryStorage(String configuration) {
        Config config = JSONObjectUtil.toObject(configuration, Config.class);
        List<String> hostnames = CollectionUtils.transformToList(config.getMdsUrls(), new Function<String, String>() {
            @Override
            public String call(String url) {
                MdsUri uri = new MdsUri(url);
                return uri.getHostname();
            }
        });

        List<ExternalPrimaryStorageVO> externalPrimaryStorageVOS = Q.New(ExternalPrimaryStorageVO.class)
                .eq(ExternalPrimaryStorageVO_.identity, ZbsConstants.IDENTITY)
                .list();

        if (externalPrimaryStorageVOS == null) {
            return;
        }

        boolean existingHostnameFound = externalPrimaryStorageVOS.stream()
                .map(ExternalPrimaryStorageVO::getAddonInfo)
                .anyMatch(addonInfo -> hostnames.stream().anyMatch(addonInfo::contains));

        if (existingHostnameFound) {
            throw new ApiMessageInterceptionException(argerr("Cannot add ZBS primary storage. There has been some ZBS primary storage using MDS with hostnames: %s", hostnames));
        }
    }
}
