package net.exmo.sre.sixtyseconds.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.exmo.sre.sixtyseconds.network.TeamPingS2CPacket;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.*;

/**
 * 队友标点客户端管理器：
 * <ul>
 *   <li>接收 {@link TeamPingS2CPacket} 并存下队友标点（位置 + 玩家名 + 过期时间）。</li>
 *   <li>每帧在世界中渲染标点光柱（半透明，带深度测试，{@link #PING_DURATION_TICKS} 后渐隐消失）。</li>
 *   <li>同时在区域地图上添加临时标记点（{@link SixtySecondsClientMapZone}）。</li>
 * </ul>
 */
public final class TeamPingClientManager {

    /** 标点持续时间（tick），5 秒。 */
    public static final int PING_DURATION_TICKS = 100;

    /** 标点光柱 ARGB 颜色（金色/橙色暖色调）。 */
    private static final int BEAM_COLOR = 0xFFFFAA00;

    /** 世界标点在区域地图上的标记颜色 ARGB（与光柱同色相）。 */
    private static final int MAP_COLOR = 0xFFFFAA00;

    /** 光柱半透明渲染层：带深度测试（被墙体遮挡即不可见），不写深度，使用半透明混合。 */
    private static final RenderType BEAM_RENDER_TYPE = RenderType.create(
            "sixty_seconds_team_ping_beam",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLE_STRIP, 256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    /** 每个队友标点记录。 */
    private static final class PingEntry {
        final UUID playerId;
        final String playerName;
        final Vec3 pos;
        final long expireGameTime;

        PingEntry(UUID playerId, String playerName, Vec3 pos, long expireGameTime) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.pos = pos;
            this.expireGameTime = expireGameTime;
        }
    }

    private static final List<PingEntry> PINGS = new ArrayList<>();

    private TeamPingClientManager() {
    }

    /** 客户端收包时调用。 */
    public static void onPacket(TeamPingS2CPacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        long expire = client.level.getGameTime() + PING_DURATION_TICKS;
        Vec3 pos = new Vec3(packet.x() + 0.5, packet.y(), packet.z() + 0.5);

        // 解析玩家名（客户端可能没有此玩家的完整 profile，尝试从本地玩家列表找）
        String name = "???";
        if (client.level.players() != null) {
            var found = client.level.players().stream()
                    .filter(p -> p.getUUID().equals(packet.playerId()))
                    .findFirst();
            if (found.isPresent()) {
                name = found.get().getGameProfile().getName();
            }
        }

        PINGS.add(new PingEntry(packet.playerId(), name, pos, expire));

        // 同时在地图上打标记
        SixtySecondsClientMapZone.addPingMarker(pos.x, pos.z);
    }

    /** 每帧在世界中渲染所有未过期的标点光柱。挂在 WorldRenderEvents.AFTER_TRANSLUCENT。 */
    public static void render(WorldRenderContext context) {
        if (PINGS.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            PINGS.clear();
            return;
        }
        long now = client.level.getGameTime();
        Vec3 cameraPos = context.camera().getPosition();
        PoseStack matrices = context.matrixStack();
        VertexConsumer buffer = context.consumers().getBuffer(BEAM_RENDER_TYPE);

        for (Iterator<PingEntry> it = PINGS.iterator(); it.hasNext();) {
            PingEntry ping = it.next();
            long left = ping.expireGameTime - now;
            if (left <= 0) {
                it.remove();
                continue;
            }

            float alpha = 0.15F + 0.45F * (float) left / PING_DURATION_TICKS;
            renderBeam(matrices, buffer, ping.pos, cameraPos, alpha, now);
        }
    }

    /** 渲染一条从地面升起的竖直光柱（4 面十字形，类似 beacon 光柱但更细）。 */
    private static void renderBeam(PoseStack matrices, VertexConsumer buffer, Vec3 pos,
            Vec3 cameraPos, float alpha, long gameTime) {
        double dx = pos.x - cameraPos.x;
        double dy = pos.y - cameraPos.y;
        double dz = pos.z - cameraPos.z;

        // 光柱从目标点起向上 3 格
        float bottom = (float) dy;
        float top = bottom + 3.0F;

        int r = (BEAM_COLOR >> 16) & 0xFF;
        int g = (BEAM_COLOR >> 8) & 0xFF;
        int b = BEAM_COLOR & 0xFF;
        int a = (int) (alpha * 255);

        // 脉冲效果：光柱粗细随 time 变化 0.08-0.16
        float pulse = 0.08F + 0.08F * (float) Math.sin(gameTime * 0.3);
        float hw = pulse; // half-width

        matrices.pushPose();
        matrices.translate(dx, 0, dz);
        Matrix4f m = matrices.last().pose();

        // 四面十字光柱（沿 X 轴和 Z 轴各一面板）
        // X 面板：从 (-hw, bottom, 0) 到 (+hw, top, 0)
        buffer.addVertex(m, -hw, bottom, 0).setColor(r, g, b, a);
        buffer.addVertex(m, -hw, top, 0).setColor(r, g, b, 0); // 顶部渐透明
        buffer.addVertex(m, +hw, bottom, 0).setColor(r, g, b, a);
        buffer.addVertex(m, +hw, top, 0).setColor(r, g, b, 0);

        // Z 面板：从 (0, bottom, -hw) 到 (0, top, +hw)
        buffer.addVertex(m, 0, bottom, -hw).setColor(r, g, b, a);
        buffer.addVertex(m, 0, top, -hw).setColor(r, g, b, 0);
        buffer.addVertex(m, 0, bottom, +hw).setColor(r, g, b, a);
        buffer.addVertex(m, 0, top, +hw).setColor(r, g, b, 0);

        matrices.popPose();
    }

    /** 断线时清空全部标点。 */
    public static void clear() {
        PINGS.clear();
    }
}
