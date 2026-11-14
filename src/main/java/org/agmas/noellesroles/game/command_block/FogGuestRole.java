package org.agmas.noellesroles.game.command_block;

import java.util.List;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;

/** 雾客：制造遮挡视线的雾区，并在雾内隐身。 */
public class FogGuestRole extends NormalRole {
    public FogGuestRole() {
        super(Noellesroles.id("fog_guest"), 0xA9BBC1, false, true, MoodType.FAKE, Integer.MAX_VALUE, true);
    }

    public static void registerSkill() {
        RoleSkill.register(ModRoles.FOG_GUEST,
                RoleSkill.skill(SRE.id("fog_guest_rise"), "skill.noellesroles.fog_guest.rise", context -> {
                    ServerPlayer player = context.player();
                    if (!GameUtils.isPlayerAliveAndSurvival(player)) return false;
                    FogGuestZoneEntity zone = new FogGuestZoneEntity(ModEntities.FOG_GUEST_ZONE, player.level());
                    zone.start(player);
                    return player.level().addFreshEntity(zone);
                }).cooldownSeconds(80).announceToSelf().showOnHud(true).build(),
                RoleSkill.skill(SRE.id("fog_guest_hide"), "skill.noellesroles.fog_guest.hide", context -> {
                    ServerPlayer player = context.player();
                    if (!GameUtils.isPlayerAliveAndSurvival(player)) return false;
                    if (!FogGuestZoneEntity.isInside(player)) {
                        player.displayClientMessage(Component.translatable("message.noellesroles.fog_guest.outside"), true);
                        return false;
                    }
                    player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 5 * 20, 0, false, false, true));
                    return true;
                }).shifted(true).cooldownSeconds(10).announceToSelf().showOnHud(true).build());
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of(ModItems.FOG_BLADE.getDefaultInstance());
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        return List.of(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 130, ShopEntry.Type.TOOL),
                new ShopEntry(ModItems.FOG_BELL.getDefaultInstance(), 230, ShopEntry.Type.TOOL));
    }
}
