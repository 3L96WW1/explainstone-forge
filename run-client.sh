#!/usr/bin/env bash
# 启动开发客户端：Gradle 用 JDK 21 跑，游戏本体用 JDK 17（toolchain）
# 用法：./run-client.sh        或   JDK17=/path/to/jdk17 ./run-client.sh
set -e
cd "$(dirname "$0")"

# Gradle 本身用 21（17.0.21 的 C2 编译器在 Gradle 配置阶段会偶发段错误）
GRADLE_JAVA="/usr/lib/jvm/java-21-openjdk-amd64"
[ -x "$GRADLE_JAVA/bin/java" ] || GRADLE_JAVA="$JAVA_HOME"

# 游戏用 17
JDK17="${JDK17:-$HOME/java/jdk17}"
[ -x "$JDK17/bin/java" ] || JDK17="/home/karine/Documents/explainstone-template-1.21.1/.toolchain/jdk17"

JAVA_HOME="$GRADLE_JAVA" ./gradlew runClient \
  -Porg.gradle.java.installations.paths="$JDK17" \
  "$@"
