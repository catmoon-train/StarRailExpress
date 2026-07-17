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

public class SixtySecondsMailboxBlockEntity extends BlockEntity implements Container {

    protected NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    public int ownerTeamId = -1;

    public SixtySecondsMailboxBlockEntity(BlockPos pos, BlockState state) {
        super(org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_MAILBOX_ENTITY, pos, state);
    }

    @Override
    public int getContainerSize() {
        return 27;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack item : items) {
            if (!item.isEmpty()) return false;
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
        if (!result.isEmpty()) setChanged();
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

    /** 只允许放入稿纸、实体游戏币、快递包裹、废料 */
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_DRAFT_PAPER)
                || stack.is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_COIN)
                || stack.is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_EXPRESS_PACKAGE)
                || stack.is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_SCRAP);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(27, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        ownerTeamId = tag.getInt("OwnerTeam");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("OwnerTeam", ownerTeamId);
    }

    /** 破坏时倒出全部内容 */
    public NonNullList<ItemStack> contents() {
        return items;
    }
}
