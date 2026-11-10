package pro.fazeclan.river.stupid_express.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.wifi.starrailexpress.client.InputHandler;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import pro.fazeclan.river.stupid_express.constants.SEItems;
import pro.fazeclan.river.stupid_express.role.arsonist.ArsonistPackets;
import pro.fazeclan.river.stupid_express.role.arsonist.ArsonistRoleData;

import java.util.Locale;

/** 纵火犯的输入、外观和私有 HUD；不启动疯魔，也不在客户端决定伤害或消耗。 */
@Environment(EnvType.CLIENT)
public final class ArsonistClient {
    private static final String KEY = "hud.stupid_express.arsonist.";
    private static final int MOMENT_TICKS = 80 * 20;
    private static final int PANEL_WIDTH = 196;
    private static final int ORANGE = 0xFFFF982E;
    private static final int LIGHT = 0xFFFFD4A1;
    private static final int MUTED = 0xFFC5A88D;
    private static boolean registered;
    // 受击者独立收包，不依赖纵火犯实体是否在客户端的追踪距离内。
    private static int localHeat;
    private static int reloadSpanTicks;

    private ArsonistClient() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        ClientPlayNetworking.registerGlobalReceiver(ArsonistPackets.HeatS2C.ID,
                (payload, context) -> context.client().execute(() ->
                        localHeat = Mth.clamp(payload.heat(), 0, 100)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(ArsonistClient::tick);
        HudRenderCallback.EVENT.register((graphics, delta) ->
                renderHud(graphics, delta.getGameTimeDeltaPartialTick(false)));
        WorldRenderEvents.AFTER_ENTITIES.register(ArsonistClient::renderBurnValues);
    }

    private static void clear() {
        localHeat = 0;
        reloadSpanTicks = 0;
    }

    private static boolean isLiving(Player player) {
        return player != null && player.isAlive() && !player.isSpectator()
                && GameUtils.isPlayerAliveAndSurvival(player);
    }

    private static boolean inMatch(Minecraft client) {
        return client.level != null && SREClient.gameComponent != null && SREClient.gameComponent.isRunning();
    }

    private static void tick(Minecraft client) {
        if (!inMatch(client) || !isLiving(client.player)) {
            clear();
            return;
        }
        ArsonistRoleData data = ArsonistRoleData.of(client.player);
        if (data == null || !data.arsonMoment) {
            reloadSpanTicks = 0;
            return;
        }
        // 只记录本次装填首次看到的剩余时长，不额外要求服务端暴露装填总时长字段。
        reloadSpanTicks = data.reloadTicks > 0 ? Math.max(reloadSpanTicks, data.reloadTicks) : 0;
        if (client.player.getMainHandItem().is(SEItems.FLAMETHROWER)) {
            boolean requested = false;
            while (InputHandler.sniperReloadKeybind.consumeClick()) requested = true;
            // 打开界面期间仅清掉本武器的积压点击，绝不发送装填请求或抢走狙击枪按键。
            if (requested && client.screen == null) {
                ClientPlayNetworking.send(new ArsonistPackets.ReloadC2S());
            }
        }
    }

    private static void renderHud(GuiGraphics graphics, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui || !inMatch(client) || !isLiving(client.player)) return;
        ArsonistRoleData data = ArsonistRoleData.of(client.player);
        if (data != null) renderRolePanel(graphics, client, data, partialTick);
        if (localHeat > 0) {
            int width = Math.min(160, graphics.guiWidth() / 2 - 24);
            if (width < 24) return;
            int x = 8;
            int y = Math.max(8, graphics.guiHeight() - 82);
            panel(graphics, x, y, width, 34);
            label(graphics, client.font, text("heat", localHeat), x + 9, y + 7, width - 18, LIGHT);
            progress(graphics, x + 9, y + 22, width - 18, localHeat / 100.0F);
        }
    }

