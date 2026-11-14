#!/bin/sh
# Xcode build phase: compiles the Kotlin `:shared` module into Shared.framework for the
# SDK/architecture Xcode is currently building, then embeds and signs it.
set -e

if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then
  echo "Skipping Gradle build: OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES"
  exit 0
fi

# Xcode does not inherit the shell's JAVA_HOME. Prefer Android Studio's bundled JDK,
# which matches the Gradle daemon toolchain, then fall back to the system default.
if [ -z "$JAVA_HOME" ]; then
  STUDIO_JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  if [ -x "$STUDIO_JBR/bin/java" ]; then
    export JAVA_HOME="$STUDIO_JBR"
  elif /usr/libexec/java_home >/dev/null 2>&1; then
    JAVA_HOME="$(/usr/libexec/java_home)"
    export JAVA_HOME
  fi
fi

cd "$SRCROOT/.."
./gradlew :shared:embedAndSignAppleFrameworkForXcode
