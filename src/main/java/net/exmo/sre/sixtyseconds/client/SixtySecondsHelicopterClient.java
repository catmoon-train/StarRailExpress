package net.exmo.sre.sixtyseconds.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.network.SixtySecondsHelicopterS2CPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 客户端直升机撤离状态 + 渲染：接收 {@link SixtySecondsHelicopterS2CPacket}，
 * 在撤离区渲染绿色混凝土标记框 + HUD 显示撤离进度。
 */
@Environment(EnvType.CLIENT)
public final class SixtySecondsHelicopterClient {

    /** 绿色混凝土标记 ARGB（Minecraft 石灰混凝土 #5F9A3F 近似值）。 */
    private static final int GREEN_CONCRETE = 0xFF5F9A3F;
    /** 绿色半透明（用于 HUD 文本和地图标记）。 */
    private static final int GREEN_TEXT = 0xFF55FF55;

    private static boolean active = false;
    private static BlockPos landingPos = BlockPos.ZERO;
    private static int evacRadius = 8;
    private static int totalSeconds = 0;
    private static int remainingSeconds = 0;

    private SixtySecondsHelicopterClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SixtySecondsHelicopterS2CPacket.ID, (payload, context) ->
                context.client().execute(() -> {
                    active = payload.active();
                    if (active) {
                        landingPos = new BlockPos(payload.x(), payload.y(), payload.z());
                        evacRadius = payload.evacRadius();
                        totalSeconds = payload.totalSeconds();
                        remainingSeconds = payload.remainingSeconds();
                    }
                }));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            active = false;
            landingPos = BlockPos.ZERO;
        });

        // HUD 渲染
        HudRenderCallback.EVENT.register((graphics, delta) ->
                renderHud(graphics, Minecraft.getInstance().font,
                        graphics.guiWidth(), graphics.guiHeight()));

        // 世界渲染（绿色框）
        WorldRenderEvents.AFTER_TRANSLUCENT.register(SixtySecondsHelicopterClient::renderWorld);
    }

    /** 世界渲染：在撤离区画绿色线框。挂在 WorldRenderEvents.AFTER_TRANSLUCENT。 */
    public static void renderWorld(WorldRenderContext context) {
        if (!active || landingPos.equals(BlockPos.ZERO)) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !isSixtySeconds(client)) return;

        Vec3 cam = context.camera().getPosition();
        AABB box = new AABB(landingPos).inflate(evacRadius);

        // 调整 box 的 y 范围让它在地面
        double y = landingPos.getY();
        box = new AABB(
                box.minX, y - 0.5, box.minZ,
                box.maxX, y + 0.5, box.maxZ);

        PoseStack matrices = context.matrixStack();
        matrices.pushPose();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        LevelRenderer.renderLineBox(matrices, context.consumers().getBuffer(
                net.minecraft.client.renderer.RenderType.lines()), box,
                ((GREEN_CONCRETE >> 16) & 0xFF) / 255F,
                ((GREEN_CONCRETE >> 8) & 0xFF) / 255F,
                (GREEN_CONCRETE & 0xFF) / 255F,
                0.85F);

        matrices.popPose();
    }

    /** HUD 渲染：屏幕中央上方显示撤离倒计时。 */
    public static void renderHud(GuiGraphics g, Font font, int screenW, int screenH) {
        if (!active) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !isSixtySeconds(client)) return;

        int min = remainingSeconds / 60;
        int sec = remainingSeconds % 60;
        String timeStr = String.format("%d:%02d", min, sec);
        int timeColor = remainingSeconds <= 30 ? 0xFFFF5555 : 0xFF55FF55;

        String line1 = "§e§l🚁 "
                + Component.translatable("hud.noellesroles.sixty_seconds.helicopter_title").getString()
                + " §r§e🚁";
        String line2 = "§7"
                + Component.translatable("hud.noellesroles.sixty_seconds.helicopter_pos",
                        landingPos.getX(), landingPos.getZ()).getString();
        String line3 = "§a§l" + timeStr + " §7"
                + Component.translatable("hud.noellesroles.sixty_seconds.helicopter_countdown").getString();

        int y = screenH / 2 - 50;
        g.drawCenteredString(font, line1, screenW / 2, y, 0xFFAA00);
        g.drawCenteredString(font, line2, screenW / 2, y + 12, 0xFFAAAAAA);
        g.drawCenteredString(font, line3, screenW / 2, y + 24, timeColor);
    }

    private static boolean isSixtySeconds(Minecraft client) {
        return io.wifi.starrailexpress.client.SREClient.gameComponent != null
                && io.wifi.starrailexpress.client.SREClient.gameComponent.isRunning()
                && SixtySecondsMod.MODE != null
                && io.wifi.starrailexpress.client.SREClient.gameComponent.getGameMode() == SixtySecondsMod.MODE;
    }
}
