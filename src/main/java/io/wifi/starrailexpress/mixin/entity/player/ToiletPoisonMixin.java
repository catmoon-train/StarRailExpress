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

import io.wifi.starrailexpress.util.Scheduler;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ToiletPoisonMixin {

    @Unique
    private Scheduler.ScheduledTask sre$poisonToiletTask = null;

    @Inject(method = "stopRiding", at = @At("HEAD"))
    public void sre$cancelToiletPoisonTask(CallbackInfo ci) {
        // 玩家离开座位时取消中毒任务
        if (this.sre$poisonToiletTask != null) {
            this.sre$poisonToiletTask.cancel();
            this.sre$poisonToiletTask = null;
        }
    }
}
