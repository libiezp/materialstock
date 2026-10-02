# 开发环境搭建

面向要改代码的人。本文只讲**通用做法**，不绑定某台机器的路径。

---

## 环境要求

| 项目 | 版本 | 说明 |
|---|---|---|
| JDK | **21** | 项目要求，`build.gradle` 里 `release = 21` |
| Gradle | 用仓库自带的 `gradlew` | `fabric-loom 1.16.3` 需要 Gradle 8.12+ |
| IDE | IntelliJ IDEA | 装 Fabric/Minecraft Development 插件不是必需的 |

> **不要**用系统里另一个版本的 Gradle 直接构建，一律走 `./gradlew`（wrapper 会自己下对应发行版）。

---

## 首次准备

### 1. 放置第三方依赖

`libs/` 里需要两个 jar，**不由仓库提供**，请按 [`../libs/README.md`](../libs/README.md) 下载：

```
libs/litematica.jar     # Litematica 0.19.54+
libs/malilib.jar        # MaLiLib   0.21.7+
```

文件名必须一致（`build.gradle` 里按此名引用）。

### 2. 构建

```bash
./gradlew build            # Windows: gradlew.bat build
```

产物：`build/libs/materialstock-<version>.jar`

### 3. IDEA 导入

1. 用 IDEA 打开项目根目录
2. 弹「Trust Gradle project」→ **Trust**
3. 等 Gradle 同步（首次会下载依赖，视网络几分钟到十几分钟）
4. 若 Gradle 报错，检查两个设置：
   - `File → Settings → Build, Execution, Deployment → Build Tools → Gradle`
   - **Gradle JVM**：必须选 **21**
   - **Gradle user home**：默认即可（想隔离可自定义）

---

## 调试：热替换（改代码不用退游戏）

### 步骤

1. Gradle 面板 → `Tasks` → `fabric` → **`runClient`** → 右键 **Debug**（必须 Debug，不是 Run）
2. 游戏启动后进入世界，按 `Y` 打开界面
3. 改代码 → **Ctrl+F9**（`Build → Recompile`）→ JVM 热替换已改的方法体
4. **游戏里关闭界面再按 `Y` 重新打开即生效**，无需重启游戏

### 局限性（很重要）

热替换**只对方法体内部有效**（改坐标、逻辑、字符串等）。

**以下属于结构性改动，热替换无效**，必须重启 `runClient`：

- 新增 / 删除方法或字段
- 改类名、方法签名
- 改 `fabric.mod.json`、mixin 配置

> 曾经因为投递的 jar 是旧的（改了结构但没重新 `build`），
> 导致游戏报 `Failed to load class file for '...CategoryHelper'` 崩溃。
> **结构性改动后一律先 `./gradlew build` 再投递。**

---

## 开发版与正式版的隔离

Loom 会用项目下的 `run/` 作为游戏目录，**与你的正式游戏目录结构上天然隔离**：

| | 路径 | 谁在用 |
|---|---|---|
| 正式版 mods | `<你的游戏目录>/mods/` | 你的启动器 |
| 正式版配置 | `<你的游戏目录>/config/materialstock/stock.json` | 正式版游戏读写 |
| **开发版** mods | `run/mods/` | loom `runClient` |
| **开发版**配置 | `run/config/materialstock/stock.json` | 开发版游戏读写 |

配置目录由 `StockConfig.java` 的 `FabricLoader.getConfigDir()` 按**运行根目录**决定，
所以 dev 里改配置**不会**影响正式版。

> ⚠️ `run/` 已被 `.gitignore` 排除 —— 里面有存档、截图、日志、账号缓存，
> **绝不要提交**。

### runClient 的环境差异

dev 客户端**只加载** `run/mods/` 里的 mod（需要自己放 litematica/malilib/fabric-api），
**不会**加载你正式目录里的其它 mod（钠、其它优化 mod 等）。

因此由**环境 mod 引起**的问题（如某些配方被其它 mod 改动）在 dev 下可能复现不了，**以正式环境为准**。

---

## 投放（把 jar 装到正式环境）

```bash
./gradlew build
# 然后把 build/libs/materialstock-<version>.jar 复制到 <游戏目录>/mods/
```

### 两个常见错误

1. **放错目录**：是 `<游戏目录>/versions/<版本>/mods/`，
   不是 `<游戏目录>/mods/`（后者常常是空目录，放那儿不生效）
2. **放错产物**：要 `build/libs/*.jar`，**不要** `build/devlibs/*-dev.jar`
   （dev 版含未 remap 的类，正式环境跑不了）

---

## 代码结构

```
src/main/java/com/materialstock/
├── MaterialStock.java        模组入口（ClientModInitializer）
├── StockConfig.java          配置读写（stock.json）+ 按世界分存
├── WorldManager.java         世界切换时装载/保存各世界的选区与缓存
├── KeyBindings.java          快捷键
├── craft/                    合成计算
│   ├── CraftCalculator.java    配方查找、基础材料推导、子合成树
│   ├── BrewingRecipes.java     药水酿造表（原版没有 brewing 配方数据，必须内置）
│   └── PotionRecipe.java       把酿造表适配成 Recipe（纯展示用）
├── scan/                     扫描与缓存
│   ├── StockAreaScanner.java   备货区/材料区扫描
│   ├── ContainerCache.java     容器内容缓存（备货区/材料区有 isMaterial 区分）
│   ├── ContainerProbe.java     联机模式的容器探查（靠 tag query 封包）
│   └── SelectionManager.java   框选的两个角点
├── litematica/               与 Litematica 交互
├── gui/                      界面（malilib GuiBase）
├── render/                   世界内高亮（WorldRenderer）与信息浮窗（HudRenderer）
├── util/                     拼音、数量格式化
└── mixin/                    拦截 setblock 探查响应
```

### 两个值得注意的设计

**1. 备货区与材料区共用 `ContainerCache`，靠 `isMaterial` 标记区分**

材料区的容器也放进同一个缓存（为了持久化，让追踪高亮重启后仍有效），
但**所有备货区统计必须排除 `isMaterial` 条目** —— 两个区域要求独立统计。

改这个缓存前，**先列出全部读取点**，否则很容易让材料区数据混进备货区库存。

**2. 药水需要"变体 id"**

所有药水都是 `minecraft:potion`，靠 `POTION` 数据组件区分效果。
本模组用 `minecraft:potion#invisibility` 这样的**规范 id** 区分，
`CraftCalculator.parseVariant()` 负责解析。

因此**任何按物品 id 取物品的地方都不能直接用 `ResourceLocation.tryParse`**，
否则会丢掉效果后缀导致解析失败。

---

## 提交前自检

```bash
./gradlew build          # 必须通过
```

并确认 `git status` **没有**出现：

- `run/` 下的任何内容
- `build/`、`.gradle/`、`.idea/`
- `libs/*.jar`

---

## 附：写 PowerShell 脚本的坑

本仓库 `devtools/` 下有几个 `.ps1`。注意 **Windows PowerShell 5.1** 会按 ANSI 解码无 BOM 的 `.ps1`，
里面的中文会吃掉换行导致解析报错。

**规则**：`.ps1` 一律只写 ASCII，或存成 UTF-8 **with BOM**。
