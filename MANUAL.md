[![image](grengine.jpg)](https://grengine.ch)

# Grengine User Manual

[Grengine](https://grengine.ch/) is an engine for running and embedding Groovy in a Java VM.

## Features

* _Convenience and Speed_: Scripts can be run very conveniently
  and are automatically only (re-)compiled when necessary.
  All operations are automatically thread-safe and Grengine never
  creates additional threads to do its work.

* _Flexibility and Control_: Beyond useful default behavior, many things
  can be configured and tweaked and there is full control over the
  "life cycle" of a script: script compilation, class loading,
  script instance creation and script running.

* _Extensibility_: Where configurable options are not enough,
  it is usually straightforward which classes to override or
  which interfaces to implement differently to get exactly
  the behavior you want.

* _Security_: The same compiled bytecode can be used very easily in multiple
  class loaders, thus fully separating static variables in scripts.
  This allows, for example, to implement separate user sessions in
  a web server, so that no user information can leak between sessions
  even if the same Groovy scripts are used for all users.
  Note, however, that class loading is a rather expensive operation
  with limited garbage collection, hence this feature does not scale
  well with larger numbers of sessions (more details further below).
  
* _Safety_: Grengine works with Java VM 8 or later and works with
  basically any Groovy version that runs on Java 8 or later.
  The interface between Grengine and the Groovy JDK is so narrow
  and so widely used that it is very unlikely that new Groovy versions
  will break interoperability with Grengine, and even if it was so,
  adapting Grengine would almost certainly be a small matter
  with no impact on the Grengine API.

## Running Scripts with Grengine

### Basic Usage

With Grengine, a script can essentially be run by script text,
script file or script URL:

```java
Grengine gren = new Grengine();
gren.run("println 'Hello World!'");
gren.run(new File("MyScript.groovy"));
gren.run(new URL("https://somewhere.org/MyScript.groovy"));
```

A binding can optionally be indicated by passing a `Binding`
object or a `Map<String,Object>`.

```java
Map<String,Object> map = new HashMap<String,Object>();
map.put("x", 5);
map.put("y", 2);
Binding binding = new Binding(map);
gren.run("println x+y", binding);
gren.run(scriptFile, map);
```

In Groovy, passing a map is easier than in Java:

```groovy
gren.run('println x+y', [ 'x' : 5, 'y' : 2 ])
```

For more convenient use in Java, Grengine provides an easier way to
construct a `Binding` by indicating alternating `String` and `Object`
parameters in pairs:

```java
gren.run("println x+y", gren.binding("x", 5, "y", 2));
```

### Behind the Scenes and Performance

What happens "behind the scenes" when you run a Groovy script?

A simple Groovy script like "return 2" is implicitly roughly equivalent
to the following Java source:

```java
public class SomeGeneratedClassName extends groovy.lang.Script {
  public Object run() { return 2; }
}
```

First this source is compiled. (Unlike some other script languages,
Groovy is always compiled to bytecode for a Java VM; it is never
interpreted directly.) In this simple case, with no inner classes,
the result is a single byte array (`byte[]`) associated with the class
name "SomeGeneratedClassName".

The next step is loading the class, for that you need a `java.lang.ClassLoader`.
From the outside, the class is loaded by calling

```java
Class<?> clazz = classLoader.loadClass("SomeGeneratedClassName");
```

Inside the class loader, at the first request to load the class by name,
the class is "defined". This is the moment when the bytecode is actually
loaded into the VM as an instance of `java.lang.Class`:

```java
Class<?> clazz = this.defineClass("SomeGeneratedClassName", bytes, 0, bytes.length);
```

It is important to be aware that a class can only be defined *_once_*
per class loader instance.
There is no way to "unload" a class in a Java VM or to replace the bytecode.
If a class loader and its classes are not used (referenced) any more, it is
eventually garbage collected by the VM.
If you need a new version of the same class, at least implicitly a new class
loader instance is always required.

Once a class is loaded, an instance can be created and the script
can be run, optionally with a specific binding:

```java
Script script = (Script)clazz.newInstance();
script.setBinding(binding);
Object obj = script.run();
```

#### Text-based Scripts

Let's see what the "SomeGeneratedClassName" is when using the Groovy JDK
(`GroovyShell` and `GroovyClassLoader`) and when using Grengine:

```java
String scriptText = "println this.class.name";
GroovyShell shell = new GroovyShell();
shell.evaluate(scriptText);
shell.evaluate(scriptText);
GroovyClassLoader gcl = new GroovyClassLoader();
((Script)gcl.parseClass(scriptText).newInstance()).run();
((Script)gcl.parseClass(scriptText).newInstance()).run();
Grengine gren = new Grengine();
gren.run(scriptText);
gren.run(scriptText);
```

This will output something like this:

```
Script1
Script2
script14128476235402024152416
script14128476235552024152416
ScriptE15A19DFB5A38B7835981B7078A86D3B
ScriptE15A19DFB5A38B7835981B7078A86D3B
```

In case of the Groovy JDK, the generated class name is different
for each call.
(This may be a bit difficult to spot in case of the `GroovyClassLoader`,
because only two digits are different; actually `System.nanoTime()` is
used to generate the middle part of the class name.)
With Grengine, the name is always the same for the same script text:
"Script" plus the MD5 hash of the script text.

In terms of performance, Grengine makes a big difference if you run
the same script text multiple times, but is still slower than running
an already created `Script` instance:

```java
long t0 = System.nanoTime();
for (int i=0; i<1000; i++) shell.evaluate("return 2");
long t1 = System.nanoTime();
for (int i=0; i<1000; i++) gren.run("return 2");
long t2 = System.nanoTime();
for (int i=0; i<1000; i++) script.run();
long t3 = System.nanoTime();
System.out.printf("GroovyShell: %8.3f ms%n", (t1-t0)/1000000.0);
System.out.printf("Grengine:    %8.3f ms%n", (t2-t1)/1000000.0);
System.out.printf("Script:      %8.3f ms%n", (t3-t2)/1000000.0);
```

Here's the output I got on my computer*:

```
GroovyShell: 4807.417 ms
Grengine:      54.801 ms
Script:         0.262 ms
```

The difference between the `GroovyShell` and Grengine is so huge
because the `GroovyShell` compiles each time and compiling is
very expensive compared to everything else (except if the script
itself did something that took a long time, of course).
The difference between Grengine and calling the script directly
comes from the initial compilation plus (for each call) the overhead
of calculating the MD5 hash, looking up the already compiled
(and loaded) class and creating a script instance.

Note that you can optionally also define the desired class name
for a script:

```java
gren.run("println this.class.name", "MyScript");
shell.evaluate("println this.class.name", "MyScript");
```

This will have no effect if the script text explicitly declares
a class.

#### File-based Scripts

For script files, the default class name is simply the file name
(without extension), independently of whether you use Grengine or
the Groovy JDK.

Grengine identifies script files by the canonical file path
(with fallback to the absolute file path if the canonical file
path cannot be determined, which is very rarely the case in practice).
In addition, `File.lastModified()` is queried before each run and,
if the file had been modified, it is recompiled, but only then.
In contrast, the `GroovyShell` compiles each time.
This leads to similar performance differences when running a script
file that contains "return 2" 1000 times*:

```
GroovyShell: 4966.928 ms
Grengine:      30.594 ms
```

For Grengine the main overhead (at least when running a script on
a local drive) is `File.lastModified()`, which can be an astonishingly
slow call, especially on Windows.

#### URL-based Scripts

For script URLs, Grengine identifies the script by its URL and, by default,
the script text at the URL is only read once and then assumed never
to change again. This default is based on the assumption that typically
when a URL is used, getting the script text is a slow operation and,
unlike with files, there is no other way to find out whether the script
text at the URL has changed.

There are several ways to tweak and optimize the defaults of Grengine
regarding scripts by text, file and URL, which will be explained
a bit later on.

### Separating Loading/Creating/Running of Scripts

With Grengine (as with the Groovy JDK) it is possible to separate class
loading from object creation and from running. Grengine offers a lot of
convenience here, again:

```java
Class<?> clazz;
clazz = gren.load("return 2");
clazz = gren.load("return 2", "MyDesiredClassName");
clazz = gren.load(scriptFile);
clazz = gren.load(scriptUrl);
Script script;
script = gren.create(clazz);
script = gren.create("return 2");
script = gren.create("return 2", "MyDesiredClassName");
script = gren.create(scriptFile);
script = gren.create(scriptUrl);
Object obj;
obj = gren.run(script);
obj = gren.run(script, binding);
obj = gren.run(script, map);
obj = gren.run("return 2");
obj = gren.run("return x", binding);
obj = gren.run("return x", map);
obj = gren.run("return x", gren.binding("x", 5));
obj = gren.run("return 2", "MyDesiredClassName");
obj = gren.run("return x", "MyDesiredClassName", binding);
// ...
```

### The Source Interface

The interface `Source` abstracts a Groovy script source. It has essentially
the following two methods:

```java
String getId();
long getLastModified();
```

For sources from script text, file and URL, there are interfaces
that extend `Source`, with the following (pretty obvious) additional
methods:

```java
// TextSource extends Source:
String getText();
```

```java
// FileSource extends Source:
File getFile();
```

```java
// UrlSource extends Source:
URL getUrl();
```

For the default implementations, the source ID is as follows:

* `DefaultTextSource`: The MD5 hash of the script text.
* `DefaultFileSource`: The canonical file path of the script file
  (with fallback to the absolute file path of the script file, if
  the canonical file path cannot be obtained, which is very rarely
  the case in practice).
* `DefaultUrlSource`: The URL.

Last modified is as follows:

* `DefaultTextSource`: 0
* `DefaultFileSource`: `File.lastModified()`
* `DefaultUrlSource`: 0

Grengine provides convenience methods for getting `Source` instances,
and these sources can also be directly used to load classes, create
`Script` instances and to run scripts:

```java
Source textSource = gren.source("return 2");
Source textSourceWithName = gren.source("return 2", "MyScript");
Source fileSource = gren.source(scriptFile);
Source urlSource = gren.source(scriptUrl);
System.out.println(textSource.getId() + " - " + textSource.getLastModified());
System.out.println(textSourceWithName.getId() + " - " + textSourceWithName.getLastModified());
System.out.println(fileSource.getId() + " - " + fileSource.getLastModified());
System.out.println(urlSource.getId() + " - " + urlSource.getLastModified());
clazz = gren.load(textSource);
script = gren.create(fileSource);
obj = gren.run(urlSource, gren.binding("x", 5));
```

Here's a sample output of the above:

```
/groovy/script/Script61E5513229BA3D53A09D057769AC99CC - 0
/groovy/script/Script61E5513229BA3D53A09D057769AC99CC/MyScript - 0
/private/var/folders/38/r0n49vmn7zg5dffk79_tgpl80000gn/T/MyScript.groovy - 1912774471000
file:/var/folders/38/r0n49vmn7zg5dffk79_tgpl80000gn/T/MyScript.groovy - 0
```

### Tweaking Performance with the SourceFactory

In order to create sources, Grengine uses a `SourceFactory`, by default
set to `new DefaultSourceFactory()`, which provides instances of the default
source implementations.
Alternatively, the `DefaultSourceFactory` can be constructed with different
settings*:

```java
Grengine grenDefault = new Grengine();
Grengine grenTweaked= new Grengine.Builder()
        .setSourceFactory(new DefaultSourceFactory.Builder()
                .setTrackTextSourceIds(true)
                .setTrackFileSourceLastModified(true)
                .build())
        .build();
grenDefault.run("return 2");
grenTweaked.run("return 2");
grenDefault.run(scriptFile);
grenTweaked.run(scriptFile);
long t0 = System.nanoTime();
for (int i=0; i<1000; i++) grenDefault.run("return 2");
long t1 = System.nanoTime();
for (int i=0; i<1000; i++) grenTweaked.run("return 2");
long t2 = System.nanoTime();
for (int i=0; i<1000; i++) grenDefault.run(scriptFile);
long t3 = System.nanoTime();
for (int i=0; i<1000; i++) grenTweaked.run(scriptFile);
long t4 = System.nanoTime();
System.out.printf("Script Text - Default:  %8.3f ms%n", (t1-t0)/1000000.0);
System.out.printf("Script Text - Tweaked:  %8.3f ms%n", (t2-t1)/1000000.0);
System.out.printf("Script File - Default:  %8.3f ms%n", (t3-t2)/1000000.0);
System.out.printf("Script File - Tweaked:  %8.3f ms%n", (t4-t3)/1000000.0);
```

```
Script Text - Default:    44.138 ms
Script Text - Tweaked:    11.271 ms
Script File - Default:    19.193 ms
Script File - Tweaked:    11.873 ms
```

The options of the `DefaultSourceFactory.Builder` in detail:

* `setTrackTextSourceIds(boolean track)`\
  Caches a map of script text to source ID, in order to reduce
  the number of MD5 hash calculations for text-based sources.             |
* `setTrackFileSourceLastModified(boolean track)`\
  Caches a map of source ID to file last modified, in order to
  reduce the number of `file.lastModified()` calls for
  file-based sources.
* `setFileLastModifiedTrackingLatencyMs(long latencyMs)`\
  Sets the latency for checking if a file has been modified;
  default is 1000 ms (one second), which is also often the
  resolution of `file.lastModified()` in practice.
* `setTrackUrlContent(boolean track)`\
  Caches a MD5 hash of the  content (script text) of all used URLs
  and each time a URL is given to the Grengine, gets the URL content
  again if a configurable latency period has expired
  (and recompiles then, if necessary).
* `setUrlTrackingLatencyMs(long latencyMs)`\
  Sets the latency for checking if URL content has been modified;
  default is 60000 ms (one minute).

For further optimizations, you could override some methods in
`DefaultSourceFactory` or provide your own implementation of the 
`SourceFactory` interface.

## Grengine as a Script Container

### Directory-based Grengine

Often you may have some Groovy scripts in a directory which you
may want to run directly or use as a library or API. To make things
concrete, suppose there are the following two files in the current
working directory:

```groovy
.Util.groovy
class Util {
  def concat(def a, def b) { return "$a:$b" }
}
```

```groovy
.Test.groovy
println new Util().concat('xx', 'yy')
```

Now create and use a Grengine based on these sources:

```java
File scriptDir = new File(".");
Grengine gren = new Grengine(scriptDir);
gren.run(new File(scriptDir, "Test.groovy"));
gren.run("println new Util().concat('xx', 'yy')");
```

```
xx:yy
xx:yy
```

By default, changes in the sources in the directory are detected
with a latency of 5 seconds. This includes modifications of file
content, as well as creating and deleting files in the directory.
If changes are detected, all sources in the directory are recompiled,
with dependencies between the scripts fully considered by the compiler.

Example (in Groovy):

```groovy
def utilFile = new File(scriptDir, 'Util.groovy')
def newUtilFile = new File(scriptDir, 'NewUtil.groovy')
def testFile = new File(scriptDir, 'Test.groovy')
gren.run(testFile)
utilFile.delete()
newUtilFile.setText('class Util { def concat(def a, def b) { return "$a--$b" } }')
testFile.setText('println new Util().concat("aa", "bb")')
gren.run(testFile)
Thread.sleep(6000)
gren.run(testFile)
```

```
xx:yy
xx:yy
aa--bb
```

By default, only files with extension `.groovy` in the script directory
are considered and subdirectories are ignored.
Optionally, you can change both, as follows:

```groovy
def config = new CompilerConfiguration()
config.setScriptExtensions([ "groovy", "funky" ] as Set)
def gren = new Grengine(config, scriptDir, DirMode.WITH_SUBDIRS_RECURSIVE)
```

### Script Dependencies

There are again quite a few differences between what Grengine does and
what different classes in the Groovy JDK do in similar situations.
Let's assume again that there are the two files "Util.groovy" and "Test.java"
in the current working directory.
With a `GroovyShell` from the Groovy JDK:

```groovy
def shell = new GroovyShell()
shell.parse('Util.groovy')
shell.evaluate('Test.groovy')
```

"xx:yy" is printed, but if you try the same with a default Grengine
(one that is not directory-based):

```groovy
def gren = new Grengine()
gren.load('Util.groovy')
gren.run('Test.groovy')
```

execution fails at the last line with a `CompileException` stating that
the class `Util` could not be resolved.

Why?

Grengine strictly separates between scripts in its "container", i.e.
scripts that are defined for the Grengine when it is created, and
scripts that are given to be run (or created or loaded) by the
Grengine at runtime.

The latter scripts run each in their own individual class loader.
They all share the same parent class loader, which includes all of the
compiled "container" script classes, but they do not see each other's
classes.
These individual class loaders are managed by what is called a
`TopCodeCache` in Grengine.

This more structured approach has some advantages.

The approach in the Groovy JDK's `GroovyShell` is well suited for interactive
use, where you may usually want to be able to add code script by script.
Beyond that, depending on the use case, this behavior may be more
problematic:

* Thread-safety: Which thread comes first can in general influence
  behavior in calls in other threads.
* Script dependencies: For example, two classes in separate scripts
  may refer to each other; this cannot be handled with sequential calls.
  
The correct handling of dependencies between scripts is also a (minor) issue
if you add a script directory to a `GroovyClassLoader`, but that approach
already covers more cases in practice. For example

```groovy
def loader = new GroovyClassLoader()
loader.addClasspath('.')
def clazz = loader.loadClass('Test')
clazz.newInstance().run()
```

prints out "xx:yy". The `GroovyClassLoader` tries to load classes by name,
i.e. because `Test.groovy` references a class `Util`, the loader searches
for a file `Util.groovy` in its classpath and, if found, compiles it and
loads the class.
This works only if the file name matches the class name. For example, in Groovy
a file `Extras.groovy` might contain several non-inner classes, including
`Util` (which is not possible in Java) - in that case the loader would not
find the class `Util` (unless `Test.groovy` or another of its dependencies
would first refer to a class `Extras` and there was a class `Extras` in
`Extras.groovy`).

If you need 100% correct handling of dependencies using the Groovy JDK,
you use a `GroovyScriptEngine`, but then you are limited to running only
the scripts that are defined for the engine.

Grengine allows to do both, and more, as will be shown shortly.

For the moment note that you can simply use Grengine as the parent class
loader of a `GroovyShell` or `GroovyClassLoader` etc.:

```groovy
def gren = new Grengine(new File('.'))
def shell = new GroovyShell(gren.asClassLoader())
shell.evaluate("println new Util().concat('aa', 'bb')")
```

Or you can have it the other way round, i.e. use a `GroovyClassLoader`
as the parent class loader of Grengine:

```groovy
def loader = new GroovyClassLoader()
loader.addClasspath('.')
def gren = new Grengine(loader)
def clazz = gren.loadClass('Test')
clazz.newInstance().run()
```

Since with Grengine you can add more controlled sets of Groovy sources
"between" the top Grengine API and the `GroovyClassLoader` you can often
have both the flexibility of the Groovy JDK and the control and additional
features of Grengine, depending on what you need.

### Sources Layers

In general, a Grengine's "container" scripts can consist of any number
of layers of sources:

```java
List<Sources> sourcesLayers = ...;
Grengine gren = new Grengine.Builder()
        .setSourcesLayers(sourcesLayers)
        .build();
```

These sources are compiled layer by layer and each layers implicitly gets its
own class loader instance.
The lowest layer can only see its scripts (and all classes in the parent
class loader).
The next layer can see its scripts and everything below, and so on.
Each class loader in the top code cache can see all of that and its own script.

```
----- ----- ----- ----- -----   top code cache       |
-----------------------------   sources layer n      |
-----------------------------   sources layer n-1    |  Grengine
-----------------------------   ...                  |
-----------------------------   sources layer 2      |
-----------------------------   sources layer 1      |
-----------------------------   parent class loader
-----------------------------   ...
-----------------------------   root class loader
```

Now, it can happen – by accident or by design – that a class with
the same name appears more than once in different class loaders
in this layered structure.

Which class should be loaded?

Traditionally, in Java it was recommended to load from the lowest possible
class loader, i.e. "parent-first", also in order to economize resources.
Nowadays the opposite, let me call it "current-first", is not uncommon.
For example, some Java web application containers prefer to load classes from
the webapp first before loading classes from the container.
In general, "parent-first" is maybe more suited in "static" setups and
"current-first" more in "dynamic" setups, like maybe also often with Groovy
scripts.

In Grengine, the default for sources layers is "current-first", but for
the top code layer it is "parent-first", in order to give the precompiled
layers (with their full dependency awareness) precedence over a dynamically
compiled version.

In other words, for a directory-based Grengine:

```java
File scriptDir = new File(".");
Grengine gren = new Grengine(scriptDir);
gren.run(new File(scriptDir, "Test.groovy"));
gren.run(new File(someOtherScriptDir, "Test2.groovy"));
```

"Test.groovy" is run from the compiled code in the only sources layer
and no extra copy will be made in the top code cache.
"Test2.groovy", in turn, is compiled and made part of the top code cache.
In terms of latency, this means, in this case, that updates to "Test.groovy"
will have a latency of 5 seconds, but for "Test2.groovy", it will only be
the latency of `File.lastModified()`.

#### The Sources Interface

The interface that abstracts sources has essentially the following methods:

```java
// Sources:
Set<Source> getSourceSet();
long getLastModified();
String getName();
CompilerFactory getCompilerFactory();
```

The first method gets the set of `Source` instances contained in the `Sources`.
Depending on the implementation, this set may change or not.
For example, if sources are based on a directory and script files are deleted
or created, the set will change.
If so or if the `lastModified` of any of the `Source` instances changes, the
method `getLastModified()` will return a new value, although typically with
a configurable latency.
Providing a name is optional in all provided implementations and (unlike the
ID of a `Source`) is not required to be unique. It is recommended, though,
to choose a name that helps a human reader to identify the `Sources` instance.
The compiler factory allows, for example, to define a separate
compiler configuration for each layer.

#### DirBasedSources

Here's how to construct a `Sources` instance based on a directory,
with all possible options set:

```java
Sources dirBasedSources = new DirBasedSources.Builder(dir)
        .setDirMode(DirMode.WITH_SUBDIRS_RECURSIVE)
        .setScriptExtensions("groovy", "funky")
        .setName("dirbased")
        .setCompilerFactory(new DefaultGroovyCompilerFactory())
        .setSourceFactory(new DefaultSourceFactory())
        .setLatencyMs(200)
        .build();
```

The given source factory is used to create `Source` instances from script
files.

#### FixedSetSources

Here's how to construct a `Sources` instance based on a fixed set of
`Source` instances, with all possible options set:

```java
Set<Source> sourceSet = ...;
Sources fixedSetSources = new FixedSetSources.Builder(sourceSet)
        .setName("fixed")
        .setCompilerFactory(new DefaultGroovyCompilerFactory())
        .setLatencyMs(200)
        .build();
```

#### CompositeSources

Here's how to construct a `Sources` instance based on a collection
of `Sources` instances, with all possible options set:

```java
Collection<Sources> sourcesCollection = ...;
Sources compositeSources = new CompositeSources.Builder(sourcesCollection)
        .setName("composite")
        .setCompilerFactory(new DefaultGroovyCompilerFactory())
        .setLatencyMs(200)
        .build();
```

Note that since `CompositeSources` implements `Sources`, `CompositeSources`
may be arbitrarily nested.
And, of course, the concept is extensible, you may implement additional
classes that implement `Sources` and compose them into a collection, too.

#### Source/Sources Utilities

See `SourceUtil` and `SourcesUtil` for some static utility methods that are
especially useful in Java, where dealing with sets and collections is usually
more cumbersome than in Groovy.
Some examples:

```java
Set<Source> sourceSet;
sourceSet = SourceUtil.filesToSourceSet(file1, file2, file3);
sourceSet = SourceUtil.filesToSourceSet(sourceFactory, file1, file2);
sourceSet = SourceUtil.urlsToSourceSet(url1, url2, url3);
sourceSet = SourceUtil.sourceArrayToSourceSet(source1, source2, source3);
Sources sources;
sources = SourcesUtil.sourceSetToSources(sourceSet, "name");
sources = SourcesUtil.sourceSetToSources(sourceSet, "name", compilerFactory);
```

### Container Maintenance

When you create a Grengine based on sources layers and compilation fails,
you get an exception immediately.
Later on, if sources have changed and no longer compile without errors,
you get no exception when using Grengine, instead the last state of Grengine
where compilation worked remains in use.

If you want to know if compilation of sources layers failed, you have two
options. Either you call:

```java
GrengineException e = gren.getLastException();
```

or you register a callback when creating the engine. For that you have
to implement the interface `UpdateExceptionNotifier`:

```java
void notify(GrengineException updateException);
```

and register it when creating the Grengine:

```java
UpdateExceptionNotifier notifier = new MyUpdateExceptionNotifier();
Grengine gren = new Grengine.Builder()
        .setSourcesLayers(sourcesLayers)
        .setUpdateExceptionNotifier(notifier)
        .build();
```

Note that there are no additional threads in a Grengine. The Grengine
only checks for updated sources when you call any of its methods that
require it to do so, like load/create/run.

In addition to compilation errors, you can optionally also prohibit
duplicate classes with the same name, within the sources layers or
between the sources layers and the parent class loader:

```java
Grengine gren = new Grengine.Builder()
        .setEngine(new LayeredEngine.Builder()
                .setAllowSameClassNamesInMultipleCodeLayers(false)
                .setAllowSameClassNamesInParentAndCodeLayers(false)
                .build()
        .setSourcesLayers(sourcesLayers)
        .build();
```

If set like this, class name conflicts lead to a `ClassNameConflictException`
at compile time, which is a subclass of `GrengineException`

### Grengine Exceptions

Grengine defines its own `GrengineException`. Nothing special, except maybe
that it also declares a method that allows to obtain the date and time the
exception had been thrown:

```java
Date date = new GrengineException().getDateThrown();
```

The following exceptions are subclasses of `GrengineException`:

* `CompileException`\
  Exception thrown when compilation failed.
  Has a method `Sources getSources()` that provides the sources
  that failed to compile.
* `LoadException`\
  Exception thrown when loading a class failed.
* `CreateException`\
  Exception thrown when creating an instance of `groovy.lang.Script`
  failed.
* `ClassNameConflictException`\
  Exception optionally thrown if code layers or code layers and parent
  class loader contain classes with the same name.
  Has two extra methods that provide information about which classes
  in which layers had the same name.

## Advanced Usage

### Session Separation

Grengine provides a unique feature that is difficult to achieve
with the Groovy JDK, except in simple cases:
The same compiled bytecode, including all compiled sources layers of
a Grengine and the top code cache can be shared in multiple, completely
isolated "sessions".

Suppose a web application allows its administrator to configure a
login with some Groovy scripts and the administrator, not much of
a programmer, more a scripter, writes and configures a simple static
utility class like this one:

```groovy
class LoginUtil {
  static String username
  static String password
  static boolean login() {
    def success = false
    // do login in some way, using username and password
    return success
  }
}
```

Now, suppose there is a shared `GroovyShell` for all user sessions
in the web application and the directory that contains "LoginUtil.groovy"
has been added to the `GroovyClassLoader` of the `GroovyShell`.
Finally, during each login, configured scripts like these are run:

```groovy
def username = ...
def password = ...
LoginUtil.username = username
LoginUtil.password = password

[source,groovy]
def success = LoginUtil.login()
if (success) {
  // ...
} else {
  // ...
}
```

Now, if several users log in at the same time, it can happen that
username and password set for one user are overwritten by
the ones for another user before `Util.login()` is called for the
first user, so that in the end the first user has successfully
logged in as the second user!

With the Groovy JDK, you could use separate instances of `GroovyShell`
for each session, which would mean that all scripts would have to be
compiled for each session.
Or you could have a master `GroovyClassLoader` that has a target directory
set in its `CompilerConfiguration` and then add the target directory to the
classpath of a slave `GroovyClassLoader` instance per session.

With Grengine, you can use separate class loaders based on the same
compiled byte code with a single Grengine instance.
You can choose between "attached" loaders that are automatically updated
when the Grengine's sources layers change and all share a top code cache,
or you can have "detached" loaders that remain constant during the session,
i.e. compiled sources layers remain constant during the lifetime of the
loader and have a top code cache only shared with loaders that have the
same compiled sources layers.

```java
Loader loader = gren.getLoader();
Loader loader1 = gren.newAttachedLoader();
Loader loader2 = gren.newDetachedLoader();
gren.run("return 2");
gren.run(loader, "return 2");
gren.run(loader1, scriptFile, binding);
gren.create(loader2, scriptUrl);
```

All variations of load/create/run can optionally have a loader as its
first parameter.
If not indicated, the default loader is used, an attached loader that
can be obtained with `gren.getLoader()`.

Note that the `Loader` class is an opaque wrapper around an actual class
loader.

#### Alternative Session Separation

The following code addresses the issue of shared static variables differently,
namely by not allowing static (non-final) variables in Groovy sources or
issuing a warning etc., with a `CompilationCustomizer` like this one:

```groovy
class NoStaticCompilationCustomizer extends CompilationCustomizer {

  NoStaticCompilationCustomizer() { super(CompilePhase.CANONICALIZATION) }

  void call(SourceUnit source, GeneratorContext context, ClassNode classNode)
      throws CompilationFailedException {
    classNode.fields.each { field ->
      if (Modifier.isStatic(field.modifiers) && !Modifier.isFinal(field.modifiers)) {
        // throw or warn, etc.
      }
    }
  }
}
```

```groovy
def config = new CompilerConfiguration()
config.addCompilationCustomizers(new NoStaticCompilationCustomizer())
```

So, instead of isolating static variables in different class loaders, the approach
here is to use just one class loader and not to let the static variables be created
in the first place, or at least make operators aware of potential security issues.

This puts an additional burden on administrators, namely to check script validity
and to know how to refactor Groovy sources when needed, and it is also somewhat less
robust against unintended leaks between sessions, because even static _final_
variables can be modified from different sessions, depending on their type, a `Map`,
for example. The latter issue could be covered to some degree with an extended
CompilationCustomizer, but this would again add complexity that administrators
would have to understand and know how to refactor.

On the other hand, this workaround is by design faster than multiple class loaders
and essentially free of the garbage collection issues described in the next section.

### Class Loading and Garbage Collection

Although loading classes from bytecode obtained from compiling Groovy scripts
is a lot less expensive than compiling them (plus afterwards also loading the
resulting bytecode), it is still somewhat more expensive than one might naively
expect and there are a few things to be aware of when operating that way.

In the following, I will simply call classes compiled by the Groovy compiler
from Groovy scripts/sources _Groovy classes_ and classes compiled by the Java
compiler from Java sources _Java classes_.

* ***Class Loading***\
  Experimentally, loading of a typical Groovy class is often about 10 times
  slower than loading a Java class with similarly complex source code, but
  both are relatively expensive operations (of the order of a millisecond
  for a small Groovy class, to give a rough indication). For Java classes,
  this is apparently mainly expensive because some security checks have to
  be made on the bytecode. For Groovy classes, it is mainly expensive
  because some metadata is needed to later efficiently call methods
  dynamically, and the like.
* ***Garbage Collection***\
  Classes are stored in _PermGen_ (up to Java 7) resp. _Metaspace_ (Java 8
  and later) plus some associated data on the Heap, at least for Groovy
  classes the latter is normally the case (metadata). Whereas for Java
  classes, unused classes appear to be usually garbage collected from
  PermGen/Metaspace continuously, with Groovy classes this typically does
  not happen before PermGen/Metaspace or the Heap reach a configured limit.
  The reasons for that are the technical complexities of a dynamic language
  paired with Java VM restrictions and bugs, performance requirements (fast
  access to metadata from the class) and remaining backwards compatible
  with previous Groovy versions (except when making a major release).
  Note that by default on Java VMs there is typically no limit set for
  Metaspace (but there is for PermGen), so setting a limit is crucial in
  practice when using Groovy.
* ***Garbage Collection Bugs***\
  In the past, several Groovy versions had failed at garbage collecting
  Groovy classes and their class loaders, resulting finally in an
  `OutOfMemoryError` due to exhaustion of PermGen/Metaspace or the Heap,
  whichever limit was reached first. From Groovy 2.4.0 to 2.4.7 you had to
  make sure you set the system property `groovy.use.classvalue=true` in the
  context of Grengine (or when using the Groovy JDK to compile and run
  scripts). Note that under different circumstances, like the
  one described in [GROOVY-7591](https://issues.apache.org/jira/browse/GROOVY-7591):
  Use of ClassValue causes major memory leak] you would instead have had to
  set it to false! That Groovy bug is actually in turn due to an issue in
  Oracle/OpenJDK Java VMs regarding garbage collection under some
  circumstances, more precisely a general issue that also affects a new
  feature (`ClassValue`) introduced in order to make thing easier(!) for
  dynamic languages in the Java VM, see
  [JDK-8136353](https://bugs.openjdk.java.net/browse/JDK-8136353).

In a setup in which you don't know when a loaded class will not be needed
any more, and you want or need to load many Groovy classes repeatedly,
first set a limit on PermGen/Metaspace, then verify that classes can be
garbage collected once the limit is reached and that throughput is sufficient
for your needs (despite the relatively slow class loading performance of
Groovy (and Java) classes in the Java VM). And don't forget to repeat this
at least when you upgrade Groovy to a new version, but probably also when
you upgrade Java.

In a setup in which you know exactly when you won't need a Grengine or a
Loader any more (including all the classes it ever loaded), you can explicitly
make it available by calling its `close()` method.

Example 1:

```java
Grengine gren = new Grengine();
gren.run("int x=0; [1,2,3].each { x+=it }; x");
gren.close();
```

Example 2:

```java
Grengine gren = new Grengine();
Loader loaderA = gren2.newAttachedLoader();
gren.run(loaderA, "int x=0; [1,2,3].each { x+=it }; x");
loaderA.close();
Loader loaderD = gren2.newDetachedLoader();
gren.run(loaderD, "int x=0; [1,2,3].each { x+=it }; x");
loaderD.close();
gren.close();
```

This eliminates all the `OutOfMemoryError` issues described above. With Oracle
Java 8 (and apparently with Oracle Java 6 and 7 on Windows) this leads
generally to "on-the-fly" garbage collection, i.e. classes and their loaders
are generally already collected before any limit on PermGen/Metaspace or Heap
is reached. On VMs in which this is not the case, garbage collection when the
limit is reached causes no noticeable delay, as opposed to when not closing,
where the delay can easily be several seconds in which the VM does not respond
to anything at all...

Finally, note that you can even provide a custom cleanup function, just implement
the `ClassReleaser` interface and set it in the `Engine`.

### Grengine and Grape

The ability to get dependencies from a Maven repository (or similar),
at *runtime*,  including transitive dependencies, which *Grape* offers,
is a pretty much unique and cool feature that almost only Groovy offers
so easily:

```groovy
@Grab('com.google.guava:guava:18.0')
import com.google.common.base.Ascii
println "Grape: 'C' is upper case: ${Ascii.isUpperCase('C' as char)}"
```

With Grengine this does not work if you just create e.g. a Grengine instance
with `new Grengine()`, because Grape only works if there is a `GroovyClassLoader`
(or a `RootLoader`) somewhere up in the class loader parent hierarchy.
(The workaround `@GrabConfig(systemClassLoader=true)` before a grab does not
always help, most prominently it fails in a webapp container like Tomcat.)
In addition, in the case of Grengine, that GroovyClassLoader would not be the
one that was used to compile the sources, which can lead to race conditions
when loading classes from bytecode, because the Grape dependencies are added
to the classpath in a static initializer, which may or may not run before
classes from those dependencies are attempted to be loaded by the Java VM.
(This is a general issue that affects loading of any classes compiled from
sources that grab dependencies with Grape, see
[GROOVY-8108](https://issues.apache.org/jira/browse/GROOVY-8108).)

Moreover, there is an open bug in Groovy Grape,
[GROOVY-7407](https://issues.apache.org/jira/browse/GROOVY-7407),
which is hard to fix in full generality. Namely, grabs are only thread-safe
if they all go through the same GroovyClassLoader.
They are not if you use different GroovyClassLoader instances, and also not
across different class loaders for the Grape classes or different Java VMs
([GROOVY-8097](https://issues.apache.org/jira/browse/GROOVY-8097)).

Grengine provides easy support for alleviating GROOVY-7407 in practice, except
across different Java VMs, and prevents GROOVY-8108 from affecting Grengine.

Optionally the `GrapeEngine` in the `Grape.class`, which is obtained with
`Grape.getInstance()` -- and so far is always an instance of a class called
`GrapeIvy` (using Apache Ivy to resolve dependencies) -- is wrapped with a
Grengine-specific instance that locks all grabs on `Grape.class` or on a
freely eligible lock object and passes on all calls to the original
`GrapeEngine` instance.
For example, if you wanted to safely use Grape across different webapps in a
Tomcat, the webapps might lock on some rather unusual class in the Java JDK,
instead of on `Grape.class`, which would typically be separately loaded classes
if the Groovy JAR is part of each webapp and not installed at the Tomcat level.
Also part of the wrapper is a mechanism where you can optionally pass the runtime
GroovyClassLoader while compiling via a `CompilationCustomizer`, with the effect
that grabs are made on both the runtime class loader and the compile time class
loader, thus eliminating GROOVY-8108.

In practice, things are quite easy to use.
For a Grengine that uses Grape and is based of sources in a given directory,
instead of

```java
Grengine gren = new Grengine(dir);
```

you would do this:

```java
Grengine.Grape.activate();
Grengine gren = Grengine.Grape.newGrengine(dir)
```

The first call wraps the `GrapeEngine` in the `Grape` class, which has a
"global" impact on all Groovy scripts and classes that grab dependencies,
but this does no harm to others, in fact it has no effect except on them
except that it eliminates the GROOVY-7407 issue within the scope of the
loaded `Grape.class`.
(With the exception of performance: If one grab takes a few seconds because
it has to download a dependency from a remote repository, any other scripts
that want to grab, too, must wait. On the other hand, if those scripts would
not wait, their grabs might fail or even get Grape into a state in which
grabbing would not work any more until exiting the Java VM.)

The above shortcut works with all convenience Grengine constructors, the
ones from directories, a collection of URLs or without any sources layers.
To deactivate wrapping again, simply call:

```java
Grengine.Grape.deactivate();
```

If you want to use a different lock, use:

```java
Grengine.Grape.activate(myLock);
```

In more sophisticated use cases where you define the elements of the
Grengine in more detail, you can directly use the `DefaultGroovyCompiler`
class.
The methods `enableGrapeSupport()` and `disableGrapeSupport()` have
exactly the same effect als the activate/deactivate methods mentioned
above.
The only thing you usually have to do in addition, is to modify the
`CompilerConfiguration` with a call like this, where `runtimeLoader`
would be the parent loader of the `Engine` you create:

```java
GroovyClassLoader runtimeLoader = ...;
CompilerConfiguration config = ...;
DefaultGroovyCompiler.withGrape(config, runtimeLoader);
```

The compiler configuration is then set in the `CompilerFactory`, which,
in turn, is used for the `Sources` and the `TopCodeCache` of the `Engine`.
Here is a real example in Groovy (from [Jexler](https://grengine.ch/jexler/)):

```groovy
private Grengine createGrengine() {

  // setting most things explicitly even if would be default value anyway

  // for Grape to work, a GroovyClassLoader must be a parent loader
  final GroovyClassLoader runtimeLoader = new GroovyClassLoader()
  Grengine.Grape.activate()
  //System.setProperty('groovy.grape.report.downloads', 'true')
  //System.setProperty('ivy.message.logger.level', '4')

  final CompilerConfiguration config = new CompilerConfiguration().with {
    optimizationOptions.put(INVOKEDYNAMIC, true)
    targetBytecode = JDK8
    addCompilationCustomizers(new ImportCustomizer().with {
      addStarImports('ch.grengine.jexler', 'ch.grengine.jexler.service', 'ch.grengine.jexler.tool')
    })
  }
  DefaultGroovyCompiler.withGrape(config, runtimeLoader)

  final CompilerFactory theCompilerFactory = new DefaultGroovyCompilerFactory(config)

  final Grengine gren = new Grengine.Builder().with {
    sourcesLayers = [(Sources)new JexlerContainerSources.Builder(this).with {
      compilerFactory = theCompilerFactory
      sourceFactory = new DefaultSourceFactory()
      latencyMs = 800
      build()
    }]
    latencyMs = 800
    engine = new LayeredEngine.Builder().with {
      parent = runtimeLoader
      allowSameClassNamesInMultipleCodeLayers = false
      allowSameClassNamesInParentAndCodeLayers = true
      withTopCodeCache = true
      topLoadMode = LoadMode.PARENT_FIRST
      topCodeCacheFactory = new DefaultTopCodeCacheFactory.Builder().with {
        compilerFactory = theCompilerFactory
        build()
      }
      build()
    }
    build()
  }

  final GrengineException lastUpdateException = gren.lastUpdateException
  if (lastUpdateException != null) {
    trackIssue(this, 'Compiling container sources failed at startup' +
        ' - utility classes are not available to jexlers.', lastUpdateException)
  }

  return gren
}
```

Note that there are only two things done specifically to support Grape here,
the activation call and the call to adapt the compiler configuration which
is then passed to the constructed `CompilerFactory`.

By the way, you might also want to use the activate/deactivate calls simply
to eliminate the GROOVY-7407 issue when using only the Groovy JDK, but
nothing from Grengine except for those calls.

## Grengine as a Framework

This section is mainly for developers interested in the structure of
Grengine and, in particular, in how to modify and extend default
behavior by subclassing existing classes or by (re-)implementing
interfaces.

I will be concise here. See Javadoc and source code for details.

### Compiler, Code and Bytecode

The compiler interface is very simple:

```java
Code compile(Sources sources) throws CompileException;
```

The interface `Code` wraps bytecode plus associated class names,
including the name of the main class per `Source` instance, plus
some information about the `Sources`, namely last modified at
compile time and the sources name.

The class `Bytecode` is just a simple bean that wraps a class name
and its bytecode byte array.

There are two implementations of `Code`, one for an arbitrary number
of `Source` instances in the `Sources` given at compilation,
`DefaultCode`, and `DefaultSingleSourceCode` for a single `Source`
instance. The latter is primarily useful in the context of the
top code cache.

The default implementation of `Compiler` is `DefaultGroovyCompiler`.
It does nothing special, during compilation a GroovyClassLoader is
created, and optionally it writes classes also to a target directory,
if indicated in the compiler configuration.

It is imaginable to implement `Compiler` for other languages, like
Java or Scala.
Difficulties would be to find out the main class name and which
class names come from which source and methods like `gren.run(...)`
would maybe not make that much sense if you had to explicitly
implement `groovy.lang.Script` in Java or Scala scripts, but on
a lower level, you could still use a lot of the automatisms of
Grengine regarding compilation and management of compiled code.

The interface `CompilerFactory` and its default implementation
are straightforward.

Use the static utility methods in `ClassNameConflictAnalyzer` to
check for class name conflicts between different instances of `Code`
or relative to a parent class loader.

### Source-based Class Loaders

The abstract class `SourceClassLoader` extends `ClassLoader`
essentially with the following methods for loading classes by
`Source` and (main) class name:

```java
Class<?> loadMainClass(Source source) throws CompileException, LoadException;
Class<?> loadClass(Source source, String name) throws CompileException, LoadException;
BytecodeClassLoader findBytecodeClassLoaderBySource(Source source);
LoadMode getLoadMode();
```

When loading a class by source, first the matching source is searched
by ID in the class loader hierarchy.
If found there, the main class can be returned or any other class
that resulted from compiling the same source.
Classes not associated with that source or not with any source at all,
are not found this way, only if loaded directly with `loadClass(className)`.

The load mode is an enum with two values, `PARENT_FIRST` and `CURRENT_FIRST`.

The basic implementation of `SourceClassLoader` is `BytecodeClassLoader`. Constructor:

```java
BytecodeClassLoader(ClassLoader parent, LoadMode loadMode, Code code);
```

It can operate in both load modes; I recommend to take a look at the code.

It also contains two static utility methods that are used by other
source class loaders, `loadMainClassBySource(...)` and 
`loadClassBySourceAndName(...)`.

Based on `BytecodeClassLoader` is `LayeredClassLoader`, which contains
several layers of `BytecodeClassLoader`, associated with layers of
`Sources` resp. `Code`, plus optionally a `TopCodeCache`.

The `LayeredClassLoader` can be cloned to copies based on identical
bytecode.

### Engine and Grengine

The interface `Engine` defines the essential functionality for a
Grengine, without all the convenience methods for load/create/run
and without automatic updates of sources layers.
These layers can be updated by providing layers of `Sources` or
layers of already compiled `Code`.

The so far only implementation `LayeredEngine` uses a
`LayeredClassLoader`.
Layers can be updated while the engine is used.

The abstract class `BaseGrengine` implements most of the matrix
of convenience methods for `Grengine`, which extends `BaseGrengine`.
In addition, `Grengine` provides the automatic updates of sources
layers and the callback for update exceptions.

A Grengine can be constructed with a custom `Engine` and
`SourceFactory`, plus the notifier for update exceptions.

### Miscellaneous

Many classes override `toString()` in order to produce strings useful
for logging.
A few classes, including all implementations of `Source`, override
`equals()` and `hashCode()`, so that they can be used as map keys
or in sets.

Many classes have an inner `Builder` class for flexible creation of
instances, as well as to make it easier to add features in the future
with a consistent interface.

## Enjoy!

There is so much yet to explore between the static world of Java
and the dynamically free world of script languages.

Groovy can span it all like no other language.

Especially the Groovy compiler provides fantastic features that allow
to span the whole gap in terms of the language, with optional
static compilation and strong typing and much more...

On the other hand, the Groovy JDK classes like `GroovyShell`,
`GroovyScriptEngine` and even the `GroovyClassLoader` seem to me
to lean more towards the side of dynamic scripting.

I hope you will have fun with Grengine and that it will allow you
to make things with Groovy that are yet unseen!

&ast; Performance on a Java VM depends on lots of parameters.
Beyond the order of magnitude not too much attention should be
given to the informal numbers presented here. Generally, I find it
best to measure performance as closely as possible to an actually
deployed situation and to compare the effect of different
optimization attempts there, simply because there are almost
always surprises in practice.

## Release Notes

Required Java versions in a nutshell:

* Grengine 1: Java 6 or later.
* Grengine 2+3: Java 8 or later.
* Grengine 4: Not released, yet, presumably Java 11 or later (required for Groovy 5).

### Grengine 3

#### 3.1.0 (18 September 2024)

* Changed: Packages back to `ch.grengine` and website back to [grengine.ch](https://grengine.ch/).

#### 3.0.2 (26 February 2023)

* Fix: The workaround for the Grape concurrency issue
  [GROOVY-7407](https://issues.apache.org/jira/browse/GROOVY-7407)
  now also works with Groovy 3 and 4.

#### 3.0.1 (25 February 2023)

* Changed: Dependency to groovy JAR no longer in published pom
  because Groovy 4 has a different group ID (`org.apache.groovy`)
  than earlier Groovy versions (`org.codehaus.groovy`).

#### 3.0.0 (21 January 2019)

* Changed: Packages renamed from `ch.grengine.\*` to
  `ch.artecat.grengine.*` for Grengine's new home at
  artecat.ch/grengine.
* Fix: `ByteCodeClassLoader` internally uses `getDefinedPackage()` if
  available (Java 9 and later), else continues to use `getPackage()`.

### Grengine 2

#### 2.0.0 (28 July 2018)

* Changed: Requires Java 8 or later. Note that package names have remained
  unchanged despite the new major version since Grengine is not very widely
  used and incompatible interface changes are sparse.
  Under the hood, the code has been streamlined by using Java 8 features,
  and unit tests have been significantly regularized and streamlined.
* Changed:
** `GrengineException` and its subclasses are now `RuntimeExceptions`.
** Null method arguments now lead to `NullPointerException` instead
   of `IllegalArgumentException`.
* Removed:
** `SourceUtil#CHARSET_UTF_8` => use `StandardCharsets.UTF_8`.
** `SourceUtil#getTextStartNoLinebreaks()`
   => use `SourceUtil#getTextStartNoLineBreaks()`.
** `SourcesUtil#sourcesArrayToList()` => use `Arrays.asList()`.
** `CodeUtil#codeArrayToList()` => use `Arrays.asList()`.
** `CodeUtil` class (the above was its only method).

### Grengine 1

#### 1.3.0 (20 July 2017)

* New: New methods `asClassLoader()` for `Grengine` and `Engine` that allow
to use a Grengine resp. its engine as parent class loader for `GroovyShell`
or `GroovyClassLoader` (or any other class loader).

#### 1.2.1 (28 April 2017)

* Fix: Java 9 compatibility (removed dependency on `javax.xml.bind package`,
  which is not available by default on Java 9).

#### 1.2.0 (4 March 2017)

* New/Fix: Extended support for Grape with Grengine and an easy-to-use
  workaround for [GROOVY-7407](https://issues.apache.org/jira/browse/GROOVY-7407)
  that can also be used independently when only using the Groovy JDK.

#### 1.1.1 (24 February 2017)

* New: Convenience `loadClass()` and `loadMainClass()` methods in `BaseGrengine`
  for using the default loader.
* Deprecated: Instead of `SourceUtil#getTextStartNoLinebreaks()`, use the new
  method `getTextStartNoLineBreaks()` without the typo.

#### 1.1.0 (8 June 2016)

* New: `Grengine`, `BaseGrengine`, `Engine` and `Loader` now implement the
  `Closable` interface and the `SourceClassLoader` interface now contains
  a similar cleanup method for allowing to make classes and their class
  loaders more easily available for garbage collection when they are no
  longer needed. See the section "Class Loading and Garbage Collection"
  for details.

#### 1.0.6 (4 June 2016)

* Fix: Fixed concurrency issue in top code cache (`LayeredClassLoader`).

#### 1.0.5 (24 October 2015)

* Fix: `DirBasedSources` now treats (sub-)directories that cannot be listed
  as empty, no longer throws a `NullPointerException` in this case.

#### 1.0.4 (23 August 2015)

* Optimization: The `BytecodeClassLoader` class now locks individually per class
  resp. package name when defining classes resp. packages; previously it locked
  on the `BytecodeClassLoader` instance.
* New (documentation): Section about the cost of session separation.

#### 1.0.3 (9 May 2015)

* New: Convenience `Grengine` constructors that allow to set the parent class loader
  of the engine more easily, for easier Grape support.

#### 1.0.2 (11 October 2014)

* Changed: `Grengine` constructors from `CompilerConfiguration` and script directory
  now default to using the script extensions defined in the `CompilerConfiguration`.

#### 1.0.1 (4 October 2014)

* New (performance): `DefaultSourceFactory` options for caching text source ID and
  file source last modified.
* Changed: Slightly changed ID string of `DefaultTextSource` with a desired name.
* New test: Manual test `GrengineVisualPerformanceTest` which prints useful info
  regarding performance.

#### 1.0.0 (29 September 2014)

* First public release.
