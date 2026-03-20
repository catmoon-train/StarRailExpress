package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.api.RoleComponent;
import org.ladysnake.cca.api.v3.component.ComponentKey;

public class BomberPlayerComponent implements RoleComponent {
    public static final int BOMB_COST = 100;
    private final Player player;
    public static final ComponentKey<BomberPlayerComponent> KEY = ModComponents.BOMBER;

    public BomberPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public void init() {
    }

    @Override
    public void clear() {
        this.init();
    }

    public void buyBomb() {
        if (player.level().isClientSide)
            return;

        SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(player);
        if (shopComponent.balance >= BOMB_COST) {
            shopComponent.addToBalance(-BOMB_COST);

            ItemStack bombStack = ModItems.BOMB.getDefaultInstance();
            CompoundTag tag = new CompoundTag();
            tag.putUUID("owner", player.getUUID());
            var customData = CustomData.of(tag);
            bombStack.set(DataComponents.CUSTOM_DATA, customData);
            if (!RoleUtils.insertStackInFreeSlot(player, bombStack)) {
                player.drop(bombStack, false);
            }
        } else {
            player.displayClientMessage(Component.translatable("message.noellesroles.insufficient_funds"), true);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.send(new ClientboundSoundPacket(
                        BuiltInRegistries.SOUND_EVENT.wrapAsHolder(TMMSounds.UI_SHOP_BUY_FAIL),
                        SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 1.0F,
                        0.9F + player.getRandom().nextFloat() * 0.2F, player.getRandom().nextLong()));
            }
        }
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // No persistent data needed for now
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // No persistent data needed for now
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, Provider registryLookup) {
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, Provider registryLookup) {
    }
}