/*
 * Copyright (c) 2013, 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.utils.servicehelper;

import java.util.HashMap;
import java.util.Map;

import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

/**
 * The class helps to register and retrieve OSGi service registry
 */
public final class ServiceHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceHelper.class);
    private static final Map<Class<?>, Object> OVERRIDES = new HashMap<>();

    /**
     * Override a global instance. This should generally only be used for testing.
     *
     * @param clazz The target class.
     * @param instance The instance to return for the class.
     */
    public static <T> void overrideGlobalInstance(Class<T> clazz, T instance) {
        ServiceHelper.OVERRIDES.put(clazz, instance);
    }

    /**
     * Retrieve global instance of a class via OSGI registry, if
     * there are many only the first is returned.
     *
     * @param clazz The target class
     * @param bundle The caller
     */
    public static Object getGlobalInstance(Class<?> clazz, Object bundle) {
        return getGlobalInstance(clazz, bundle, null);
    }

    /**
     * Retrieve global instance of a class via OSGI registry, if
     * there are many only the first is returned. On this version an LDAP
     * type of filter is applied
     *
     * @param clazz The target class
     * @param bundle The caller
     * @param serviceFilter LDAP filter to be applied in the search
     */
    public static Object getGlobalInstance(Class<?> clazz, Object bundle,
                                           String serviceFilter) {
        if (OVERRIDES.containsKey(clazz)) {
            return OVERRIDES.get(clazz);
        }
        Object[] instances = getGlobalInstances(clazz, bundle, serviceFilter);
        if (instances != null && instances.length > 0) {
            return instances[0];
        }
        return null;
    }

    /**
     * Retrieve all the Instances of a Service, optionally
     * filtered via serviceFilter if non-null else all the results are
     * returned if null
     *
     * @param clazz The target class
     * @param bundle The caller
     * @param serviceFilter LDAP filter to be applied in the search
     */
    private static Object[] getGlobalInstances(Class<?> clazz, Object bundle,
                                              String serviceFilter) {
        Object instances[] = null;
        try {
            Bundle ourBundle = FrameworkUtil.getBundle(bundle.getClass());
            if (ourBundle != null) {
                BundleContext bCtx = ourBundle.getBundleContext();

                ServiceReference<?>[] services = bCtx.getServiceReferences(clazz
                        .getName(), serviceFilter);

                if (services != null) {
                    instances = new Object[services.length];
                    for (int i = 0; i < services.length; i++) {
                        instances[i] = bCtx.getService(services[i]);
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Error retrieving global instances of {} from caller {} with filter {}",
                    clazz, bundle, serviceFilter, e);
        }
        return instances;
    }
}
