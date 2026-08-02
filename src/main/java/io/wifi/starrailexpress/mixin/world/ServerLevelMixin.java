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

package io.wifi.starrailexpress.mixin.world;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {
    @Inject(method = "tickTime", at = @At("HEAD"), require = 0, cancellable = true)
    private void tickTime(CallbackInfo ci) {
        SREGameTimeComponent cca = SREGameTimeComponent.KEY.getNullable((Object) this);
        if (cca != null) {
            if (cca.levelGameTimeFrozen) {
                ci.cancel();
            }
        }
    }
    // protected void tickTime() {
    // if (this.tickTime) {
    // long l = this.levelData.getGameTime() + 1L;
    // this.serverLevelData.setGameTime(l);
    // this.serverLevelData.getScheduledEvents().tick(this.server, l);
    // if (this.levelData.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
    // this.setDayTime(this.levelData.getDayTime() + 1L);
    // }

    // }
    // }
}
