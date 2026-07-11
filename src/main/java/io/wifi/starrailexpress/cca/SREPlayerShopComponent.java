package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.ShopEntry;
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

import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.NRSounds;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.List;

public class SREPlayerShopComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SREPlayerShopComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("shop"),
            SREPlayerShopComponent.class);
    private final Player player;
    public int balance = 0;
    public long grenadeLastPurchaseTime = 0;
    // 仅服务端存储：
    public int total_cost = 0;

    public SREPlayerShopComponent(Player player) {
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
    public void init() {
        this.balance = 0;
        this.grenadeLastPurchaseTime = 0;
        this.total_cost = 0;
        this.sync();
    }

    @Override
    public void clear() {
        init();
    }

    /**
     * 服务端Only
     */
    public int getTotalCost() {
        return this.total_cost;
    }

    /**
     * 服务端Only
     */
    public int getTotalCostAndClear() {
        int r = this.total_cost;
        this.total_cost = 0;
        return r;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    public void addToBalance(int amount) {
        if (tryConvertWizardBalance(amount)) {
            return;
        }
        this.setBalance(this.balance + amount);
    }

    public void setBalance(int amount) {
        if (tryConvertWizardBalance(amount)) {
            return;
        }
        if (this.balance != amount) {
            this.balance = amount;
            // try{
            // throw new RuntimeException("Hello!");
            // }catch(Exception e){
            // SRE.LOGGER.info("Balance {}",amount,e);
            // }
            this.sync();
        }
    }

    private boolean tryConvertWizardBalance(int amount) {
        if (this.player.level().isClientSide) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(this.player.level());
        if (gameWorld == null || !gameWorld.isRole(this.player, org.agmas.noellesroles.role.ModRoles.WIZARD)) {
            return false;
        }
        if (amount > 0) {
            org.agmas.noellesroles.component.ModComponents.WIZARD.get(this.player).addMana(
                    amount * org.agmas.noellesroles.config.NoellesRolesConfig.HANDLER.instance().wizardManaPerCoin);
        }
        if (this.balance != 0) {
            this.balance = 0;
            this.sync();
        }
        return true;
    }

    public void tryBuy(int index) {
        if (index < 0 || index >= getShopEntries().size())
            return;
        ShopEntry entry = getShopEntries().get(index);
        if (this.player.hasEffect(ModEffects.SAFE_TIME)) {
            // 安全时间不准买
            this.player.displayClientMessage(
                    Component
                            .translatable("message.tip.purchase_failed_with_reason",
                                    Component.translatable("message.tip.purchase_failed.safe_time"))
                            .withStyle(ChatFormatting.DARK_RED),
                    true);
            return;
        }
        if (this.player.hasEffect(ModEffects.SHOP_BANNED)) {
            // 商店被禁止
            this.player.displayClientMessage(
                    Component
                            .translatable("message.tip.purchase_failed_with_reason",
                                    Component.translatable("message.tip.purchase_failed.shop_banned"))
                            .withStyle(ChatFormatting.DARK_RED),
                    true);
            return;
        }
        // 手榴弹购买冷却检查（可配置，默认30秒）
        if (entry.stack().is(TMMItems.GRENADE)) {
            int purchaseCooldownTicks = SREConfig.instance().grenadePurchaseCooldown * 20;
            long timeSincePurchase = player.level().getGameTime() - this.grenadeLastPurchaseTime;
            if (timeSincePurchase < purchaseCooldownTicks) {
                this.player.displayClientMessage(
                        Component.translatable("message.tip.purchase_cooldown",
                                (purchaseCooldownTicks - timeSincePurchase) / 20).withStyle(ChatFormatting.DARK_RED),
                        true);
                if (this.player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.connection.send(new ClientboundSoundPacket(
                            BuiltInRegistries.SOUND_EVENT.wrapAsHolder(TMMSounds.UI_SHOP_BUY_FAIL),
                            SoundSource.PLAYERS, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), 1.0f,
                            0.9f + this.player.getRandom().nextFloat() * 0.2f,
                            serverPlayer.getRandom().nextLong()));
                }
                return;
            }
        }
        // 动态价格：实际价格由玩家的 DynamicShopComponent 决定（折扣/减价等），默认等于基础价格。
        // Dynamic price: the effective price is resolved by the player's
        // DynamicShopComponent
        // (discounts/reductions/etc.); it defaults to the base price when no modifier
        // exists.
        final int price = DynamicShopComponent.KEY.get(this.player).effectivePrice(entry);
        if (FabricLoader.getInstance().isDevelopmentEnvironment() && this.balance < price)
            this.balance = price * 10;
        boolean isOnCooldown = this.player.getCooldowns().isOnCooldown(entry.stack().getItem());
        boolean haveEnoughBalance = this.balance >= price;
        // 重置错误信息
        entry.setFailedMessage(null);
        if (haveEnoughBalance && !isOnCooldown
                && entry.canDisplay(this.player) && entry.canBuy(this.player) && !entry.isSafeTime(this.player)
                && entry.onBuy(this.player)) {
            this.total_cost += price;
            this.balance -= price;
            // 手榴弹购买后记录购买时间
            if (entry.stack().is(TMMItems.GRENADE)) {
                this.grenadeLastPurchaseTime = player.level().getGameTime();
            }
            if (this.player instanceof ServerPlayer player) {
                player.connection.send(
                        new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(TMMSounds.UI_SHOP_BUY),
                                SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 1.0f,
                                0.9f + this.player.getRandom().nextFloat() * 0.2f, player.getRandom().nextLong()));
                SRE.REPLAY_MANAGER.recordStoreBuy(player.getUUID(),
                        BuiltInRegistries.ITEM.getKey(entry.stack().getItem()), entry.stack().getCount(),
                        price);
            }
        } else {
            Component reason = null;
            if (isOnCooldown) {
                reason = Component.translatable("message.tip.purchase_failed.cooldown");
            } else if (!haveEnoughBalance) {
                reason = Component.translatable("message.tip.purchase_failed.not_enough_money");
            } else {
                reason = entry.getFailedMessage();
            }
            if (reason != null) {
                this.player.displayClientMessage(
                        Component.translatable("message.tip.purchase_failed_with_reason", reason)
                                .withStyle(ChatFormatting.DARK_RED),
                        true);
            } else {
                this.player.displayClientMessage(
                        Component.translatable("message.tip.purchase_failed").withStyle(ChatFormatting.DARK_RED), true);
            }
            if (this.player instanceof ServerPlayer player) {
                player.connection.send(new ClientboundSoundPacket(
                        BuiltInRegistries.SOUND_EVENT.wrapAsHolder(TMMSounds.UI_SHOP_BUY_FAIL), SoundSource.PLAYERS,
                        player.getX(), player.getY(), player.getZ(), 1.0f,
                        0.9f + this.player.getRandom().nextFloat() * 0.2f, player.getRandom().nextLong()));
            }
        }
        this.sync();
    }

    private @NotNull List<ShopEntry> getShopEntries() {
        final var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        final var role = gameWorldComponent.getRole(player);
        if (gameWorldComponent != null && role != null && GameUtils.isPlayerAliveAndSurvival(player)) {
            final var shopEntries = ShopContent.getShopEntries(
                    role.getIdentifier());
            if (shopEntries != null && !shopEntries.isEmpty()) {
                return shopEntries;
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

    public static boolean useBlackoutWithMultiplier(@NotNull Player player, double multtiplier) {
        return useBlackout(player,
                (int) ((double) SREWorldBlackoutComponent.getMaxDuration(player.level()) * multtiplier));
    }

    public static boolean useBlackout(@NotNull Player player, int duration) {
        SREWorldBlackoutComponent blackCCA = SREWorldBlackoutComponent.KEY.get(player.level());
        if (blackCCA.blackOutRemainingTicks > 0)
            return false;
        boolean triggered = blackCCA.triggerBlackout(true, duration);
        if (triggered) {
            // 公共 Cooldown
            player.level().players().forEach(
                    p -> p.getCooldowns().addCooldown(TMMItems.BLACKOUT, GameConstants.getBlackoutCooldownGlobal()));

            SRE.REPLAY_MANAGER.recordSkillUsed(player.getUUID(), BuiltInRegistries.ITEM.getKey(TMMItems.BLACKOUT));
            player.getCooldowns().addCooldown(TMMItems.BLACKOUT,
                    GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.BLACKOUT, 0));
        }
        return triggered;
    }

    public static boolean useMonitorBroken(@NotNull Player player, int duration) {
        SREMonitorWorldComponent monitorCCA = SREMonitorWorldComponent.KEY.get(player.level());
        if (monitorCCA.brokenTime > 0)
            return false;
        boolean triggered = monitorCCA.triggerBroken(true, duration);
        if (triggered) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    NRSounds.SHORT_CIRCUIT, SoundSource.MASTER, 5.0F, 1.0F);

            // 公共 Cooldown
            player.level().players().forEach(
                    p -> p.getCooldowns().addCooldown(TMMItems.MONITOR_BROKEN,
                            GameConstants.getMonitorBrokenCooldownGlobal()));

            SRE.REPLAY_MANAGER.recordSkillUsed(player.getUUID(),
                    BuiltInRegistries.ITEM.getKey(TMMItems.MONITOR_BROKEN));
            player.getCooldowns().addCooldown(TMMItems.MONITOR_BROKEN,
                    GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.MONITOR_BROKEN, 0));
        }
        return triggered;
    }

    public static boolean useBlackout(@NotNull Player player) {
        return useBlackout(player, SREWorldBlackoutComponent.getMaxDuration(player.level()));
    }

    public static void addGlobalPsychoCooldown(Player player) {
        var gamecca = SREGameWorldComponent.getInstance(player);
        for (var p : player.level().players()) {
            if (GameUtils.isPlayerAliveAndSurvival(p) && gamecca.isKillerTeam(p)) {
                if (!p.getCooldowns().isOnCooldown(TMMItems.PSYCHO_MODE) && !p.getUUID().equals(player.getUUID())) {
                    p.getCooldowns().addCooldown(TMMItems.PSYCHO_MODE,
                            SREConfig.instance().psychoGlobalCooldown * 20);
                }
            }
        }
    }

    /**
     * 触发psycho
     * 
     * @param player
     * @param multtiplier 时间倍乘参数
     * @param armour      护盾层数
     * @return
     */
    public static boolean usePsychoMode(@NotNull Player player, double multtiplier, int armour) {
        boolean started = SREPlayerPsychoComponent.KEY.get(player).startPsycho(multtiplier, armour);
        if (started) {
            player.getCooldowns().addCooldown(TMMItems.PSYCHO_MODE,
                    GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.PSYCHO_MODE, 0));
            addGlobalPsychoCooldown(player);
            SRE.REPLAY_MANAGER.recordSkillUsed(player.getUUID(), BuiltInRegistries.ITEM.getKey(TMMItems.PSYCHO_MODE));
        }
        return started;
    }

    /**
     * 触发psycho
     * 
     * @param player
     * @param time   时间
     * @param armour 护盾层数
     * @return
     */
    public static boolean usePsychoMode_time(@NotNull Player player, int time, int armour) {
        player.getCooldowns().addCooldown(TMMItems.PSYCHO_MODE,
                GameConstants.ITEM_COOLDOWNS.getOrDefault(TMMItems.PSYCHO_MODE, 0));
        boolean started = SREPlayerPsychoComponent.KEY.get(player).startPsycho_time(time, armour);
        if (started) {
            SRE.REPLAY_MANAGER.recordSkillUsed(player.getUUID(), BuiltInRegistries.ITEM.getKey(TMMItems.PSYCHO_MODE));
        }
        return started;
    }

    /**
     * 触发psycho
     * 
     * @param player
     * @param multtiplier 时间倍乘参数
     * @return
     */
    public static boolean usePsychoMode(@NotNull Player player, double multtiplier) {
        return usePsychoMode(player, multtiplier, 1);
    }

    /**
     * 触发psycho
     * 
     * @param player
     * @return
     */
    public static boolean usePsychoMode(@NotNull Player player) {
        return usePsychoMode(player, 1d);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("Balance", this.balance);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.balance = tag.getInt("Balance");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
