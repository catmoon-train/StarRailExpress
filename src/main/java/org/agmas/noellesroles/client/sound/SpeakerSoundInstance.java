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

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 跟随玩家肩部的循环音源。位置由 tick 与 mixin 每帧刷新。
 */
public class SpeakerSoundInstance extends AbstractTickableSoundInstance {
    private final UUID ownerId;
    private boolean released;

    public SpeakerSoundInstance(SoundEvent sound, UUID ownerId, Vec3 pos) {
        super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.ownerId = ownerId;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0F;
        this.pitch = 1.0F;
        this.relative = false;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public void refreshPosition(Player player, float partialTick) {
        Vec3 pos = shoulderPos(player, partialTick);
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
    }

    public static Vec3 shoulderPos(Player player, float partialTick) {
        double x = Mth.lerp(partialTick, player.xo, player.getX());
        double y = Mth.lerp(partialTick, player.yo, player.getY()) + player.getBbHeight() * 0.78;
        double z = Mth.lerp(partialTick, player.zo, player.getZ());
        float yaw = (float) Math.toRadians(Mth.lerp(partialTick, player.yRotO, player.getYRot()));
        // 右肩偏置，让别人听到的位置和扛着的音响对上
        x -= Mth.sin(yaw + (float) Math.PI / 2.0F) * 0.32;
        z += Mth.cos(yaw + (float) Math.PI / 2.0F) * 0.32;
        return new Vec3(x, y, z);
    }

    public void release() {
        this.released = true;
        this.stop();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (released) {
            this.stop();
        }
    }
}
