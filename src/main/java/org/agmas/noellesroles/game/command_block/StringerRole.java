package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.game.command_block.item.GazeRays;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.StringerRoleData;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 丝线人：先给视线里的人牵一根短线，再用线轴把人拽到面前。
 * 丝刃只割线上的人。自己的代价是短发光、短减速和半秒不能用物品，不砍冲刺。
 */
public class StringerRole extends NormalRole {
    public static final ResourceLocation DEATH_BLADE = org.agmas.noellesroles.Noellesroles.id("stringer_blade");
    public static final double MARK_RANGE = 14.0D;
    public static final double PULL_RANGE = 16.0D;
    public static final double PULL_DISTANCE = 2.5D;
    public static final int MARK_TICKS = 7 * 20;
    public static final int TARGET_SLOW = 20;
    public static final int SELF_GLOW = 30;
    public static final int PULL_HOLD = 40;

    public StringerRole(ResourceLocation id, int color, boolean innocent, boolean killer, SRERole.MoodType mood,
            int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    public static void registerSkill() {
        RoleSkill.register(ModRoles.STRINGER, RoleSkill.skill(
                SRE.id("stringer_mark"),
                "skill.noellesroles.stringer.mark",
                StringerRole::mark)
                .cooldownSeconds(18).announceToSelf().showOnHud(true).build());
    }

    private static boolean mark(RoleSkill.RoleSkillContext context) {
        ServerPlayer self = context.player();
        if (self.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(self)) {
            return false;
        }
        List<ServerPlayer> seen = GazeRays.playersAlong(self, MARK_RANGE, 0.4D);
        ServerPlayer target = null;
        for (ServerPlayer other : seen) {
            if (self.hasLineOfSight(other)) {
                target = other;
                break;
            }
        }
        if (target == null) {
            self.displayClientMessage(Component.translatable("message.noellesroles.stringer.no_target")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        StringerRoleData data = RoleData.getNullable(StringerRoleData.class, self);
        if (data == null) {
            return false;
        }
        long now = self.serverLevel().getGameTime();
        data.mark(target.getUUID(), now + MARK_TICKS);
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, MARK_TICKS, 0, false, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, TARGET_SLOW, 0, false, false, true));
        self.addEffect(new MobEffectInstance(MobEffects.GLOWING, SELF_GLOW, 0, false, false, true));
        LegendFx.pair(self.serverLevel(), self.position().add(0.0D, 0.2D, 0.0D),
                target.position().add(0.0D, 0.2D, 0.0D), 1.1F, LegendFx.STRING);
        self.serverLevel().playSound(null, self.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.45F,
                1.8F);
        self.displayClientMessage(Component.translatable("message.noellesroles.stringer.marked", target.getName())
                .withStyle(ChatFormatting.GOLD), true);
        target.displayClientMessage(Component.translatable("message.noellesroles.stringer.marked_you")
                .withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    /** 把当前标记拽到面前。失败不进入物品冷却。 */
    public static boolean pull(ServerPlayer self) {
        StringerRoleData data = RoleData.getNullable(StringerRoleData.class, self);
        ServerPlayer target = data == null ? null : data.current(self);
        if (target == null) {
            self.displayClientMessage(Component.translatable("message.noellesroles.stringer.pull_none")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (self.distanceToSqr(target) > PULL_RANGE * PULL_RANGE || !self.hasLineOfSight(target)) {
            self.displayClientMessage(Component.translatable("message.noellesroles.stringer.pull_far")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        Vec3 spot = StandSpots.find(self.serverLevel(), self.position(), StandSpots.horizontal(self, 1.0D),
                PULL_DISTANCE);
        if (spot == null) {
            self.displayClientMessage(Component.translatable("message.noellesroles.stringer.pull_stuck")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        Vec3 from = target.position();
        StandSpots.place(target, spot);
        long now = self.serverLevel().getGameTime();
        data.extend(now + PULL_HOLD);
        self.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0, false, false, true));
        self.addEffect(ModEffects.of(ModEffects.USED_BANED, 10, 0, false, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, PULL_HOLD, 0, false, false, true));
        LegendFx.pair(self.serverLevel(), from.add(0.0D, 0.2D, 0.0D), spot.add(0.0D, 0.2D, 0.0D), 1.3F,
                LegendFx.STRING);
        self.serverLevel().playSound(null, self.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                0.55F, 1.25F);
        self.displayClientMessage(Component.translatable("message.noellesroles.stringer.pulled", target.getName())
                .withStyle(ChatFormatting.GOLD), true);
        target.displayClientMessage(Component.translatable("message.noellesroles.stringer.yanked")
                .withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        shop.add(new ShopEntry(ModItems.THREAD_SPOOL.getDefaultInstance(), 65, ShopEntry.Type.TOOL));
        shop.add(new ShopEntry(ModItems.THREAD_BLADE.getDefaultInstance(), 90, ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 80, ShopEntry.Type.TOOL));
        return shop;
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of();
    }
}