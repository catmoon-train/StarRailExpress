package io.wifi.starrailexpress.settlement;

import io.wifi.starrailexpress.api.replay.GameReplayData;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes;
import io.wifi.starrailexpress.api.replay.ReplayPlayerProfile;
import io.wifi.starrailexpress.api.replay.ReplayTimelineEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.function.Function;

/** 仅为结算补全缺失的公开事件文本，不改动原回放的格式化与筛选规则。 */
public final class SettlementEventText {
    private static final String PREFIX = "settlement.starrailexpress.event.";

    private SettlementEventText() { }

    /** 保留已有组件及其样式、参数；只有空文本或原始枚举占位符才使用本地化兜底。 */
    public static Component resolve(ReplayTimelineEvent event, GameReplayData.ReplayEvent raw,
                                    Function<ResourceLocation, Component> itemName) {
        String type = event.type() == null ? "" : event.type().name();
        String legacyType = raw == null ? data(event, "legacyType") : raw.type.name();
        if (!needsFallback(event.text(), type, legacyType)) return event.text();
        String effectiveType = legacyType == null || legacyType.isBlank() ? type : legacyType;
        Component actor = playerName(event.actor());
        Component target = playerName(event.target());
        String key = fallbackKey(effectiveType);
        if ("STORE_BUY".equals(effectiveType)) {
            Purchase purchase = parsePurchase(itemUsed(event, raw), message(event, raw));
            var text = Component.translatable(key, actor, itemName(purchase.itemId(), itemName));
            if (purchase.quantity() != null)
                text.append(Component.translatable(PREFIX + "purchase_quantity", purchase.quantity()));
            if (purchase.cost() != null)
                text.append(Component.translatable(PREFIX + "purchase_cost", purchase.cost()));
            return text;
        }
        return switch (effectiveType) {
            case "ITEM_USED", "SKILL_USED", "SKILL_RELEASE" -> Component.translatable(key, actor,
                    itemName(parseItem(itemUsed(event, raw)), itemName));
            case "PLAYER_KILL", "PLAYER_POISONED", "ROPE_PULL" -> Component.translatable(key, actor, target);
            // 原始记录的 source 是设陷者，target 才是踩中陷阱的人。
            case "TRAP_TRIGGERED" -> Component.translatable(key, target, actor);
            default -> Component.translatable(key,
                    key.equals(PREFIX + "generic") && event.actor() == null ? target : actor);
        };
    }

    /** 购买物品去掉数量后缀；其他事件维持既有 itemUsed 取值方式。 */
    public static String item(ReplayTimelineEvent event, GameReplayData.ReplayEvent raw) {
        if (event.type() == ReplayEventTypes.EventType.STORE_BUY) {
            ResourceLocation id = parsePurchase(itemUsed(event, raw), message(event, raw)).itemId();
            return id == null ? null : id.toString();
        }
        String item = raw == null ? null : raw.itemUsed;
        return item == null || item.isBlank() || "minecraft:air".equals(item) ? null : item;
    }

    static boolean needsFallback(Component text, String type, String legacyType) {
        if (text == null) return true;
        String value = text.getString().strip();
        return value.isBlank() || value.equals(type) || value.equals(legacyType);
    }

    static String fallbackKey(String type) {
        String suffix = switch (type == null ? "" : type) {
            case "GAME_START", "GAME_END", "PLAYER_JOIN", "PLAYER_LEAVE", "PLAYER_KILL",
                    "PLAYER_POISONED", "TASK_COMPLETE", "STORE_BUY", "DOOR_LOCK", "DOOR_UNLOCK",
                    "DOOR_OPEN", "ARMOR_BREAK", "DOOR_CLOSE", "LOCKPICK_ATTEMPT", "ITEM_USED",
                    "MOOD_CHANGE", "PSYCHO_STATE_CHANGE", "BLACKOUT_START", "BLACKOUT_END",
                    "GRENADE_THROWN", "CHANGE_ROLE", "PLAYER_REVIVAL", "SKILL_RELEASE", "BOMB_DEFUSE",
                    "BOMB_DETONATE", "DISGUISE", "TRAP_TRIGGERED", "DOOR_PRY", "DOOR_SEAL", "ROPE_PULL",
                    "ROLE_ASSIGNMENT" -> type.toLowerCase(Locale.ROOT);
            case "SKILL_USED" -> "item_used";
            case "CUSTOM_MESSAGE", "CUSTOM_EVENT" -> "custom_event";
            default -> "generic";
        };
        return PREFIX + suffix;
    }

    /**
     * 购买记录的 itemUsed 是「命名空间:路径:数量」，message 才是价格。
     * 不把 StoreBuyDetails.cost 当作价格：旧转换器实际向该字段填入了数量。
     * 只有第二个冒号才可能分隔数量，不能把普通「命名空间:路径」拆坏。
     */
    static Purchase parsePurchase(String encodedItem, String recordedCost) {
        String item = encodedItem;
        Integer quantity = null;
        if (item != null && item.lastIndexOf(':') > item.indexOf(':')) {
            int separator = item.lastIndexOf(':');
            quantity = nonNegativeInteger(item.substring(separator + 1));
            if (quantity != null && quantity == 0) quantity = null;
            item = item.substring(0, separator);
        }
        ResourceLocation id = parseItem(item);
        return new Purchase(id, id == null ? null : quantity, nonNegativeInteger(recordedCost));
    }

    record Purchase(ResourceLocation itemId, Integer quantity, Integer cost) { }

    private static Integer nonNegativeInteger(String value) {
        if (value == null || value.isEmpty() || !value.chars().allMatch(c -> c >= '0' && c <= '9')) return null;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static ResourceLocation parseItem(String value) {
        if (value == null || value.isBlank() || "unknown".equals(value)) return null;
        ResourceLocation id = ResourceLocation.tryParse(value);
        return id == null || "minecraft:air".equals(id.toString()) ? null : id;
    }

    private static Component itemName(ResourceLocation id, Function<ResourceLocation, Component> resolver) {
        Component name = id == null ? null : resolver.apply(id);
        return name == null ? Component.translatable(PREFIX + "unknown_item") : name;
    }

    private static Component playerName(ReplayPlayerProfile player) {
        return player == null || player.name() == null || player.name().isBlank()
                ? Component.translatable(PREFIX + "unknown_player") : Component.literal(player.name());
    }

    private static String itemUsed(ReplayTimelineEvent event, GameReplayData.ReplayEvent raw) {
        return raw == null ? data(event, "itemUsed") : raw.itemUsed;
    }

    private static String message(ReplayTimelineEvent event, GameReplayData.ReplayEvent raw) {
        return raw == null ? data(event, "message") : raw.message;
    }

    private static String data(ReplayTimelineEvent event, String key) {
        return event.data() == null ? null : event.data().get(key);
    }
}
