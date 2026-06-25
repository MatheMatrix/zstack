package org.zstack.server.hardware;

import org.junit.Test;
import org.zstack.header.server.PhysicalServerHardwareInfoVO;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class UnifiedHardwareInfoMergeTest {

    private final PhysicalServerHardwareService svc = new PhysicalServerHardwareService();

    @Test
    public void nullSourceFieldDoesNotClobberTarget() {
        UnifiedHardwareInfo target = new UnifiedHardwareInfo();
        target.setSerialNumber("SN-FROM-FRU");
        target.setManufacturer("Dell");
        target.setCpuCores(96);

        UnifiedHardwareInfo source = new UnifiedHardwareInfo();
        // explicitly leave serialNumber/manufacturer null; only contribute cpuModel
        source.setCpuModel("Xeon Gold 6338");

        boolean changed = svc.mergeNonNull(target, source);

        assertTrue("expected merge to copy cpuModel", changed);
        assertEquals("SN-FROM-FRU", target.getSerialNumber());
        assertEquals("Dell", target.getManufacturer());
        assertEquals(Integer.valueOf(96), target.getCpuCores());
        assertEquals("Xeon Gold 6338", target.getCpuModel());
    }

    @Test
    public void firstNonNullWinsOverLaterSource() {
        UnifiedHardwareInfo target = new UnifiedHardwareInfo();
        UnifiedHardwareInfo first = new UnifiedHardwareInfo();
        first.setSerialNumber("SN-FROM-FRU");
        first.setManufacturer("Dell");
        svc.mergeNonNull(target, first);

        UnifiedHardwareInfo second = new UnifiedHardwareInfo();
        second.setSerialNumber("SN-FROM-KVM-AGENT");
        second.setManufacturer("Lenovo");
        second.setCpuArchitecture("x86_64");

        boolean changed = svc.mergeNonNull(target, second);

        assertTrue("expected merge to copy cpuArchitecture", changed);
        assertEquals("first source's serial wins", "SN-FROM-FRU", target.getSerialNumber());
        assertEquals("first source's manufacturer wins", "Dell", target.getManufacturer());
        assertEquals("x86_64", target.getCpuArchitecture());
    }

    @Test
    public void mergingFullyEmptySourceReturnsFalse() {
        UnifiedHardwareInfo target = new UnifiedHardwareInfo();
        target.setSerialNumber("SN-FROM-FRU");

        boolean changed = svc.mergeNonNull(target, new UnifiedHardwareInfo());

        assertFalse(changed);
        assertEquals("SN-FROM-FRU", target.getSerialNumber());
    }

    @Test
    public void mergingNullSourceIsSafe() {
        UnifiedHardwareInfo target = new UnifiedHardwareInfo();
        target.setSerialNumber("SN");

        boolean changed = svc.mergeNonNull(target, null);

        assertFalse(changed);
        assertEquals("SN", target.getSerialNumber());
    }

    @Test
    public void numericZeroIsTreatedAsValue() {
        UnifiedHardwareInfo target = new UnifiedHardwareInfo();
        UnifiedHardwareInfo source = new UnifiedHardwareInfo();
        source.setTotalMemoryBytes(0L);

        boolean changed = svc.mergeNonNull(target, source);

        assertTrue(changed);
        assertEquals(Long.valueOf(0L), target.getTotalMemoryBytes());
    }

    @Test
    public void allFieldsFlowThroughOnEmptyTarget() {
        UnifiedHardwareInfo target = new UnifiedHardwareInfo();
        UnifiedHardwareInfo source = fullyPopulated();

        boolean changed = svc.mergeNonNull(target, source);

        assertTrue(changed);
        assertEquals("Dell", target.getManufacturer());
        assertEquals("R750", target.getModel());
        assertEquals("SN-1", target.getSerialNumber());
        assertEquals("v2.10", target.getBiosVersion());
        assertEquals("Xeon Gold 6338", target.getCpuModel());
        assertEquals(Integer.valueOf(2), target.getCpuSockets());
        assertEquals(Integer.valueOf(64), target.getCpuCores());
        assertEquals("x86_64", target.getCpuArchitecture());
        assertEquals(Long.valueOf(549755813888L), target.getTotalMemoryBytes());
    }

    @Test
    public void emptyTargetWithEmptySourceLeavesEverythingNull() {
        UnifiedHardwareInfo target = new UnifiedHardwareInfo();
        boolean changed = svc.mergeNonNull(target, new UnifiedHardwareInfo());
        assertFalse(changed);
        assertNull(target.getManufacturer());
        assertNull(target.getCpuModel());
        assertNull(target.getTotalMemoryBytes());
    }

    @Test
    public void applyNonNullKeepsVoFieldCoverageAlignedWithMergeNonNull() throws Exception {
        PhysicalServerHardwareInfoVO row = new PhysicalServerHardwareInfoVO();
        UnifiedHardwareInfo source = fullyPopulated();

        applyNonNull(row, source);

        assertEquals(source.getManufacturer(), row.getManufacturer());
        assertEquals(source.getModel(), row.getModel());
        assertEquals(source.getSerialNumber(), row.getSerialNumber());
        assertEquals(source.getBiosVersion(), row.getBiosVersion());
        assertEquals(source.getCpuModel(), row.getCpuModel());
        assertEquals(source.getCpuSockets(), row.getCpuSockets());
        assertEquals(source.getCpuCores(), row.getCpuCores());
        assertEquals(source.getCpuArchitecture(), row.getCpuArchitecture());
        assertEquals(source.getTotalMemoryBytes(), row.getTotalMemoryBytes());
    }

    @Test
    public void applyNonNullDoesNotClobberVoFieldsWithNullSourceValues() throws Exception {
        PhysicalServerHardwareInfoVO row = new PhysicalServerHardwareInfoVO();
        row.setSerialNumber("SN-FROM-DB");
        row.setCpuSockets(2);

        UnifiedHardwareInfo source = new UnifiedHardwareInfo();
        source.setCpuArchitecture("x86_64");

        applyNonNull(row, source);

        assertEquals("SN-FROM-DB", row.getSerialNumber());
        assertEquals(Integer.valueOf(2), row.getCpuSockets());
        assertEquals("x86_64", row.getCpuArchitecture());
    }

    private UnifiedHardwareInfo fullyPopulated() {
        UnifiedHardwareInfo s = new UnifiedHardwareInfo();
        s.setManufacturer("Dell");
        s.setModel("R750");
        s.setSerialNumber("SN-1");
        s.setBiosVersion("v2.10");
        s.setCpuModel("Xeon Gold 6338");
        s.setCpuSockets(2);
        s.setCpuCores(64);
        s.setCpuArchitecture("x86_64");
        s.setTotalMemoryBytes(549755813888L);
        return s;
    }

    private void applyNonNull(PhysicalServerHardwareInfoVO row, UnifiedHardwareInfo info) throws Exception {
        Method method = PhysicalServerHardwareService.class.getDeclaredMethod(
                "applyNonNull", PhysicalServerHardwareInfoVO.class, UnifiedHardwareInfo.class);
        method.setAccessible(true);
        method.invoke(svc, row, info);
    }
}
