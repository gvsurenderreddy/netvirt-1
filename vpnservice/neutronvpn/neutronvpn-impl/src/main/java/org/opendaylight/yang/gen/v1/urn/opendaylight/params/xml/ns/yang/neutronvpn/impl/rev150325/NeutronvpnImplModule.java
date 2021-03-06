/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.neutronvpn.impl.rev150325;

import org.opendaylight.netvirt.neutronvpn.NeutronvpnProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.LockManagerService;

public class NeutronvpnImplModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.neutronvpn
        .impl.rev150325.AbstractNeutronvpnImplModule {
    public NeutronvpnImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight
            .controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public NeutronvpnImplModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight
            .controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn
            .opendaylight.params.xml.ns.yang.neutronvpn.impl.rev150325.NeutronvpnImplModule oldModule, java.lang
            .AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        // TODO:implement
        LockManagerService lockManagerService = getRpcRegistryDependency().getRpcService(LockManagerService.class);
        NeutronvpnProvider provider = new NeutronvpnProvider(getRpcRegistryDependency(),
                getNotificationPublishServiceDependency(),getNotificationServiceDependency());
        provider.setMdsalManager(getMdsalutilDependency());
        provider.setLockManager(lockManagerService);
        provider.setEntityOwnershipService(getEntityOwnershipServiceDependency());
        getBrokerDependency().registerProvider(provider);
        return provider;
    }

}
