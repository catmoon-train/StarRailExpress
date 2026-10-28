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

package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.content.item.Colors;
import io.wifi.starrailexpress.content.item.SkinableItem;
import io.wifi.starrailexpress.data.PlayerSkinEconomyManager;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 皮肤管理工具类，用于处理物品皮肤相关的操作
 */
public class ItemSkinManager {
    public static class Skin {
        public final int color;
        public final String tooltipName;

        Skin(int color, String tooltipName) {
            this.color = color;
            this.tooltipName = tooltipName;
        }

        public String getName() {
            return this.tooltipName.toLowerCase(Locale.ROOT);
        }

        public int getColor() {
            return this.color;
        }

        public static Skin fromString(String itemType, String name) {
            if (!skinMap.containsKey(itemType)) {
                return null;
            }
            var childSkinMap = skinMap.get(itemType);
            if (childSkinMap.containsKey(name.toLowerCase(Locale.ROOT))) {
                return childSkinMap.get(name.toLowerCase(Locale.ROOT));
            }
            return childSkinMap.get("default");
        }

        // public static Skin getNext(Skin skin) {
        // Skin[] values = Skin.values();
        // return values[(skin.ordinal() + 1) % values.length];
        // }
    }

    public enum QualityColor {
        COMMON(new Color(0xFFEEEEEE).getRGB()),
        UNCOMMON(new Color(0xFF33FF55).getRGB()),
        RARE(new Color(0xFFAAAAFF).getRGB()),
        EPIC(new Color(0xFFAA55FF).getRGB()),
        LEGENDARY(new Color(0xFFFFAA55).getRGB()),
        UNBELIEVABLE(new Color(0xFFFF3F3F).getRGB());

        QualityColor(int i) {
            color = i;
        }

        private final int color;

        public int getColor() {
            return color;
        }
    }

    public static Skin getSkinFromName(String itemType, String name) {
        if (!skinMap.containsKey(itemType)) {
            return null;
        }
        var childSkinMap = skinMap.get(itemType);
        if (childSkinMap.containsKey(name.toLowerCase(Locale.ROOT))) {
            return childSkinMap.get(name.toLowerCase(Locale.ROOT));
        }
        return childSkinMap.get("default");
    }

    public static class KnifeSkin {
        public static final Skin DEFAULT_SKIN = new Skin(Colors.LIGHT_GRAY, "default");
    }

    // Revolver skins
    public static class RevolverSkin {
        public static final Skin REVOLVER_DEFAULT_SKIN = new Skin(Colors.GRAY, "default");
    }

    // Grenade skins
    public static class GrenadeSkin {
        public static final Skin GRENADE_DEFAULT_SKIN = new Skin(Colors.GRAY, "default");
    }

    // Bat skins
    public static class BatSkin {
        public static final Skin BAT_DEFAULT_SKIN = new Skin(Colors.GRAY, "default");
    }

    // Hat skins
    public static class HatSkin {
        public static final Skin HAT_DEFAULT_SKIN = new Skin(Colors.GRAY, "default");
    }

    public static void registerACustomSkin(String skinType, String skinID, int color) {
        skinMap.putIfAbsent(skinType, new HashMap<>());
        skinMap.get(skinType).put(skinID, new Skin(color, skinID));
        // 分配皮肤整数ID（如未分配），用于网络包的高效同步
        skinIdByTypeMap.computeIfAbsent(skinType, k -> new HashMap<>());
        skinByIdTypeMap.computeIfAbsent(skinType, k -> new HashMap<>());
        if (!skinIdByTypeMap.get(skinType).containsKey(skinID)) {
            int id = skinIdByTypeMap.get(skinType).size();
            skinIdByTypeMap.get(skinType).put(skinID, id);
            skinByIdTypeMap.get(skinType).put(id, skinID);
        }
    }

    /**
     * 获取皮肤类型的整数ID
     */
    public static int getSkinTypeId(String typeName) {
        return skinTypeIdMap.getOrDefault(typeName, -1);
    }

    /**
     * 根据整数ID获取皮肤类型名称
     */
    public static String getSkinTypeById(int id) {
        return skinTypeByIdMap.get(id);
    }

