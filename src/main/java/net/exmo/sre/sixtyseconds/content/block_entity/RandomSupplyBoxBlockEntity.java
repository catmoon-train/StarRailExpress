package net.exmo.sre.sixtyseconds.content.block_entity;

import net.exmo.sre.sixtyseconds.content.block.RandomSupplyBoxBlock;
import net.exmo.sre.sixtyseconds.loot.SixtySecondsLootStore;
import net.exmo.sre.sixtyseconds.loot.SixtySecondsLootTable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.init.ModBlocks;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 随机物资箱方块实体：完全复用 {@link SupplyBoxBlockEntity} 的行为（每日刷新、每人领取一次、搜刮定时），
 * 唯一区别是每次刷新从<b>已启用的类别集合</b>中随机挑一个 loot 类别。
 * <p>
 * 等级 {@link #tier} 决定默认类别集合（低/高级），创造模式下可通过 GUI 勾选/取消具体类别。
 */
public class RandomSupplyBoxBlockEntity extends SupplyBoxBlockEntity {
    /** 等级（low / high），由方块传入。 */
    private String tier = "";
    /** 当前已启用的 loot 类别集合。 */
    private final Set<String> enabledCategories = new LinkedHashSet<>();

    public RandomSupplyBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SIXTY_SECONDS_RANDOM_SUPPLY_BOX_ENTITY, pos, state);
    }

    // ── 等级初始化 ──────────────────────────────────────────────────

    /** 首次使用时从方块读取等级并初始化默认类别（仅当 tier 为空时执行）。 */
    public void initTierIfNeeded(String blockTier) {
        if (!tier.isEmpty()) return;
        this.tier = blockTier;
        if (enabledCategories.isEmpty()) {
            enabledCategories.addAll(RandomSupplyBoxBlock.defaultCategories(tier));
        }
        setChanged();
    }

    /** 某类别是否为当前等级的默认可用类别。 */
    public boolean isCategoryDefault(String category) {
        return RandomSupplyBoxBlock.defaultCategories(tier).contains(category);
    }

    // ── 已启用类别管理 ──────────────────────────────────────────────

    public Set<String> getEnabledCategories() {
        return enabledCategories;
    }

    public void setEnabledCategories(Set<String> categories) {
        enabledCategories.clear();
        enabledCategories.addAll(categories);
        setChanged();
    }

    // ── 覆写：从已启用类别中随机 ────────────────────────────────────

    @Override
    protected String chooseCategory(ServerLevel level, SixtySecondsLootTable table) {
        List<String> names = rollableCategories(table);
        if (names.isEmpty()) {
            return super.chooseCategory(level, table);
        }
        return names.get(level.random.nextInt(names.size()));
    }

    @Override
    protected boolean hasAnyLoot(ServerLevel level) {
        return !rollableCategories(
                SixtySecondsLootStore.get(level)).isEmpty();
    }

    /** 只在<b>已启用且</b>有可抽条目的类别里随机，避免抽中空类别浪费当天唯一一次搜刮机会。 */
    private List<String> rollableCategories(SixtySecondsLootTable table) {
        List<String> names = new java.util.ArrayList<>();
        for (String name : enabledCategories) {
            if (table.canRoll(name)) {
                names.add(name);
            }
        }
        return names;
    }

    // ── NBT 序列化 ──────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!tier.isEmpty()) {
            tag.putString("RandomTier", tier);
        }
        if (!enabledCategories.isEmpty()) {
            CompoundTag cats = new CompoundTag();
            int i = 0;
            for (String cat : enabledCategories) {
                cats.putString("cat" + i, cat);
                i++;
            }
            cats.putInt("size", i);
            tag.put("RandomEnabledCategories", cats);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tier = tag.getString("RandomTier");
        if (tag.contains("RandomEnabledCategories")) {
            CompoundTag cats = tag.getCompound("RandomEnabledCategories");
            int size = cats.getInt("size");
            enabledCategories.clear();
            for (int i = 0; i < size; i++) {
                String cat = cats.getString("cat" + i);
                if (!cat.isEmpty()) {
                    enabledCategories.add(cat);
                }
            }
        }
        // 兼容旧版 NBT：如果 tier 为空但 enabledCategories 不为空，尝试从类别名反推等级
        if (tier.isEmpty() && !enabledCategories.isEmpty()) {
            // 检查是否为高级类别
            for (String cat : enabledCategories) {
                if (cat.startsWith("advanced_")) {
                    tier = "high";
                    break;
                }
            }
            if (tier.isEmpty()) {
                tier = "low";
            }
        }
    }
}
