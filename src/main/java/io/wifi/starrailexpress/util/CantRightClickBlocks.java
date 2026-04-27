package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.SRE;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.FlowerPotBlock;

import java.util.*;

public class CantRightClickBlocks {
    public static final Set<String> CANNOT_INTERACT_IDS = new HashSet<>(Set.of(
            "supplementaries:fire_pit",
            "supplementaries:item_shelf"));
    // 原版工作方块集合
    public static final Set<Block> ALLOWED_BLOCKS = new HashSet<>();

    static {
        // 允许的方块集合
        Collections.addAll(ALLOWED_BLOCKS,
                Blocks.LECTERN

        // 这里可以添加其他允许的方块
        );
    }
    public static final Set<Block> VANILLA_WORKSTATIONS = new HashSet<>(Set.of(
            Blocks.CHISELED_BOOKSHELF,
            Blocks.CRAFTING_TABLE,
            Blocks.FURNACE,
            Blocks.BLAST_FURNACE,
            Blocks.SMOKER,
            Blocks.CAMPFIRE,
            Blocks.SOUL_CAMPFIRE,
            Blocks.CARTOGRAPHY_TABLE,
            Blocks.FLETCHING_TABLE,
            Blocks.SMITHING_TABLE,
            Blocks.GRINDSTONE,
            Blocks.STONECUTTER,
            Blocks.LOOM,
            Blocks.ANVIL,
            Blocks.CHIPPED_ANVIL,
            Blocks.DAMAGED_ANVIL,
            Blocks.BREWING_STAND,
            Blocks.CAULDRON,
            Blocks.BELL,
            Blocks.ENCHANTING_TABLE,
            Blocks.BEACON,
            Blocks.RESPAWN_ANCHOR,
            Blocks.ENDER_CHEST,
            Blocks.SHULKER_BOX,
            Blocks.FLOWER_POT,
            // 更多需要限制的方块...
            Blocks.CHEST,
            Blocks.TRAPPED_CHEST,
            Blocks.BARREL,
            Blocks.DISPENSER,
            Blocks.DROPPER,
            Blocks.HOPPER,
            Blocks.COMPOSTER,
            Blocks.CRAFTER));
    public static List<String> cantClickItems = new ArrayList<>(List.of(
            "supplementaries:item_shelf",
            "supplementaries:notice_board",
            "supplementaries:pedestal"));

    /**
     * 判断是否应该阻止与方块的交互
     */
    public static boolean shouldPreventInteraction(Block block) {
        if (SRE.isLobby)
            return false;
        return !isAllowedBlock(block) || cantClickItems.contains(BuiltInRegistries.BLOCK.getKey(block).toString())
                || CANNOT_INTERACT_IDS.contains(BuiltInRegistries.BLOCK.getKey(block).toString());
    }

    /**
     * 检查方块是否在允许的列表中
     */
    public static boolean isAllowedBlock(Block block) {
        // 如果在允许列表中，直接返回true
        if (ALLOWED_BLOCKS.contains(block)) {
            return true;
        }
        if (block instanceof FlowerPotBlock)
            return false;
        if (block instanceof DecoratedPotBlock)
            return false;
        // 如果是原版工作方块，禁止交互
        if (VANILLA_WORKSTATIONS.contains(block)) {
            return false;
        }
        if (CANNOT_INTERACT_IDS.contains(BuiltInRegistries.BLOCK.getKey(block).toString())) {
            return false;
        }
        if (BuiltInRegistries.BLOCK.getKey(block).getPath().contains("shulker_box")) {
            return false;
        }
        // 检查是否为TMM模组的方块
        // ResourceLocation blockId = level().registryAccess()
        // .registryOrThrow(Registries.BLOCK)
        // .getKey(block);
        //
        // String namespace = blockId.getNamespace();

        return true;
        // 允许TMM模组的方块（除了"minopp"命名空间）
    }
}
