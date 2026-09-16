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

package io.wifi.starrailexpress.customitem;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.DevItems;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义列车物品加载器。
 *
 * <p>
 * 与 {@code CustomRoleLoader} / {@code CustomModifierLoader} 对应：服务端从存档读
 * {@code sre_custom_items.json} 建索引，客户端从 config 目录读同步副本；
 * 所有自定义列车物品都是同一个注册物品 + {@link SREDataComponentTypes#CUSTOM_ITEM_ID} 组件。
 */
public final class CustomItemLoader {

    /** 物品 id -> 配置数据 */
    private static final Map<String, CustomItemData> loadedItems = new LinkedHashMap<>();

    /** 原版基础攻速修饰的 id（{@code Item.BASE_ATTACK_SPEED_ID} 的等价写法）。 */
    private static final ResourceLocation BASE_ATTACK_SPEED_ID = ResourceLocation.withDefaultNamespace("base_attack_speed");

    private CustomItemLoader() {
    }

    // ==================== 重载 ====================

    /** 服务端重载（从世界存档读取，权威）。 */
    public static void reload(MinecraftServer server) {
        loadedItems.clear();
        CustomItemConfig config;
        if (server != null) {
            Path worldPath = server.getWorldPath(LevelResource.ROOT);
            config = CustomItemConfig.loadFromFile(worldPath);
        } else {
            config = CustomItemConfig.getInstance();
        }
        if (config == null || config.items == null) {
            config = new CustomItemConfig();
        }
        registerAll(config);
        SRE.LOGGER.info("[CustomItem] Loaded {} custom items", loadedItems.size());
    }

    /** 客户端重载（从 config 目录读取同步副本）。 */
    public static void reloadClient() {
        loadedItems.clear();
        CustomItemConfig config = CustomItemConfig.loadFromDefaultPath();
        registerAll(config);
        SRE.LOGGER.info("[CustomItem-Client] Reloaded {} custom items from local config", loadedItems.size());
    }

    private static void registerAll(CustomItemConfig config) {
        if (config == null || config.items == null) {
            return;
        }
        for (CustomItemData data : config.items) {
            if (data == null) {
                continue;
            }
            try {
                data.sanitize();
                if (data.id.isBlank()) {
                    continue;
                }
                if (loadedItems.containsKey(data.id)) {
                    SRE.LOGGER.error("[CustomItem] Duplicated item id: {}", data.id);
                    continue;
                }
                loadedItems.put(data.id, data);
            } catch (Exception e) {
                SRE.LOGGER.error("[CustomItem] Failed to load item: {}",
                        data == null ? "null" : data.id, e);
            }
        }
    }

    /** 清理全部索引（离开服务器 / 重载前）。 */
    public static void removeAll() {
        loadedItems.clear();
    }

    /** 与 {@code CustomRoleLoader.removeClientCache()} 对应。 */
    public static void removeClientCache() {
        removeAll();
    }

    // ==================== 查询 ====================

    public static CustomItemData get(String id) {
        return id == null ? null : loadedItems.get(id);
    }

    public static boolean hasAny() {
        return !loadedItems.isEmpty();
    }

    /** 已加载的自定义列车物品数据快照（用于编辑界面）。 */
    public static List<CustomItemData> getAllData() {
        return new ArrayList<>(loadedItems.values());
    }

    /** 注册物品本身（默认无材质）。 */
    public static Item customItem() {
        Item item = DevItems.CUSTOM_ITEM;
        if (item == null || item == Items.AIR) {
            item = BuiltInRegistries.ITEM.get(SRE.id("custom_item"));
        }
        return item;
    }

    /** 读取物品栈上的自定义物品 id（非自定义列车物品返回空串）。 */
    public static String getCustomItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String id = stack.get(SREDataComponentTypes.CUSTOM_ITEM_ID);
        return id == null ? "" : id;
    }

    /** 物品栈对应的配置数据（非自定义列车物品 / 未找到返回 null）。 */
    public static CustomItemData getData(ItemStack stack) {
        String id = getCustomItemId(stack);
        return id.isEmpty() ? null : get(id);
    }

    /**
     * 物品配置的枪械手持姿势（非自定义列车物品返回 null，客户端渲染可直接用）。
     */
    public static CustomItemData.HoldPose holdPose(ItemStack stack) {
        CustomItemData data = getData(stack);
        return data == null ? null : data.holdPose();
    }

    /** 物品性质（{@code Kind}；非自定义列车物品返回 null，客户端渲染可直接用）。 */
    public static CustomItemData.Kind kind(ItemStack stack) {
        CustomItemData data = getData(stack);
        return data == null ? null : data.kind();
    }

    /**
     * 枪械道具的手持姿势（非枪械道具返回 null）。
     *
     * <p>
     * {@code holdPose} 的默认值是 {@link CustomItemData.HoldPose#REVOLVER}，但它只有枪械道具读才有意义：
     * 蓄力道具 / 投掷物 / 食物等性质读它，只会被这个默认值污染（被套上持枪姿势、被追踪枪口位置）。
     * 客户端所有读 {@code holdPose} 的地方都应该走这里。
     */
    public static CustomItemData.HoldPose gunHoldPose(ItemStack stack) {
        return kind(stack) == CustomItemData.Kind.GUN ? holdPose(stack) : null;
    }

    /**
     * 是否该按「左轮手枪式」持枪渲染（手臂伸直 + 枪口位置追踪）。
     *
     * <p>
     * 必须是<b>枪械道具</b>把 {@code holdPose} 设成
     * {@link CustomItemData.HoldPose#REVOLVER} 才算。
     */
    public static boolean isHeldLikeRevolver(ItemStack stack) {
        return gunHoldPose(stack) == CustomItemData.HoldPose.REVOLVER;
    }

    // ==================== 构建物品栈 ====================

    /** 按配置构建一个自定义列车物品栈。 */
    public static ItemStack buildStack(CustomItemData data, int count) {
        Item item = customItem();
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, Math.max(1, Math.min(64, count)));
        applyData(stack, data);
        return stack;
    }

    /** 把配置数据写进物品栈（名称 / tooltip / 食物 / 攻速 / 弹药 / 自定义 id）。 */
    public static void applyData(ItemStack stack, CustomItemData data) {
        if (stack == null || stack.isEmpty() || data == null) {
            return;
        }
        data.sanitize();
        stack.set(SREDataComponentTypes.CUSTOM_ITEM_ID, data.id);

        if (data.displayName != null && !data.displayName.isBlank()) {
            stack.set(DataComponents.ITEM_NAME, Component.literal(data.displayName));
        } else {
            stack.remove(DataComponents.ITEM_NAME);
        }

        List<Component> lore = new ArrayList<>();
        for (String line : data.tooltip) {
            if (line == null || line.isEmpty()) {
                continue;
            }
            lore.add(Component.literal(line)
                    .withStyle(style -> style.withItalic(false).withColor(0xFFAFAFAF)));
        }
        if (lore.isEmpty()) {
            stack.remove(DataComponents.LORE);
        } else {
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }

        if (data.kind() == CustomItemData.Kind.FOOD) {
            stack.set(DataComponents.FOOD, new FoodProperties.Builder()
                    .nutrition(data.nutrition)
                    .saturationModifier((float) data.saturation)
                    .build());
        } else {
            stack.remove(DataComponents.FOOD);
        }

        if (data.kind() == CustomItemData.Kind.VANILLA_WEAPON) {
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                    .add(Attributes.ATTACK_SPEED,
                            new AttributeModifier(BASE_ATTACK_SPEED_ID, data.attackSpeed,
                                    AttributeModifier.Operation.ADD_VALUE),
                            EquipmentSlotGroup.MAINHAND)
                    .build());
        } else {
            stack.remove(DataComponents.ATTRIBUTE_MODIFIERS);
        }

        if (data.kind() == CustomItemData.Kind.GUN && data.ammoSystem) {
            Integer current = stack.get(SREDataComponentTypes.AMMO_COUNT);
            if (current == null || current < 0 || current > data.maxAmmo) {
                stack.set(SREDataComponentTypes.AMMO_COUNT, data.maxAmmo);
            }
        } else {
            stack.remove(SREDataComponentTypes.AMMO_COUNT);
        }
    }

    // ==================== 指令执行 ====================

    /** 批量执行指令（空串自动跳过）。 */
    public static void executeCommands(List<String> commands, ServerPlayer base) {
        executeCommands(commands, base, null);
    }

    /**
     * 批量执行指令，并把 {@code <attacker>} 替换为「手持该道具影响 base 的玩家」。
     *
     * @param attacker 影响 base 的玩家（道具使用者 / 攻击者）；为 null 时 {@code <attacker>}
     *                 等同于 {@code <player>}
     */
    public static void executeCommands(List<String> commands, ServerPlayer base, ServerPlayer attacker) {
        if (commands == null || base == null) {
            return;
        }
        for (String command : commands) {
            executeCommand(command, base, attacker);
        }
    }

    /** 执行一条配置指令（{@code <attacker>} 等同于 {@code <player>}）。 */
    public static void executeCommand(String command, ServerPlayer base) {
        executeCommand(command, base, null);
    }

    /**
     * 执行一条配置指令。
     *
     * <p>
     * 语义与自定义职业一致：{@code <player>} 替换为 {@code base} 的玩家名，{@code <attacker>}
     * 替换为「手持该道具影响 {@code base} 的玩家」（{@code attacker} 为 null 时等同于 {@code <player>}），
     * {@code ~ ~ ~} 替换为 {@code base} 的坐标，{@code @p} 替换为距离 {@code base} 最近的其他存活玩家。
     *
     * <p>
     * 「被作用 / 被击中的玩家执行的指令」把 {@code base} 传成该玩家、{@code attacker} 传成使用者即可。
     */
    public static void executeCommand(String command, ServerPlayer base, ServerPlayer attacker) {
        if (command == null || base == null) {
            return;
        }
        // 允许按游戏里的习惯带前导斜杠写（"/say x" 与 "say x" 等价）
        String raw = command.trim();
        if (raw.startsWith("/")) {
            raw = raw.substring(1);
        }
        if (raw.isBlank()) {
            return;
        }
        MinecraftServer server = base.getServer();
        if (server == null) {
            return;
        }
        String processed = processCommandSelectors(raw
                .replace("<player>", base.getGameProfile().getName())
                .replace("<attacker>", attacker != null ? attacker.getGameProfile().getName()
                        : base.getGameProfile().getName())
                .replace("~ ~ ~", String.format("%.1f %.1f %.1f", base.getX(), base.getY(), base.getZ())),
                base);
        try {
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack()
                            .withPermission(SREConfig.instance().customRolePermission)
                            .withSuppressedOutput()
                            .withEntity(base)
                            .withLevel(base.serverLevel())
                            .withPosition(base.position())
                            .withRotation(base.getRotationVector()),
                    processed);
        } catch (Exception e) {
            SRE.LOGGER.warn("[CustomItem] Failed to execute configured command '{}': {}", processed, e.getMessage());
        }
    }

    /** 处理指令中的 {@code @p} 选择器（其余 {@code @s @a @r} 由 Minecraft 原生解析）。 */
    private static String processCommandSelectors(String cmd, ServerPlayer base) {
        if (cmd == null || !cmd.contains("@p")) {
            return cmd;
        }
        var level = base.serverLevel();
        var alivePlayers = level.getPlayers(p -> GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p));
        ServerPlayer nearest = null;
        double minDist = Double.MAX_VALUE;
        for (ServerPlayer p : alivePlayers) {
            if (p == base) {
                continue;
            }
            double dist = base.distanceToSqr(p);
            if (dist < minDist) {
                minDist = dist;
                nearest = p;
            }
        }
        return cmd.replace("@p", nearest != null ? nearest.getGameProfile().getName() : base.getGameProfile().getName());
    }
}
