# ZStack 构建加速指南

## 为什么 `-T 1C` 提升有限？

- **依赖链长**：header → core → plugin* → premium，大量模块串行，可并行的只有同一“层”的模块。
- **单模块重**：mevoco、testlib-premium、hybrid、header 等单模块编译时间 30s～70s，并行只能缩短“层内”时间。
- **内存压力**：并行会起多个 JVM（GMavenPlus/编译器），若总内存不足易触发 GC 或交换，反而变慢。

---

## 推荐做法（按收益排序）

### 1. 日常开发：不 clean，只编改动的模块（收益最大）

**从 zstack 根目录构建 premium 时，必须先激活 profile `-P premium`**（根 pom 默认模块列表里没有 premium，只有激活该 profile 后 reactor 才会包含 premium）。

```bash
# 只编 premium 整棵子工程（含其依赖：header、core、plugin 等）
# -Dmevoco.skip.obfuscate=true 避免 mvnd 下 exec 插件 NPE，见下文说明
mvnd install -DskipTests -P premium -Dmevoco.skip.obfuscate=true -pl premium -am

# 只编 premium 下某模块（如 mevoco）及其依赖
mvnd install -DskipTests -P premium -Dmevoco.skip.obfuscate=true -pl premium/mevoco -am

# 只编 premium 下某插件
mvnd install -DskipTests -P premium -Dmevoco.skip.obfuscate=true -pl premium/plugin-premium/某插件名 -am
```

若**已进入 premium 目录**，则不需要 `-P premium`，直接：

```bash
cd premium && mvnd install -DskipTests -Dmevoco.skip.obfuscate=true

# 只编 premium 内某模块
cd premium && mvnd install -DskipTests -Dmevoco.skip.obfuscate=true -pl mevoco -am
```

避免每次 `clean` 全量重编，可节省大半时间。

### 2. 使用 Maven Daemon（mvnd）— 二次及以后构建明显更快

mvnd 保持 JVM 常驻，避免每次冷启动和重复加载类，对多模块项目通常有 **2～4 倍** 提升。

```bash
# macOS（mvnd 需通过官方 tap 安装，默认 brew 无此 formula）
brew install mvndaemon/homebrew-mvnd/mvnd

# 使用方式与 mvn 一致
mvnd clean install -DskipTests
mvnd install -DskipTests -pl premium/mevoco -am
```

**使用 mvnd 构建含 premium 时**：exec-maven-plugin 在 mvnd 下会因 `MavenSession.getContainer()` 为 null 报错，需跳过 mevoco 的混淆步骤：

```bash
mvnd install -DskipTests -P premium -Dmevoco.skip.obfuscate=true
```

需要发布/混淆时用普通 Maven：`mvn install -DskipTests -P premium`（不加该参数）。

注意：首次运行仍会较慢，后续构建才会体现加速。

**mvnd 内存与 Groovy 编译**：test-premium、test、testlib-premium 等模块大量使用 Groovy，gmavenplus 会在同一 JVM（mvnd daemon）里解析/编译，易占满堆或 Metaspace 导致无响应。已为 gmavenplus-plugin 开启 **`<fork>true</fork>`**：Groovy 编译在独立子进程中执行，完成后子进程退出释放内存，减轻 daemon 压力。根目录 `.mvn/jvm.config` 已为 mvnd daemon 配置：

- **堆**：`-Xmx10240m`（10GB），供 Groovy 源码与 stub 常驻
- **Metaspace**：`-XX:MaxMetaspaceSize=2560m`（2.5GB），应对大量 Groovy/Java 类加载
- **CodeCache**：`-XX:ReservedCodeCacheSize=512m`、`-XX:NonProfiledCodeHeapSize=240m`，避免 JIT 代码缓存满
- **G1**：`-XX:+UseG1GC`、`-XX:+UseStringDeduplication`

建议物理内存 ≥16GB 再跑 `mvnd install -DskipTests -P premium` 全量；否则可先用 `mvn` 或只编到 `test`（`-pl test -am`）。修改 `.mvn/jvm.config` 后必须重启 daemon 才生效：

```bash
mvnd --stop
mvnd install -DskipTests -P premium -Dmevoco.skip.obfuscate=true
```

### 3. 离线构建（依赖已齐全时）

```bash
mvn install -DskipTests -o
```

避免每次解析/检查远程仓库，可省数秒到数十秒。

### 4. 适当提高并行度（在内存充足时）

若机器内存 ≥16GB，可尝试：

```bash
# 最多 4 个线程，避免过多并行导致 OOM
mvn install -DskipTests -T 4
```

不建议在 8GB 或以下机器用 `-T 1C`，易与 `.mvn/jvm.config` 里的大堆一起导致交换。

### 5. 只编到某模块，不编完全部

```bash
# 只编到 build（含 header/core/plugin 等），不编 test/testlib/premium 测试相关
mvn install -DskipTests -pl build -am

# 只编到 crypto，不编 testlib-premium / test-premium
mvn install -DskipTests -pl premium/crypto -am
```

适合验证主链或某个子模块，跳过最耗时的 testlib-premium 等。

### 6. 关闭/缩短测试（已有 -DskipTests 时可略过）

已使用 `-DskipTests` 时，测试已跳过。若某处仍会跑测试，可再加：

```bash
-Dmaven.test.skip=true
```

---

## 建议的日常组合

| 场景 | 命令示例 |
|------|----------|
| 从**根目录**只编 premium | `mvnd install -DskipTests -P premium -Dmevoco.skip.obfuscate=true -pl premium -am` |
| 从**根目录**只编 mevoco | `mvnd install -DskipTests -P premium -Dmevoco.skip.obfuscate=true -pl premium/mevoco -am` |
| 已 **cd premium**，只编某插件 | `mvnd install -DskipTests -Dmevoco.skip.obfuscate=true -pl plugin-premium/xxx -am` |
| 全量构建（含 premium，少用 clean） | `mvnd install -DskipTests -P premium -Dmevoco.skip.obfuscate=true` |
| 全量 + 并行（内存够） | `mvnd install -DskipTests -P premium -Dmevoco.skip.obfuscate=true -T 4` |
| 依赖已全、不想连网 | `mvnd install -DskipTests -P premium -Dmevoco.skip.obfuscate=true -o` |

---

## 可选：为编译器开启增量（需各模块兼容）

在根 `pom.xml` 的 `maven-compiler-plugin` 的 `pluginManagement` 里可统一开启：

```xml
<configuration>
    <useIncrementalCompilation>true</useIncrementalCompilation>
</configuration>
```

部分老模块若出现“改完不重编”的异常，再对单模块关闭即可。当前项目未全局开启，可按需试验。
