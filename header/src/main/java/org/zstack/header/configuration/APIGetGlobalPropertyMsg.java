package org.zstack.header.configuration;

import org.zstack.header.message.APISyncCallMessage;
import org.zstack.header.vm.MetadataImpact;

/**
 * Created by frank on 7/23/2015.
 */
@MetadataImpact(MetadataImpact.Impact.NONE)
public class APIGetGlobalPropertyMsg extends APISyncCallMessage {

    public static APIGetGlobalPropertyMsg __example__() {
        APIGetGlobalPropertyMsg msg = new APIGetGlobalPropertyMsg();


        return msg;
    }

}