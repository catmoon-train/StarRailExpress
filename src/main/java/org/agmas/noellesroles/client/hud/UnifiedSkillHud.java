package org.agmas.noellesroles.client.hud;

import io.wifi.starrailexpress.api.RolePassive;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;

import java.util.List;

/**
 * Modern skill HUD for roles using the unified skill registry.
 *
 * Design principles:
 * - Compact card-style layout with subtle translucent backgrounds
 * - No usage count (castCount) shown
 * - "还可使用X次" only shown when the skill has a usage cap (maxCharges > 0)
 * - Respects the {@code showOnHud} flag on each skill definition
 * - Selected skill highlighted with a bright indicator dot
 */
public final class UnifiedSkillHud {
    private UnifiedSkillHud() {
    }

    // ── Colour palette ──────────────────────────────────────────────
    private static final int BG_READY = 0x40104018; // translucent green
    private static final int BG_COOLDOWN = 0x40402020; // translucent red
    private static final int BG_EMPTY = 0x30202020; // translucent grey
    private static final int FG_READY = 0xFF55FF55;
    private static final int FG_COOLDOWN = 0xFFFF7755;
    private static final int FG_EMPTY = 0xFF888888;
    private static final int FG_MUTED = 0xFFAAAAAA;
    private static final int INDICATOR_ON = 0xFF40E840;
    private static final int INDICATOR_OFF = 0xFF555555;
    private static final int PASSIVE_FG = 0xFF9A9ACD;
    private static final int CHARGE_FG = 0xFFE0C850;

    // ── Layout constants ────────────────────────────────────────────
    private static final int PAD = 4;
    private static final int CARD_H = 14;
    private static final int GAP = 2;
    private static final int DOT_R = 2;

    public static void register() {
        CommonHudRenderCallback.EVENT.register((graphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.screen != null || SREClient.gameComponent == null) {
                return;
            }
            var role = SREClient.getCachedPlayerRole();
            // 布袋鬼有自绘的鬼术 HUD，跳过
            if (role != null && role.identifier().equals(org.agmas.noellesroles.role.ModRoles.MA_CHEN_XU_ID)) {
                return;
            }
            // 小镇做题家有自绘的 HUD，跳过
            if (role != null && role.identifier().equals(org.agmas.noellesroles.role.ModRoles.EXAMPLER_ID)) {
                return;
            }
            List<RoleSkill.Definition> skills = RoleSkill.getDefinitions(role);
            List<RolePassive.Definition> passives = RolePassive.getDefinitions(role);
            if (skills.isEmpty() && passives.isEmpty()) {
                return;
            }

            // Filter visible skills
            List<RoleSkill.Definition> visible = skills.stream()
                    .filter(RoleSkill.Definition::showOnHud)
                    .toList();
            if (visible.isEmpty() && passives.isEmpty()) {
                return;
            }

            var ability = SREAbilityPlayerComponent.KEY.get(client.player);
            ability.ensureSkills(skills);

            int screenWidth = graphics.guiWidth();
            int screenHeight = graphics.guiHeight();

            // Build text lines first to measure width
            int cardCount = visible.size() + (passives.isEmpty() ? 0 : 1);
            int maxTextW = 0;
            int[] textWidths = new int[cardCount];
            Component[] lines = new Component[cardCount];
            int[] colors = new int[cardCount];
            int[] bgColors = new int[cardCount];

            int idx = 0;
            int selectedIdx = -1;

            // Skill cards
            for (int i = 0; i < visible.size(); i++) {
                RoleSkill.Definition skill = visible.get(i);
                SREAbilityPlayerComponent.SkillState state = ability.getSkillState(skill.id());
                boolean selected = skill.equals(visible.get(Math.min(ability.getSelectedSkill(), visible.size() - 1)));
                if (selected)
                    selectedIdx = idx;

                Component stateText;
                boolean isReady = false;
                int color;
                int bg;
                if (state.cooldown > 0) {
                    stateText = Component.translatable("hud.sre.skill.cooldown",
                            String.format("%.1f", state.cooldown / 20.0F));
                    color = FG_COOLDOWN;
                    bg = BG_COOLDOWN;
                } else if (state.charges == 0) {
                    stateText = Component.translatable("hud.sre.skill.empty");
                    color = FG_EMPTY;
                    bg = BG_EMPTY;
                } else {
                    stateText = Component.translatable("hud.sre.skill.ready");
                    color = FG_READY;
                    bg = BG_READY;
                    isReady = true;
                }

                Component line = Component.translatable(skill.nameKey())
                        .append(Component.literal("  "))
                        .append(stateText);

                // Only show charge info when there's a usage cap
                if (state.maxCharges > 0) {
                    line = line.copy().append(Component.literal("  "))
                            .append(Component.translatable("hud.sre.skill.charges_remaining", state.charges)
                                    .withStyle(s -> s.withColor(CHARGE_FG)));
                }
                if (skill.shifted() && isReady) {
                    line = line.copy()
                            .append(Component.translatable("hud.sre.skill.shift_to_use"))
                            .withStyle(ChatFormatting.GRAY);
                }

                lines[idx] = line;
                colors[idx] = selected ? color : FG_MUTED;
                bgColors[idx] = bg;
                textWidths[idx] = client.font.width(line);
                if (textWidths[idx] > maxTextW)
                    maxTextW = textWidths[idx];
                idx++;
            }

            // Passive line
            if (!passives.isEmpty()) {
                Component passiveLine = Component.translatable("hud.sre.passive_label");
                for (int i = 0; i < passives.size(); i++) {
                    passiveLine = passiveLine.copy().append(Component.translatable(passives.get(i).nameKey()));
                    if (i < passives.size() - 1) {
                        passiveLine = passiveLine.copy().append(Component.literal(" · "));
                    }
                }
                lines[idx] = passiveLine;
                colors[idx] = PASSIVE_FG;
                bgColors[idx] = 0x30181838;
                textWidths[idx] = client.font.width(passiveLine);
                if (textWidths[idx] > maxTextW)
                    maxTextW = textWidths[idx];
            }

            // Rendering — anchored to bottom-right
            int cardW = maxTextW + PAD * 2 + DOT_R * 2 + 4;
            int totalH = cardCount * CARD_H + (cardCount - 1) * GAP;
            int baseX = screenWidth - cardW - 6;
            int baseY = screenHeight - totalH - 20;

            for (int c = 0; c < cardCount; c++) {
                int cx = baseX;
                int cy = baseY + c * (CARD_H + GAP);

                // Card background
                graphics.fill(cx, cy, cx + cardW, cy + CARD_H, bgColors[c]);

                // Selection indicator dot
                int dotX = cx + PAD + DOT_R;
                int dotY = cy + CARD_H / 2;
                int dotColor = (c == selectedIdx) ? INDICATOR_ON : INDICATOR_OFF;
                fillCircle(graphics, dotX, dotY, DOT_R, dotColor);

                // Text
                int textX = cx + PAD + DOT_R * 2 + 4;
                int textY = cy + (CARD_H - client.font.lineHeight) / 2 + 1;
                graphics.drawString(client.font, lines[c], textX, textY, colors[c], false);
            }
        });
    }

    /** Draw a small filled circle. */
    private static void fillCircle(FakeGuiGraphics graphics, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dxMax = (int) Math.sqrt(r * r - dy * dy);
            graphics.fill(cx - dxMax, cy + dy, cx + dxMax + 1, cy + dy + 1, color);
        }
    }
}
