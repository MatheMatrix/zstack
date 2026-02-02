#!/bin/bash
# Java 17 兼容性验证脚本
# 用于验证所有依赖在Java 17下的兼容性

set -e

echo "=========================================="
echo "  Java 17 兼容性验证"
echo "=========================================="
echo ""

# 1. 检查Java版本
echo "[1/6] 检查Java版本..."
JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
if [ "$JAVA_VERSION" != "17" ]; then
    echo "❌ 错误: 需要Java 17，当前版本: $JAVA_VERSION"
    java -version
    exit 1
fi
echo "✓ Java版本: $(java -version 2>&1 | head -1)"
echo ""

# 2. 检查关键依赖版本
echo "[2/6] 检查关键依赖版本..."
echo "检查Spring Framework版本..."
SPRING_VERSION=$(mvn help:evaluate -Dexpression=spring.framework.version -q -DforceStdout 2>/dev/null || echo "unknown")
echo "  Spring Framework: $SPRING_VERSION"
if [[ "$SPRING_VERSION" == "5.2"* ]]; then
    echo "  ⚠️  警告: Spring 5.2.x 非官方支持Java 17，建议升级到5.3.x"
fi

echo "检查Hibernate版本..."
HIBERNATE_VERSION=$(mvn help:evaluate -Dexpression=hibernate.version -q -DforceStdout 2>/dev/null || echo "unknown")
echo "  Hibernate: $HIBERNATE_VERSION"

echo "检查AspectJ版本..."
ASPECTJ_VERSION=$(mvn help:evaluate -Dexpression=aspectj.version -q -DforceStdout 2>/dev/null || echo "unknown")
echo "  AspectJ: $ASPECTJ_VERSION"

echo "检查Groovy版本..."
GROOVY_VERSION=$(mvn help:evaluate -Dexpression=groovy.version -q -DforceStdout 2>/dev/null || echo "unknown")
echo "  Groovy: $GROOVY_VERSION"
echo ""

# 3. 编译测试（核心模块）
echo "[3/6] 编译核心模块 (core, header, utils)..."
if mvn clean compile -pl core,header,utils -DskipTests -q; then
    echo "✓ 核心模块编译成功"
else
    echo "❌ 错误: 核心模块编译失败"
    exit 1
fi
echo ""

# 4. 编译测试（所有模块）
echo "[4/6] 编译所有模块..."
if mvn clean compile -DskipTests -q; then
    echo "✓ 全量编译成功"
else
    echo "❌ 错误: 全量编译失败"
    exit 1
fi
echo ""

# 5. 单元测试（工具类）
echo "[5/6] 运行单元测试 (utils, abstraction)..."
if mvn test -pl utils,abstraction -Djacoco.skip=true -q 2>&1 | tee /tmp/java17-test.log; then
    echo "✓ 单元测试通过"
else
    TEST_FAILED=$(grep -c "FAILURE\|ERROR" /tmp/java17-test.log || echo "0")
    if [ "$TEST_FAILED" -gt 0 ]; then
        echo "⚠️  警告: 部分单元测试失败，请检查日志"
    else
        echo "✓ 单元测试完成"
    fi
fi
echo ""

# 6. 依赖兼容性检查
echo "[6/6] 检查依赖树（Spring/Hibernate相关）..."
echo "Spring相关依赖:"
mvn dependency:tree -Dincludes=org.springframework:* -q 2>/dev/null | grep -E "spring" | head -10 || echo "  无Spring依赖"
echo ""
echo "Hibernate相关依赖:"
mvn dependency:tree -Dincludes=org.hibernate:* -q 2>/dev/null | grep -E "hibernate" | head -10 || echo "  无Hibernate依赖"
echo ""

echo "=========================================="
echo "  验证完成"
echo "=========================================="
echo ""
echo "建议:"
if [[ "$SPRING_VERSION" == "5.2"* ]]; then
    echo "  - 考虑升级Spring Framework到5.3.23以获得官方Java 17支持"
fi
echo "  - 运行完整集成测试: mvn test -pl test -Dtest=SomeBasicCase"
echo "  - 检查运行时兼容性: 启动应用并测试核心功能"
echo ""
