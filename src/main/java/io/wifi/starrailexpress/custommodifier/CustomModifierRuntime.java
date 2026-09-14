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
import io.wifi.starrailexpress.custommodifier.CustomModifierData.ConditionData;
import io.wifi.starrailexpress.custommodifier.CustomModifierData.ConditionType;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.utils.RandomSelector;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.agmas.harpymodloader.modifiers.SREModifier;
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
 * 药水效果持续挂载（每 30 秒补一次，失去修饰符后最多 30 秒自然消失）、玩家属性直接加上；
 * 「执行指令」这一行在界面中不会出现，所以全局触发不执行指令。
 *
 * <p>
 * <b>条件触发</b>：每秒判定一次条件（死亡 / 说话 / 使用物品为事件型，由对应事件触发判定）；
 * 条件由「与 / 或」逐条串联；满足时（上升到「满足」的那一次）执行指令 + 给予药水效果，
 * 若勾选了「条件触发后移除修饰符」则在触发后移除该修饰符。
 */
public final class CustomModifierRuntime {

    private static final int SECOND_TICKS = 20;
    /** 全局药水效果的刷新时长（tick）：失去修饰符后最多这么久自然消失。 */
    private static final int GLOBAL_EFFECT_DURATION = 20 * 30;
    /** 剩余时间低于该值时补一次全局药水效果。 */
    private static final int GLOBAL_EFFECT_REFRESH_THRESHOLD = 20 * 20;
    /** 玩家属性修饰符 id 前缀（命名空间固定为自定义修饰符命名空间）。 */
    private static final String ATTR_PREFIX = "attr_";

    private static final Map<String, State> STATES = new HashMap<>();
    /** 玩家 UUID -> 上次处理死亡的游戏刻（用于两个死亡事件去重）。 */
    private static final Map<UUID, Long> LAST_DEATH_FIRE = new HashMap<>();
    private static boolean initialized = false;

    private CustomModifierRuntime() {
    }

    /** 每（玩家, 修饰符）的运行时状态。 */
    private static final class State {
        boolean lastResult = false;
        final Set<Integer> oneShotFired = new HashSet<>();
    }

    // ==================== 初始化 ====================

