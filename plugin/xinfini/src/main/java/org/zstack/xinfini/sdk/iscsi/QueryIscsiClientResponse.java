package org.zstack.xinfini.sdk.iscsi;

import org.zstack.xinfini.sdk.XInfiniQueryResponse;

import java.util.List;

/**
 * @ Author : yh.w
 * @ Date   : Created in 11:51 2024/5/28
 */
public class QueryIscsiClientResponse extends XInfiniQueryResponse {
    List<IscsiClientModule> items;

    public List<IscsiClientModule> getItems() {
        return items;
    }

    public void setItems(List<IscsiClientModule> items) {
        this.items = items;
    }
}
