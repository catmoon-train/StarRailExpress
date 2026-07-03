package org.agmas.noellesroles.content.block_entity;

import com.mojang.logging.LogUtils;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Component.Serializer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.init.SREFumoBlocks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class SREPlushBlockEntity extends BlockEntity {
    private static final String TAG_PROFILE = "profile";
    private static final String TAG_CLICK_SOUND = "click_sound";
    private static final String TAG_CUSTOM_NAME = "custom_name";
    private static final String TAG_CUSTOM_TEXTURE = "custom_texture";
    private static final Logger LOGGER = LogUtils.getLogger();

    public double squash;
    private Component customName;
    private ResourceLocation customTexture;
    @Nullable
    private ResourceLocation clickSound;

    @Nullable
    private ResolvableProfile owner;

    public SREPlushBlockEntity(BlockPos pos, BlockState state) {
        super(SREFumoBlocks.PLUSH_BLOCK_ENTITY, pos, state);
    }

    @Nullable
    public ResourceLocation getCustomTexture() {
        return this.customTexture;
    }

    @Nullable
    public Component getCustomName() {
        return this.customName;
    }

    public void setCustomName(@Nullable Component name) {
        this.customName = (name == null) ? null : name;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
        }
    }

    public static void tick(Level world, BlockPos pos, BlockState state, @NotNull SREPlushBlockEntity spark) {
        if (spark.squash > (double) 0.0F) {
            spark.squash /= (double) 3.0F;
            if (spark.squash < (double) 0.01F) {
                spark.squash = (double) 0.0F;
                if (world != null) {
                    world.sendBlockUpdated(pos, state, state, 2);
                }
            }
        }
    }

    public void squish(int squash) {
        this.squash += (double) squash;
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
        }

        this.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider provider) {
        nbt.putDouble("squash", this.squash);
        if (this.owner != null) {
            nbt.put(TAG_PROFILE, ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE, this.owner).getOrThrow());
        }

        if (this.clickSound != null) {
            nbt.putString(TAG_CLICK_SOUND, this.clickSound.toString());
        }

        if (this.customName != null) {
            nbt.putString("custom_name", Serializer.toJson(this.customName, provider));
        }

        if (this.customTexture != null) {
            nbt.putString(TAG_CUSTOM_TEXTURE, this.customTexture.toString());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider provider) {
        this.squash = nbt.getDouble("squash");
        if (nbt.contains(TAG_PROFILE)) {
            ResolvableProfile.CODEC.parse(NbtOps.INSTANCE, nbt.get(TAG_PROFILE))
                    .resultOrPartial((string) -> LOGGER.error("Failed to load profile from player head: {}", string))
                    .ifPresent(this::setOwner);
        }

        if (nbt.contains(TAG_CLICK_SOUND, 8)) {
            this.clickSound = ResourceLocation.tryParse(nbt.getString(TAG_CLICK_SOUND));
        }

        if (nbt.contains(TAG_CUSTOM_NAME, 8)) {
            this.customName = parseCustomNameSafe(nbt.getString(TAG_CUSTOM_NAME), provider);
        } else {
            this.customName = null;
        }
        if (nbt.contains(TAG_CUSTOM_TEXTURE, 8)) {
            this.customTexture = ResourceLocation.tryParse(nbt.getString(TAG_CUSTOM_TEXTURE));
        }
    }

    public void setOwner(@Nullable ResolvableProfile resolvableProfile) {
        synchronized (this) {
            this.owner = resolvableProfile;
        }

        this.updateOwnerProfile();
    }

    @Nullable
    public ResolvableProfile getOwnerProfile() {
        return this.owner;
    }

    @Nullable
    public ResourceLocation getClickSound() {
        return this.clickSound;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    private void updateOwnerProfile() {
        if (this.owner != null && !this.owner.isResolved()) {
            this.owner.resolve().thenAcceptAsync((resolvableProfile) -> {
                this.owner = resolvableProfile;
                this.setChanged();
            }, SkullBlockEntity.CHECKED_MAIN_THREAD_EXECUTOR);
        } else {
            this.setChanged();
        }
    }

    protected void applyImplicitComponents(BlockEntity.DataComponentInput dataComponentInput) {
        super.applyImplicitComponents(dataComponentInput);
        this.setOwner((ResolvableProfile) dataComponentInput.get(DataComponents.PROFILE));
        this.clickSound = (ResourceLocation) dataComponentInput.get(DataComponents.NOTE_BLOCK_SOUND);
        this.customName = (Component) dataComponentInput.get(DataComponents.CUSTOM_NAME);
        this.customTexture = dataComponentInput.get(SREDataComponentTypes.TEXTURE);
    }

    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        builder.set(DataComponents.PROFILE, this.owner);
        builder.set(DataComponents.NOTE_BLOCK_SOUND, this.clickSound);
        builder.set(DataComponents.CUSTOM_NAME, this.customName);
        builder.set(SREDataComponentTypes.TEXTURE, this.customTexture);
    }

}
