package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.event.AllowOtherCameraType;
import io.wifi.starrailexpress.network.StreamingSpectatorPayload;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;

import com.mojang.blaze3d.platform.Window;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class StreamingSpectatorClient {
    private static boolean active;
    private static UUID targetUuid;
    private static int cameraMode = StreamingSpectatorPayload.CAMERA_NONE;
    private static boolean cameraBound;
    private static List<ItemStack> targetInventory = List.of();
    private static int selectedHotbarSlot = -1;

    // 标题缩放
    private static final float TITLE_1_SCALE = 2.0F; // 18px
    private static final float TITLE_2_SCALE = 1.667F; // 15px
    private static final float TITLE_3_SCALE = 1.444F; // 13px
    // 普通文本 1.0F (9px)

    private static final int GOLD_TITLE = 0xFFFFDA76;
    private static final int GOLD_HEADING = 0xFFFFC44D;
    private static final int GOLD_TEXT = 0xFFFFF0C3;
    private static final int GOLD_MUTED = 0xFFD8BE76;
    private static final int GOLD_WARNING = 0xFFFFC29B;

    private static final int HOTBAR_SLOTS = StreamingSpectatorPayload.HOTBAR_SLOTS;
    private static final int HOTBAR_WIDTH = 182;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int HOTBAR_SLOT_SIZE = 20;
    private static final int PANEL_SHADOW = 0x26000000;
    private static final int PANEL_BORDER = 0x99E2B654;

    private StreamingSpectatorClient() {
    }

    public static boolean isActive() {
        if (Minecraft.getInstance().player == null)
            return false;
        return active;
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(StreamingSpectatorPayload.ID,
                (payload, context) -> context.client().execute(() -> applyPayload(context.client(), payload)));
        ClientTickEvents.END_CLIENT_TICK.register(StreamingSpectatorClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear(client));
        AllowOtherCameraType.EVENT.register((original, localPlayer) -> getLockedCameraType());
        CommonHudRenderCallback.EVENT.register((renderer, delta) -> renderHud(renderer));
    }

    private static void applyPayload(Minecraft client, StreamingSpectatorPayload payload) {
        UUID previousTarget = targetUuid;
        active = payload.active();
        targetUuid = payload.targetUuid();
        cameraMode = payload.cameraMode();
        targetInventory = copyInventory(payload.inventory());
        selectedHotbarSlot = payload.selectedHotbarSlot();
        if (!active || targetUuid == null) {
            clearCameraBinding(client);
        } else if (!targetUuid.equals(previousTarget)) {
            clearCameraBinding(client);
        }
    }

    private static void tick(Minecraft client) {
        if (!active || client.player == null || client.level == null || targetUuid == null) {
            if (!active)
                clearCameraBinding(client);
            return;
        }
        Entity target = client.level.getPlayerByUUID(targetUuid);
        if (target == null) {
            clearCameraBinding(client);
            return;
        }
        if (client.getCameraEntity() != target) {
            client.setCameraEntity(target);
        }
        cameraBound = true;
    }

    public static void renderHud(FakeGuiGraphics guiGraphics) {
        Minecraft client = Minecraft.getInstance();
        if (!active || client.options.hideGui || client.player == null || client.level == null)
            return;

        Font font = client.font;

        Window window = client.getWindow();

        int magicVarInt = (int) ((double) window.getWidth() / window.getGuiScale());
        int screenWidth = (double) window.getWidth() / window.getGuiScale() > (double) magicVarInt ? magicVarInt + 1
                : magicVarInt;

        int screenHeight = client.getWindow().getGuiScaledHeight();
        Player target = targetUuid == null ? null : client.level.getPlayerByUUID(targetUuid);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(-(double) screenWidth / 4d, 0, 0);
        renderSidebar(guiGraphics, font, screenWidth, screenHeight, target);
        guiGraphics.pose().popPose();
        renderTargetHotbar(guiGraphics, font, screenWidth, screenHeight);
    }

    // ========== 左侧信息面板 ==========
    private static void renderSidebar(FakeGuiGraphics guiGraphics, Font font, int screenWidth, int screenHeight,
            Player target) {
        int panelWidth = screenWidth / 4;
        int margin = 4;
        int wrapWidth = panelWidth - margin * 2;

        List<HudLine> lines = new ArrayList<>();

        // # 观战：玩家ID
        String playerName = target != null ? target.getDisplayName().getString() : "???";
        Component title = Component.literal("观战：" + playerName).withStyle(ChatFormatting.BOLD);
        addWrapped(lines, font, title, wrapWidth, GOLD_TITLE, 0, TITLE_1_SCALE);

        // ## 职业
        addWrapped(lines, font, Component.literal("职业").withStyle(ChatFormatting.BOLD), wrapWidth, GOLD_HEADING, 0,
                TITLE_2_SCALE);

        if (target != null) {
            SRERole role = SREClient.gameComponent != null ? SREClient.gameComponent.getRole(target) : null;
            if (role != null) {
                addWrapped(lines, font, role.getName().copy().withColor(GOLD_HEADING), wrapWidth, GOLD_HEADING, 0,
                        TITLE_3_SCALE);
                addMultiline(lines, font, role.getSimpleDescription(), wrapWidth, GOLD_TEXT, 1.0F);
            } else {
                addWrapped(lines, font, Component.translatable("hud.sre.streaming_spectator.unknown_role"), wrapWidth,
                        GOLD_WARNING, 0, TITLE_3_SCALE);
            }
        } else {
            addWrapped(lines, font, Component.translatable("hud.sre.streaming_spectator.unknown_role"), wrapWidth,
                    GOLD_WARNING, 0, TITLE_3_SCALE);
        }

        // ## 修饰符
        addWrapped(lines, font, Component.literal("修饰符").withStyle(ChatFormatting.BOLD), wrapWidth, GOLD_HEADING, 0,
                TITLE_2_SCALE);

        if (target != null && SREClient.modifierComponent != null) {
            List<SREModifier> modifiers = SREClient.modifierComponent.getDisplayableModifiers(target);
            modifiers.sort(Comparator.comparing(mod -> mod.identifier().toString()));
            if (modifiers.isEmpty()) {
                addWrapped(lines, font, Component.translatable("hud.sre.streaming_spectator.none"), wrapWidth,
                        GOLD_MUTED, 0, 1.0F);
            } else {
                for (SREModifier modifier : modifiers) {
                    addWrapped(lines, font, modifier.getName(true), wrapWidth, GOLD_TEXT, 0, TITLE_3_SCALE);
                    addMultiline(lines, font, modifier.getSimpleDescription(), wrapWidth, GOLD_MUTED, 1.0F);
                }
            }
        } else {
            addWrapped(lines, font, Component.translatable("hud.sre.streaming_spectator.modifiers_unavailable"),
                    wrapWidth, GOLD_MUTED, 0, 1.0F);
        }

        // 黑色背景（全高）
        guiGraphics.fill(0, 0, panelWidth, screenHeight, 0xFF000000);

        // 绘制文本
        int y = margin;
        for (HudLine line : lines) {
            drawLine(guiGraphics, font, line, margin, y);
            y += line.height();
        }
    }

    // ========== 热键栏（底部中央，避让左侧面板） ==========
    private static void renderTargetHotbar(FakeGuiGraphics guiGraphics, Font font, int screenWidth, int screenHeight) {
        // 右侧游戏区域的中央
        int leftAreaWidth = screenWidth / 4;
        int rightAreaWidth = screenWidth - leftAreaWidth;
        int rightAreaCenterX = leftAreaWidth + rightAreaWidth / 2;
        int hotbarX = rightAreaCenterX - HOTBAR_WIDTH / 2;
        int hotbarY = screenHeight - HOTBAR_HEIGHT;

        // 绘制背景
        guiGraphics.fill(hotbarX + 1, hotbarY + 1, hotbarX + HOTBAR_WIDTH + 1, hotbarY + HOTBAR_HEIGHT + 1,
                PANEL_SHADOW);
        guiGraphics.fill(hotbarX, hotbarY, hotbarX + HOTBAR_WIDTH, hotbarY + HOTBAR_HEIGHT, 0x7A2C1B07);
        guiGraphics.fill(hotbarX, hotbarY, hotbarX + HOTBAR_WIDTH, hotbarY + 1, PANEL_BORDER);
        guiGraphics.fill(hotbarX, hotbarY + HOTBAR_HEIGHT - 1, hotbarX + HOTBAR_WIDTH, hotbarY + HOTBAR_HEIGHT,
                PANEL_BORDER);
        guiGraphics.fill(hotbarX, hotbarY, hotbarX + 1, hotbarY + HOTBAR_HEIGHT, PANEL_BORDER);
        guiGraphics.fill(hotbarX + HOTBAR_WIDTH - 1, hotbarY, hotbarX + HOTBAR_WIDTH, hotbarY + HOTBAR_HEIGHT,
                PANEL_BORDER);

        for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
            int slotX = hotbarX + 1 + slot * HOTBAR_SLOT_SIZE;
            renderHotbarSlot(guiGraphics, font, slot, slotX, hotbarY + 1);
        }
    }

    private static void renderHotbarSlot(FakeGuiGraphics guiGraphics, Font font, int slot, int x, int y) {
        boolean selected = slot == selectedHotbarSlot;
        if (selected) {
            guiGraphics.fill(x - 1, y - 1, x + HOTBAR_SLOT_SIZE + 1, y + HOTBAR_SLOT_SIZE + 1, 0x80FFD86A);
            guiGraphics.fill(x, y, x + HOTBAR_SLOT_SIZE, y + HOTBAR_SLOT_SIZE, 0x66351F05);
        } else {
            guiGraphics.fill(x, y, x + HOTBAR_SLOT_SIZE, y + HOTBAR_SLOT_SIZE, 0x55351F05);
        }
        guiGraphics.fill(x, y, x + HOTBAR_SLOT_SIZE, y + 1, 0x7AFFE08A);
        guiGraphics.fill(x, y + HOTBAR_SLOT_SIZE - 1, x + HOTBAR_SLOT_SIZE, y + HOTBAR_SLOT_SIZE, 0x7A7D5520);
        guiGraphics.fill(x, y, x + 1, y + HOTBAR_SLOT_SIZE, 0x7AFFE08A);
        guiGraphics.fill(x + HOTBAR_SLOT_SIZE - 1, y, x + HOTBAR_SLOT_SIZE, y + HOTBAR_SLOT_SIZE, 0x7A7D5520);

        ItemStack stack = getInventoryStack(slot);
        if (!stack.isEmpty()) {
            guiGraphics.renderItem(stack, x + 2, y + 2);
            guiGraphics.renderItemDecorations(font, stack, x + 2, y + 2);
        }
    }

    private static ItemStack getInventoryStack(int index) {
        if (index < 0 || index >= targetInventory.size())
            return ItemStack.EMPTY;
        ItemStack stack = targetInventory.get(index);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    // ========== 通用工具方法 ==========
    private static void addMultiline(List<HudLine> lines, Font font, Component component, int wrapWidth, int color,
            float scale) {
        if (component == null)
            return;
        String raw = component.getString();
        if (raw.isBlank())
            return;
        for (String part : raw.split("\\\\n|\\n")) {
            if (part.isBlank()) {
                lines.add(new HudLine(FormattedCharSequence.EMPTY, 0x00FFFFFF, 0, 1.0F, lineHeight(font, 1.0F)));
            } else {
                addWrapped(lines, font, Component.literal(part), wrapWidth, color, 0, scale);
            }
        }
    }

    private static void addWrapped(List<HudLine> lines, Font font, Component component, int width, int color,
            int indent, float scale) {
        int scaledWidth = Math.max(40, Mth.floor((width - indent) / scale));
        for (FormattedCharSequence sequence : font.split(component, scaledWidth)) {
            lines.add(new HudLine(sequence, color, indent, scale, lineHeight(font, scale)));
        }
    }

    private static AllowOtherCameraType.ReturnCameraType getLockedCameraType() {
        if (!active || targetUuid == null)
            return AllowOtherCameraType.ReturnCameraType.NO_CHANGE;
        return switch (cameraMode) {
            case StreamingSpectatorPayload.CAMERA_FIRST_PERSON -> AllowOtherCameraType.ReturnCameraType.FIRST_PERSON;
            case StreamingSpectatorPayload.CAMERA_THIRD_PERSON_BACK ->
                AllowOtherCameraType.ReturnCameraType.THIRD_PERSON_BACK;
            default -> AllowOtherCameraType.ReturnCameraType.NO_CHANGE;
        };
    }

    private static void clear(Minecraft client) {
        active = false;
        targetUuid = null;
        cameraMode = StreamingSpectatorPayload.CAMERA_NONE;
        targetInventory = List.of();
        selectedHotbarSlot = -1;
        clearCameraBinding(client);
    }

    private static void clearCameraBinding(Minecraft client) {
        if (cameraBound && client.player != null && client.getCameraEntity() != client.player) {
            client.setCameraEntity(client.player);
        }
        cameraBound = false;
    }

    private static void drawLine(FakeGuiGraphics guiGraphics, Font font, HudLine line, int x, int y) {
        if (line.scale() == 1.0F) {
            guiGraphics.drawString(font, line.text(), x, y, line.color(), false);
            return;
        }
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        guiGraphics.pose().scale(line.scale(), line.scale(), 1.0F);
        guiGraphics.drawString(font, line.text(), 0, 0, line.color(), false);
        guiGraphics.pose().popPose();
    }

    private static int lineHeight(Font font, float scale) {
        return Math.max(1, Mth.ceil(font.lineHeight * scale)) + 2;
    }

    private static List<ItemStack> copyInventory(List<ItemStack> inventory) {
        if (inventory == null || inventory.isEmpty())
            return List.of();
        List<ItemStack> copy = new ArrayList<>(inventory.size());
        for (ItemStack stack : inventory)
            copy.add(stack == null ? ItemStack.EMPTY : stack.copy());
        return copy;
    }

    private record HudLine(FormattedCharSequence text, int color, int indent, float scale, int height) {
    }
}