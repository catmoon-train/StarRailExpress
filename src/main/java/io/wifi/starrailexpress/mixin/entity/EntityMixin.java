package io.wifi.starrailexpress.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.cca.PlayerAFKComponent;
import io.wifi.starrailexpress.SRE;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {
    @Shadow
    private Level level;
    @Inject(
            method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;vibrationAndSoundEffectsFromBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;ZZLnet/minecraft/world/phys/Vec3;)Z",ordinal = 0)
    )
    public void moving(MoverType p_19973_, Vec3 p_19974_, CallbackInfo ci){
        Entity self = (Entity) (Object)this;
        if (self instanceof ServerPlayer serverPlayer){
            // 更新该玩家的最后移动时间
            PlayerAFKComponent.KEY.maybeGet(serverPlayer).ifPresent(PlayerAFKComponent::updateActivity);

        }
    }
    @WrapMethod(method = "canCollideWith")
    protected boolean tmm$solid(Entity other, Operation<Boolean> original) {
        final var gameWorldComponent = StarGameWorldComponent.KEY.get(this.level);
        if (gameWorldComponent.isRunning()) {
            Entity self = (Entity) (Object) this;
            if (SRE.canCollideEntity.stream().anyMatch(p -> p.test(self) || p.test( other))){
                return true;
            }

            if (self instanceof Player && other instanceof Player) {
//                final var role = gameWorldComponent.getRole((Player) self);
//                final var role1 = gameWorldComponent.getRole((Player) other);
                return SRE.canCollide.stream().noneMatch(p -> p.test((Player) self) || p.test((Player) other));
            }
        }
        return original.call(other);
    }
}