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

package ch.grengine.code;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static java.util.Objects.requireNonNull;


/**
 * Helper class for analyzing class name conflicts.
 * <p>
 * Can identify classes with the same name in different code layers
 * or in code layers and a parent class loader.
 * 
 * @since 1.0
 * 
 * @author Alain Stalder
 * @author Made in Switzerland.
 */
public class ClassNameConflictAnalyzer {
    
    /**
     * gets a map of class name to a list of all {@link Code} layers
     * that contain a class with that name.
     * 
     * @param codeLayers code layers
     *
     * @return map of class name to list of code layers
     * @throws NullPointerException if code layers are null
     * 
     * @since 1.0
     */
    public static Map<String,List<Code>> getAllClassNamesMap(final List<Code> codeLayers) {
        requireNonNull(codeLayers, "Code layers are null.");
        Map<String,List<Code>> origins = new HashMap<>();
        for (Code code : codeLayers) {
            Set<String> classNameSet = code.getClassNameSet();
            for (String className : classNameSet) {
                List<Code> codeList = origins.computeIfAbsent(className, k -> new LinkedList<>());
                codeList.add(code);
            }
        }
        return origins;        
    }

    /**
     * gets a map of class name to a list of all {@link Code} layers
     * that contain a class with that name, but only if the class name
     * occurs more than once in the code layers.
     * 
     * @param codeLayers code layers
     *
     * @return map of class name to list of code layers
     * @throws NullPointerException if code layers are null
     * 
     * @since 1.0
     */
    public static Map<String,List<Code>> getSameClassNamesInMultipleCodeLayersMap(final List<Code> codeLayers) {
        requireNonNull(codeLayers, "Code layers are null.");
        Map<String,List<Code>> origins = getAllClassNamesMap(codeLayers);
        Map<String,List<Code>> originsWithDuplicates = new HashMap<>();
        for (Entry<String, List<Code>> entry : origins.entrySet()) {
            String name = entry.getKey();
            List<Code> codeList = entry.getValue();
            if (codeList.size() > 1) {
                originsWithDuplicates.put(name, codeList);
            }
        }
        return originsWithDuplicates;        
    }
    
    /**
     * gets a map of class name to a list of all {@link Code} layers
     * that contain a class with that name, but only if the class name
     * occurs also in the given parent class loader.
     * <p>
     * Note that this method tries to load all of these classes by name
     * from the parent class loader.
     * 
     * @param parent parent class loader
     * @param codeLayers code layers
     *
     * @return map of class name to list of code layers
     * @throws NullPointerException if the parent class loader or code layers are null
     * 
     * @since 1.0
     */
    public static Map<String,List<Code>> getSameClassNamesInParentAndCodeLayersMap(
            final ClassLoader parent, final List<Code> codeLayers) {
        requireNonNull(parent, "Parent class loader is null.");
        requireNonNull(codeLayers, "Code layers are null.");
        Map<String,List<Code>> origins = getAllClassNamesMap(codeLayers);
        Map<String,List<Code>> originsWithDuplicateInParent = new HashMap<>();
        for (Entry<String, List<Code>> entry : origins.entrySet()) {
            String name = entry.getKey();
            List<Code> codeList = entry.getValue();
            try {
                parent.loadClass(name);
                originsWithDuplicateInParent.put(name, codeList);
            } catch (Throwable t) {
                // ignore
            }
        }
        return originsWithDuplicateInParent;        
    }

}
