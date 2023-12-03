package org.zstack.storage.ceph.primary;

import org.zstack.core.db.Q;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.volume.VolumeVO;
import org.zstack.header.volume.VolumeVO_;
import org.zstack.utils.DebugUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;

import static org.zstack.core.Platform.argerr;

/**
 * @ Author : yh.w
 * @ Date   : Created in 16:44 2022/8/30
 */
public class CephRequiredUrlParser {
    private static final HashMap<String, AbstractUriParser> uriParsers = new HashMap<>();

    static {
        parseRequiredInstallUri();
    }

    public static InstallPath getInstallPathFromUri(String requiredUrl) {
        String protocol;
        try {
            protocol = new URI(requiredUrl).getScheme();
        } catch (URISyntaxException e) {
            throw new OperationFailureException(
                    argerr("invalid uri, correct example is ceph://$POOLNAME/$VOLUMEUUID or volume://$VOLUMEUUID"));
        }

        return uriParsers.get(protocol).parseUri(requiredUrl);
    }

    public static class InstallPath {
        public String fullPath;
        public String poolName;

        public InstallPath disassemble() {
            DebugUtils.Assert(fullPath != null, "fullPath cannot be null");
            String path = fullPath.replaceFirst("ceph://", "");
            poolName = path.substring(0, path.lastIndexOf("/"));
            return this;
        }

        public String makeFullPath() {
            DebugUtils.Assert(poolName != null, "poolName cannot be null");
            fullPath = String.format("ceph://%s/", poolName);
            return fullPath;
        }
    }

    abstract static class AbstractUriParser {
        abstract InstallPath parseUri(String uri);
    }

    private static void parseRequiredInstallUri() {
        String protocolVolume = "volume";
        String protocolCeph = "ceph";

        AbstractUriParser volumeParser = new AbstractUriParser() {
            @Override
            InstallPath parseUri(String uri) {
                String volumeUuid = uri.replaceFirst("volume://", "");
                String path = Q.New(VolumeVO.class).select(VolumeVO_.installPath).eq(VolumeVO_.uuid, volumeUuid).findValue();
                InstallPath p = new InstallPath();
                p.fullPath = path;
                p.disassemble();
                return p;
            }
        };

        AbstractUriParser cephParser = new AbstractUriParser() {
            @Override
            InstallPath parseUri(String uri) {
                InstallPath p = new InstallPath();
                p.fullPath = uri;
                p.disassemble();
                return p;
            }
        };

        uriParsers.put(protocolVolume, volumeParser);
        uriParsers.put(protocolCeph, cephParser);
    }
}
