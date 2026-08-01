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

package pro.fazeclan.river.stupid_express.mixin.modifier.loose_end;

import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.network.RemoveStatusBarPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameUtils.class)
public class LooseEndBarClearMixin {
    @Inject(method = "stopGame", at = @At("HEAD"))
    private static void stopGame(ServerLevel world, CallbackInfo ci){
        world.players().forEach(serverPlayer -> {
            RemoveStatusBarPayload payload = new RemoveStatusBarPayload("loose_end");
            ServerPlayNetworking.send(serverPlayer, payload);
        });
    }
}
