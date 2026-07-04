# Mod Whitelist Hash Generator 工具说明

## 功能概述

`generate_hash_config.py` 是一个Python脚本，用于一键获取Minecraft `mods`、`resourcepacks`、`shaderpacks` 文件夹下文件的SHA256哈希值，并生成适配 `mod_whitelist` 配置文件的JSON数组格式。

## 使用方法

### 1. 环境要求
- Python 3.6+
- 在 StarRailExpress 项目根目录下运行

### 2. 基本用法
```bash
cd g:\exmo\StarRailExpress
python tools\generate_hash_config.py
```

### 3. 目录结构要求
脚本会自动检测以下目录：
- `run/mods/` - 模组文件夹（当前版本暂不处理模组哈希）
- `run/resourcepacks/` - 资源包文件夹
- `run/shaderpacks/` - 光影包文件夹

### 4. 输出文件
生成的配置文件保存在：
```
run/config/mod_whitelist/allowed_hashes.json
```

文件格式示例：
```json
{
  "ALLOWED_RESOURCE_PACK_HASHES": [
    "630b026e863ea03c786e524ed8b5867d179d3cedc6d518961f8be184f748df07",
    "05e3cd5f1cdc22e2f77f4fafed0e3a490e56705741d01ea710c5fd4a190b9255"
  ],
  "ALLOWED_SHADER_PACK_HASHES": [
    "24a20634a7832d422d3cd5023be16829f26840e25c69404dd418306ea79f63f0"
  ]
}
```

### 5. 配置到服务器
1. 打开生成的 `allowed_hashes.json` 文件
2. 复制 `ALLOWED_RESOURCE_PACK_HASHES` 和 `ALLOWED_SHADER_PACK_HASHES` 数组内容
3. 粘贴到 `./config/starrailexpress-config.json` 中对应的配置项
4. 确保启用相应的验证选项：
   ```json
   "ENABLE_RESOURCE_PACK_VERIFICATION": true,
   "VERIFY_RESOURCE_PACK_HASHES": true,
   "ENABLE_SHADER_PACK_VERIFICATION": true,
   "VERIFY_SHADER_PACK_HASHES": true
   ```

## 特性

### 自动合并
- 如果 `allowed_hashes.json` 文件已存在，脚本会自动合并新旧哈希值
- 自动去重，避免重复的哈希值
- 保持原有哈希值的顺序

### 错误处理
- 跳过无法读取的文件并给出警告
- 跳过子目录（只处理文件）
- 目录不存在时自动跳过

### 性能优化
- 分块读取大文件，避免内存溢出
- 支持中断操作（Ctrl+C）

## 注意事项

1. **模组哈希**：当前版本主要针对资源包和光影包，模组验证使用不同的机制
2. **文件格式**：只处理 `.zip`、`.jar` 等压缩包文件，其他格式可能需要手动验证
3. **权限要求**：确保Python有读取Minecraft目录的权限
4. **编码问题**：脚本使用UTF-8编码，确保系统支持中文路径

## 故障排除

### 常见问题
- **"目录不存在"**：确保在正确的项目根目录运行，或手动创建相应目录
- **"无法读取文件"**：检查文件权限，确保Minecraft未锁定文件
- **"计算失败"**：文件可能已损坏，建议重新下载

### 调试模式
如需详细调试信息，可以在脚本中添加更多的日志输出。

## 更新日志

### v1.0.0
- 初始版本
- 支持资源包和光影包SHA256哈希计算
- 自动合并现有配置
- 完整的错误处理和用户提示

---

# 语言键管理器

`language_manager.py` 是用于维护 Minecraft JSON 语言文件的图形化工具。它可以识别项目中多个
`assets/<命名空间>/lang` 目录，并以选定的主语言文件为基准比较、导出、排序和同步其他语言。

## 启动方式

环境要求：Python 3.10+，并安装 Python 自带的 Tkinter。

在项目根目录运行：

```bash
python tools/language_manager.py
```

如需使用 AI 翻译，安装 OpenAI Python SDK：

```bash
python -m pip install -r tools/requirements-language-manager.txt
```

