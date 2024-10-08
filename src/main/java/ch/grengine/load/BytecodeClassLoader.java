/*
   Copyright 2014-now by Alain Stalder. Made in Switzerland.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       https://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package ch.grengine.load;

import ch.grengine.source.Source;
import ch.grengine.code.Bytecode;
import ch.grengine.code.Code;
import ch.grengine.except.LoadException;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.util.Objects.requireNonNull;


/**
 * Bytecode-based class loader that also implements the {@link SourceClassLoader}
 * interface.
 * <p>
 * Loads classes based on given bytecode or from the parent class loader.
 * Depending on the given load mode, the parent class loader is searched
 * first or last.
 * 
 * @since 1.0
 * 
 * @author Alain Stalder
 * @author Made in Switzerland.
 */
public class BytecodeClassLoader extends SourceClassLoader {

    // available in Java 9 and later
    private static final Method GET_DEFINED_PACKAGE_METHOD;
    static {
        Method method;
        try {
            method = BytecodeClassLoader.class.getMethod("getDefinedPackage", String.class);
        } catch (NoSuchMethodException e) {
            method = null;
        }
        GET_DEFINED_PACKAGE_METHOD = method;
    }
    
    private final LoadMode loadMode;
    private final Code code;
    private final Map<String,Object> locks = new HashMap<>();
    private final Queue<WeakReference<Class<?>>> classQueue = new ConcurrentLinkedQueue<>();
    
    /**
     * constructor.
     * 
     * @param parent parent class loader
     * @param loadMode load mode
     * @param code code with bytecode for one or more classes
     * 
     * @throws NullPointerException if any of the arguments is null
     * 
     * @since 1.0
     */
    public BytecodeClassLoader(final ClassLoader parent, final LoadMode loadMode, final Code code) {
        super(requireNonNull(parent, "Parent class loader is null."));
        requireNonNull(loadMode, "Load mode is null.");
        requireNonNull(code, "Code is null.");
        this.loadMode = loadMode;
        this.code = code;
    }
    
    /*
     * Default calling sequence of loadClass(..) from Oracle Javadoc:
     * 
     * - Invoke findLoadedClass(String) to check if the class has already been loaded.
     * - Invoke the loadClass method on the parent class loader. If the parent is null
     *   the class loader built-in to the virtual machine is used, instead.
     * - Invoke the findClass(String) method to find the class.
     * 
     * LoadMode.PARENT_FIRST: Follows this convention; findClass(name) tries to load
     *   the class from bytecode and, if not present, throws a ClassNotFoundException.
     * 
     * LoadMode.CURRENT_FIRST: Instead in loadClass(..) tries first to load the class
     *   from bytecode, then tries the other two options.
     */

    private Class<?> loadClassFromBytecode(final String name) {
        final Bytecode bc = code.getBytecode(name);
        if (bc == null) {
            return null;
        }

        Class<?> clazz;

        final String packageName;
        final Object packageNameLock;
        final Object nameLock;
        synchronized(locks) {
            if ((clazz = findLoadedClass(name)) != null) {
                return clazz;
            }
            packageName = getPackageName(name);
            packageNameLock = (packageName == null) ? null : locks.computeIfAbsent(packageName, l -> new Object());
            nameLock = locks.computeIfAbsent(name, l -> new Object());
        }

        // define package if not already defined
        if (packageName != null) {
            synchronized (packageNameLock) {
                if (getDefinedPackageOrPackage(packageName) == null) {
                    definePackage(packageName);
                }
            }
        }

        // define class if not already defined
        synchronized (nameLock) {
            if ((clazz = findLoadedClass(name)) == null) {
                clazz = defineClass(name, bc.getBytes());
                classQueue.add(new WeakReference<>(clazz));
            }
        }

        // OK to remove locks from map here, because at this point always
        // both class and package have already been defined, so it does
        // not matter whether other threads lock on these or other locks
        // for the same class or package names anymore.
        synchronized(locks) {
            if (packageNameLock != null) {
                locks.remove(packageName);
            }
            locks.remove(name);
        }

        return clazz;
    }

    private String getPackageName(final String className) {
        final int i = className.lastIndexOf('.');
        if (i >= 0) {
            return className.substring(0, i);
        } else {
            return null;
        }
    }

    // package scope for unit tests
    void definePackage(final String packageName) {
        definePackage(packageName, null, null, null, null, null, null, null);
    }

    // package scope for unit tests
    Class<?> defineClass(final String name, final byte[] bytes) {
        return defineClass(name, bytes, 0, bytes.length);
    }


    @Override
    protected Class<?> findClass(final String name) throws ClassNotFoundException {

        final Class<?> clazz;
        if ((clazz = loadClassFromBytecode(name)) != null) {
            return clazz;
        }
        
        // not found
        throw new ClassNotFoundException(name);
    }

    
    @Override
    protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
        
