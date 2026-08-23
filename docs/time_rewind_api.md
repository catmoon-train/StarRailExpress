# 时间回溯 API / Time Rewind API

> 本文档介绍 StarRail Express 时间回溯模块的用法，面向想为角色/游戏模式接入"回溯"能力的开发者。  
> This document explains the StarRail Express time rewind module for developers who want to hook rewind into roles or game modes.

---

## 目录 / Table of Contents

1. [快速开始 / Quick Start](#快速开始--quick-start)
2. [直接回溯（非平滑）/ Instant Restore](#直接回溯非平滑--instant-restore)
3. [核心概念 / Core Concepts](#核心概念--core-concepts)
4. [API 参考 / API Reference](#api-参考--api-reference)
5. [排队队列 / Queueing](#排队队列--queueing)
6. [旁观者保护 / Spectator Protection](#旁观者保护--spectator-protection)
7. [取消与游戏生命周期 / Cancel & Game Lifecycle](#取消与游戏生命周期--cancel--game-lifecycle)
8. [线程要求 / Threading](#线程要求--threading)
9. [区域回溯 / Area Rewind](#区域回溯--area-rewind)
10. [测试指令 / Test Command](#测试指令--test-command)
11. [注意事项 / Notes](#注意事项--notes)

---

## 快速开始 / Quick Start

**包 / Package:** `org.agmas.noellesroles.api.time`

最小用法：捕获一个节点，然后用流畅构建器平滑回溯到该节点。

```java
import org.agmas.noellesroles.api.time.TimeRewind;
import org.agmas.noellesroles.api.time.TimeRewindSnapshot;
import org.agmas.noellesroles.api.time.TimeRewindResult;

// 1. 捕获当前状态（必须在服务端线程）
TimeRewindSnapshot snapshot = TimeRewind.capture(player);

// 2. 平滑回溯：玩家被传送到节点位置并应用完整状态
TimeRewind.smoothRewind(player, snapshot)
        .duration(80)                     // 动画时长（tick），默认 50，范围 [1, 600]
        .onComplete(result -> {           // 完成/取消/失败都会调用一次
            if (result.isSuccess()) {
                // 回溯成功
            } else {
                // 查看 result.failures() 定位问题
            }
        })
        .start();
```

> **提示 / Tip:** 什么都不配置也可以：`TimeRewind.smoothRewind(player, snapshot).start()`。
> **注意 / Note:** `start()` 只会在 snapshot 不属于该玩家时返回 `false`；玩家已有进行中的回溯时会自动排队，而不是失败。
> **需要立即生效（无动画）？** 直接调用 `TimeRewind.restore(player, snapshot)`，见[直接回溯（非平滑）](#直接回溯非平滑--instant-restore)。

---

## 直接回溯（非平滑）/ Instant Restore

直接回溯不做动画、不排队、不切换旁观者：调用后**立即**把玩家状态应用为节点内容，适合不需要"播放过程"的场景（检查点、倒带结算、一次性修正）。

**包 / Package:** `org.agmas.noellesroles.api.time`

```java
TimeRewindSnapshot snapshot = TimeRewind.capture(player);
TimeRewindResult result = TimeRewind.restore(player, snapshot);
if (result.isSuccess()) {
    // 立即生效，无动画
} else {
    result.failures().forEach(f -> LOGGER.warn("[{}] {}", f.scope(), f.message()));
}
```

### 捕获过滤 / Capture Options

默认策略会跳过 `player_skins`、`player_progression`、`nametag_inventory` 三个组件（`TimeRewindOptions.DEFAULT`）。需要控制恢复范围时传入自定义选项：

```java
TimeRewindSnapshot snapshot = TimeRewind.capture(player,
        TimeRewindOptions.builder()
                .excludeComponent(ResourceLocation.fromNamespaceAndPath("starrailexpress", "player_skins"))
                .includeComponent(/* 恢复默认被跳过的组件 */)
                .build());
```

### 与平滑回溯的区别 / vs Smooth Rewind

| 维度 / Aspect | 直接回溯 `restore` | 平滑回溯 `smoothRestore` / `smoothRewind` |
| --- | --- | --- |
| 入口 / Entry | `TimeRewind.restore(player, snapshot)` | `TimeRewind.smoothRewind(player, snapshot).start()` |
| 生效方式 / Effect | 立即 / Instant | 动画传送到节点锚点后恢复 / Animated slide, then restore |
| 排队 / Queueing | 无（同步执行）/ None | 有（同一玩家自动排队）/ Per-player queue |
| 旁观切换 / Spectator | 无 / None | 有（避免受伤）/ Spectator during playback |
| 取消 / Cancel | 不可取消（同步完成）/ N/A | `cancelSmoothRestore(player)` / `cancelAllRewinds(server)` |
| 结果 / Result | 直接返回 `TimeRewindResult` | `start()` 返回 `boolean`，结果经 `onComplete` 回调 |
| 回调 / Callback | 无 / None | `onComplete(Consumer<TimeRewindResult>)` 恰好一次 |
| 断线 / Disconnect | 不适用（调用时玩家在线）/ N/A | 失败结果 + 尽力恢复模式 |

> **提示 / Tip:** 平滑回溯的最终状态与直接回溯完全一致（到达锚点后执行的是同一个 `TimeRewind.restore`），区别只在"到达"的过程。

---

## 核心概念 / Core Concepts

| 概念 / Concept | 说明 / Description |
| --- | --- |
| **捕获 / Capture** | `TimeRewind.capture(player)` 把玩家的完整状态（原版 NBT + CCA 组件）快照为 `TimeRewindSnapshot`。节点是内存中的，不是持久化存档。 |
| **立即恢复 / Instant restore** | `TimeRewind.restore(player, snapshot)` 不做动画，直接应用节点（见[直接回溯](#直接回溯非平滑--instant-restore)）。 |
| **平滑回溯 / Smooth rewind** | `TimeRewind.smoothRewind(...).start()` 或 `smoothRestore(...)`：玩家先被动画传送到节点锚点，到达后再执行与 `restore` 完全相同的状态恢复。 |
| **排队 / Queueing** | 同一玩家的多个回溯请求按顺序排队执行（见[排队队列](#排队队列--queueing)）。 |
| **旁观保护 / Spectator protection** | 回溯期间玩家自动切换为旁观者避免受伤，结束时恢复到节点记录的游戏模式（见[旁观者保护](#旁观者保护--spectator-protection)）。 |

### 相关类型 / Related Types

| 类型 / Type | 说明 / Description |
| --- | --- |
| `TimeRewind` | 唯一公开入口（门面）。 |
| `TimeRewindSnapshot` | 单个玩家的不可变节点：`playerId()`、`dimension()`、`position()`、`yRot()`/`xRot()`、`componentCount()`、`warnings()`。 |
| `TimeRewindResult` | 恢复结果：`restoredComponents()`、`failures()`、`isSuccess()`。 |
| `TimeRewindOptions` | 捕获选项：`TimeRewindOptions.DEFAULT` 或 `TimeRewindOptions.builder().excludeComponent(id).build()`。 |
| `TimeRewindAreaSnapshot` / `TimeRewindAreaResult` | 区域（掉落物、门等）的节点与结果。 |

---

## API 参考 / API Reference

### `TimeRewind`

| 方法 / Method | 说明 / Description |
| --- | --- |
| `capture(ServerPlayer)` / `capture(ServerPlayer, TimeRewindOptions)` | 捕获玩家节点。 |
| `restore(ServerPlayer, TimeRewindSnapshot)` | 立即恢复（无动画）。 |
| `smoothRewind(ServerPlayer, TimeRewindSnapshot)` | 返回 `SmoothRewindBuilder`，平滑回溯的推荐入口。 |
| `smoothRestore(ServerPlayer, TimeRewindSnapshot, int, Consumer<TimeRewindResult>)` | 平滑回溯（静态调用）。 |
| `smoothRestore(ServerPlayer, TimeRewindSnapshot, int)` | 同上，无回调。 |
| `cancelSmoothRestore(ServerPlayer)` | 取消该玩家的回溯（含整个队列），不应用节点。 |
| `cancelAllRewinds(MinecraftServer)` | 取消服务端所有回溯；游戏开始/结束时会自动调用。 |
| `isSmoothRewinding(ServerPlayer)` | 该玩家是否正在回溯（含排队中）。 |
| `activeSmoothRewinds()` | 当前正在进行回溯的玩家数。 |
| `playVisual(ServerPlayer, int)` | 只播放客户端回溯特效，不传送不恢复（预览用）。 |
| `captureArea(ServerLevel, AABB)` / `restoreArea(ServerLevel, TimeRewindAreaSnapshot)` | 区域回溯。 |
| `registerComponentAdapter(...)` | 为某个 CCA 组件注册自定义快照格式（注册表初始化时调用）。 |

### `SmoothRewindBuilder`（推荐 / Recommended）

| 方法 / Method | 说明 / Description |
| --- | --- |
| `duration(int ticks)` | 动画时长，clamp 到 `[1, 600]`（默认 50）。 |
| `onComplete(Consumer<TimeRewindResult>)` | 完成/取消/失败时恰好调用一次的回调。 |
| `start()` | 开始回溯；返回 `false` 仅当 snapshot 不属于该玩家。 |

```java
TimeRewind.smoothRewind(player, snapshot)
        .duration(100)
        .onComplete(result -> { /* 必定触发一次 */ })
        .start();
```

---

## 排队队列 / Queueing

同一个玩家可以连续发起多个回溯，它们**严格按顺序执行**：第一个先播完，之后的依次排队，等前一个完成（恢复、特效、回调全部结束）后再开始。

- 队列中的回溯在**真正激活时**才记录起点（位置/朝向/游戏模式），因此第二个回溯会从第一个回溯恢复后的位置开始滑动到它的目标。
- `isSmoothRewinding(player)` 在整个会话期间（含排队）都返回 `true`。
- 回溯期间死亡惩罚系统会把玩家视为"正在回溯"（`DeathPenaltyComponent`），排队中同样生效。

```java
// 连续入队两个回溯：A 先播，B 在 A 完成后自动开始
TimeRewind.smoothRewind(player, snapshotA).duration(40).start();
TimeRewind.smoothRewind(player, snapshotB).duration(60).start();
```

> **注意 / Note:** 多个玩家各自独立排队，互不影响。

---

## 旁观者保护 / Spectator Protection

回溯开始时，玩家会被切换为旁观者以**避免受伤、避免成为攻击目标**：

```java
player.setGameMode(GameType.SPECTATOR);
```

同时保留原有效果（隐身、无敌、禁止移动/转身、皮肤遮蔽、`TIME_REWIND_MARK`）作为第二层防护，每 tick 重新施加。

- **正常结束**：`TimeRewind.restore` 会把节点记录的游戏模式（NBT 中的 `playerGameType`）写回，因此玩家自动"变回实际 snapshot 的模式"。
- **恢复失败**：若 restore 抛异常，玩家仍处于旁观，会回退到回溯开始前的游戏模式，并清理回溯期间施加的保护效果。
- **取消**：恢复到回溯开始前的游戏模式。
- **断线**：尽力把游戏模式恢复为回溯前的值，避免重进服务器后卡在旁观者。
- 效果清理是**精确的**：只移除"会话开始时玩家本没有"的效果，不会误删玩家原有的 `SAFE_TIME`、隐身等状态。

---

## 取消与游戏生命周期 / Cancel & Game Lifecycle

### 手动取消 / Manual Cancel

```java
boolean wasActive = TimeRewind.cancelSmoothRestore(player);
```

取消语义：停止当前播放 + 清空该玩家的整个队列 + 恢复游戏模式 + 清理效果 + 对**所有**被取消的请求（当前和排队中的）回调传入一个失败的 `TimeRewindResult`（`failures()` 中标记 `cancelled during rewind`）。因此依赖回调释放资源的调用方不会挂起。

### 游戏开始 / 结束自动取消 / Auto-cancel on Game Start/End

`TimeRewindPlayback` 在初始化时注册了 `io.wifi.starrailexpress.event.OnGameStarted` 和 `OnGameEnd` 两个事件，**游戏正式开始时和游戏结束时都会立刻取消所有进行中/排队的回溯**，防止玩家以旁观者或带效果的状态进入下一局。

- 该行为在 `TimeRewind.initialize()` 中注册；`Noellesroles.onInitialize()` 已调用它，所以**服务端启动时即注册**（无需等待首次使用）。
- 需要手动触发时：`TimeRewind.cancelAllRewinds(server)`（任意线程安全，会自动调度到服务端线程）。

---

## 线程要求 / Threading

- `capture` / `restore` / `smoothRewind` / `smoothRestore` / `cancelSmoothRestore` 都**必须在 Minecraft 服务端线程**调用；`smoothRestore` 与 `cancelSmoothRestore` 在线程不对时会抛出 `IllegalStateException`。
- 播放逻辑由 `ServerTickEvents.END_SERVER_TICK` 驱动，全部在服务端线程完成。
- 内部使用 `ConcurrentHashMap` / `ConcurrentLinkedQueue`，只读查询（`isSmoothRewinding`、`activeSmoothRewinds`）任意线程安全；`cancelAllRewinds` 内部做了 `server.execute` 调度，可安全从其他线程调用。
- `completion` 回调在服务端线程执行，注意不要在回调里做长时间阻塞操作。

---

## 区域回溯 / Area Rewind

除了玩家回溯，还可以对**掉落物、SmallDoor、C4 状态**等场景状态做回溯：

```java
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import net.minecraft.world.phys.AABB;

AABB area = AreasWorldComponent.KEY.get(level).getPlayArea();
TimeRewindAreaSnapshot areaSnapshot = TimeRewind.captureArea(level, area);
TimeRewindAreaResult areaResult = TimeRewind.restoreArea(level, areaSnapshot);
```

区域回溯是立即生效的（无动画），且要求目标维度与节点一致。

---

## 测试指令 / Test Command

操作员（权限 ≥ `SREConfig.timeRewindPermission`，默认 2）可使用测试指令。节点用 ResourceLocation 区分，每个玩家可存多个节点；未指定 id 时使用默认 id `noellesroles:default`。子命令为小写+下划线风格。

```
/sre:rewind capture [id] [targets]          捕获节点（可指定 id）
/sre:rewind restore [id] [targets] [ticks]  不带 ticks = 直接回溯（立即生效）；带 ticks = 平滑回溯
/sre:rewind cancel [targets]                取消进行中的平滑回溯
/sre:rewind visual [targets] [ticks]        只播放特效预览
/sre:rewind area_capture [id]               捕获当前世界区域节点
/sre:rewind area_restore [id]               恢复当前世界区域节点
/sre:rewind roledata [id] [targets]         检查节点是否包含 RoleData 适配
/sre:rewind status                          查看节点总数与播放状态
/sre:rewind clear_all                       清空服务器全部节点（不影响进行中动画）
/sre:rewind clear_player [targets] [id]     清空指定玩家的全部节点（或仅指定 id）
/sre:rewind clear_area [id]                 清空当前世界的区域节点（或仅指定 id）
```

> 带 `ticks` 的平滑回溯在同一玩家连续执行时会排队而不是报"正在回溯"；不带 `ticks` 的直接回溯同步立即完成，无排队。

---

## 注意事项 / Notes

- `duration` 会被 clamp 到 `[1, 600]` tick（最多 30 秒）。
- snapshot 必须属于目标玩家（`snapshot.playerId()`），否则 `start()` 返回 `false`。
- 回溯期间玩家不能手动移动（移动包被 mixin 取消），服务器每 tick 会锁定位置并归零速度。
- 若玩家当前维度与节点维度不一致，平滑滑动会被跳过，但到达后仍会执行跨维度恢复。
- 玩家在回溯中断线：当前及排队的请求都会收到失败的 `TimeRewindResult`，并尽力恢复游戏模式。
