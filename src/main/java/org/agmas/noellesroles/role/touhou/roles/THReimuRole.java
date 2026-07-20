package org.agmas.noellesroles.role.touhou.roles;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.KillerKnifeShopEntry;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class THReimuRole extends TouhouRole {

    public static final int FLY_COOLDOWN = 120 * 20;
    public static final int MAX_DURATION = 5 * 20;

    public THReimuRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    /**
     * 在HarpyModLoader中使用
     */
    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of(ModItems.REIMU_GOHEI.getDefaultInstance());
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        ArrayList<ShopEntry> SHOP = new ArrayList<>();
        SHOP.add(new KillerKnifeShopEntry(ModItems.DANMUKU.getDefaultInstance(), SREConfig.instance().knifePrice + 50,
                20));
        SHOP.add(new KillerKnifeShopEntry(TMMItems.KNIFE.getDefaultInstance(), SREConfig.instance().knifePrice, 50));
        SHOP.add(new ShopEntry(ModItems.FAKE_REVOLVER.getDefaultInstance(),
                100, ShopEntry.Type.WEAPON));
        SHOP.add(new ShopEntry(TMMItems.REVOLVER.getDefaultInstance(),
                SREConfig.instance().revolverPrice * 2, ShopEntry.Type.WEAPON));
        SHOP.add(new ShopEntry(TMMItems.GRENADE.getDefaultInstance(),
                SREConfig.instance().grenadePrice, ShopEntry.Type.WEAPON));

        SHOP.add(new ShopEntry(ModItems.SHORT_SHOTGUN.getDefaultInstance(),
                SREConfig.instance().shortShotgunPrice, ShopEntry.Type.WEAPON));
        SHOP.add(new ShopEntry(TMMItems.PSYCHO_MODE.getDefaultInstance(),
                SREConfig.instance().psychoModePrice, ShopEntry.Type.WEAPON) {
            @Override
            public boolean canBuy(@NotNull Player player) {
                if (player.getCooldowns().isOnCooldown(TMMItems.PSYCHO_MODE)) {
                    return false;
                }
                return super.canBuy(player);
            }

            @Override
            public boolean onBuy(@NotNull Player player) {
                if (player.getCooldowns().isOnCooldown(TMMItems.PSYCHO_MODE)) {
                    return false;
                }

                return SREPlayerShopComponent.usePsychoMode(player);
            }
        });
        // defaultEntries.add(new ShopEntry(TMMItems.POISON_VIAL.getDefaultInstance(),
        // TMMConfig.poisonVialPrice, ShopEntry.Type.POISON));
        // defaultEntries.add(new ShopEntry(TMMItems.SCORPION.getDefaultInstance(),
        // TMMConfig.scorpionPrice, ShopEntry.Type.POISON));
        SHOP.add(new ShopEntry(TMMItems.FIRECRACKER.getDefaultInstance(),
                SREConfig.instance().firecrackerPrice, ShopEntry.Type.TOOL));
        SHOP.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(),
                SREConfig.instance().lockpickPrice, ShopEntry.Type.TOOL));
        SHOP.add(new ShopEntry(TMMItems.CROWBAR.getDefaultInstance(),
                SREConfig.instance().crowbarPrice, ShopEntry.Type.TOOL));
        SHOP.add(new ShopEntry(TMMItems.BODY_BAG.getDefaultInstance(),
                SREConfig.instance().bodyBagPrice, ShopEntry.Type.TOOL));
        SHOP.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(),
                SREConfig.instance().blackoutPrice, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(@NotNull Player player) {
                return SREPlayerShopComponent.useBlackout(player);
            }
        });
        SHOP.add(new ShopEntry(new ItemStack(TMMItems.NOTE, 4), SREConfig.instance().notePrice,
                ShopEntry.Type.TOOL));
        return SHOP;
    }

    public static boolean checkPlayerIsOutOfAreas(ServerPlayer player, AreasWorldComponent areas) {
        var playArea = areas.getPlayArea();
        if (playArea.contains(player.position())) {
            return false;
        }
        return true;
    }

    @Override
    public void serverTick(ServerPlayer player) {
        var abilityCCA = getAbilityComponent(player);
        var areas = AreasWorldComponent.getInstance(player);
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;
        if (player.getAbilities().flying || player.getAbilities().mayfly) {
            if (!areas.areasSettings.canJump) {
                stopFlying(player);
                return;
            }
            if (checkPlayerIsOutOfAreas(player, areas)) {
                stopFlying(player);
                return;
            }
            if (abilityCCA.duration <= 0) {
                abilityCCA.setDuration(0);
                stopFlying(player);
            }
        }
    }

    @Override
    public void clientTick(Player player) {
    }

    public static void stopFlying(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable("skill.noellesroles.reimu.stopped").withStyle(ChatFormatting.RED), true);
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.getAbilities().setFlyingSpeed(0.05F);
        player.fallDistance = 0;
        player.onUpdateAbilities();
    }

    public static void startFlying(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable("skill.noellesroles.reimu.started").withStyle(ChatFormatting.GREEN), true);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.getAbilities().setFlyingSpeed(0.18F);
        player.onUpdateAbilities();
    }

}
