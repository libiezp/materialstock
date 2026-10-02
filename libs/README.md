# libs/ — 第三方依赖放置说明

本目录**不放 jar 文件**（`.gitignore` 已排除），因为这里的 jar 都是**别人的作品**，
各有自己的许可证，不应随本仓库分发。请自行下载后放到本目录，文件名必须**完全一致**。

## 需要放这里的两个 jar

| 文件名（必须一致） | 下载地址 | 本仓库验证过的版本 |
|---|---|---|
| `litematica.jar` | https://modrinth.com/mod/litematica | **0.19.54**（或更高，但需 ≤ 1.21.1 分支） |
| `malilib.jar` | https://modrinth.com/mod/malilib | **0.21.7**（或更高，但需 ≤ 1.21.1 分支） |

> 在 Modrinth 的 **Versions** 页选择对应的游戏版本（1.21 或 1.21.1），
> 下载后把文件名改成 `litematica.jar` / `malilib.jar`。

## 为什么需要这么低的版本？

本模组的 `fabric.mod.json` 声明：

```json
"litematica": ">=0.19.54",
"malilib":   ">=0.21.7"
```

这两个下界不是随便写的，是用**字节码逐版本比对**得出的：

| 依赖 | 卡在哪个 API | 首个具备的版本 |
|---|---|---|
| litematica | `FileType.fromName(String)` | **0.19.54**（0.19.50 ~ 0.19.53 没有此方法） |
| malilib | `GuiBase.getScreenWidth/getScreenHeight`<br>`GuiTextFieldGeneric.getTextWrapper/setTextWrapper` | **0.21.7**（0.21.0 ~ 0.21.6 没有这些方法） |

所以：
- 用**低于**下界的版本 → 编译失败（找不到符号），或运行时 `NoSuchMethodError`
- 用**高于**下界的版本 → 正常（这些 API 没有移除）

## `fabric-api.jar` 不需要放这里

本目录里出现过的 `fabric-api.jar` **未被 `build.gradle` 引用**
（fabric-api 走 Maven：`net.fabricmc.fabric-api:fabric-api`）。
它只是当初手工留下的备份，删掉不影响构建。

## 放好之后

```bash
./gradlew build          # 或 Windows 下 gradlew.bat build
```

构建产物在 `build/libs/materialstock-<version>.jar`。

## 许可证提示

- **本模组**：MIT（见仓库根目录 `LICENSE`）
- **litematica / malilib**：作者 maruohon，有自己的许可证，使用时请遵守其条款

本仓库**不分发**上述 jar，只声明依赖关系，因此不涉及再分发。
