/*
 * Copyright (c) 2014, 2015 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.openstack.netvirt.api;

import org.opendaylight.netvirt.openstack.netvirt.AbstractHandler;
import org.opendaylight.netvirt.openstack.netvirt.AbstractEvent;
import org.osgi.framework.ServiceReference;

/**
 * Openstack related events will be enqueued into a common event queue.
 * This interface provides access to an event dispatcher, as well as registration to link dispatcher to which handlers
 * dispatcher will utilize.
 */
public interface EventDispatcher {
    /**
     * Enqueue the event.
     * @param event the {@link AbstractEvent} event to be handled.
     */
    void enqueueEvent(AbstractEvent event);
    void eventHandlerAdded(final ServiceReference ref, AbstractHandler handler);
    void eventHandlerRemoved(final ServiceReference ref);
}

