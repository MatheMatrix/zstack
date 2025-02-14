package org.zstack.header.host;

/**
 * Created by boce.wang on 01/24/2025.
 */
public interface HostHugepageExtensionPoint {
    boolean checkHugepageSupport(HostInventory host);
}
