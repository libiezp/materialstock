# 材料备货助手 (Material Stock)

> Minecraft Fabric 客户端模组 —— **Litematica（投影）的备料助手**
> Litematica 告诉你"**要什么**"，这个模组告诉你"**还差什么**"。

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21%20~%201.21.1-62B47A)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0B4)](https://fabricmc.net/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

---

## 这是什么

照着投影盖建筑，最烦的是**备料**：跑遍仓库数还差多少铁块、白色混凝土、红石元件……

本模组做三件事：

1. **读投影** → 列出该投影需要的全部材料与数量，按原版标签分类整理
2. **数仓库** → 框选你的箱子，扫描出"已经有多少"
3. **算差额** → 直接告诉你还缺多少；缺的能进合成计算器，逐层推导到基础材料（要挖多少矿、烧多少石头）

### 与 Litematica 自带材料清单的区别

| | Litematica 自带 | 本模组 |
|---|---|---|
| 列出投影所需材料 | ✅ | ✅ |
| **对比仓库实际库存** | ❌ | ✅ |
| **算"还缺多少"** | ❌ | ✅ |
| 合成配方逐层推导（铁块→铁锭→…） | ❌ | ✅ |
| 按原版标签分类 + 自定义分类 | ❌ | ✅ |
| 世界上高亮"这个材料在哪个箱子" | ❌ | ✅ |
| 搜索支持拼音（`bai` / `bshnt`） | ❌ | ✅ |
| 药水酿造链（隐身药水等） | ❌ | ✅ |

---

## 下载

**玩家（只想装上玩）**，两个地方拿到的东西完全一样：

| 方式 | 位置 | 适合 |
|---|---|---|
| 直接点开 | [`release/materialstock-1.0.0.jar`](release/materialstock-1.0.0.jar) —— 点开后按右上角 **Download** 按钮 | 就想赶紧下载一个文件 |
| Releases 页 | [**Releases**](https://github.com/libiezp/materialstock/releases) | 想看清版本说明和历次更新 |

校验值写在 [`release/SHA256.txt`](release/SHA256.txt)，下载后可选校验。

> ⚠️ 不要用仓库右上角的 `Code → Download ZIP` 去装模组 —— 那是**整个源码包**，
> 里面没有能直接放进 `mods/` 的 jar（本节表格里的那个才是）。

**开发者（想改代码）**：见 [从源码构建](#从源码构建)。

---

## 快速开始

### 前置要求

| 项目 | 要求 |
|---|---|
| Minecraft | **1.21 ~ 1.21.1** |
| Java | **21** |
| Fabric Loader | ≥ 0.15.11 |
| [Fabric API](https://modrinth.com/mod/fabric-api) | 任意版本 |
| [Litematica](https://modrinth.com/mod/litematica) | **≥ 0.19.54** |
| [MaLiLib](https://modrinth.com/mod/malilib) | **≥ 0.21.7** |

> ⚠️ Litematica **0.19.53 及以下** / MaLiLib **0.21.6 及以下**缺少本模组用到的接口。
> 版本不足时 Fabric 会**明确提示版本不满足并拒绝加载**（不会崩游戏）。

### 安装

1. 装好 Fabric（请用**启动器自带**的 Fabric 安装功能 —— 官网 Fabric Installer 是给官方启动器做的，PCL2/HMCL 用它会报「找不到启动器配置文件」）
2. 把 `fabric-api`、`litematica`、`malilib`、**本模组** 一起放进 `.minecraft/mods/`
3. 进游戏，按 **`Y`** 打开界面

### 第一次使用

```
按 Y 打开 → 搜投影 → 点进去看材料清单   （此时是只读的，不改任何东西）
到「备货区」页 → 按 K 选两个对角点 → 保存为备货区 → 扫描备货区
回到材料清单 → "已有 / 缺少" 就是真实差额了
```

**完整上手教程见 [`docs/GETTING_STARTED.md`](docs/GETTING_STARTED.md)**

---

## 文档

| 文档 | 内容 |
|---|---|
| [`docs/GETTING_STARTED.md`](docs/GETTING_STARTED.md) | **新人上手**：概念、流程、常见坑 |
| [`docs/CONTROLS.md`](docs/CONTROLS.md) | **控件与快捷键对照**：每个界面逐个按钮说明 |
| [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) | 开发环境搭建、热替换调试、投放流程 |

---

## 核心概念：备货区 vs 材料区

新手最容易混的地方：

| | **备货区** | **材料区** |
|---|---|---|
| 含义 | 我已经有的东西 | 另一份独立的库存 |
| 计入"还缺多少" | ✅ 是 | ❌ 否（独立统计） |
| 默认显示世界范围框 | 显示 | **不显示**（按 `H` 才显示） |

> **记忆方法**：算差额时**只认备货区**。材料区是另一本账。

---

## 快捷键

界面底部会**实时**显示当前绑定，改键后立即生效。

| 按键 | 默认 | 作用 |
|---|---|---|
| 打开主界面 | `Y` | 打开本模组 |
| 框选角点 | `K` | 对准方块按两次记一对角点 |
| 材料区高亮开关 | `H` | 显示/隐藏材料区范围框 |

---

## 从源码构建

```bash
# 0. 需要 JDK 21（gradlew 会自行下载 Gradle，无需先装 Gradle）
# 1. 按 libs/README.md 说明下载 litematica 与 malilib 放进 libs/，文件名必须是
#    litematica.jar / malilib.jar（libs/ 里的 jar 不进版本库）
# 2. 构建
./gradlew build          # Windows: gradlew.bat build
# 产物：build/libs/materialstock-<version>.jar
```

依赖的第三方 jar 细节（下载地址、版本边界、为什么不能随仓库分发）见
**[`libs/README.md`](libs/README.md)**。

---

## 许可

- **本模组**：[MIT](LICENSE)
- **Litematica / MaLiLib**：作者 [maruohon](https://github.com/maruohon)，各有自己的许可证，使用时请遵守
- 本仓库**不分发**上述第三方 jar，仅在 `libs/README.md` 中声明依赖与下载地址
