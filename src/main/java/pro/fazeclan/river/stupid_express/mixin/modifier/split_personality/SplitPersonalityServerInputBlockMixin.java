package pro.fazeclan.river.stupid_express.mixin.modifier.split_personality;

// import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
// import net.minecraft.world.level.GameType;
// import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
// import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;

/**
 * 服务器端 - 禁止旁观者的移动
 * 确保旁观者在服务器端无法移动或交互
 */
@Mixin(Player.class)
public abstract class SplitPersonalityServerInputBlockMixin {

    @Inject(method = "aiStep()V", at = @At("HEAD"), cancellable = true)
    void blockServerSideSplitPersonalityInput(CallbackInfo ci) {
        // Player player = (Player) (Object) this;
        // if (player instanceof ServerPlayer serverPlayer) {
        //     var component = SplitPersonalityComponent.KEY.get(player);
        //     if (component.getTemporaryRevivalStartTick() > 0) {
        //         return;
        //     }

        //     // 如果是旁观者，清除所有移动
        //     // if (component != null && component.getMainPersonality() != null && !component.isCurrentlyActive()) {
        //     //     // 禁止移动
        //     //     if (!serverPlayer.isSpectator())
        //     //         serverPlayer.setGameMode(GameType.SPECTATOR);
        //     //     UUID targetPlayerUUID = component.getCurrentActivePerson();
        //     //     if (!serverPlayer.getCamera().getUUID().equals(targetPlayerUUID)) {
        //     //         Player targetplayer = serverPlayer.level().getPlayerByUUID(component.getCurrentActivePerson());
        //     //         serverPlayer.setCamera(targetplayer);
        //     //     }
        //     // }
        // }
    }
}