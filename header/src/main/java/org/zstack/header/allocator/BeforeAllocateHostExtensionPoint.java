package org.zstack.header.allocator;

public interface BeforeAllocateHostExtensionPoint {
    /**
     * <p>This method is called before the host allocation process starts.
     * It allows for pre-processing of the allocation specification.
     *
     * <p>You can modify allocation specification,
     * specify the requirements of the assigned host,
     * set the information for host allocation.
     *
     * <p>But you should NOT raise exception in these methods.
     *
     * <p>If you want to set the allocate flow chain,
     * you should implement the interface of {@link HostAllocatorStrategy}.
     * </p>
     *
     * @param spec the host allocation specification
     * @see HostAllocatorStrategy
     * @see HostSortorStrategy
     */
    void beforeAllocateHost(HostAllocatorSpec spec);
}