    /**
     * 获取指定类型中皮肤的整数ID
     */
    public static int getSkinId(String typeName, String skinName) {
        HashMap<String, Integer> typeMap = skinIdByTypeMap.get(typeName);
        if (typeMap == null)
            return -1;
        return typeMap.getOrDefault(skinName, -1);
    }

    /**
     * 根据整数ID获取指定类型中皮肤的名称
     */
    public static String getSkinById(String typeName, int id) {
        HashMap<Integer, String> typeMap = skinByIdTypeMap.get(typeName);
        if (typeMap == null)
            return null;
        return typeMap.get(id);
    }

    public static class SkinTypes {
        public static final String KNIFE = "knife";
        public static final String REVOLVER = "revolver";
        public static final String BAT = "bat";
        public static final String GRENADE = "grenade";
        public static final String HAT = "hat";
        /** 音乐盒：装配后在对局中循环播放对应曲目，曲目表见 {@link MusicBoxTracks}。 */
        public static final String MUSIC_BOX = "music_box";
        // === 以下为后续扩展的可换肤物品（类型名与物品注册名一致） ===
        public static final String SHORT_SHOTGUN = "short_shotgun";
        /** 德林加手枪：亡命徒德林加手枪（desperado_gun）共用同一皮肤类型。 */
        public static final String DERRINGER = "derringer";
        /** 开锁器：劣质开锁器（inferior_lockpick）共用同一皮肤类型。 */
        public static final String LOCKPICK = "lockpick";
        public static final String CROWBAR = "crowbar";
        public static final String BODY_BAG = "body_bag";
        public static final String NOTE = "note";
        public static final String FIRECRACKER = "firecracker";
        public static final String LETTER = "letter";
        public static final String HANDCUFFS = "handcuffs";
        public static final String LOCK = "lock";
        /** 钥匙：列车钥匙与乘务员钥匙（noellesroles:master_key_p）共用；万能钥匙（master_key）不参与皮肤。 */
        public static final String KEY = "key";
        /** 原版弓：分未拉弓 / 蓄力 0~2 三个阶段。 */
        public static final String BOW = "bow";
        /** 原版弩：分待机 / 蓄力 0~2 / 装填箭 / 装填烟花阶段。 */
        public static final String CROSSBOW = "crossbow";
        /**
         * 随从：装配后以客户端虚拟实体形式跟随主人，仅改变外观、不替换任何装备。
         * 材质与建模由附属模组（sre-skin）提供，本体只负责状态、同步与运动。
         */
        public static final String PET = "pet";
    }

    /** 皮肤选择项中的「随机」伪皮肤名：每次开局从该类型已拥有的皮肤中随机抽取。 */
    public static final String RANDOM_SKIN = "random";

    protected static final HashMap<String, HashMap<String, Skin>> skinMap = new HashMap<>();
    // 皮肤类型ID映射（type name → int ID），用于网络包的高效同步
    private static final HashMap<String, Integer> skinTypeIdMap = new HashMap<>();
    // 皮肤类型反向映射（int ID → type name）
    private static final HashMap<Integer, String> skinTypeByIdMap = new HashMap<>();
    // 皮肤名称ID映射（type name → (skin name → int ID)）
    private static final HashMap<String, HashMap<String, Integer>> skinIdByTypeMap = new HashMap<>();
    // 皮肤名称反向映射（type name → (int ID → skin name)）
    private static final HashMap<String, HashMap<Integer, String>> skinByIdTypeMap = new HashMap<>();
    private static int registerId = 0;

    public static void registerType(String skinType) {
        skinTypeIdMap.putIfAbsent(skinType, registerId);
        skinTypeByIdMap.putIfAbsent(registerId, skinType);
        skinIdByTypeMap.putIfAbsent(skinType, new HashMap<>());
        skinByIdTypeMap.putIfAbsent(skinType, new HashMap<>());
        skinMap.putIfAbsent(skinType, new HashMap<>());
        registerId++;
    }

