package org.agmas.noellesroles.role.touhou.roles;

import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class THReimuRole extends TouhouRole {

    public static final int FLY_COOLDOWN = 120 * 20;
    public static final int MAX_DURATION = 5 * 20;

    public THReimuRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    public static boolean checkPlayerIsOutOfAreas(ServerPlayer player, AreasWorldComponent areas) {
        var playArea = areas.getPlayArea();
        if (playArea.contains(player.position())) {
            return false;
        }
        return true;
    }

    @Override
    public void serverTick(ServerPlayer player) {
        var abilityCCA = getAbilityComponent(player);
        var areas = AreasWorldComponent.getInstance(player);
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;
        if (player.getAbilities().flying || player.getAbilities().mayfly) {
            if (!areas.areasSettings.canJump) {
                stopFlying(player);
                return;
            }
            if (checkPlayerIsOutOfAreas(player, areas)) {
                stopFlying(player);
                return;
            }
            if (abilityCCA.duration <= 0) {
                abilityCCA.setDuration(0);
                stopFlying(player);
            }
        }
    }

    @Override
    public void clientTick(Player player) {
    }

    public static void stopFlying(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable("skill.noellesroles.reimu.stopped").withStyle(ChatFormatting.RED), true);
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.fallDistance = 0;
        player.onUpdateAbilities();
    }

    public static void startFlying(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable("skill.noellesroles.reimu.started").withStyle(ChatFormatting.GREEN), true);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
    }

}
