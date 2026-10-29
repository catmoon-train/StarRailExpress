package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.command_block.item.GazeRays;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.DreadlordRoleData;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 恐惧魔王：伪装成乘客。盯住一个人会让所有人看见自己被注视，并打乱皮肤和名牌。
 * 恐惧技能开启 22.5 秒狂暴，走 Psycho 的倒计时、滤镜和追杀音。二技能遁入黑暗最多持续 10 秒。
 */
public class DreadlordRole extends NormalRole {
    public static final ResourceLocation DEATH = Noellesroles.id("reaper_scythe");
    public static final double GAZE_RANGE = 16.0D;
    public static final double GAZE_DOT = 0.82D;
    public static final int GAZE_TICKS = 3 * 20;
    public static final int DREAD_TICKS = 10 * 20;
    public static final int KILL_FORM_TICKS = 450;
    public static final int FORM_COOLDOWN = 90;
    public static final int FORM_COST = 100;
    public static final int DARK_COOLDOWN = 30 * 20;
    public static final int DARK_DURATION = 10 * 20;
    public static final int AMBUSH_LOCK = 20;
    public static final int EXECUTE_COOLDOWN = 170;
    public static final int HIT_STUN = 3 * 20;
    /** 狂暴中被命中的僵直。黑暗游走仍然无敌，不进这段硬直。 */
    public static final int KILLING_HIT_STUN = 8 * 20;
    public static final double AMBUSH_RANGE = 2.2D;
    public static final double KILL_SCALE = 1.5D;

    public DreadlordRole(ResourceLocation id, int color, boolean innocent, boolean killer, SRERole.MoodType mood,
            int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    @Override
    public Item getPsychoItem() {
        return ModItems.REAPER_SCYTHE;
    }

    @Override
    public boolean shouldClearGrantedPsychoWeapon(Player player, Item weapon) {
        return false;
    }

    @Override
    public ResourceLocation getPsychoSkin(Player player, boolean isSlim) {
        return Noellesroles.id("textures/entity/player/dreadlord.png");
    }

    public static void registerSkill() {
        RoleSkill.register(ModRoles.DREADLORD,
                RoleSkill.skill(
                        SRE.id("dreadlord_form"),
                        "skill.noellesroles.dreadlord.form",
                        context -> useForm(context.player()))
                        .cooldownSeconds(FORM_COOLDOWN)
                        .announceToSelf().showOnHud(true).build(),
                RoleSkill.skill(
                        SRE.id("dreadlord_dark"),
                        "skill.noellesroles.dreadlord.dark",
                        context -> useDark(context.player()))
                        .cooldownTicks(DARK_COOLDOWN)
                        .toggleable(true)
                        .announceToSelf().showOnHud(true).build());
    }

    public static boolean useForm(ServerPlayer player) {
        DreadlordRoleData data = RoleData.getNullable(DreadlordRoleData.class, player);
        if (data == null || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        return data.useForm();
    }

    public static boolean useDark(ServerPlayer player) {
        DreadlordRoleData data = RoleData.getNullable(DreadlordRoleData.class, player);
        if (data == null || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        return data.useDark();
    }

    public static boolean isDreadlord(Player player) {
        return player != null && SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.DREADLORD);
    }

    public static DreadlordRoleData dataOf(Player player) {
        return RoleData.getNullable(DreadlordRoleData.class, player);
    }

    /** 黑暗游走和杀戮形态都无敌，列车碾压和强制死亡除外。 */
    public static boolean rejectsDeath(Player player, ResourceLocation reason, boolean forced) {
        if (!isDreadlord(player) || forced || isTrain(reason)) {
            return false;
        }
        DreadlordRoleData data = dataOf(player);
        return data != null && (data.isDark() || data.isKilling());
    }

    public static boolean isTrain(ResourceLocation reason) {
        return reason != null && (GameConstants.DeathReasons.FELL_OUT_OF_TRAIN.equals(reason)
                || reason.getPath().contains("train"));
    }

    public static void stun(ServerPlayer target, int ticks) {
        target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, ticks, 0, false, false, true));
        target.addEffect(new MobEffectInstance(ModEffects.TURN_BANED, ticks, 0, false, false, true));
    }

    public static ServerPlayer nearestLiving(ServerPlayer self, double range) {
        ServerPlayer nearest = null;
        double best = range * range;
        for (ServerPlayer other : self.serverLevel().players()) {
            if (other == self || !GameUtils.isPlayerAliveAndSurvival(other)) {
                continue;
            }
            double dist = other.distanceToSqr(self);
            if (dist <= best && self.hasLineOfSight(other)) {
                best = dist;
                nearest = other;
            }
        }
        return nearest;
    }

    public static boolean lookingAt(ServerPlayer viewer, ServerPlayer target) {
        return viewer.distanceToSqr(target) <= GAZE_RANGE * GAZE_RANGE
                && GazeRays.lookingAt(viewer, target, GAZE_DOT);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        shop.add(new ShopEntry(ModItems.REAPER_SCYTHE.getDefaultInstance(), 90, ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 80, ShopEntry.Type.TOOL));
        shop.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), 150, ShopEntry.Type.TOOL));
        return shop;
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of();
    }
}
