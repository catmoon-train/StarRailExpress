# 时间回溯 API

`org.agmas.noellesroles.api.time.TimeRewind` 提供服务端玩家的内存快照与恢复接口。它用于技能、事件和扩展模组，不是跨重启的存档格式。

## 默认回溯范围

- Vanilla 玩家状态：位置、朝向、速度、生命、饥饿、经验、背包、末影箱、能力、游戏模式、药水效果、空气、燃烧/冰冻、物品冷却等。
- 所在维度，以及仍然存在的载具和观察镜头关系。
- 玩家实体上注册的 CCA 组件。
- 恢复后的背包、能力、生命、经验、移动、药水效果及 CCA 网络同步。

玩家快照针对一个玩家，不会隐式回滚世界。游戏区域内的掉落物和 `SmallDoor` 可通过独立的区域快照回溯；其它世界方块、全局计时器或普通实体仍不在默认范围内。

## 基本用法

所有方法必须在服务端线程调用：

```java
TimeRewindSnapshot snapshot = TimeRewind.capture(player);

// 稍后恢复同一个 ServerPlayer
TimeRewindResult result = TimeRewind.restore(player, snapshot);
if (!result.isSuccess()) {
    LOGGER.warn("Partial rewind: {}", result.failures());
}
```

快照绑定玩家 UUID。用另一名玩家恢复会被拒绝，不会修改目标玩家。

需要播放完整动画时，使用平滑恢复。服务端会以 smoother-step 曲线把玩家从当前位置拉回节点，抵达后才执行精确的 vanilla/CCA 恢复；回调仍在服务端线程执行：

```java
boolean started = TimeRewind.restoreSmooth(player, snapshot, 50, result -> {
    if (!result.isSuccess()) {
        LOGGER.warn("Partial rewind: {}", result.failures());
    }
});

TimeRewind.cancelSmoothRestore(player); // 取消动画，不应用节点
```

同维度会逐 tick 平滑移动；跨维度会在原地播放时空蓄力并在结束 tick 精确换维。播放期间由 `ServerGamePacketListenerTimeRewindMixin` 屏蔽移动包，避免客户端输入与轨迹互相拉扯。shader、紫青色时间尾迹、到达闪光和声音由同一个持续时间驱动。

## 游戏区域快照

区域快照记录指定维度和 AABB 内的全部 `ItemEntity`（完整实体 NBT、UUID、位置、速度、年龄、拾取延迟和实体 CCA）以及 `SmallDoorBlockEntity`。C4 掉落物额外恢复实体外的引爆、粘附和归属状态，且不会影响区域外 C4。门会恢复上下两格方块状态、开关、卡住/破门、自动关闭倒计时、交互冷却、钥匙和物证数据。

```java
AABB playArea = AreasWorldComponent.KEY.get(level).getPlayArea();
TimeRewindAreaSnapshot areaSnapshot = TimeRewind.captureArea(level, playArea);

// 稍后恢复同一维度的游戏区域
TimeRewindAreaResult areaResult = TimeRewind.restoreArea(level, areaSnapshot);
```

恢复时会删除区域中当前存在的掉落物，以及从快照区域移动到其它位置或维度的原掉落物，然后使用原 UUID 重建快照实体。门扫描按已加载区块执行，不会遍历区域内的每一个方块，也不会为回溯强制加载区块。

## 排除控制状态

负责触发回溯的状态机和技能冷却通常不应被它自己的快照覆盖：

```java
TimeRewindOptions options = TimeRewindOptions.builder()
        .excludeComponent(MyRewindComponent.KEY)
        .excludeComponent(SREAbilityPlayerComponent.KEY)
        .build();

TimeRewindSnapshot snapshot = TimeRewind.capture(player, options);
```

滞时鬼使用的正是这个模式，因此完整状态会回溯，但 120 秒技能冷却不会被清除。

### 默认不回溯的局外组件

所有 `TimeRewindOptions.builder()` 都默认排除以下跨对局/账号数据：

- `starrailexpress:player_skins`
- `starrailexpress:player_progression`
- `starrailexpress:nametag_inventory`

因此皮肤、皮肤货币、成长等级、任务进度和称号库存不会被时间回溯覆盖，也不会触发数据库数据倒退。确有特殊需求时可通过 `includeComponent(...)` 显式允许，但普通对局技能不应这样做。

## loose_end 接入

