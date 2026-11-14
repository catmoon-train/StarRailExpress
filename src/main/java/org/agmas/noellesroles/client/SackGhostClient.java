package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.world.phys.EntityHitResult;
import org.agmas.noellesroles.role_data.killer.MaChenXuRoleData;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.item.GhostWardRelicItem;
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.SackGhostItems;
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.SackGhostPackets;
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.SackGhostRules;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 法器持有者私有HUD与红色透视；公开领域包不授予非持有者透视。 */
public final class SackGhostClient {
    public static final int RELIC_RED = 0xFF4747;
    private record RealmView(int remaining, int duration) {}
    private static final Map<UUID, RealmView> REALMS = new HashMap<>();
    private record FormView(int flags, int remaining) {}
    private static final Map<UUID, FormView> FORMS = new HashMap<>();
    private static int visualTicks;
    private static int veilRemaining;
    private static int veilTotal;
    private static ClientLevel level;
    private static Player viewer;
    private static boolean eligibleCarrier;
    private static boolean registered;
    private static ItemStack relic = ItemStack.EMPTY;
    private static UUID relicId;
    private static long relicSnapshot = -1;
    private static long relicClock;
    private SackGhostClient() {}

    public static boolean shouldReveal(Entity target) {
        Minecraft client = Minecraft.getInstance();
        return target instanceof Player && client.player != null && target != client.player && target.level() == client.level
                && GameUtils.isPlayerAliveAndSurvival((Player) target)
                && SackGhostRules.relicCanReveal(eligibleCarrier, REALMS.containsKey(target.getUUID()),
                        client.player.distanceToSqr(target));
    }

    public static boolean hasActiveRealm() { return !REALMS.isEmpty(); }
    public static int realmRemaining() { return REALMS.values().stream().mapToInt(RealmView::remaining).max().orElse(0); }
    public static int realmDuration() {
        return REALMS.values().stream().max(java.util.Comparator.comparingInt(RealmView::remaining))
                .map(RealmView::duration).orElse(600);
    }

    public static boolean playable() {
        Minecraft client = Minecraft.getInstance();
        return client.level != null && client.player != null
                && SREClient.gameComponent != null && SREClient.gameComponent.isRunning();
    }

    /** 与服务端战斗时钟一致：暂停技能、时停和暂停菜单均不消耗表现寿命。 */
    public static boolean clockRunning() {
        Minecraft client = Minecraft.getInstance();
        return playable() && !client.isPaused() && SREClient.gameComponent.isSkillAvailable
                && !SREGameTimeComponent.KEY.get(client.level).isTimeFrozen()
                && !client.level.tickRateManager().isFrozen() && !client.player.hasEffect(ModEffects.TIME_STOP);
    }

    public static float visualPartial(float partial) { return clockRunning() ? Math.clamp(partial, 0, 1) : 0; }
    public static float visualTime(float partial) { return visualTicks + visualPartial(partial); }

    static void useWorld(ClientLevel next) {
        Player current = Minecraft.getInstance().player;
        if (level != next || viewer != current) { clear(); level = next; viewer = current; }
    }

    public static int formFlags(Player player) {
        if (level != Minecraft.getInstance().level || !playable() || !GameUtils.isPlayerAliveAndSurvival(player)) return 0;
        FormView view = FORMS.get(player.getUUID());
        return view == null ? 0 : view.flags();
    }

