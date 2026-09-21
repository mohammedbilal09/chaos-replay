#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Maven Start Up Batch script
#
# Required ENV Vars:
# ------------------
#   JAVA_HOME - location of a JDK home dir
#
# Optional ENV Vars
# -----------------
#   M2_HOME - location of maven2's installed home dir
#   MAVEN_OPTS - parameters to pass to the Java VM when running Maven
#     e.g. to debug Maven itself, use
#       set MAVEN_OPTS=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000
#   MAVEN_SKIP_RC - flag to disable loading of mavenrc files
# ----------------------------------------------------------------------------

if [ -z "$MAVEN_SKIP_RC" ]; then
  if [ -f /etc/mavenrc ]; then
    . /etc/mavenrc
  fi
  if [ -f "$HOME/.mavenrc" ]; then
    . "$HOME/.mavenrc"
  fi
fi

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false;
darwin=false;
mingw=false
case "`uname`" in
  CYGWIN*) cygwin=true ;;
  Darwin*) darwin=true ;;
  MINGW*)  mingw=true ;;
esac

## Resolve links - $0 may be a link to the script
PRG="$0"

# need this for relative symlinks
while [ -h "$PRG" ] ; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG="`dirname "$PRG"`/$link"
  fi
done

saveddir=`pwd`

MAVEN_PROJECTBASEDIR=`dirname "$PRG"`
export MAVEN_PROJECTBASEDIR

# Need to change to the project directory to read properties correctly
cd "$MAVEN_PROJECTBASEDIR" || exit 1

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
  if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
    # IBM's JDK on AIX uses strange locations for the executables
    JAVACMD="$JAVA_HOME/jre/sh/java"
  else
    JAVACMD="$JAVA_HOME/bin/java"
  fi
  if [ ! -x "$JAVACMD" ] ; then
    echo "The JAVA_HOME environment variable is not defined correctly" >&2
    echo "This environment variable is needed to run this program" >&2
    echo "NB: JAVA_HOME should point to a JDK not a JRE" >&2
    exit 1
  fi
else
  JAVACMD="java"
  which java >/dev/null 2>&1 || (echo "Error: JAVA_HOME is not defined, cannot find \"java\" in your PATH." >&2 && exit 1)
fi

WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"
WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"

if [ ! -f "$WRAPPER_PROPERTIES" ]; then
  echo "Error: Cannot find $WRAPPER_PROPERTIES" >&2
  exit 1
fi

WRAPPER_URL=$(grep '^wrapperUrl=' "$WRAPPER_PROPERTIES" | cut -d'=' -f2-)
DISTRIBUTION_URL=$(grep '^distributionUrl=' "$WRAPPER_PROPERTIES" | cut -d'=' -f2-)

if [ ! -f "$WRAPPER_JAR" ]; then
  mkdir -p "$MAVEN_PROJECTBASEDIR/.mvn/wrapper"
  if [ -n "$WRAPPER_URL" ]; then
    echo "Downloading Maven Wrapper JAR from $WRAPPER_URL..."
    if command -v curl >/dev/null 2>&1; then
      curl -fsSL -o "$WRAPPER_JAR" "$WRAPPER_URL"
    elif command -v wget >/dev/null 2>&1; then
      wget -q -O "$WRAPPER_JAR" "$WRAPPER_URL"
    else
      echo "Error: curl or wget is required to download Maven Wrapper JAR" >&2
      exit 1
    fi
  fi
fi

cd "$saveddir" || exit 1

exec "$JAVACMD" \
  $MAVEN_OPTS \
  -classpath "$WRAPPER_JAR" \
  -Dmaven.multiModuleProjectDirectory="$MAVEN_PROJECTBASEDIR" \
  org.apache.maven.wrapper.MavenWrapperMain \
  "$@"
