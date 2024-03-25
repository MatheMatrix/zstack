package org.zstack.header.vm;

import org.zstack.header.rest.SDK;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@SDK
public class CloneTemplateVmResults {
    private int numberOfClonedVm;
    private List<CloneTemplateVmInstanceInventory> inventories;

    public int getNumberOfClonedVm() {
        return numberOfClonedVm;
    }

    public void setNumberOfClonedVm(int numberOfClonedVm) {
        this.numberOfClonedVm = numberOfClonedVm;
    }

    public List<CloneTemplateVmInstanceInventory> getInventories() {
        return inventories;
    }

    public void setInventories(List<CloneTemplateVmInstanceInventory> inventories) {
        this.inventories = inventories;
    }

    public List<CloneTemplateVmInstanceInventory> getInventoriesWithoutError() {
        return inventories.stream().filter(it -> it.getError() == null).collect(Collectors.toList());
    }

    public void resetNumberOfCloneVm() {
        numberOfClonedVm = inventories == null ? 0 :
                (int) inventories.stream().filter(it -> it.getError() == null).count();
    }

    public synchronized void addVmInstanceInventory(final CloneTemplateVmInstanceInventory inv) {
        if (inventories == null) {
            inventories = new ArrayList<>();
        }

        inventories.add(inv);
    }
}
