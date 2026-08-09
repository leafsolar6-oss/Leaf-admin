#!/bin/sh
exec java -classpath "$(dirname "$0")/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