推荐通过环境变量提供阿里云百炼 API Key：

```powershell
$env:DASHSCOPE_API_KEY="sk-xxx"
python tools/language_manager.py
```

也可以在“AI 翻译设置”页临时填写 API Key。工具不会将 API Key 写入项目文件。

只扫描和验证语言文件，不打开窗口：

```bash
python tools/language_manager.py --scan .
```

## 主要功能

1. **扫描多个 assets**
   - 自动识别任意源码模块中的 `assets/<命名空间>/lang/*.json`。
   - 默认忽略 `.git`、`.gradle`、`build`、`bin`、`run`、`dist` 等构建产物目录。
   - 可以手动选择其他扫描根目录或添加单个语言文件。

2. **比较语言文件**
   - 选择主语言后，目标列表自动显示同一命名空间下的其他语言。
   - 分别统计缺失键、空值键、与主语言同值键和目标独有键。
   - `=== 标题 ===` 形式的空值键被视为分组标题，不会计入未翻译统计。

3. **导出待翻译键**
   - 可勾选导出缺失键、空值键和与主语言同值键。
   - “包含主语言原文（方便翻译）”默认启用，导出的键会带上主语言对应值。
   - 取消该选项后，导出值为空字符串。
   - 导出顺序和分组标题跟随主语言文件。

4. **排序和同步**
   - “一键排序整理”只调整目标文件已有键的顺序，不自动补键。
   - “同步合并缺失键”按主语言结构补齐缺失键，补入值使用主语言原文。
   - 两种操作都会保留目标已有翻译和目标独有键。

5. **AI 翻译**
   - “AI 翻译选中键”：在比较表格中使用 Ctrl/Shift 选择一个或多个语言键。
   - “AI 翻译勾选类别”：使用与“导出勾选类别”完全相同的缺失键、空值键、
     与主语言同值筛选项批量翻译。
   - 在“AI 翻译设置”页配置 API Key、OpenAI 兼容 Base URL、模型和批次大小。
   - 默认使用阿里云百炼北京地域兼容地址和 `qwen-plus`。
   - 请求在后台线程执行，不会卡住窗口；每批返回都校验键集合、空值、占位符、
     换行和 Minecraft `§` 格式码。
   - 返回空值、缺键或格式异常时会自动对失败键单独重试一次；重试仍失败只跳过
     对应键，其余成功翻译仍可预览合并。
   - AI 返回后先显示差异预览，确认后才备份并合并到目标语言文件。
   - API 调用可能产生费用，具体以模型服务商计费规则为准。

6. **导入翻译并合并**
   - 支持导入 JSON、Markdown JSON 代码块，以及 `key=value`、
     `key<Tab>value` 文本。
   - 可直接导入此前“导出待翻译键”生成的文件，填写译文后自动合并。
   - 只合并主语言中存在的非空普通键；分组标题、未知键和空值会被跳过并统计。
   - 已有译文可被导入值更新，但保存前会显示完整差异预览。

7. **跨命名空间汇总**
   - 按语言代码选择多个命名空间文件并合并导出。
   - 同名同值键自动去重；同名不同值键必须在冲突窗口中逐项选择来源。

## 保存与恢复

- 写入前会显示内容或差异预览。
- 原地排序、同步或覆盖已有导出文件前，会在同一目录创建时间戳备份，例如：

```text
zh_cn.json.bak.20260606-213000
```

- 原地更新会保留原文件的 UTF-8 BOM、CRLF/LF 换行风格以及末尾换行状态。
- 若要恢复，将备份文件复制回原文件名即可。

## 错误检查

以下情况会停止对应操作并显示具体文件：

- JSON 语法错误
- 根节点不是 JSON 对象
- 键或值不是字符串
- 文件无法读取或写入

同一个键重复出现时，工具会按照常见 JSON 解析规则采用最后一项的值，并在比较状态中
区分提示同值重复和不同值重复。“一键排序整理”保存后会把这些重复行整理为唯一键。

运行核心逻辑测试：

```bash
python -m unittest tests.test_language_manager
```
