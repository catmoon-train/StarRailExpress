# ModernUI 货币图片字形兼容

## 问题与原因

ModernUI Fabric 1.21.1 / 3.13.0.1 的 `TextLayoutEngine.reload()` 会重建 `minecraft:default` 的字体集合。部分默认字体策略（例如 `sDefaultFontBehavior == 0`）直接使用用户选定的 Typeface，不保留默认字体 JSON 内的 bitmap provider。

因此，私有区码位可能落到用户字体或后备字体中的另一种符号，而非金币贴图。原来的 `U+E000` 出现放大镜与这条路径吻合，但没有用户的实际字体配置，不能确认截图具体使用了哪个字体。单纯换成冷门码位只能降低撞字概率，无法保证 bitmap provider 被保留。

## 补丁

- 当前金币、代币码位继续使用 `U+F6A8`、`U+F6A9`，不再迁移。
- 增加 `starrailexpress:currency_icons` 独立字体，直接定义两个 bitmap provider；不引用 `minecraft:default`，避免再次进入默认字体替换路径。
- `ModernUICurrencyFontMixin` 在 `TextLayoutProcessor` 收集字符的入口，仅将默认字体下的这两个码位改为独立字体。普通文字和其他显式字体不变，颜色、粗体、斜体等其余样式保留。
- 入口按用户提供的 3.13.0.1 JAR 核对：`lambda$new$1(int, Style, int)`。String、FormattedText 和 FormattedCharSequence 均使用此字符 sink，改动在布局分段与测宽之前完成。
- 使用 `@Pseudo` 和 `SREMixinPlugin` 的 `modernui` 检测，无 ModernUI 时不启用；不增加 ModernUI 编译或运行依赖，不修改用户配置或第三方 JAR。
- 不接管旧码位 `E000/E001`，避免影响已经占用它们的其他模组。

## 验证与限制

- 独立字体与默认字体中的货币 provider 内容一致，资源 JSON 校验通过。
- 补丁及可选加载插件隔离编译通过；12 个字体路由用例通过（两个图标、普通字符、旧码位、相邻码位、显式其他字体），并验证颜色/粗体/斜体保留。
- 全项目 `compileJava` 被训练模块的 5 个无关编译错误阻断，不能据此交付完整构建产物。
- 未进行 Minecraft + ModernUI 的游戏内渲染或实际 Mixin 应用测试。更新 ModernUI 时需重新核对上述内部 sink 方法。

游戏内验收：开启 ModernUI 文本引擎与自选字体，检查 HUD 金币、商店价格、售货机和带代币图标的翻译文本；金币和代币应显示原贴图，数字及普通文字仍使用 ModernUI 字体。切换字体或重载资源后复查，无 ModernUI 时保持原版行为。
