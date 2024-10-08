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

package ch.grengine.sources;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


class DirModeTest {

    @Test
    void testValueOf() {

        // when/then

        assertThat(DirMode.valueOf(DirMode.NO_SUBDIRS.toString()), CoreMatchers.is(DirMode.NO_SUBDIRS));
        assertThat(DirMode.valueOf(DirMode.WITH_SUBDIRS_RECURSIVE.toString()), CoreMatchers.is(DirMode.WITH_SUBDIRS_RECURSIVE));
    }
    
    @Test
    void testValues() {

        // when/then

        assertThat(DirMode.values().length, is(2));
    }

}
