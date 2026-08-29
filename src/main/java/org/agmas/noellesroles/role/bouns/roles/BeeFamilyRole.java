package org.agmas.noellesroles.role.bouns.roles;

import org.agmas.noellesroles.handler.utils.BeeFamilyManager;
import org.agmas.noellesroles.role.bouns.BounsRoles;
import org.agmas.noellesroles.role_data.neutral.BeeFamilyRoleData;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;

import io.wifi.starrailexpress.api.CustomWinnerRoleInterface;
import io.wifi.starrailexpress.api.EggRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class BeeFamilyRole extends EggRole implements CustomWinnerRoleInterface {

    private static final int BEE_WORKER_DEATH_TIMEOUT_TICKS = 120 * 20;

    public BeeFamilyRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        this.addFlag("bee_family");
        this.setCanBePoisoned(false);
        this.setRoleData(BeeFamilyRoleData::new);
    }

    @Override
    public boolean didPlayerWin(ServerPlayer player, boolean original, WinStatus winStatus) {
        final var roundEnd = SREGameRoundEndComponent.KEY.get(player.level());
        if (winStatus == WinStatus.CUSTOM || winStatus == WinStatus.CUSTOM_COMPONENT) {
            if (roundEnd.CustomWinnerID != null)
                if (roundEnd.CustomWinnerID.equals("bee_family")) {
                    return true;
                }
        }
        return original;
    }

    @Override
    public void onInit(MinecraftServer server, ServerPlayer player) {
        if (RoleUtils.isPlayerTheJob(player, BounsRoles.BEE_WORKER)) {
            getAbilityComponent(player).setDuration(BEE_WORKER_DEATH_TIMEOUT_TICKS);
        }
        player.displayClientMessage(getChannelText(player), true);
    }

    @Override
    public void onDeath(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason,
            boolean forceDeath) {
        if (!(victim instanceof ServerPlayer player))
            return;
        var role = RoleUtils.getPlayerRole(victim);
        if (role.equals(BounsRoles.BEE_QUEEN)) {
            final var roledata = RoleData.getNullable(BeeFamilyRoleData.class, player);
            if (roledata != null) {
                if (roledata.markTarget != null) {
                    var reviveTarget = player.serverLevel().getPlayerByUUID(roledata.markTarget);
                    if (reviveTarget instanceof ServerPlayer serverRevive
                            && !GameUtils.isPlayerAliveAndSurvival(serverRevive)) {
                        final SRERole beforeRole = RoleUtils.getPlayerRole(serverRevive);
                        RoleUtils.changeRole(reviveTarget, BounsRoles.BEE_QUEEN);
                        MCItemsUtils.clearItem(serverRevive);

                        // 给予金币
                        final var reviveShopCca = SREPlayerShopComponent.KEY.get(serverRevive);
                        reviveShopCca.balance = (SREPlayerShopComponent.KEY.get(victim).balance);
                        if (reviveShopCca.balance < 100) {
                            reviveShopCca.balance = 100;
                        }
                        reviveShopCca.sync();
                        GameUtils.revivePlayerToItsRoom(serverRevive);
                        RoleUtils.sendWelcomeAnnouncement(serverRevive);
                        if (!(beforeRole instanceof BeeFamilyRole))
                            RoleData.ifPresent(BeeFamilyRoleData.class, serverRevive,
                                    (data) -> data.beforeRole = beforeRole);
                    }
                    // roledata.markTarget;
                }
            }
        }
        // 检查蜜蜂家族是否全体死亡。如果是恢复死者原本职业。
        BeeFamilyManager.checkBeeFamilyFailure(player.serverLevel());
        return;
    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;
        if (RoleUtils.isPlayerTheJob(player, BounsRoles.BEE_WORKER)) {
            if (getAbilityComponent(player).duration <= 0) {
                GameUtils.forceKillPlayer(player, true, null, GameConstants.DeathReasons.TIMEOUT);
            }
        }
    }

    public static Component getChannelText(Player player) {
        BeeFamilyRoleData roleData = RoleData.getNullable(BeeFamilyRoleData.class, player);
        if (roleData == null) {
            return Component.empty();
        }

        Component cdText = Component
                .translatable("hud.noellesroles.bee_family.channel",
                        roleData.beeChannel
                                ? Component.translatable("hud.noellesroles.bee_family.channel.bee")
                                        .withStyle(ChatFormatting.YELLOW)
                                : Component.translatable("hud.noellesroles.bee_family.channel.normal")
                                        .withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GOLD);
        return cdText;
    }
}
