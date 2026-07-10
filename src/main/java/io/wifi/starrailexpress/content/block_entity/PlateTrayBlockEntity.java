package io.wifi.starrailexpress.content.block_entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class PlateTrayBlockEntity extends BlockEntity {
    public final List<ItemStack> storedItems = new ArrayList<>();
    public String poisoner = null;
    public boolean isPoisonFake = false;
    public String armorer = null;
    public String weakArmorer = null;
    public PlateType plate = PlateType.DRINK;

    public PlateTrayBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    private void sync() {
        if (this.level != null && !this.level.isClientSide) {
            this.setChanged();

            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
    }

    public static <T extends BlockEntity> void clientTick(Level world, BlockPos pos, BlockState state, T blockEntity) {
    }

    public List<ItemStack> getStoredItems() {
        return this.storedItems;
    }

    public void addItem(@NotNull ItemStack stack) {
        if (stack.isEmpty())
            return;
        this.storedItems.add(stack.copy());
        this.sync();
    }

    public void clearItems() {
        this.storedItems.clear();
        this.sync();
    }

    public ItemStack removeItem(int index) {
        if (index < 0 || index >= this.storedItems.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = this.storedItems.remove(index);
        this.sync();
        return stack;
    }

    public String getPoisoner() {
        return this.poisoner;
    }

    public String getArmorer() {
        return this.armorer;
    }

    public void setArmorer(String armorer) {
        this.armorer = armorer;
        this.sync();
    }

    public String getWeakArmorer() {
        return this.weakArmorer;
    }

    public void setWeakArmorer(String weakArmorer) {
        this.weakArmorer = weakArmorer;
        this.sync();
    }

    public void setPoisoner(String poisoner) {
        this.poisoner = poisoner;
        this.sync();
    }

    public boolean isDrink() {
        return this.plate == PlateType.DRINK;
    }

    public void setDrink(boolean drink) {
        this.plate = drink ? PlateType.DRINK : PlateType.FOOD;
        this.sync();
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        CompoundTag itemsNbt = new CompoundTag();
        for (int i = 0; i < this.storedItems.size(); i++) {
            if (!this.storedItems.get(i).isEmpty())
                itemsNbt.put("Item" + i, this.storedItems.get(i).save(registryLookup));
        }
        nbt.put("Items", itemsNbt);
        if (this.poisoner != null)
            nbt.putString("poisoner", this.poisoner);
        nbt.putBoolean("isPoisonFake", this.isPoisonFake);
        if (this.armorer != null)
            nbt.putString("armorer", this.armorer);
        if (this.weakArmorer != null)
            nbt.putString("weakArmorer", this.weakArmorer);
        nbt.putBoolean("Drink", this.plate == PlateType.DRINK);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        this.storedItems.clear();
        if (nbt.contains("Items")) {
            CompoundTag itemsNbt = nbt.getCompound("Items");
            for (String key : itemsNbt.getAllKeys()) {
                Optional<ItemStack> itemStack = ItemStack.parse(registryLookup, itemsNbt.get(key));
                itemStack.ifPresent(this.storedItems::add);
            }
        }
        this.poisoner = nbt.contains("poisoner") ? nbt.getString("poisoner") : null;
        this.isPoisonFake = nbt.contains("isPoisonFake") ? nbt.getBoolean("isPoisonFake") : false;
        this.armorer = nbt.contains("armorer") ? nbt.getString("armorer") : null;
        this.weakArmorer = nbt.contains("weakArmorer") ? nbt.getString("weakArmorer") : null;
        this.plate = nbt.getBoolean("Drink") ? PlateType.DRINK : PlateType.FOOD;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        return this.saveWithoutMetadata(registryLookup);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public enum PlateType {
        DRINK,
        FOOD
    }
}
