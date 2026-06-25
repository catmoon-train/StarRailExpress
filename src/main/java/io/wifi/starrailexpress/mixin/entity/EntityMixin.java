package io.wifi.starrailexpress.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.rules.CollisionRules;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerAFKComponent;
import io.wifi.starrailexpress.event.CanCollideWith;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {
    @Shadow
    private Level level;

    @Inject(method = "onExplosionHit", at = @At("HEAD"), cancellable = true)
    public void addHurtTagToPlayerWithExplosion(Entity direct, CallbackInfo ci) {
    }

    @Inject(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;vibrationAndSoundEffectsFromBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;ZZLnet/minecraft/world/phys/Vec3;)Z", ordinal = 0))
    public void moving(MoverType p_19973_, Vec3 p_19974_, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof ServerPlayer serverPlayer) {
            // 更新该玩家的最后移动时间
            SREPlayerAFKComponent.KEY.maybeGet(serverPlayer).ifPresent(SREPlayerAFKComponent::updateActivity);

        }
    }

    @WrapMethod(method = "canCollideWith")
    protected boolean tmm$solid(Entity other, Operation<Boolean> original) {
        if (SRE.isLobby)
            return original.call(other);
        final var gameWorldComponent = SREGameWorldComponent.KEY.get(this.level);
        if (gameWorldComponent.isRunning()) {
            Entity self = (Entity) (Object) this;
            TrueFalseResult result = CanCollideWith.ENTITY.invoker().allowCollideWith(self, other);
            if (result.equals(TrueFalseResult.FALSE)) {
                return false;
            } else if (result.equals(TrueFalseResult.TRUE)) {
                return true;
            }
            if (CollisionRules.canCollideEntity.stream().anyMatch(p -> p.test(self) || p.test(other))) {
                return true;
            }

            if (self instanceof Player sp) {
                TrueFalseResult result2 = CanCollideWith.PLAYER.invoker().allowCollideWith(sp, other);
                if (result2.equals(TrueFalseResult.FALSE)) {
                    return false;
                } else if (result2.equals(TrueFalseResult.TRUE)) {
                    return true;
                }
                if (other instanceof Player so) {
                    // final var role = gameWorldComponent.getRole((Player) self);
                    // final var role1 = gameWorldComponent.getRole((Player) other);
                    return CollisionRules.canCollide.stream().noneMatch(p -> p.test(sp) || p.test(so));
                }
            }
        }
        return original.call(other);
    }

    @Inject(method = "canSpawnSprintParticle", at = @At("HEAD"), cancellable = true)
    private void onSpawnSprintParticle(CallbackInfoReturnable<Boolean> ci) {
        if (SRE.isLobby)
            return;
        Entity self = (Entity) (Object) this;
        // 只针对玩家，且该玩家对本客户端不可见（隐身效果）
        if (self instanceof Player player && player.isInvisible()) {
            SREGameWorldComponent gwc = SREGameWorldComponent.KEY.get(player.level());
            if (gwc.isRole(player, ModRoles.WIND_YAOSE) || gwc.isRole(player, ModRoles.NOSTALGIST)) {
                ci.setReturnValue(false);
                ci.cancel();
            }
        }

    }

    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    private void onPlayStepSound(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        if ((Entity) (Object) this instanceof Player player && player.isInvisible()) {
            SREGameWorldComponent gwc = SREGameWorldComponent.KEY.get(player.level());
            if (gwc.isRole(player, ModRoles.WIND_YAOSE) || gwc.isRole(player, ModRoles.NOSTALGIST)) {
                ci.cancel();
            }
        }
    }
}