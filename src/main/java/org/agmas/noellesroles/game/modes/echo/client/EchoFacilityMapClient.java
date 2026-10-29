package org.agmas.noellesroles.game.modes.echo.client;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.modes.echo.EchoGameMode;
import org.agmas.noellesroles.game.modes.echo.map.EchoFacilityMapPayload;
import org.agmas.noellesroles.game.modes.echo.state.EchoMachineView;
import org.agmas.noellesroles.game.modes.echo.state.EchoPhase;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/** 本局公开设施点位的只读缓存；不从玩家、掉落物或管理员预览推导地图标记。 */
public final class EchoFacilityMapClient {
    private static EchoFacilityMapPayload snapshot;
    private static ClientLevel world;
    private static int age;
    private static final EnumSet<EchoMachineView.Type> VISIBLE = EnumSet.allOf(EchoMachineView.Type.class);
    private static List<EchoMachineView.Type> types = List.of();
    private EchoFacilityMapClient() { }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(EchoFacilityMapPayload.ID, (payload, context) -> context.client().execute(() -> {
            var level = context.client().level;
            if (level == null || !level.dimension().location().equals(payload.dimension())) return;
            if (payload.match().equals(EchoFacilityMapPayload.NONE)) { clear(); return; }
            if (snapshot == null || !snapshot.match().equals(payload.match())) {
                VISIBLE.clear(); VISIBLE.addAll(EnumSet.allOf(EchoMachineView.Type.class));
            }
            snapshot = payload; world = level; age = 0;
            types = payload.entries().stream().map(EchoFacilityMapPayload.Entry::type).distinct().sorted().toList();
        }));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (snapshot != null && (++age > 200 || !inMatch() || client.level != world)) clear();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    /** 等待设施快照期间也可打开地图与扫描地形，不要求持有物品。 */
    public static boolean inMatch() {
        var client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return false;
        var game = SREGameWorldComponent.KEY.get(client.level);
        if (!(game.getGameMode() instanceof EchoGameMode) || game.getGameStatus() != SREGameWorldComponent.GameStatus.ACTIVE) return false;
        var own = EchoBagClient.data();
        return own == null || own.matchId() == null || own.phase() != EchoPhase.SETTLING && own.phase() != EchoPhase.FINISHED;
    }
    public static boolean ready() {
        if (!inMatch() || snapshot == null || Minecraft.getInstance().level != world || age > 200) return false;
        var own = EchoBagClient.data();
        return own == null || own.matchId() == null || own.matchId().equals(snapshot.match());
    }
    public static UUID match() { return ready() ? snapshot.match() : null; }
    public static List<EchoFacilityMapPayload.Entry> entries() { return ready() ? snapshot.entries() : List.of(); }
    public static List<EchoMachineView.Type> types() { return ready() ? types : List.of(); }
    public static boolean visible(EchoMachineView.Type type) { return VISIBLE.contains(type); }
    public static void toggle(EchoMachineView.Type type) { if (!VISIBLE.remove(type)) VISIBLE.add(type); }
    public static void showAll(boolean show) { VISIBLE.clear(); if (show) VISIBLE.addAll(EnumSet.allOf(EchoMachineView.Type.class)); }
    public static Component tr(String key, Object... args) { return Component.translatable("echo.map." + key, args); }
    public static Component name(EchoFacilityMapPayload.Entry entry) {
        if (entry.type() == EchoMachineView.Type.EVACUATION_CONSOLE && entry.number() == 2) return tr("evacuation_zone");
        return tr("facility_number", Component.translatable(entry.type().nameKey()), entry.number());
    }
    public static int color(EchoMachineView.Type type) {
        return switch (type) {
            case BEACON, CORE_BEACON -> 0xFF72C17B;
            case CRYSTAL, ALTAR -> 0xFF5EB7D8;
            case SUPPLY, BLACK_MARKET -> 0xFFD4AF37;
            case BLACK_NODE, BLACK_DROPOFF -> 0xFFB18AE6;
            case CORE_CONSOLE, FREE_EXIT -> 0xFFE0AD5B;
            case EVACUATION_CONSOLE, BROKER_EXIT -> 0xFFE06B65;
        };
    }
    public static String symbol(EchoMachineView.Type type) {
        return switch (type) {
            case BEACON -> "B"; case CORE_BEACON -> "C"; case CRYSTAL -> "R"; case ALTAR -> "K";
            case SUPPLY -> "S"; case BLACK_MARKET -> "$"; case BLACK_NODE -> "D"; case BLACK_DROPOFF -> "+";
            case CORE_CONSOLE -> "G"; case FREE_EXIT -> "F"; case EVACUATION_CONSOLE -> "E"; case BROKER_EXIT -> "X";
        };
    }
    /** 色块+符号同用于大小地图及图例，色盲也能区分设施类别。 */
    public static void drawMarker(FakeGuiGraphics g, EchoMachineView.Type type, int x, int y, boolean small) {
        int radius = small ? 3 : 5;
        g.fill(x-radius-1, y-radius-1, x+radius+2, y+radius+2, 0xE0100B06);
        g.renderOutline(x-radius, y-radius, radius*2+1, radius*2+1, color(type));
        float scale = small ? 0.6F : 0.85F;
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1F);
        var font = Minecraft.getInstance().font;
        g.drawString(font, symbol(type), -font.width(symbol(type))/2, -4, color(type), false);
        g.pose().popPose();
    }
    private static void clear() { snapshot = null; world = null; age = 0; types = List.of(); }
}