    public static void register() {
        if (registered) return;
        registered = true;
        org.agmas.noellesroles.game.roles.killer.ma_chen_xu.SackGhostActions.setClientVeilProbe(() -> playable() && veilRemaining > 0);
        SackGhostEffects.register();
        EntityRendererRegistry.register(ModEntities.SACK_GHOST_EFFIGY, SackGhostEffigyRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(SackGhostPackets.FormS2C.ID, (packet, context) -> context.client().execute(() -> {
            useWorld(context.client().level);
            if (packet.flags() == 0 || packet.remaining() <= 0) FORMS.remove(packet.owner());
            else if (FORMS.size() < 128 || FORMS.containsKey(packet.owner()))
                FORMS.put(packet.owner(), new FormView(packet.flags() & 7, Math.clamp(packet.remaining(), 1, 800)));
        }));
        ClientPlayNetworking.registerGlobalReceiver(SackGhostPackets.VeilS2C.ID, (packet, context) -> context.client().execute(() -> {
            useWorld(context.client().level);
            veilRemaining = Math.clamp(packet.remaining(), 0, 60);
            veilTotal = 60;
        }));
        ClientPlayNetworking.registerGlobalReceiver(SackGhostPackets.RealmS2C.ID, (packet, context) -> context.client().execute(() -> {
            useWorld(context.client().level);
            if (packet.remaining() <= 0) REALMS.remove(packet.owner());
            else if (REALMS.size() < 128 || REALMS.containsKey(packet.owner()))
                REALMS.put(packet.owner(), new RealmView(Math.clamp(packet.remaining(), 0, 800), Math.clamp(packet.duration(), 1, 800)));
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            useWorld(client.level);
            if (!playable()) { clear(); return; }
            if (!GameUtils.isPlayerAliveAndSurvival(client.player)) veilRemaining = 0;
            FORMS.entrySet().removeIf(entry -> {
                Player owner = client.level.getPlayerByUUID(entry.getKey());
                return owner != null && !GameUtils.isPlayerAliveAndSurvival(owner);
            });
            SackGhostEffects.tick();
            relic = ItemStack.EMPTY;
            for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
                ItemStack stack = client.player.getInventory().getItem(i);
                var tag = SackGhostItems.tag(stack);
                if (stack.getItem() instanceof GhostWardRelicItem && stack.getCount() == 1 && tag.hasUUID("Relic") && tag.hasUUID("Round")) { relic = stack; break; }
            }
            eligibleCarrier = !relic.isEmpty() && GameUtils.isPlayerAliveAndSurvival(client.player) && SackGhostItems.eligibleRelicCarrier(client.player);
            var state = SackGhostItems.tag(relic);
            UUID id = state.hasUUID("Relic") ? state.getUUID("Relic") : null;
            long snapshot = state.getLong("Clock");
            if (!java.util.Objects.equals(id, relicId) || snapshot != relicSnapshot) {
                relicId = id;
                relicSnapshot = snapshot;
                relicClock = snapshot;
            }
            if (clockRunning()) {
                visualTicks++;
                if (veilRemaining > 0) veilRemaining--;
                FORMS.replaceAll((owner, view) -> new FormView(view.flags(), Math.max(0, view.remaining() - 1)));
                FORMS.values().removeIf(view -> view.remaining() <= 0);
                relicClock++;
                REALMS.replaceAll((realmId, view) -> new RealmView(Math.max(0, view.remaining() - 1), view.duration()));
                REALMS.values().removeIf(view -> view.remaining() <= 0);
            }
        });
        HudRenderCallback.EVENT.register((graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (playable()) SackGhostEffects.renderVeil(graphics, veilRemaining, veilTotal, delta.getGameTimeDeltaPartialTick(false));
            if (!eligibleCarrier || client.player == null || client.options.hideGui) return;
            int y = Math.max(8, graphics.guiHeight() - 102);
            int width = Math.min(234, graphics.guiWidth() - 16);
            graphics.fill(6, y - 5, 6 + width, y + 53, 0xBE101C1A);
            var tag = SackGhostItems.tag(relic);
            int ward = SackGhostRules.remaining(relicClock, tag.getLong("WardReadyAt"));
            int seal = SackGhostRules.remaining(relicClock, tag.getLong("BreakReadyAt"));
            graphics.drawString(client.font, Component.translatable("hud.noellesroles.relic.title"), 12, y, 0xFFADE8CA);
            graphics.drawString(client.font, Component.translatable("hud.noellesroles.relic.ward", (ward + 19) / 20), 12, y + 13, 0xFFE1E8D8);
            graphics.drawString(client.font, Component.translatable("hud.noellesroles.relic.break", (seal + 19) / 20, SackGhostRules.RELIC_BREAK_COST), 12, y + 26, 0xFFF0BC91);
            if (client.player.hasEffect(ModEffects.GHOST_WARD)) graphics.drawString(client.font, Component.translatable("hud.noellesroles.relic.protected"), 12, y + 39, 0xFF81E2B5);
        });
    }

    /** 四阶段成长、实物储备、独立冷却与匿名枪械预警。 */
    public static void renderRoleHud(FakeGuiGraphics gui) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui || !GameUtils.isPlayerAliveAndSurvival(client.player)) return;
        MaChenXuRoleData data = SackGhostItems.data(client.player);
        if (data == null || data.stage <= 0) return;
        int x = 12, y = Math.max(8, gui.guiHeight() - 204);
        int width = Math.min(300, gui.guiWidth() - 20);
        gui.fill(x - 6, y - 6, x + width, y + 180, 0xBD140D1D);
        gui.fill(x - 6, y - 6, x - 4, y + 180, 0xFF9A3454);
        gui.drawString(client.font, Component.translatable("hud.noellesroles.ma_chen_xu.phase",
                Component.translatable("hud.noellesroles.ma_chen_xu.phase" + data.stage)), x, y, 0xFFE7B3E5);
        int next = data.stage >= 4 ? 0 : SackGhostRules.stageThreshold(data.stage + 1, data.initialPlayers);
        gui.drawString(client.font, Component.translatable(next > 0 ? "hud.noellesroles.ma_chen_xu.evolution_progress"
                : "hud.noellesroles.ma_chen_xu.total_san_loss", data.totalSanLoss, next), x, y + 13, 0xFFE4CFB0);
        int start = data.stage <= 1 ? 0 : SackGhostRules.stageThreshold(data.stage, data.initialPlayers);
        float progress = next <= 0 ? 1 : Math.clamp((data.totalSanLoss - start) / (float) Math.max(1, next - start), 0, 1);
        gui.fill(x, y + 25, x + width - 12, y + 28, 0xFF332336);
        gui.fill(x, y + 25, x + (int) ((width - 12) * progress), y + 28, 0xFFB44865);
        gui.drawString(client.font, Component.translatable("hud.noellesroles.sack.stock", SackGhostItems.artCount(client.player), data.ghostSkills.size()), x, y + 34, 0xFFE1D6E8);
        gui.drawString(client.font, Component.translatable("hud.noellesroles.sack.draw", NoellesrolesClient.abilityBind.getTranslatedKeyMessage(),
                (data.generationCooldown + 19) / 20), x, y + 47, 0xFFC3B8D0);
        Component realm = data.otherworldActive ? Component.translatable("hud.noellesroles.sack.realm_active", (data.otherworldDuration + 19) / 20, data.verdicts.size())
                : data.stage < 3 ? Component.translatable("message.noellesroles.sack.realm_locked")
                : Component.translatable("hud.noellesroles.sack.realm_ready", client.options.keyShift.getTranslatedKeyMessage(),
                        NoellesrolesClient.abilityBind.getTranslatedKeyMessage(), (data.ultimateCooldown + 19) / 20);
        gui.drawString(client.font, realm, x, y + 60, data.otherworldActive ? 0xFFFF9A97 : 0xFFC3B8D0);
        gui.drawString(client.font, Component.translatable("hud.noellesroles.sack.cast", (data.castCooldown + 19) / 20), x, y + 73, 0xFFAAA1B3);
        gui.drawString(client.font, Component.translatable(data.escapeTicks > 0 ? "hud.noellesroles.sack.escape_active" : "hud.noellesroles.sack.escape",
                ((data.escapeTicks > 0 ? data.escapeTicks : data.escapeCooldown) + 19) / 20), x, y + 86, data.escapeTicks > 0 ? 0xFFFF7777 : 0xFFC3B8D0);
        gui.drawString(client.font, Component.translatable(data.shroudTicks > 0 ? "hud.noellesroles.sack.shroud_active" : "hud.noellesroles.sack.shroud",
                ((data.shroudTicks > 0 ? data.shroudTicks : data.shroudCooldown) + 19) / 20), x, y + 99, data.shroudTicks > 0 ? 0xFFBFB3EB : 0xFFC3B8D0);
        if (data.echoTicks > 0) gui.drawString(client.font, Component.translatable("hud.noellesroles.sack.echo_return", (data.echoTicks + 19) / 20), x, y + 112, 0xFFFFBC9E);
        if (data.threat > 0) {
            String warning = data.bodyThreat ? data.threat >= 2 ? "body_held" : "body_threat" : "threat";
            gui.drawString(client.font, Component.translatable("hud.noellesroles.sack." + warning), x, y + 127, 0xFFFF5A5A);
            if (data.threat >= 2) {
                gui.pose().pushPose();
                gui.pose().translate(x + width - 28, y + 139, 0);
                gui.pose().scale(2.5F, 2.5F, 1);
                gui.drawString(client.font, Component.translatable("hud.noellesroles.sack.danger"), 0, 0, 0xFFFF2020);
                gui.pose().popPose();
            }
        }
        if (client.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player target && data.isEnemy(target)) {
            int san = (int) Math.ceil(SREPlayerMoodComponent.KEY.get(target).getMood() * 100);
            String state = data.otherworldActive ? data.verdicts.contains(target.getUUID()) ? "marked" : "strike"
                    : san <= 15 ? "devour" : "need_san";
            gui.drawString(client.font, Component.translatable("hud.noellesroles.sack.target", san,
                    Component.translatable("hud.noellesroles.sack." + state)), x, y + 146, 0xFFFFBC9E);
        }
        if (client.player.hasEffect(ModEffects.SKILL_BANED) || client.player.hasEffect(ModEffects.USED_BANED)) {
            gui.drawString(client.font, Component.translatable("message.tip.cant_use_skill"), x, y + 163, 0xFFFF7777);
        }
    }

    private static void clear() {
        REALMS.clear(); eligibleCarrier = false; relic = ItemStack.EMPTY; level = null; viewer = null;
        relicId = null; relicSnapshot = -1; relicClock = 0;
        FORMS.clear(); visualTicks = 0; veilRemaining = veilTotal = 0;
        SackGhostEffects.clear();
    }
}
