#!/bin/sh
APP_HOME=$( cd -P "${0%"${0##*/}"}" > /dev/null && printf '%s\n' "$PWD" ) || exit
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
JAVACMD=${JAVA_HOME:+$JAVA_HOME/bin/}java
exec "$JAVACMD" -Xmx64m -Xms64m \
    -Dorg.gradle.appname="${0##*/}" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
