/*
 * Copyright (c) 2014, 2015 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.openstack.netvirt.api;

import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.osgi.framework.ServiceReference;

/**
 * The NetworkingProviderManager handles the mapping between {@link Node}
 * and registered {@link NetworkingProvider} implementations
 */
public interface NetworkingProviderManager {
    /**
     * Returns the Networking Provider for a given node
     * @param ovsdbNode a {@link Node}
     * @return a NetworkProvider
     * @see NetworkingProvider
     */
    NetworkingProvider getProvider(Node ovsdbNode);
    void providerAdded(final ServiceReference ref, final NetworkingProvider provider);
    void providerRemoved(final ServiceReference ref);
}