    public static void init() {
        if (initialized)
            return;
        initialized = true;

        // 死亡：事件型条件。与实体交互方块一致，两个事件都挂（无击杀者 / 被玩家击杀）
        OnPlayerDeath.EVENT.register((player, reason) -> handleDeath(player, reason));
        OnPlayerDeathWithKiller.EVENT.register((player, killer, reason) -> handleDeath(player, reason));
        // 说话：事件型条件
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, bound) -> {
            if (sender != null) {
                triggerEvent(sender, ConditionType.SPEAK,
                        message == null ? "" : message.signedContent());
            }
        });
        // 使用物品：事件型条件
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClientSide && player instanceof ServerPlayer serverPlayer) {
                triggerEvent(serverPlayer, ConditionType.USE_ITEM,
                        BuiltInRegistries.ITEM.getKey(serverPlayer.getItemInHand(hand).getItem()).toString());
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
        // 定期清理「已失去修饰符」的玩家属性加成
        ServerTickEvents.END_SERVER_TICK.register(server -> {
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

        State state = state(player, entry);
        long now = gameTime(player);

        if (data.isGlobalTrigger()) {
            applyGlobal(player, entry, data, now);
            return;
        }
        // 条件触发：每秒判定一次
        if (now % SECOND_TICKS != 0)
            return;
        evaluate(player, entry, data, null, null);
    }

    /**
     * 死亡处理：两个死亡事件都注册了，同一刻只处理一次，避免被玩家击杀时重复触发。
     */
    private static void handleDeath(Player player, ResourceLocation reason) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        long now = serverPlayer.level().getGameTime();
        Long last = LAST_DEATH_FIRE.get(serverPlayer.getUUID());
        if (last != null && last == now) {
            return;
        }
        LAST_DEATH_FIRE.put(serverPlayer.getUUID(), now);
        triggerEvent(serverPlayer, ConditionType.DEATH, reason == null ? "" : reason.getPath());
    }

    /** 事件型条件触发时，对所有拥有对应条件自定义修饰符的玩家做一次判定。 */
    private static void triggerEvent(ServerPlayer player, ConditionType type, String payload) {
        WorldModifierComponent component = WorldModifierComponent.KEY.get(player.level());
        for (SREModifier modifier : component.getModifiers(player)) {
            if (!(modifier instanceof CustomModifierEntry entry))
                continue;
            CustomModifierData data = entry.getData();
            if (data == null || data.isGlobalTrigger())
                continue;
            boolean relevant = false;
            for (ConditionData condition : safe(data.conditions)) {
                if (type.name().equals(condition.type)) {
                    relevant = true;
                    break;
                }
            }
            if (!relevant)
                continue;
            evaluate(player, entry, data, type, payload);
        }
    }

    /** 判定条件并按「与 / 或」串联，满足时执行触发内容。 */
    private static void evaluate(ServerPlayer player, CustomModifierEntry entry, CustomModifierData data,
            ConditionType eventType, String payload) {
        List<ConditionData> conditions = safe(data.conditions);
        if (conditions.isEmpty())
            return;

        State state = state(player, entry);
        long now = gameTime(player);

        boolean result = false;
        for (int i = 0; i < conditions.size(); i++) {
            boolean value = evalCondition(player, state, conditions.get(i), i, now, eventType, payload);
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
                fireActions(player, entry, data);
            }
        } else {
            state.lastResult = false;
        }
    }

    // ==================== 条件判定 ====================

    private static boolean evalCondition(ServerPlayer player, State state, ConditionData condition, int index,
            long now, ConditionType eventType, String payload) {
        ConditionType type;
        try {
            type = ConditionType.valueOf(condition.type);
        } catch (Exception e) {
            return false;
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
                int interval = (int) (condition.value * SECOND_TICKS);
                return interval > 0 && now % interval == 0;
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
            default -> false;
        };
    }

    // ==================== 触发内容 ====================

    private static void fireActions(ServerPlayer player, CustomModifierEntry entry, CustomModifierData data) {
        for (String command : safe(data.commands)) {
            executeCommand(command, player);
        }
        for (CustomModifierData.EffectData effect : safe(data.effects)) {
            applyEffect(player, effect, Math.max(1, effect.durationSeconds));
        }
        if (data.removeModifierOnTrigger) {
            WorldModifierComponent.KEY.get(player.level()).removeModifier(player, entry);
            STATES.remove(key(player, entry));
        }
    }

    /** 全局触发：持续药水效果 + 玩家属性。 */
    private static void applyGlobal(ServerPlayer player, CustomModifierEntry entry, CustomModifierData data, long now) {
        for (CustomModifierData.EffectData effect : safe(data.effects)) {
            applyPermanentEffect(player, effect);
        }
        applyAttributes(player, entry, data);
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
    private static void applyAttributes(ServerPlayer player, CustomModifierEntry entry, CustomModifierData data) {
        List<CustomModifierData.AttributeData> attributes = safe(data.attributes);
        for (int i = 0; i < attributes.size(); i++) {
            CustomModifierData.AttributeData config = attributes.get(i);
            Holder<Attribute> holder = resolveAttribute(config.attributeId);
            if (holder == null)
                continue;
            AttributeInstance instance = player.getAttribute(holder);
            if (instance == null)
                continue;
            ResourceLocation id = attributeId(entry, i);
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
            if (data == null || !data.isGlobalTrigger())
                continue;
            List<CustomModifierData.AttributeData> attributes = safe(data.attributes);
            for (int i = 0; i < attributes.size(); i++) {
                result.add(attributeId(entry, i));
            }
        }
        return result;
    }

    private static ResourceLocation attributeId(CustomModifierEntry entry, int index) {
        String path = entry.identifier().getPath() + "_" + ATTR_PREFIX + index;
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
                            .withPermission(2)
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
        LAST_DEATH_FIRE.remove(playerId);
    }
}
