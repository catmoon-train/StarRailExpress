package org.agmas.noellesroles.mixin.client.roles.insane;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.index.TMMEntities;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.component.InsaneKillerPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.agmas.noellesroles.component.InsaneKillerPlayerComponent.isPlayerBodyEntity;
import static org.agmas.noellesroles.component.InsaneKillerPlayerComponent.playerBodyEntities;

@Mixin(PlayerRenderer.class)
public abstract class InsaneKillerRenderMixin
        extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public InsaneKillerRenderMixin(EntityRendererProvider.Context context,
            PlayerModel<AbstractClientPlayer> entityModel, float f) {
        super(context, entityModel, f);
    }


    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    protected void setupRotations(AbstractClientPlayer abstractClientPlayer, float f, float g, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, CallbackInfo ci) {
        if (abstractClientPlayer.isSpectator())
            return;
        InsaneKillerPlayerComponent component = InsaneKillerPlayerComponent.KEY.get(abstractClientPlayer);
        ClientLevel clientLevel = abstractClientPlayer.clientLevel;
        if (component.isActive) {
            ci.cancel();
            isPlayerBodyEntity.put(abstractClientPlayer.getUUID(), true);
            if (!playerBodyEntities.containsKey(abstractClientPlayer.getUUID())){
//                if (abstractClientPlayer.getUUID() == Minecraft.getInstance().player.getUUID()){
//                    Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);
//                }
                final var body = new PlayerBodyEntity(TMMEntities.PLAYER_BODY, abstractClientPlayer.level());
                body.setPlayerUuid(abstractClientPlayer.getUUID());
                Vec3 spawnPos = abstractClientPlayer.position().add(abstractClientPlayer.getLookAngle().normalize().scale(1));
                body.moveTo(spawnPos.x(), abstractClientPlayer.getY(), spawnPos.z(), 0, 0f);
//                body.setYRot(abstractClientPlayer.getYHeadRot());
//                body.setYHeadRot(abstractClientPlayer.getYHeadRot());
                clientLevel.addEntity(body);
                playerBodyEntities.put(abstractClientPlayer.getUUID(), body);
            }
            final var playerBodyEntity = playerBodyEntities.get(abstractClientPlayer.getUUID());

            Entity entity = clientLevel.getEntity(playerBodyEntity.getId());
            if (entity!=null) {
                entity.setPos(abstractClientPlayer.getX(), abstractClientPlayer.getY(), abstractClientPlayer.getZ());
                entity.setYHeadRot(0);
//                entity.setXRot(0);
            }
//            playerBodyEntity.moveTo(abstractClientPlayer.getX(), abstractClientPlayer.getY(), abstractClientPlayer.getZ());
//            Minecraft.getInstance().getEntityRenderDispatcher().render(playerBodyEntity, 0.0D, 0.0D, 0, f, g, poseStack, multiBufferSource, i);
            // 模拟尸体渲染：绕 Z 轴旋转 90 度，并下移到地面
//            poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
//            poseStack.translate(0.0F, -0.85F, 0.0F);
        }else {
            if (isPlayerBodyEntity.getOrDefault(abstractClientPlayer.getUUID(), false)) {
//                if (abstractClientPlayer == Minecraft.getInstance().player) {
//                    Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
//                }
                isPlayerBodyEntity.put(abstractClientPlayer.getUUID(), false);
                if (playerBodyEntities.containsKey(abstractClientPlayer.getUUID())) {
                    clientLevel.removeEntity(playerBodyEntities.get(abstractClientPlayer.getUUID()).getId(), Entity.RemovalReason.DISCARDED);
                    playerBodyEntities.remove(abstractClientPlayer.getUUID());

                }
            }
        }
    }
}