package org.agmas.noellesroles.client.decree;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.innocence.decree.Decree;
import org.agmas.noellesroles.packet.DecreeS2CPacket;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 入离场包驱动的本地领域：各施法者独立计时，不改变实体或用户设置。 */
public final class DecreeClient {
    private static final Map<UUID, Field> FIELDS = new LinkedHashMap<>();
    private static final List<Field> LEAVING = new ArrayList<>();
    private static final DecreeVoice VOICE = new DecreeVoice();
    private static ClientLevel level;
    private static long impactAt;

    private DecreeClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(DecreeS2CPacket.ID,
                (packet, context) -> context.client().execute(() -> accept(packet)));
        ClientTickEvents.END_CLIENT_TICK.register(DecreeClient::tick);
        HudRenderCallback.EVENT.register(DecreeClient::renderHud);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> VOICE.destroy());
        VOICE.initialize();
    }

    /** 接收器在客户端主线程调用；零时长不解析词条，始终删除对应施法者。 */
    public static void accept(DecreeS2CPacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;
        if (level != client.level) {
            clear();
            level = client.level;
        }
        if (packet.remainingTicks() <= 0) {
            retire(packet.casterId());
            return;
        }
        Decree decree = Decree.byId(packet.decreeId());
        if (decree == null) return;
        Field field = FIELDS.get(packet.casterId());
        if (field == null || field.decree != decree) {
            retire(packet.casterId());
            field = new Field(packet.casterId(), decree);
            FIELDS.put(packet.casterId(), field);
        }
        field.remaining = Math.min(packet.remainingTicks(), decree.durationTicks());
        if (packet.impact() && !field.impacted && applies(field, client)) {
            field.impacted = true;
            impactAt = System.nanoTime();
            client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 0.65F, 0.3F));
            VOICE.say(Component.translatable("speech.noellesroles.decree",
                    Component.translatable(decree.translationKey())).getString(), true);
        }
        closeInventory(client);
    }

    private static void retire(UUID caster) {
        Field old = FIELDS.remove(caster);
        if (old != null && old.opacity > 0F) LEAVING.add(old);
    }

    public static void clear() {
        FIELDS.clear();
        LEAVING.clear();
        level = null;
        impactAt = 0L;
        VOICE.clear();
    }

    private static void tick(Minecraft client) {
        if (client.level == null || client.player == null || level != client.level
                || !SREGameWorldComponent.KEY.get(client.level).isRunning()) {
            if (!FIELDS.isEmpty() || !LEAVING.isEmpty() || impactAt != 0L) clear();
            return;
        }
        if (client.isPaused()) return;
        var iterator = FIELDS.values().iterator();
        while (iterator.hasNext()) {
            Field field = iterator.next();
            field.remaining--;
            field.age++;
            field.opacity = Math.max(0F, Math.min(1F, field.opacity + (applies(field, client) ? 0.12F : -0.08F)));
            if (field.remaining <= 0) {
                iterator.remove();
                if (field.opacity > 0F) LEAVING.add(field);
            }
        }
        LEAVING.removeIf(field -> (field.opacity -= 0.08F) <= 0F);
        closeInventory(client);
        if (isRestricted(Decree.SPRINT)) client.player.setSprinting(false);
    }

    private static boolean applies(Field field, Minecraft client) {
        if (client.player == null || client.level != level) return false;
        Player caster = client.level.getPlayerByUUID(field.caster);
        // 实体尚未同步时信任服务端入场快照；可见时用实时位置避免移动边界滞后。
        return caster == null || (caster.isAlive() && !caster.isSpectator() && caster.distanceToSqr(client.player) <= 225D);
    }

    public static boolean isRestricted(Decree decree) {
        Minecraft client = Minecraft.getInstance();
        for (Field field : FIELDS.values()) {
            if (field.decree == decree && field.remaining > 0 && applies(field, client)
                    && !(decree == Decree.LIGHT && field.caster.equals(client.player.getUUID()))) return true;
        }
        return false;
    }

    public static boolean blocksScreen(Screen screen) {
        return (screen instanceof AbstractContainerScreen<?>
                || screen instanceof io.wifi.starrailexpress.client.gui.screen.ingame.LimitedHandledScreen<?>)
                && isRestricted(Decree.INVENTORY);
    }

    private static void closeInventory(Minecraft client) {
        if (blocksScreen(client.screen) && client.player != null) client.player.closeContainer();
    }

    /** 只向当前观察者追加轮廓；不写 Glowing 状态，不覆盖其它轮廓来源。 */
    public static boolean shouldGlow(Entity entity) {
        Minecraft client = Minecraft.getInstance();
        if (!(entity instanceof Player target) || client.player == null || client.level != level) return false;
        for (Field field : FIELDS.values()) {
            if (field.remaining <= 0 || !applies(field, client)) continue;
            if (target.getUUID().equals(field.caster)) return true;
            if (field.decree == Decree.INVISIBILITY && target.isInvisible() && !target.isSpectator()) {
                Player caster = client.level.getPlayerByUUID(field.caster);
                if (caster != null && caster.distanceToSqr(target) <= 225D) return true;
            }
        }
        return false;
    }

    /** 短促的局部相机压力，最多 0.45 秒、2.2 厘米；每帧 setup 重建，无累计偏移。 */
    public static float cameraPressure() {
        if (impactAt == 0L || Minecraft.getInstance().player == null) return 0F;
        double elapsed = (System.nanoTime() - impactAt) / 1_000_000_000D;
        return elapsed >= 0.45D ? 0F : (float) (0.022D * Math.pow(1D - elapsed / 0.45D, 2D));
    }

    private static void renderHud(GuiGraphics g, DeltaTracker tracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level != level) return;
        int w = g.guiWidth(), h = g.guiHeight();
        // 白金曝光感叠层兼容 shader，不修改 gamma、视角或其它持久设置。
        if (impactAt != 0L) {
            double elapsed = (System.nanoTime() - impactAt) / 1_000_000_000D;
            if (elapsed < 0.65D) {
                int alpha = (int) (42D * Math.pow(1D - elapsed / 0.65D, 2D));
                g.fill(0, 0, w, h, alpha << 24 | 0xFFF2CC);
            }
        }
        if (client.options.hideGui) return;
        List<Field> visible = new ArrayList<>();
        for (Field field : FIELDS.values()) if (field.opacity > 0.01F) visible.add(field);
        visible.addAll(LEAVING);
        int rows = Math.max(1, (h / 2 - 24) / 26);
        int columns = Math.max(1, (visible.size() + rows - 1) / rows);
        int cell = Math.min(400, (w - 16) / columns);
        int start = (w - cell * columns) / 2;
        for (int i = 0; i < visible.size(); i++) {
            Field field = visible.get(i);
            int x = start + i / rows * cell, y = 20 + i % rows * 26;
            int alpha = (int) (255F * field.opacity);
            int glow = (int) ((22D + 5D * Math.sin((field.age + tracker.getGameTimeDeltaPartialTick(false)) * 0.12D)) * field.opacity);
            g.fillGradient(x + 2, y - 3, x + cell - 2, y + 21,
                    glow << 24 | 0xFFE3A0, 0x00FFE3A0);
            g.fill(x + 8, y + 18, x + cell - 8, y + 19, (alpha / 3) << 24 | 0xEBCB77);
            String full = Component.translatable("hud.noellesroles.decree.prohibited",
                    Component.translatable(field.decree.translationKey())).getString();
            int count = full.codePointCount(0, full.length());
            int reveal = Math.min(count, Math.max(0, field.age * 2));
            String text = full.substring(0, full.offsetByCodePoints(0, reveal));
            text = client.font.plainSubstrByWidth(text, cell - 16);
            if (alpha > 4) {
                g.drawString(client.font, text, x + (cell - client.font.width(text)) / 2, y,
                        alpha << 24 | 0xFFF4DC, false);
                String time = Component.translatable("hud.noellesroles.decree.remaining",
                        Math.max(0, (field.remaining + 19) / 20)).getString();
                g.drawString(client.font, time, x + (cell - client.font.width(time)) / 2, y + 10,
                        alpha << 24 | 0xE3C77B, false);
            }
        }
    }

    private static final class Field {
        private final UUID caster;
        private final Decree decree;
        private int remaining, age;
        private float opacity;
        private boolean impacted;

        private Field(UUID caster, Decree decree) {
            this.caster = caster;
            this.decree = decree;
        }
    }
}
