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

package pro.fazeclan.river.stupid_express.mixin.general;

import com.mojang.brigadier.context.CommandContext;

import io.wifi.starrailexpress.SRE;

import java.lang.reflect.Method;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.commands.StuckCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes /stuck concede an SRE-GAME match when that optional companion mod is
 * loaded.
 */
@Mixin(StuckCommand.class)
public abstract class StuckGameExitMixin {
   @Inject(method = "stuckDeal", at = @At("HEAD"), cancellable = true)
   private static void sreGame$forfeitCurrentGame(CommandContext<CommandSourceStack> context,
         CallbackInfoReturnable<Integer> cir) {
      try {
         ServerPlayer player = context.getSource().getPlayer();
         if (player == null) {
            return;
         }
         if (!SRE.isLobby) {
            return;
         }
         Class<?> sreGame = Class.forName("net.exmo.sreGame.SreGame");
         Method handler = sreGame.getMethod("handleStuck", ServerPlayer.class);
         if (Boolean.TRUE.equals(handler.invoke(null, player))) {
            cir.setReturnValue(1);
         }
      } catch (ReflectiveOperationException ignored) {
         // SRE-GAME is optional; retain the normal /stuck behavior when it is absent.
      }
   }
}
