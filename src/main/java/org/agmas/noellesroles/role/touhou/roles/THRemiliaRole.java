package org.agmas.noellesroles.role.touhou.roles;

import org.agmas.noellesroles.handler.THEventHandler;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class THRemiliaRole extends TouhouRole {
    public static final int COOLDOWN_TICKS = 20 * 120;

    public THRemiliaRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public InteractionResult rightClickEntity(Player player, Entity target) {
        if (!GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player))
            return InteractionResult.PASS;
        if (target instanceof PlayerBodyEntity be
                && !org.agmas.noellesroles.content.entity.DoomedSinnerBodyEntity.isDoomedSinnerBody(be)) {
            PlayerBodyEntityComponent bdrc = PlayerBodyEntityComponent.KEY.get(be);
            bdrc.playerRole = THEventHandler.getRandomRole().identifier();
            bdrc.sync();
            be.setDeathReason(THEventHandler.getRandomDeathReason());
        }
        return InteractionResult.PASS;
    }

    @Override
    public ResourceLocation getPsychoSkin(Player player, boolean isSlim) {
        return SRE.id("textures/entity/custom_psycho/remilia.png");
    }
}
