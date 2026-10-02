# devtools — 安全投放（不参与构建）

本目录的东西**不会被编译进 mod**，也不影响任何现有路径。
它只做一件事：**拦住"开发版意外写进正式版"**。

> 注：本目录的脚本最初是为作者本机写的，默认路径按下面的示例。
> 使用前请先看「路径配置」一节，改成你自己的游戏目录。

---

## 背景

开发时存在两个游戏目录（loom 的 `run/` 与你的正式游戏目录），二者**结构上隔离**：

| | 路径（示例） | 谁在用 |
|---|---|---|
| 正式版 mods | `<游戏目录>\versions\1.21\mods\` | 你的启动器 |
| 正式版配置 | `<游戏目录>\versions\1.21\config\materialstock\` | 正式版游戏读写 |
| 开发版 mods | `run\mods\` | loom `runClient` |
| 开发版配置 | `run\config\materialstock\` | 开发版游戏读写 |

配置目录由 `StockConfig.java` 的 `FabricLoader.getConfigDir()` 按**运行根目录**决定，
所以 dev 里改配置**不会**影响正式版。

但 jar 的投递是**人工复制**，没有任何代码约束 —— 这是唯一的污染入口。
所以只给这一个入口加闸。

---

## 路径配置

两个脚本默认按作者的目录结构（`D:\.minecraft\versions\1.21\mods`）。
**换成你自己的**：

| 脚本 | 位置 | 改什么 |
|---|---|---|
| `guard-prod-path.ps1` | 顶部 `$ALLOWED_PROD_MODS` | 允许写入的正式版 mods 目录（精确匹配） |
| `deploy-to-prod.ps1` | 顶部目标路径变量 | 投递目标 |

> ⚠️ 注意区分：是 `<游戏目录>\versions\<版本>\mods\`，
> **不是** `<游戏目录>\mods\`（后者常常是空目录，放那儿不生效 —— 这是最常见的误操作）。

---

## 两道闸

### 闸 1：`guard-prod-path.ps1`（路径判定）

任何要投递 jar 的流程，先调它判定目标路径。规则：

- 目标必须**精确等于**允许的 mods 目录
- 拒绝 `<游戏目录>\mods`（空目录，放那儿不生效）
- 拒绝游戏根目录及其它任何目录
- 拒绝目标目录里出现开发版产物特征（`-dev.jar`、`-sources.jar`）

### 闸 2：`deploy-to-prod.ps1`（唯一投放通道）

如果**确实**要把构建产物放到正式版（这是有意为之的正常操作），不要手拖，用这个脚本。
它会：

1. 先过闸 1
2. 检查游戏是否正在运行（jar 被占用时复制会失败或产生半截文件）
3. **先备份**现有 jar 到 `devtools\backup\`
4. 复制并 **SHA256 校验**
5. 打印回滚命令

---

## 用法

```powershell
# 只判定，不动文件（默认行为，安全）
.\devtools\guard-prod-path.ps1 -Path '<游戏目录>\mods'

# 有意投递（加 -Apply 才会真正写）
.\devtools\deploy-to-prod.ps1 -Source 'build\libs\materialstock-1.0.0.jar' -Apply
```

两个脚本**默认都是干跑（dry-run）**，不加 `-Apply` 绝不写正式版。

---

## 它拦不住什么

- 你在资源管理器里**手动拖拽** jar 到正式版 mods —— 系统级操作，脚本管不到。
  所以闸 2 的作用是：**让"正确的投放方式"比手动拖拽更省事**。
- `gradle build` 本身不部署任何东西（`build.gradle` 无 copy/dest 逻辑），
  所以构建永远只产出到 `build\libs\`，天然安全。

---

## 维护注意：这两个 .ps1 必须保持纯 ASCII

Windows PowerShell **5.1** 在读取**无 BOM** 的 `.ps1` 时按 **ANSI/GBK** 解码，
中文注释会变成乱码，并且**把换行一起吃掉**，导致解析出错误的行号、变量"未定义"等假报错。

本目录的脚本已经踩过这个坑（中文注释使 98 行塌成 83 行，报
`检索不到变量"$ALLOWED_PROD_MODS"`）。所以：

- `guard-prod-path.ps1` 和 `deploy-to-prod.ps1` **一律只写 ASCII**
- 中文说明写在这个 `README.md` 里（`.md` 不会被 PowerShell 解析，安全）
- 若要新增中文脚本，请存成 **UTF-8 with BOM**，否则同样会炸

验证脚本是否纯 ASCII：

```powershell
$b = [System.IO.File]::ReadAllBytes('.\devtools\guard-prod-path.ps1')
@($b | Where-Object { $_ -gt 127 }).Count   # 输出 0 即为纯 ASCII
```
