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

package org.nuxeo.runtime.tomcat.dev;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.catalina.loader.WebappClassLoader;
import org.nuxeo.osgi.application.MutableClassLoader;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */

public class NuxeoDevWebappClassLoader extends WebappClassLoader implements
        MutableClassLoader, WebResourcesCacheFlusher {

    public LocalClassLoader createLocalClassLoader(URL... urls) {
        LocalClassLoader cl = new LocalURLClassLoader(urls, this);
        addChildren(cl);
        return cl;
    }

    protected DevFrameworkBootstrap bootstrap;

    protected File webinf;

    protected List<LocalClassLoader> children;

    protected volatile LocalClassLoader[] _children;

    public NuxeoDevWebappClassLoader() {
        this.children = new ArrayList<LocalClassLoader>();
    }

    public NuxeoDevWebappClassLoader(ClassLoader parent) {
        super(parent);
        this.children = new ArrayList<LocalClassLoader>();
    }

    public void setBootstrap(DevFrameworkBootstrap bootstrap) {
        this.bootstrap = bootstrap;
        webinf = new File(new File(bootstrap.getHome(), "nuxeo.war"), "WEB-INF");
    }

    public DevFrameworkBootstrap getBootstrap() {
        return bootstrap;
    }

    public synchronized void addChildren(LocalClassLoader loader) {
        children.add(loader);
        _children = null;
    }

    public synchronized void removeChildren(ClassLoader loader) {
        children.remove(loader);
        _children = null;
    }

    public synchronized void clear() {
        children.clear();
        _children = null;
    }

    public synchronized void flushWebResources() {
        resourceEntries.clear();
    }

    public LocalClassLoader[] getChildren() {
        LocalClassLoader[] cls = _children;
        if (cls == null) {
            synchronized (this) {
                _children = children.toArray(new LocalClassLoader[children.size()]);
                cls = _children;
            }
        }
        return cls;
    }

    /**
     * Do not synchronize this method at method level to avoid deadlocks.
     */
    @Override
    public Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
        try {
            synchronized (this) {
                return super.loadClass(name, resolve);
            }
        } catch (ClassNotFoundException e) {
            for (LocalClassLoader cl : getChildren()) {
                try {
                    return cl.loadLocalClass(name, resolve);
                } catch (ClassNotFoundException ee) {
                    // do nothing
                }
            }
        }
        throw new ClassNotFoundException(name);
    }

    @Override
    public URL getResource(String name) {
        URL url = super.getResource(name);
        if (url != null) {
            return url;
        }
        for (LocalClassLoader cl : getChildren()) {
            url = cl.getLocalResource(name);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Enumeration<URL> urls = super.getResources(name);
        if (urls.hasMoreElements()) {
            return urls;
        }
        for (LocalClassLoader cl : getChildren()) {
            urls = cl.getLocalResources(name);
            if (urls.hasMoreElements()) {
                return urls;
            }
        }
        return urls;
    }

    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }

    @Override
    public void setParentClassLoader(ClassLoader pcl) {
        super.setParentClassLoader(pcl);
    }

    public ClassLoader getParentClassLoader() {
        return parent;
    }

    @Override
    public ClassLoader getClassLoader() {
        return this;
    }

    public void installSeamClasses(File[] dirs) throws IOException {
        File seamdev = new File(webinf, "dev");
        if (seamdev.exists()) {
            IOUtils.deleteTree(seamdev);
        }
        seamdev.mkdirs();
        for (File dir : dirs) {
            IOUtils.copyTree(dir, new File(seamdev, dir.getName()));
        }
    }

    public void installResourceBundleFragments(List<File> files)
            throws IOException {
        File webClasses = new File(webinf, "classes");
        Map<String, List<File>> fragments = new HashMap<String, List<File>>();

        for (File file : files) {
            String name = resourceBundleName(file);
            if (!fragments.containsKey(name)) {
                fragments.put(name, new ArrayList<File>());
            }
            fragments.get(name).add(file);
        }
        for (String name : fragments.keySet()) {
            IOUtils.appendResourceBundleFragments(name, fragments.get(name),
                    webClasses);
        }
    }

    protected static String resourceBundleName(File file) {
        String name = file.getName();
        int lastDotIdx = name.lastIndexOf('.');
        if (lastDotIdx == -1) {
            lastDotIdx = 0;
        }
        String resourceName = name.substring(lastDotIdx);
        return name;
    }
}
