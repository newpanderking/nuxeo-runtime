/*
 * (C) Copyright 2006-2008 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     bstefanescu
 *
 * $Id$
 */

package org.nuxeo.runtime.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 *
 */
public class AdapterManager {

    protected Map<Class<?>, AdaptableDescriptor> adapters;
    
    private static AdapterManager instance = new AdapterManager();
    
    public static AdapterManager getInstance() {
        return instance;
    }
    
    public AdapterManager() {
        adapters = new ConcurrentHashMap<Class<?>, AdaptableDescriptor>(); 
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getAdapter(Object instance, Class<T> adapter) {
        AdapterFactory af = (AdapterFactory)getAdapterFactory(instance, adapter);
        if (af != null) {
            return adapter.cast(af.getAdapter(instance, adapter));
        }
        if (adapter.isInstance(instance)) {
            return (T)instance;
        }
        return null;
    }
    
    public AdapterFactory<?> getAdapterFactory(Object instance, Class<?> adapter) {
        return getAdapterFactory(instance.getClass(), adapter);
    }
    
    protected AdapterFactory<?> getAdapterFactory(Class<?> klass, Class<?> adapter) {
        AdaptableDescriptor descriptor = adapters.get(klass);
        if (descriptor != null) {
            AdapterFactory<?> factory = descriptor.getAdapterFactory(adapter);
            if (factory != null) {
                return factory;
            }
        }        
        return findAdapterFactory(klass, adapter);
    }
    
    protected synchronized AdapterFactory<?> findAdapterFactory(Class<?> klass, Class<?> adapter) {
        // try again the lookup (we are synchronized now)
        AdaptableDescriptor descriptor = adapters.get(klass);
        if (descriptor != null) {
            AdapterFactory<?> factory = descriptor.getAdapterFactory(adapter);
            if (factory != null) {
                return factory;
            }
        } else {
            descriptor = new AdaptableDescriptor(klass);
            adapters.put(klass, descriptor);                        
        }
        // ask super types
        Class<?>[] superTypes = descriptor.getSuperTypes();
        for (int i=0; i<superTypes.length; i++) {
            AdapterFactory<?> factory = findAdapterFactory(superTypes[i], adapter);
            if (factory != null) {
                descriptor.addAdapterFactory(adapter, factory);
                return factory;
            }
        }
        return null;        
    }
    
    public synchronized void registerAdapter(AdapterFactory<?> factory) {
        Class<?> adaptable = factory.getAdaptableType();
        AdaptableDescriptor descriptor = adapters.get(adaptable);
        if (descriptor == null) {
            descriptor = new AdaptableDescriptor(adaptable);
            adapters.put(adaptable, descriptor);
        }
        for (Class<?> klass : factory.getAdapterTypes()) {
            descriptor.addAdapterFactory(klass, factory);    
        }        
    }
    
    
    public void unregisterAdapter(Class<?> adapter) {
        //TODO
    }
    
}