    private static void renderRolePanel(GuiGraphics graphics, Minecraft client,
                                       ArsonistRoleData data, float partialTick) {
        int height = data.arsonMoment ? 112 : 90;
        // 始终留在准星右侧，并避开底部快捷栏；小窗口整体缩放，而非遮住屏幕中央。
        float scale = Math.min(1.0F, Math.min((graphics.guiWidth() / 2.0F - 24) / PANEL_WIDTH,
                (graphics.guiHeight() - 64.0F) / height));
        if (scale <= 0) return;
        graphics.pose().pushPose();
        graphics.pose().translate(graphics.guiWidth() - 8 - PANEL_WIDTH * scale,
                graphics.guiHeight() - 48 - height * scale, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        panel(graphics, 0, 0, PANEL_WIDTH, height);
        Font font = client.font;
        int width = PANEL_WIDTH - 20;
        label(graphics, font, text(data.arsonMoment ? "moment" : "preparation"), 10, 8, width, ORANGE);
        if (data.arsonMoment) {
            int ticks = Mth.clamp(data.momentTicks, 0, MOMENT_TICKS);
            label(graphics, font, text("moment_time", (ticks + 19) / 20), 10, 25, width, LIGHT);
            progress(graphics, 10, 37, width, ticks / (float) MOMENT_TICKS);
            int fuel = Mth.clamp(data.fuelUnits, 0, 1000);
            label(graphics, font, text("fuel", decimal(fuel / 10.0F)), 10, 47, width, LIGHT);
            progress(graphics, 10, 59, width, fuel / 1000.0F);
            int reload = Math.max(0, data.reloadTicks);
            Component reloadText = reload > 0 ? text("reload_time", decimal(reload / 20.0F)) : text("reload_ready");
            label(graphics, font, reloadText, 10, 69, width, reload > 0 ? ORANGE : MUTED);
            float reloadProgress = reload > 0 ? 1.0F - reload / (float) Math.max(reload, reloadSpanTicks) : 1.0F;
            progress(graphics, 10, 81, width, reloadProgress);
            label(graphics, font, text("reload_hint", InputHandler.sniperReloadKeybind.getTranslatedKeyMessage()),
                    10, 96, width, MUTED);
        } else {
            int required = Math.max(1, data.ignitionRequired);
            int doused = Math.max(0, data.dousedCount);
            label(graphics, font, text("doused", doused, required, 80), 10, 25, width, LIGHT);
            progress(graphics, 10, 37, width, doused / (float) required);
            float gunCooldown = client.player.getCooldowns().getCooldownPercent(SEItems.FIRE_GUN, partialTick);
            label(graphics, font, text("fire_gun", 100, decimal(gunCooldown * 30), 30),
                    10, 47, width, MUTED);
            progress(graphics, 10, 59, width, 1.0F - gunCooldown);
            float oilCooldown = client.player.getCooldowns().getCooldownPercent(SEItems.LARGE_OIL_CAN, partialTick);
            label(graphics, font, text("oil_can", decimal(oilCooldown * 60), 60), 10, 69, width, MUTED);
            progress(graphics, 10, 81, width, 1.0F - oilCooldown);
        }
        graphics.pose().popPose();
    }

    private static Component text(String suffix, Object... args) {
        return Component.translatable(KEY + suffix, args);
    }

    private static String decimal(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x + 2, y + 2, x + width + 2, y + height + 2, 0x60000000);
        graphics.fill(x, y, x + width, y + height, 0xFF58351B);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xE6100D0B);
        graphics.fill(x, y, x + width, y + 1, ORANGE);
        graphics.fill(x, y + 1, x + 2, y + height, 0xFFB55E1E);
    }

    private static void label(GuiGraphics graphics, Font font, Component text,
                              int x, int y, int maxWidth, int color) {
        float scale = Math.min(1.0F, maxWidth / (float) Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static void progress(GuiGraphics graphics, int x, int y, int width, float value) {
        graphics.fill(x, y, x + width, y + 5, 0xFF57341B);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 4, 0xFF21170F);
        int filled = Math.round((width - 2) * Mth.clamp(value, 0.0F, 1.0F));
        if (filled > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + filled, y + 4, ORANGE);
            graphics.fill(x + 1, y + 1, x + 1 + filled, y + 2, LIGHT);
        }
    }

    private static void renderBurnValues(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui || !inMatch(client) || !isLiving(client.player)) return;
        ArsonistRoleData ownComp = ArsonistRoleData.of(client.player);
        PoseStack poses = context.matrixStack();
        if (ownComp == null || poses == null) return;
        Vec3 camera = context.camera().getPosition();
        float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
        boolean rendered = false;
        for (Player target : client.level.players()) {
            if (target == client.player || !isLiving(target) || client.player.distanceToSqr(target) > 32 * 32) continue;
            int heat = Mth.clamp(ownComp.burnValues.getOrDefault(target.getUUID(), 0), 0, 100);
            Vec3 position = target.getPosition(partial);
            if (position.distanceToSqr(camera) > 32 * 32) continue;
            poses.pushPose();
            poses.translate(position.x - camera.x, position.y - camera.y + target.getBbHeight() + 0.7,
                    position.z - camera.z);
            // 与原版名牌一致，使用相机完整朝向（包括俯仰），不能只绕 Y 轴转动。
            poses.mulPose(client.getEntityRenderDispatcher().cameraOrientation());
            poses.scale(-0.025F, -0.025F, 0.025F);
            Matrix4f matrix = poses.last().pose();
            // 普通名牌背景与 NORMAL 字体都保留深度测试，不使用透视 RenderType。
            VertexConsumer vertices = buffers.getBuffer(RenderType.textBackground());
            quad(vertices, matrix, -32, -2, 32, 9, 0xDD140E09);
            quad(vertices, matrix, -32, 11, 32, 16, 0xEE342012);
            if (heat > 0) quad(vertices, matrix, -31, 12, -31 + 62 * heat / 100.0F, 15, ORANGE);
            Component percentage = text("burn_percent", heat);
            client.font.drawInBatch(percentage, -client.font.width(percentage) / 2.0F, 0,
                    LIGHT, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            poses.popPose();
            rendered = true;
        }
        if (rendered) buffers.endBatch();
    }

    private static void quad(VertexConsumer vertices, Matrix4f matrix,
                             float left, float top, float right, float bottom, int color) {
        vertices.addVertex(matrix, left, top, 0).setColor(color).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, left, bottom, 0).setColor(color).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, right, bottom, 0).setColor(color).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, right, top, 0).setColor(color).setLight(LightTexture.FULL_BRIGHT);
    }
}
