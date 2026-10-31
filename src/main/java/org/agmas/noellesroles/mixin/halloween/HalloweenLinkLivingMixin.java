package org.agmas.noellesroles.mixin.halloween;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.agmas.noellesroles.game.halloween.HalloweenLinkItems;
import org.spongepowered.asm.mixin.Mixin;

/** 食物、药水等持续使用完成时，空瓶/碗等结果也在同一使用事务内回写。 */
@Mixin(LivingEntity.class)
public abstract class HalloweenLinkLivingMixin {
    @WrapMethod(method = "completeUsingItem")
    private void halloween$linkFinish(Operation<Void> original) {
        if ((Object) this instanceof ServerPlayer player) HalloweenLinkItems.use(player, () -> original.call());
        else original.call();
    }
}
