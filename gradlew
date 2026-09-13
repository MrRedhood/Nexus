#!/bin/sh

set -e

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)

if [ -n "$JAVA_HOME" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD="java"
fi

if ! command -v "$JAVACMD" >/dev/null 2>&1; then
  echo "ERROR: JAVA_HOME is not set and no 'java' command could be found." >&2
  exit 1
fi

WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_MAIN=org.gradle.wrapper.GradleWrapperMain

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "ERROR: Gradle wrapper JAR is missing." >&2
  echo "Generate the wrapper with a matching Gradle distribution before building." >&2
  exit 1
fi

exec "$JAVACMD" -classpath "$WRAPPER_JAR" "$WRAPPER_MAIN" "$@"
