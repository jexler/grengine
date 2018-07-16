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

import ch.grengine.code.CompilerFactory;
import ch.grengine.code.groovy.DefaultGroovyCompilerFactory;
import ch.grengine.source.MockSource;
import ch.grengine.source.Source;
import ch.grengine.source.SourceUtil;

import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;


public class FixedSetSourcesTest {
    
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testConstructDefaults() throws Exception {
        MockSource m1 = new MockSource("id1");
        MockSource m2 = new MockSource("id2");
        Set<Source> set = SourceUtil.sourceArrayToSourceSet(m1, m2);
        FixedSetSources.Builder builder = new FixedSetSources.Builder(set);
        FixedSetSources s = builder.build();
        
        Thread.sleep(30);
        assertThat(s.getBuilder(), is(builder));
        assertThat(s.getSourceSet(), is(set));
        assertThat(s.getName(), is(notNullValue()));
        assertThat(s.getCompilerFactory(), is(notNullValue()));
        assertThat(s.getCompilerFactory(), instanceOf(DefaultGroovyCompilerFactory.class));
        assertThat(s.getBuilder().getSourceSet(), is(s.getSourceSet()));
        assertThat(s.getBuilder().getName(), is(s.getName()));
        assertThat(s.getBuilder().getSourceSet(), is(s.getSourceSet()));
        assertThat(s.getBuilder().getLatencyMs(), is(FixedSetSources.Builder.DEFAULT_LATENCY_MS));
        assertThat(s.getLastModified() < System.currentTimeMillis(), is(true));
    }
    
    @Test
    public void testConstructAllDefined() throws Exception {
        MockSource m1 = new MockSource("id1");
        MockSource m2 = new MockSource("id2");
        Set<Source> set = SourceUtil.sourceArrayToSourceSet(m1, m2);
        FixedSetSources.Builder builder = new FixedSetSources.Builder(set);
        CompilerFactory factory = new DefaultGroovyCompilerFactory();
        builder.setName("fixed").setCompilerFactory(factory).setLatencyMs(200);
        FixedSetSources s = builder.build();
        
        Thread.sleep(30);
        assertThat(s.getBuilder(), is(builder));
        assertThat(s.getSourceSet(), is(set));
        assertThat(s.getName(), is("fixed"));
        assertThat(s.getCompilerFactory(), is(factory));
        assertThat(s.getBuilder().getSourceSet(), is(s.getSourceSet()));
        assertThat(s.getBuilder().getName(), is(s.getName()));
        assertThat(s.getBuilder().getSourceSet(), is(s.getSourceSet()));
        assertThat(s.getBuilder().getLatencyMs(), is(200L));
        assertThat(s.getLastModified() < System.currentTimeMillis(), is(true));
    }
    
    @Test
    public void testConstructSourceSetNull() throws Exception {
        try {
            new FixedSetSources.Builder(null);
            fail();
        } catch (NullPointerException e) {
            assertThat(e.getMessage(), is("Source set is null."));
        }
    }
    
    @Test
    public void testModifyBuilderAfterUse() throws Exception {
        MockSource m1 = new MockSource("id1");
        MockSource m2 = new MockSource("id2");
        Set<Source> set = SourceUtil.sourceArrayToSourceSet(m1, m2);
        FixedSetSources.Builder builder = new FixedSetSources.Builder(set);
        builder.build();
        try {
            builder.setName("name");
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), is("Builder already used."));
        }
    }
    
    @Test
    public void testLastModified() throws Exception {
        MockSource m1 = new MockSource("id1");
        MockSource m2 = new MockSource("id2");
        Set<Source> set = SourceUtil.sourceArrayToSourceSet(m1, m2);
        FixedSetSources.Builder builder = new FixedSetSources.Builder(set);
        FixedSetSources s = builder.setLatencyMs(50).build();
        
        m2.setLastModified(1);
        long lastMod = s.getLastModified();
        Thread.sleep(30);
        assertThat(lastMod, is(s.getLastModified()));
        Thread.sleep(30);
        long lastMod2 = s.getLastModified();
        assertThat(lastMod2 > lastMod, is(true));
        Thread.sleep(60);
        assertThat(lastMod2, is(s.getLastModified()));
    }

}
