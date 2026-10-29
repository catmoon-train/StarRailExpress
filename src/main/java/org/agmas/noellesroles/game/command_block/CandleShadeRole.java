package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.CandleShadeRoleData;

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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** 烛影：点亮前方所有人。对方看着你会吹灭自己的烛火，命中后短暂隐身加速。 */
public class CandleShadeRole extends NormalRole {
    public static final ResourceLocation DEATH_SNIP = org.agmas.noellesroles.Noellesroles.id("candle_snip");
    public static final double MARK_RANGE = 14.0D;
    public static final double SNUFF_RANGE = 10.0D;
    public static final int MARK_TICKS = 8 * 20;
    public static final double SNUFF_DOT = 0.55D;

    public CandleShadeRole(ResourceLocation id, int color, boolean innocent, boolean killer, SRERole.MoodType mood,
            int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    public static void registerSkill() {
        RoleSkill.register(ModRoles.CANDLE_SHADE, RoleSkill.skill(
                SRE.id("candle_shade_light"),
                "skill.noellesroles.candle_shade.light",
                context -> light(context.player()))
                .cooldownSeconds(18).announceToSelf().showOnHud(true).build());
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (entity instanceof ServerPlayer target && source.getEntity() instanceof ServerPlayer attacker
                    && attacker != target && damageTaken > 0.0F) {
                grantHitEffects(attacker);
            }
        });
    }

    public static boolean light(ServerPlayer player) {
        CandleShadeRoleData data = RoleData.getNullable(CandleShadeRoleData.class, player);
        if (data == null || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        if (data.hasMark()) {
            player.displayClientMessage(Component.translatable("message.noellesroles.candle_shade.already"), true);
            return false;
        }
        List<ServerPlayer> hit = player.serverLevel().players().stream()
                .filter(target -> target != player && GameUtils.isPlayerAliveAndSurvival(target))
                .filter(target -> player.distanceToSqr(target) <= MARK_RANGE * MARK_RANGE)
                .filter(target -> player.getLookAngle().dot(target.getEyePosition().subtract(player.getEyePosition())) > 0)
                .filter(player::hasLineOfSight)
                .toList();
        if (hit.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.noellesroles.candle_shade.miss"), true);
            return false;
        }
        long now = player.level().getGameTime();
        for (ServerPlayer target : hit) {
            data.light(target, now);
        }
        return true;
    }

    private static void grantHitEffects(ServerPlayer player) {
        if (RoleData.getNullable(CandleShadeRoleData.class, player) == null
                || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 10 * 20, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 2, 4));
    }

    @Override
    public void onKill(Player victim, boolean spawnBody, Player killer, ResourceLocation deathReason) {
        super.onKill(victim, spawnBody, killer, deathReason);
        if (killer instanceof ServerPlayer attacker && killer != victim) {
            grantHitEffects(attacker);
        }
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        shop.add(new ShopEntry(ModItems.LAMP_OIL.getDefaultInstance(), 60, ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(ModItems.CANDLE_SNIP.getDefaultInstance(), 90, ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 80, ShopEntry.Type.TOOL));
        return shop;
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of();
    }
}