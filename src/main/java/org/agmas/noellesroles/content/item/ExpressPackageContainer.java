package org.agmas.noellesroles.content.item;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** 快递包裹的1格虚拟容器 */
public class ExpressPackageContainer implements Container {
    private final ItemStack packageStack;
    private final NonNullList<ItemStack> items;

    private ExpressPackageContainer(ItemStack stack, ServerLevel level) {
        this.packageStack = stack;
        this.items = NonNullList.withSize(1, ItemStack.EMPTY);
        ItemStack content = ExpressPackageItem.extractContent(stack, level);
        if (!content.isEmpty()) {
            items.set(0, content);
        }
    }

    public static ExpressPackageContainer create(ItemStack stack, ServerLevel level) {
        return new ExpressPackageContainer(stack, level);
    }

    /** Read content without removing from NBT (for tooltip display) */
    public static ItemStack peekContent(ItemStack stack, HolderLookup.Provider registries) {
        var tag = ExpressPackageItem.getContents(stack);
        if (!tag.isEmpty()) {
            return ItemStack.parseOptional(registries, tag);
        }
        return ItemStack.EMPTY;
    }

    @Override public int getContainerSize() { return 1; }
    @Override public boolean isEmpty() { return items.get(0).isEmpty(); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        save();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = ContainerHelper.takeItem(items, slot);
        save();
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        save();
    }

    @Override public void setChanged() { save(); }

    private void save() {
        ExpressPackageItem.setContent(packageStack, items.get(0));
    }

    @Override public boolean stillValid(Player player) { return true; }

    @Override
    public void clearContent() {
        items.clear();
        save();
    }
}
