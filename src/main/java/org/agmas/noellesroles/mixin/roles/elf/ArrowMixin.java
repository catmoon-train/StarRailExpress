package org.agmas.noellesroles.mixin.roles.elf;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameFunctions;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(AbstractArrow.class)
public class ArrowMixin {
    @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true)
    private void noellesroles$onHitEntity(EntityHitResult entityHitResult, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        if (entityHitResult.getEntity() instanceof ServerPlayer player) {
            AbstractArrow arrow = (AbstractArrow) (Object) this;
            if (arrow instanceof SpectralArrow){
                player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 20, 0, false, false, true));
                arrow.discard();
                ci.cancel();

                return;

            }
            if (arrow instanceof Arrow) {
                if (arrow.getOwner() instanceof ServerPlayer serverPlayer) {
                    if (GameWorldComponent.KEY.get(serverPlayer.serverLevel()).isRole(serverPlayer, ModRoles.ELF)) {
                        isHit = true;
                        GameFunctions.killPlayer(player, true, serverPlayer, SRE.id("arrow"));
                    }
                }
            }
        }
    }

    private static boolean isHit = false;

    @Inject(method = "onHitEntity", at = @At("TAIL"))
    private void noellesroles$onHitEntitTail(EntityHitResult entityHitResult, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        if (isHit) {
            AbstractArrow arrow = (AbstractArrow) (Object) this;
            arrow.discard();
            isHit = false;
        }
    }

    @Inject(method = "onHitEntity", at = @At("HEAD"))
    private void noellesroles$onHitPlayerBody(EntityHitResult entityHitResult, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        if (entityHitResult.getEntity() instanceof PlayerBodyEntity player) {
            AbstractArrow arrow = (AbstractArrow) (Object) this;
            arrow.discard();
        }
    }

    @Inject(method = "onHitBlock", at = @At("TAIL"))
    private void noellesroles$onHitBlock(BlockHitResult blockHitResult, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (arrow instanceof SpectralArrow arrow1) {
            if (arrow.getOwner() instanceof ServerPlayer serverPlayer) {
                if (GameWorldComponent.KEY.get(serverPlayer.serverLevel()).isRole(serverPlayer, ModRoles.ELF)) {
                    // 获取箭矢击中的位置
                    BlockPos hitPos = blockHitResult.getBlockPos();
                    // 获取附近玩家列表（例如半径为5格）
                    List<ServerPlayer> nearbyPlayers = serverPlayer.serverLevel().getEntitiesOfClass(ServerPlayer.class,
                            new AABB(hitPos).inflate(8));
                    // 输出附近玩家数量
                    serverPlayer.sendSystemMessage(
                            Component.translatable("message.arrow.near_by_players", nearbyPlayers.size())
                                    .withStyle(ChatFormatting.GREEN),
                            true);
                    arrow1.discard();
                }
            }
        } else {
            arrow.discard();
        }
    }
}
