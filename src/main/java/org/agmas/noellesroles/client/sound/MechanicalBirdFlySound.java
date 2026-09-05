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

package org.agmas.noellesroles.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.agmas.noellesroles.content.entity.MechanicalBirdEntity;

/** 机械小鸟操控时的持续飞行音。 */
public class MechanicalBirdFlySound extends AbstractTickableSoundInstance {
    private static MechanicalBirdFlySound current;

    private final MechanicalBirdEntity bird;

    private MechanicalBirdFlySound(MechanicalBirdEntity bird) {
        super(SoundEvents.ELYTRA_FLYING, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.bird = bird;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.35F;
        this.pitch = 1.25F;
        this.x = (float) bird.getX();
        this.y = (float) bird.getY();
        this.z = (float) bird.getZ();
    }

    public static void ensurePlaying(MechanicalBirdEntity bird) {
        Minecraft client = Minecraft.getInstance();
        if (current != null && client.getSoundManager().isActive(current) && current.bird == bird) {
            return;
        }
        current = new MechanicalBirdFlySound(bird);
        client.getSoundManager().play(current);
    }

    @Override
    public void tick() {
        Minecraft client = Minecraft.getInstance();
        if (bird.isRemoved() || client.getCameraEntity() != bird) {
            stop();
            if (current == this) {
                current = null;
            }
            return;
        }
        this.x = (float) bird.getX();
        this.y = (float) bird.getY();
        this.z = (float) bird.getZ();
        this.volume = bird.isDashing() ? 0.7F : 0.4F;
        this.pitch = bird.isDashing() ? 1.55F : 1.25F;
    }
}
