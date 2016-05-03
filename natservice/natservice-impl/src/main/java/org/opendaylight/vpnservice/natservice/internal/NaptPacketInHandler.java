/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.natservice.internal;

import org.opendaylight.controller.liblldp.NetUtils;
import org.opendaylight.vpnservice.mdsalutil.MetaDataUtil;
import org.opendaylight.vpnservice.mdsalutil.packet.Ethernet;
import org.opendaylight.vpnservice.mdsalutil.packet.IPv4;
import org.opendaylight.vpnservice.mdsalutil.packet.TCP;
import org.opendaylight.vpnservice.mdsalutil.packet.UDP;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.HashSet;
import com.google.common.primitives.Ints;

public class NaptPacketInHandler implements PacketProcessingListener {

    private static final Logger LOG = LoggerFactory.getLogger(NaptPacketInHandler.class);
    private final static HashSet<String> incomingPacketMap = new HashSet<>();
    private final EventDispatcher naptEventdispatcher;

    public NaptPacketInHandler(EventDispatcher eventDispatcher) {
        this.naptEventdispatcher = eventDispatcher;
    }

    @Override
    public void onPacketReceived(PacketReceived packetReceived) {
        String internalIPAddress = "";
        int portNumber = 0;
        long routerId = 0L;
        NAPTEntryEvent.Operation operation = NAPTEntryEvent.Operation.ADD;
        NAPTEntryEvent.Protocol protocol;

        Short tableId = packetReceived.getTableId().getValue();

        if (LOG.isTraceEnabled()) {
            LOG.trace("packet: {}, tableId {}", packetReceived, tableId);
        }

        if (tableId == NatConstants.OUTBOUND_NAPT_TABLE) {
            LOG.debug("NAT Service : NAPTPacketInHandler Packet for Outbound NAPT Table");
            byte[] inPayload = packetReceived.getPayload();
            Ethernet ethPkt = new Ethernet();
            if (inPayload != null) {
                try {
                    ethPkt.deserialize(inPayload, 0, inPayload.length * NetUtils.NumBitsInAByte);
                } catch (Exception e) {
                    LOG.warn("Failed to decode Packet", e);
                    return;
                }
                if (ethPkt.getPayload() instanceof IPv4) {
                    IPv4 ipPkt = (IPv4) ethPkt.getPayload();
                    byte[] ipSrc = Ints.toByteArray(ipPkt.getSourceAddress());

                    internalIPAddress = NatUtil.toStringIpAddress(ipSrc, LOG);
                    LOG.trace("Retrieved internalIPAddress {}", internalIPAddress);
                    if (ipPkt.getPayload() instanceof TCP) {
                        TCP tcpPkt = (TCP) ipPkt.getPayload();
                        portNumber = tcpPkt.getSourcePort();
                        if(portNumber < 0){
                            portNumber = 32767 + portNumber + 32767 + 2;
                            LOG.trace("Retrieved and extracted TCP portNumber {}", portNumber);
                        }
                        protocol = NAPTEntryEvent.Protocol.TCP;
                        LOG.trace("Retrieved TCP portNumber {}", portNumber);
                    } else if (ipPkt.getPayload() instanceof UDP) {
                        UDP udpPkt = (UDP) ipPkt.getPayload();
                        portNumber = udpPkt.getSourcePort();
                        if(portNumber < 0){
                            portNumber = 32767 + portNumber + 32767 + 2;
                            LOG.trace("Retrieved and extracted UDP portNumber {}", portNumber);
                        }
                        protocol = NAPTEntryEvent.Protocol.UDP;
                        LOG.trace("Retrieved UDP portNumber {}", portNumber);
                    } else {
                        LOG.error("Incoming Packet is neither TCP or UDP packet");
                        return;
                    }
                } else {
                    LOG.error("Incoming Packet is not IPv4 packet");
                    return;
                }

                if(internalIPAddress != null) {
                    String sourceIPPortKey = internalIPAddress + ":" + portNumber;
                    LOG.debug("NAT Service : sourceIPPortKey {} mapping maintained in the map", sourceIPPortKey);
                    if (!incomingPacketMap.contains(sourceIPPortKey)) {
                        incomingPacketMap.add(internalIPAddress + portNumber);

                        BigInteger metadata = packetReceived.getMatch().getMetadata().getMetadata();
                        routerId = (metadata.and(MetaDataUtil.METADATA_MASK_VRFID)).longValue();
                        if( routerId <= 0) {
                            LOG.error("Nat Service : Router ID is invalid");
                            return;
                        }
                        //send to Event Queue
                        NAPTEntryEvent naptEntryEvent = new NAPTEntryEvent(internalIPAddress,portNumber,routerId,
                                operation,protocol);
                        naptEventdispatcher.addNaptEvent(naptEntryEvent);
                    } else {
                        LOG.trace("Ignore the packet, already processed");
                    }
                }else {
                    LOG.error("Nullpointer exception in retrieving internalIPAddress");
                }
            }
        }else {
            LOG.trace("Packet is not from the Outbound NAPT table");
        }
    }

    public void removeIncomingPacketMap(String sourceIPPortKey) {
        incomingPacketMap.remove(sourceIPPortKey);
        LOG.debug("NAT Service : sourceIPPortKey {} mapping is removed from map", sourceIPPortKey);
    }
}