package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.PaperDollRoleData;
import org.agmas.noellesroles.game.command_block.item.GazeRays;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 纸人：受击后半秒内按技能免除此击并后闪，成功后冷却 32 秒。
 * 成功后虚弱 3 秒、发光 2 秒，冲刺不缩短。
 */
public class PaperDollRole extends NormalRole {
    public static final ResourceLocation DEATH_CUT = org.agmas.noellesroles.Noellesroles.id("paper_cut");
    public static final ResourceLocation SKILL_ID = SRE.id("paper_doll_arm");
    public static final double TURN_RANGE = 8.0D;

    public PaperDollRole(ResourceLocation id, int color, boolean innocent, boolean killer, SRERole.MoodType mood,
            int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    public static void registerSkill() {
        RoleSkill.register(ModRoles.PAPER_DOLL, RoleSkill.skill(
                SKILL_ID,
                "skill.noellesroles.paper_doll.arm",
                PaperDollRole::arm)
                .cooldownSeconds(32).announceToSelf().showOnHud(true).build(),
                RoleSkill.skill(SRE.id("paper_doll_turn"), "skill.noellesroles.paper_doll.turn", PaperDollRole::turn)
                        .cooldownSeconds(60).announceToSelf().showOnHud(true).build());
    }

    public static boolean deferDeath(Player victim, boolean spawnBody,
            Player killer, ResourceLocation reason, boolean forced) {
        if (forced || !(victim instanceof ServerPlayer self)
                || !CommandBlockDirector.isActive(self.serverLevel())
                || !SREGameWorldComponent.KEY.get(self.level()).isRole(self, ModRoles.PAPER_DOLL)) {
            return false;
        }
        PaperDollRoleData data = RoleData.getNullable(PaperDollRoleData.class, self);
        return data != null && data.deferDeath(self, spawnBody, killer, reason);
    }

    private static boolean arm(RoleSkill.RoleSkillContext context) {
        ServerPlayer self = context.player();
        if (self.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(self)) {
            return false;
        }
        PaperDollRoleData data = RoleData.getNullable(PaperDollRoleData.class, self);
        if (data == null) {
            return false;
        }
        if (!data.dodge(self)) {
            self.displayClientMessage(Component.translatable("message.noellesroles.paper_doll.no_hit")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        return true;
    }

    /** 让准星内的单个目标随机水平转向，保持位置和俯仰角，服务端同步到本人视角。 */
    private static boolean turn(RoleSkill.RoleSkillContext context) {
        ServerPlayer self = context.player();
        if (!GameUtils.isPlayerAliveAndSurvival(self)) {
            return false;
        }
        ServerPlayer target = GazeRays.firstVisiblePlayer(self, TURN_RANGE, 0.3D);
        if (target == null) {
            self.displayClientMessage(Component.translatable("message.noellesroles.paper_doll.turn_no_target")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        float yaw = self.getRandom().nextFloat() * 360.0F - 180.0F;
        float pitch = target.getXRot();
        target.setYRot(yaw);
        target.setYHeadRot(yaw);
        target.setYBodyRot(yaw);
        target.connection.teleport(target.getX(), target.getY(), target.getZ(), yaw, pitch);
        self.displayClientMessage(Component.translatable("message.noellesroles.paper_doll.turned", target.getDisplayName())
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        target.displayClientMessage(Component.translatable("message.noellesroles.paper_doll.turn_received")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        shop.add(new ShopEntry(ModItems.PAPER_CUT.getDefaultInstance(), 80, ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 80, ShopEntry.Type.TOOL));
        return shop;
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of();
    }
}