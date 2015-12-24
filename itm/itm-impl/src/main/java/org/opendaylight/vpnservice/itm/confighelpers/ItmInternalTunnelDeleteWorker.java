/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.itm.confighelpers;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.itm.impl.ItmUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.op.rev150701.Tunnels;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.op.rev150701.tunnels.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.op.rev150701.tunnels.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;

public class ItmInternalTunnelDeleteWorker {
   private static final Logger logger = LoggerFactory.getLogger(ItmInternalTunnelDeleteWorker.class) ;

    public static List<ListenableFuture<Void>> deleteTunnels(DataBroker dataBroker, List<DPNTEPsInfo> dpnTepsList, List<DPNTEPsInfo> meshedDpnList)
    {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        WriteTransaction t = dataBroker.newWriteOnlyTransaction();
        try {
            if (dpnTepsList == null || dpnTepsList.size() == 0) {
                logger.debug("no vtep to delete");
                return null ;
            }

            if (meshedDpnList == null || meshedDpnList.size() == 0) {
                logger.debug("No Meshed Vteps");
                return null ;
            }
            for (DPNTEPsInfo srcDpn : dpnTepsList) {
                logger.trace("Processing srcDpn " + srcDpn);
                for (TunnelEndPoints srcTep : srcDpn.getTunnelEndPoints()) {
                    logger.trace("Processing srcTep " + srcTep);
                    String srcTZone = srcTep.getTransportZone();

                    // run through all other DPNS other than srcDpn
                    for (DPNTEPsInfo dstDpn : meshedDpnList) {
                        if (!(srcDpn.getDPNID().equals(dstDpn.getDPNID()))) {
                            for (TunnelEndPoints dstTep : dstDpn.getTunnelEndPoints()) {
                                logger.trace("Processing dstTep " + dstTep);
                                if (dstTep.getTransportZone().equals(srcTZone)) {
                                    // remove all trunk interfaces
                                    logger.trace("Invoking removeTrunkInterface between source TEP {} , Destination TEP {} " ,srcTep , dstTep);
                                    removeTrunkInterface(dataBroker, srcTep, dstTep, srcDpn.getDPNID(), dstDpn.getDPNID(), t, futures);
                                }
                            }
                        }
                    }

                    // removing vtep / dpn from Tunnels OpDs.
                    InstanceIdentifier<TunnelEndPoints> tepPath =
                                    InstanceIdentifier.builder(Tunnels.class).child(DPNTEPsInfo.class, srcDpn.getKey())
                                                    .child(TunnelEndPoints.class, srcTep.getKey()).build();

                    logger.trace("Tep Removal from DPNTEPSINFO CONFIG DS " + srcTep);
                    t.delete(LogicalDatastoreType.CONFIGURATION, tepPath);
                    InstanceIdentifier<DPNTEPsInfo> dpnPath =
                                    InstanceIdentifier.builder(Tunnels.class).child(DPNTEPsInfo.class, srcDpn.getKey())
                                                    .build();
                    Optional<DPNTEPsInfo> dpnOptional =
                                    ItmUtils.read(LogicalDatastoreType.CONFIGURATION, dpnPath, dataBroker);
                    if (dpnOptional.isPresent()) {
                        DPNTEPsInfo dpnRead = dpnOptional.get();
                        // remove dpn if no vteps exist on dpn
                        if (dpnRead.getTunnelEndPoints() == null || dpnRead.getTunnelEndPoints().size() == 0) {
                            logger.debug( "Removing Terminating Service Table Flow ") ;
                           // setUpOrRemoveTerminatingServiceTable(dpnRead.getDPNID(), false);
                            logger.trace("DPN Removal from DPNTEPSINFO CONFIG DS " + dpnRead);
                            t.delete(LogicalDatastoreType.CONFIGURATION, dpnPath);
                            InstanceIdentifier<Tunnels> tnlContainerPath =
                                            InstanceIdentifier.builder(Tunnels.class).build();
                            Optional<Tunnels> containerOptional =
                                            ItmUtils.read(LogicalDatastoreType.CONFIGURATION,
                                                            tnlContainerPath, dataBroker);
                            // remove container if no DPNs are present
                            if (containerOptional.isPresent()) {
                                Tunnels tnls = containerOptional.get();
                                if (tnls.getDPNTEPsInfo() == null || tnls.getDPNTEPsInfo().isEmpty()) {
                                    logger.trace("Container Removal from DPNTEPSINFO CONFIG DS");
                                    t.delete(LogicalDatastoreType.CONFIGURATION, tnlContainerPath);
                                }
                            }
                        }
                    }
                }
            }
            futures.add( t.submit() );
        } catch (Exception e1) {
            logger.error("exception while deleting tep", e1);
        }
        return futures ;
    }

    private static void removeTrunkInterface(DataBroker dataBroker, TunnelEndPoints srcTep, TunnelEndPoints dstTep, BigInteger srcDpnId, BigInteger dstDpnId,
        WriteTransaction t, List<ListenableFuture<Void>> futures) {
        String trunkfwdIfName =
                        ItmUtils.getTrunkInterfaceName(srcTep.getInterfaceName(), srcTep.getIpAddress()
                                        .getIpv4Address().getValue(), dstTep.getIpAddress().getIpv4Address()
                                        .getValue());
        logger.trace("Removing forward Trunk Interface " + trunkfwdIfName);
        InstanceIdentifier<Interface> trunkIdentifier = ItmUtils.buildId(trunkfwdIfName);
        logger.debug(  " Removing Trunk Interface Name - {} , Id - {} from Config DS {}, {} ", trunkfwdIfName, trunkIdentifier ) ;
        t.delete(LogicalDatastoreType.CONFIGURATION, trunkIdentifier);
        String trunkRevIfName =
                        ItmUtils.getTrunkInterfaceName(dstTep.getInterfaceName(), dstTep.getIpAddress()
                                        .getIpv4Address().getValue(), srcTep.getIpAddress().getIpv4Address()
                                        .getValue());
        logger.trace("Removing Reverse Trunk Interface " + trunkRevIfName);
        trunkIdentifier = ItmUtils.buildId(trunkfwdIfName);
        logger.debug(  " Removing Trunk Interface Name - {} , Id - {} from Config DS {}, {} ", trunkfwdIfName, trunkIdentifier ) ;
        t.delete(LogicalDatastoreType.CONFIGURATION, trunkIdentifier);
    }
}
