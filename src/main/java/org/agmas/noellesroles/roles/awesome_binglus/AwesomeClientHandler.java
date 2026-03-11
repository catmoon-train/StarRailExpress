package org.agmas.noellesroles.roles.awesome_binglus;

import org.agmas.noellesroles.component.AwesomePlayerComponent;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.player.Player;

public class AwesomeClientHandler {

    public static void renderParticleOfPlayer(Minecraft client, Player p, AwesomePlayerComponent aweC) {
        // Noellesroles.LOGGER.info(p.getScoreboardName() + ":" + aweC.nearByDeathTime);
        if (aweC.nearByDeathTime <= 0) {
            return;
        }
        DustParticleOptions greenDust = new DustParticleOptions(
                new Vector3f(1.0f
                        * ((float) aweC.nearByDeathTime
                                / (float) AwesomePlayerComponent.nearByDeathTimeRecordTime),
                        0.0f,
                        0.0f),
                (2.0f * ((float) aweC.nearByDeathTime
                        / (float) AwesomePlayerComponent.nearByDeathTimeRecordTime)) + 0.2f);
        client.level.addParticle(
                greenDust, true,
                p.getX(), p.getY() + 2.0, p.getZ(), // 在玩家头上稍上方
                0, 0, 0 // 速度为0
        );
    }

}
