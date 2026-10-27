package org.agmas.noellesroles.client;

import java.util.HashSet;
import java.util.Set;

import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.content.entity.CommandBlockMarkerEntity;
import org.agmas.noellesroles.game.command_block.CommandBlockCracks;
import org.agmas.noellesroles.game.command_block.CommandBlockRules;
import org.agmas.noellesroles.packet.CommandBlockProgressS2CPacket;

import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.client.gui.HudMoodRenderer;
import io.wifi.starrailexpress.client.gui.MiniGameHudRenderer;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** 屏幕左侧的破坏进度（百分比）、下个事件的倒计时条，以及只给杀手看的裂缝发光。 */
public final class CommandBlockProgressClient {
    private static int progress;
    private static int goal;
    /** 下个事件还剩多少 tick；服务端每 40 tick 纠偏一次，其余由客户端自行倒数。 */
    private static int eventRemaining;
    /** 事件间隔总 tick；为 0 表示不画倒计时条。 */
    private static int eventTotal;
    private static final Set<Integer> GLOWING = new HashSet<>();

    private CommandBlockProgressClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CommandBlockProgressS2CPacket.ID,
                (packet, context) -> context.client().execute(() -> {
                    progress = Math.max(0, packet.progress());
                    goal = Math.max(0, packet.goal());
                    eventRemaining = Math.max(0, packet.eventRemaining());
                    eventTotal = Math.max(0, packet.eventTotal());
                    if (goal > 0) {
                        io.wifi.starrailexpress.client.gui.MapRuleIntroHud.clear();
                    }
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(CommandBlockProgressClient::tick);
        CommonHudRenderCallback.EVENT.register((graphics, delta) -> render(graphics));
    }

    public static boolean isShowing() {
        return goal > 0;
    }

    /**
     * 当前命令方块的破坏阶段（0~3），与服务端 {@code CommandBlockRules.destructionStage} 同一套换算：
     * 进度不到 1/3 为阶段 0，1/3~2/3 为阶段 1，2/3 以上为阶段 2，打满即阶段 3（已被摧毁）。
     */
    public static int stage() {
        if (!isShowing()) {
            return 0;
        }
        return CommandBlockRules.destructionStage(progress, goal);
    }

    private static void clear() {
        progress = 0;
        goal = 0;
        eventRemaining = 0;
        eventTotal = 0;
        GLOWING.clear();
    }

    private static void render(FakeGuiGraphics graphics) {
        if (goal <= 0) {
            return;
        }
        var client = Minecraft.getInstance();
        if (client.options.hideGui) {
            return;
        }
        var font = client.font;
        var config = SREClientConfig.instance();
        float ratio = Mth.clamp(progress / (float) goal, 0.0F, 1.0F);
        int percent = Math.round(100.0F * ratio);
        Component text = Component.translatable("hud.noellesroles.command_block.progress", percent);
        int width = Math.max(64, font.width(text));
        int x = config.moodLeftOffset + 22;
        int y = config.moodTopOffset
                + Math.max(HudMoodRenderer.getHudBottom(), MiniGameHudRenderer.getHudBottom()) + 6;
        int barY = y + font.lineHeight + 3;
        int filled = Math.round(width * ratio);

        // 此回调只在缓存更新 tick 执行，文字和图形必须一起入缓存，不能绕到原生 GuiGraphics。
        graphics.drawString(font, text, x, y, 0xFFE7D4FF, true);
        graphics.fill(x, barY, x + width, barY + 1, 0x663E2F4A);
        if (filled > 0) {
            graphics.fill(x, barY, x + filled, barY + 1, 0xFFBA82E6);
        }
        renderEventTimer(graphics, client, x, width, barY + 4);
    }

    /** 下个事件的倒计时条，紧贴在血量条下方。只给杀手看，不写事件名也不写秒数。 */
    private static void renderEventTimer(FakeGuiGraphics graphics, Minecraft client, int x, int width, int y) {
        if (eventTotal <= 0 || !isKiller(client)) {
            return;
        }
        int remaining = Mth.clamp(eventRemaining, 0, eventTotal);
        // 越接近下一次事件，条越满。
        int filled = Math.round(width * Mth.clamp((eventTotal - remaining) / (float) eventTotal, 0.0F, 1.0F));
        graphics.fill(x, y, x + width, y + 1, 0x663E2F4A);
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + 1, 0xFF8C6BC8);
        }
    }

    private static void tick(Minecraft client) {
        // 会议(时间冻结)期间倒计时条与服务端一同停表，解冻后继续
        if (eventRemaining > 0 && client.level != null
                && !SREGameTimeComponent.KEY.get(client.level).isTimeFrozen()) {
            eventRemaining--;
        }
        tickGlow(client);
    }

    private static void tickGlow(Minecraft client) {
        if (client.level == null || client.player == null) {
            GLOWING.clear();
            return;
        }
        boolean show = isShowing();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof CommandBlockMarkerEntity) {
                entity.setGlowingTag(show);
            }
        }
        if (!show) {
            clearGlow(client);
            return;
        }
        boolean killer = isKiller(client);
        if (!killer) {
            clearGlow(client);
            return;
        }
        Set<Integer> seen = new HashSet<>();
        var box = client.player.getBoundingBox().inflate(128.0D);
        for (Entity entity : client.level.getEntities(client.player, box, CommandBlockCracks::isVisibleCrack)) {
            entity.setGlowingTag(true);
            seen.add(entity.getId());
        }
        GLOWING.removeIf(id -> {
            if (seen.contains(id)) {
                return false;
            }
            Entity entity = client.level.getEntity(id);
            if (entity != null) {
                entity.setGlowingTag(false);
            }
            return true;
        });
        GLOWING.addAll(seen);
    }

    private static void clearGlow(Minecraft client) {
        if (client.level == null) {
            GLOWING.clear();
            return;
        }
        for (int id : GLOWING) {
            Entity entity = client.level.getEntity(id);
            if (entity != null) {
                entity.setGlowingTag(false);
            }
        }
        GLOWING.clear();
    }

    private static boolean isKiller(Minecraft client) {
        if (SREClient.gameComponent == null || client.player == null) {
            return false;
        }
        SRERole role = SREClient.gameComponent.getRole(client.player);
        return role != null && (role.isKiller() || CommandBlockRules.isPoolRole(role));
    }
}
