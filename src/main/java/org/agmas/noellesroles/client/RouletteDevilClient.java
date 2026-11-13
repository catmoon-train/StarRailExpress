package org.agmas.noellesroles.client;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.agmas.noellesroles.client.rolescreen.RouletteDevilRoleScreenExtension;
import org.agmas.noellesroles.packet.RouletteDevilStateS2CPacket;
import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.client.RoleInstinctEvents;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.SREPlayerUtils;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** 只缓存服务端授权的可见性和选人状态，不在客户端推算理智或改变对决结果。 */
public final class RouletteDevilClient {
    private static final RouletteDevilStateS2CPacket EMPTY = new RouletteDevilStateS2CPacket(
            List.of(), List.of(), List.of(), List.of(), List.of(), 0, 0, 0, 0L, null, 0L);
    private static RouletteDevilStateS2CPacket state = EMPTY;
    private static Set<UUID> demons = Set.of();
    private static Set<UUID> visible = Set.of();
    private static Set<UUID> lowSan = Set.of();
    private static Set<UUID> seeded = Set.of();
    private static Set<UUID> mature = Set.of();
    private static ClientLevel world;
    private static boolean registered;

    private RouletteDevilClient() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ModRoles.ROULETTE_DEVIL.setInventoryScreenExtensionFactory(RouletteDevilRoleScreenExtension::new);
        ClientPlayNetworking.registerGlobalReceiver(RouletteDevilStateS2CPacket.ID, (packet, context) -> {
            var connection = context.client().getConnection();
            context.client().execute(() -> {
                // 断线后的旧任务不得把私有状态带入下一条连接。
                if (connection != null && context.client().getConnection() == connection) accept(packet);
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            checkWorld();
            RouletteDevilSmokeRenderer.tick(client);
        });
        HudRenderCallback.EVENT.register((graphics, delta) ->
                renderTransition(graphics, delta.getGameTimeDeltaPartialTick(false)));
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(ModRoles.ROULETTE_DEVIL.identifier(),
                (client, viewer, target, enabled) -> {
                    if (enabled && target instanceof Player && target != viewer && isLowSan(target.getUUID())) {
                        return TrueFalseAndCustomResult.custom(0xFF3388FF);
                    }
                    return TrueFalseAndCustomResult.pass();
                });
    }

    private static void accept(RouletteDevilStateS2CPacket packet) {
        checkWorld();
        if (world == null) return;
        state = packet;
        demons = Set.copyOf(packet.demons());
        visible = Set.copyOf(packet.visible());
        lowSan = Set.copyOf(packet.lowSan());
        seeded = Set.copyOf(packet.seeded());
        mature = Set.copyOf(packet.mature());
    }

    private static void checkWorld() {
        ClientLevel current = Minecraft.getInstance().level;
        if (world != current) {
            clear();
            world = current;
        }
    }

    public static void clear() {
        state = EMPTY;
        RouletteDevilSmokeRenderer.clear();
        demons = visible = lowSan = seeded = mature = Set.of();
        world = null;
    }

    public static RouletteDevilStateS2CPacket state() {
        checkWorld();
        return state;
    }

    /** 即使尚未收到私有快照，也不能短暂显示已同步职业的玩家模型。 */
    public static boolean suppressPlayer(Entity entity) {
        if (!(entity instanceof Player player)) return false;
        checkWorld();
        if (world == null || entity.level() != world) return false;
        return demons.contains(entity.getUUID()) || (SREClient.gameComponent != null
                && SREClient.gameComponent.isRole(player, ModRoles.ROULETTE_DEVIL)
                && GameUtils.isPlayerAliveAndSurvival(player));
    }

    public static Set<UUID> visibleDemons() {
        checkWorld();
        return visible;
    }

    /** 对决对手的无条件感知也由服务端 visible 授权；本地不附加理智门槛。 */
    public static boolean canSeeSmoke(UUID target) {
        checkWorld();
        return demons.contains(target) && visible.contains(target);
    }

    public static boolean isLowSan(UUID target) {
        checkWorld();
        return lowSan.contains(target);
    }

    public static boolean isSeeded(UUID target) {
        checkWorld();
        return seeded.contains(target);
    }

    public static boolean isMature(UUID target) {
        checkWorld();
        return mature.contains(target);
    }

    /** 仅控制按钮可用性；距离、理智、种子和对决占用仍须由服务端复核。 */
    public static boolean canChallenge(UUID target) {
        Minecraft client = Minecraft.getInstance();
        checkWorld();
        return client.player != null && world != null && client.getConnection() != null
                && SREClient.gameComponent != null && SREClient.gameComponent.isRunning()
                && SREClient.gameComponent.isRole(client.player, ModRoles.ROULETTE_DEVIL)
                && GameUtils.isPlayerAliveAndSurvival(client.player)
                && client.getConnection().getPlayerInfo(target) != null
                && SREPlayerUtils.isPlayerAlive(world, target)
                && !client.player.getUUID().equals(target)
                && state.duelOpponent() == null && (lowSan.contains(target) || mature.contains(target));
    }

    /** 一秒入场只使用世界时间与临时绘制，不修改亮度、相机或后处理配置。 */
    private static void renderTransition(GuiGraphics graphics, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        RouletteDevilStateS2CPacket snapshot = state();
        if (world == null || client.player == null || snapshot.transitionUntil() <= 0L) return;
        double remaining = snapshot.transitionUntil() - (world.getGameTime() + partialTick);
        if (remaining <= 0.0 || remaining > 20.0) return;
        float progress = (float) (1.0 - remaining / 20.0);
        float fade = Mth.clamp((float) remaining / 6.0F, 0.0F, 1.0F);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        graphics.fill(0, 0, width, height, ((int) (255 * fade) << 24) | 0x030208);
        for (int i = 0; i < 16; i++) {
            int insetX = width * i / 64;
            int insetY = height * i / 64;
            int color = (int) (15 * fade) << 24;
            graphics.fill(insetX, insetY, width - insetX, insetY + height / 64 + 1, color);
            graphics.fill(insetX, height - insetY - height / 64 - 1, width - insetX, height - insetY, color);
            graphics.fill(insetX, insetY, insetX + width / 64 + 1, height - insetY, color);
            graphics.fill(width - insetX - width / 64 - 1, insetY, width - insetX, height - insetY, color);
        }
        // 三条收束旋臂；几何仅在这二十 tick 内存在，不会遗留常驻遮罩。
        for (int arm = 0; arm < 3; arm++) {
            for (int i = 0; i < 48; i++) {
                double angle = i * 0.16 + progress * Math.PI * 4 + arm * Math.PI * 2 / 3;
                double radius = Math.min(width, height) * 0.65 * i / 48.0 * (1.0 - progress * 0.7);
                int x = width / 2 + (int) (Math.cos(angle) * radius);
                int y = height / 2 + (int) (Math.sin(angle) * radius);
                int size = 2 + i / 12;
                graphics.fill(x - size, y - size, x + size, y + size,
                        ((int) (180 * fade) << 24) | 0x181022);
            }
        }
    }
}
