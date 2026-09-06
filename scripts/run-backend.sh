#!/usr/bin/env bash
# Convenience wrapper used during development.
set -euo pipefail
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(dirname "$0")/../backend"
exec mvn spring-boot:run -Dspring-boot.run.profiles="${SPRING_PROFILES_ACTIVE:-local}"
