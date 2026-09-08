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

package org.agmas.noellesroles.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.effects.StatusAilmentItems;
import org.agmas.noellesroles.content.effects.StatusAilmentPolicy;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 手疾：holding a knife/gun makes the view tremble. Draw-in is the strongest beat.
 */
@Environment(EnvType.CLIENT)
public final class HandTremorClientHandle {
    private static final float DRAW_DECAY = 0.86f;
    private static ItemStack lastWeapon = ItemStack.EMPTY;
    private static float drawImpulse;

    private HandTremorClientHandle() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LocalPlayer player = client.player;
            if (player == null || client.isPaused() || !player.isAlive()) {
                lastWeapon = ItemStack.EMPTY;
                drawImpulse = 0f;
                return;
            }
            MobEffectInstance effect = player.getEffect(ModEffects.HAND_TREMOR);
            ItemStack held = player.getMainHandItem();
            boolean holdingWeapon = StatusAilmentItems.isWeapon(held)
                    || StatusAilmentItems.isWeapon(player.getOffhandItem());
            if (effect == null || !holdingWeapon) {
                lastWeapon = ItemStack.EMPTY;
                drawImpulse *= DRAW_DECAY;
                return;
            }
            int amplifier = effect.getAmplifier();
            ItemStack weapon = StatusAilmentItems.isWeapon(held) ? held : player.getOffhandItem();
            if (lastWeapon.isEmpty() || !ItemStack.isSameItemSameComponents(lastWeapon, weapon)) {
                drawImpulse = 1.0f;
            }
            lastWeapon = weapon.copy();
            float idle = StatusAilmentPolicy.handTremorIdleAmplitude(amplifier);
            float draw = StatusAilmentPolicy.handTremorDrawAmplitude(amplifier) * drawImpulse;
            drawImpulse *= DRAW_DECAY;
            float t = player.tickCount;
            float yawDrift = (Mth.sin(t * 0.37f) + 0.45f * Mth.sin(t * 0.71f + 1.1f)) * (idle + draw);
            float pitchDrift = (Mth.cos(t * 0.41f + 0.4f) + 0.4f * Mth.sin(t * 0.63f)) * (idle + draw) * 0.72f;
            player.turn(yawDrift / 0.15f, pitchDrift / 0.15f);
        });
    }
}
