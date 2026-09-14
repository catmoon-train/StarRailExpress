/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.disguise;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.disguise.ClientEntityDisguiseCache;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 通用实体伪装 API：把玩家伪装成任意已注册实体。
 * <p>
 * 外观整体替换——客户端用目标实体的渲染器绘制玩家（玩家本体、名牌、附属物一起被替换），
 * 同时把眼高压到该实体眼高。**碰撞箱尺寸保持不变**：地图是按人的尺寸做的，只改眼高，
 * 于是相机、准星射线、枪械命中判定一起下移，画面与命中点不会错开。
 * <p>
 * 三种结束方式：
 * <ul>
 * <li><b>时长</b>：{@code durationTicks > 0}，走到期自动解除（用游戏时间，暂停时不推进）。</li>
 * <li><b>自定义条件</b>：传入 {@link Predicate}，每 tick 求值，{@code test} 返回 {@code true} 即解除。</li>
 * <li><b>长期</b>：{@code durationTicks <= 0} 且不传条件，直到 {@link #clear} 或开局 / 结束重置。</li>
 * </ul>
 * 不枚举任何具体实体：状态只存「实体类型 + 外观 NBT」，眼高在设置时用一个临时实体算一次，
 * 之后查询都是 O(1) 查表，渲染与命中路径上不创建实体。
 *
 * <pre>{@code
 * // 长期伪装成牛
 * EntityDisguise.disguise(serverPlayer, EntityType.COW);
 *
 * // 30 秒后自动解除
 * EntityDisguise.disguise(serverPlayer, EntityType.COW, 20 * 30);
 *
 * // 带外观 NBT 的伪装（羊的颜色、史莱姆尺寸……）
 * EntityDisguise.disguise(serverPlayer, EntityType.SHEEP, nbt, 0);
 *
 * // 直接传入一个实体：类型与外观 NBT 一并复制
 * EntityDisguise.disguise(serverPlayer, someEntity, 20 * 30);
 *
 * // 自定义结束条件：下水即解除
 * EntityDisguise.disguise(serverPlayer, EntityType.COW, null, 0, Player::isInWater);
 *
 * EntityDisguise.clear(serverPlayer);
 * boolean disguised = EntityDisguise.isDisguised(player);
 * }</pre>
 */
public final class EntityDisguise {

    /**
     * 与客户端渲染无关、或不该跟着伪装走的键。刻意只列「确定非外观」的：
     * <ul>
     * <li>身份标记（无外观作用）：{@code id} / {@code UUID}</li>
     * <li>位置与运动：{@code Pos} / {@code Motion} / {@code Rotation} / {@code FallDistance} / {@code OnGround}
     * ——位置由客户端逐帧从客户端自己的玩家实体上抄，见 {@code EntityDisguiseRenderer#copyPlayerState}</li>
     * <li>生存状态：{@code Health} / {@code Air} / {@code Fire} / {@code Invulnerable} / {@code PortalCooldown}
     * / {@code HurtTime} / {@code HurtByTimestamp} / {@code DeathTime} / {@code AbsorptionAmount}</li>
     * <li>AI 与服务端数据：{@code Brain} / {@code Memories} / 各类目标与计时器 / 掉落表 / 拴绳 / 刷新与持久化标记</li>
     * <li>{@code CustomNameVisible}：伪装永远不显示名牌，所以只留名字本身、不带上可见标记
     * （见 {@link #CustomName} 为什么不删）</li>
     * </ul>
     * 这些键如果与裸实体不同（受伤、AI 记忆、被拴绳……）就会被差集留下，所以这里再兜一层。
     * <p>
     * <b>刻意保留</b>：
     * <ul>
     * <li>{@code CustomName}——原版有些外观效果就是靠名字触发的：{@code jeb_} 绵羊（彩虹毛）、
     * {@code Toast} 兔子、{@code Dinnerbone} / {@code Grumm}（倒过来）。删掉名字这些效果全部失效。
     * 渲染时只把 {@code CustomNameVisible} 按掉，所以不会凭空多出名牌。</li>
     * <li>{@code Tags}——让指令能区分「这一类伪装」，例如
     * {@code /sre:disguise start infinite @p minecraft:cow {Tags:["boss_cow"]}} 之后用
     * {@code /execute if data sre:disguise @p Tags} 就能筛人。空标签列表等于默认值，
     * 会被差集自动丢掉，不占包体。</li>
     * </ul>
     */
    private static final Set<String> NON_RENDER_KEYS = Set.of(
            "CustomNameVisible", "id", "UUID",
            "Pos", "Motion", "Rotation", "FallDistance", "OnGround",
            "Health", "Air", "Fire", "Invulnerable", "PortalCooldown",
            "HurtTime", "HurtByTimestamp", "DeathTime", "AbsorptionAmount",
            "Brain", "Memories", "WanderTarget", "PatrolTarget", "HomePos", "DespawnDelay",
            "PersistenceRequired", "CanPickUpLoot", "DeathLootTable", "DeathLootTableSeed",
            "Leash", "CannotEnterHiveTicks", "SleepTimer", "SleepingX", "SleepingY", "SleepingZ");

    private EntityDisguise() {
    }

    // ------------------------------------------------------------------ 查询

    /**
     * 当前伪装状态（服务端读管理器，客户端读同步缓存），未伪装时返回
     * {@link EntityDisguiseState#NONE}。
     */
    public static EntityDisguiseState get(@Nullable Player player) {
        if (player == null) {
            return EntityDisguiseState.NONE;
        }
        Level level = player.level();
        if (level != null && level.isClientSide) {
            return ClientEntityDisguiseCache.get(player.getUUID());
        }
        return EntityDisguiseManager.get(player.getUUID());
    }

    public static EntityDisguiseState get(@Nullable UUID uuid) {
        return EntityDisguiseManager.get(uuid);
    }

    /** 是否处于伪装状态。 */
    public static boolean isDisguised(@Nullable Player player) {
        return !get(player).isNone();
    }

    public static boolean isDisguised(@Nullable UUID uuid) {
        return !get(uuid).isNone();
    }

    // ------------------------------------------------------------------ 设置

    /** 伪装成指定实体类型，长期有效，直到 {@link #clear} 或开局 / 结束重置。 */
    public static boolean disguise(ServerPlayer player, EntityType<?> type) {
        return disguise(player, type, null, 0, null);
    }

    /**
     * @param durationTicks 持续 tick；{@code <=0} 表示长期
     */
    public static boolean disguise(ServerPlayer player, EntityType<?> type, int durationTicks) {
        return disguise(player, type, null, durationTicks, null);
    }

    public static boolean disguise(ServerPlayer player, EntityType<?> type, @Nullable CompoundTag nbt,
            int durationTicks) {
        return disguise(player, type, nbt, durationTicks, null);
    }

    /**
     * 核心入口。
     *
     * @param nbt          外观 NBT（可为 null），会剥掉名牌、身份与位置等无关字段
     * @param durationTicks 持续 tick；{@code <=0} 表示长期
     * @param endPredicate 自定义结束条件（可为 null）
     */
    public static boolean disguise(ServerPlayer player, EntityType<?> type, @Nullable CompoundTag nbt,
            int durationTicks, @Nullable Predicate<ServerPlayer> endPredicate) {
        if (player == null || type == null) {
            return false;
        }
        if (type == EntityType.PLAYER) {
            // 玩家模型需要 AbstractClientPlayer，包装成人形会直接 ClassCastException；
            // 「看起来是别的玩家」请用 MorphApi。
            SRE.LOGGER.warn("EntityDisguise 不支持伪装成 minecraft:player，请改用 MorphApi");
            return false;
        }
        CompoundTag clean = sanitizeNbt(nbt);
        // 一次探针同时得到「要发的外观 NBT」与眼高。
        Projection projection = project(player.level(), type, clean);
        EntityDisguiseState state = EntityDisguiseState.of(type, projection.renderNbt(), projection.eyeHeight());
        return EntityDisguiseManager.set(player, state, expireAt(durationTicks), endPredicate);
    }

    /**
     * 伪装成「某个具体实体」：复制它的类型与外观 NBT（羊的颜色、狼的项圈、史莱姆尺寸、
     * 村民职业……都在 NBT 里）。
     */
    public static boolean disguise(ServerPlayer player, Entity entity) {
        return disguise(player, entity, 0, null);
    }

    public static boolean disguise(ServerPlayer player, Entity entity, int durationTicks) {
        return disguise(player, entity, durationTicks, null);
    }

    public static boolean disguise(ServerPlayer player, Entity entity, int durationTicks,
            @Nullable Predicate<ServerPlayer> endPredicate) {
        if (entity == null) {
            return false;
        }
        return disguise(player, entity.getType(), entity.saveWithoutId(new CompoundTag()), durationTicks,
                endPredicate);
    }

    /** 解除伪装。返回是否实际发生了变化。 */
    public static boolean clear(ServerPlayer player) {
        return EntityDisguiseManager.clear(player, false);
    }

    /**
     * 覆写现有伪装的外观 NBT（保留时长 / predicate 等结束条件），返回是否真的改了。
     * <p>
     * 主要给 {@code /data modify ... sre:disguise} 用；默认值味道的键不会被差集投影丢掉，
     * 也就是「你写什么就是什么」，便于指令来回读写。
     */
    public static boolean setNbt(ServerPlayer player, @Nullable CompoundTag nbt) {
        return EntityDisguiseManager.setNbt(player, nbt);
    }

    /**
     * 剩余伪装时间（游戏刻），服务端可读（不动包体）。
     *
     * @return {@code -1} 表示没有到期时间（无限期或由 predicate 结束）；{@code 0} 表示未伪装或已到期
     */
    public static int getRemainingTicks(@Nullable Player player) {
        return EntityDisguiseManager.remainingTicks(player == null ? null : player.getUUID());
    }

    /** 是否挂了自定义结束条件（predicate）。 */
    public static boolean hasEndPredicate(@Nullable Player player) {
        return EntityDisguiseManager.hasEndPredicate(player == null ? null : player.getUUID());
    }

    /** 清空全部玩家的伪装并同步到客户端（服务端）。 */
    public static void clearAll(MinecraftServer server) {
        EntityDisguiseManager.resetAll(server);
    }

    // ------------------------------------------------------- 眼高（mixin 入口）

    /**
     * 供 {@code Player#getDefaultDimensions} 钩子调用：返回只改了眼高的尺寸，未伪装时返回 {@code null}。
     * <p>
     * 取 {@code min(玩家当前姿态眼高, 实体眼高)}：游泳、睡觉等本就低于实体的姿态不会被抬高，
     * 末影人这类高个子实体也不会把玩家眼高抬起来。碰撞箱 width / height 原样保留。
     * <p>
     * 这条路径每帧都会被问到，所以只做一次 {@code isEmpty()} 快速返回 + 一次哈希查找，不创建任何对象或实体。
     */
    public static @Nullable EntityDimensions applyEyeHeight(Player self, EntityDimensions original) {
        if (self == null || original == null) {
            return null;
        }
        Level level = self.level();
        EntityDisguiseState state = level != null && level.isClientSide
                ? (ClientEntityDisguiseCache.isEmpty() ? EntityDisguiseState.NONE
                        : ClientEntityDisguiseCache.get(self.getUUID()))
                : (EntityDisguiseManager.isEmpty() ? EntityDisguiseState.NONE
                        : EntityDisguiseManager.get(self.getUUID()));
        float eyeHeight = state.eyeHeight();
        // 负值是「算不出来」的哨兵值，此时保持原样，别把相机按到脚底。
        if (state.isNone() || eyeHeight < 0.0F) {
            return null;
        }
        return original.withEyeHeight(Math.min(original.eyeHeight(), eyeHeight));
    }

    /**
     * 计算伪装成该实体后的眼高：用一个临时实体带上外观 NBT 取站立尺寸，
     * 这样史莱姆尺寸、幼年体这类「由 NBT 决定尺寸」的实体也自动正确，无需枚举。
     * <p>
     * 只在设置伪装时调用一次。失败返回 {@code -1}（哨兵值，表示保持玩家原眼高）。
     */
    public static float computeEyeHeight(@Nullable Level level, EntityType<?> type, @Nullable CompoundTag nbt) {
        return project(level, type, nbt).eyeHeight();
    }

    /**
     * 投影结果：真正需要发到客户端的外观 NBT + 眼高。
     *
     * @param renderNbt 只含「偏离同类型裸实体默认值」的键；为 {@code null} 表示客户端建一个裸实体就是对的
     */
    public record Projection(@Nullable CompoundTag renderNbt, float eyeHeight) {
    }

    /**
     * 用一个临时实体一次算完两件事：眼高 + 需要发给客户端的外观 NBT。
     * <p>
     * NBT 取的是**与「同类型裸实体」的逐键差集**：一个正常生成的生物，它的
     * {@code Health / Attributes / Air / Motion / Pos / Brain…} 全等于默认值，于是全部被丢掉，
     * 只剩真正的变体信息（羊的颜色、史莱姆尺寸、村民职业、装备、幼年……）。客户端重建时也是从
     * 裸实体起步再套这份差集，所以结果与服务端完全一致——而包体通常从几百字节降到几十字节。
     * <p>
     * 只在设置伪装时执行一次（两次 {@code saveWithoutId} + 一次按键比较），不在 tick 或渲染路径上。
     */
    public static Projection project(@Nullable Level level, EntityType<?> type, @Nullable CompoundTag nbt) {
        if (type == null) {
            return new Projection(null, -1.0F);
        }
        float fallbackEye = type.getDimensions().eyeHeight();
        if (level == null) {
            return new Projection(sanitizeNbt(nbt), fallbackEye);
        }
        try {
            Entity probe = type.create(level);
            if (probe == null) {
                return new Projection(sanitizeNbt(nbt), fallbackEye);
            }
            // 基准必须先取：baseline 与 actual 来自同一个探针实体，所以身份、坐标、空气、
            // 默认属性这些「没变过」的键会自然相等而被丢掉。
            CompoundTag baseline = probe.saveWithoutId(new CompoundTag());
            if (nbt != null) {
                probe.load(nbt);
            }
            float eyeHeight = probe.getDimensions(Pose.STANDING).eyeHeight();
            CompoundTag renderNbt = diff(baseline, probe.saveWithoutId(new CompoundTag()));
            return new Projection(renderNbt.isEmpty() ? null : renderNbt, eyeHeight);
        } catch (Throwable throwable) {
            SRE.LOGGER.warn("EntityDisguise 计算投影失败，回退到原样发送 NBT {}", EntityType.getKey(type), throwable);
            return new Projection(sanitizeNbt(nbt), fallbackEye);
        }
    }

    /**
     * 实体类型的本地化名字（{@code entity.minecraft.cow} → 「牛」/「Cow」）。
     * 其他模组的实体没提供翻译时回退到 {@code namespace:path}，不会显示成裸的翻译键。
     */
    public static Component displayName(EntityType<?> type) {
        if (type == null) {
            return Component.empty();
        }
        return Component.translatableWithFallback(type.getDescriptionId(), EntityType.getKey(type).toString());
    }

    private static CompoundTag diff(CompoundTag baseline, CompoundTag actual) {
        CompoundTag result = new CompoundTag();
        for (String key : actual.getAllKeys()) {
            if (NON_RENDER_KEYS.contains(key)) {
                continue;
            }
            Tag value = actual.get(key);
            Tag reference = baseline.get(key);
            if (value != null && (reference == null || !value.equals(reference))) {
                result.put(key, value.copy());
            }
        }
        return result;
    }

    /**
     * 去掉不该跟着伪装走、或与客户端渲染无关的字段。
     * <p>
     * 名字与身份（否则伪装期间头顶飘着实体名字、临时实体还会被套上别的 UUID）；
     * 坐标 / 速度 / 朝向（位置由客户端逐帧从**客户端自己的玩家实体**上抄，见
     * {@code EntityDisguiseRenderer#copyPlayerState}，所以这些不但没必要发，发过来也只是徒增流量）；
     * 生命 / 空气 / 落地 / 传送门冷却等生存状态；以及 Brain / Memories 这类可能很大又纯服务端的 AI 数据。
     * <p>
     * 刻意**不**动 {@code Age}(幼年)、{@code Size}(史莱姆)、{@code Color}/{@code variant}、
     * {@code Sheared}、{@code CollarColor}、{@code Saddle}/{@code ChestedHorse}、{@code Owner}(项圈)、
     * {@code HandItems}/{@code ArmorItems}、{@code ActiveEffects} 等会影响外观的键。
     */
    public static @Nullable CompoundTag sanitizeNbt(@Nullable CompoundTag nbt) {
        if (nbt == null) {
            return null;
        }
        CompoundTag copy = nbt.copy();
        for (String key : NON_RENDER_KEYS) {
            copy.remove(key);
        }
        return copy;
    }

    private static long expireAt(int durationTicks) {
        if (durationTicks <= 0) {
            return 0;
        }
        return SRE.getTicksFromGameStart() + durationTicks;
    }
}
