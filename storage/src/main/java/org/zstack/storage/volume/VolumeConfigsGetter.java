package org.zstack.storage.volume;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.header.volume.VolumeConfigs;
import org.zstack.header.volume.VolumeConfigsExtensionPoint;

import java.util.List;

/**
 * @ Author : yh.w
 * @ Date   : Created in 11:00 2025/2/5
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class VolumeConfigsGetter {

    @Autowired
    private PluginRegistry pluginRgty;

    public VolumeConfigs getConfigs(String volUuid) {
        VolumeConfigs cfs = new VolumeConfigs();
        List<VolumeConfigsExtensionPoint> exts = pluginRgty.getExtensionList(VolumeConfigsExtensionPoint.class);
        if (exts.isEmpty()) {
            return cfs;
        }

        for (VolumeConfigsExtensionPoint ext : exts) {
           ext.setVolumeConfigs(cfs, volUuid);
        }

        return cfs;
    }
}
