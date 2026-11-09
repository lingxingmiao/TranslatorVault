# TranslatorVault

[![Build](https://github.com/lingxingmiao/TranslatorVault/actions/workflows/build.yml/badge.svg)](https://github.com/lingxingmiao/TranslatorVault/actions/workflows/build.yml)

Vault Patcher 的**独立扩展插件**：把 Vault Patcher 模块里的硬编码 `原文 -> 译文` 导出成当前游戏版本对应的标准 Minecraft 语言文件；同时能读取 mod jar 内嵌的 Vault Patcher 模块，让“翻译后的 mod jar”直接参与替换。

**不修改 Vault Patcher 本体，不连接 TranslatorMinecraft，不调用任何翻译 API，不需要 key。**

## 安装

1. 安装 Vault Patcher（≥ 1.5.4）。
2. 把 `translatorvault-1.1.0.jar` 放到：

   ```text
   .minecraft/vaultpatcher/plugins/translatorvault-1.1.0.jar
   ```

3. 启动游戏。插件会在 Vault Patcher 加载完模块后读取所有 `Pairs` 并生成文件。

## 输出

| 游戏版本 | 文件 | 格式 |
|---|---|---|
| 1.6.2 – 1.12.2 | `vaultpatcher/lang/zh_CN.lang` | `原文=译文` |
| 1.13 – 26.4 | `vaultpatcher/lang/zh_cn.json` | `{"原文": "译文"}` |

版本从 `VaultPatcher.mcVersion` 读取；LaunchWrapper 下识别不到版本时按 1.6.2–1.12.2 的 legacy `.lang` 处理。

## 配置

首次启动生成：

```text
config/vaultpatcher_asm/translatorvault.json
```

```json
{
  "enable": true,
  "target_language": "zh_cn",
  "output_dir": "vaultpatcher/lang",
  "include_unchanged": false
}
```

- `target_language`：目标语言代码。1.12.2 会规范成 `zh_CN`，26.4 规范成 `zh_cn`。
- `output_dir`：相对游戏根目录的输出目录。
- `include_unchanged`：是否保留“原文=原文”的条目，默认不保留。

## 工作流程

1. Vault Patcher 读取 `vaultpatcher/modules/*.json`，构建 `Pairs`。
2. 插件在 `VaultPatcherPlugin.end()` 中遍历 `Utils.translationInfoMap` 和 `Utils.dynTranslationInfos`。
3. 合并 static / dynamic pair，去掉 dynamic 的 `@` 前缀。
4. 按版本写 `.lang` 或 `.json`。

插件不会修改内存里的 `Pairs`，不会覆盖 `vaultpatcher/i18n`，也不会参与文本替换；替换仍然完全由 Vault Patcher 本体完成。

## 内嵌模块：让翻译后的 mod jar 直接可用

插件会在 `start()`（Vault Patcher 构建转换器之前）扫描：

```text
.minecraft/mods/*.jar
  └── vaultpatcher/modules/<modid>-hardcoded.json
  └── translatorvault/modules/<modid>-hardcoded.json
```

把这些模块里的 `k -> v` 注册到 `Utils.translationInfoMap`。随后 Vault Patcher 的 ASM 转换器会正常替换对应类里的 `LDC` 字符串。

这正好配合 TranslatorMinecraft 的 `trans-hardcoded.py`：

```text
mod jar
  → TranslatorMinecraft 扫描 .class 常量池、翻译硬编码文本
  → 把 vaultpatcher/modules/<modid>-hardcoded.json 写回 jar
  → 安装这个 jar + TranslatorVault + Vault Patcher
  → TranslatorVault 读取内嵌模块
  → Vault Patcher 替换 LDC，游戏内显示译文
  → TranslatorVault 再导出 lang 文件
```

所以，**只装 TranslatorVault 不会替换**；替换由 Vault Patcher 完成。但有了内嵌模块，翻译后的 jar 不需要再手动把模块复制到 `vaultpatcher/modules`。

## 版本与加载器

插件没有加载器代码，完全复用 Vault Patcher 的入口：

- Forge / NeoForge / Fabric / Quilt / Cleanroom / LWJGL3ify：跟随 Vault Patcher 的支持范围。
- 1.6.2 – 26.4：由版本号决定 `.lang` / `.json`。
- 插件 class 为 Java 8 字节码，可在 Java 8 – 25 运行。

## 构建

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu17-jdk'
.\gradlew.bat clean build verifyPluginJar
```

产物：

```text
build/libs/translatorvault-1.1.0.jar
```

`libs/vaultpatcher-1.5.4-api.jar` 只用于编译（`compileOnly`），不会打进插件 jar。

## 已验证

- 干净 Vault Patcher 1.5.4（未修改）+ Java 8 + LaunchWrapper 模拟 1.12.2：插件正常加载，生成 `zh_CN.lang`。
- 模拟 26.4：生成 `zh_cn.json`。
- mod jar 内嵌 `vaultpatcher/modules/sample-hardcoded.json`：模块被读取，`Sample` 类的 `LDC "Reactor Controller"` 被替换为 `反应堆控制器`。
- 插件 jar 不包含任何 `me/fengming/vaultpatcher_asm/` 类。

## 许可证

GPL-3.0，见 [LICENSE](LICENSE)。
