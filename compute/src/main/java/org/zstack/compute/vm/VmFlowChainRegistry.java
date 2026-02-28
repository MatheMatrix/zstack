package org.zstack.compute.vm;

import org.zstack.core.workflow.FlowChainBuilder;
import org.zstack.header.core.workflow.FlowChain;
import org.zstack.header.exception.CloudConfigureFailException;
import org.zstack.header.vm.VmInstanceInventory;

import java.util.List;

/**
 * Registry for all VM lifecycle FlowChainBuilders.
 * Extracted from VmInstanceManagerImpl to reduce God Class complexity.
 */
public class VmFlowChainRegistry {
    private List<String> createVmWorkFlowElements;
    private List<String> stopVmWorkFlowElements;
    private List<String> rebootVmWorkFlowElements;
    private List<String> startVmWorkFlowElements;
    private List<String> destroyVmWorkFlowElements;
    private List<String> migrateVmWorkFlowElements;
    private List<String> attachIsoWorkFlowElements;
    private List<String> detachIsoWorkFlowElements;
    private List<String> attachVolumeWorkFlowElements;
    private List<String> expungeVmWorkFlowElements;
    private List<String> pauseVmWorkFlowElements;
    private List<String> resumeVmWorkFlowElements;

    private FlowChainBuilder createVmFlowBuilder;
    private FlowChainBuilder stopVmFlowBuilder;
    private FlowChainBuilder rebootVmFlowBuilder;
    private FlowChainBuilder startVmFlowBuilder;
    private FlowChainBuilder destroyVmFlowBuilder;
    private FlowChainBuilder migrateVmFlowBuilder;
    private FlowChainBuilder attachVolumeFlowBuilder;
    private FlowChainBuilder attachIsoFlowBuilder;
    private FlowChainBuilder detachIsoFlowBuilder;
    private FlowChainBuilder expungeVmFlowBuilder;
    private FlowChainBuilder pauseVmFlowBuilder;
    private FlowChainBuilder resumeVmFlowBuilder;

    public void buildFlowChains() {
        try {
            createVmFlowBuilder = FlowChainBuilder.newBuilder().setFlowClassNames(createVmWorkFlowElements).construct();
            stopVmFlowBuilder = FlowChainBuilder.newBuilder().setFlowClassNames(stopVmWorkFlowElements).construct();
            rebootVmFlowBuilder = FlowChainBuilder.newBuilder().setFlowClassNames(rebootVmWorkFlowElements).construct();
            startVmFlowBuilder = FlowChainBuilder.newBuilder().setFlowClassNames(startVmWorkFlowElements).construct();
            destroyVmFlowBuilder = FlowChainBuilder.newBuilder().setFlowClassNames(destroyVmWorkFlowElements).construct();
            migrateVmFlowBuilder = FlowChainBuilder.newBuilder().setFlowClassNames(migrateVmWorkFlowElements).construct();
            attachVolumeFlowBuilder = FlowChainBuilder.newBuilder().setFlowClassNames(attachVolumeWorkFlowElements).construct();
            attachIsoFlowBuilder = FlowChainBuilder.newBuilder().setFlowClassNames(attachIsoWorkFlowElements).construct();
            detachIsoFlowBuilder = FlowChainBuilder.newBuilder().setFlowClassNames(detachIsoWorkFlowElements).construct();
            expungeVmFlowBuilder = FlowChainBuilder.newBuilder().setFlowClassNames(expungeVmWorkFlowElements).construct();
            pauseVmFlowBuilder = FlowChainBuilder.newBuilder().setFlowClassNames(pauseVmWorkFlowElements).construct();
            resumeVmFlowBuilder = FlowChainBuilder.newBuilder().setFlowClassNames(resumeVmWorkFlowElements).construct();
        } catch (Exception e) {
            throw new CloudConfigureFailException(VmFlowChainRegistry.class, e.getMessage(), e);
        }
    }

    // --- FlowChain getters ---

