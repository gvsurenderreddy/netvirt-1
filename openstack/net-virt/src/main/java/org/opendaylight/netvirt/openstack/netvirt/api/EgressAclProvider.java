/*
 * Copyright (c) 2014, 2015 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.openstack.netvirt.api;

import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronSecurityGroup;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronSecurityRule;
import org.opendaylight.netvirt.openstack.netvirt.translator.Neutron_IPs;

import java.util.List;

/**
 *  This interface allows egress Port Security flows to be written to devices.
 */
public interface EgressAclProvider {

    /**
     * Program port security Group.
     *
     * @param dpid the dpid
     * @param segmentationId the segmentation id
     * @param attachedMac the attached mac
     * @param localPort the local port
     * @param securityGroup the security group
     * @param portUuid the uuid of the port.
     * @param write  is this flow write or delete
     */
    void programPortSecurityGroup(Long dpid, String segmentationId, String attachedMac,
                                       long localPort, NeutronSecurityGroup securityGroup,
                                       String portUuid, boolean write);
    /**
     * Program port security rule.
     *
     * @param dpid the dpid
     * @param segmentationId the segmentation id
     * @param attachedMac the attached mac
     * @param localPort the local port
     * @param portSecurityRule the security rule
     * @param vmIp the ip of the remote vm if it has a remote security group.
     * @param write  is this flow write or delete
     */
    void programPortSecurityRule(Long dpid, String segmentationId, String attachedMac,
                                 long localPort, NeutronSecurityRule portSecurityRule,
                                 Neutron_IPs vmIp, boolean write) ;
    /**
     *  Program fixed egress security group rules that will be associated with the VM port when a vm is spawned.
     *
     * @param dpid the dpid
     * @param segmentationId the segmentation id
     * @param attachedMac the attached mac
     * @param localPort the local port
     * @param srcAddressList the list of source ip address assigned to vm
     * @param write is this flow writing or deleting
     */
    void programFixedSecurityGroup(Long dpid, String segmentationId,String attachedMac, long localPort,
                                  List<Neutron_IPs> srcAddressList, boolean write);
}