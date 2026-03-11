package org.agmas.noellesroles.mixin.client.roles.morphling;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.client.SREClient;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.roles.morphling.MorphlingPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SkinSplitPersonalityComponent;

import java.util.UUID;


@Mixin(CapeLayer.class)
public abstract class MorphlingCapeRendererMixin {

    @Shadow public abstract void render(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, AbstractClientPlayer abstractClientPlayer, float f, float g, float h, float j, float k, float l);

    private static AbstractClientPlayer abstractClientPlayerEntity;
    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
            at = @At("HEAD")
    )
    void renderMorphlingSkin(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, AbstractClientPlayer abstractClientPlayer, float f, float g, float h, float j, float k, float l, CallbackInfo ci) {
        abstractClientPlayerEntity = abstractClientPlayer;
    }
    
    @Unique
    private static final ThreadLocal<Boolean> isInMorphingCall = ThreadLocal.withInitial(() -> false);

    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/PlayerSkin;capeTexture()Lnet/minecraft/resources/ResourceLocation;"))
    ResourceLocation renderMorphlingSkin(PlayerSkin instance) {
        // 防止递归调用
        if (isInMorphingCall.get()) {
            return instance.capeTexture();
        }
        
        try {
            isInMorphingCall.set(true);
            
            if (SREClient.moodComponent != null) {
                if ((ConfigWorldComponent.KEY.get(abstractClientPlayerEntity.level())).insaneSeesMorphs && SREClient.moodComponent.isLowerThanDepressed() && NoellesrolesClient.SHUFFLED_PLAYER_ENTRIES_CACHE.containsKey(abstractClientPlayerEntity.getUUID())) {
                    final var playerInfo = SREClient.PLAYER_ENTRIES_CACHE.get(NoellesrolesClient.SHUFFLED_PLAYER_ENTRIES_CACHE.get(abstractClientPlayerEntity.getUUID()));
                    if (playerInfo==null) return instance.capeTexture();
                    final var skin = playerInfo.getSkin();
                    if (skin==null) return instance.capeTexture();
                    final var texture = skin.capeTexture();
                    return texture;
                }

            }
            // 检查双重人格组件 - 如果玩家不是活跃人格，则显示主人格的斗篷
            var skinSplitPersonalityComponent = SkinSplitPersonalityComponent.KEY.get(abstractClientPlayerEntity);
            if (skinSplitPersonalityComponent != null) {
                final var skinToAppearAs = skinSplitPersonalityComponent.getSkinToAppearAs();
                if (skinToAppearAs !=null) {

                        final var playerInfo = SREClient.PLAYER_ENTRIES_CACHE.get(skinToAppearAs);
                        if (playerInfo == null) {
                            return instance.capeTexture();
                        }
                        final var skin = playerInfo.getSkin();
                        if (skin == null) {
                            return instance.capeTexture();
                        }
                        final var texture = skin.capeTexture();
                        return texture;
                    }

            }
            

            final var morphlingPlayerComponent = MorphlingPlayerComponent.KEY.get(abstractClientPlayerEntity);
            if (morphlingPlayerComponent != null && morphlingPlayerComponent.getMorphTicks() > 0) {
                UUID disguiseUuid = MorphlingPlayerComponent.KEY.get(abstractClientPlayerEntity).disguise;
                if (disguiseUuid != null) {
                    // 尝试通过 TMMClient 获取玩家信息，而不是直接获取世界中的玩家
                    final var playerInfo = SREClient.PLAYER_ENTRIES_CACHE.get(disguiseUuid);
                    if (playerInfo != null) {
                        final var skin = playerInfo.getSkin();
                        if (skin != null) {
                            final var texture = skin.capeTexture();
                            if (texture != null) {
                                return texture;
                            }
                        }
                    }
                    
                    // 如果 TMMClient 中没有找到玩家信息，检查是否伪装成本地玩家
                    if (Minecraft.getInstance().player != null && disguiseUuid.equals(Minecraft.getInstance().player.getUUID())) {
                        if (Minecraft.getInstance().player != abstractClientPlayerEntity) { // 防止自己伪装成自己导致递归
                            return Minecraft.getInstance().player.getSkin().capeTexture();
                        }
                    }
                    
                    Log.info(LogCategory.GENERAL, "Morphling disguise player info not found in cache: " + disguiseUuid.toString());
                }
                return instance.capeTexture();
            }


        } finally {
            isInMorphingCall.set(false);
        }
        return instance.capeTexture();
    }
    

}
