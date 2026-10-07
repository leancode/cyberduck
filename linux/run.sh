#!/bin/bash
# Run the Linux GUI from the build tree. Needs JDK 25 (JAVA_HOME or java on the PATH) and a prior
# `mvn install` of the reactor. Pass application arguments through, for example: linux/run.sh --version
set -e
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
java=java
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    java="$JAVA_HOME/bin/java"
fi
classpath="linux/target/classpath.txt"
if [ ! -s "$classpath" ] || [ linux/pom.xml -nt "$classpath" ]; then
    mvn -q --batch-mode --no-transfer-progress -pl linux dependency:build-classpath -Dmdep.outputFile=target/classpath.txt >&2
fi
exec "$java" --enable-native-access=ALL-UNNAMED -cp "linux/target/classes:$(cat "$classpath")" ch.cyberduck.ui.fx.MainApplication "$@"
