# release/ — 构建好的成品 jar

本目录放的是**已经编译好的模组成品**，给不想自己构建的人直接下载用。

| 文件 | 说明 |
|---|---|
| `materialstock-1.0.0.jar` | 模组本体，215,843 字节 |
| `SHA256.txt` | 校验值 |

```
SHA256: 8DE02EEDA51941949F37A94BDD141D95CCD401D770A68575670C4C1B30A9866F
```

校验方法（Windows PowerShell）：

```powershell
Get-FileHash .\materialstock-1.0.0.jar -Algorithm SHA256
# 输出的哈希应与 SHA256.txt 里的一致
```

## 怎么用

1. 连同 `fabric-api`、`litematica`（≥ 0.19.54）、`malilib`（≥ 0.21.7）一起放进 `.minecraft/mods/`
2. 进游戏按 `Y` 打开界面

完整说明见仓库根目录的 [`README.md`](../README.md) 和 [`docs/GETTING_STARTED.md`](../docs/GETTING_STARTED.md)。

## 版本规则（重要）

- **一个版本一个文件，不要覆盖已发布的 jar。** 发了 1.0.1 就新增 `materialstock-1.0.1.jar`
  （或把旧的挪到 `release/old/`），否则已经下载过 1.0.0 的人无法判断自己手上的是不是原版。
- 本目录里的 jar 必须与 `gradle.properties` 里的 `mod_version` 对应。
- 更新 jar 后请同步更新 `SHA256.txt`。

## 本目录的 jar 是怎么来的

由本仓库源码构建，不是手工改过的：

```bash
./gradlew build          # Windows: gradlew.bat build
# 产物 build/libs/materialstock-<version>.jar 复制到本目录并改名
```

本目录**只含本模组自己的代码**，不含 litematica / malilib / fabric-api 等第三方内容
（那些请自行从各自官方渠道下载，理由见 [`libs/README.md`](../libs/README.md)）。
