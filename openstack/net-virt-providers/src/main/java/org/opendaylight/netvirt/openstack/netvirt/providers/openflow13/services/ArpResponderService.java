/*
 * Copyright (c) 2014, 2015 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.services;

import java.math.BigInteger;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;

import org.opendaylight.netvirt.openstack.netvirt.api.Action;
import org.opendaylight.netvirt.openstack.netvirt.api.ArpProvider;
import org.opendaylight.netvirt.openstack.netvirt.api.Status;
import org.opendaylight.netvirt.openstack.netvirt.api.StatusCode;
import org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.AbstractServiceInstance;
import org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.Service;
import org.opendaylight.netvirt.openstack.netvirt.api.Constants;
import org.opendaylight.netvirt.openstack.netvirt.providers.ConfigInterface;
import org.opendaylight.netvirt.utils.mdsal.openflow.ActionUtils;
import org.opendaylight.netvirt.utils.mdsal.openflow.FlowUtils;
import org.opendaylight.netvirt.utils.mdsal.openflow.MatchUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev100924.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.EtherType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

public class ArpResponderService extends AbstractServiceInstance implements ArpProvider, ConfigInterface {
    private static final Logger LOG = LoggerFactory.getLogger(ArpResponderService.class);

    public ArpResponderService() {
        super(Service.ARP_RESPONDER);
    }

    public ArpResponderService(Service service) {
        super(service);
    }

    @Override
    public Status programStaticArpEntry(Long dpid, String segmentationId, String macAddressStr,
                                        InetAddress ipAddress, Action action) {
        if (ipAddress instanceof Inet6Address) {
            // WORKAROUND: For now ipv6 is not supported
            // TODO: implement ipv6 case
            LOG.debug("ipv6 address case is not implemented yet. dpid {} segmentationId {} macAddressStr, "
                    + "ipAddress {} action {}",
                    dpid, segmentationId, macAddressStr, ipAddress, action);
            return new Status(StatusCode.NOTIMPLEMENTED);
        }

        NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(dpid);
        FlowBuilder flowBuilder = new FlowBuilder();
        String flowName = "ArpResponder_" + segmentationId + "_" + ipAddress.getHostAddress();
        FlowUtils.initFlowBuilder(flowBuilder, flowName, getTable()).setPriority(1024);

        MatchBuilder matchBuilder = new MatchBuilder();
        MatchUtils.createEtherTypeMatch(matchBuilder, new EtherType(Constants.ARP_ETHERTYPE));
        MatchUtils.createArpDstIpv4Match(matchBuilder, MatchUtils.iPv4PrefixFromIPv4Address(ipAddress.getHostAddress()));

        if (segmentationId != null) {
            final Long inPort = MatchUtils.parseExplicitOFPort(segmentationId);
            if (inPort != null) {
                MatchUtils.createInPortMatch(matchBuilder, dpid, inPort);
            } else {
                MatchUtils.createTunnelIDMatch(matchBuilder, new BigInteger(segmentationId));
            }
        }

        flowBuilder.setMatch(matchBuilder.build());

        if (action.equals(Action.ADD)) {
            // Instructions List Stores Individual Instructions
            InstructionsBuilder isb = new InstructionsBuilder();
            List<Instruction> instructions = Lists.newArrayList();
            InstructionBuilder ib = new InstructionBuilder();
            ApplyActionsBuilder aab = new ApplyActionsBuilder();
            ActionBuilder ab = new ActionBuilder();
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action> actionList = Lists.newArrayList();

            // Move Eth Src to Eth Dst
            ab.setAction(ActionUtils.nxMoveEthSrcToEthDstAction());
            ab.setOrder(0);
            ab.setKey(new ActionKey(0));
            actionList.add(ab.build());

            // Set Eth Src
            MacAddress macAddress = new MacAddress(macAddressStr);
            ab.setAction(ActionUtils.setDlSrcAction(macAddress));
            ab.setOrder(1);
            ab.setKey(new ActionKey(1));
            actionList.add(ab.build());

            // Set ARP OP
            ab.setAction(ActionUtils.nxLoadArpOpAction(BigInteger.valueOf(0x02L)));
            ab.setOrder(2);
            ab.setKey(new ActionKey(2));
            actionList.add(ab.build());

            // Move ARP SHA to ARP THA
            ab.setAction(ActionUtils.nxMoveArpShaToArpThaAction());
            ab.setOrder(3);
            ab.setKey(new ActionKey(3));
            actionList.add(ab.build());

            // Move ARP SPA to ARP TPA
            ab.setAction(ActionUtils.nxMoveArpSpaToArpTpaAction());
            ab.setOrder(4);
            ab.setKey(new ActionKey(4));
            actionList.add(ab.build());

            // Load Mac to ARP SHA
            ab.setAction(ActionUtils.nxLoadArpShaAction(macAddress));
            ab.setOrder(5);
            ab.setKey(new ActionKey(5));
            actionList.add(ab.build());

            // Load IP to ARP SPA
            ab.setAction(ActionUtils.nxLoadArpSpaAction(ipAddress.getHostAddress()));
            ab.setOrder(6);
            ab.setKey(new ActionKey(6));
            actionList.add(ab.build());

            // Output of InPort
            ab.setAction(ActionUtils.outputAction(FlowUtils.getSpecialNodeConnectorId(dpid, "INPORT")));
            ab.setOrder(7);
            ab.setKey(new ActionKey(7));
            actionList.add(ab.build());

            // Create Apply Actions Instruction
            aab.setAction(actionList);
            ib.setInstruction(new ApplyActionsCaseBuilder().setApplyActions(aab.build()).build());
            ib.setOrder(0);
            ib.setKey(new InstructionKey(0));
            instructions.add(ib.build());

            flowBuilder.setInstructions(isb.setInstruction(instructions).build());
            writeFlow(flowBuilder, nodeBuilder);
        } else {
            removeFlow(flowBuilder, nodeBuilder);
        }

        // ToDo: WriteFlow/RemoveFlow should return something we can use to check success
        return new Status(StatusCode.SUCCESS);
    }

    @Override
    public void setDependencies(BundleContext bundleContext, ServiceReference serviceReference) {
        super.setDependencies(bundleContext.getServiceReference(ArpProvider.class.getName()), this);
    }

    @Override
    public void setDependencies(Object impl) {}
}
