#!/bin/sh
# Maven Wrapper bootstrap script (Apache Maven Wrapper 3.3.2)
set -e

APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
CLASSPATH="$APP_HOME/.mvn/wrapper/maven-wrapper.jar"

exec java \
    -Dmaven.multiModuleProjectDirectory="$APP_HOME" \
    -cp "$CLASSPATH" \
    org.apache.maven.wrapper.MavenWrapperMain \
    "$@"
