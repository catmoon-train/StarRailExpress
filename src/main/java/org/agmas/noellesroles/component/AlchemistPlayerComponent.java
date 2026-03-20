package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import io.wifi.starrailexpress.api.RoleComponent;

import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 药剂师玩家组件
 *
 * 平民阵营，真实心情，默认冲刺时间
 *
 * 被动：持续蹲下每30秒获取一次药剂素材
 *
 * 技能：
 * - 蹲下按技能键：切换当前调制的药剂（肾上腺素/抗生素/鹤顶红/狗皮膏药）
 * - 直接按技能键：消耗2个素材+相应金币调制一份对应的药剂
 *
 * 药剂：
 * - 肾上腺素：100金币，增加目标体力上限
 * - 抗生素：100金币，解除目标中毒
 * - 鹤顶红：200金币，使目标中毒
 * - 狗皮膏药：150金币，目标30秒内san值不会下降
 *
 * 限制：每种药剂只能调两次，游戏结束时重置
 */
public class AlchemistPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    /** 组件键 */
    public static final ComponentKey<AlchemistPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "alchemist"),
            AlchemistPlayerComponent.class);

    /** 蹲下获取素材间隔（20秒 = 400 tick） */
    public static final int MATERIAL_GATHER_INTERVAL = 20 * 20;

    /** 每次蹲下获取的素材数量 */
    public static final int MATERIALS_PER_GATHER = 1;

    /** 调制药剂需要的素材数量 */
    public static final int MATERIALS_TO_CRAFT = 1;

    /** 药剂最大调制次数 */
    public static final int MAX_CRAFT_COUNT = 2;

    /** 药剂类型枚举 */
    public static final int POTION_ADRENALINE = 0; // 肾上腺素 - 100金币
    public static final int POTION_ANTIBIOTIC = 1; // 抗生素 - 100金币
    public static final int POTION_HEDINGHONG = 2; // 鹤顶红 - 175金币
    public static final int POTION_DOGSKIN_PLASTER = 3; // 狗皮膏药 - 150金币

    /** 药剂总数 */
    public static final int POTION_COUNT = 4;

    private final Player player;

    /** 蹲下获取素材计时器 */
    private int materialGatherTimer = MATERIAL_GATHER_INTERVAL;

    /** 当前选择的药剂索引 */
    private int currentPotionIndex = POTION_ADRENALINE;

    /** 每种药剂的已调制次数 */
    private final int[] potionCraftCounts = new int[POTION_COUNT];

    /**
     * 构造函数
     */
    public AlchemistPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.materialGatherTimer = tag.getInt("MaterialGatherTimer");
        this.currentPotionIndex = tag.getInt("CurrentPotionIndex");

        // 读取药剂调制次数
        for (int i = 0; i < POTION_COUNT; i++) {
            this.potionCraftCounts[i] = tag.getInt("PotionCraftCount_" + i);
        }
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("MaterialGatherTimer", this.materialGatherTimer);
        tag.putInt("CurrentPotionIndex", this.currentPotionIndex);

        // 保存药剂调制次数
        for (int i = 0; i < POTION_COUNT; i++) {
            tag.putInt("PotionCraftCount_" + i, this.potionCraftCounts[i]);
        }
    }

    @Override
    public void init() {
        this.materialGatherTimer = MATERIAL_GATHER_INTERVAL;
        this.currentPotionIndex = POTION_ADRENALINE;
        for (int i = 0; i < POTION_COUNT; i++) {
            this.potionCraftCounts[i] = 0;
        }
        sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public void serverTick() {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isRunning())
            return;
        if (!gameWorldComponent.isRole(player, ModRoles.ALCHEMIST))
            return;
        // 检查游戏是否开始、玩家是否是药剂师角色
        {
            // 检查玩家是否蹲下
            if (player.isShiftKeyDown()) {
                if (materialGatherTimer > 0) {
                    // 每10秒同步一次到客户端
                    if (materialGatherTimer % 200 == 0) {
                        sync();
                    }
                    // 计时中，减少计时器
                    materialGatherTimer--;
                    if (materialGatherTimer == 0) {
                        // 计时结束，获取素材
                        gatherMaterials();
                        // 重置计时器为初始状态（需要重新蹲下30秒）
                        materialGatherTimer = MATERIAL_GATHER_INTERVAL;
                        sync();
                    }
                }
                // 如果计时器为0，说明刚获得素材，已经被重置为MATERIAL_GATHER_INTERVAL，需要继续蹲下30秒
            } else {
                // 不蹲下时重置计时器到初始状态
                if (materialGatherTimer != MATERIAL_GATHER_INTERVAL) {
                    materialGatherTimer = MATERIAL_GATHER_INTERVAL;
                }
            }
        }
    }

    /**
     * 获取药剂素材
     */
    private void gatherMaterials() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        // 给予玩家药剂素材
        ItemStack materials = new ItemStack(ModItems.ALCHEMY_MATERIAL, MATERIALS_PER_GATHER);
        if (!player.getInventory().add(materials)) {
            // 背包满了，丢弃在地上
            player.drop(materials, false);
        }

        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.alchemist.material_gathered", MATERIALS_PER_GATHER)
                        .withStyle(ChatFormatting.GREEN),
                true);

        // 播放获取素材的音效
        serverPlayer.level().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    /**
     * 切换药剂（蹲下按技能键）
     */
    public void switchPotion() {
        currentPotionIndex = (currentPotionIndex + 1) % POTION_COUNT;

        if (player instanceof ServerPlayer serverPlayer) {
            Component potionName = getPotionName(currentPotionIndex);
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.alchemist.potion_selected", potionName)
                            .withStyle(ChatFormatting.LIGHT_PURPLE),
                    true);
        }

        sync();
    }

    /**
     * 调制当前药剂（直接按技能键）
     */
    public void craftPotion() {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        // 检查当前药剂的调制次数
        if (potionCraftCounts[currentPotionIndex] >= MAX_CRAFT_COUNT) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.alchemist.max_craft_reached")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 检查素材是否足够
        int materialCount = countMaterials();
        if (materialCount < MATERIALS_TO_CRAFT) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.alchemist.insufficient_materials", MATERIALS_TO_CRAFT)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 检查金币是否足够
        int goldCost = getPotionCost(currentPotionIndex);
        SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(player);
        if (shopComponent.balance < goldCost) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.alchemist.insufficient_gold", goldCost)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 消耗素材
        removeMaterials(MATERIALS_TO_CRAFT);

        // 扣除金币
        shopComponent.balance -= goldCost;
        shopComponent.sync();

        // 给予药剂
        ItemStack potion = getPotionItemStack(currentPotionIndex);
        if (!player.getInventory().add(potion)) {
            // 背包满了，丢弃在地上
            player.drop(potion, false);
        }

        // 增加调制次数
        potionCraftCounts[currentPotionIndex]++;

        // 通知玩家
        Component potionName = getPotionName(currentPotionIndex);
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.alchemist.potion_crafted", potionName)
                        .withStyle(ChatFormatting.GOLD),
                true);

        // 播放调制成功的音效
        serverPlayer.level().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.2F, 1.0F);

        sync();
    }

    /**
     * 获取当前药剂索引
     */
    public int getCurrentPotionIndex() {
        return currentPotionIndex;
    }

    /**
     * 获取蹲下获取素材剩余时间（秒）
     */
    public int getMaterialGatherRemainingSeconds() {
        return (materialGatherTimer + 19) / 20;
    }

    /**
     * 获取当前药剂的调制次数
     */
    public int getPotionCraftCount(int potionIndex) {
        if (potionIndex < 0 || potionIndex >= POTION_COUNT) {
            return 0;
        }
        return potionCraftCounts[potionIndex];
    }

    /**
     * 获取当前药剂的调制次数
     */
    public int getCurrentPotionCraftCount() {
        return potionCraftCounts[currentPotionIndex];
    }

    /**
     * 计算玩家背包中的药剂素材数量
     */
    private int countMaterials() {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.ALCHEMY_MATERIAL)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * 从玩家背包中移除指定数量的药剂素材
     */
    private void removeMaterials(int amount) {
        int remaining = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.ALCHEMY_MATERIAL) && remaining > 0) {
                int stackCount = stack.getCount();
                if (stackCount <= remaining) {
                    remaining -= stackCount;
                    stack.setCount(0);
                } else {
                    stack.shrink(remaining);
                    remaining = 0;
                }
            }
        }
    }

    /**
     * 获取药剂的名称
     */
    private Component getPotionName(int potionIndex) {
        return switch (potionIndex) {
            case POTION_ADRENALINE -> Component.translatable("potion.noellesroles.adrenaline");
            case POTION_ANTIBIOTIC -> Component.translatable("potion.noellesroles.antibiotic");
            case POTION_HEDINGHONG -> Component.translatable("potion.noellesroles.hedinghong");
            case POTION_DOGSKIN_PLASTER -> Component.translatable("potion.noellesroles.dogskin_plaster");
            default -> Component.literal("未知药剂");
        };
    }

    /**
     * 获取药剂的调制金币花费
     */
    public static int getPotionCost(int potionIndex) {
        return switch (potionIndex) {
            case POTION_ADRENALINE, POTION_ANTIBIOTIC -> 100;
            case POTION_HEDINGHONG -> 200;
            case POTION_DOGSKIN_PLASTER -> 150;
            default -> 0;
        };
    }

    /**
     * 获取药剂的key（用于翻译）
     */
    public static String getPotionKey(int potionIndex) {
        return switch (potionIndex) {
            case org.agmas.noellesroles.component.AlchemistPlayerComponent.POTION_ADRENALINE -> "adrenaline";
            case org.agmas.noellesroles.component.AlchemistPlayerComponent.POTION_ANTIBIOTIC -> "antibiotic";
            case org.agmas.noellesroles.component.AlchemistPlayerComponent.POTION_HEDINGHONG -> "hedinghong";
            case org.agmas.noellesroles.component.AlchemistPlayerComponent.POTION_DOGSKIN_PLASTER -> "dogskin_plaster";
            default -> "unknown";
        };
    }

    /**
     * 获取药剂的物品栈
     */
    public static ItemStack getPotionItemStack(int potionIndex) {
        return switch (potionIndex) {
            case POTION_ADRENALINE -> new ItemStack(ModItems.ADRENALINE);
            case POTION_ANTIBIOTIC -> new ItemStack(ModItems.ANTIBIOTIC);
            case POTION_HEDINGHONG -> new ItemStack(ModItems.HEDINGHONG);
            case POTION_DOGSKIN_PLASTER -> new ItemStack(ModItems.DOGSKIN_PLASTER);
            default -> ItemStack.EMPTY;
        };
    }

    /**
     * 同步组件数据到客户端
     */
    private void sync() {
        if (!player.level().isClientSide) {
            KEY.sync(player);
        }
    }

    @Override
    public void clientTick() {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorldComponent.isRunning())
            return;
        if (!gameWorldComponent.isRole(player, ModRoles.ALCHEMIST))
            return;
        // 检查游戏是否开始、玩家是否是药剂师角色
        {
            // 检查玩家是否蹲下
            if (player.isShiftKeyDown()) {
                if (materialGatherTimer > 1) {
                    // 计时中，减少计时器
                    materialGatherTimer--;
                }
                // 如果计时器为0，说明刚获得素材，已经被重置为MATERIAL_GATHER_INTERVAL，需要继续蹲下30秒
            } else {
                // 不蹲下时重置计时器到初始状态
                materialGatherTimer = MATERIAL_GATHER_INTERVAL;
            }
        }
    }
    
    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
