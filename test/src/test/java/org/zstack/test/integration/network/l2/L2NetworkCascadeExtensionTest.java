package org.zstack.test.integration.network.l2;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.InOrder;
import org.zstack.core.cascade.CascadeAction;
import org.zstack.core.cascade.CascadeConstant;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.core.Completion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.network.l2.L2DeleteConfirmExtensionPoint;
import org.zstack.header.network.l2.L2NetworkInventory;
import org.zstack.header.network.l2.L2NetworkVO;
import org.zstack.network.l2.L2NetworkCascadeExtension;
import org.zstack.network.l2.L2NetworkExtensionPointEmitter;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class L2NetworkCascadeExtensionTest {
    @Test
    public void deletionCheckCancelsAllBegunReservationsWhenLaterCheckFails() throws Exception {
        L2NetworkCascadeExtension extension = new L2NetworkCascadeExtension();
        L2NetworkExtensionPointEmitter emitter = mock(L2NetworkExtensionPointEmitter.class);
        PluginRegistry pluginRegistry = mock(PluginRegistry.class);
        L2DeleteConfirmExtensionPoint confirmedDelete = mock(L2DeleteConfirmExtensionPoint.class);
        L2NetworkInventory first = inventory("l2-1");
        L2NetworkInventory second = inventory("l2-2");
        ErrorCode checkFailure = new ErrorCode("TEST", "check failed");

        setPrivateField(extension, "extpEmitter", emitter);
        setPrivateField(extension, "pluginRgty", pluginRegistry);
        when(pluginRegistry.getExtensionList(L2DeleteConfirmExtensionPoint.class))
                .thenReturn(Collections.singletonList(confirmedDelete));
        when(confirmedDelete.supports(any(L2NetworkInventory.class))).thenReturn(true);
        when(confirmedDelete.check(first)).thenReturn(null);
        when(confirmedDelete.check(second)).thenReturn(checkFailure);

        RecordingCompletion completion = new RecordingCompletion();
        extension.asyncCascade(deletionCheckAction(first, second), completion);

        Assert.assertSame(checkFailure, completion.error);
        Assert.assertFalse(completion.succeeded);
        InOrder order = inOrder(confirmedDelete);
        order.verify(confirmedDelete).begin(first);
        order.verify(confirmedDelete).check(first);
        order.verify(confirmedDelete).begin(second);
        order.verify(confirmedDelete).check(second);
        order.verify(confirmedDelete).cancel(second);
        order.verify(confirmedDelete).cancel(first);
    }

    @Test
    public void deletionCleanupDoesNotDeleteConfirmedMetadataTwice() throws Exception {
        L2NetworkCascadeExtension extension = new L2NetworkCascadeExtension();
        DatabaseFacade dbf = mock(DatabaseFacade.class);
        PluginRegistry pluginRegistry = mock(PluginRegistry.class);
        L2DeleteConfirmExtensionPoint confirmedDelete = mock(L2DeleteConfirmExtensionPoint.class);
        L2NetworkInventory inventory = inventory("l2-1");

        setPrivateField(extension, "dbf", dbf);
        setPrivateField(extension, "pluginRgty", pluginRegistry);
        when(pluginRegistry.getExtensionList(L2DeleteConfirmExtensionPoint.class))
                .thenReturn(Collections.singletonList(confirmedDelete));
        when(confirmedDelete.supports(inventory)).thenReturn(true);

        RecordingCompletion completion = new RecordingCompletion();
        extension.asyncCascade(deletionCleanupAction(inventory), completion);

        Assert.assertTrue(completion.succeeded);
        verify(dbf).eoCleanup(L2NetworkVO.class, inventory.getUuid());
        verify(confirmedDelete, never()).deleteLocalMetadata(inventory);
    }

    private CascadeAction deletionCheckAction(L2NetworkInventory... inventories) {
        return new CascadeAction()
                .setActionCode(CascadeConstant.DELETION_CHECK_CODE)
                .setParentIssuer(L2NetworkVO.class.getSimpleName())
                .setParentIssuerContext(Arrays.asList(inventories));
    }

    private CascadeAction deletionCleanupAction(L2NetworkInventory inventory) {
        return new CascadeAction()
                .setActionCode(CascadeConstant.DELETION_CLEANUP_CODE)
                .setParentIssuer(L2NetworkVO.class.getSimpleName())
                .setParentIssuerContext(Collections.singletonList(inventory));
    }

    private L2NetworkInventory inventory(String uuid) {
        L2NetworkInventory inventory = new L2NetworkInventory();
        inventory.setUuid(uuid);
        return inventory;
    }

    private void setPrivateField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class RecordingCompletion extends Completion {
        private boolean succeeded;
        private ErrorCode error;

        private RecordingCompletion() {
            super(null);
        }

        @Override
        public void success() {
            succeeded = true;
        }

        @Override
        public void fail(ErrorCode errorCode) {
            error = errorCode;
        }
    }
}
