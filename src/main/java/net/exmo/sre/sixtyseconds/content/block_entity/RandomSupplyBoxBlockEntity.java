package net.exmo.sre.sixtyseconds.content.block_entity;

import net.exmo.sre.sixtyseconds.loot.SixtySecondsLootTable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.init.ModBlocks;

import java.util.List;

/**
 * 随机物资箱方块实体：完全复用 {@link SupplyBoxBlockEntity} 的行为（每日刷新、每人领取一次、搜刮定时），
 * 唯一区别是每次刷新<b>随机</b>挑一个 loot 类别，而非固定 {@link #category}。
 */
public class RandomSupplyBoxBlockEntity extends SupplyBoxBlockEntity {
    public RandomSupplyBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SIXTY_SECONDS_RANDOM_SUPPLY_BOX_ENTITY, pos, state);
    }

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
                net.exmo.sre.sixtyseconds.loot.SixtySecondsLootStore.get(level)).isEmpty();
    }

    /** 只在有可抽条目的类别里随机，避免抽中空类别浪费当天唯一一次搜刮机会。 */
    private static List<String> rollableCategories(SixtySecondsLootTable table) {
        List<String> names = new java.util.ArrayList<>();
        for (String name : table.categoryNames()) {
            if (table.canRoll(name)) {
                names.add(name);
            }
        }
        return names;
    }
}
