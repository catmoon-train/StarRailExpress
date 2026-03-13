package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.ShopEntry;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.List;

public class StarPlayerShopComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<StarPlayerShopComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("shop"),
            StarPlayerShopComponent.class);
    private final Player player;
    public int balance = 0;

    public StarPlayerShopComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void reset() {
        this.balance = 0;
        this.sync();
    }

    @Override
    public void clear() {
        reset();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    public void addToBalance(int amount) {
        this.setBalance(this.balance + amount);
    }

    public void setBalance(int amount) {
        if (this.balance != amount) {
            this.balance = amount;
            this.sync();
        }
    }

    public void tryBuy(int index) {
        if (index < 0 || index >= getShopEntries().size())
            return;
        ShopEntry entry = getShopEntries().get(index);
        if (FabricLoader.getInstance().isDevelopmentEnvironment() && this.balance < entry.price())
            this.balance = entry.price() * 10;
        if (this.balance >= entry.price() && !this.player.getCooldowns().isOnCooldown(entry.stack().getItem())
                && entry.canBuy(this.player) && entry.onBuy(this.player)) {
            this.balance -= entry.price();
            if (this.player instanceof ServerPlayer player) {
                player.connection.send(
                        new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(TMMSounds.UI_SHOP_BUY),
                                SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 1.0f,
                                0.9f + this.player.getRandom().nextFloat() * 0.2f, player.getRandom().nextLong()));
                SRE.REPLAY_MANAGER.recordStoreBuy(player.getUUID(),
                        BuiltInRegistries.ITEM.getKey(entry.stack().getItem()), entry.stack().getCount(),
                        entry.price());
            }
        } else {
            this.player.displayClientMessage(Component.literal("购买失败").withStyle(ChatFormatting.DARK_RED), true);
            if (this.player instanceof ServerPlayer player) {
                player.connection.send(new ClientboundSoundPacket(
                        BuiltInRegistries.SOUND_EVENT.wrapAsHolder(TMMSounds.UI_SHOP_BUY_FAIL), SoundSource.PLAYERS,
                        player.getX(), player.getY(), player.getZ(), 1.0f,
                        0.9f + this.player.getRandom().nextFloat() * 0.2f, player.getRandom().nextLong()));
            }
        }
        this.sync();
    }

    public static boolean isPlayerAliveAndSurvival(Player player) {
        return player != null && !player.isSpectator() && !player.isCreative();
    }

    private @NotNull List<ShopEntry> getShopEntries() {
        final var gameWorldComponent = StarGameWorldComponent.KEY.get(player.level());
        final var role = gameWorldComponent.getRole(player);
        if (gameWorldComponent != null && role != null && isPlayerAliveAndSurvival(player)) {
            final var shopEntries = ShopContent.getShopEntries(
                    role.getIdentifier());
            if (!shopEntries.isEmpty()) {
                return shopEntries;
            }
            if (gameWorldComponent.canUseKillerFeatures(player)) {
                return ShopContent.defaultEntries;
            }
        }
        return List.of();
    }

    @Override
    public void clientTick() {

    }

    @Override
    public void serverTick() {

    }

    public static boolean useBlackout(@NotNull Player player) {
        player.getCooldowns().addCooldown(TMMItems.BLACKOUT,
                GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.BLACKOUT, 0));
        boolean triggered = StarWorldBlackoutComponent.KEY.get(player.level()).triggerBlackout();
        if (triggered) {
            SRE.REPLAY_MANAGER.recordSkillUsed(player.getUUID(), BuiltInRegistries.ITEM.getKey(TMMItems.BLACKOUT));
        }
        return triggered;
    }

    public static boolean usePsychoMode(@NotNull Player player) {
        player.getCooldowns().addCooldown(TMMItems.PSYCHO_MODE,
                GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.PSYCHO_MODE, 0));
        boolean started = StarPlayerPsychoComponent.KEY.get(player).startPsycho();
        if (started) {
            SRE.REPLAY_MANAGER.recordSkillUsed(player.getUUID(), BuiltInRegistries.ITEM.getKey(TMMItems.PSYCHO_MODE));
        }
        return started;
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("Balance", this.balance);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.balance = tag.getInt("Balance");
    }
}