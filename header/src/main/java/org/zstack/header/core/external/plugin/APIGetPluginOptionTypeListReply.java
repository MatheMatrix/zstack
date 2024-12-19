package org.zstack.header.core.external.plugin;

import org.zstack.abstraction.OptionType;
import org.zstack.header.message.APIReply;
import org.zstack.header.rest.RestResponse;

import java.util.Collection;

@RestResponse(fieldsTo = {"optionTypeList"})
public class APIGetPluginOptionTypeListReply extends APIReply {
    private Collection<OptionType> optionTypeList;

    public Collection<OptionType> getOptionTypeList() {
        return optionTypeList;
    }

    public void setOptionTypeList(Collection<OptionType> optionTypeList) {
        this.optionTypeList = optionTypeList;
    }

    public static APIGetPluginOptionTypeListReply __example__() {
        APIGetPluginOptionTypeListReply reply = new APIGetPluginOptionTypeListReply();
        return reply;
    }
}