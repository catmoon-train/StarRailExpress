package net.exmo.sre.sixtyseconds.content.block_entity;

import net.exmo.sre.sixtyseconds.content.block.RandomSupplyBoxBlock;
import net.exmo.sre.sixtyseconds.loot.SixtySecondsLootStore;
import net.exmo.sre.sixtyseconds.loot.SixtySecondsLootTable;
import net.exmo.sre.sixtyseconds.loot.SixtySecondsRandomBoxConfigStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.init.ModBlocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 随机物资箱方块实体：完全复用 {@link SupplyBoxBlockEntity} 的行为（每日刷新、每人领取一次、搜刮定时），
 * 唯一区别是每次刷新从<b>全局已启用的类别集合</b>（{@link SixtySecondsRandomBoxConfigStore}）中
 * 随机挑一个 loot 类别。
 * <p>
 * 类别配置不再每箱各存 NBT，而是通过服务端全局配置管理。管理员在任意随机箱上编辑保存后，
 * 同等级的全部随机箱立刻生效。等级（low/high）从方块状态派生，不存 NBT。
 */
public class RandomSupplyBoxBlockEntity extends SupplyBoxBlockEntity {

    public RandomSupplyBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SIXTY_SECONDS_RANDOM_SUPPLY_BOX_ENTITY, pos, state);
    }

    // ── 等级派生 ──────────────────────────────────────────────────

    /** 从方块状态获取等级（不存 NBT，始终跟随方块类型）。 */
    private String getTier() {
        if (getBlockState().getBlock() instanceof RandomSupplyBoxBlock rb) {
            return rb.tier();
        }
        return "low"; // fallback
    }

    // ── 覆写：从全局已启用类别中随机 ────────────────────────────────

    @Override
    protected String chooseCategory(ServerLevel level, SixtySecondsLootTable table) {
        String tier = getTier();
        Set<String> enabled = SixtySecondsRandomBoxConfigStore.get(level).getEnabled(tier);
        List<String> names = rollableCategories(table, enabled);
        if (names.isEmpty()) {
            return super.chooseCategory(level, table);
        }
        return names.get(level.random.nextInt(names.size()));
    }

    @Override
    protected boolean hasAnyLoot(ServerLevel level) {
        String tier = getTier();
        Set<String> enabled = SixtySecondsRandomBoxConfigStore.get(level).getEnabled(tier);
        SixtySecondsLootTable table = SixtySecondsLootStore.get(level);
        for (String name : enabled) {
            if (table.canRoll(name)) return true;
        }
        return false;
    }

    /** 只在<b>已启用且</b>有可抽条目的类别里随机，避免抽中空类别浪费当天唯一一次搜刮机会。 */
    private static List<String> rollableCategories(SixtySecondsLootTable table, Set<String> enabled) {
        List<String> names = new ArrayList<>();
        for (String name : enabled) {
            if (table.canRoll(name)) {
                names.add(name);
            }
        }
        return names;
    }

    // ── NBT 序列化：只继承父类字段，不存 tier 和类别配置 ────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // 不再保存 RandomTier 和 RandomEnabledCategories —— 全部从全局配置读取
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // 旧 NBT 中的 RandomTier / RandomEnabledCategories 被忽略，
        // 全由全局配置（SixtySecondsRandomBoxConfigStore）接管
    }
}
