package pro.fazeclan.river.stupid_express.mixin.modifier.taskmaster;

import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

@Mixin(io.wifi.starrailexpress.api.RoleMethodDispatcher.class)
public abstract class TaskmasterGiveCoinsMixin {

    @Inject(method = "callOnFinishQuest(Lnet/minecraft/world/entity/player/Player;Ljava/lang/String;IZ)V", at = @At("TAIL"))
    private static void taskmasterGiveCoins(Player player, String quest, int taskStreak, boolean isParallelTask, CallbackInfo ci) {
        // Give additional coins to taskmaster when completing a task
        WorldModifierComponent modifier = WorldModifierComponent.KEY.get(player.level());
        if (modifier.isModifier(player, SEModifiers.TASKMASTER)) {
            SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(player);
            shopComponent.addToBalance(25);
        }
    }
}
