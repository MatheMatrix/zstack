package org.zstack.header.allocator;

import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.host.HostInventory;
import org.zstack.header.message.MessageReply;

import java.util.List;

/**
 * Created by Wenhao.Zhang on 24-01-19
 */
public class HostAllocatorResults {
    public HostAllocatorStrategy allocator;
    public HostSortorStrategy sorter;
    public HostAllocatorSpec spec;
    public List<HostInventory> results;
    public HostInventory firstCandidate;

    public HostAllocatorResults withAllocatorStrategyFactory(HostAllocatorStrategyFactory factory) {
        allocator = factory.getHostAllocatorStrategy();
        sorter = factory.getHostSortorStrategy();
        return this;
    }

    public HostAllocatorResults withSpec(HostAllocatorSpec spec) {
        this.spec = spec;
        return this;
    }

    public MessageReply createReply() {
        if (spec.isDryRun()) {
            AllocateHostDryRunReply reply = new AllocateHostDryRunReply();
            reply.setHosts(results);
            return reply;
        }

        AllocateHostReply reply = new AllocateHostReply();
        reply.setHost(firstCandidate);
        return reply;
    }

    public static MessageReply createReply(ErrorCode errorCode) {
        MessageReply reply = new MessageReply();
        reply.setError(errorCode);
        return reply;
    }
}
