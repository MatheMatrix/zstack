package org.zstack.test.storage.primary.local;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.header.storage.primary.PrimaryStorageOverProvisioningManager;
import org.zstack.storage.primary.local.AllocatePrimaryStorageForVmMigrationFlow;

/**
 * Created by david on 2/9/17.
 */
public class TestAllocatePsFlow {

    @InjectMocks
    private AllocatePrimaryStorageForVmMigrationFlow allocateFlow;

    @Mock
    private DatabaseFacade dbf;

    @Mock
    private PrimaryStorageOverProvisioningManager ratioMgr;

    @Before
    public void setUp() {
        allocateFlow = new AllocatePrimaryStorageForVmMigrationFlow();
        MockitoAnnotations.initMocks(this);
    }

    @Test(expected = CloudRuntimeException.class)
    public void testFirstFlowException() {
        // There is no other flows - we the first flow.
        allocateFlow.allocate();
    }
}
