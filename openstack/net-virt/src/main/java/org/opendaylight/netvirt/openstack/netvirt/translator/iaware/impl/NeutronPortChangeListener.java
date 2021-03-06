/*
 * Copyright (c) 2015 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.openstack.netvirt.translator.iaware.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronPort_AllowedAddressPairs;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronPort_ExtraDHCPOption;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronPort_VIFDetail;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronPort;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronSecurityGroup;
import org.opendaylight.netvirt.openstack.netvirt.translator.Neutron_IPs;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.INeutronSecurityGroupCRUD;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.NeutronCRUDInterfaces;
import org.opendaylight.netvirt.openstack.netvirt.translator.iaware.INeutronPortAware;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.binding.rev150712.PortBindingExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.binding.rev150712.binding.attributes.VifDetails;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.AllowedAddressPairs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.ExtraDhcpOpts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.portsecurity.rev150712.PortSecurityExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeutronPortChangeListener implements ClusteredDataChangeListener, AutoCloseable{
    private static final Logger LOG = LoggerFactory.getLogger(NeutronPortChangeListener.class);

    private ListenerRegistration<DataChangeListener> registration;
    private DataBroker db;

    public NeutronPortChangeListener(DataBroker db){
        this.db = db;
        InstanceIdentifier<Port> path = InstanceIdentifier
                .create(Neutron.class)
                .child(Ports.class)
                .child(Port.class);
        LOG.debug("Register listener for Neutron Port model data changes");
        registration =
                this.db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION, path, this, DataChangeScope.ONE);

    }

    @Override
    public void onDataChanged(
            AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> changes) {
        LOG.trace("Data changes : {}",changes);

        Object[] subscribers = NeutronIAwareUtil.getInstances(INeutronPortAware.class, this);
        createPort(changes, subscribers);
        updatePort(changes, subscribers);
        deletePort(changes, subscribers);
    }

    private void createPort(
            AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> changes,
            Object[] subscribers) {
        for (Entry<InstanceIdentifier<?>, DataObject> newPort : changes.getCreatedData().entrySet()) {
        	if(newPort.getValue() instanceof Port){
                NeutronPort port = fromMd((Port)newPort.getValue());
                for(Object entry: subscribers){
                    INeutronPortAware subscriber = (INeutronPortAware)entry;
                    subscriber.neutronPortCreated(port);
                }
        	}
        }
    }

    private void updatePort(
            AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> changes,
            Object[] subscribers) {
        Map<String, NeutronPort> originalPortMap = getChangedPorts(changes.getOriginalData());
        for (Entry<InstanceIdentifier<?>, DataObject> updatePort : changes.getUpdatedData().entrySet()) {
            if (updatePort.getValue() instanceof Port) {
                NeutronPort port = fromMd((Port)updatePort.getValue());
                NeutronPort originalPort = originalPortMap.get(port.getID());
                if (originalPort != null) {
                    port.setOriginalPort(originalPort);
                } else {
                    LOG.warn("Original Port data is missing");
                }
                for (Object entry: subscribers) {
                    INeutronPortAware subscriber = (INeutronPortAware)entry;
                    subscriber.neutronPortUpdated(port);
                }
            }
        }
    }

    private void deletePort(
            AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> changes,
            Object[] subscribers) {
        for (InstanceIdentifier<?> deletedPortPath : changes.getRemovedPaths()) {
            if(deletedPortPath.getTargetType().equals(Port.class)){
                NeutronPort port = fromMd((Port)changes.getOriginalData().get(deletedPortPath));
                for(Object entry: subscribers){
                    INeutronPortAware subscriber = (INeutronPortAware)entry;
                    subscriber.neutronPortDeleted(port);
                }
            }
        }
    }

    /*
     * This method is borrowed from NeutronPortInterface.java class of Neutron Northbound class.
     * We will be utilizing similar code from other classes from the same package of neutron project.
     */
    private NeutronPort fromMd(Port port) {

        NeutronPort result = new NeutronPort();
        result.setAdminStateUp(port.isAdminStateUp());
        if (port.getAllowedAddressPairs() != null) {
            List<NeutronPort_AllowedAddressPairs> pairs = new ArrayList<>();
            for (AllowedAddressPairs mdPair : port.getAllowedAddressPairs()) {
                NeutronPort_AllowedAddressPairs pair = new NeutronPort_AllowedAddressPairs();
                pair.setIpAddress(String.valueOf(mdPair.getIpAddress().getValue()));
                pair.setMacAddress(mdPair.getMacAddress().getValue());
                pairs.add(pair);
            }
            result.setAllowedAddressPairs(pairs);
        }
        result.setDeviceID(port.getDeviceId());
        result.setDeviceOwner(port.getDeviceOwner());
        if (port.getExtraDhcpOpts() != null) {
            List<NeutronPort_ExtraDHCPOption> options = new ArrayList<>();
            for (ExtraDhcpOpts opt : port.getExtraDhcpOpts()) {
                NeutronPort_ExtraDHCPOption arg = new NeutronPort_ExtraDHCPOption();
                arg.setName(opt.getOptName());
                arg.setValue(opt.getOptValue());
                options.add(arg);
            }
            result.setExtraDHCPOptions(options);
        }
        if (port.getFixedIps() != null) {
            List<Neutron_IPs> ips = new ArrayList<>();
            for (FixedIps mdIP : port.getFixedIps()) {
                Neutron_IPs ip = new Neutron_IPs();
                ip.setIpAddress(String.valueOf(mdIP.getIpAddress().getValue()));
                ip.setSubnetUUID(mdIP.getSubnetId().getValue());
                ips.add(ip);
            }
            result.setFixedIPs(ips);
        }
        result.setMacAddress(port.getMacAddress().getValue());
        result.setName(port.getName());
        result.setNetworkUUID(String.valueOf(port.getNetworkId().getValue()));
        if (port.getSecurityGroups() != null) {
            Set<NeutronSecurityGroup> allGroups = new HashSet<>();
            NeutronCRUDInterfaces interfaces = new NeutronCRUDInterfaces().fetchINeutronSecurityGroupCRUD(this);
            INeutronSecurityGroupCRUD sgIf = interfaces.getSecurityGroupInterface();
            for (Uuid sgUuid : port.getSecurityGroups()) {
                NeutronSecurityGroup secGroup = sgIf.getNeutronSecurityGroup(sgUuid.getValue());
                if (secGroup != null) {
                    allGroups.add(sgIf.getNeutronSecurityGroup(sgUuid.getValue()));
                }
            }
            List<NeutronSecurityGroup> groups = new ArrayList<>();
            groups.addAll(allGroups);
            result.setSecurityGroups(groups);
        }
        result.setStatus(port.getStatus());
        if (port.getTenantId() != null) {
            result.setTenantID(String.valueOf(port.getTenantId().getValue()).replace("-", ""));
        }
        result.setPortUUID(String.valueOf(port.getUuid().getValue()));
        addExtensions(port, result);
        return result;
    }

    protected void addExtensions(Port port, NeutronPort result) {
        PortBindingExtension binding = port.getAugmentation(PortBindingExtension.class);
        result.setBindinghostID(binding.getHostId());
        if (binding.getVifDetails() != null) {
            final Map<String, String> details = new HashMap<String, String>(binding.getVifDetails().size());
            for (final VifDetails vifDetail : binding.getVifDetails()) {
                details.put(vifDetail.getDetailsKey(), vifDetail.getValue());
            }
            result.setVIFDetails(details);
        }
        result.setBindingvifType(binding.getVifType());
        result.setBindingvnicType(binding.getVnicType());
        PortSecurityExtension portSecurity = port.getAugmentation(PortSecurityExtension.class);
        if (portSecurity != null && portSecurity.isPortSecurityEnabled() != null) {
            result.setPortSecurityEnabled(portSecurity.isPortSecurityEnabled());
        }
    }

    private  Map<String,NeutronPort> getChangedPorts(Map<InstanceIdentifier<?>, DataObject> changedData) {
        LOG.trace("getChangedPorts:" + changedData);
        Map<String,NeutronPort> portMap = new HashMap<>();
        for (Map.Entry<InstanceIdentifier<?>, DataObject> changed : changedData.entrySet()) {
            if (changed.getValue() instanceof Port) {
                NeutronPort port = fromMd((Port)changed.getValue());
                portMap.put(port.getID(), port);
            }
        }
        return portMap;
    }

    @Override
    public void close() throws Exception {
        registration.close();
    }
}