    static {
        // 初始化皮肤类型ID映射（顺序固定，确保服务端和客户端一致）
        // 新增类型只能追加到末尾，否则会打乱已同步的整数 ID
        String[] typeOrder = { SkinTypes.KNIFE, SkinTypes.REVOLVER, SkinTypes.BAT, SkinTypes.GRENADE, SkinTypes.HAT,
                SkinTypes.MUSIC_BOX,
                SkinTypes.SHORT_SHOTGUN, SkinTypes.DERRINGER, SkinTypes.LOCKPICK, SkinTypes.CROWBAR,
                SkinTypes.BODY_BAG, SkinTypes.NOTE, SkinTypes.FIRECRACKER, SkinTypes.LETTER,
                SkinTypes.HANDCUFFS, SkinTypes.LOCK, SkinTypes.KEY, SkinTypes.BOW, SkinTypes.CROSSBOW,
                SkinTypes.PET };
        for (int i = 0; i < typeOrder.length; i++) {
            registerType(typeOrder[i]);
        }
        // 更新：可以不提供默认材质
    }

    public static ResourceLocation getResourceLocationOfItem(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    public static HashMap<String, Skin> getSkins(Item it) {
        var itr = getResourceLocationOfItem(it);
        String itemName = null;
        if (itr != null) {
            itemName = itr.getPath();
        }
        return skinMap.getOrDefault(itemName, new HashMap<>());
    }

    public static HashMap<String, Skin> getSkins(String itemName) {
        return skinMap.getOrDefault(itemName, new HashMap<>());
    }

    public static HashMap<String, HashMap<String, Skin>> getSkins() {
        return skinMap;
    }

    public static Integer getLootChance(Player player) {
        return PlayerSkinEconomyManager.getLootChance(player);
    }

    public static void addLootChance(Player player, Integer chance) {
        PlayerSkinEconomyManager.addLootChance(player, chance == null ? 0 : chance);
    }

    public static Integer getCoinNum(Player player) {
        return PlayerSkinEconomyManager.getCoinNum(player);
    }

    public static void addCoinNum(Player player, Integer num) {
        PlayerSkinEconomyManager.addCoinNum(player, num == null ? 0 : num);
    }

    /**
     * 获取玩家当前装备的皮肤名称
     *
     * @param player    玩家
     * @param itemStack 物品堆栈
     * @return 皮肤名称
     */
    public static String getEquippedSkin(Player player, ItemStack itemStack) {
        // ItemStack数据优先级高于玩家自身
        String skinName;
        if (itemStack.has(SREDataComponentTypes.SKIN)) {
            skinName = itemStack.get(SREDataComponentTypes.SKIN);
        } else {
            skinName = PlayerSkinEconomyManager.getEquippedSkin(player, itemStack);
        }
        return resolveRandomSkin(player, getItemTypeName(itemStack), skinName);
    }

    /** 判断某个皮肤名是否为「随机」。 */
    public static boolean isRandomSkin(String skinName) {
        return skinName != null && RANDOM_SKIN.equalsIgnoreCase(skinName);
    }

    /**
     * 把「随机」解析成本局实际使用的皮肤名。
     * <p>
     * 解析结果由 {@code SREPlayerSkinsComponent#getResolvedSkin} 提供（开局时服务端抽取并同步），
     * 若尚未抽取则回退到未装备。
     */
    public static String resolveRandomSkin(Player player, String itemTypeName, String skinName) {
        if (!isRandomSkin(skinName)) {
            return skinName;
        }
        if (player == null) {
            return "default";
        }
        try {
            String resolved = SREPlayerSkinsComponent.KEY.get(player).getResolvedSkin(itemTypeName);
            if (resolved != null && !resolved.isBlank() && !isRandomSkin(resolved)) {
                return resolved;
            }
        } catch (Exception ignored) {
            // 组件尚未挂载（例如极早期渲染），回退到未装备
        }
        return "default";
    }

    /**
     * 从玩家在该类型下已拥有的皮肤中随机挑一个（不含未装备）。
     *
     * @return 抽中的皮肤名；若一个都没有则返回 {@code default}
     */
    public static String pickRandomUnlockedSkin(Player player, String itemTypeName) {
        List<String> pool = new ArrayList<>();
        if (player != null) {
            try {
                Map<String, Boolean> unlocked = SREPlayerSkinsComponent.KEY.get(player)
                        .getUnlockedSkinsForItemType(itemTypeName);
                for (Map.Entry<String, Boolean> entry : unlocked.entrySet()) {
                    String name = entry.getKey();
                    if (name == null || name.isBlank() || "default".equalsIgnoreCase(name) || isRandomSkin(name)) {
                        continue;
                    }
                    if (Boolean.TRUE.equals(entry.getValue()) && !pool.contains(name)) {
                        pool.add(name);
                    }
                }
            } catch (Exception ignored) {
                // 组件缺失时按「无已拥有皮肤」处理
            }
        }
        if (player == null || pool.isEmpty()) {
            return "default";
        }
        return pool.get(player.getRandom().nextInt(pool.size()));
    }

    /**
     * 玩家是否对该物品类型选择了「随机」。
     */
    public static boolean isRandomSelected(Player player, String itemTypeName) {
        if (player == null) {
            return false;
        }
        return isRandomSkin(PlayerSkinEconomyManager.getEquippedSkinForItemType(player, itemTypeName));
    }

    /**
     * 服务端盖章：把玩家当前装备的皮肤写进物品栈的 {@code SKIN} 组件。
     * <p>
     * 原版物品（弓、弩）无法覆写 {@code Item#inventoryTick}，由 mixin 调用本方法。
     * 组件会随物品栈同步给客户端，这样其他玩家手持的、物品展示框里的、掉在地上的物品
     * 都能显示持有者自己的皮肤，而不是渲染端本地玩家的皮肤。
     */
    public static void applyEquippedSkin(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }
        String itemType = getItemSkinType(stack.getItem());
        if (itemType == null) {
            return;
        }
        if (stack.get(SREDataComponentTypes.SKIN) == null) {
            stack.set(SREDataComponentTypes.SKIN, getEquippedSkin(player, stack));
        } else if (isRandomSelected(player, itemType)) {
            // 选择了「随机」：跟随每局开局的抽取结果刷新，避免沿用上一局的快照
            String resolved = resolveRandomSkin(player, itemType, RANDOM_SKIN);
            if (!resolved.equals(stack.get(SREDataComponentTypes.SKIN))) {
                stack.set(SREDataComponentTypes.SKIN, resolved);
            }
        }
    }

