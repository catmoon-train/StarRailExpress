package org.agmas.noellesroles.role.touhou.roles;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.role_data.killer.HoujuuNueRoleData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.DiscountShopEntry;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

public class THHoujuuNueRole extends TouhouRole {

    public THHoujuuNueRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public void onKill(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason) {
        var roledataOptional = RoleData.getOptional(HoujuuNueRoleData.class, killer);
        if (!roledataOptional.isPresent())
            return;
        var roledata = roledataOptional.get();
        roledata.addLayers();
        killer.displayClientMessage(Component.translatable("hud.houjuu_nue.tip.add_layer", roledata.slownessLayers),
                true);
        return;
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        ArrayList<ShopEntry> SHOP = new ArrayList<>();
        SHOP.add(new DiscountShopEntry(TMMItems.KNIFE.getDefaultInstance(), SREConfig.instance().knifePrice, 50));
        SHOP.add(new ShopEntry(TMMItems.REVOLVER.getDefaultInstance(),
                SREConfig.instance().revolverPrice, ShopEntry.Type.WEAPON));
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

    /**
     * 当赋予modifier时调用，如果需要操作modifiers列表可以直接操纵，不需要同步，也不需要调用WorldModifierComponent的sync
     * 
     * @param player
     * @param modifiers
     */
    @Override
    public void onAssignedModifiers(ServerPlayer player, Set<SREModifier> modifiers) {
        // - 自带 Jeb 与隐秘修饰符，且可与其他修饰符共存。
        modifiers.add(SEModifiers.JEB_);
        modifiers.add(SEModifiers.SECRETIVE);
    };
}
