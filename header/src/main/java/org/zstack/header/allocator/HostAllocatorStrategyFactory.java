package org.zstack.header.allocator;

public interface HostAllocatorStrategyFactory {
    HostAllocatorStrategyType getHostAllocatorStrategyType();

    HostAllocatorStrategy getHostAllocatorStrategy();

    HostSortorStrategy getHostSortorStrategy();
}
