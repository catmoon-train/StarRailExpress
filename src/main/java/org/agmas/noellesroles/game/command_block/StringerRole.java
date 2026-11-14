package org.agmas.noellesroles.game.command_block;

import java.util.List;
import java.util.UUID;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.command_block.item.GazeRays;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.StringerRoleData;
import org.agmas.noellesroles.utils.OpenScreenManager;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** 丝线人：缠丝控制、线轴拉近、双人牵线；所有技能判定由服务端完成。 */
public class StringerRole extends NormalRole {
    public static final ResourceLocation DEATH_BLADE = Noellesroles.id("stringer_blade");
    public static final ResourceLocation INVENTORY = Noellesroles.id("stringer_inventory");
    public static final double MARK_RANGE = 15.0D;
    public static final double PULL_DISTANCE = 2.5D;
    public static final double LINK_DISTANCE = 10.0D;
    public static final int MARK_TICKS = 10 * 20;
    public static final int LINK_TICKS = 60 * 20;

    public StringerRole(ResourceLocation id, int color, boolean innocent, boolean killer, SRERole.MoodType mood,
            int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    public static void registerSkill() {
        RoleSkill.register(ModRoles.STRINGER,
                RoleSkill.skill(SRE.id("stringer_mark"), "skill.noellesroles.stringer.mark", StringerRole::mark)
                        .cooldownSeconds(35).announceToSelf().showOnHud(true).build(),
                RoleSkill.skill(SRE.id("stringer_link"), "skill.noellesroles.stringer.link", StringerRole::link)
                        .cooldownSeconds(90).announceToSelf().showOnHud(true).build());
    }

    public static boolean canAct(ServerPlayer self) {
        return !self.isSpectator() && GameUtils.isPlayerAliveAndSurvival(self)
                && SREGameWorldComponent.KEY.get(self.level()).isRole(self, ModRoles.STRINGER);
    }

    private static boolean mark(RoleSkill.RoleSkillContext context) {
        ServerPlayer self = context.player();
        if (!canAct(self)) return false;
        ServerPlayer target = GazeRays.firstVisiblePlayer(self, MARK_RANGE, 0.4D);
        StringerRoleData data = RoleData.getNullable(StringerRoleData.class, self);
        if (target == null || data == null) {
            message(self, "no_target");
            return false;
        }
        if (!io.wifi.starrailexpress.event.TargetingEvents.ALLOW_SKILL_TARGET.invoker().allow(self, target)) return false;
        data.mark(target, MARK_TICKS);
        LegendFx.pair(self.serverLevel(), self.position().add(0, 0.2D, 0),
                target.position().add(0, 0.2D, 0), 1.1F, LegendFx.STRING);
        self.serverLevel().playSound(null, self.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 0.45F, 1.8F);
        self.displayClientMessage(Component.translatable("message.noellesroles.stringer.marked", target.getName())
                .withStyle(ChatFormatting.GOLD), true);
        message(target, "marked_you");
        return true;
    }

    /** 打开选择不消耗冷却；只有经专用包提交的有效双人选择才返回成功。 */
    private static boolean link(RoleSkill.RoleSkillContext context) {
        ServerPlayer self = context.player();
        StringerRoleData data = RoleData.getNullable(StringerRoleData.class, self);
        if (!canAct(self) || data == null || self.hasEffect(ModEffects.SKILL_BANED)) return false;
        UUID[] choice = data.pendingChoice();
        if (choice == null) {
            data.beginSelection();
            OpenScreenManager.openScreen(self, INVENTORY);
            return false;
        }
        ServerPlayer first = self.server.getPlayerList().getPlayer(choice[0]);
        ServerPlayer second = self.server.getPlayerList().getPlayer(choice[1]);
        if (first == null || second == null || first == second
                || first.level() != self.level() || second.level() != self.level()
                || !GameUtils.isPlayerAliveAndSurvival(first) || !GameUtils.isPlayerAliveAndSurvival(second)
                || !data.selectionOpen()) {
            message(self, "link_invalid");
            return false;
        }
        if (!io.wifi.starrailexpress.event.TargetingEvents.ALLOW_SKILL_TARGET.invoker().allow(self, first)
                || !io.wifi.starrailexpress.event.TargetingEvents.ALLOW_SKILL_TARGET.invoker().allow(self, second)) {
            return false;
        }
        data.link(first, second, LINK_TICKS);
        data.endSelection();
        message(first, "linked_you");
        message(second, "linked_you");
        return true;
    }

    /** 接收器只转发 UUID；统一技能入口复核冷却、禁用、事件和消耗。 */
    public static void selectLink(ServerPlayer self, UUID first, UUID second) {
        StringerRoleData data = RoleData.getNullable(StringerRoleData.class, self);
        if (!canAct(self) || data == null || !data.selectionOpen() || self.hasEffect(ModEffects.SKILL_BANED)) return;
        data.setPendingChoice(first, second);
        try {
            RoleSkill.beginUse(self, null, 1, RoleSkill.Phase.PRESS, false);
        } finally {
            data.clearPendingChoice();
        }
    }

    /** 拉取最近的有效缠丝目标；没有额外距离、视线或旧版代价。 */
    public static boolean pull(ServerPlayer self) {
        if (!canAct(self) || self.hasEffect(ModEffects.USED_BANED)) return false;
        StringerRoleData data = RoleData.getNullable(StringerRoleData.class, self);
        ServerPlayer target = data == null ? null : data.current(self);
        if (target == null) {
            message(self, "pull_none");
            return false;
        }
        Vec3 spot = StandSpots.find(self.serverLevel(), self.position(), StandSpots.horizontal(self, 1), PULL_DISTANCE);
        if (spot == null || !self.serverLevel().noCollision(target,
                target.getBoundingBox().move(spot.subtract(target.position())))) {
            message(self, "pull_stuck");
            return false;
        }
        Vec3 from = target.position();
        StandSpots.place(target, spot);
        data.hold(self, 6);
        LegendFx.pair(self.serverLevel(), from.add(0, 0.2D, 0), spot.add(0, 0.2D, 0), 1.3F, LegendFx.STRING);
        self.serverLevel().playSound(null, self.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.55F, 1.25F);
        self.displayClientMessage(Component.translatable("message.noellesroles.stringer.pulled", target.getName())
                .withStyle(ChatFormatting.GOLD), true);
        message(target, "yanked");
        return true;
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("message.noellesroles.stringer." + key)
                .withStyle(ChatFormatting.GOLD), true);
    }

    @Override
    public void onDeath(net.minecraft.world.entity.player.Player victim, boolean spawnBody,
            net.minecraft.world.entity.player.Player killer, ResourceLocation deathReason, boolean forceDeath) {
        StringerRoleData data = RoleData.getNullable(StringerRoleData.class, victim);
        if (data != null) data.clear();
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        return List.of(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 80, ShopEntry.Type.TOOL),
                new ShopEntry(ModItems.THREAD_BALL.getDefaultInstance(), 250, ShopEntry.Type.TOOL));
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of(ModItems.THREAD_SPOOL.getDefaultInstance(), ModItems.THREAD_BLADE.getDefaultInstance());
    }
}
