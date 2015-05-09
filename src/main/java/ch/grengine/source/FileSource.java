/*
   Copyright 2014-now by Alain Stalder. Made in Switzerland.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package ch.grengine.source;

import java.io.File;


/**
 * Interface for a file-based script source.
 * 
 * @since 1.0
 * 
 * @author Alain Stalder
 * @author Made in Switzerland.
 */
public interface FileSource extends Source {
    
    /**
     * gets the canonical file of this source, with fallback to the absolute file.
     * <p>
     * Hence {@code fileSource.getFile().isAbsolute()} always returns true.
     *
     * @return canonical file of this source
     * 
     * @since 1.0
     */
    File getFile();

}
