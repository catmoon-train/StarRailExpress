/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.mixin.client.roles.panda;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.roles.neutral.panda.PandaClientHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(Panda.class)
public abstract class PandaClientMixin extends Animal {
    protected PandaClientMixin(EntityType<? extends Animal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void updateWalkAnimation(float f) {
        super.updateWalkAnimation(f);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void tick(CallbackInfo ci) {
        PandaClientHandle.pandaMap.forEach(
                (uuid, panda) -> {
                    if (panda.getUUID().equals((getUUID()))) {
                        Minecraft instance = Minecraft.getInstance();
                        ClientPacketListener connection = instance.getConnection();
                        if (connection != null) {
                            Optional<PlayerInfo> first = connection.getOnlinePlayers().stream()
                                    .filter(player -> player.getProfile().getId().equals(uuid)).findFirst();
                            if (first.isEmpty()) {
                                this.discard();
                            } else {
                                PlayerInfo playerInfo = first.get();
                                if (playerInfo.getGameMode() == GameType.SPECTATOR) {
                                    this.discard();
                                } else {
                                    ClientLevel level = instance.level;
                                    Player playerByUUID = level.getPlayerByUUID(uuid);
                                    if (playerByUUID != null) {
                                        this.walkAnimation.position = playerByUUID.walkAnimation.position;
                                        this.walkAnimation.speedOld = playerByUUID.walkAnimation.speedOld;
                                        this.walkAnimation.speed = playerByUUID.walkAnimation.speed;
                                        this.xo = playerByUUID.xo;
                                        this.yo = playerByUUID.yo;
                                        this.zo = playerByUUID.zo;
                                        this.setPos(playerByUUID.getX(), playerByUUID.getY(), playerByUUID.getZ());
                                        // 使用玩家的 WalkAnimation
                                        // calculateEntityAnimation(false);
                                        this.setRot(playerByUUID.getYRot(), playerByUUID.getXRot());
                                        this.setYHeadRot(playerByUUID.getYHeadRot());
                                        this.setYBodyRot(playerByUUID.yBodyRot);
                                    }
                                }
                            }
                        }
                    }
                });
    }
}
