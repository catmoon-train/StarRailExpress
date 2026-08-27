package org.agmas.noellesroles.handler.utils.client;

import org.agmas.noellesroles.role.bouns.BounsRoles;
import org.agmas.noellesroles.role.bouns.roles.BeeFamilyRole;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.client.OnRenderRoleName;
import io.wifi.starrailexpress.event.client.RoleInstinctEvents;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.Color;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import net.minecraft.world.entity.player.Player;

public class BeeFamilyClientManager {
    private static final RoleInstinctEvents.InnerRoleInstinctFunction BEE_FAMILY_INSTINCT = (client, viewer, target,
            isInstinctEnabled) -> {
        if (!SREClient.isPlayerAliveAndInSurvival())
            return TrueFalseAndCustomResult.pass();
        if (!isInstinctEnabled)
            return TrueFalseAndCustomResult.pass();
        if (target instanceof Player targetPlayer) {
            var targetRole = SREClient.gameComponent.getRole(targetPlayer);
            if (targetRole != null && targetRole instanceof BeeFamilyRole) {
                if (SREClient.gameComponent.isRole(targetPlayer, BounsRoles.BEE_QUEEN))
                    return TrueFalseAndCustomResult.custom(new Color(255, 39, 185).getRGB());
                return TrueFalseAndCustomResult.custom(new Color(255, 255, 0).getRGB());
            }
        }
        return TrueFalseAndCustomResult.pass();
    };

    public static void registerEvents() {
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(BounsRoles.BEE_QUEEN.identifier(),
                BEE_FAMILY_INSTINCT);
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(BounsRoles.BEE_WORKER.identifier(),
                BEE_FAMILY_INSTINCT);
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(BounsRoles.BEE_WASP.identifier(),
                BEE_FAMILY_INSTINCT);

        // 蜜蜂家族互相知道身份
        OnRenderRoleName.RENDER_PLAYER_ROLE.register((player, target, context, tickCounter, renderer) -> {
            if (target == null)
                return TrueFalseAndCustomResult.pass();
            if (!GameUtils.isPlayerAliveAndSurvival(player))
                return TrueFalseAndCustomResult.pass();
            if (SREClient.gameComponent != null) {
                if (SREClient.gameComponent.getRole(player) instanceof BeeFamilyRole
                        && SREClient.gameComponent.getRole(target) instanceof BeeFamilyRole targetRole) {
                    return TrueFalseAndCustomResult.custom(RoleUtils.getRoleNameWithColor(targetRole));
                }
            }
            return TrueFalseAndCustomResult.pass();
        });
    }
}
