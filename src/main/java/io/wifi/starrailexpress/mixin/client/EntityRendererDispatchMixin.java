package io.wifi.starrailexpress.mixin.client;

import com.google.common.collect.ImmutableMap;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.render.entity.PlayerBodyEntityRenderer;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.SRE;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;

@SuppressWarnings("unchecked")
@Mixin(EntityRenderDispatcher.class)
public class EntityRendererDispatchMixin {
    @Unique
    private static final Map<PlayerSkin.Model, EntityRendererProvider<PlayerBodyEntity>> PLAYER_BODY_RENDERER_FACTORIES = Map
            .of(
                    PlayerSkin.Model.WIDE,
                    context -> new PlayerBodyEntityRenderer<>(context, false),
                    PlayerSkin.Model.SLIM,
                    context -> new PlayerBodyEntityRenderer<>(context, true));

    @Unique
    private Map<PlayerSkin.Model, EntityRenderer<? extends PlayerBodyEntity>> bodyModelRenderers = Map.of();

    @Inject(method = "renderHitbox", at = @At("HEAD"), cancellable = true)
    private static void disableF3B(PoseStack poseStack, VertexConsumer vertexConsumer, Entity entity, float f, float g,
            float h, float i, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        if (Minecraft.getInstance() == null)
            return;
        if (Minecraft.getInstance().player == null)
            return;
        if (Minecraft.getInstance().player.isCreative())
            return;
        ci.cancel();
    }

    @Inject(method = "onResourceManagerReload", at = @At("TAIL"))
    public void reload(ResourceManager manager, CallbackInfo ci, @Local EntityRendererProvider.Context context) {
        this.bodyModelRenderers = reloadPlayerBodyRenderers(context);
    }

    @Inject(method = "getRenderer", at = @At("HEAD"), cancellable = true)
    public <T extends Entity> void tmm$addPlayerBodyRenderer(T entity,
            CallbackInfoReturnable<EntityRenderer<? super T>> cir) {
        if (entity instanceof PlayerBodyEntity body) {
            PlayerInfo playerListEntry = SREClient.PLAYER_ENTRIES_CACHE.get(body.getPlayerUuid());
            if (playerListEntry == null) {
                cir.setReturnValue((EntityRenderer<? super T>) this.bodyModelRenderers.get(PlayerSkin.Model.WIDE));
            } else {
                PlayerSkin.Model model = playerListEntry.getSkin().model();
                EntityRenderer<? extends PlayerBodyEntity> entityRenderer = this.bodyModelRenderers.get(model);
                cir.setReturnValue((EntityRenderer<? super T>) (entityRenderer != null ? entityRenderer
                        : (EntityRenderer) this.bodyModelRenderers.get(PlayerSkin.Model.WIDE)));
            }
        }
    }

    @Unique
    private static Map<PlayerSkin.Model, EntityRenderer<? extends PlayerBodyEntity>> reloadPlayerBodyRenderers(
            EntityRendererProvider.Context ctx) {
        ImmutableMap.Builder<PlayerSkin.Model, EntityRenderer<? extends PlayerBodyEntity>> builder = ImmutableMap
                .builder();
        PLAYER_BODY_RENDERER_FACTORIES.forEach((model, factory) -> {
            try {
                builder.put(model, factory.create(ctx));
            } catch (Exception var5) {
                throw new IllegalArgumentException("Failed to create player body model for " + model, var5);
            }
        });
        return builder.build();
    }

}
