package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedHandledScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.item.CourierMailData;
import org.agmas.noellesroles.content.item.CourierMailItem;
import org.agmas.noellesroles.packet.CourierMailReceiveC2SPacket;
import org.agmas.noellesroles.packet.CourierMailReplyC2SPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** 收信人阅读/领取/回信 GUI — 信纸风格 */
public class CourierMailReceiveScreen extends Screen {
    private static final int GUI_W = 256, GUI_H = 210;
    private static final int LINE_TOP = 42;

    private final InteractionHand hand;
    private final String message;
    private final int effect;
    private final boolean hasItem;
    private final String attachName;
    private final boolean isReplyItem;
    private static final int REPLY_LINES = 5;
    private boolean claimed;
    private boolean showingReply;
    private final String[] replyLines = new String[]{"", "", "", "", ""};
    private int replyRow;
    private @Nullable TextFieldHelper replyField;
    private int replyItemSlot = -1;
    private boolean showingItems;

    public CourierMailReceiveScreen(InteractionHand hand) {
        super(Component.translatable("screen.noellesroles.courier.receive"));
        this.hand = hand;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ItemStack stack = mc.player.getItemInHand(hand);
            this.message = CourierMailData.getMessage(stack);
            this.effect = CourierMailData.getEffect(stack);
            this.hasItem = CourierMailData.hasAttached(stack);
            this.attachName = CourierMailData.getAttachmentName(stack);
            this.isReplyItem = CourierMailData.isReply(stack);
            this.claimed = CourierMailData.isClaimed(stack);
            // 非回信且已领取过效果 → 直接进入回信页
            if (!isReplyItem && this.claimed) {
                this.showingReply = true;
            }
            if (!isReplyItem && CourierMailData.isReplyMode(stack)) {
                this.claimed = true;
                this.showingReply = true;
            }
        } else {
            this.message = "";
            this.effect = 0;
            this.hasItem = false;
            this.attachName = "";
            this.isReplyItem = false;
        }
    }

    private int tickCounter;

    @Override
    public void tick() {
        // 每 20 tick（1 秒）检查一次手中是否有信件
        if (++tickCounter % 20 == 0 && minecraft != null && minecraft.player != null) {
            ItemStack stack = minecraft.player.getItemInHand(hand);
            if (stack.isEmpty() || !(stack.getItem() instanceof CourierMailItem)) {
                onClose();
            }
        }
    }

    @Override
    protected void init() {
        int cx = (width - GUI_W) / 2;
        int cy = (height - GUI_H) / 2;

        if (showingReply) {
            if (minecraft != null) {
                replyField = new TextFieldHelper(
                        () -> replyLines[replyRow],
                        s -> replyLines[replyRow] = s,
                        TextFieldHelper.createClipboardGetter(minecraft),
                        TextFieldHelper.createClipboardSetter(minecraft),
                        s -> minecraft.font.width(s) <= GUI_W - 80
                );
            }
            addRenderableWidget(new BlackTextBtn(cx + 30, cy + LINE_TOP + 80, 80, 20,
                    Component.translatable("screen.noellesroles.courier.reply_item"), b -> showingItems = !showingItems));
            addRenderableWidget(new BlackTextBtn(cx + GUI_W / 2 - 40, cy + GUI_H - 30, 80, 20,
                    Component.translatable("screen.noellesroles.courier.confirm"), b -> sendReply()));
            return;
        }

        if (!claimed) {
            if (isReplyItem) {
                // 收件回信 — 仅领取效果后关闭
                addRenderableWidget(new BlackTextBtn(cx + GUI_W / 2 - 40, cy + GUI_H - 35, 80, 20,
                        Component.translatable("screen.noellesroles.courier.claim"), b -> claim()));
            } else {
                // 他人来信 — 领取效果并直接进入回信页
                addRenderableWidget(new BlackTextBtn(cx + GUI_W - 130, cy + GUI_H - 35, 100, 20,
                        Component.translatable("screen.noellesroles.courier.claim_reply"), b -> claimAndReply()));
            }
        }
    }

    private void claim() {
        ClientPlayNetworking.send(new CourierMailReceiveC2SPacket(hand == InteractionHand.MAIN_HAND));
        claimed = true;
        if (isReplyItem && minecraft != null && minecraft.player != null) {
            minecraft.player.getItemInHand(hand).shrink(1);
        }
        // 领取后自动关闭页面
        onClose();
    }

    private void claimAndReply() {
        ClientPlayNetworking.send(new CourierMailReceiveC2SPacket(hand == InteractionHand.MAIN_HAND));
        claimed = true;
        // 将回信模式写入物品 NBT，重开页面时直接回到回信页
        if (minecraft != null && minecraft.player != null) {
            ItemStack stack = minecraft.player.getItemInHand(hand);
            CourierMailData.setReplyMode(stack, true);
        }
        showingReply = true;
        clearWidgets();
        init();
    }

    private void startReply() {
        showingReply = true;
        clearWidgets();
        init();
    }

    private void sendReply() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < REPLY_LINES; i++) {
            if (i > 0) sb.append('\n');
            sb.append(replyLines[i]);
        }
        byte[] msgBytes = sb.toString().trim().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ClientPlayNetworking.send(new CourierMailReplyC2SPacket(hand == InteractionHand.MAIN_HAND, msgBytes, replyItemSlot));
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (showingReply && replyField != null) {
            if (keyCode == GLFW.GLFW_KEY_UP && replyRow > 0) { replyRow--; replyField.setCursorToEnd(); return true; }
            if (keyCode == GLFW.GLFW_KEY_DOWN && replyRow < REPLY_LINES - 1) { replyRow++; replyField.setCursorToEnd(); return true; }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (replyRow < REPLY_LINES - 1) { replyRow++; replyField.setCursorToEnd(); }
                return true;
            }
            if (replyField.keyPressed(keyCode)) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (showingReply && replyField != null) {
            replyField.charTyped(chr);
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (showingItems && minecraft != null && minecraft.player != null) {
            int cx = (width - GUI_W) / 2, cy = (height - GUI_H) / 2;
            int sx = cx + 25, sy = cy + GUI_H - 120;
            for (int i = 0; i < 9; i++) {
                int ix = sx + i * 21;
                if (mx >= ix && mx <= ix + 18 && my >= sy && my <= sy + 18) {
                    ItemStack s = minecraft.player.getInventory().getItem(i);
                    if (!s.isEmpty() && !isFiltered(s)) {
                        replyItemSlot = i;
                        showingItems = false;
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float delta) {
        this.renderBackground(g, mouseX, mouseY, delta);
        super.render(g, mouseX, mouseY, delta);

        int cx = (width - GUI_W) / 2;
        int cy = (height - GUI_H) / 2;

        g.fill(cx, cy, cx + GUI_W, cy + GUI_H, 0xFFF5E6C8);
        g.renderOutline(cx, cy, GUI_W, GUI_H, 0xFFA08060);
        g.renderOutline(cx + 6, cy + 6, GUI_W - 12, GUI_H - 12, 0xFFC4A882);
        g.drawCenteredString(font, Component.literal("\u2709  ").append(title), cx + GUI_W / 2, cy + 12, 0xFF5B3A1E);

        if (showingReply) {
            g.drawString(font, Component.translatable("screen.noellesroles.courier.reply"), cx + 30, cy + 40, 0xFF3B2312);

            for (int i = 0; i < REPLY_LINES; i++) {
                int lineY = cy + LINE_TOP + i * 14;
                g.fill(cx + 38, lineY, cx + GUI_W - 38, lineY + 1, 0xFFD4C4A8);
                int textY = lineY - font.lineHeight + 1;
                g.drawString(font, replyLines[i], cx + 38, textY, replyRow == i ? 0xFF3B2312 : 0xFF5B3A1E);
            }

            if (showingItems && minecraft != null && minecraft.player != null) {
                int sx = cx + 25, sy = cy + GUI_H - 120;
                g.fill(sx - 4, sy - 4, sx + 200, sy + 60, 0xFFF0E6D0);
                g.renderOutline(sx - 4, sy - 4, 200, 60, 0xFFA08060);
                g.drawString(font, Component.translatable("screen.noellesroles.courier.pick_item"), sx, sy - 14, 0xFF3B2312);
                for (int i = 0; i < 9; i++) {
                    ItemStack s = minecraft.player.getInventory().getItem(i);
                    if (s.isEmpty() || isFiltered(s)) continue;
                    int ix = sx + i * 21;
                    g.fill(ix, sy, ix + 18, sy + 18, replyItemSlot == i ? 0xFFAA8866 : 0xFF887766);
                    g.renderItem(s, ix + 1, sy + 1);
                    g.renderItemDecorations(font, s, ix + 1, sy + 1);
                }
            }
        } else {
            int drawY = cy + LINE_TOP - 10;
            for (String line : splitMessage(message, 35)) {
                g.drawString(font, line, cx + 38, drawY, 0xFF3B2312);
                drawY += 12;
            }
            g.fill(cx + 20, cy + GUI_H - 70, cx + GUI_W - 20, cy + GUI_H - 69, 0xFFC4A882);

            String effKey = switch (effect) {
                case 1 -> "item.noellesroles.courier_mail.effect.san";
                case 2 -> "item.noellesroles.courier_mail.effect.speed";
                case 3 -> "item.noellesroles.courier_mail.effect.disguise";
                default -> null;
            };
            if (effKey != null) {
                g.drawString(font, "\u2726 " + Component.translatable(effKey).getString(), cx + 30, cy + GUI_H - 52, 0xFF5B8A3E);
            }
            if (hasItem) {
                String name = attachName.isEmpty() ? Component.translatable("screen.noellesroles.courier.has_attached").getString() : attachName;
                g.drawString(font, "\u25C6 " + name, cx + 30, cy + GUI_H - 38, 0xFF886644);
            }
        }
    }

    private static List<String> splitMessage(String msg, int len) {
        List<String> lines = new ArrayList<>();
        for (String part : msg.split("\n")) {
            for (int i = 0; i < part.length(); i += len) {
                lines.add(part.substring(i, Math.min(i + len, part.length())));
            }
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    private boolean isFiltered(ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (stack.getItem() instanceof CourierMailItem) return true;
        for (var predicate : LimitedHandledScreen.NotAllowItemTakePredicates) {
            if (predicate.test(stack)) return true;
        }
        return false;
    }

    private static class BlackTextBtn extends Button {
        BlackTextBtn(int x, int y, int w, int h, Component msg, OnPress onPress) { super(x, y, w, h, msg, onPress, DEFAULT_NARRATION); }
        @Override public void renderString(GuiGraphics g, net.minecraft.client.gui.Font font, int color) {
            g.drawCenteredString(font, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, 0x000000);
        }
    }
}
