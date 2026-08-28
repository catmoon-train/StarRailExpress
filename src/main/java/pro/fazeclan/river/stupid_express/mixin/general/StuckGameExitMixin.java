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
