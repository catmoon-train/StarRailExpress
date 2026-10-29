package org.agmas.noellesroles.game.halloween;

import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.DoorBlock;
import org.agmas.noellesroles.game.halloween.village.*;
import org.agmas.noellesroles.game.halloween.village.client.*;

/** 只处理授权的本地视觉/声音；光照只改 lightmap 像素，世界方块完全不变。 */
@Environment(EnvType.CLIENT)
public final class HalloweenVillageClient {
    private static boolean registered, active;
    private static ClientLevel syncedLevel;
    private static long receivedAt;
    private static int phase, threshold = 5;
    private static Set<UUID> souls = Set.of();
    private static volatile float brightness = 1;
    private HalloweenVillageClient() {}

    public static void register() {
        if (registered) return;
        registered = true;
        EntityRendererRegistry.register(HalloweenVillage.NPC, VillageNpcRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(VillagePackets.Open.ID, (packet, context) -> context.client().execute(() -> {
            if (context.client().screen == null || context.client().screen instanceof VillageShopScreen) {
                context.client().setScreen(new VillageShopScreen(packet));
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(VillagePackets.Night.ID, (packet, context) -> context.client().execute(() -> {
            syncedLevel = context.client().level;
            active = packet.active(); phase = packet.phase(); threshold = Mth.clamp(packet.darkThreshold(), 0, 15);
            receivedAt = syncedLevel == null ? 0 : syncedLevel.getGameTime();
            souls = active ? Set.copyOf(packet.souls()) : Set.of();
            HalloweenVillage.setClientNight(syncedLevel, active);
            if (active) VillageGhostAudio.setAllowed(souls); else VillageGhostAudio.clear();
        }));
        ClientTickEvents.END_CLIENT_TICK.register(HalloweenVillageClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        WorldRenderEvents.AFTER_ENTITIES.register(HalloweenVillageClient::renderSouls);
        ItemTooltipCallback.EVENT.register((stack, context, flags, lines) -> {
            if (Minecraft.getInstance().level != null && HalloweenRuntime.isMapEnabled(Minecraft.getInstance().level)
                    && stack.getItem() instanceof BlockItem block && (block.getBlock() instanceof DoorBlock
                    || block.getBlock() instanceof io.wifi.starrailexpress.content.block.SmallDoorBlock)) {
                lines.add(Component.translatable("halloween.village.knock_hint").withStyle(ChatFormatting.DARK_PURPLE));
            }
        });
    }

    private static void tick(Minecraft client) {
        if (client.level != syncedLevel || client.level == null || client.player == null
                || client.player.isSpectator() || !client.player.isAlive()
                || !io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(client.level).isRunning()
                || client.level.getGameTime() - receivedAt > 60) {
            reset();
            return;
        }
        long elapsed = phase + client.level.getGameTime() - receivedAt;
        // 偶尔连续停灯约 2 秒，其余时间不规则暗闪；每次 lightmap 重算即恢复原色。
        long cycle = Math.floorMod(elapsed, 300);
        brightness = !active ? 1 : cycle >= 100 && cycle < 145 ? .045F
                : ((elapsed / 3) % 7 == 0 || (elapsed / 5) % 11 == 0) ? .28F : .88F;
    }

    private static void reset() {
        active = false; brightness = 1; souls = Set.of(); syncedLevel = null;
        HalloweenVillage.setClientNight(null, false);
        VillageGhostAudio.clear();
    }

    /** NativeImage 的 ABGR 像素按通道缩放，不触碰 alpha；普通局逐位保持原值。 */
    public static int shadeLightmap(int color) {
        float scale = brightness;
        if (scale >= 1) return color;
        int red = (int) ((color & 255) * scale);
        int green = (int) (((color >>> 8) & 255) * scale);
        int blue = (int) (((color >>> 16) & 255) * scale);
        return (color & 0xFF000000) | (blue << 16) | (green << 8) | red;
    }

    private static void renderSouls(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!active || client.level != syncedLevel || client.player == null || client.level == null
                || client.player.isSpectator() || client.level.getMaxLocalRawBrightness(client.player.blockPosition()) > threshold) return;
        var poseStack = context.matrixStack();
        if (poseStack == null) return;
        var camera = context.camera().getPosition();
        var buffer = client.renderBuffers().bufferSource();
        float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
        for (net.minecraft.world.entity.player.Player other : client.level.players()) {
            if (!(other instanceof AbstractClientPlayer soul) || !soul.isSpectator() || soul == client.player
                    || !souls.contains(soul.getUUID()) || soul.distanceToSqr(client.player) > 4096
                    || client.level.getMaxLocalRawBrightness(soul.blockPosition()) > threshold) continue;
            double x = Mth.lerp(partial, soul.xo, soul.getX()) - camera.x;
            double y = Mth.lerp(partial, soul.yo, soul.getY()) - camera.y + 1.6;
            double z = Mth.lerp(partial, soul.zo, soul.getZ()) - camera.z;
            poseStack.pushPose();
            poseStack.translate(x, y, z);
            poseStack.mulPose(client.getEntityRenderDispatcher().cameraOrientation());
            poseStack.scale(.55F, .55F, .55F);
            var vertex = buffer.getBuffer(RenderType.entityTranslucent(soul.getSkin().texture()));
            var pose = poseStack.last();
            vertex.addVertex(pose, -.5F, .5F, 0).setColor(170, 205, 255, 150).setUv(.125F, .125F)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0, 0, 1);
            vertex.addVertex(pose, -.5F, -.5F, 0).setColor(170, 205, 255, 150).setUv(.125F, .25F)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0, 0, 1);
            vertex.addVertex(pose, .5F, -.5F, 0).setColor(170, 205, 255, 150).setUv(.25F, .25F)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0, 0, 1);
            vertex.addVertex(pose, .5F, .5F, 0).setColor(170, 205, 255, 150).setUv(.25F, .125F)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0, 0, 1);
            poseStack.popPose();
        }
    }
}
