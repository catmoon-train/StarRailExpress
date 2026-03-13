package org.agmas.noellesroles.roles.gambler;

import static io.wifi.starrailexpress.game.GameFunctions.getSpawnPos;
import static io.wifi.starrailexpress.game.GameFunctions.roomToPlayer;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.StarPlayerShopComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.SRE;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class GamblerRole extends Role {

    public GamblerRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public boolean onUseGun(Player player) {
        if (player.level().isClientSide())
            return false;
        if (player.isShiftKeyDown()) {
            GamblerPlayerComponent gamblerPlayerComponent = GamblerPlayerComponent.KEY.get(player);
            gamblerPlayerComponent.usedAbility = true;

            if (gamblerPlayerComponent.selectedRole != null) {
                if (player instanceof ServerPlayer sp) {
                    // 掉枪
                    RoleUtils.dropAndClearAllSatisfiedItems(sp, TMMItemTags.GUNS);
                }
                var role = RoleUtils.getRole(gamblerPlayerComponent.selectedRole);
                if (role == null) {
                    return false;
                }
                SRE.REPLAY_MANAGER.recordPlayerKill(null, player.getUUID(),
                        Noellesroles.id("gamble_self_kill"));
                RoleUtils.changeRole(player, role);

                StarPlayerShopComponent playerShopComponent = (StarPlayerShopComponent) StarPlayerShopComponent.KEY.get(player);
                playerShopComponent.addToBalance(50);

                if (player instanceof ServerPlayer serverPlayer) {

                    RoleUtils.sendWelcomeAnnouncement(serverPlayer);

                    teleport(player);
                }

                player.level().players().forEach(
                        p -> {
                            p.playNotifySound(NRSounds.GAMBER_DEATH, SoundSource.PLAYERS, 0.5F, 1.3F);
                            p.playNotifySound(SoundEvents.BAT_HURT, SoundSource.PLAYERS, 0.5F, 1.3F);
                        });
            } else {
                GameFunctions.killPlayer(player, true, null, Noellesroles.id("gamble_self_kill"));
            }
            return false;
        }
        return false;
    }

    private static void teleport(Player player) {

        Vec3 pos = getSpawnPos(AreasWorldComponent.KEY.get(player.level()), roomToPlayer.get(player.getUUID()));
        if (pos != null) {
            player.teleportTo(pos.x(), pos.y() + 1, pos.z());
        }

    }
}
