package org.agmas.noellesroles.client.hud;

import io.wifi.starrailexpress.api.RepairRole;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.component.RepairRolePlayerComponent;
import net.exmo.sre.repair.state.RepairModeState;
import net.exmo.sre.repair.role.RepairRoleDefinition;
import org.agmas.noellesroles.init.ModBlocks;

import java.util.ArrayList;
import java.util.List;

public final class RepairEscapeHud {
    private static final ResourceLocation PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "starrailexpress", "textures/gui/waiting_inventory.png");
    private static final int PANEL_SRC_W = 176;
    private static final int PANEL_SRC_H = 166;
    private static final int PANEL_TEX_SIZE = 256;
    private static final int MAIN_W = 150;
    private static final int MAIN_H = 58;
    private static final int EVENT_W = 160;
    private static final int EVENT_H = 28;

    private static final List<CoinToast> coinToasts = new ArrayList<>();
    private static final List<CombatCue> combatCues = new ArrayList<>();
    // 客户端提示淡出计时
    private static String lastLockPromptKey = "";
    private static long lockPromptSetTick = 0L;
    private static BlockPos lastSearchHintPos = null;
    private static long searchHintStartTick = 0L;

    private RepairEscapeHud() {
    }

    public static void register() {
        CommonHudRenderCallback.EVENT.register((graphics, deltaTracker) -> render(graphics));
    }

    private static void render(FakeGuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || SREClient.gameComponent == null
                || !SREClient.gameComponent.isRunning()
                || SREClient.gameComponent.getGameMode() != SREGameModes.REPAIR_ESCAPE_MODE
                || !isRepairRole()) {
            return;
        }

        LocalPlayer player = client.player;
        RepairRolePlayerComponent component = ModComponents.REPAIR_ROLES.get(player);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        long tick = client.level.getGameTime();

        renderMainPanel(graphics, player, component, 8, Math.max(8, height - MAIN_H - 50), tick);
        renderEventPanel(graphics, component, Mth.clamp(width / 2 - EVENT_W / 2, 8, width - EVENT_W - 8), 8);
        renderSearchAndLockPrompt(graphics, player, component, width, height, tick);
        renderRepairInjuryEdges(graphics, component, width, height, tick);
        renderCombatCues(graphics, width, height, tick);
        renderCoinToasts(graphics, width, height, tick);
    }

    public static void pushCoinToast(int amount, String sourceKey) {
        Minecraft client = Minecraft.getInstance();
        long tick = client.level == null ? 0L : client.level.getGameTime();
        coinToasts.add(new CoinToast(amount, Component.translatable(sourceKey), tick));
        if (coinToasts.size() > 4) {
            coinToasts.remove(0);
        }
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.35F, 0.28F));
    }

    public static void pushCombatCue(int kind, int entityId, double x, double y, double z, String weaponId) {
        Minecraft client = Minecraft.getInstance();
        long tick = client.level == null ? 0L : client.level.getGameTime();
        String weapon = weaponId == null ? "" : weaponId;
        combatCues.add(new CombatCue(kind, entityId, x, y, z, weapon, tick));
        if (combatCues.size() > 8) {
            combatCues.remove(0);
        }
        if (kind == org.agmas.noellesroles.packet.RepairCombatFeedbackS2CPacket.ATTACK && client.level != null
                && client.level.getEntity(entityId) instanceof LivingEntity attacker) {
            attacker.swing(InteractionHand.MAIN_HAND);
            var particle = "hammer".equals(weapon) ? net.minecraft.core.particles.ParticleTypes.CRIT
                    : "hook".equals(weapon) ? net.minecraft.core.particles.ParticleTypes.SCULK_SOUL
                    : net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK;
            client.level.addParticle(particle, x, y, z, 0.0D, 0.03D, 0.0D);
        }
        if (kind == org.agmas.noellesroles.packet.RepairCombatFeedbackS2CPacket.HIT
                || kind == org.agmas.noellesroles.packet.RepairCombatFeedbackS2CPacket.DOWNED) {
            client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_ATTACK_CRIT, 0.85F, 0.22F));
        }
    }

    private static boolean isRepairRole() {
        var role = SREClient.getCachedPlayerRole();
        return role instanceof RepairRole;
    }

    private static void renderMainPanel(FakeGuiGraphics graphics, LocalPlayer player,
            RepairRolePlayerComponent component, int x, int y, long tick) {
        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        int accent = roleAccent(component.activeRole);
        drawWoodPanel(graphics, x, y, MAIN_W, MAIN_H, accent);

        Component repair = Component.translatable("hud.noellesroles.repair.stations",
                component.completedStations, RepairModeState.REQUIRED_REPAIRED_STATIONS);
        graphics.drawString(font, repair, x + 9, y + 7, 0xFFFFE6A3);
        graphics.drawString(font, component.gatesPowered
                        ? Component.translatable("hud.noellesroles.repair.gates_powered")
                        : Component.translatable("hud.noellesroles.repair.gates_unpowered"),
                x + 83, y + 7, component.gatesPowered ? 0xFF9FE6A0 : 0xFFFFC857);

        float stationPct = Mth.clamp(component.completedStations / (float) RepairModeState.REQUIRED_REPAIRED_STATIONS,
                0.0F, 1.0F);
        drawPixelBar(graphics, x + 9, y + 18, 132, 5, stationPct, 0xFF241006,
                component.gatesPowered ? 0xFF8DCC7D : 0xFFE9C46A);

        Component pressure = Component.translatable("hud.noellesroles.repair.pressure",
                component.downedAllies, component.activeTrialPrisoners);
        drawFitted(graphics, font, pressure, x + 9, y + 28, 70, 0xFFF2C88B);
        drawFitted(graphics, font, injuryText(component), x + 83, y + 28, 58, injuryColor(component));

        int cooldown = skillCooldown(component, tick);
        int weaponCooldown = weaponCooldown(component, tick);
        Component skill = Component.translatable("hud.noellesroles.repair.skill_line",
                activeSkillName(component.activeRole), cooldown <= 0 ? "OK" : cooldown + "s");
        drawFitted(graphics, font, skill, x + 9, y + 38, 78, cooldown <= 0 ? 0xFFB9F6CA : 0xFFFFB36B);
        drawFitted(graphics, font, Component.translatable("hud.noellesroles.repair.weapon_cooldown_line",
                weaponCooldown <= 0 ? "OK" : weaponCooldown + "s"), x + 89, y + 38, 52,
                weaponCooldown <= 0 ? 0xFFB9F6CA : 0xFFFFB36B);

        Component subSkill = subSkillLine(component);
        drawFitted(graphics, font, subSkill, x + 9, y + 48, 132, 0xFFFFE8A3);

        if (component.nearestTrialProgress > 0) {
            int seconds = Math.max(0, (RepairModeState.TRIAL_EXECUTION_TICKS - component.nearestTrialProgress) / 20);
            drawFitted(graphics, font, Component.translatable("hud.noellesroles.repair.self_trial", seconds),
                    x + 89, y + 28, 52, 0xFFFF8A80);
        } else if (RepairRoleDefinition.byId(component.activeRole)
                .map(role -> role.faction == RepairRoleDefinition.Faction.NEUTRAL).orElse(false)) {
            int needed = component.neutralTaskNeeded > 0 ? component.neutralTaskNeeded : neutralGoal(component.activeRole);
            drawFitted(graphics, font, Component.translatable("hud.noellesroles.repair.neutral_task",
                    component.neutralTaskProgress, needed), x + 89, y + 28, 52, 0xFFFFE8A3);
        }
    }

    private static void renderEventPanel(FakeGuiGraphics graphics, RepairRolePlayerComponent component, int x, int y) {
        if (component.currentEventKey == null || component.currentEventKey.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        int danger = Mth.clamp(component.currentEventDanger, 0, 100);
        int accent = danger >= 70 ? 0xFFE85D4F : danger >= 45 ? 0xFFFFC857 : 0xFFA7C957;
        drawWoodPanel(graphics, x, y, EVENT_W, EVENT_H, accent);
        drawFitted(graphics, client.font, Component.translatable(component.currentEventKey), x + 8, y + 6,
                EVENT_W - 54, 0xFFFFE6A3);
        graphics.drawString(client.font, Component.literal(Math.max(0, component.currentEventTicks / 20) + "s"),
                x + EVENT_W - 30, y + 6, 0xFFFFC857);
        drawPixelBar(graphics, x + 8, y + 18, EVENT_W - 16, 4, danger / 100.0F, 0xFF251007, accent);
    }

    private static void renderSearchAndLockPrompt(FakeGuiGraphics graphics, LocalPlayer player,
            RepairRolePlayerComponent component, int width, int height, long tick) {
        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        int centerX = width / 2;
        int centerY = height / 2;
        if (component.searchTarget.present() && component.searchTotalTicks > 0) {
            float pct = Mth.clamp((tick - component.searchStartTick) / (float) component.searchTotalTicks, 0.0F, 1.0F);
            drawPixelBar(graphics, centerX - 42, centerY - 31, 84, 6, pct, 0xDD1A0C05, 0xFFFFC857);
            drawFitted(graphics, font, Component.translatable(component.searchPromptKey), centerX - 44,
                    centerY - 43, 88, 0xFFFFE6A3);
            return;
        }
        // 锁定提示：带4秒开始淡出、5秒完全消失
        if (component.lockPromptKey != null && !component.lockPromptKey.isEmpty()) {
            if (!component.lockPromptKey.equals(lastLockPromptKey)) {
                lastLockPromptKey = component.lockPromptKey;
                lockPromptSetTick = tick;
            }
            long age = tick - lockPromptSetTick;
            int alpha = age >= 100 ? 0 : age >= 80 ? (int) ((100 - age) / 20.0F * 255) : 255;
            if (alpha > 0) {
                int color = (alpha << 24) | 0xFF8A80;
                drawFitted(graphics, font, Component.literal(component.lockPromptKey), centerX - 64, centerY + 20,
                        128, color);
            }
            return;
        } else {
            lastLockPromptKey = "";
        }
        // 准心指向可搜索柜子提示：带4秒开始淡出、5秒完全消失
        if (client.hitResult instanceof BlockHitResult blockHit && client.hitResult.getType() == HitResult.Type.BLOCK
                && client.level != null && client.level.getBlockState(blockHit.getBlockPos()).is(ModBlocks.HOTBAR_STORAGE)
                && player.distanceToSqr(blockHit.getBlockPos().getCenter()) <= 4.8D * 4.8D) {
            BlockPos currentPos = blockHit.getBlockPos();
            if (!currentPos.equals(lastSearchHintPos)) {
                lastSearchHintPos = currentPos;
                searchHintStartTick = tick;
            }
            long age = tick - searchHintStartTick;
            int alpha = age >= 100 ? 0 : age >= 80 ? (int) ((100 - age) / 20.0F * 255) : 255;
            if (alpha > 0) {
                int color = (alpha << 24) | 0xFFE6A3;
                drawFitted(graphics, font, Component.translatable("hud.noellesroles.repair.search_hint"),
                        centerX + 10, centerY + 8, 104, color);
            }
        } else {
            lastSearchHintPos = null;
        }
    }

    private static void renderRepairInjuryEdges(FakeGuiGraphics graphics, RepairRolePlayerComponent component,
            int width, int height, long tick) {
        if (component.repairInjuryLevel <= 0 || component.downed) {
            return;
        }
        float pulse = 0.55F + 0.25F * Mth.sin(tick * 0.22F);
        int alpha = (int) (pulse * 105.0F);
        int color = (alpha << 24) | 0xAA0F08;
        int edgeW = Math.max(10, width / 13);
        int edgeH = Math.max(10, height / 11);
        graphics.fill(0, 0, width, 3, color);
        graphics.fill(0, height - 3, width, height, color);
        graphics.fill(0, 0, 3, height, color);
        graphics.fill(width - 3, 0, width, height, color);
        for (int i = 0; i < edgeW; i += 2) {
            int a = (int) (alpha * (1.0F - i / (float) edgeW) * 0.42F);
            graphics.fill(i, 0, i + 1, height, (a << 24) | 0xAA0F08);
            graphics.fill(width - i - 1, 0, width - i, height, (a << 24) | 0xAA0F08);
        }
        for (int i = 0; i < edgeH; i += 2) {
            int a = (int) (alpha * (1.0F - i / (float) edgeH) * 0.34F);
            graphics.fill(0, i, width, i + 1, (a << 24) | 0xAA0F08);
            graphics.fill(0, height - i - 1, width, height - i, (a << 24) | 0xAA0F08);
        }
    }

    private static int skillCooldown(RepairRolePlayerComponent component, long tick) {
        long remaining = component.activeSkillCooldownEndTick - tick;
        return remaining <= 0L ? 0 : (int) Math.ceil(remaining / 20.0D);
    }

    private static int weaponCooldown(RepairRolePlayerComponent component, long tick) {
        long remaining = component.hunterWeaponCooldownEndTick - tick;
        return remaining <= 0L ? 0 : (int) Math.ceil(remaining / 20.0D);
    }

    private static Component injuryText(RepairRolePlayerComponent component) {
        if (component.trialStand.present()) return Component.translatable("hud.noellesroles.repair.injury.trial");
        if (component.carriedBy != null) return Component.translatable("hud.noellesroles.repair.injury.carried");
        if (component.downed) return Component.translatable("hud.noellesroles.repair.injury.downed");
        return Component.translatable(switch (Mth.clamp(component.repairInjuryLevel, 0, 3)) {
            case 0 -> "hud.noellesroles.repair.injury.healthy";
            case 1 -> "hud.noellesroles.repair.injury.light";
            case 2 -> "hud.noellesroles.repair.injury.heavy";
            default -> "hud.noellesroles.repair.injury.critical";
        });
    }

    private static int injuryColor(RepairRolePlayerComponent component) {
        if (component.trialStand.present() || component.carriedBy != null || component.downed) return 0xFFFF8A80;
        return switch (Mth.clamp(component.repairInjuryLevel, 0, 3)) {
            case 0 -> 0xFFB9F6CA;
            case 1 -> 0xFFFFE082;
            case 2 -> 0xFFFFB36B;
            default -> 0xFFFF8A80;
        };
    }

    private static Component activeSkillName(String activeRole) {
        if (activeRole == null || activeRole.isEmpty()) {
            return Component.translatable("hud.noellesroles.repair.active_skill.selecting");
        }
        return Component.translatable("hud.noellesroles.repair.active_skill." + activeRole);
    }

    private static Component subSkillLine(RepairRolePlayerComponent component) {
        if (component.downed && component.carriedBy == null) {
            // 倒地挣扎进度文字化显示
            int progress = Mth.clamp(component.downedStruggleProgress, 0, 100);
            return Component.translatable("hud.noellesroles.repair.downed_struggle_line", progress);
        }
        if (component.carriedBy != null) {
            // 搬运挣扎进度
            int progress = Mth.clamp(component.carryStruggleProgress, 0, 100);
            return Component.translatable("hud.noellesroles.repair.struggle_line", progress);
        }
        if (component.carrying != null) {
            return Component.translatable("hud.noellesroles.repair.drop_line");
        }
        if (component.activeAttackPlugin != null && !component.activeAttackPlugin.isEmpty()) {
            return Component.translatable("hud.noellesroles.repair.plugin_line",
                    Component.translatable("item.noellesroles.hunter_plugin_" + component.activeAttackPlugin));
        }
        if (component.selectedSkillState != null && !component.selectedSkillState.isEmpty()) {
            return Component.translatable("hud.noellesroles.repair.state_line",
                    Component.translatable("hud.noellesroles.repair.skill_state." + component.selectedSkillState));
        }
        return Component.translatable("hud.noellesroles.repair.item_skill_empty");
    }

    private static int neutralGoal(String activeRole) {
        return switch (activeRole) {
            case "archivist" -> RepairModeState.ARCHIVIST_TASK_NEEDED;
            case "saboteur" -> RepairModeState.SABOTEUR_TASK_NEEDED;
            case "collector" -> RepairModeState.COLLECTOR_TASK_NEEDED;
            default -> 0;
        };
    }

    private static int roleAccent(String activeRole) {
        return RepairRoleDefinition.byId(activeRole).map(role -> switch (role.faction) {
            case SURVIVOR -> 0xFF7BC4A6;
            case HUNTER -> 0xFFE85D4F;
            case NEUTRAL -> 0xFFFFC857;
        }).orElse(0xFFE9C46A);
    }

    private static void renderCombatCues(FakeGuiGraphics graphics, int width, int height, long tick) {
        combatCues.removeIf(cue -> tick - cue.createdTick > 20);
        int centerX = width / 2;
        int centerY = height / 2;
        for (CombatCue cue : List.copyOf(combatCues)) {
            long age = tick - cue.createdTick;
            float pct = Mth.clamp(age / 20.0F, 0.0F, 1.0F);
            int alpha = (int) ((1.0F - pct) * 145.0F);
            int cueColor = cue.kind == org.agmas.noellesroles.packet.RepairCombatFeedbackS2CPacket.DOWNED
                    ? 0xE85D4F : 0xE9C46A;
            int color = (alpha << 24) | cueColor;
            int radius = 9 + (int) (pct * 18.0F);
            graphics.fill(centerX - radius, centerY - 14, centerX + radius, centerY - 12, color);
            graphics.fill(centerX - radius, centerY + 12, centerX + radius, centerY + 14, color);
            graphics.fill(centerX - radius, centerY - 14, centerX - radius + 2, centerY + 14, color);
            graphics.fill(centerX + radius - 2, centerY - 14, centerX + radius, centerY + 14, color);
        }
    }

    private static void renderCoinToasts(FakeGuiGraphics graphics, int width, int height, long tick) {
        Minecraft client = Minecraft.getInstance();
        coinToasts.removeIf(toast -> tick - toast.createdTick > 58);
        int index = 0;
        for (CoinToast toast : List.copyOf(coinToasts)) {
            long age = tick - toast.createdTick;
            float alpha = Mth.clamp(age / 8.0F, 0.0F, 1.0F) * Mth.clamp((58 - age) / 12.0F, 0.0F, 1.0F);
            int a = (int) (alpha * 225.0F);
            int toastWidth = 126;
            int x = width - toastWidth - 8;
            int y = height / 4 + index * 20;
            graphics.fill(x, y, x + toastWidth, y + 17, ((int) (alpha * 185.0F) << 24) | 0x1A0C05);
            graphics.fill(x, y, x + toastWidth, y + 2, (a << 24) | 0xD8A442);
            graphics.drawString(client.font, Component.literal("+" + toast.amount).withStyle(ChatFormatting.GOLD),
                    x + 6, y + 5, (a << 24) | 0xFFE6A3);
            drawFitted(graphics, client.font, toast.source, x + 34, y + 5, toastWidth - 40,
                    (a << 24) | 0xFFF0DCA8);
            index++;
        }
    }

    private static void drawWoodPanel(FakeGuiGraphics graphics, int x, int y, int width, int height, int accent) {
        graphics.fill(x + 2, y + 3, x + width + 2, y + height + 3, 0x66000000);
        graphics.blit(PANEL_TEXTURE, x, y, width, height, 0.0F, 0.0F, PANEL_SRC_W, PANEL_SRC_H,
                PANEL_TEX_SIZE, PANEL_TEX_SIZE);
        graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, 0x641D0D05);
        int gold = (accent & 0x00FFFFFF) | 0xFF000000;
        graphics.fill(x, y, x + width, y + 1, 0xFFD8A442);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF6B3E16);
        graphics.fill(x, y, x + 1, y + height, 0xFFD8A442);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF6B3E16);
        graphics.fill(x + 5, y + 4, x + 20, y + 5, gold);
        graphics.fill(x + 5, y + 4, x + 6, y + 17, gold);
        graphics.fill(x + width - 20, y + height - 5, x + width - 5, y + height - 4, gold);
        graphics.fill(x + width - 6, y + height - 17, x + width - 5, y + height - 4, gold);
    }

    private static void drawPixelBar(FakeGuiGraphics graphics, int x, int y, int width, int height, float pct,
            int backColor, int fillColor) {
        graphics.fill(x, y, x + width, y + height, 0xFF120804);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, backColor);
        int filled = (int) ((width - 2) * Mth.clamp(pct, 0.0F, 1.0F));
        graphics.fill(x + 1, y + 1, x + 1 + filled, y + height - 1, fillColor);
    }

    private static void drawFitted(FakeGuiGraphics graphics, Font font, Component text, int x, int y, int maxWidth,
            int color) {
        String value = text.getString();
        if (font.width(value) > maxWidth) {
            while (value.length() > 1 && font.width(value + "..") > maxWidth) {
                value = value.substring(0, value.length() - 1);
            }
            value = value + "..";
        }
        graphics.drawString(font, Component.literal(value), x, y, color);
    }

    private record CoinToast(int amount, Component source, long createdTick) {
    }

    private record CombatCue(int kind, int entityId, double x, double y, double z, String weaponId, long createdTick) {
    }
}
