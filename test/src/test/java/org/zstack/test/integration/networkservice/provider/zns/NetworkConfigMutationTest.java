package org.zstack.test.integration.networkservice.provider.zns;

import org.junit.Test;
import org.zstack.header.network.NetworkConfigMutation;
import org.zstack.header.network.l2.NetworkOperationOrigin;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NetworkConfigMutationTest {
    @Test
    public void encapsulationMutationHasATypedImmutableTarget() {
        NetworkConfigMutation mutation = NetworkConfigMutation.encapsulation(
                "l2-1", NetworkOperationOrigin.API, "operation-1", "account-1",
                "L2GeneveNetwork", 5001);

        assertEquals(NetworkConfigMutation.Kind.ENCAP, mutation.getKind());
        assertEquals("l2-1", mutation.getL2Uuid());
        assertEquals("L2GeneveNetwork", mutation.getEncapsulation().getL2Type());
        assertEquals(5001, mutation.getEncapsulation().getVirtualNetworkId());
        assertFalse(Arrays.stream(NetworkConfigMutation.class.getDeclaredFields())
                .map(Field::getType).anyMatch(Map.class::isAssignableFrom));
    }

    @Test
    public void l3AndIpamMutationsKeepTypedImmutableTargets() {
        NetworkConfigMutation l3 = NetworkConfigMutation.l3Create(
                "l2-1", NetworkOperationOrigin.API, "operation-2", "account-1",
                "l3-1", "L3BasicNetwork", Collections.singletonList("router::r1"));
        assertEquals(NetworkConfigMutation.Kind.L3_CREATE, l3.getKind());
        assertEquals("l3-1", l3.getL3Create().getL3Uuid());

        NetworkConfigMutation ipam = NetworkConfigMutation.ipam(
                "l2-1", NetworkOperationOrigin.API, "operation-3", "account-1",
                "l3-1", 4, "192.0.2.1/24", Collections.singletonList(
                        new NetworkConfigMutation.IpRangeTarget(
                                "range-1", "192.0.2.10", "192.0.2.200")));
        assertEquals(NetworkConfigMutation.Kind.IPAM, ipam.getKind());
        assertEquals("l3-1", ipam.getIpam().getL3Uuid());
        assertEquals("range-1", ipam.getIpam().getRanges().get(0).getRangeUuid());

        NetworkConfigMutation deleteIpam = NetworkConfigMutation.deleteIpam(
                "l2-1", NetworkOperationOrigin.API, "operation-5", "account-1",
                "l3-1", 4);
        assertTrue(deleteIpam.getIpam().isDelete());
        assertEquals("l3-1", deleteIpam.getIpam().getL3Uuid());

        NetworkConfigMutation dhcp = NetworkConfigMutation.dhcp(
                "l2-1", NetworkOperationOrigin.API, "operation-4", "account-1",
                "l3-1", true, Collections.singletonList("enableDHCP::true"));
        assertEquals(NetworkConfigMutation.Kind.DHCP_DNS, dhcp.getKind());
        assertEquals("l3-1", dhcp.getDhcp().getL3Uuid());
        assertEquals(Collections.singletonList("enableDHCP::true"),
                dhcp.getDhcp().getSystemTags());

        NetworkConfigMutation dns = NetworkConfigMutation.dhcpDns(
                "l2-1", NetworkOperationOrigin.API, "operation-6", "account-1",
                "l3-1", 4, Collections.singletonList("192.0.2.53"));
        assertEquals(Integer.valueOf(4), dns.getDhcp().getIpVersion());
        assertEquals(Collections.singletonList("192.0.2.53"),
                dns.getDhcp().getDnsServers());
        assertTrue(dns.getDhcp().isEnabled());
    }
}
