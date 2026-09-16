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

package io.wifi.starrailexpress.custommodifier;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.RoleTeam;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREMonitorWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerDamageTrackerComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.cca.SREWeakArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.custommodifier.CustomModifierData.ConditionData;
import io.wifi.starrailexpress.custommodifier.CustomModifierData.ConditionType;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.network.packet.CustomModifierCountdownPacket;
import io.wifi.utils.RandomSelector;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.component.DefibrillatorComponent;
import org.agmas.noellesroles.component.InfectedPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 自定义修饰符运行时引擎。
 *
 * <p>
 * <b>全局触发</b>（没有任何条件）：拥有该修饰符即持续生效——
 * 药水效果持续挂载（每 30 秒补一次，失去修饰符后最多 30 秒自然消失）、玩家属性直接加上，
 * 并在「获得该修饰符」的那一刻把指令执行一次（全局触发没有「条件满足」的时机）。
 * 界面里对全局触发不显示指令行；但配置从「条件触发」改回全局触发时指令会残留在数据里，
 * 这种残留同样按「获得时执行一次」处理。
 *
 * <p>
 * <b>条件触发</b>：每秒判定一次条件（死亡 / 聊天栏说话 / 使用物品为事件型，由对应事件触发判定）；
 * 条件由「与 / 或」逐条串联；满足时（上升到「满足」的那一次）执行指令 + 给予药水效果，
 * 若勾选了「条件触发后移除修饰符」则在触发后移除该修饰符。
 */
public final class CustomModifierRuntime {

    private static final int SECOND_TICKS = 20;
    /** 「静止不动」判定阈值：单刻位移平方不超过该值即视为未移动。 */
    private static final double STILL_EPSILON_SQR = 0.01D * 0.01D;
    /** 全局药水效果的刷新时长（tick）：失去修饰符后最多这么久自然消失。 */
    private static final int GLOBAL_EFFECT_DURATION = 20 * 30;
    /** 剩余时间低于该值时补一次全局药水效果。 */
    private static final int GLOBAL_EFFECT_REFRESH_THRESHOLD = 20 * 20;
    /** 玩家属性修饰符 id 前缀（命名空间固定为自定义修饰符命名空间）。 */
    private static final String ATTR_PREFIX = "attr_";

    private static final Map<String, State> STATES = new HashMap<>();
    /**
     * 已登记、尚未处理的死亡（玩家 UUID -> 死亡信息）。
     *
     * <p>
     * 一次死亡会先后触发 {@code OnPlayerDeath}（无击杀者）与 {@code OnPlayerDeathWithKiller}（有击杀者）
     * 两个事件。这里先把两次登记合并，等本刻末的 {@code END_SERVER_TICK} 再统一结算，
     * 保证「被谁击杀」类条件一定能拿到击杀者，同时天然避免了重复触发。
     */
    private static final Map<UUID, PendingDeath> PENDING_DEATHS = new HashMap<>();
    /** 正在倒计时的「死亡 N 秒后」条件（键 = 玩家 UUID|修饰符 id|组下标）。 */
    private static final Map<String, Countdown> COUNTDOWNS = new HashMap<>();
    private static boolean initialized = false;

    /** 一条待处理的死亡信息。 */
    private static final class PendingDeath {
        ServerPlayer killer;
        ResourceLocation reason;
        Vec3 pos;
    }

    /** 一条「死亡 N 秒后」倒计时。 */
    private static final class Countdown {
        final UUID playerId;
        final String modifierId;
        /** 该条件所在的触发组下标。 */
        final int groupIndex;
        final String label;
        final ConditionType type;
        final boolean revive;
        final Vec3 pos;
        /** 到期刻，基准为「游戏开始刻」（见 {@link #countdownNow}）。 */
        final long deadline;

        Countdown(UUID playerId, String modifierId, int groupIndex, String label, ConditionType type,
                boolean revive, Vec3 pos, long deadline) {
            this.playerId = playerId;
            this.modifierId = modifierId;
            this.groupIndex = groupIndex;
            this.label = label;
            this.type = type;
            this.revive = revive;
            this.pos = pos;
            this.deadline = deadline;
        }

        String key() {
            return playerId + "|" + modifierId + "|" + groupIndex;
        }
    }

    private CustomModifierRuntime() {
    }

    /** 每（玩家, 修饰符）的运行时状态：按触发组分开存放，组与组之间互不影响。 */
    private static final class State {
        final Map<Integer, GroupState> groups = new HashMap<>();

        GroupState group(int index) {
            return groups.computeIfAbsent(index, key -> new GroupState());
        }
    }

