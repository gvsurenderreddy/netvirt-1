/*
 * Copyright (c) 2014, 2015 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.openstack.netvirt.api;

import java.net.InetAddress;

/**
 *  This interface allows NAT flows to be written to devices
 */
public interface OutboundNatProvider {
    Status programIpRewriteRule(Long dpidLong,
                                String matchSegmentationId,
                                String matchDestMacAddress,
                                InetAddress matchSrcAddress,
                                String rewriteSrcMacAddress,
                                String rewriteDestMacAddress,
                                InetAddress rewriteSrcAddress,
                                Long OutPort,
                                Action action);

    Status programIpRewriteExclusion(Long dpid, String segmentationId,
                                     String excludedCidr, Action action);

}
