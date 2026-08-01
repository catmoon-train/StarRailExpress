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

package org.agmas.noellesroles.mixin.roles;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * 通用「无法中毒死亡」mixin，合并自原 PoisonerNoPoisonMixin 和 GlitchRobotNoPoisonMixin。
 * 通过 {@link SRERole#canBePoisoned()} 控制是否免疫中毒，取代硬编码的角色判断。
 */
@Mixin(SREPlayerPoisonComponent.class)
public abstract class RoleNoPoisonMixin {

    @Shadow
    private Player player;
    @Shadow
    public int poisonTicks;
    @Shadow
    public UUID poisoner;
    @Shadow
    public boolean fakePoison;

    @Inject(method = "setPoisonTicks", at = @At("HEAD"), cancellable = true)
    private void noPoisonSet(int ticks, UUID poisoner, CallbackInfo ci) {
        if (!canBePoisoned()) {
            ci.cancel();
        }
    }

    @Inject(method = "setFakePoisonTicks", at = @At("HEAD"), cancellable = true)
    private void noPoisonSetFake(int ticks, UUID poisoner, CallbackInfo ci) {
        if (!canBePoisoned()) {
            ci.cancel();
        }
    }

    @Inject(method = "serverTick", at = @At("HEAD"))
    private void noPoisonServerTick(CallbackInfo ci) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(this.player.level());
        if (gameWorld != null) {
            SRERole role = gameWorld.getRole(this.player);
            if (role != null && !role.canBePoisoned()) {
                if (this.poisonTicks > 0) {
                    this.poisonTicks = -1;
                    this.poisoner = null;
                    this.fakePoison = false;
                }
            }
        }
    }

    private boolean canBePoisoned() {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(this.player.level());
        if (gameWorld == null) {
            return true;
        }
        SRERole role = gameWorld.getRole(this.player);
        return role == null || role.canBePoisoned();
    }
}