    /** 单个触发组的运行时状态（键 = 组在 {@code data.effectiveGroups()} 里的下标）。 */
    private static final class GroupState {
        boolean lastResult = false;
        /** 全局组：指令只在获得该修饰符时执行一次。 */
        boolean globalFired = false;
        /** 「只触发一次」的条件（按组内条件下标存）。 */
        final Set<Integer> oneShotFired = new HashSet<>();
        /** 定时类条件的「下次到期刻」（按组内条件下标存），不受整秒判定对齐影响。 */
        final Map<Integer, Long> nextDue = new HashMap<>();
        /** 「静止不动」连续计数（tick）。 */
        int stillTicks = 0;
        /** 上一刻位置（判断是否移动）。 */
        Vec3 lastStillPos;
        /** 上一次「静止刻数是否达标」的结果，用于只在翻转时判定。 */
        boolean stillSatisfied = false;
    }

    // ==================== 初始化 ====================

    public static void init() {
        if (initialized)
            return;
        initialized = true;

        // 死亡：两个事件都会触发（先无击杀者、后有击杀者），这里只登记，等本刻末统一结算
        OnPlayerDeath.EVENT.register((player, reason) -> recordDeath(player, null, reason));
        OnPlayerDeathWithKiller.EVENT.register((player, killer, reason) -> recordDeath(player, killer, reason));
        // 聊天栏说话：事件型条件（只认聊天栏文字，不含语音与指令）
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, bound) -> {
            if (sender != null) {
                triggerEvent(sender, ConditionType.SPEAK,
                        message == null ? "" : message.signedContent(), null);
            }
        });
        // 使用物品：事件型条件
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClientSide && player instanceof ServerPlayer serverPlayer) {
                triggerEvent(serverPlayer, ConditionType.USE_ITEM,
                        BuiltInRegistries.ITEM.getKey(serverPlayer.getItemInHand(hand).getItem()).toString(), null);
            }
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        });
        // 修饰符被移除时清掉运行时状态，避免残留
        ModifierRemoved.EVENT.register((player, modifier) -> {
            if (player != null && modifier instanceof CustomModifierEntry entry) {
                STATES.remove(player.getUUID() + "|" + entry.identifier());
            }
        });
        // 玩家退出时清掉全部状态
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> clearPlayerState(handler.getPlayer().getUUID()));
        // 每刻：推进「死亡 N 秒后」倒计时、结算本刻登记的死亡；每 10 刻清理一次遗留属性
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCountdowns(server);
            processPendingDeaths(server);
            if (server.getTickCount() % 10 == 0) {
                cleanupAttributes(server);
            }
        });
    }

    // ==================== 入口 ====================

    /** 由修饰符的 serverGameTickEvent 每刻调用（仅当玩家拥有该修饰符）。 */
    public static void serverTick(ServerPlayer player, SREModifier modifier) {
        if (!(modifier instanceof CustomModifierEntry entry))
            return;
        CustomModifierData data = entry.getData();
        if (data == null)
            return;

        // 仅标记使用：不判条件、不执行任何内容
        if (data.markerOnly) {
            return;
        }

        State state = state(player, entry);
        long now = gameTime(player);
        List<CustomModifierData.TriggerGroupData> groups = data.effectiveGroups();
        if (groups.isEmpty()) {
            return;
        }

        // 每个触发组各自判定、各自执行自己的内容：全局组每刻维持，条件组每秒判一次
        boolean evaluateNow = now % SECOND_TICKS == 0;
        for (int i = 0; i < groups.size(); i++) {
            CustomModifierData.TriggerGroupData group = groups.get(i);
            GroupState groupState = state.group(i);
            if (group.isGlobal()) {
                applyGlobal(player, entry, group, i, now);
                // 全局组没有「条件满足」的时机：获得该修饰符时把指令执行一次
                if (!groupState.globalFired) {
                    groupState.globalFired = true;
                    fireActions(player, entry, group);
                }
                continue;
            }
            // 含「静止不动」条件的组：每刻只做一次很便宜的位置比较来维护计数，
            // 仅当「静止刻数是否达标」发生翻转时才判定——精确到填写的刻数，
            // 其余条件仍按秒判定，避免每刻做全量判定（性能）。
            int stillThreshold = stayStillThreshold(group);
            if (stillThreshold > 0) {
                updateStillTicks(groupState, player);
                boolean satisfied = groupState.stillTicks >= stillThreshold;
                if (satisfied != groupState.stillSatisfied) {
                    groupState.stillSatisfied = satisfied;
                    evaluate(player, entry, groupState, group, null, null, null);
                    continue;
                }
            }
            if (evaluateNow) {
                evaluate(player, entry, groupState, group, null, null, null);
            }
        }
    }

    /** 组内「静止不动」条件里最小的目标刻数；没有该条件时返回 -1。 */
    private static int stayStillThreshold(CustomModifierData.TriggerGroupData group) {
        int threshold = -1;
        for (ConditionData condition : safe(group.conditions)) {
            if (parseType(condition.type) == ConditionType.STAY_STILL) {
                int value = Math.max(1, (int) Math.round(condition.value));
                threshold = threshold < 0 ? value : Math.min(threshold, value);
            }
        }
        return threshold;
    }

    /** 每刻更新「连续静止不动」计数（移动即清零）。 */
    private static void updateStillTicks(GroupState state, ServerPlayer player) {
        Vec3 pos = player.position();
        if (state.lastStillPos != null && state.lastStillPos.distanceToSqr(pos) <= STILL_EPSILON_SQR) {
            state.stillTicks++;
        } else {
            state.stillTicks = 0;
        }
        state.lastStillPos = pos;
    }

    /** 登记一次死亡（两个死亡事件都会走到这里，后到的击杀者会覆盖先前的 null）。 */
    private static void recordDeath(Player player, Player killer, ResourceLocation reason) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        PendingDeath pending = PENDING_DEATHS.computeIfAbsent(serverPlayer.getUUID(), id -> new PendingDeath());
        if (killer instanceof ServerPlayer serverKiller) {
            pending.killer = serverKiller;
        }
        pending.reason = reason;
        pending.pos = serverPlayer.position();
    }

    /** 本刻末统一结算登记过的死亡：死亡条件 + 「死亡 N 秒后」倒计时。 */
    private static void processPendingDeaths(MinecraftServer server) {
        if (PENDING_DEATHS.isEmpty()) {
            return;
        }
        List<UUID> ids = new ArrayList<>(PENDING_DEATHS.keySet());
        for (UUID id : ids) {
            PendingDeath pending = PENDING_DEATHS.remove(id);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (pending == null || player == null) {
                continue;
            }
            triggerEvent(player, ConditionType.DEATH, pending.reason == null ? "" : pending.reason.getPath(),
                    pending.killer);
            startDeathCountdowns(player, pending.pos);
        }
    }

    /** 扫描该玩家身上的自定义修饰符，为含「死亡 N 秒后」条件的修饰符启动倒计时。 */
    private static void startDeathCountdowns(ServerPlayer player, Vec3 pos) {
        WorldModifierComponent component = WorldModifierComponent.KEY.get(player.level());
        long now = countdownNow(player);
        for (SREModifier modifier : component.getModifiers(player)) {
            if (!(modifier instanceof CustomModifierEntry entry)) {
                continue;
            }
            CustomModifierData data = entry.getData();
            if (data == null || data.markerOnly) {
                continue;
            }
            List<CustomModifierData.TriggerGroupData> groups = data.effectiveGroups();
            for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
                // 全局组不判条件（条件只是切回条件组时的残留），也就不该驱动倒计时
                if (groups.get(groupIndex).isGlobal()) {
                    continue;
                }
                for (ConditionData condition : safe(groups.get(groupIndex).conditions)) {
                    ConditionType type = parseType(condition.type);
                    if (type != ConditionType.DEATH_COUNTDOWN && type != ConditionType.DEATH_COUNTDOWN_REVIVE) {
                        continue;
                    }
                    int seconds = (int) Math.max(1L, Math.round(condition.value));
                    String label = data.displayName != null && !data.displayName.isBlank() ? data.displayName
                            : data.englishId;
                    Countdown countdown = new Countdown(player.getUUID(), entry.identifier().getPath(), groupIndex,
                            label, type, type == ConditionType.DEATH_COUNTDOWN_REVIVE, pos,
                            now + seconds * SECOND_TICKS);
                    COUNTDOWNS.put(countdown.key(), countdown);
                    sendCountdown(player, countdown, true);
                    // 同一个组里只认第一个倒计时条件
                    break;
                }
            }
        }
    }

    /** 事件型条件触发时，对所有拥有对应条件自定义修饰符的玩家做一次判定。 */
    private static void triggerEvent(ServerPlayer player, ConditionType type, String payload, ServerPlayer killer) {
        WorldModifierComponent component = WorldModifierComponent.KEY.get(player.level());
        for (SREModifier modifier : component.getModifiers(player)) {
            if (!(modifier instanceof CustomModifierEntry entry))
                continue;
            CustomModifierData data = entry.getData();
            if (data == null || data.markerOnly)
                continue;
            List<CustomModifierData.TriggerGroupData> groups = data.effectiveGroups();
            State state = state(player, entry);
            for (int i = 0; i < groups.size(); i++) {
                CustomModifierData.TriggerGroupData group = groups.get(i);
                if (group.isGlobal()) {
                    continue;
                }
                boolean relevant = false;
                for (ConditionData condition : safe(group.conditions)) {
                    ConditionType conditionType = parseType(condition.type);
                    if (conditionType == null) {
                        continue;
                    }
                    // 四种「被谁击杀」条件同样由死亡事件驱动
                    if (conditionType == type
                            || (type == ConditionType.DEATH && isKillerCondition(conditionType))) {
                        relevant = true;
                        break;
                    }
                }
                if (!relevant) {
                    continue;
                }
                evaluate(player, entry, state.group(i), group, type, payload, killer);
            }
        }
    }

    /** 判定条件并按「与 / 或」串联，满足时执行触发内容。 */
    private static void evaluate(ServerPlayer player, CustomModifierEntry entry, GroupState state,
            CustomModifierData.TriggerGroupData group, ConditionType eventType, String payload,
            ServerPlayer killer) {
        List<ConditionData> conditions = safe(group.conditions);
        if (conditions.isEmpty())
            return;

        long now = gameTime(player);

        boolean result = false;
        for (int i = 0; i < conditions.size(); i++) {
            boolean value = evalCondition(player, state, conditions.get(i), i, now, eventType, payload, killer);
            if (i == 0) {
                result = value;
            } else {
                boolean or = "OR".equalsIgnoreCase(conditions.get(i - 1).logic);
                result = or ? (result || value) : (result && value);
            }
        }

        if (result) {
            if (!state.lastResult) {
                state.lastResult = true;
                fireActions(player, entry, group);
            }
        } else {
            state.lastResult = false;
        }
    }

    // ==================== 条件判定 ====================

    private static boolean evalCondition(ServerPlayer player, GroupState state, ConditionData condition, int index,
            long now, ConditionType eventType, String payload, ServerPlayer killer) {
        ConditionType type = parseType(condition.type);
        if (type == null) {
            return false;
        }

        // ---- 倒计时型条件：「死亡 N 秒后」，只在服务端倒计时结束时判定为真 ----
        if (type == ConditionType.DEATH_COUNTDOWN || type == ConditionType.DEATH_COUNTDOWN_REVIVE) {
            return eventType == type;
        }

        // ---- 「被谁击杀」条件：只在死亡时判定 ----
        if (isKillerCondition(type)) {
            if (eventType != ConditionType.DEATH || killer == null || killer == player) {
                return false;
            }
            return matchesKiller(player, killer, condition, type);
        }

        // ---- 事件型条件 ----
        if (type == ConditionType.DEATH || type == ConditionType.SPEAK || type == ConditionType.USE_ITEM) {
            if (eventType != type)
                return false;
            String kw = trim(condition.stringValue);
            if (kw.isEmpty())
                return true;
            if (type == ConditionType.SPEAK)
                return payload != null && payload.contains(kw);
            if (type == ConditionType.USE_ITEM)
                return kw.equals(payload);
            return kw.equalsIgnoreCase(payload);
        }

        // ---- 计时型条件 ----
        switch (type) {
            case TIMER: {
                // 用「到期刻」而不是 now % interval == 0：后者在间隔不是 20 的整数倍时永远不会命中
                int interval = (int) Math.max(SECOND_TICKS, Math.round(condition.value * SECOND_TICKS));
                return dueNow(state, index, now, interval);
            }
            case TIME_ANCHOR: {
                if (state.oneShotFired.contains(index))
                    return false;
                // 基准为「游戏开始」（SREGameTimeComponent），不是该修饰符生效时刻
                boolean hit = compareDouble(elapsedSeconds(player), condition.value, condition.comparison);
                if (hit) {
                    state.oneShotFired.add(index);
                }
                return hit;
            }
            case INTERVAL_CHANCE: {
                int interval = Math.max(1, condition.intervalSeconds) * SECOND_TICKS;
                if (now % interval != 0)
                    return false;
                return RandomSelector.tryChance(Math.max(0, Math.min(10000, condition.chance)), 10000);
            }
            default:
                break;
        }

        // ---- 状态型条件 ----
        return switch (type) {
            case HAS_ITEM -> {
                String id = trim(condition.stringValue);
                yield !id.isEmpty() && player.getInventory().items.stream().anyMatch(stack -> !stack.isEmpty()
                        && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id));
            }
            case COIN_AMOUNT -> compareInt(SREPlayerShopComponent.KEY.get(player).balance, condition.value,
                    condition.comparison);
            case HAS_KILLED -> compareInt(
                    SREGameWorldComponent.KEY.get(player.level()).getPlayerKills(player.getUUID()), condition.value,
                    condition.comparison);
            case PLAYER_COUNT -> compareInt(player.serverLevel().players().size(), condition.value,
                    condition.comparison);
            case ALIVE_PLAYERS -> compareInt(
                    (int) player.serverLevel().players().stream().filter(p -> !p.isSpectator()).count(),
                    condition.value, condition.comparison);
            case IS_SNEAKING -> player.isCrouching();
            case IS_SPRINTING -> player.isSprinting();
            case HAS_EFFECT -> {
                String id = trim(condition.stringValue);
                yield !id.isEmpty() && player.getActiveEffects().stream()
                        .anyMatch(effect -> effect.getEffect().getRegisteredName().equals(id));
            }
            case WORLD_TIME -> {
                long time = player.level().getDayTime() % 24000L;
                yield switch (trim(condition.worldTimeType).toUpperCase()) {
                    case "NOON" -> time >= 5000 && time < 7000;
                    case "SUNSET" -> time >= 12000 && time < 13000;
                    case "NIGHT" -> time >= 13000;
                    case "MIDNIGHT" -> time >= 17000 && time < 19000;
                    default -> time >= 0 && time < 12000;
                };
            }
            case MOOD_VALUE -> compareFloat(SREPlayerMoodComponent.KEY.get(player).getMood(), condition.value,
                    condition.comparison);
            case IS_PSYCHO -> SREPlayerPsychoComponent.KEY.get(player).getPsychoTicks() > 0;
            case IS_POISONED -> SREPlayerPoisonComponent.KEY.get(player).getPoisonTicks() > 0;
            case IS_INFECTED -> {
                InfectedPlayerComponent infected = ModComponents.INFECTED.get(player);
                yield infected != null && infected.infectedTicks > 0;
            }
            case ARMOR_AMOUNT -> compareInt(
                    SREArmorPlayerComponent.KEY.get(player).getAllArmorCount()
                            + SREWeakArmorPlayerComponent.KEY.get(player).getWeakArmor(),
                    condition.value, condition.comparison);
            case HAS_TASK -> !SREPlayerTaskComponent.KEY.get(player).tasks.isEmpty();
            case TASK_STREAK -> compareInt(SREPlayerTaskComponent.KEY.get(player).taskStreak, condition.value,
                    condition.comparison);
            case PSYCHOS_ACTIVE -> compareInt(SREGameWorldComponent.KEY.get(player.level()).getPsychosActive(),
                    condition.value, condition.comparison);
            case IS_BLACKOUT -> SREWorldBlackoutComponent.KEY.get(player.level()).isBlackoutActive();
            case IS_MONITOR_BROKEN -> SREMonitorWorldComponent.KEY.get(player.level()).isBroken();
            case NEED_TASK_TYPE -> {
                String required = trim(condition.stringValue);
                if (required.isEmpty()) {
                    yield false;
                }
                yield SREPlayerTaskComponent.KEY.get(player).tasks.values().stream().anyMatch(task -> {
                    String taskType = task.getType().name().toLowerCase();
                    return required.equals(taskType) || "random".equals(required);
                });
            }
            case PLAYER_DAMAGED_BY_PLAYER -> SREPlayerDamageTrackerComponent.hasPlayerDamage(player, now);
            case PLAYER_DAMAGED_BY_NON_PLAYER -> SREPlayerDamageTrackerComponent.hasNonPlayerDamage(player, now);
            case ELAPSED_TIME -> compareDouble(elapsedSeconds(player), condition.value, condition.comparison);
            case FAKE_POISONED -> SREPlayerPoisonComponent.KEY.get(player).fakePoison;
            case HAS_WEAK_ARMOR -> SREWeakArmorPlayerComponent.KEY.get(player).getWeakArmor() > 0;
            case STAY_STILL -> state.stillTicks >= Math.max(1, (int) Math.round(condition.value));
            default -> false;
        };
    }

    /**
     * 定时类条件（{@link ConditionType#TIMER}）的「本次是否到期」判定。
     *
     * <p>
     * 不用 {@code now % interval == 0}：判定只发生在 {@link #SECOND_TICKS} 的整数倍刻上，
     * 与「间隔的整数倍」未必重合（间隔不是 20 的整数倍时几乎永远不重合），
     * 会出现「填了 1.5 秒却完全不触发 / 填了 0.5 秒反而每秒都触发」。
     * 这里记录下次到期刻，按实际经过的时间判定。
     *
     * <p>
     * 到期刻按「条件下标」存放：工具里改完配置重载后条件对象会换成新的，下标仍可对齐，
     * 但间隔被改小时要把过远的到期刻拉近，否则会继续等旧的长间隔。
     */
    private static boolean dueNow(GroupState state, int index, long now, int interval) {
        Long due = state.nextDue.get(index);
        if (due == null) {
            // 第一次判定：从当前时刻起算一个完整间隔（「拥有该修饰符后每 N 秒」）
            state.nextDue.put(index, now + interval);
            return false;
        }
        if (now < due) {
            if (due - now > interval) {
                state.nextDue.put(index, now + interval);
            }
            return false;
        }
        // 中间若漏判（长时间没走到这里），直接跳到下一个未到期的周期，不补触发
        long next = due;
        while (next <= now) {
            next += interval;
        }
        state.nextDue.put(index, next);
        return true;
    }

    // ==================== 「被谁击杀」条件 ====================

    /** 是否为「被谁击杀」类条件（都由死亡事件驱动判定）。 */
    private static boolean isKillerCondition(ConditionType type) {
        return type == ConditionType.KILLED_BY_SAME_TEAM || type == ConditionType.KILLED_BY_TEAM
                || type == ConditionType.KILLED_BY_ROLE || type == ConditionType.KILLED_BY_MODIFIER;
    }

    private static ConditionType parseType(String name) {
        try {
            return ConditionType.valueOf(name);
        } catch (Exception e) {
            return null;
        }
    }

    /** 判定「被谁击杀」类条件。 */
    private static boolean matchesKiller(ServerPlayer victim, ServerPlayer killer, ConditionData condition,
            ConditionType type) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(victim.level());
        SRERole killerRole = game.getRole(killer);
        return switch (type) {
            case KILLED_BY_SAME_TEAM -> {
                // 「同阵营」按阵营大类比较（好人 / 中立 / 杀手方中立 / 杀手 / 警长），与角色权重系统一致
                int victimType = PlayerRoleWeightManager.getRoleType(game.getRole(victim));
                int killerType = PlayerRoleWeightManager.getRoleType(killerRole);
                yield victimType > 0 && victimType == killerType;
            }
            case KILLED_BY_TEAM -> {
                RoleTeam team = parseTeam(condition.stringValue);
                yield team != null && team.matches(killerRole);
            }
            case KILLED_BY_ROLE -> {
                String id = trim(condition.stringValue);
                yield !id.isEmpty() && killerRole != null && matchesContentId(killerRole.identifier(), id);
            }
            case KILLED_BY_MODIFIER -> {
                String id = trim(condition.stringValue);
                yield !id.isEmpty() && WorldModifierComponent.KEY.get(victim.level()).getModifiers(killer).stream()
                        .anyMatch(modifier -> modifier != null && matchesContentId(modifier.identifier(), id));
            }
            default -> false;
        };
    }

    private static RoleTeam parseTeam(String name) {
        try {
            return RoleTeam.valueOf(trim(name).toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    /** 职业 / 修饰符 id 匹配：完整 id（{@code namespace:path}）与 path 都认。 */
    private static boolean matchesContentId(ResourceLocation id, String expected) {
        if (id == null) {
            return false;
        }
        return id.toString().equals(expected) || id.getPath().equals(expected);
    }

    // ==================== 「死亡 N 秒后」倒计时 ====================

    /** 每刻推进倒计时：到点触发内容（按需复活），玩家被其它途径复活或游戏结束时取消。 */
    private static void tickCountdowns(MinecraftServer server) {
        if (COUNTDOWNS.isEmpty()) {
            return;
        }
        for (var iterator = COUNTDOWNS.entrySet().iterator(); iterator.hasNext();) {
            var mapEntry = iterator.next();
            Countdown countdown = mapEntry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(countdown.playerId);
            if (player == null) {
                iterator.remove();
                continue;
            }
            // 已经被别的途径复活 / 游戏已结束：取消倒计时
            if (GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player) || !GameUtils.isGameRunning(player)) {
                iterator.remove();
                sendCountdown(player, countdown, false);
                continue;
            }
            if (countdownNow(player) < countdown.deadline) {
                continue;
            }
            iterator.remove();
            sendCountdown(player, countdown, false);
            completeCountdown(player, countdown);
        }
    }

    /** 倒计时结束：找到对应的自定义修饰符并触发内容；勾了「附带复活」时再复活玩家。 */
    private static void completeCountdown(ServerPlayer player, Countdown countdown) {
        WorldModifierComponent component = WorldModifierComponent.KEY.get(player.level());
        boolean found = false;
        for (SREModifier modifier : component.getModifiers(player)) {
            if (!(modifier instanceof CustomModifierEntry entry))
                continue;
            if (!entry.identifier().getPath().equals(countdown.modifierId))
                continue;
            CustomModifierData data = entry.getData();
            if (data == null || data.markerOnly)
                continue;
            List<CustomModifierData.TriggerGroupData> groups = data.effectiveGroups();
            if (countdown.groupIndex < 0 || countdown.groupIndex >= groups.size()) {
                continue;
            }
            found = true;
            evaluate(player, entry, state(player, entry).group(countdown.groupIndex),
                    groups.get(countdown.groupIndex), countdown.type, null, null);
        }
        if (!found || !countdown.revive) {
            return;
        }
        if (GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)) {
            return;
        }
        // 复活：先清掉自己的尸体，再走与除颤器一致的复活流程（回死亡点 + 安全时间）
        PlayerBodyEntity body = DefibrillatorComponent.findPlayerBodyEntity(player);
        if (body != null) {
            body.discard();
        }
        Vec3 pos = countdown.pos == null ? player.position() : countdown.pos;
        GameUtils.revivePlayer(player, pos.x, pos.y, pos.z);
    }

    /** 通知客户端倒计时状态（只发给当事人）。 */
    private static void sendCountdown(ServerPlayer player, Countdown countdown, boolean active) {
        long remaining = Math.max(0L, countdown.deadline - countdownNow(player));
        int seconds = (int) ((remaining + 19L) / 20L);
        ServerPlayNetworking.send(player,
                new CustomModifierCountdownPacket(countdown.modifierId, countdown.groupIndex, countdown.label,
                        seconds, countdown.revive, active));
    }

    // ==================== 触发内容 ====================

    /** 触发某一组的内容（指令 + 药水效果 + 按需移除修饰符）。 */
    private static void fireActions(ServerPlayer player, CustomModifierEntry entry,
            CustomModifierData.TriggerGroupData group) {
        for (String command : safe(group.commands)) {
            executeCommand(command, player);
        }
        for (CustomModifierData.EffectData effect : safe(group.effects)) {
            applyEffect(player, effect, Math.max(1, effect.durationSeconds));
        }
        // 全局组没有「触发」这一时机，文档里也写明全局触发时该开关不生效
        if (group.removeModifierOnTrigger && !group.isGlobal()) {
            WorldModifierComponent.KEY.get(player.level()).removeModifier(player, entry);
            STATES.remove(key(player, entry));
        }
    }

    /** 全局组：持续药水效果 + 玩家属性。 */
    private static void applyGlobal(ServerPlayer player, CustomModifierEntry entry,
            CustomModifierData.TriggerGroupData group, int groupIndex, long now) {
        for (CustomModifierData.EffectData effect : safe(group.effects)) {
            applyPermanentEffect(player, effect);
        }
        applyAttributes(player, entry, group, groupIndex);
    }

    private static void applyPermanentEffect(ServerPlayer player, CustomModifierData.EffectData effect) {
        Holder<MobEffect> holder = resolveEffect(effect.effectId);
        if (holder == null)
            return;
        MobEffectInstance current = player.getEffect(holder);
        if (current != null && (current.isInfiniteDuration()
                || current.getDuration() > GLOBAL_EFFECT_REFRESH_THRESHOLD)) {
            return;
        }
        player.addEffect(new MobEffectInstance(holder, GLOBAL_EFFECT_DURATION, Math.max(0, effect.amplifier), false,
                false, true));
    }

    private static void applyEffect(ServerPlayer player, CustomModifierData.EffectData effect, int durationSeconds) {
        Holder<MobEffect> holder = resolveEffect(effect.effectId);
        if (holder == null)
            return;
        player.addEffect(new MobEffectInstance(holder, durationSeconds * SECOND_TICKS, Math.max(0, effect.amplifier),
                false, true, true));
    }

    /** 全局触发的玩家属性：只在缺失时添加，失去修饰符后由 {@link #cleanupAttributes} 移除。 */
    private static void applyAttributes(ServerPlayer player, CustomModifierEntry entry,
            CustomModifierData.TriggerGroupData group, int groupIndex) {
        List<CustomModifierData.AttributeData> attributes = safe(group.attributes);
        for (int i = 0; i < attributes.size(); i++) {
            CustomModifierData.AttributeData config = attributes.get(i);
            Holder<Attribute> holder = resolveAttribute(config.attributeId);
            if (holder == null)
                continue;
            AttributeInstance instance = player.getAttribute(holder);
            if (instance == null)
                continue;
            ResourceLocation id = attributeId(entry, groupIndex, i);
            if (instance.getModifier(id) == null) {
                instance.addTransientModifier(
                        new AttributeModifier(id, config.value, AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    /** 移除「玩家已不再拥有对应自定义修饰符」的属性加成。 */
    private static void cleanupAttributes(MinecraftServer server) {
        List<Holder.Reference<Attribute>> attributes = BuiltInRegistries.ATTRIBUTE.holders().toList();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Set<ResourceLocation> expected = expectedAttributeIds(player);
            for (Holder.Reference<Attribute> attribute : attributes) {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance == null)
                    continue;
                List<ResourceLocation> toRemove = new ArrayList<>();
                for (AttributeModifier modifier : instance.getModifiers()) {
                    ResourceLocation id = modifier.id();
                    if (id != null && CustomModifierData.NAMESPACE.equals(id.getNamespace())
                            && !expected.contains(id)) {
                        toRemove.add(id);
                    }
                }
                for (ResourceLocation id : toRemove) {
                    instance.removeModifier(id);
                }
            }
        }
    }

    private static Set<ResourceLocation> expectedAttributeIds(ServerPlayer player) {
        Set<ResourceLocation> result = new HashSet<>();
        WorldModifierComponent component = WorldModifierComponent.KEY.get(player.level());
        for (SREModifier modifier : component.getModifiers(player)) {
            if (!(modifier instanceof CustomModifierEntry entry))
                continue;
            CustomModifierData data = entry.getData();
            if (data == null || data.markerOnly)
                continue;
            List<CustomModifierData.TriggerGroupData> groups = data.effectiveGroups();
            for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
                CustomModifierData.TriggerGroupData group = groups.get(groupIndex);
                // 属性只在全局组里常驻
                if (!group.isGlobal()) {
                    continue;
                }
                List<CustomModifierData.AttributeData> attributes = safe(group.attributes);
                for (int i = 0; i < attributes.size(); i++) {
                    result.add(attributeId(entry, groupIndex, i));
                }
            }
        }
        return result;
    }

    /** 属性修饰符 id：带上组下标，多组之间的同名属性不会互相覆盖。 */
    private static ResourceLocation attributeId(CustomModifierEntry entry, int groupIndex, int index) {
        String path = entry.identifier().getPath() + "_" + ATTR_PREFIX + groupIndex + "_" + index;
        return ResourceLocation.fromNamespaceAndPath(CustomModifierData.NAMESPACE, path);
    }

    // ==================== 指令 ====================

    private static void executeCommand(String command, ServerPlayer player) {
        if (command == null || command.isBlank() || player.getServer() == null)
            return;
        String processed = command
                .replace("<player>", player.getGameProfile().getName())
                .replace("~ ~ ~", String.format("%.1f %.1f %.1f", player.getX(), player.getY(), player.getZ()));
        try {
            player.getServer().getCommands().performPrefixedCommand(
                    player.getServer().createCommandSourceStack()
                            .withPermission(SREConfig.instance().customModifierPermission)
                            .withSuppressedOutput()
                            .withEntity(player)
                            .withLevel(player.serverLevel())
                            .withPosition(player.position())
                            .withRotation(player.getRotationVector()),
                    processed);
        } catch (Exception e) {
            SRE.LOGGER.warn("[CustomModifier] Failed to execute command '{}': {}", processed, e.getMessage());
        }
    }

    // ==================== 工具 ====================

    private static Holder<MobEffect> resolveEffect(String id) {
        ResourceLocation location = id == null || id.isBlank() ? null : ResourceLocation.tryParse(id.trim());
        if (location == null)
            return null;
        return BuiltInRegistries.MOB_EFFECT.getHolder(location).orElse(null);
    }

    private static Holder<Attribute> resolveAttribute(String id) {
        ResourceLocation location = id == null || id.isBlank() ? null : ResourceLocation.tryParse(id.trim());
        if (location == null)
            return null;
        return BuiltInRegistries.ATTRIBUTE.getHolder(location).orElse(null);
    }

    /**
     * 游戏开始后经过的秒数。
     *
     * <p>
     * 基准是「游戏开始」而不是该修饰符生效时刻：直接取
     * {@link SREGameTimeComponent} 的 {@code resetTime - time}（与实体交互方块一致）。
     * 取不到时返回 -1，使比较类条件不成立，避免误触发。
     */
    private static long elapsedSeconds(ServerPlayer player) {
        try {
            SREGameTimeComponent timeComponent = SREGameTimeComponent.KEY.get(player.serverLevel());
            return Math.max(0L, (timeComponent.getResetTime() - timeComponent.getTime()) / SECOND_TICKS);
        } catch (Exception e) {
            return -1L;
        }
    }

    private static long gameTime(ServerPlayer player) {
        return player.level().getGameTime();
    }

    /**
     * 「死亡 N 秒后」倒计时专用的时间基准：自游戏开始以来的刻数，时间冻结时不递增，
     * 因此会议等冻结期间倒计时会停住（与客户端 HUD 用的时钟一致）。
     */
    private static long countdownNow(ServerPlayer player) {
        return GameUtils.getTicksFromGameStart(player.level());
    }

    private static State state(ServerPlayer player, CustomModifierEntry entry) {
        return STATES.computeIfAbsent(key(player, entry), k -> new State());
    }

    private static String key(ServerPlayer player, CustomModifierEntry entry) {
        UUID uuid = player.getUUID();
        return uuid + "|" + entry.identifier();
    }

    private static boolean compareInt(int actual, double target, String comparison) {
        int expected = (int) target;
        return switch (trim(comparison).toUpperCase()) {
            case "GREATER" -> actual > expected;
            case "LESS" -> actual < expected;
            case "GREATER_EQUAL" -> actual >= expected;
            case "LESS_EQUAL" -> actual <= expected;
            default -> actual == expected;
        };
    }

    private static boolean compareDouble(double actual, double target, String comparison) {
        return switch (trim(comparison).toUpperCase()) {
            case "GREATER" -> actual > target;
            case "LESS" -> actual < target;
            case "GREATER_EQUAL" -> actual >= target;
            case "LESS_EQUAL" -> actual <= target;
            default -> Math.abs(actual - target) < 0.001D;
        };
    }

    private static boolean compareFloat(float actual, double target, String comparison) {
        return compareDouble(actual, target, comparison);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }

    /** 供外部（例如失去修饰符时）清理某个玩家的全部运行时状态。 */
    public static void clearPlayerState(UUID playerId) {
        if (playerId == null)
            return;
        String prefix = playerId + "|";
        STATES.keySet().removeIf(key -> key.startsWith(prefix));
        COUNTDOWNS.keySet().removeIf(key -> key.startsWith(prefix));
        PENDING_DEATHS.remove(playerId);
    }
}
