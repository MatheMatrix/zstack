package org.zstack.header.host;

import java.util.List;

public class BlockDevicesList {
    static class BlockDevice  {
        private String name;
        private String type;
        private long size;
        private long physec;
        private long logsec;
        private String mountpoint;
        private List<BlockDevice> children;
    }

    private List<BlockDevice> blockDevices; // 使用 List 来存储多个块设备
}
