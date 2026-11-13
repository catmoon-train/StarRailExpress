package org.agmas.noellesroles.game.roles.innocence.decree;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.SkillCastAnnounce;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.innocence.DecreeMageRoleData;
import org.agmas.noellesroles.utils.MoneyUtils;
import org.agmas.noellesroles.utils.OpenScreenManager;

/** 律令法师：先编辑律令，确认后由服务端原子校验并支付。 */
public final class DecreeMageRole extends NormalRole {
    public static final ResourceLocation ID = Noellesroles.id("decree_mage");
    public static final ResourceLocation SKILL_ID = Noellesroles.id("decree_mage_cast");

    public DecreeMageRole() {
        super(ID, 0xEED796, true, false, MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false);
        setRoleData(DecreeMageRoleData::new);
        setCanSeeCoin(true);
    }

    public static RoleSkill.Definition skill() {
        return RoleSkill.skill(SKILL_ID, "skill.noellesroles.decree_mage", context -> {
            if (canCast(context.player())) {
                OpenScreenManager.openScreen(context.player(), OpenScreenManager.DECREE_MAGE_SCREEN);
            }
            // GUI 打开不是施法，不提前扣费、消耗次数或开始冷却。
            return false;
        }).initialCooldownSeconds(80).cooldownSeconds(65).showOnHud(true).build();
    }

    private static boolean canCast(ServerPlayer player) {
        var game = SREGameWorldComponent.KEY.get(player.level());
        return game.isRunning() && game.isRole(player, ModRoles.DECREE_MAGE)
                && GameUtils.isPlayerAliveAndSurvival(player)
                && !player.hasEffect(ModEffects.SKILL_BANED) && !player.hasEffect(ModEffects.SKILL_FREEZED)
                && !player.hasEffect(ModEffects.SAFE_TIME)
                && !DecreeService.isRestricted(player, Decree.SKILL);
    }

    public static void cast(ServerPlayer player, String decreeId) {
        Decree decree = Decree.byId(decreeId);
        if (decree == null) { message(player, "invalid"); return; }
        if (!canCast(player)) { message(player, "unavailable"); return; }
        var data = RoleData.getNullable(DecreeMageRoleData.class, player);
        var ability = SREAbilityPlayerComponent.KEY.get(player);
        var definition = RoleSkill.getDefinitions(ModRoles.DECREE_MAGE).stream()
                .filter(skill -> skill.id().equals(SKILL_ID)).findFirst().orElse(null);
        if (data == null || definition == null) return;
        ability.ensureSkills(RoleSkill.getDefinitions(ModRoles.DECREE_MAGE));
        if (!ability.canUseSkill(SKILL_ID)) { ability.showUnavailableMessage(SKILL_ID); return; }
        if (data.isUsed(decree)) { message(player, "used"); return; }
        if (!RoleSkill.beforeUse(player, ModRoles.DECREE_MAGE)) return;
        if (!MoneyUtils.cost(player, decree.cost())) { message(player, "funds"); return; }
        data.markUsed(decree);
        ability.markSkillUsed(definition);
        DecreeService.cast(player, decree);
        SkillCastAnnounce.tryAnnounce(player, ModRoles.DECREE_MAGE, definition);
        RoleSkill.afterUse(player, ModRoles.DECREE_MAGE);
    }

    private static void message(ServerPlayer player, String suffix) {
        player.displayClientMessage(Component.translatable("message.noellesroles.decree." + suffix), true);
    }
}
