package net.exmo.sre.sixtyseconds.client.screen;

import net.exmo.sre.sixtyseconds.logic.SixtySecondsNpcMenu;
import net.exmo.sre.sixtyseconds.network.NpcDialogueActionC2SPacket;
import net.exmo.sre.sixtyseconds.network.OpenNpcDialogueS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * NPC 对话菜单（客户端）：展示服务端算好的选项（闲聊 / 打听消息 / 交易 / 雇佣 / 偷窃 / 抢劫 / 离开），
 * 点选后发 {@link NpcDialogueActionC2SPacket} 由服务端重校验执行。
 * 结构照抄 {@link ShelterDoorScreen} 的选项行绘制，头部换成 NPC 名 + 变体 + 代币余额。
 * 风格遵循 {@code docs/ui_style.md}。
 */
public class NpcDialogueScreen extends Screen {
    // ── ui_style 色板 ─────────────────────────────────────────
    private static final int PANEL_TOP = 0xD81A1008;
    private static final int PANEL_BOTTOM = 0xD820140A;
    private static final int BORDER = 0xFF8B6914;
    private static final int DECO_LINE = 0x33FFE8C0;
    private static final int GOLD = 0xFFD4AF37;
    private static final int TEXT = 0xFFFFF4DC;
    private static final int MUTED = 0xFF9E8B6E;
    private static final int GREEN = 0xFF72C17B;
    private static final int RED = 0xFFE06B65;
    private static final int BLUE = 0xFF5EB7D8;
    private static final int ORANGE = 0xFFE0AD5B;
    private static final int PURPLE = 0xFFB18AE6;
    private static final int EDGE_IDLE = 0xFF5A4530;

    private static final int ROW_H = 32;
    private static final int ROW_GAP = 4;
    private static final int PAD = 12;
    /** 头部高度：标题 + NPC 名/变体/余额那一行。 */
    private static final int HEADER_H = 46;

    private final OpenNpcDialogueS2CPacket data;
    private final float[] hoverAnim;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int rowsTop;

    public NpcDialogueScreen(OpenNpcDialogueS2CPacket data) {
        super(Component.literal(data.npcName()));
        this.data = data;
        this.hoverAnim = new float[data.options().size()];
    }

