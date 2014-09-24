opentripplanner-graph-file-version
==================================

Find an OpenTripPlanner version string from Graph.obj file by searching its serialized data.

To compile/run using Leiningen: 
lein javac
java -cp target/classes GraphFileVersion /path/to/Graph.obj

To compile/run using Java only:

javac GraphFileVersion.java
java GraphFileVersion /path/to/Graph.obj

