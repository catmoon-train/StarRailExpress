package org.agmas.noellesroles.game.modes.echo.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.modes.echo.EchoGameMode;
import org.agmas.noellesroles.game.modes.echo.objective.EchoPublicPayload;
import org.agmas.noellesroles.game.modes.echo.state.*;
import java.util.*;

/** 公开目标与本人状态分区；旁观者不读取其他人的私有RoleData。 */
public final class EchoHud {
    private static final int TEXT = 0xFFFFF4DC, GOLD = 0xFFD4AF37, MUTED = 0xFF9E8B6E;
    private static final int GREEN = 0xFF72C17B, RED = 0xFFE06B65;
    private static EchoPublicPayload view;
    private static int age;
    private EchoHud() { }
    static EchoPublicPayload view() { return view; }
    static int age() { return age; }
    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(EchoPublicPayload.ID,(p,c) -> c.client().execute(() -> { view=p; age=0; }));
        EchoObjectiveClient.initialize();
        EchoMachineClient.initialize();
        EchoFacilityMapClient.initialize();
        EchoRollClient.initialize();
        EchoRepairClient.initialize();
        EchoShopClient.initialize();
        EchoBeaconRingClient.register();
        EchoScreenFx.initialize();
        EchoSkillHud.initialize();
        EchoAtmosphereClient.initialize();
        org.agmas.noellesroles.game.modes.echo.anim.EchoHuntClient.initialize();
        EchoHuntFxClient.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if(view==null) return;
            if(client.level==null || client.player==null || !client.level.dimension().location().equals(view.dimension()) || ++age>100
                    || !(io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(client.level).getGameMode() instanceof EchoGameMode)
                    || view.state().phase()==EchoPhase.FINISHED || view.state().phase()==EchoPhase.SETTLING) view=null;
        });
        org.agmas.noellesroles.client.event.CommonHudRenderCallback.EVENT.register((g,dt) -> render(g));
    }

    /** 三个 HUD 共用公开会话门控，旧本人投影不能跨局或向旁观者泄漏。 */
    static boolean visible() {
        var client = Minecraft.getInstance();
        return client.player != null && client.level != null && !client.options.hideGui && view != null && age <= 100
                && client.level.dimension().location().equals(view.dimension())
                && io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(client.level).getGameMode() instanceof EchoGameMode
                && view.state().phase() != EchoPhase.FINISHED && view.state().phase() != EchoPhase.SETTLING;
    }

    static EchoRoleData ownData() {
        if (!visible() || Minecraft.getInstance().player.isSpectator()) return null;
        var data = EchoBagClient.data();
        return data != null && data.faction() != null && view.match().equals(data.matchId()) ? data : null;
    }

    private static void render(FakeGuiGraphics g) {
        if (!visible()) return;
        var client = Minecraft.getInstance();
        if (client.screen != null) return;
        int width = client.getWindow().getGuiScaledWidth(), height = client.getWindow().getGuiScaledHeight();
        var layout = new EchoHudLayout(width, height);
        var s = view.state();
        var d = ownData();
        long tick = s.displayTick(age);
        var header = layout.header();
        int x = header.x(), y = header.y(), pw = header.width();
        panel(g, x, y, pw, header.height());
        long seconds = (s.displayRemaining(age) + 19) / 20;
        Component phase = s.paused() ? tr("paused") : tr("phase." + s.phase().name().toLowerCase(Locale.ROOT));
        row(g, tr("phase_time", phase, String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)),
                x + 5, y + 4, pw / 2 - 7, s.paused() ? RED : GOLD);
        Component gate = s.coreOpen() ? tr("compact.core_open", EchoPublicState.seconds(s.coreUntil(), tick)) : tr("compact.core_closed");
        row(g, gate, x + pw / 2, y + 4, pw / 2 - 5, s.coreOpen() ? GREEN : MUTED);
        if (s.phase() == EchoPhase.OPERATIONS && !s.beacons().isEmpty()) {
            int count = s.beacons().size();
            for (int i = 0; i < count; i++) {
                int left = x + 5 + i * (pw - 10) / count;
                int right = x + 5 + (i + 1) * (pw - 10) / count - 4;
                var b = s.beacons().get(i);
                boolean blocked = b.stalled(tick);
                int color = b.active() ? GREEN : blocked ? RED : TEXT;
                int progress = b.active() ? 1000 : Math.max(0, Math.min(999, b.progress()));
                row(g, tr("compact.beacon", i + 1, b.delivered(), b.required(), progress / 10), left, y + 17, right - left, color);
                g.fill(left, y + 29, right, y + 31, 0xFF49341E);
                g.fill(left, y + 29, left + (right - left) * progress / 1000, y + 31, b.active() ? GREEN : blocked ? RED : GOLD);
            }
        } else if (s.phase() == EchoPhase.EVACUATION) {
            Component evac = s.evacuationOpen() ? tr("evac.open", s.escaped())
                    : s.closedUntil() > tick ? tr("evac.overload", s.escaped(), EchoPublicState.seconds(s.closedUntil(), tick))
                    : tr("evac.confirm", s.escaped());
            row(g, evac, x + 5, y + 19, pw - 10, s.evacuationOpen() ? GREEN : RED);
        } else row(g, tr("compact.details", client.options.keyInventory.getTranslatedKeyMessage()), x + 5, y + 19, pw - 10, MUTED);
        if (d == null) return;

        // 临时情报只在相关时展开；右侧狩猎区和底部生命/快捷栏始终留空。
        var context = layout.context();
        int infoX = context.x(), infoWidth = context.width(), infoY = context.y();
        if (!d.life().standing()) {
            Component life = d.life() == EchoLifeState.DOWNED ? tr("life.bleeding", (d.bleedRemaining() + 19) / 20)
                    : tr("life.label", tr("life." + d.life().name().toLowerCase(Locale.ROOT)));
            row(g, life, infoX, infoY, infoWidth, RED);
            infoY += 11;
        }
        int n = 0;
        for (var signal : d.signals()) if (signal.target() == null && d.projectedTick() < signal.until() && n++ < 2) {
            String kind = switch (signal.kind()) {
                case "salvage" -> "salvage"; case "key_contract" -> "key_contract";
                case "cargo_pulse" -> "cargo"; case "key_pulse" -> "key"; case "fault_pulse" -> "fault";
                case "relay" -> "relay";
                default -> "tracking";
            };
            // 故障声纹用红色，其它方向信号保持金色。
            int signalColor = "fault".equals(kind) ? RED : GOLD;
            row(g, tr("signal." + kind, tr("direction." + Math.floorMod(signal.sector(), 8))), infoX, infoY, infoWidth, signalColor);
            infoY += 11;
        }
        if (EchoCardClient.selectingSecond()) {
            row(g, tr("second_point"), infoX, infoY, infoWidth, GOLD);
            infoY += 11;
        } else if (!EchoBagClient.localError().isEmpty()) {
            row(g, Component.translatable("echo.inventory.request." + EchoBagClient.localError()), infoX, infoY, infoWidth, RED);
            infoY += 11;
        } else if (!d.requestError().isEmpty()) {
            row(g, translated("echo.error." + d.requestError().toLowerCase(Locale.ROOT), "request_failed"), infoX, infoY, infoWidth, RED);
            infoY += 11;
        }
        infoY = EchoMachineClient.renderHud(g, infoX, infoY, infoWidth, context.bottom() - 18);
        {
            var action = d.actionView();
            int ay = Math.max(infoY, Math.min(height / 2 + 20, context.bottom() - 18));
            if (action != null) {
                int progress = Math.min(999, action.progress(tick));
                Component name = translated("echo.dashboard.action." + action.kind().toLowerCase(Locale.ROOT), "action.unknown");
                row(g, tr("action.progress", name, progress / 10, EchoPublicState.seconds(action.startedAt() + action.duration(), tick)),
                        infoX, ay, infoWidth, TEXT);
                g.fill(infoX, ay + 12, infoX + infoWidth, ay + 15, 0xFF49341E);
                g.fill(infoX, ay + 12, infoX + infoWidth * progress / 1000, ay + 15, GOLD);
            } else if (!d.interactionError().isEmpty()) {
                row(g, translated("echo.error." + d.interactionError().toLowerCase(Locale.ROOT), "request_failed"), infoX, ay, infoWidth, RED);
            }
        }
        renderSupplies(g, d, s, tick, width, height);
    }

    static String goal(EchoPublicState s, EchoRoleData d) {
        if (s.paused()) return "paused";
        if (d == null) return "public." + s.phase().name().toLowerCase(Locale.ROOT);
        if (!d.life().standing()) return "life." + d.life().name().toLowerCase(Locale.ROOT);
        if (s.phase() == EchoPhase.SAFE) return "safe." + d.faction().name().toLowerCase(Locale.ROOT);
        if (d.faction() == EchoFaction.BROKER) {
            var p = d.contractProgress();
            if (p != null && p.status().equals("SUCCEEDED")) return "broker.done";
            if (p != null && p.status().equals("ARMED")) return "broker.survive";
            return d.contract() == null ? "broker.wait" : "broker." + d.contract().name().toLowerCase(Locale.ROOT);
        }
        if (s.phase() == EchoPhase.EVACUATION) return d.faction() == EchoFaction.CARRIER
                ? s.evacuationOpen() ? "carrier.evacuate" : "carrier.wait_gate"
                : s.overloadUsed() ? "interceptor.guard" : "interceptor.overload";
        if (d.faction() == EchoFaction.INTERCEPTOR) return "interceptor.operations";
        if (!s.beacons().isEmpty() && s.beacons().stream().allMatch(b -> b.delivered() >= b.required()))
            return s.beacons().stream().allMatch(EchoPublicState.Beacon::active) ? "carrier.ready" : "carrier.escort";
        if (!d.cargo().isEmpty()) return "carrier.deliver";
        return d.key() != null && !s.coreOpen() ? "carrier.unlock" : "carrier.collect";
    }

    private static void renderSupplies(FakeGuiGraphics g, EchoRoleData d, EchoPublicState s, long tick, int width, int height) {
        var layout = new EchoHudLayout(width, height);
        var supplies = layout.supplies();
        g.fill(supplies.x(), supplies.y(), supplies.right(), supplies.bottom(), 0xA01A1008);
        int half = supplies.width() / 2;
        row(g, tr("cargo", d.cargo().size(), d.capacity(), d.blackCargoCount()), supplies.x() + 3, supplies.y() + 1, half - 6, TEXT);
        Component key = d.key() == null ? tr("key.none") : d.keyPulseAt() > tick
                ? tr("compact.key", EchoPublicState.seconds(d.keyPulseAt(), tick)) : tr("compact.key_held");
        row(g, key, supplies.x() + half, supplies.y() + 1, half - 3, GOLD);
        var utility = layout.utility();
        Component basic = d.faction() == EchoFaction.CARRIER ? EchoRollClient.hud(d)
                : d.faction() == EchoFaction.INTERCEPTOR && s.phase() == EchoPhase.EVACUATION
                ? tr(s.overloadUsed() ? "overload.used" : "overload.ready") : tr("compact.details", Minecraft.getInstance().options.keyInventory.getTranslatedKeyMessage());
        row(g, basic, utility.x() + 3, utility.y() + 1, utility.width() / 2 - 7, MUTED);
        // 本模式常规 HUD 屏蔽原版血条时，补回基于真实 health 的三颗心，而非旧生命枚举。
        if (!io.wifi.starrailexpress.client.SREClient.shouldRenderVanillaHud()) {
            var health = layout.health();
            // 公共 HUD 只在脏 tick 录制，心形精灵必须入帧缓存，不能直接绘制一帧后消失。
            var graphics = g;
            float hp = Math.max(0, Math.min(6, d.health()));
            for (int i = 0; i < 3; i++) {
                int hx = health.x() + i * 9;
                graphics.blitSprite(net.minecraft.resources.ResourceLocation.withDefaultNamespace("hud/heart/container"), hx, health.y(), 9, 9);
                if (hp > i * 2) graphics.blitSprite(net.minecraft.resources.ResourceLocation.withDefaultNamespace(
                        hp >= i * 2 + 2 ? "hud/heart/full" : "hud/heart/half"), hx, health.y(), 9, 9);
            }
        }
    }

    static Component tr(String key, Object... args) { return Component.translatable("echo.dashboard." + key, args); }
    private static Component translated(String key, String fallback) {
        return I18n.exists(key) ? Component.translatable(key) : tr(fallback);
    }
    private static void panel(FakeGuiGraphics g, int x, int y, int width, int height) {
        g.fillGradient(x, y, x + width, y + height, 0xD81A1008, 0xD820140A);
        g.renderOutline(x, y, width, height, 0xFF8B6914);
        g.fill(x + 1, y + 1, x + width - 1, y + 2, 0x33FFE8C0);
    }
    static void row(FakeGuiGraphics g, Component text, int x, int y, int width, int color) {
        var font = Minecraft.getInstance().font;
        String line = text.getString();
        if (font.width(line) > width) line = font.plainSubstrByWidth(line, Math.max(0, width - font.width("…"))) + "…";
        g.enableScissor(x, y, x + width, y + 10);
        g.drawString(font, line, x, y, color, false);
        g.disableScissor();
    }
}
