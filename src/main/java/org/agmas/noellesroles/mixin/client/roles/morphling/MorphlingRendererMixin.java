package org.agmas.noellesroles.mixin.client.roles.morphling;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.client.SREClient;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.roles.morphling.MorphlingPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SkinSplitPersonalityComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;

import java.util.UUID;


@Mixin(PlayerRenderer.class)
public abstract class MorphlingRendererMixin {

    @Shadow public abstract ResourceLocation getTextureLocation(AbstractClientPlayer abstractClientPlayerEntity);
    

    
    @Unique
    private static final ThreadLocal<Boolean> isInMorphingCall = ThreadLocal.withInitial(() -> false);

    @Unique
    private static UUID getShuffledTarget(AbstractClientPlayer player) {
        final var level = player.level();
        if (level == null) {
            return null;
        }
        var worldModifiers = WorldModifierComponent.KEY.get(level);
        if (worldModifiers != null && worldModifiers.isModifier(player, SEModifiers.JEB_)) {
            return NoellesrolesClient.JEB_SHUFFLED_PLAYER_ENTRIES_CACHE.get(player.getUUID());
        }
        if (SREClient.moodComponent == null) {
            return null;
        }
        if (!NoellesrolesClient.SHUFFLED_PLAYER_ENTRIES_CACHE.containsKey(player.getUUID())) {
            return null;
        }
        if ((ConfigWorldComponent.KEY.get(level)).insaneSeesMorphs && SREClient.moodComponent.isLowerThanDepressed()) {
            return NoellesrolesClient.SHUFFLED_PLAYER_ENTRIES_CACHE.get(player.getUUID());
        }
        return null;
    }

    @Inject(method = "getTextureLocation(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/resources/ResourceLocation;", at = @At("HEAD"), cancellable = true)
    void renderMorphlingSkin(AbstractClientPlayer abstractClientPlayerEntity, CallbackInfoReturnable<ResourceLocation> cir) {
        // 防止递归调用
        if (isInMorphingCall.get()) {
            return;
        }
        
        try {
            isInMorphingCall.set(true);

            final var level = abstractClientPlayerEntity.level();
            if (level == null)return;
            final var shuffledTarget = getShuffledTarget(abstractClientPlayerEntity);
            if (shuffledTarget != null) {
                final var playerInfo = SREClient.PLAYER_ENTRIES_CACHE.get(shuffledTarget);
                if (playerInfo == null)
                    return;
                final var skin = playerInfo.getSkin();
                if (skin == null)
                    return;
                final var texture = skin.texture();
                cir.setReturnValue(texture);
                cir.cancel();
                return;
            }
            // 检查双重人格组件 - 如果玩家不是活跃人格，则显示主人格的皮肤

            var splitPersonalityComponent = SkinSplitPersonalityComponent.KEY.get(abstractClientPlayerEntity);
            if (splitPersonalityComponent != null) {
                final var skinToAppearAs = splitPersonalityComponent.getSkinToAppearAs();
                if (skinToAppearAs !=null) {

                        final var playerInfo = SREClient.PLAYER_ENTRIES_CACHE.get(skinToAppearAs);
                        if (playerInfo == null) {
                            return;
                        }
                        final var skin = playerInfo.getSkin();
                        if (skin == null) return;
                        final var texture = skin.texture();
                        cir.setReturnValue(texture);
                        cir.cancel();

                }
            }

            
            final var morphlingPlayerComponent = MorphlingPlayerComponent.KEY.get(abstractClientPlayerEntity);
            if (morphlingPlayerComponent != null && morphlingPlayerComponent.getMorphTicks() > 0 ) {
                final var disguise = (MorphlingPlayerComponent.KEY.get(abstractClientPlayerEntity)).disguise;
                final var playerInfo = SREClient.PLAYER_ENTRIES_CACHE.get(disguise);
                if (playerInfo==null)return;
                final var skin = playerInfo.getSkin();
                if (skin==null)return;
                final var texture = skin.texture();
                if (texture != null) {
                    cir.setReturnValue(texture);
                    cir.cancel();
                }
//                if (Minecraft.getInstance().player != null && disguise != null && disguise.equals(Minecraft.getInstance().player.getUUID())) {
//                    if (Minecraft.getInstance().player != abstractClientPlayerEntity) { // 防止自己伪装成自己导致递归
//                        cir.setReturnValue(getTextureLocation(Minecraft.getInstance().player));
//                        cir.cancel();
//                    }
//                }
                        return;
                    }


        } finally {
            isInMorphingCall.set(false);
        }
    }
    
    @WrapOperation(method = "renderHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getSkin()Lnet/minecraft/client/resources/PlayerSkin;"))
    PlayerSkin renderArm(AbstractClientPlayer instance, Operation<PlayerSkin> original) {
        // 检查双重人格组件 - 如果玩家不是活跃人格，则返回主人格的皮肤
        try {
            var splitPersonalityComponent = SplitPersonalityComponent.KEY.get(instance);
            final var level = instance.level();
            if (level == null) return original.call(instance);
            if (splitPersonalityComponent != null && !splitPersonalityComponent.isCurrentlyActive()) {
                UUID mainPersonalityId = splitPersonalityComponent.getMainPersonality();
                if (mainPersonalityId != null) {
                    final var playerInfo = SREClient.PLAYER_ENTRIES_CACHE.get((mainPersonalityId));
                    if (playerInfo == null) {
                        return original.call(instance);
                    }
                    final var skin = playerInfo.getSkin();
                    if (skin == null) return original.call(instance);
                    return skin;
                }
            }

            var component = MorphlingPlayerComponent.KEY.get(instance);
            if (component != null && component.getMorphTicks() > 0 && component.disguise != null) {
                final var playerInfo = SREClient.PLAYER_ENTRIES_CACHE.get((component.disguise));
                if (playerInfo != null) {
                    final var skin = playerInfo.getSkin();
                    if (skin != null) {
                        return skin;
                    }
                } else {
                    Log.info(LogCategory.GENERAL, "Morphling disguise is null!!!");
                }
            }

            final var shuffledTarget = getShuffledTarget(instance);
            if (shuffledTarget != null) {
                var playerInfo = SREClient.PLAYER_ENTRIES_CACHE.get(shuffledTarget);
                if (playerInfo != null) {
                    final var skin = playerInfo.getSkin();
                    if (skin != null) {
                        return skin;
                    }
                }
            }
        } catch (Exception e) {
            Noellesroles.LOGGER.error("Error in renderArm", e);
        }
        return original.call(instance);

    }



}
