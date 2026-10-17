#!/bin/sh
# Gradle wrapper bootstrap for OfflineMessenger.
APP_BASE_NAME=${0##*/}
APP_HOME=$(cd "${0%/*}" 2>/dev/null && pwd -P)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