    /**
     * 在物品提示末尾追加「皮肤：xxx」。
     * <p>
     * 该皮肤类型还没有任何皮肤（例如弓/弩尚未添加皮肤资源）时不显示，避免无意义提示。
     *
     * @param player 物品持有者，可为 {@code null}（此时只读取物品栈上的皮肤组件）
     */
    public static void appendSkinTooltip(ItemStack stack, List<Component> tooltip, Player player) {
        String itemType = getItemSkinType(stack.getItem());
        if (itemType == null || getSkins(itemType).isEmpty()) {
            return;
        }
        String skinName;
        if (player != null) {
            skinName = getEquippedSkin(player, stack);
        } else {
            skinName = stack.get(SREDataComponentTypes.SKIN);
            if (skinName == null || skinName.isBlank()) {
                return;
            }
        }
        Skin skin = Skin.fromString(itemType, skinName);
        if (skin != null) {
            tooltip.add(Component.translatable("tip.skin").withStyle(style -> style.withColor(Colors.GRAY))
                    .append(Component.translatableWithFallback(
                            "screen.sre.skins." + itemType + "." + skin.tooltipName + ".name", skin.tooltipName)
                            .withStyle(style -> style.withColor(skin.getColor()))));
        } else if ("default".equals(skinName)) {
            tooltip.add(Component.translatable("tip.skin").withStyle(style -> style.withColor(Colors.GRAY))
                    .append(Component.translatableWithFallback("screen.sre.skins.default", "Default"))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * 为实体渲染准备一个带皮肤的 ItemStack 副本。
     */
    public static ItemStack applySkinToStack(ItemStack base, String skinName) {
        ItemStack stack = base.copy();
        String skin = (skinName == null || skinName.isBlank()) ? "default" : skinName;
        stack.set(SREDataComponentTypes.SKIN, skin);
        return stack;
    }

    /**
     * 设置玩家当前装备的皮肤
     *
     * @param player    玩家
     * @param itemStack 物品堆栈
     * @param skinName  皮肤名称
     */
    public static void setEquippedSkin(Player player, ItemStack itemStack, String skinName) {
        PlayerSkinEconomyManager.setEquippedSkinForItemType(player, getItemTypeName(itemStack), skinName);
    }

    /**
     * 检查玩家是否解锁了某个皮肤
     *
     * @param player    玩家
     * @param itemStack 物品堆栈
     * @param skinName  皮肤名称
     * @return 是否解锁
     */
    public static boolean isSkinUnlocked(Player player, ItemStack itemStack, String skinName) {
        return PlayerSkinEconomyManager.isSkinUnlocked(player, itemStack, skinName);

    }

    /**
     * 解锁皮肤给玩家
     *
     * @param player    玩家
     * @param itemStack 物品堆栈
     * @param skinName  皮肤名称
     */
    public static void unlockSkin(Player player, ItemStack itemStack, String skinName) {
        PlayerSkinEconomyManager.unlockSkin(player, itemStack, skinName);
    }

    /**
     * 锁定皮肤（移除解锁状态）
     *
     * @param player    玩家
     * @param itemStack 物品堆栈
     * @param skinName  皮肤名称
     */
    public static void lockSkin(Player player, ItemStack itemStack, String skinName) {
        PlayerSkinEconomyManager.lockSkinForItemType(player, getItemTypeName(itemStack), skinName);
    }

    /**
     * 解锁指定物品类型的皮肤
     *
     * @param player       玩家
     * @param itemTypeName 物品类型名称
     * @param skinName     皮肤名称
     */
    public static void sync(Player player) {
        PlayerSkinEconomyManager.flushBlocking(player.getUUID());
    }

    public static void unlockSkinForItemTypeNoSync(Player player, String itemTypeName, String skinName) {
        PlayerSkinEconomyManager.unlockSkinForItemType(player, itemTypeName, skinName);
    }

    public static void unlockSkinForItemType(Player player, String itemTypeName, String skinName) {
        PlayerSkinEconomyManager.unlockSkinForItemType(player, itemTypeName, skinName);
    }

    /**
     * 设置指定物品类型的装备皮肤
     *
     * @param player       玩家
     * @param itemTypeName 物品类型名称
     * @param skinName     皮肤名称
     */
    public static void setEquippedSkinForItemType(Player player, String itemTypeName, String skinName) {
        PlayerSkinEconomyManager.setEquippedSkinForItemType(player, itemTypeName, skinName);
    }

    /**
     * 从物品堆栈获取物品类型名称
     *
     * @param itemStack 物品堆栈
     * @return 物品类型名称
     */
    public static String getItemTypeName(ItemStack itemStack) {
        Item item = itemStack.getItem();
        String skinType = getItemSkinType(item);
        if (skinType != null) {
            return skinType;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(item).getPath();
        return itemId.toLowerCase();
    }

    // === 原版物品的皮肤支持 ===
    // 原版物品（弓、弩等）无法改成 {@link SkinableItem} 的子类，这里显式登记「物品 → 皮肤类型」。
    private static final Map<Item, String> vanillaSkinItems = new java.util.LinkedHashMap<>();

    /**
     * 把原版物品登记为某个皮肤类型的持有者，例如
     * {@code registerVanillaSkinItem(Items.BOW, SkinTypes.BOW)}。
     */
    public static void registerVanillaSkinItem(Item item, String itemTypeName) {
        if (item == null || itemTypeName == null) {
            return;
        }
        vanillaSkinItems.putIfAbsent(item, itemTypeName);
        if (!skinTypeIdMap.containsKey(itemTypeName)) {
            registerType(itemTypeName);
        }
    }

    /** 已登记的原版可换肤物品（物品 → 皮肤类型）。 */
    public static Map<Item, String> getVanillaSkinItems() {
        return vanillaSkinItems;
    }

    /**
     * 物品对应的皮肤类型；不可换肤的物品返回 {@code null}。
     */
    public static String getItemSkinType(Item item) {
        if (item instanceof SkinableItem skinable) {
            return skinable.getItemSkinType();
        }
        return vanillaSkinItems.get(item);
    }
}
