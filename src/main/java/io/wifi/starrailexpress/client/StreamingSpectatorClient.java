package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.event.AllowOtherCameraType;
import io.wifi.starrailexpress.network.StreamingSpectatorPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.harpymodloader.modifiers.SREModifier;

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
    private static final float ROLE_HUD_SCALE = 1.4F;
    private static final float DESCRIPTION_SCALE = 0.6F;
    private static final int PANEL_FILL = 0x7A3A2408;
    private static final int PANEL_BORDER = 0x99E2B654;
    private static final int PANEL_SHADOW = 0x26000000;
    private static final int GOLD_TITLE = 0xFFFFDA76;
    private static final int GOLD_HEADING = 0xFFFFC44D;
    private static final int GOLD_TEXT = 0xFFFFF0C3;
    private static final int GOLD_MUTED = 0xFFD8BE76;
    private static final int GOLD_WARNING = 0xFFFFC29B;
    private static final int HOTBAR_SLOTS = StreamingSpectatorPayload.HOTBAR_SLOTS;
    private static final int HOTBAR_WIDTH = 182;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int HOTBAR_SLOT_SIZE = 20;

    private StreamingSpectatorClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(StreamingSpectatorPayload.ID, (payload, context) ->
                context.client().execute(() -> applyPayload(context.client(), payload)));
        ClientTickEvents.END_CLIENT_TICK.register(StreamingSpectatorClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear(client));
        AllowOtherCameraType.EVENT.register((original, localPlayer) -> getLockedCameraType());
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
            if (!active) {
                clearCameraBinding(client);
            }
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

    public static void renderHud(GuiGraphics guiGraphics) {
        Minecraft client = Minecraft.getInstance();
        if (!active || client.options.hideGui || client.player == null || client.level == null) {
            return;
        }

        Font font = client.font;
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        Player target = targetUuid == null ? null : client.level.getPlayerByUUID(targetUuid);

        renderRoleHud(guiGraphics, font, screenWidth, screenHeight, target);
        if (target != null) {
            renderModifierHud(guiGraphics, font, screenWidth, screenHeight, target);
            renderTargetHotbar(guiGraphics, font, screenWidth, screenHeight);
        }
    }

    private static void renderRoleHud(GuiGraphics guiGraphics, Font font, int screenWidth, int screenHeight,
            Player target) {
        int x = 10;
        int y = 28;
        int width = scaledRolePanelWidth(screenWidth);
        int wrapWidth = width - 24;
        int maxPanelHeight = Math.max(54, Mth.floor(screenHeight * 0.46F / ROLE_HUD_SCALE));

        List<HudLine> lines = new ArrayList<>();
        if (target == null) {
            addWrapped(lines, font,
                    Component.translatable("hud.sre.streaming_spectator.title").withStyle(ChatFormatting.BOLD),
                    wrapWidth, GOLD_TITLE, 0);
            addWrapped(lines, font,
                    Component.translatable("hud.sre.streaming_spectator.waiting"), wrapWidth, GOLD_TEXT, 0);
        } else {
            buildRoleLines(lines, font, wrapWidth, target);
        }

        PanelLayout layout = measurePanel(lines, maxPanelHeight, font);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        guiGraphics.pose().scale(ROLE_HUD_SCALE, ROLE_HUD_SCALE, 1.0F);
        renderTextPanel(guiGraphics, font, 0, 0, width, lines, layout);
        guiGraphics.pose().popPose();
    }

    private static int scaledRolePanelWidth(int screenWidth) {
        int upper = Math.min(310, Math.max(150, Mth.floor((screenWidth - 20) / ROLE_HUD_SCALE)));
        int lower = Math.min(190, upper);
        return Mth.clamp(screenWidth / 3, lower, upper);
    }

    private static void renderModifierHud(GuiGraphics guiGraphics, Font font, int screenWidth, int screenHeight,
            Player target) {
        int width = Mth.clamp(screenWidth / 3, 190, 310);
        int wrapWidth = width - 24;
        List<HudLine> lines = new ArrayList<>();
        buildModifierLines(lines, font, wrapWidth, target);

        int maxPanelHeight = Math.max(58, screenHeight / 3);
        PanelLayout layout = measurePanel(lines, maxPanelHeight, font);
        int x = 10;
        int y = Math.max(34, screenHeight - layout.height() - 34);
        renderTextPanel(guiGraphics, font, x, y, width, lines, layout);
    }

    private static void buildRoleLines(List<HudLine> lines, Font font, int wrapWidth, Player target) {
        addWrapped(lines, font,
                Component.translatable("hud.sre.streaming_spectator.target", target.getDisplayName())
                        .withStyle(ChatFormatting.BOLD),
                wrapWidth, GOLD_TITLE, 0);

        SRERole role = SREClient.gameComponent == null ? null : SREClient.gameComponent.getRole(target);
        if (role != null) {
            addWrapped(lines, font, role.getName().copy().withColor(GOLD_HEADING), wrapWidth, GOLD_HEADING, 0);
            addDescription(lines, font, role.getSimpleDescription(), wrapWidth, GOLD_TEXT, 8);
        } else {
            addWrapped(lines, font,
                    Component.translatable("hud.sre.streaming_spectator.unknown_role"), wrapWidth, GOLD_WARNING, 0);
        }
    }

    private static void buildModifierLines(List<HudLine> lines, Font font, int wrapWidth, Player target) {
        addWrapped(lines, font,
                Component.translatable("hud.sre.streaming_spectator.modifiers").withStyle(ChatFormatting.BOLD),
                wrapWidth, GOLD_HEADING, 0);
        if (SREClient.modifierComponent == null) {
            addWrapped(lines, font,
                    Component.translatable("hud.sre.streaming_spectator.modifiers_unavailable"),
                    wrapWidth, GOLD_MUTED, 8);
            return;
        }

        List<SREModifier> modifiers = SREClient.modifierComponent.getDisplayableModifiers(target);
        modifiers.sort(Comparator.comparing(modifier -> modifier.identifier().toString()));
        if (modifiers.isEmpty()) {
            addWrapped(lines, font,
                    Component.translatable("hud.sre.streaming_spectator.none"), wrapWidth, GOLD_MUTED, 8);
            return;
        }

        for (SREModifier modifier : modifiers) {
            addWrapped(lines, font, modifier.getName(true), wrapWidth, GOLD_TEXT, 8);
            addDescription(lines, font, modifier.getSimpleDescription(), wrapWidth, GOLD_MUTED, 16);
        }
    }

    private static void addDescription(List<HudLine> lines, Font font, Component component, int wrapWidth, int color,
            int indent) {
        if (component == null) {
            return;
        }
        String raw = component.getString();
        if (raw.isBlank()) {
            return;
        }
        for (String part : raw.split("\\\\n|\\n")) {
            if (part.isBlank()) {
                addSpacer(lines);
            } else {
                addWrapped(lines, font, Component.literal(part), wrapWidth, color, indent, DESCRIPTION_SCALE);
            }
        }
    }

    private static void addWrapped(List<HudLine> lines, Font font, Component component, int width, int color,
            int indent) {
        addWrapped(lines, font, component, width, color, indent, 1.0F);
    }

    private static void addWrapped(List<HudLine> lines, Font font, Component component, int width, int color,
            int indent, float scale) {
        int scaledWidth = Math.max(40, Mth.floor((width - indent) / scale));
        for (FormattedCharSequence sequence : font.split(component, scaledWidth)) {
            lines.add(new HudLine(sequence, color, indent, scale, lineHeight(font, scale)));
        }
    }

    private static void addSpacer(List<HudLine> lines) {
        lines.add(new HudLine(FormattedCharSequence.EMPTY, 0x00FFFFFF, 0, 1.0F, 5));
    }

    private static AllowOtherCameraType.ReturnCameraType getLockedCameraType() {
        if (!active || targetUuid == null) {
            return AllowOtherCameraType.ReturnCameraType.NO_CHANGE;
        }
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

    private static void drawPanel(GuiGraphics guiGraphics, int x, int y, int width, int height, int fill, int border) {
        guiGraphics.fill(x + 2, y + 2, x + width + 2, y + height + 2, PANEL_SHADOW);
        guiGraphics.fill(x, y, x + width, y + height, fill);
        guiGraphics.fill(x, y, x + width, y + 1, border);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, border);
        guiGraphics.fill(x, y, x + 1, y + height, border);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, border);
    }

    private static PanelLayout measurePanel(List<HudLine> lines, int maxPanelHeight, Font font) {
        int maxTextHeight = Math.max(0, maxPanelHeight - 18);
        VisibleLines visible = collectVisibleLines(lines, maxTextHeight, font);
        int ellipsisHeight = visible.truncated() ? lineHeight(font, 1.0F) : 0;
        return new PanelLayout(18 + visible.height() + ellipsisHeight, visible);
    }

    private static void renderTextPanel(GuiGraphics guiGraphics, Font font, int x, int y, int width,
            List<HudLine> lines, PanelLayout layout) {
        drawPanel(guiGraphics, x, y, width, layout.height(), PANEL_FILL, PANEL_BORDER);
        int lineY = y + 10;
        for (int i = 0; i < layout.visible().count(); i++) {
            HudLine line = lines.get(i);
            drawLine(guiGraphics, font, line, x + 12 + line.indent(), lineY);
            lineY += line.height();
        }
        if (layout.visible().truncated()) {
            guiGraphics.drawString(font, Component.literal("..."), x + 12, lineY, GOLD_MUTED, false);
        }
    }

    private static VisibleLines collectVisibleLines(List<HudLine> lines, int maxHeight, Font font) {
        int count = 0;
        int height = 0;
        int ellipsisHeight = lineHeight(font, 1.0F);
        for (int i = 0; i < lines.size(); i++) {
            HudLine line = lines.get(i);
            boolean hasMore = i < lines.size() - 1;
            int reserve = hasMore ? ellipsisHeight : 0;
            if (height + line.height() + reserve > maxHeight) {
                return new VisibleLines(count, height, true);
            }
            count++;
            height += line.height();
        }
        return new VisibleLines(count, height, false);
    }

    private static void drawLine(GuiGraphics guiGraphics, Font font, HudLine line, int x, int y) {
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

    private static void renderTargetHotbar(GuiGraphics guiGraphics, Font font, int screenWidth, int screenHeight) {
        int x = screenWidth / 2 - HOTBAR_WIDTH / 2;
        int y = screenHeight - HOTBAR_HEIGHT;

        guiGraphics.fill(x + 1, y + 1, x + HOTBAR_WIDTH + 1, y + HOTBAR_HEIGHT + 1, PANEL_SHADOW);
        guiGraphics.fill(x, y, x + HOTBAR_WIDTH, y + HOTBAR_HEIGHT, 0x7A2C1B07);
        guiGraphics.fill(x, y, x + HOTBAR_WIDTH, y + 1, PANEL_BORDER);
        guiGraphics.fill(x, y + HOTBAR_HEIGHT - 1, x + HOTBAR_WIDTH, y + HOTBAR_HEIGHT, PANEL_BORDER);
        guiGraphics.fill(x, y, x + 1, y + HOTBAR_HEIGHT, PANEL_BORDER);
        guiGraphics.fill(x + HOTBAR_WIDTH - 1, y, x + HOTBAR_WIDTH, y + HOTBAR_HEIGHT, PANEL_BORDER);

        for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
            int slotX = x + 1 + slot * HOTBAR_SLOT_SIZE;
            renderHotbarSlot(guiGraphics, font, slot, slotX, y + 1);
        }
    }

    private static void renderHotbarSlot(GuiGraphics guiGraphics, Font font, int slot, int x, int y) {
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
        if (index < 0 || index >= targetInventory.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = targetInventory.get(index);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    private static List<ItemStack> copyInventory(List<ItemStack> inventory) {
        if (inventory == null || inventory.isEmpty()) {
            return List.of();
        }
        List<ItemStack> copy = new ArrayList<>(inventory.size());
        for (ItemStack stack : inventory) {
            copy.add(stack == null ? ItemStack.EMPTY : stack.copy());
        }
        return copy;
    }

    private record HudLine(FormattedCharSequence text, int color, int indent, float scale, int height) {
    }

    private record VisibleLines(int count, int height, boolean truncated) {
    }

    private record PanelLayout(int height, VisibleLines visible) {
    }
}