    @Override
    protected void init() {
        panelW = (int) Mth.clamp(this.width * 0.55F, 300, 380);
        panelH = HEADER_H + data.options().size() * (ROW_H + ROW_GAP) + 24;
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        rowsTop = panelY + HEADER_H;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_TOP, PANEL_BOTTOM);
        g.renderOutline(panelX, panelY, panelW, panelH, BORDER);
        g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 2, DECO_LINE);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        // 头部：NPC 名（大） + 变体 + 余额
        g.drawCenteredString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD),
                this.width / 2, panelY + 12, GOLD);
        Component variant = Component.translatable(variantKey(data.variantId()));
        g.drawString(this.font, variant, panelX + PAD, panelY + 28, MUTED);
        Component funds = Component.translatable(
                "gui.noellesroles.sixty_seconds.npc.funds", data.tokens());
        g.drawString(this.font, funds,
                panelX + panelW - PAD - this.font.width(funds), panelY + 28, GOLD);

        for (int i = 0; i < data.options().size(); i++) {
            drawRow(g, i, mouseX, mouseY);
        }
    }

    private void drawRow(GuiGraphics g, int index, int mouseX, int mouseY) {
        OpenNpcDialogueS2CPacket.Option option = data.options().get(index);
        int x = panelX + PAD;
        int w = panelW - PAD * 2;
        int y = rowsTop + index * (ROW_H + ROW_GAP);
        boolean enabled = option.enabled();
        boolean hover = enabled && isInRect(mouseX, mouseY, x, y, w, ROW_H);

        // hover 平滑过渡（插值而非瞬变）
        float target = hover ? 1F : 0F;
        hoverAnim[index] += (target - hoverAnim[index]) * 0.22F;
        float t = hoverAnim[index];

        int base = enabled ? 0x66231A10 : 0x44140E08;
        g.fill(x, y, x + w, y + ROW_H, blendColors(base, 0x33FFFFFF, t * 0.5F));
        g.renderOutline(x, y, w, ROW_H, enabled ? blendColors(EDGE_IDLE, GOLD, t) : 0xFF3A2E20);
        g.fill(x + 1, y + 1, x + 4, y + ROW_H - 1, enabled ? accentColor(option.action()) : 0xFF4A4038);

        Component name = Component.translatable(titleKey(option.action()));
        Component desc = descFor(option, enabled);
        int nameColor = enabled ? blendColors(TEXT, GOLD, t) : 0xFF7A6F5C;
        g.drawString(this.font, name.copy().withStyle(ChatFormatting.BOLD), x + 10 + (int) (t * 3), y + 6,
                nameColor);
        g.drawString(this.font, desc, x + 10, y + 18, enabled ? MUTED : 0xFF6B5F4C);
    }

    private Component descFor(OpenNpcDialogueS2CPacket.Option option, boolean enabled) {
        return switch (option.action()) {
            case SixtySecondsNpcMenu.ACTION_TALK ->
                    Component.translatable("gui.noellesroles.sixty_seconds.npc.action.talk_desc");
            case SixtySecondsNpcMenu.ACTION_INTEL ->
                    Component.translatable("gui.noellesroles.sixty_seconds.npc.action.intel_desc");
            case SixtySecondsNpcMenu.ACTION_TRADE -> enabled
                    ? Component.translatable("gui.noellesroles.sixty_seconds.npc.action.trade_desc",
                            option.param())
                    : Component.translatable("gui.noellesroles.sixty_seconds.npc.action.trade_none");
            case SixtySecondsNpcMenu.ACTION_HIRE -> enabled
                    ? Component.translatable("gui.noellesroles.sixty_seconds.npc.action.hire_desc",
                            option.param())
                    : Component.translatable("gui.noellesroles.sixty_seconds.npc.action.hire_none",
                            option.param());
            case SixtySecondsNpcMenu.ACTION_STEAL ->
                    Component.translatable("gui.noellesroles.sixty_seconds.npc.action.steal_desc",
                            option.param());
            case SixtySecondsNpcMenu.ACTION_ROB ->
                    Component.translatable("gui.noellesroles.sixty_seconds.npc.action.rob_desc");
            case SixtySecondsNpcMenu.ACTION_SHOP_EDIT ->
                    Component.translatable("gui.noellesroles.sixty_seconds.npc.action.shop_edit_desc");
            case SixtySecondsNpcMenu.ACTION_LEAVE ->
                    Component.translatable("gui.noellesroles.sixty_seconds.npc.action.leave_desc");
            default -> Component.empty();
        };
    }

    private static String titleKey(int action) {
        return switch (action) {
            case SixtySecondsNpcMenu.ACTION_TALK -> "gui.noellesroles.sixty_seconds.npc.action.talk";
            case SixtySecondsNpcMenu.ACTION_INTEL -> "gui.noellesroles.sixty_seconds.npc.action.intel";
            case SixtySecondsNpcMenu.ACTION_TRADE -> "gui.noellesroles.sixty_seconds.npc.action.trade";
            case SixtySecondsNpcMenu.ACTION_HIRE -> "gui.noellesroles.sixty_seconds.npc.action.hire";
            case SixtySecondsNpcMenu.ACTION_STEAL -> "gui.noellesroles.sixty_seconds.npc.action.steal";
            case SixtySecondsNpcMenu.ACTION_ROB -> "gui.noellesroles.sixty_seconds.npc.action.rob";
            case SixtySecondsNpcMenu.ACTION_SHOP_EDIT -> "gui.noellesroles.sixty_seconds.npc.action.shop_edit";
            case SixtySecondsNpcMenu.ACTION_LEAVE -> "gui.noellesroles.sixty_seconds.npc.action.leave";
            default -> "gui.noellesroles.sixty_seconds.npc.dialogue.title";
        };
    }

    private static int accentColor(int action) {
        return switch (action) {
            case SixtySecondsNpcMenu.ACTION_TALK -> MUTED;
            case SixtySecondsNpcMenu.ACTION_INTEL -> BLUE;
            case SixtySecondsNpcMenu.ACTION_TRADE -> GOLD;
            case SixtySecondsNpcMenu.ACTION_HIRE -> GREEN;
            case SixtySecondsNpcMenu.ACTION_STEAL -> PURPLE;
            case SixtySecondsNpcMenu.ACTION_ROB -> RED;
            case SixtySecondsNpcMenu.ACTION_SHOP_EDIT -> ORANGE;
            default -> MUTED;
        };
    }

    /** 变体号 → 实体名语言键（与 {@code SixtySecondsNpcEntity.Variant} 的 id 对齐）。 */
    private static String variantKey(int variantId) {
        return switch (variantId) {
            case 0 -> "entity.noellesroles.sixty_seconds_npc_merchant";
            case 1 -> "entity.noellesroles.sixty_seconds_npc_soldier";
            case 2 -> "entity.noellesroles.sixty_seconds_npc_bandit";
            default -> "entity.noellesroles.sixty_seconds_npc_traveler";
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            for (int i = 0; i < data.options().size(); i++) {
                OpenNpcDialogueS2CPacket.Option option = data.options().get(i);
                int y = rowsTop + i * (ROW_H + ROW_GAP);
                if (option.enabled() && isInRect((int) mouseX, (int) mouseY, x, y, w, ROW_H)) {
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    ClientPlayNetworking.send(new NpcDialogueActionC2SPacket(
                            data.entityId(), option.action(), option.param()));
                    // 交易/编辑由服务端重推新屏；其余（闲聊/情报/偷窃/抢劫/离开）就地关屏
                    // ——偷窃的进度条走搜刮 HUD 覆盖层，屏必须关掉才看得见
                    if (!opensNewScreen(option.action())) {
                        onClose();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 这些动作服务端会推一个新屏过来，本屏不要抢先关（会把新屏一起关掉）。 */
    private static boolean opensNewScreen(int action) {
        return action == SixtySecondsNpcMenu.ACTION_TRADE
                || action == SixtySecondsNpcMenu.ACTION_SHOP_EDIT;
    }

    private static boolean isInRect(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static int blendColors(int c1, int c2, float t) {
        int a1 = c1 >>> 24, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = c2 >>> 24, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int) (a1 + (a2 - a1) * t) << 24) | ((int) (r1 + (r2 - r1) * t) << 16)
                | ((int) (g1 + (g2 - g1) * t) << 8) | (int) (b1 + (b2 - b1) * t);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
