package net.exmo.sre.sixtyseconds.client;

import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.network.TeamPingC2SPacket;
import net.exmo.sre.sixtyseconds.network.TeamPingS2CPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * 60s 队友标点客户端 handler：
 * <ul>
 *   <li>按 V 键（默认）在准星对准的位置打标点。</li>
 *   <li>将标点发送服务端（{@link TeamPingC2SPacket}），服务端转发给同队队友。</li>
 *   <li>收到队友标点后（{@link TeamPingS2CPacket}）在世界与地图上渲染光柱+标记。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class TeamPingClientHandler {

    /** 标点按键：V 键（默认未占用、方便一只手操作）。 */
    public static final KeyMapping PING_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.noellesroles.team_ping",
            GLFW.GLFW_KEY_V,
            "category.noellesroles.sixty_seconds"));

    /** 标点冷却 tick（防止连按刷标点），默认 2 秒。 */
    private static final int PING_COOLDOWN_TICKS = 40;
    private static long lastPingGameTime = Long.MIN_VALUE;

    private TeamPingClientHandler() {
    }

    public static void register() {
        // 服端推队友标点 → 客户端渲染光柱 + 地图标记
        ClientPlayNetworking.registerGlobalReceiver(TeamPingS2CPacket.ID, (payload, context) ->
                context.client().execute(() -> TeamPingClientManager.onPacket(payload)));

        // 断线清空标点
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            TeamPingClientManager.clear();
            lastPingGameTime = Long.MIN_VALUE;
        });

        // 每 tick 检测按键
        ClientTickEvents.END_CLIENT_TICK.register(TeamPingClientHandler::tick);

        // 每帧在世界中渲染标点光柱
        WorldRenderEvents.AFTER_TRANSLUCENT.register(TeamPingClientManager::render);
    }

    private static void tick(Minecraft client) {
        while (PING_KEY.consumeClick()) {
            if (!canPing(client)) continue;
            if (isOnCooldown(client)) continue;

            Vec3 target = raycastTarget(client);
            if (target == null) continue;

            // 冷却起点
            lastPingGameTime = client.level.getGameTime();

            // 发服务端
            ClientPlayNetworking.send(new TeamPingC2SPacket(
                    (int) target.x, (int) target.y, (int) target.z));
        }
    }

    /** 射线检测：优先实体命中点，其次方块命中面外推，最后准星前方 16 格。 */
    private static Vec3 raycastTarget(Minecraft client) {
        if (client.hitResult == null || client.player == null) return null;

        if (client.hitResult.getType() == HitResult.Type.ENTITY) {
            EntityHitResult ehr = (EntityHitResult) client.hitResult;
            return ehr.getLocation();
        }
        if (client.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) client.hitResult;
            // 在命中面偏移一点，让标点不要嵌在墙里
            return bhr.getLocation().add(
                    bhr.getDirection().getStepX() * 0.3,
                    bhr.getDirection().getStepY() * 0.3,
                    bhr.getDirection().getStepZ() * 0.3);
        }
        // 空射：取视线前方 16 格
        Vec3 look = client.player.getLookAngle();
        return client.player.getEyePosition().add(look.x * 16, look.y * 16, look.z * 16);
    }

    /** 60s 模式进行中 + 非旁观 + 已编队。 */
    private static boolean canPing(Minecraft client) {
        if (client.player == null || client.level == null || client.player.isSpectator()) return false;
        if (io.wifi.starrailexpress.client.SREClient.gameComponent == null
                || !io.wifi.starrailexpress.client.SREClient.gameComponent.isRunning()
                || SixtySecondsMod.MODE == null
                || io.wifi.starrailexpress.client.SREClient.gameComponent.getGameMode() != SixtySecondsMod.MODE)
            return false;
        return true;
    }

    private static boolean isOnCooldown(Minecraft client) {
        if (client.level == null) return true;
        return client.level.getGameTime() - lastPingGameTime < PING_COOLDOWN_TICKS;
    }
}
