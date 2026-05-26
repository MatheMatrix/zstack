package org.zstack.test.storage.primary.local;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.gc.GCCompletion;
import org.zstack.header.host.HostVO;
import org.zstack.header.storage.primary.PrimaryStorageVO;
import org.zstack.storage.primary.local.LocalStorageReturnHostCapacityGC;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

public class LocalStorageReturnHostCapacityGCTest {

    private DatabaseFacade mockDbf;
    private CloudBus mockBus;

    @Before
    public void setUp() {
        mockDbf = Mockito.mock(DatabaseFacade.class);
        mockBus = Mockito.mock(CloudBus.class);
    }

    private LocalStorageReturnHostCapacityGC createGC(
            String psUuid, String hostUuid, long size, boolean noOverProvisioning) throws Exception {
        LocalStorageReturnHostCapacityGC gc = new LocalStorageReturnHostCapacityGC();
        injectField(gc, "dbf", mockDbf);
        injectField(gc, "bus", mockBus);
        gc.primaryStorageUuid = psUuid;
        gc.hostUuid = hostUuid;
        gc.size = size;
        gc.noOverProvisioning = noOverProvisioning;
        return gc;
    }

    private void injectField(Object target, String fieldName, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null && clazz != Object.class) {
            try {
                Field f = clazz.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName + " not found in hierarchy of " + target.getClass());
    }

    @Test
    public void cancelsWhenPrimaryStorageNotFound() throws Exception {
        LocalStorageReturnHostCapacityGC gc = createGC("ps-1", "host-1", 1024, false);

        Mockito.when(mockDbf.isExist("ps-1", PrimaryStorageVO.class)).thenReturn(false);

        AtomicReference<Boolean> cancelled = new AtomicReference<>(false);
        GCCompletion completion = Mockito.mock(GCCompletion.class);
        Mockito.doAnswer(invocation -> {
            cancelled.set(true);
            return null;
        }).when(completion).cancel();

        Method m = LocalStorageReturnHostCapacityGC.class.getDeclaredMethod("triggerNow", GCCompletion.class);
        m.setAccessible(true);
        m.invoke(gc, completion);

        Assert.assertTrue("should cancel when PS not found", cancelled.get());
    }

    @Test
    public void cancelsWhenHostNotFound() throws Exception {
        LocalStorageReturnHostCapacityGC gc = createGC("ps-1", "host-1", 1024, false);

        Mockito.when(mockDbf.isExist("ps-1", PrimaryStorageVO.class)).thenReturn(true);
        Mockito.when(mockDbf.isExist("host-1", HostVO.class)).thenReturn(false);

        AtomicReference<Boolean> cancelled = new AtomicReference<>(false);
        GCCompletion completion = Mockito.mock(GCCompletion.class);
        Mockito.doAnswer(invocation -> {
            cancelled.set(true);
            return null;
        }).when(completion).cancel();

        Method m = LocalStorageReturnHostCapacityGC.class.getDeclaredMethod("triggerNow", GCCompletion.class);
        m.setAccessible(true);
        m.invoke(gc, completion);

        Assert.assertTrue("should cancel when host not found", cancelled.get());
    }

    @Test
    public void hasFourGCFields() throws Exception {
        LocalStorageReturnHostCapacityGC gc = new LocalStorageReturnHostCapacityGC();
        int gcCount = 0;
        for (Field f : LocalStorageReturnHostCapacityGC.class.getDeclaredFields()) {
            if (f.isAnnotationPresent(org.zstack.core.gc.GC.class)) {
                gcCount++;
            }
        }
        Assert.assertEquals(4, gcCount);
    }
}
