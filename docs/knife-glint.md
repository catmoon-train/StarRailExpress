# 平民与中立真刀光泽

- 平民或中立玩家持有真刀时，第一/第三人称持物显示附魔光泽；鸟兽兽的尼泊尔军刀继承 `KnifeItem`，同样生效。
- `FakeKnifeItem` 假刀不显示该光泽。其他阵营、非刀物品沿用原有附魔显示，不删除真实附魔。
- 仅在 `ItemRenderer.renderStatic` 的持有人上下文内覆盖 `ItemStack.hasFoil()` 返回值；空实体、GUI、嵌套渲染不继承别人的身份，finally 恢复上下文。不改物品组件、战斗属性、隐藏物品规则或按键。
- `G:/exmo/sre-skin` 已把原始物品栈传入 GeckoLib，其 `GeoItemRenderer` 使用 `hasFoil()` 和原版 `getFoilBufferDirect`，原生兼容，无需修改刀皮源码。更新主模组客户端以启用规则；刀皮仓库本次独立构建因旧主模组依赖API缺失未通过，未生成新刀皮产物。

自动测试覆盖纯判定与渲染接线；第一/第三人称3D模型实际光泽仍需游戏内验收。
