Setup is:
ProjectA is built by ProjectAParent
ProjectB is built by ProjectBParent
Both use ${revision]


ProjectA has a dependency to ProjectB

We use a "workspace pom" for Eclipse the references these modules.


To reproduce the error:
 run mvn install on pom.xml



The error log is:

[INFO] Scanning for projects...
[WARNING] 
[WARNING] Some problems were encountered while building the effective model for com.test:civersion-workspace-test:pom:SNAPSHOT
[WARNING] 'version' uses an unsupported snapshot version format, should be '*-SNAPSHOT' instead. @ line 8, column 12
[WARNING] 
[WARNING] It is highly recommended to fix these problems because they threaten the stability of your build.
[WARNING] 
[WARNING] For this reason, future Maven versions might no longer support building such malformed projects.
[WARNING] 
[INFO] --- tiles-maven-plugin: Injecting 1 tiles as intermediary parent artifacts for com.test:civersion-tiletest-projectB...
[INFO] Mixed 'com.test:civersion-tiletest-projectB:null' with tile 'com.test:civersion-tiletest-workspace-tile1:1' as its new parent.
[INFO] Explicitly set version to 'null' from original parent 'com.test:civersion-tiletest-projectBParent:null'.
[INFO] Mixed 'com.test:civersion-tiletest-workspace-tile1:1' with original parent 'com.test:civersion-tiletest-projectBParent:null' as its new top level parent.
[INFO] 
[ERROR] Internal error: java.lang.NullPointerException: Cannot invoke method hashCode() on null object -> [Help 1]
org.apache.maven.InternalErrorException: Internal error: java.lang.NullPointerException: Cannot invoke method hashCode() on null object
    at org.apache.maven.DefaultMaven.execute (DefaultMaven.java:120)
    at org.apache.maven.cli.MavenCli.execute (MavenCli.java:972)
    at org.apache.maven.cli.MavenCli.doMain (MavenCli.java:293)
    at org.apache.maven.cli.MavenCli.main (MavenCli.java:196)
    at sun.reflect.NativeMethodAccessorImpl.invoke0 (Native Method)
    at sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62)
    at sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43)
    at java.lang.reflect.Method.invoke (Method.java:498)
    at org.codehaus.plexus.classworlds.launcher.Launcher.launchEnhanced (Launcher.java:282)
    at org.codehaus.plexus.classworlds.launcher.Launcher.launch (Launcher.java:225)
    at org.codehaus.plexus.classworlds.launcher.Launcher.mainWithExitCode (Launcher.java:406)
    at org.codehaus.plexus.classworlds.launcher.Launcher.main (Launcher.java:347)
Caused by: java.lang.NullPointerException: Cannot invoke method hashCode() on null object
    at org.codehaus.groovy.runtime.NullObject.hashCode (NullObject.java:174)
    at org.codehaus.groovy.runtime.NullObject$hashCode.call (Unknown Source)
    at org.codehaus.groovy.runtime.callsite.CallSiteArray.defaultCall (CallSiteArray.java:47)
    at org.codehaus.groovy.runtime.callsite.NullCallSite.call (NullCallSite.java:34)
    at org.codehaus.groovy.runtime.callsite.CallSiteArray.defaultCall (CallSiteArray.java:47)
    at java_lang_String$hashCode.call (Unknown Source)
    at io.repaint.maven.tiles.NotDefaultModelCache$Key.<init> (NotDefaultModelCache.groovy:54)
    at io.repaint.maven.tiles.NotDefaultModelCache.get (NotDefaultModelCache.groovy:29)
    at org.apache.maven.model.building.DefaultModelBuilder.getCache (DefaultModelBuilder.java:1363)
    at org.apache.maven.model.building.DefaultModelBuilder.readParent (DefaultModelBuilder.java:852)
    at org.apache.maven.model.building.DefaultModelBuilder.build (DefaultModelBuilder.java:344)
    at org.apache.maven.model.building.DefaultModelBuilder.build (DefaultModelBuilder.java:252)
    at org.apache.maven.model.building.ModelBuilder$build.call (Unknown Source)
    at org.codehaus.groovy.runtime.callsite.CallSiteArray.defaultCall (CallSiteArray.java:47)
    at org.codehaus.groovy.runtime.callsite.AbstractCallSite.call (AbstractCallSite.java:125)
    at org.codehaus.groovy.runtime.callsite.AbstractCallSite.call (AbstractCallSite.java:139)
    at io.repaint.maven.tiles.TilesMavenLifecycleParticipant.thunkModelBuilder (TilesMavenLifecycleParticipant.groovy:518)
    at io.repaint.maven.tiles.TilesMavenLifecycleParticipant.orchestrateMerge (TilesMavenLifecycleParticipant.groovy:424)
    at io.repaint.maven.tiles.TilesMavenLifecycleParticipant.afterProjectsRead (TilesMavenLifecycleParticipant.groovy:322)
    at org.apache.maven.DefaultMaven.doExecute (DefaultMaven.java:264)
    at org.apache.maven.DefaultMaven.doExecute (DefaultMaven.java:192)
    at org.apache.maven.DefaultMaven.execute (DefaultMaven.java:105)
    at org.apache.maven.cli.MavenCli.execute (MavenCli.java:972)
    at org.apache.maven.cli.MavenCli.doMain (MavenCli.java:293)
    at org.apache.maven.cli.MavenCli.main (MavenCli.java:196)
    at sun.reflect.NativeMethodAccessorImpl.invoke0 (Native Method)
    at sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62)
    at sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43)
    at java.lang.reflect.Method.invoke (Method.java:498)
    at org.codehaus.plexus.classworlds.launcher.Launcher.launchEnhanced (Launcher.java:282)
    at org.codehaus.plexus.classworlds.launcher.Launcher.launch (Launcher.java:225)
    at org.codehaus.plexus.classworlds.launcher.Launcher.mainWithExitCode (Launcher.java:406)
    at org.codehaus.plexus.classworlds.launcher.Launcher.main (Launcher.java:347)
[ERROR] 
[ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
[ERROR] Re-run Maven using the -X switch to enable full debug logging.
[ERROR] 
[ERROR] For more information about the errors and possible solutions, please read the following articles:
[ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/InternalErrorException
