package net.exmo.sre.sixtyseconds.content.block_entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 保险库/基地箱子方块实体：真实槽位容器（小 18 / 中 27 / 大 54 格）。
 * {@code ownerTeamId} 记录归属队伍（放置时写入）：保险库对外队上锁，
 * 需保险库撬锁器套组才能开（见 {@link net.exmo.sre.sixtyseconds.content.block.SixtySecondsVaultBlock}）。
 */
public class SixtySecondsVaultBlockEntity extends BlockEntity implements Container {

    private NonNullList<ItemStack> items = NonNullList.withSize(54, ItemStack.EMPTY);
    /** 归属队伍 id；-1 = 未认领。 */
    public int ownerTeamId = -1;

    public SixtySecondsVaultBlockEntity(BlockPos pos, BlockState state) {
        super(org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_VAULT_ENTITY, pos, state);
    }

    /** 实际格数由所在方块（行数×9）决定；items 统一开满 54 格，多余格不暴露。 */
    private int rows() {
        if (getBlockState().getBlock()
                instanceof net.exmo.sre.sixtyseconds.content.block.SixtySecondsVaultBlock vault) {
            return vault.rows();
        }
        return 3;
    }

    @Override
    public int getContainerSize() {
        return rows() * 9;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < getContainerSize(); i++) {
            if (!items.get(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    /** 破坏时倒出全部内容。 */
    public NonNullList<ItemStack> contents() {
        return items;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(54, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        ownerTeamId = tag.getInt("OwnerTeam");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("OwnerTeam", ownerTeamId);
    }
}