    public FlowChain getCreateVmWorkFlowChain(VmInstanceInventory inv) {
        return createVmFlowBuilder.build();
    }

    public FlowChain getStopVmWorkFlowChain(VmInstanceInventory inv) {
        return stopVmFlowBuilder.build();
    }

    public FlowChain getRebootVmWorkFlowChain(VmInstanceInventory inv) {
        return rebootVmFlowBuilder.build();
    }

    public FlowChain getStartVmWorkFlowChain(VmInstanceInventory inv) {
        return startVmFlowBuilder.build();
    }

    public FlowChain getDestroyVmWorkFlowChain(VmInstanceInventory inv) {
        return destroyVmFlowBuilder.build();
    }

    public FlowChain getMigrateVmWorkFlowChain(VmInstanceInventory inv) {
        return migrateVmFlowBuilder.build();
    }

    public FlowChain getAttachUninstantiatedVolumeWorkFlowChain(VmInstanceInventory inv) {
        return attachVolumeFlowBuilder.build();
    }

    public FlowChain getAttachIsoWorkFlowChain(VmInstanceInventory inv) {
        return attachIsoFlowBuilder.build();
    }

    public FlowChain getDetachIsoWorkFlowChain(VmInstanceInventory inv) {
        return detachIsoFlowBuilder.build();
    }

    public FlowChain getExpungeVmWorkFlowChain(VmInstanceInventory inv) {
        return expungeVmFlowBuilder.build();
    }

    public FlowChain getPauseWorkFlowChain(VmInstanceInventory inv) {
        return pauseVmFlowBuilder.build();
    }

    public FlowChain getResumeVmWorkFlowChain(VmInstanceInventory inv) {
        return resumeVmFlowBuilder.build();
    }

    // --- Setters for Spring XML property injection ---

    public void setCreateVmWorkFlowElements(List<String> createVmWorkFlowElements) {
        this.createVmWorkFlowElements = createVmWorkFlowElements;
    }

    public void setStopVmWorkFlowElements(List<String> stopVmWorkFlowElements) {
        this.stopVmWorkFlowElements = stopVmWorkFlowElements;
    }

    public void setRebootVmWorkFlowElements(List<String> rebootVmWorkFlowElements) {
        this.rebootVmWorkFlowElements = rebootVmWorkFlowElements;
    }

    public void setStartVmWorkFlowElements(List<String> startVmWorkFlowElements) {
        this.startVmWorkFlowElements = startVmWorkFlowElements;
    }

    public void setDestroyVmWorkFlowElements(List<String> destroyVmWorkFlowElements) {
        this.destroyVmWorkFlowElements = destroyVmWorkFlowElements;
    }

    public void setMigrateVmWorkFlowElements(List<String> migrateVmWorkFlowElements) {
        this.migrateVmWorkFlowElements = migrateVmWorkFlowElements;
    }

    public void setAttachVolumeWorkFlowElements(List<String> attachVolumeWorkFlowElements) {
        this.attachVolumeWorkFlowElements = attachVolumeWorkFlowElements;
    }

    public void setAttachIsoWorkFlowElements(List<String> attachIsoWorkFlowElements) {
        this.attachIsoWorkFlowElements = attachIsoWorkFlowElements;
    }

    public void setDetachIsoWorkFlowElements(List<String> detachIsoWorkFlowElements) {
        this.detachIsoWorkFlowElements = detachIsoWorkFlowElements;
    }

    public void setExpungeVmWorkFlowElements(List<String> expungeVmWorkFlowElements) {
        this.expungeVmWorkFlowElements = expungeVmWorkFlowElements;
    }

    public void setPauseVmWorkFlowElements(List<String> pauseVmWorkFlowElements) {
        this.pauseVmWorkFlowElements = pauseVmWorkFlowElements;
    }

    public void setResumeVmWorkFlowElements(List<String> resumeVmWorkFlowElements) {
        this.resumeVmWorkFlowElements = resumeVmWorkFlowElements;
    }
}
