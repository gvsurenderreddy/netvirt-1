/*
 * Copyright (c) 2015, 2016 Inocybe and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.openstack.netvirt.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.Maps;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.netvirt.openstack.netvirt.api.NetworkingProvider;
import org.opendaylight.netvirt.openstack.netvirt.api.OvsdbInventoryService;
import org.opendaylight.netvirt.utils.servicehelper.ServiceHelper;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology
        .Node;
import org.osgi.framework.ServiceReference;

/**
 * Unit test for {@link ProviderNetworkManagerImpl}
 */
@RunWith(MockitoJUnitRunner.class)
public class ProviderNetworkManagerImplTest {

    @InjectMocks private ProviderNetworkManagerImpl providerNetworkManagerImpl;

    @Mock private OvsdbInventoryService ovsdbInventoryService;

    @Spy private Map<Node, NetworkingProvider> nodeToProviderMapping = Maps.newHashMap();

    /**
     * Test method {@link ProviderNetworkManagerImpl#getProvider(Node)}
     */
    @Test
    public void testGetProvider(){
        // TODO test the method with no networkingProvider in the map
        // Could not be done as ProviderEntry is a private inner class of ProviderNetworkManagerImpl
        Node node = mock(Node.class);
        NetworkingProvider networkingProvider = mock(NetworkingProvider.class);
        nodeToProviderMapping.put(node, networkingProvider);
        assertEquals("Error, did not return the networkingProvider of the specified node", networkingProvider, providerNetworkManagerImpl.getProvider(node));
    }

    /**
     * Test methods {@link ProviderNetworkManagerImpl#providerRemoved(ServiceReference)}
     * and {@link ProviderNetworkManagerImpl#providerAdded(ServiceReference, NetworkingProvider)}
     */
    @Test
    public void testProviderAddedAndRemoved() throws Exception {
        Map<?, ?> map = (HashMap<?, ?>) getField("providers");

        ServiceReference<?> ref = mock(ServiceReference.class);
        when(ref.getProperty(org.osgi.framework.Constants.SERVICE_ID)).thenReturn(1L);

        providerNetworkManagerImpl.providerAdded(ref, mock(NetworkingProvider.class));

        assertEquals("Error, providerAdded() did not add the provider", 1, map.size());

        providerNetworkManagerImpl.providerRemoved(ref);

        assertEquals("Error, providerRemoved() did not remove the provider", 0, map.size());
    }

    @Test
    public void testSetDependencies() throws Exception {
        OvsdbInventoryService ovsdbInventoryService = mock(OvsdbInventoryService.class);

        ServiceHelper.overrideGlobalInstance(OvsdbInventoryService.class, ovsdbInventoryService);

        providerNetworkManagerImpl.setDependencies(mock(ServiceReference.class));

        assertEquals("Error, did not return the correct object", getField("ovsdbInventoryService"), ovsdbInventoryService);
    }

    private Object getField(String fieldName) throws Exception {
        Field field = ProviderNetworkManagerImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(providerNetworkManagerImpl);
    }
}
