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

package org.agmas.noellesroles.mixin.roles.awesome_binglus;

import io.wifi.starrailexpress.content.entity.NoteEntity;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(NoteEntity.class)
public abstract class NoteEntityMixin extends Entity {
    public NoteEntityMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void tick(CallbackInfo ci) {
        NoteEntity note = (NoteEntity) (Object) this;
        final var attached = note.getAttached(ModRoles.ENTITY_NOTE_MAKER);
        if (attached != null) {
            if (note.tickCount >= 20 * 300) { // 5min
                note.remove(Entity.RemovalReason.DISCARDED);
            } else {
                try {
                    final Optional<? extends Player> first = level().players().stream()
                            .filter(player -> player.getUUID().toString().equals(attached)).findFirst();
                    if (first.isPresent()) {
                        Player player = first.get();
                        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                            note.remove(Entity.RemovalReason.DISCARDED);
                        }
                        double yawRadians = Math.toRadians(player.getYRot());
                        double offsetX = -Math.sin(yawRadians) * -0.2; // 从玩家位置向后偏移2格
                        double offsetZ = Math.cos(yawRadians) * -0.2;
                        note.setXRot(player.getXRot());
                        note.moveTo(player.getX() + offsetX, player.getY() + 1.25, player.getZ() + offsetZ,
                                note.getYRot(), note.getXRot());
                    } else {
                        note.remove(Entity.RemovalReason.DISCARDED);
                    }

                } catch (Exception ignored) {
                    note.remove(Entity.RemovalReason.DISCARDED);
                }
            }
        }
    }
}