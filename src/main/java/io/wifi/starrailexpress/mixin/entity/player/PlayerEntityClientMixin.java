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

package io.wifi.starrailexpress.mixin.entity.player;

import io.wifi.starrailexpress.util.FlashlightInterface;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import org.agmas.noellesroles.client.FlashlightLightProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Player.class)
public abstract class PlayerEntityClientMixin extends LivingEntity implements FlashlightInterface {

    protected PlayerEntityClientMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Shadow
    public abstract float getAttackStrengthScale(float baseTime);

    @Override
    public FlashlightLightProvider getFlashlight() {
        return flashlightProvider;
    }

    @Override
    public void setFlashlight(FlashlightLightProvider status) {
        this.flashlightProvider = status;
    }

    @Unique
    public FlashlightLightProvider flashlightProvider;
}