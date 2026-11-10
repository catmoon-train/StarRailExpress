package pro.fazeclan.river.stupid_express.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.fazeclan.river.stupid_express.role.arsonist.ArsonistOverviewChunks;

/** 只在短俯瞰期间调整内存中的渲染设置与区块缓存，不保存 options。 */
@Environment(EnvType.CLIENT)
public final class ArsonistOverviewClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArsonistOverviewClient.class);
    // 正常轨道为 120 tick；留出准备/收尾余量，漏掉结束调用时也会请求服务端恢复快照。
    private static final int SAFETY_TICKS = 200;
    private static boolean registered;
    private static View active;
    private static int ticks;

    private ArsonistOverviewClient() {}

    public static void register() {
        if (registered) return;
        registered = true;
        // Fabric 1.21.1 的 payload 接收器已在主线程；不能再排队而让后续原版区块包先执行。
        ClientPlayNetworking.registerGlobalReceiver(ArsonistOverviewChunks.PrepS2C.ID,
                (packet, context) -> prepare(context.client(), packet));
        ClientPlayNetworking.registerGlobalReceiver(ArsonistOverviewChunks.RestoreS2C.ID,
                (packet, context) -> restore(context.client(), packet));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> restore(client, null));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (active == null) return;
            if (client.level != active.level()) {
                requestFinish();
                restore(client, null);
            } else if (++ticks == SAFETY_TICKS) {
                requestFinish();
            }
        });
    }

    private static void prepare(Minecraft client, ArsonistOverviewChunks.PrepS2C packet) {
        if (client.level == null || !client.level.dimension().location().equals(packet.dimension())
                || packet.normalRadius() < 2 || packet.normalRadius() > ArsonistOverviewChunks.MAX_RADIUS
                || packet.renderRadius() < 2 || packet.renderRadius() > ArsonistOverviewChunks.MAX_RADIUS) {
            requestFinish();
            return;
        }
        if (active != null) restore(client, null);
        int originalRenderDistance = client.options.renderDistance().get();
        boolean raiseRender = originalRenderDistance < packet.renderRadius();
        boolean raiseCache = packet.normalRadius() < packet.renderRadius();
        active = new View(client.level, packet, originalRenderDistance, raiseRender, raiseCache);
        ticks = 0;
        // 必须在原中心扩容以保留原区块，然后才移中心；反过来扩容会丢弃玩家原区域。
        if (raiseCache) {
            client.level.getChunkSource().updateViewRadius(packet.renderRadius());
            // 原版有效渲染距离取用户设置和服务端限制的最小值，两者都要临时满足俯瞰范围。
            client.options.setServerRenderDistance(packet.renderRadius());
        }
        if (raiseRender) {
            var option = client.options.renderDistance();
            option.set(packet.renderRadius());
            if (option.get() < packet.renderRadius()) {
                option.set(originalRenderDistance);
                LOGGER.warn("客户端渲染距离选项不接受 {}，俯瞰可能不完整；不绕过选项约束", packet.renderRadius());
            }
        }
        client.level.getChunkSource().updateViewCenter(packet.centerX(), packet.centerZ());
    }

    private static void restore(Minecraft client, ArsonistOverviewChunks.RestoreS2C packet) {
        View view = active;
        if (view == null) return;
        active = null;
        ticks = 0;
        boolean sameLevel = client.level == view.level()
                && (packet == null || client.level.dimension().location().equals(packet.dimension()));
        if (sameLevel) {
            int centerX = packet == null ? view.preparation().returnX() : packet.centerX();
            int centerZ = packet == null ? view.preparation().returnZ() : packet.centerZ();
            // 先回玩家中心再缩容，未被预览覆盖的原区块会被原版完整保留。
            client.level.getChunkSource().updateViewCenter(centerX, centerZ);
            if (view.raisedCache()) {
                client.level.getChunkSource().updateViewRadius(view.preparation().normalRadius());
                client.options.setServerRenderDistance(view.preparation().normalRadius());
            }
        }
        // 换维度时不覆盖新世界刚收到的服务端视距，但用户设置仍必须还原。
        if (view.raisedRender()) client.options.renderDistance().set(view.originalRenderDistance());
    }

    private static void requestFinish() {
        if (ClientPlayNetworking.canSend(ArsonistOverviewChunks.FinishC2S.ID)) {
            ClientPlayNetworking.send(new ArsonistOverviewChunks.FinishC2S());
        }
    }

    private record View(ClientLevel level, ArsonistOverviewChunks.PrepS2C preparation,
                        int originalRenderDistance, boolean raisedRender, boolean raisedCache) {}
}