        if (loadMode != LoadMode.CURRENT_FIRST) {
            return super.loadClass(name, resolve);
        }

        Class<?> clazz;
        if ((clazz = loadClassFromBytecode(name)) == null) {
            if ((clazz = findLoadedClass(name)) == null) {
                clazz = getParent().loadClass(name);
            }
        }
        if (resolve) {
            resolveClass(clazz);
        }
        return clazz;
    }
    
    @Override
    public Class<?> loadMainClass(final Source source) {
        return loadMainClassBySource(this, source);
    }
    
    @Override
    public Class<?> loadClass(final Source source, final String name) {
        return loadClassBySourceAndName(this, source, name);
    }

    @Override
    public BytecodeClassLoader findBytecodeClassLoaderBySource(final Source source) {
        BytecodeClassLoader loader = null;
        if (getLoadMode() == LoadMode.PARENT_FIRST) {
            if (getParent() instanceof SourceClassLoader) {
                loader = ((SourceClassLoader)getParent()).findBytecodeClassLoaderBySource(source);
            }
            if (loader == null && code.isForSource(source)) {
                loader = this;
            }
        } else {
            if (code.isForSource(source)) {
                loader = this;
            }
            if (loader == null && (getParent() instanceof SourceClassLoader)) {
                loader = ((SourceClassLoader)getParent()).findBytecodeClassLoaderBySource(source);
            }
        }
        return loader;
    }
    
    @Override
    public LoadMode getLoadMode() {
        return loadMode;
    }
    
    /**
     * Helper method for loading the main class of the given source from the given class loader.
     *
     * @param classLoader class loader
     * @param source source
     *
     * @return main class
     * @throws LoadException if loading failed, including if the class was not found
     * 
     * @since 1.0
     */
    public static Class<?> loadMainClassBySource(final ClassLoader classLoader, final Source source) {
        final BytecodeClassLoader loader = getLoader(classLoader, source);
        final String name = loader.code.getMainClassName(source);
        final Class<?> clazz = loader.loadClassFromBytecode(name);
        if (clazz == null) {
            throw new LoadException("Inconsistent code: " + loader.code +
                    ". Main class '" + name + "' not found for source. Source: " + source);
        }
        return clazz;
    }
    
    /**
     * Helper method for loading a class with the given name and from the given source
     * from the given class loader.
     *
     * @param classLoader class loader
     * @param source source
     * @param name class name
     *
     * @return class
     * @throws LoadException if loading failed, including if the class was not found
     * 
     * @since 1.0
     */
    public static Class<?> loadClassBySourceAndName(final ClassLoader classLoader, final Source source, 
            final String name) {
        final BytecodeClassLoader loader = getLoader(classLoader, source);
        if (!loader.code.getClassNames(source).contains(name)) {
            throw new LoadException("Class '" + name + "' not found for source. Source: " + source);
        }
        final Class<?> clazz = loader.loadClassFromBytecode(name);
        if (clazz == null) {
            throw new LoadException("Inconsistent code: " + loader.code +
                    ". Class '" + name + "' not found for source. Source: " + source);
        }
        return clazz;
    }

    private static BytecodeClassLoader getLoader(final ClassLoader classLoader, final Source source) {
        BytecodeClassLoader loader = null;
        if (classLoader instanceof SourceClassLoader) {
            loader = ((SourceClassLoader)classLoader).findBytecodeClassLoaderBySource(source);
        }
        if (loader == null) {
            throw new LoadException("Source not found: " + source);
        }
        return loader;
    }
    
    @Override
    public BytecodeClassLoader clone() {
        return new BytecodeClassLoader(getParent(), loadMode, code);
    }

    @Override
    public void releaseClasses(final ClassReleaser releaser) {
        WeakReference<Class<?>> ref;
        do {
            ref = classQueue.poll();
            if (ref != null) {
                Class<?> clazz = ref.get();
                if (clazz != null) {
                    try {
                        releaser.release(clazz);
                    } catch (Exception ignore) {
                    }
                }
            }
        } while (ref != null);
    }

    /**
     * gets the code.
     *
     * @return code
     * 
     * @since 1.0
     */
    public Code getCode() {
        return code;
    }

    // uses getDefinedPackage() for Java 9 and later, with fallback to getPackage()
    @SuppressWarnings("deprecation")
    private Package getDefinedPackageOrPackage(String name) {
        if (GET_DEFINED_PACKAGE_METHOD == null) {
            return getPackage(name);
        }
        try {
            return (Package)GET_DEFINED_PACKAGE_METHOD.invoke(this, name);
        } catch (IllegalAccessException | InvocationTargetException e) {
            return getPackage(name);
        }
    }

}
