/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package io.wifi.starrailexpress.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.client.gui.RoundTextRenderer;
import io.wifi.starrailexpress.client.gui.screen.TrainDummyScreen;
import io.wifi.starrailexpress.client.model.TMMModelLayers;
import io.wifi.starrailexpress.traindummy.TrainDummyEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 列车假人使用玩家模型和正版皮肤，头顶按命中状态画多行翻译文本。
 */
public class TrainDummyRenderer extends LivingEntityRenderer<TrainDummyEntity, PlayerModel<TrainDummyEntity>> {

    private static final Map<String, CachedSkin> SKINS = new HashMap<>();

    public TrainDummyRenderer(EntityRendererProvider.Context context, boolean slim) {
        super(context, new PlayerModel<>(context.bakeLayer(
                slim ? TMMModelLayers.PLAYER_BODY_SLIM : TMMModelLayers.PLAYER_BODY), slim), 0.5F);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(TrainDummyEntity entity) {
        CachedSkin skin = skin(entity);
        return skin == null ? DefaultPlayerSkin.getDefaultTexture() : skin.texture;
    }

    public static boolean slim(TrainDummyEntity entity) {
        CachedSkin skin = skin(entity);
        return skin != null ? skin.slim : entity.slim();
    }

    private static CachedSkin skin(TrainDummyEntity entity) {
        String name = entity.skinName();
        if (name == null || name.isBlank()) {
            return null;
        }
        long tick = entity.level().getGameTime();
        CachedSkin cached = SKINS.get(name);
        if (cached != null && tick - cached.tick < 80L) {
            return cached;
        }
        try {
            PlayerSkin loaded = RoundTextRenderer.getSkinTextures(name);
            if (loaded != null) {
                CachedSkin next = new CachedSkin(loaded.texture(), loaded.model() == PlayerSkin.Model.SLIM, tick);
                SKINS.put(name, next);
                return next;
            }
        } catch (Exception ignored) {
        }
        return cached;
    }

    @Override
    public void render(TrainDummyEntity entity, float yaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        if (entity.isInvisible()) {
            return;
        }
        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
        List<Component> lines = statusLines(entity);
        if (lines.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.0D, entity.getBbHeight() + 0.55D, 0.0D);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);
        int y = -(lines.size() + 1) * 10;
        for (Component line : lines) {
            float x = -this.getFont().width(line) / 2.0F;
            this.getFont().drawInBatch(line, x, y, 0xFFFFFFFF, false, poseStack.last().pose(), buffer,
                    net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, 0x40000000, packedLight);
            y += 10;
        }
        poseStack.popPose();
    }

    private static List<Component> statusLines(TrainDummyEntity entity) {
        List<Component> lines = new ArrayList<>();
        if (entity.hitFlash() > 0) {
            lines.add(Component.translatable("entity.starrailexpress.train_dummy.hit"));
        }
        if (entity.virtualHp() <= 0 && entity.deathReasonId() != null && !entity.deathReasonId().isBlank()) {
            lines.add(Component.translatable("entity.starrailexpress.train_dummy.death",
                    TrainDummyScreen.reasonName(entity.deathReasonId())));
        }
        if (entity.hitFlash() > 0 && entity.distanceTenths() >= 0) {
            lines.add(Component.translatable("entity.starrailexpress.train_dummy.distance",
                    String.format(java.util.Locale.ROOT, "%.1f", entity.distanceTenths() / 10.0D)));
        }
        if (entity.shieldBrokenFlash() > 0) {
            lines.add(Component.translatable("entity.starrailexpress.train_dummy.shield_broken"));
        }
        if (entity.poisonTicks() > 0 && !entity.fakePoison()) {
            lines.add(Component.translatable("entity.starrailexpress.train_dummy.poison", entity.poisonTicks()));
        }
        if (entity.poisonTicks() > 0 && entity.fakePoison()) {
            lines.add(Component.translatable("entity.starrailexpress.train_dummy.fake_poison", entity.poisonTicks()));
        }
        if (entity.shieldLayers() > 0) {
            lines.add(Component.translatable("entity.starrailexpress.train_dummy.shield_layers", entity.shieldLayers()));
        }
        if (entity.timedLayers() > 0) {
            lines.add(Component.translatable("entity.starrailexpress.train_dummy.timed_shield",
                    entity.timedLayers(), entity.timedTicks()));
        }
        if (entity.weakLayers() > 0) {
            lines.add(Component.translatable("entity.starrailexpress.train_dummy.weak_shield",
                    entity.weakLayers(), entity.weakTicks(), weakReasonText(entity.weakReasons())));
        }
        return lines;
    }

    private static Component weakReasonText(String raw) {
        if (raw == null || raw.isBlank()) {
            return Component.translatable("screen.starrailexpress.train_dummy.weak_reason.none");
        }
        Component text = Component.empty();
        boolean first = true;
        for (String part : raw.split(",")) {
            if (part.isBlank()) {
                continue;
            }
            if (!first) {
                text = text.copy().append(Component.literal(", "));
            }
            text = text.copy().append(TrainDummyScreen.reasonName(part.trim()));
            first = false;
        }
        return text;
    }

    @Override
    protected boolean shouldShowName(TrainDummyEntity entity) {
        return !entity.isInvisible() && Minecraft.getInstance().player != null
                && entity.distanceToSqr(Minecraft.getInstance().player) < Mth.square(64);
    }

    private record CachedSkin(ResourceLocation texture, boolean slim, long tick) {
    }
}
