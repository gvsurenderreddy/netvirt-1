/*
 * Copyright (c) 2014, 2015 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.services;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.List;

import org.opendaylight.netvirt.openstack.netvirt.api.OutboundNatProvider;
import org.opendaylight.netvirt.openstack.netvirt.api.StatusCode;
import org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.AbstractServiceInstance;
import org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.Service;
import org.opendaylight.netvirt.openstack.netvirt.api.Action;
import org.opendaylight.netvirt.openstack.netvirt.api.Status;
import org.opendaylight.netvirt.openstack.netvirt.providers.ConfigInterface;
import org.opendaylight.netvirt.utils.mdsal.openflow.ActionUtils;
import org.opendaylight.netvirt.utils.mdsal.openflow.FlowUtils;
import org.opendaylight.netvirt.utils.mdsal.openflow.InstructionUtils;
import org.opendaylight.netvirt.utils.mdsal.openflow.MatchUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev100924.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.address.address.Ipv4Builder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder;

import com.google.common.collect.Lists;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutboundNatService extends AbstractServiceInstance implements OutboundNatProvider, ConfigInterface {
    private static final Logger LOG = LoggerFactory.getLogger(OutboundNatService.class);

    public OutboundNatService() {
        super(Service.OUTBOUND_NAT);
    }

    public OutboundNatService(Service service) {
        super(service);
    }

    @Override
    public Status programIpRewriteRule(Long dpidLong,
                                       String matchSegmentationId,
                                       String matchDestMacAddress,
                                       InetAddress matchSrcAddress,
                                       String rewriteSrcMacAddress,
                                       String rewriteDestMacAddress,
                                       InetAddress rewriteSrcAddress,
                                       Long OutPort,
                                       Action action) {
        NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(dpidLong);
        FlowBuilder flowBuilder = new FlowBuilder();
        String flowName = "OutboundNAT_" + matchSegmentationId + "_" + matchSrcAddress.getHostAddress();
        FlowUtils.initFlowBuilder(flowBuilder, flowName, getTable()).setPriority(512);

        MatchBuilder matchBuilder = new MatchBuilder();
        MatchUtils.createDmacIpSaMatch(matchBuilder,
                matchDestMacAddress,
                MatchUtils.iPv4PrefixFromIPv4Address(matchSrcAddress.getHostAddress()),
                matchSegmentationId);
        flowBuilder.setMatch(matchBuilder.build());

        if (action.equals(Action.ADD)) {
            // Instructions List Stores Individual Instructions
            InstructionsBuilder isb = new InstructionsBuilder();
            List<Instruction> instructions = Lists.newArrayList();
            List<Instruction> instructions_tmp = Lists.newArrayList();
            InstructionBuilder ib = new InstructionBuilder();

            ApplyActionsBuilder aab = new ApplyActionsBuilder();
            ActionBuilder ab = new ActionBuilder();
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action> actionList =
                    Lists.newArrayList();

            // Set source Mac address
            ab.setAction(ActionUtils.setDlSrcAction(new MacAddress(rewriteSrcMacAddress)));
            ab.setOrder(0);
            ab.setKey(new ActionKey(0));
            actionList.add(ab.build());

            // DecTTL
            ab.setAction(ActionUtils.decNwTtlAction());
            ab.setOrder(1);
            ab.setKey(new ActionKey(1));
            actionList.add(ab.build());

            // Set Destination Mac address
            ab.setAction(ActionUtils.setDlDstAction(new MacAddress(rewriteDestMacAddress)));
            ab.setOrder(2);
            ab.setKey(new ActionKey(2));
            actionList.add(ab.build());

            // Set source Ip address
            Ipv4Builder ipb = new Ipv4Builder().setIpv4Address(
                    MatchUtils.iPv4PrefixFromIPv4Address(rewriteSrcAddress.getHostAddress()));
            ab.setAction(ActionUtils.setNwSrcAction(ipb.build()));
            ab.setOrder(3);
            ab.setKey(new ActionKey(3));
            actionList.add(ab.build());

            // Create Apply Actions Instruction
            aab.setAction(actionList);
            ib.setInstruction(new ApplyActionsCaseBuilder().setApplyActions(aab.build()).build());
            ib.setOrder(0);
            ib.setKey(new InstructionKey(0));
            instructions_tmp.add(ib.build());

            // Set the Output Port/Iface
            ib = new InstructionBuilder();
            InstructionUtils.addOutputPortInstructions(ib, dpidLong, OutPort, instructions_tmp);
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
    public Status programIpRewriteExclusion(Long dpid, String segmentationId, String excludedCidr,
                                            Action action) {
        String ipAddress = excludedCidr.substring(0, excludedCidr.indexOf("/"));
        InetAddress inetAddress;
        try {
            inetAddress = InetAddress.getByName(ipAddress);
        } catch (UnknownHostException e) {
            return new Status(StatusCode.BADREQUEST);
        }
        if (inetAddress instanceof Inet6Address) {
            // WORKAROUND: For now ipv6 is not supported
            // TODO: implement ipv6 cidr case
            LOG.debug("ipv6 cidr is not implemented yet. cidr {}",
                      excludedCidr);
            return new Status(StatusCode.NOTIMPLEMENTED);
        }

        NodeBuilder nodeBuilder = FlowUtils.createNodeBuilder(dpid);
        FlowBuilder flowBuilder = new FlowBuilder();
        String flowName = "OutboundNATExclusion_" + segmentationId + "_" + excludedCidr;
        FlowUtils.initFlowBuilder(flowBuilder, flowName, getTable()).setPriority(1024);

        MatchBuilder matchBuilder = new MatchBuilder();
        MatchUtils.createTunnelIDMatch(matchBuilder, new BigInteger(segmentationId));

        MatchUtils.createDstL3IPv4Match(matchBuilder, new Ipv4Prefix(excludedCidr));
        flowBuilder.setMatch(matchBuilder.build());

        if (action.equals(Action.ADD)) {
            // Instructions List Stores Individual Instructions
            InstructionsBuilder isb = new InstructionsBuilder();
            List<Instruction> instructions = Lists.newArrayList();
            InstructionBuilder ib;

            // Goto Next Table
            ib = getMutablePipelineInstructionBuilder();
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
        super.setDependencies(bundleContext.getServiceReference(OutboundNatProvider.class.getName()), this);
    }

    @Override
    public void setDependencies(Object impl) {

    }
}
