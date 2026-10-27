package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.game.command_block.item.GazeRays;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.PendulumRoleData;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** 钟摆：先记下一个人的位置，五秒后把他摆回去。秒针只收刚刚被摆回去的人。 */
public class PendulumRole extends NormalRole {
    public static final ResourceLocation DEATH_NEEDLE = org.agmas.noellesroles.Noellesroles.id("second_needle");
    public static final double MARK_RANGE = 16.0D;
    public static final int SWING_DELAY = 5 * 20;
    public static final int RETURN_WINDOW = 6 * 20;
    public static final double RETURN_LIMIT = 28.0D;

    public PendulumRole(ResourceLocation id, int color, boolean innocent, boolean killer, SRERole.MoodType mood,
            int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    public static void registerSkill() {
        RoleSkill.register(ModRoles.PENDULUM, RoleSkill.skill(
                SRE.id("pendulum_mark"),
                "skill.noellesroles.pendulum.mark",
                context -> mark(context.player()))
                .cooldownSeconds(20).announceToSelf().showOnHud(true).build());
    }

    public static boolean mark(ServerPlayer player) {
        PendulumRoleData data = RoleData.getNullable(PendulumRoleData.class, player);
        if (data == null || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        if (data.isPending()) {
            player.displayClientMessage(Component.translatable("message.noellesroles.pendulum.already"), true);
            return false;
        }
        List<ServerPlayer> hit = GazeRays.playersAlong(player, MARK_RANGE, 0.35D);
        if (hit.isEmpty() || !player.hasLineOfSight(hit.get(0))) {
            player.displayClientMessage(Component.translatable("message.noellesroles.pendulum.miss"), true);
            return false;
        }
        return data.mark(hit.get(0), player.level().getGameTime());
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        shop.add(new ShopEntry(ModItems.CLOCK_HAMMER.getDefaultInstance(), 65, ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(ModItems.SECOND_NEEDLE.getDefaultInstance(), 90, ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 80, ShopEntry.Type.TOOL));
        return shop;
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of();
    }
}
