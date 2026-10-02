#!/bin/sh
# Minimal launcher: GitHub Actions uses gradle/actions/setup-gradle.
# For local use, install Gradle 8.11.1 and run: gradle <task>
exec gradle "$@"
