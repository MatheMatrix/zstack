#!/bin/bash

# 批量修复Groovy编译配置脚本
# 用于将groovy-eclipse-compiler替换为标准Java编译器或GMavenPlus

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_ROOT"

echo "=========================================="
echo "批量修复Groovy编译配置"
echo "=========================================="
echo ""

# 查找所有包含groovy-eclipse-compiler的pom.xml文件
FILES=$(grep -r "groovy-eclipse-compiler" --include="pom.xml" . | cut -d: -f1 | sort -u)

TOTAL=$(echo "$FILES" | wc -l | tr -d ' ')
CURRENT=0
FIXED=0
SKIPPED=0

# 标准Java编译器配置（用于没有Groovy源文件的模块）
STANDARD_JAVA_CONFIG='                <configuration>
                    <source>${project.java.version}</source>
                    <target>${project.java.version}</target>
                    <debug>true</debug>
                    <parameters>true</parameters>
                </configuration>'

# GMavenPlus配置（用于有Groovy源文件的模块）
GMAVENPLUS_CONFIG='            <!-- 标准Java编译器 -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>${project.compiler.version}</version>
                <configuration>
                    <source>${project.java.version}</source>
                    <target>${project.java.version}</target>
                    <debug>true</debug>
                    <parameters>true</parameters>
                </configuration>
            </plugin>
            <!-- GMavenPlus 用于编译Groovy代码 -->
            <plugin>
                <groupId>org.codehaus.gmavenplus</groupId>
                <artifactId>gmavenplus-plugin</artifactId>
                <version>4.2.1</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>generateStubs</goal>
                            <goal>compile</goal>
                            <goal>generateTestStubs</goal>
                            <goal>compileTests</goal>
                        </goals>
                    </execution>
                </executions>
                <configuration>
                    <groovyVersion>${groovy.version}</groovyVersion>
                    <targetBytecode>${project.java.version}</targetBytecode>
                </configuration>
            </plugin>'

for FILE in $FILES; do
    CURRENT=$((CURRENT + 1))
    echo "[$CURRENT/$TOTAL] 处理: $FILE"
    
    # 检查模块目录是否有Groovy源文件
    MODULE_DIR=$(dirname "$FILE")
    HAS_GROOVY=$(find "$MODULE_DIR" -name "*.groovy" -type f 2>/dev/null | head -1)
    
    if [ -n "$HAS_GROOVY" ]; then
        echo "  ⚠️  发现Groovy源文件，需要手动配置GMavenPlus"
        echo "  📝 请手动编辑此文件，参考test/pom.xml的配置"
        SKIPPED=$((SKIPPED + 1))
        continue
    fi
    
    # 读取文件内容
    if [ ! -f "$FILE" ]; then
        echo "  ❌ 文件不存在，跳过"
        SKIPPED=$((SKIPPED + 1))
        continue
    fi
    
    # 创建备份
    BACKUP_FILE="${FILE}.backup.$(date +%Y%m%d_%H%M%S)"
    cp "$FILE" "$BACKUP_FILE"
    echo "  💾 备份文件: $BACKUP_FILE"
    
    # 使用Python进行精确替换（因为sed处理多行和缩进比较复杂）
    python3 <<PYTHON_SCRIPT
import re
import sys

file_path = "$FILE"

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 匹配groovy-eclipse-compiler配置块
# 匹配从<plugin>开始到</plugin>结束，包含groovy-eclipse-compiler的整个块
pattern = r'''\s*<plugin>\s*
\s*<groupId>org\.apache\.maven\.plugins</groupId>\s*
\s*<artifactId>maven-compiler-plugin</artifactId>\s*
\s*<version>.*?</version>\s*
\s*<configuration>\s*
\s*<compilerId>groovy-eclipse-compiler</compilerId>\s*
\s*<source>\$\{project\.java\.version\}</source>\s*
\s*<target>\$\{project\.java\.version\}</target>\s*
\s*<debuglevel>lines,vars,source</debuglevel>\s*
\s*<debug>true</debug>\s*
\s*</configuration>\s*
\s*<dependencies>\s*
\s*<dependency>\s*
\s*<groupId>org\.codehaus\.groovy</groupId>\s*
\s*<artifactId>groovy-eclipse-compiler</artifactId>\s*
\s*<version>.*?</version>\s*
\s*</dependency>\s*
\s*<dependency>\s*
\s*<groupId>org\.codehaus\.groovy</groupId>\s*
\s*<artifactId>groovy-eclipse-batch</artifactId>\s*
\s*<version>.*?</version>\s*
\s*</dependency>\s*
\s*</dependencies>\s*
\s*</plugin>'''

