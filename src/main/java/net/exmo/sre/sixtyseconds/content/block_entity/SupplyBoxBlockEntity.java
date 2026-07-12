package net.exmo.sre.sixtyseconds.content.block_entity;

import net.exmo.sre.sixtyseconds.loot.SixtySecondsLootStore;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.init.ModBlocks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 物资箱方块实体：按自身 {@link #category} 从共享 loot 表加权抽取，每日刷新（惰性：交互时按当前游戏日刷新）、
 * 按玩家领取一次。参照 {@code org.agmas.noellesroles.content.block_entity.SupplyCrateBlockEntity}。
 */
public class SupplyBoxBlockEntity extends BlockEntity {
    public String category = "tool";
    private int lastRefreshDay = -1;
    private final List<ItemStack> currentItems = new ArrayList<>();
    private final Set<UUID> claimed = new HashSet<>();

    public SupplyBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SIXTY_SECONDS_SUPPLY_BOX_ENTITY, pos, state);
    }

    /** 领取：返回本箱当前物资的副本（每人每刷新周期一次）。 */
    public List<ItemStack> claim(ServerLevel level, ServerPlayer player) {
        ensureDaily(level);
        if (currentItems.isEmpty() || claimed.contains(player.getUUID())) {
            return List.of();
        }
        claimed.add(player.getUUID());
        setChanged();
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : currentItems) {
            out.add(stack.copy());
        }
        return out;
    }

    private void ensureDaily(ServerLevel level) {
        int day = SixtySecondsState.get(level).dayNumber;
        if (day != lastRefreshDay || currentItems.isEmpty()) {
            refresh(level, day);
        }
    }

    private void refresh(ServerLevel level, int day) {
        currentItems.clear();
        claimed.clear();
        ItemStack stack = SixtySecondsLootStore.get(level).roll(category, level.random);
        if (!stack.isEmpty()) {
            currentItems.add(stack);
        }
        lastRefreshDay = day;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Category", category);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        String stored = tag.getString("Category");
        category = stored.isEmpty() ? "tool" : stored;
    }
}
