package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.game.command_block.item.GazeRays;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.FogGuestRoleData;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** 雾客：藏进雾里就出不了手。有人近距离看着你会把雾撕开。 */
public class FogGuestRole extends NormalRole {
    public static final ResourceLocation DEATH_BLADE = org.agmas.noellesroles.Noellesroles.id("fog_blade");
    public static final int HIDE_TICKS = 6 * 20;
    public static final double SHATTER_RANGE = 8.0D;

    public FogGuestRole(ResourceLocation id, int color, boolean innocent, boolean killer, SRERole.MoodType mood,
            int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    public static void registerSkill() {
        RoleSkill.register(ModRoles.FOG_GUEST, RoleSkill.skill(
                SRE.id("fog_guest_hide"),
                "skill.noellesroles.fog_guest.hide",
                context -> hide(context.player()))
                .cooldownSeconds(22).announceToSelf().showOnHud(true).build());
    }

    public static boolean hide(ServerPlayer player) {
        FogGuestRoleData data = RoleData.getNullable(FogGuestRoleData.class, player);
        if (data == null || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        return data.hide(player.level().getGameTime());
    }

    public static boolean rejectsHarm(ServerPlayer attacker) {
        FogGuestRoleData data = attacker == null ? null : RoleData.getNullable(FogGuestRoleData.class, attacker);
        return data != null && data.isHidden();
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        shop.add(new ShopEntry(ModItems.FOG_BELL.getDefaultInstance(), 70, ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(ModItems.FOG_BLADE.getDefaultInstance(), 95, ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 80, ShopEntry.Type.TOOL));
        return shop;
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of();
    }

    public static boolean inFog(ServerPlayer player) {
        return player != null && (player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
                || player.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS));
    }

    public static boolean watched(ServerPlayer watcher, ServerPlayer target) {
        return watcher != null && target != null
                && watcher.distanceToSqr(target) <= SHATTER_RANGE * SHATTER_RANGE
                && watcher.hasLineOfSight(target)
                && GazeRays.lookingAt(watcher, target, 0.55D);
    }
}
