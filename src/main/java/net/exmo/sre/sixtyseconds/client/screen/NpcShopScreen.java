package net.exmo.sre.sixtyseconds.client.screen;

import net.exmo.sre.sixtyseconds.network.NpcShopBuyC2SPacket;
import net.exmo.sre.sixtyseconds.network.OpenNpcShopS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 商人购买界面（客户端）：行式货架——物品图标 + 名称 + 单价 + 剩余库存，点击购买。
 * 服务端每次成交后重推 {@link OpenNpcShopS2CPacket}，本屏经
 * {@code NoellesrolesClient} 的接收器整屏替换刷新（S2C 开屏 → C2S 动作 → 改状态 → 重推 S2C）。
 * 潜行点击 = 一次买 8 个。风格遵循 {@code docs/ui_style.md}。
 */
public class NpcShopScreen extends Screen {
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
    private static final int EDGE_IDLE = 0xFF5A4530;

    private static final int ROW_H = 26;
    private static final int ROW_GAP = 3;
    private static final int PAD = 12;
    private static final int HEADER_H = 46;
    /** 潜行点击的批量购买数量。 */
    private static final int BULK_COUNT = 8;

    private final OpenNpcShopS2CPacket data;
    private final List<ItemStack> icons = new ArrayList<>();
    private final float[] hoverAnim;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int rowsTop;

    public NpcShopScreen(OpenNpcShopS2CPacket data) {
        super(Component.literal(data.npcName()));
        this.data = data;
        this.hoverAnim = new float[data.rows().size()];
        for (OpenNpcShopS2CPacket.Row row : data.rows()) {
            icons.add(iconOf(row));
        }
    }

    private static ItemStack iconOf(OpenNpcShopS2CPacket.Row row) {
        ResourceLocation rl = ResourceLocation.tryParse(row.itemId());
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(rl), Math.max(1, row.count()));
    }

    @Override
    protected void init() {
        panelW = (int) Mth.clamp(this.width * 0.6F, 320, 420);
        panelH = HEADER_H + Math.max(1, data.rows().size()) * (ROW_H + ROW_GAP) + 26;
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

        g.drawCenteredString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD),
                this.width / 2, panelY + 12, GOLD);
        g.drawString(this.font, Component.translatable("gui.noellesroles.sixty_seconds.npc.shop.title"),
                panelX + PAD, panelY + 28, MUTED);
        Component funds = Component.translatable(
                "gui.noellesroles.sixty_seconds.npc.funds", data.tokens());
        g.drawString(this.font, funds,
                panelX + panelW - PAD - this.font.width(funds), panelY + 28, GOLD);

        if (data.rows().isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.noellesroles.sixty_seconds.npc.shop.empty"),
                    this.width / 2, rowsTop + 8, MUTED);
        }

        ItemStack tooltip = ItemStack.EMPTY;
        for (int i = 0; i < data.rows().size(); i++) {
            if (drawRow(g, i, mouseX, mouseY)) {
                tooltip = icons.get(i);
            }
        }
        g.drawCenteredString(this.font,
                Component.translatable("gui.noellesroles.sixty_seconds.npc.shop.hint", BULK_COUNT),
                this.width / 2, panelY + panelH - 16, MUTED);
        if (!tooltip.isEmpty()) {
            g.renderTooltip(this.font, tooltip, mouseX, mouseY);
        }
    }

    /** 画一行；返回是否 hover（用于 tooltip）。 */
    private boolean drawRow(GuiGraphics g, int index, int mouseX, int mouseY) {
        OpenNpcShopS2CPacket.Row row = data.rows().get(index);
        int x = panelX + PAD;
        int w = panelW - PAD * 2;
        int y = rowsTop + index * (ROW_H + ROW_GAP);
        boolean affordable = data.tokens() >= row.price();
        boolean inStock = row.stock() > 0;
        boolean enabled = affordable && inStock;
        boolean hover = isInRect(mouseX, mouseY, x, y, w, ROW_H);

        float target = hover && enabled ? 1F : 0F;
        hoverAnim[index] += (target - hoverAnim[index]) * 0.22F;
        float t = hoverAnim[index];

        int base = enabled ? 0x66231A10 : 0x44140E08;
        g.fill(x, y, x + w, y + ROW_H, blendColors(base, 0x33FFFFFF, t * 0.5F));
        g.renderOutline(x, y, w, ROW_H, enabled ? blendColors(EDGE_IDLE, GOLD, t) : 0xFF3A2E20);

        ItemStack icon = icons.get(index);
        if (!icon.isEmpty()) {
            g.renderItem(icon, x + 4, y + 5);
            g.renderItemDecorations(this.font, icon, x + 4, y + 5);
        } else {
            g.drawString(this.font, "?", x + 9, y + 9, RED, false);
        }
        Component name = icon.isEmpty()
                ? Component.translatable("gui.noellesroles.sixty_seconds.npc.shop.unknown_item")
                : icon.getHoverName();
        g.drawString(this.font, name, x + 26, y + 9, enabled ? blendColors(TEXT, GOLD, t) : 0xFF7A6F5C);

        // 右侧：库存 + 单价
        Component stock = inStock
                ? Component.translatable("gui.noellesroles.sixty_seconds.npc.shop.stock", row.stock())
                : Component.translatable("gui.noellesroles.sixty_seconds.npc.shop.sold_out");
        int stockW = this.font.width(stock);
        g.drawString(this.font, stock, x + w - 58 - stockW, y + 9, inStock ? MUTED : RED);
        Component price = Component.translatable(
                "gui.noellesroles.sixty_seconds.npc.shop.price", row.price());
        g.drawString(this.font, price, x + w - 4 - this.font.width(price), y + 9,
                affordable ? GREEN : RED);
        return hover;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            for (int i = 0; i < data.rows().size(); i++) {
                OpenNpcShopS2CPacket.Row row = data.rows().get(i);
                int y = rowsTop + i * (ROW_H + ROW_GAP);
                if (!isInRect((int) mouseX, (int) mouseY, x, y, w, ROW_H)) {
                    continue;
                }
                if (row.stock() <= 0 || data.tokens() < row.price()) {
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
                    return true;
                }
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                int count = hasShiftDown() ? BULK_COUNT : 1;
                // 不关屏：服务端成交后会重推 OpenNpcShopS2CPacket 整屏替换（含新库存/价格/余额）
                ClientPlayNetworking.send(new NpcShopBuyC2SPacket(data.entityId(), i, count));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