# 更灵活的匹配模式（处理不同的缩进和换行）
pattern2 = r'''(\s*)<plugin>\s*
\1<groupId>org\.apache\.maven\.plugins</groupId>\s*
\1<artifactId>maven-compiler-plugin</artifactId>\s*
\1<version>([^<]+)</version>\s*
\1<configuration>\s*
\1\s*<compilerId>groovy-eclipse-compiler</compilerId>\s*
\1\s*<source>\$\{project\.java\.version\}</source>\s*
\1\s*<target>\$\{project\.java\.version\}</target>\s*
\1\s*<debuglevel>lines,vars,source</debuglevel>\s*
\1\s*<debug>true</debug>\s*
\1</configuration>\s*
\1<dependencies>\s*
\1\s*<dependency>\s*
\1\s*<groupId>org\.codehaus\.groovy</groupId>\s*
\1\s*<artifactId>groovy-eclipse-compiler</artifactId>\s*
\1\s*<version>[^<]+</version>\s*
\1\s*</dependency>\s*
\1\s*<dependency>\s*
\1\s*<groupId>org\.codehaus\.groovy</groupId>\s*
\1\s*<artifactId>groovy-eclipse-batch</artifactId>\s*
\1\s*<version>[^<]+</version>\s*
\1\s*</dependency>\s*
\1</dependencies>\s*
\1</plugin>'''

# 尝试匹配并替换
def replace_plugin(match):
    indent = match.group(1)
    version = match.group(2).strip()
    replacement = f'''{indent}<plugin>
{indent}    <groupId>org.apache.maven.plugins</groupId>
{indent}    <artifactId>maven-compiler-plugin</artifactId>
{indent}    <version>{version}</version>
{indent}    <configuration>
{indent}        <source>${{project.java.version}}</source>
{indent}        <target>${{project.java.version}}</target>
{indent}        <debug>true</debug>
{indent}        <parameters>true</parameters>
{indent}    </configuration>
{indent}</plugin>'''
    return replacement

# 使用更简单的方法：逐行处理
lines = content.split('\n')
new_lines = []
i = 0
in_plugin = False
in_config = False
in_dependencies = False
plugin_start = -1
indent = ""

while i < len(lines):
    line = lines[i]
    
    # 检测plugin开始
    if '<plugin>' in line and 'maven-compiler-plugin' in '\n'.join(lines[max(0, i-3):i+3]):
        # 向前查找groupId和artifactId
        if i > 0 and 'maven-compiler-plugin' in lines[i-1]:
            plugin_start = len(new_lines)
            indent = line[:len(line) - len(line.lstrip())]
            in_plugin = True
            new_lines.append(line)
            i += 1
            continue
    
    # 如果在plugin块中，检测groovy-eclipse-compiler
    if in_plugin:
        if '<compilerId>groovy-eclipse-compiler</compilerId>' in line:
            # 找到了，需要替换整个plugin块
            # 回退到plugin开始位置
            new_lines = new_lines[:plugin_start]
            # 添加新的标准配置
            version_line = None
            for j in range(plugin_start, i):
                if '<version>' in lines[j] and '</version>' in lines[j]:
                    version_line = lines[j]
                    break
            
            version = '${project.compiler.version}'
            if version_line:
                version_match = re.search(r'<version>([^<]+)</version>', version_line)
                if version_match:
                    version = version_match.group(1).strip()
            
            new_lines.append(f'{indent}<plugin>')
            new_lines.append(f'{indent}    <groupId>org.apache.maven.plugins</groupId>')
            new_lines.append(f'{indent}    <artifactId>maven-compiler-plugin</artifactId>')
            new_lines.append(f'{indent}    <version>{version}</version>')
            new_lines.append(f'{indent}    <configuration>')
            new_lines.append(f'{indent}        <source>${{project.java.version}}</source>')
            new_lines.append(f'{indent}        <target>${{project.java.version}}</target>')
            new_lines.append(f'{indent}        <debug>true</debug>')
            new_lines.append(f'{indent}        <parameters>true</parameters>')
            new_lines.append(f'{indent}    </configuration>')
            new_lines.append(f'{indent}</plugin>')
            
            # 跳过到</plugin>
            while i < len(lines) and '</plugin>' not in lines[i]:
                i += 1
            if i < len(lines):
                i += 1  # 跳过</plugin>
            in_plugin = False
            continue
        elif '</plugin>' in line:
            in_plugin = False
            new_lines.append(line)
            i += 1
            continue
    
    new_lines.append(line)
    i += 1

new_content = '\n'.join(new_lines)

# 如果内容有变化，写入文件
if new_content != content:
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(new_content)
    print("  ✅ 修复完成")
    sys.exit(0)
else:
    print("  ⚠️  未找到需要替换的内容（可能已修复或格式不同）")
    sys.exit(1)

PYTHON_SCRIPT

    if [ $? -eq 0 ]; then
        FIXED=$((FIXED + 1))
        echo "  ✅ 修复成功"
    else
        echo "  ⚠️  修复失败或无需修复，请手动检查"
        SKIPPED=$((SKIPPED + 1))
    fi
    echo ""
done

echo "=========================================="
echo "修复完成！"
echo "=========================================="
echo "总计: $TOTAL 个文件"
echo "修复: $FIXED 个文件"
echo "跳过: $SKIPPED 个文件"
echo ""
echo "备份文件已保存，如需恢复请使用:"
echo "  cp <file>.backup.* <file>"
echo ""
echo "建议运行验证:"
echo "  mvn clean compile -DskipTests"
echo "  或"
echo "  bash ./build/verify-java17.sh"
