# 材料备货助手 v1.0.0

Litematica（投影）的备料助手：**投影说要什么，它告诉你还差什么。**

## 主要功能

- **读投影** —— 列出投影所需的全部材料与数量，按原版标签分类整理，可自定义分类
- **数仓库** —— 框选备货区（按 `K` 选两个对角点），扫描箱子里已经有多少
- **算差额** —— 材料清单直接给出「已有 / 缺少」，缺的进合成计算器逐层推导到基础材料
- **世界里找料** —— 高亮某种材料分布在哪个箱子
- **其他** —— 搜索支持拼音（`bai` / `bshnt`）、药水酿造链推导、HUD 实时显示

## 运行环境

| 项目 | 要求 |
|---|---|
| Minecraft | 1.21 ~ 1.21.1（客户端） |
| Java | 21 |
| Fabric Loader | ≥ 0.15.11 |
| Fabric API | 任意版本 |
| Litematica | ≥ 0.19.54 |
| MaLiLib | ≥ 0.21.7 |

⚠️ Litematica 0.19.53 及以下 / MaLiLib 0.21.6 及以下**缺少本模组用到的接口**，
Fabric 会提示版本不满足并拒绝加载（不会崩游戏）。

## 安装

1. 装好 Fabric（用启动器自带的 Fabric 安装功能；官网 Fabric Installer 是给官方启动器做的，PCL2/HMCL 用它可能报「找不到启动器配置文件」）
2. 把这 4 个 jar 一起放进 `.minecraft/mods/`：
   `fabric-api`、`litematica`、`malilib`、**本模组**
3. 进游戏按 `Y` 打开界面

## 下载文件

| 文件 | 说明 |
|---|---|
| `materialstock-1.0.0.jar` | 模组本体（215,843 字节） |
| `SHA256.txt` | 校验值 |

```
SHA256: 8DE02EEDA51941949F37A94BDD141D95CCD401D770A68575670C4C1B30A9866F
```

> 校验方法（Windows PowerShell）：
> `Get-FileHash .\materialstock-1.0.0.jar -Algorithm SHA256`
> 输出的哈希应与上面一致。

## 许可

本模组 MIT。Litematica / MaLiLib 作者 [maruohon](https://github.com/maruohon)，各有自己的许可证。
本仓库与 Release **不分发**上述第三方 jar，只在文档中声明依赖与下载地址。
