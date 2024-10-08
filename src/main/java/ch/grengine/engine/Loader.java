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

package ch.grengine.engine;

import ch.grengine.load.DefaultClassReleaser;
import ch.grengine.load.ClassReleaser;
import ch.grengine.load.SourceClassLoader;

import java.io.Closeable;

import static java.util.Objects.requireNonNull;

/**
 * Wrapper for a {@link SourceClassLoader} that can only be used
 * by the {@link Engine} that created it.
 * 
 * @since 1.0
 * 
 * @author Alain Stalder
 * @author Made in Switzerland.
 */
public class Loader implements Closeable {

    private final EngineId engineId;
    private final long number;
    private final boolean isAttached;
    private final ClassReleaser classReleaser;
    private SourceClassLoader sourceClassLoader;

    /**
     * constructor, with default class releaser.
     * 
     * @param engineId the engine ID, used to authenticate the caller in some instance methods
     * @param number the loader number
     * @param isAttached whether the loader is attached to the engine or not
     * @param sourceClassLoader the {@link SourceClassLoader} to use for loading classes
     * 
     * @throws NullPointerException if any of the arguments is null
     * 
     * @since 1.0
     */
    public Loader(final EngineId engineId, final long number, final boolean isAttached,
            final SourceClassLoader sourceClassLoader) {
        this(engineId, number, isAttached, DefaultClassReleaser.getInstance(), sourceClassLoader);
    }

    /**
     * constructor, with given class releaser.
     *
     * @param engineId the engine ID, used to authenticate the caller in some instance methods
     * @param number the loader number
     * @param isAttached whether the loader is attached to the engine or not
     * @param classReleaser the class releaser
     * @param sourceClassLoader the {@link SourceClassLoader} to use for loading classes
     *
     * @throws NullPointerException if any of the arguments is null
     *
     * @since 1.1
     */
    public Loader(final EngineId engineId, final long number, final boolean isAttached,
            final ClassReleaser classReleaser, final SourceClassLoader sourceClassLoader) {
        requireNonNull(engineId, "Engine ID is null.");
        requireNonNull(sourceClassLoader, "Source class loader is null.");
        this.engineId = engineId;
        this.number = number;
        this.isAttached = isAttached;
        this.classReleaser = classReleaser;
        setSourceClassLoader(engineId, sourceClassLoader);
    }

    /**
     * gets the source class loader (if the engine ID matches).
     *
     * @param engineId engine ID
     *
     * @return source class loader
     * @throws IllegalArgumentException if the engine ID does not match
     * 
     * @since 1.0
     */
    public SourceClassLoader getSourceClassLoader(final EngineId engineId) {
        if (engineId != this.engineId) {
            throw new IllegalArgumentException("Engine ID does not match (loader created by a different engine).");
        }
        return sourceClassLoader;
    }

    /**
     * sets the source class loader (if the engine ID matches).
     *
     * @param engineId engine ID
     * @param sourceClassLoader source class loader
     *
     * @throws IllegalArgumentException if the engine ID does not match
     * 
     * @since 1.0
     */
    public void setSourceClassLoader(final EngineId engineId, final SourceClassLoader sourceClassLoader) {
        if (engineId != this.engineId) {
            throw new IllegalArgumentException("Engine ID does not match (loader created by a different engine).");
        }
        this.sourceClassLoader = sourceClassLoader;
    }
    
    /**
     * gets the loader number.
     *
     * @return loader number
     * 
     * @since 1.0
     */
    public long getNumber() {
        return number;
    }
    
    /**
     * gets whether the loader is attached to the engine or not.
     *
     * @return whether the loader is attached to the engine or not
     * 
     * @since 1.0
     */
    public boolean isAttached() {
        return isAttached;
    }
   
    /**
     * two loaders are equal if and only if their loader number and their engine ID are equal.
     *
     * @since 1.0
     */
    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof Loader)) return false;
        final Loader loader = (Loader)obj;
        return this.number == loader.number && this.engineId == loader.engineId;
    }
    
    /**
     * implementation based on loader number and engine ID.
     * 
     * @since 1.0
     */
    @Override
    public int hashCode() {
        return (int)number * 17  + engineId.hashCode();
    }

    /**
     * returns a string suitable for logging.
     *
     * @return a string suitable for logging
     * 
     * @since 1.0
     */
    public String toString() {
        return this.getClass().getSimpleName() + "[engineId=" + engineId +
        ", number=" + number + ", isAttached=" + isAttached + "]";
    }

    /**
     * release metadata for all classed ever loaded using this loader.
     * <p>
     * Allows to remove metadata associated by Groovy (or Java) with a class,
     * which is often necessary to get on-the-fly garbage collection.
     * <p>
     * Generally call only when really done using this loader and
     * all loaded classes; subsequently trying to use this loader
     * or its classes results generally in undefined behavior.
     *
     * @since 1.1
     */
    @Override
    public void close() {
        sourceClassLoader.releaseClasses(classReleaser);
    }
    
}
