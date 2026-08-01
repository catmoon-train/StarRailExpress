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

package pro.fazeclan.river.stupid_express.modifier.paranoid;

import io.wifi.starrailexpress.index.TMMSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

import java.util.List;

public class ParanoidHandler {
    private static final List<SoundEvent> SPOOKY_SOUNDS = List.of(
            TMMSounds.BLOCK_DOOR_TOGGLE,
            TMMSounds.BLOCK_LIGHT_TOGGLE,
            TMMSounds.ITEM_REVOLVER_SHOOT,
            TMMSounds.ITEM_KNIFE_PREPARE,
            TMMSounds.ITEM_LOCKPICK_DOOR,
            TMMSounds.ITEM_CROWBAR_PRY,
            TMMSounds.ITEM_BAT_HIT,
            TMMSounds.ITEM_GRENADE_THROW);

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.tickCount % 20 == 0) {
                    tickPhantasmagoria(player);
                }
            }
        });
    }

    private static void tickPhantasmagoria(ServerPlayer player) {
        Level world = player.level();
        WorldModifierComponent modifierComp = WorldModifierComponent.KEY.get(world);
        if (modifierComp == null)
            return;
        // 忽略处于旁观者模式的玩家，不应对旁观者生效
        if (player.isSpectator())
            return;

        if (!modifierComp.isModifier(player.getUUID(), SEModifiers.PARANOID)) {
            return;
        }

        RandomSource random = player.getRandom();
        // 约 1/15 的概率触发
        if (random.nextInt(15) == 0) {
            playFakeSound(player, random);
        }
    }

    private static void playFakeSound(ServerPlayer player, RandomSource random) {
        SoundEvent sound = SPOOKY_SOUNDS.get(random.nextInt(SPOOKY_SOUNDS.size()));

        double offsetX = (random.nextBoolean() ? 1.0D : -1.0D) * (3.0D + random.nextDouble() * 4.0D);
        double offsetZ = (random.nextBoolean() ? 1.0D : -1.0D) * (3.0D + random.nextDouble() * 4.0D);
        double offsetY = random.nextDouble() * 2.0D - 1.0D;

        @SuppressWarnings("unused")
        Vec3 pos = player.position().add(offsetX, offsetY, offsetZ);

        float volume = 1.0f;
        float pitch = 0.8f + (float) (random.nextDouble() * 0.4D);

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.playNotifySound(sound, SoundSource.PLAYERS, volume, pitch);
        }
    }
}
