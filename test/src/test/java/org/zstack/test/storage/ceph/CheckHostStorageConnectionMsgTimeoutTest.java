package org.zstack.test.storage.ceph;

import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.zstack.core.componentloader.ComponentLoader;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.header.host.HostInventory;
import org.zstack.header.host.HostStatus;
import org.zstack.header.host.HostVO;
import org.zstack.header.message.Message;
import org.zstack.storage.primary.CheckHostStorageConnectionMsg;
import org.zstack.test.Api;
import org.zstack.test.ApiSenderException;
import org.zstack.test.DBUtil;
import org.zstack.test.WebBeanConstructor;
import org.zstack.test.aop.CloudBusAopProxy;
import org.zstack.test.deployer.Deployer;

import java.util.List;

public class CheckHostStorageConnectionMsgTimeoutTest {
    Deployer deployer;
    Api api;
    ComponentLoader loader;
    DatabaseFacade dbf;
    CloudBusAopProxy aop;

    @Before
    public void setUp() throws Exception {
        DBUtil.reDeployDB();
        WebBeanConstructor con = new WebBeanConstructor();
        deployer = new Deployer("deployerXml/ceph/TestCephHostReconnectTimeout.xml", con);
        deployer.addSpringConfig("ceph.xml");
        deployer.addSpringConfig("cephSimulator.xml");
        deployer.addSpringConfig("KVMRelated.xml");
        deployer.addSpringConfig("CloudBusAopProxy.xml");
        deployer.build();
        api = deployer.getApi();
        loader = deployer.getComponentLoader();
        dbf = loader.getComponent(DatabaseFacade.class);
        aop = loader.getComponent(CloudBusAopProxy.class);
    }

    @Test
    public void test() throws ApiSenderException {
        aop.addMessage(CheckHostStorageConnectionMsg.class, CloudBusAopProxy.Behavior.FAIL);

        HostInventory host = deployer.hosts.get("host1");
        api.reconnectHost(host.getUuid());

        HostVO hostVO = dbf.findByUuid(host.getUuid(), HostVO.class);
        Assert.assertEquals(HostStatus.Connected, hostVO.getStatus());

        List<Message> captured = aop.getCapturedMessages();
        Assert.assertFalse("expected at least one captured CheckHostStorageConnectionMsg", captured.isEmpty());
        CheckHostStorageConnectionMsg capturedMsg = (CheckHostStorageConnectionMsg) captured.get(0);
        Assert.assertEquals("msg timeout must be 60s to prevent 30min hang", 60, capturedMsg.getTimeout());
    }
}
