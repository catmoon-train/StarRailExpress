# 伪装皮肤占位 / Disguise Skin Placeholders

这些 `disguise_skin_N.png` 是伪装效果（`noellesroles:disguise`）期间替换玩家显示的皮肤，
每个文件对应一种伪装变体（见 `DisguiseVariants`）。

- 当前文件都是 **占位图**（64×64，纯色 + 头部正面色块），请替换为正式皮肤。
- 必须是标准 Minecraft 皮肤格式：**64×64 像素**。
- 文件名与变体的对应关系定义在
  `io/wifi/starrailexpress/content/item/DisguiseVariants.java` 的 `VARIANTS` 列表里，
  列表中还指定了每套皮肤使用 **WIDE（Steve 经典）** 还是 **SLIM（Alex 纤细）** 模型。

替换时保持文件名不变即可，无需改动代码。

## 新增一套伪装皮肤

1. 在 `DisguiseVariants.VARIANTS` 末尾追加一个 `Variant("textures/entity/disguise/xxx.png", slim?)`；
2. 在 `TMMItems` 注册一个新的 `DisguiseItem(..., 变体下标)`；
3. 放置皮肤 png（本目录，64×64）、物品图标（`textures/item/`）、物品模型
   （`assets/trainmurdermystery/models/item/`），并补充三个语言文件的物品名。

---

These `disguise_skin_N.png` files are the skins shown on a player while the
disguise effect (`noellesroles:disguise`) is active. Each file maps to one
disguise variant (see `DisguiseVariants`).

- All current files are **placeholders** (64×64, flat color with a face block).
  Replace them with real skins.
- They must be standard Minecraft skins: **64×64 pixels**.
- The file→variant mapping and the WIDE/SLIM model choice live in
  `DisguiseVariants.VARIANTS`.

Keep the filenames and no code changes are needed.

### Adding another disguise

1. Append a `Variant(...)` to `DisguiseVariants.VARIANTS`;
2. Register a new `DisguiseItem(..., variantIndex)` in `TMMItems`;
3. Add the skin png (here, 64×64), item icon, item model, and lang entries.