难民/亡命徒阶段的 `RefugeeComponent.SavePlayersStats/LoadPlayersStats` 已改为使用本 API。阶段开始时为每个存活玩家保存完整玩家快照，并保存游戏区域掉落物与 `SmallDoor`；阶段结束时一并恢复。`PlayerStatsBeforeRefugee` 原有的位置、背包、金币、心情和护盾快照仍然保留，在 Mixin 或完整玩家状态恢复失败时作为兼容回退。亡命徒阶段原有的尸体清理、安全时间、棒球棍规则、回放记录和语音重置继续在完整恢复后执行。

## CCA 回溯规则

每个玩家 CCA key 只在采样和恢复时各序列化一次，优先级如下：

1. 已注册的 `TimeRewindComponentAdapter`。
2. `RoleComponent.writeToSyncNbt/readFromSyncNbt`。本项目的职业组件默认不写持久化 NBT，因此这一层不可省略。
3. 标准 CCA `Component.writeToNbt/readFromNbt`。

所有组件完成恢复后才统一调用 `ComponentKey.sync(player)`，避免客户端看到半恢复状态，也不会产生逐 tick 的 CCA 网络流量。

### RoleData

`starrailexpress:role_data` 已注册内置专用适配器，并以较晚的恢复优先级运行：先恢复其依赖的玩家 CCA，再校验职业，必要时重建正确的 `RoleData` 实例，最后读回职业状态。因此 `PelicanRoleData` 的吞食人数、腹中玩家、唯一吞食集合和技能冷却等会随节点恢复；它不是局外 progression。

`RoleData` 新增了不会写进普通存档、也不会额外暴露给客户端的回溯契约：

```java
default void writeToRewindNbt(CompoundTag tag, HolderLookup.Provider registries);
default void readFromRewindNbt(CompoundTag tag, HolderLookup.Provider registries);
```

默认实现合并 `writeToSyncNbt/readFromSyncNbt` 与可选的服务器侧 `writeToNbt/readFromNbt`，所以现有职业自动兼容，服务器私有字段也不必塞进网络同步包。如果某职业还有不属于这两种表示的临时可变状态，应只覆盖回溯方法补齐字段；不要为了回溯扩大网络同步包。

如果组件的同步 NBT 不是完整的服务端状态，可注册专用适配器：

```java
TimeRewind.registerComponentAdapter(MY_COMPONENT_KEY,
        new TimeRewindComponentAdapter<MyComponent>() {
            @Override
            public void writeSnapshot(MyComponent component, CompoundTag tag,
                    HolderLookup.Provider registries) {
                tag.putInt("serverValue", component.serverValue());
            }

            @Override
            public void readSnapshot(MyComponent component, CompoundTag tag,
                    HolderLookup.Provider registries) {
                component.setServerValue(tag.getInt("serverValue"));
            }
        });
```

适配器应在模组初始化时注册，必须只读写传入的快照标签，且不能保存该标签的引用。组件被移除或适配器缺失时，恢复会继续处理其它状态，并在 `TimeRewindResult.failures()` 中报告局部失败。

## Mixin seam

`ServerPlayerTimeRewindMixin` 只承担 vanilla 没有提供单一公开接口的工作：对 live `ServerPlayer` 读写完整 NBT、保存物品冷却、载具和观察镜头，并在恢复后集中刷新连接侧缓存和必要数据包。`ServerGamePacketListenerTimeRewindMixin` 只负责平滑播放期间的输入隔离。业务代码不应直接调用 Mixin 接口；统一通过 `TimeRewind.capture/restore/restoreSmooth` 使用。

## 测试与控制指令

下列指令需要权限等级 2，测试节点只保存在内存中：

- `/sre:rewind capture [targets]`：捕获玩家节点。
- `/sre:rewind restore [targets] [ticks]`：平滑回溯，默认 50 tick。
- `/sre:rewind cancel [targets]`：取消动画且不应用节点。
- `/sre:rewind visual <targets> [ticks]`：只预览 shader/动画时钟。
- `/sre:rewind area capture|restore`：测试当前世界游戏区域的掉落物与 SmallDoor。
- `/sre:rewind roledata [targets]`：检查测试节点是否包含 RoleData 专用适配快照，并显示当前实现类。
- `/sre:rewind status`：显示玩家节点、区域节点和正在播放的数量。
- `/sre:rewind clear`：清空测试节点，不影响正在播放的动画。
