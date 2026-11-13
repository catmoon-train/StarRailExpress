package io.wifi.starrailexpress.training.client;

import io.wifi.starrailexpress.client.gui.widget.SreButton;
import io.wifi.starrailexpress.training.TrainingActionPayload;
import io.wifi.starrailexpress.training.TrainingStatePayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 训练面板的会话缓存与原版菜单接入；不依据存档名推断训练权限。 */
@Environment(EnvType.CLIENT)
public final class TrainingClient {
    private static CompoundTag state = new CompoundTag();
    private static boolean registered;
    private static boolean hasCatalogue;
    private static boolean autoOpened;
    private static boolean autoOpenPending;
    private static long revision;

    private TrainingClient() { }

    public static void register() {
        if (registered) return;
        registered = true;
        ClientPlayNetworking.registerGlobalReceiver(TrainingStatePayload.ID, (payload, context) -> {
            var connection = context.client().getConnection();
            CompoundTag incoming = payload.state() == null ? new CompoundTag() : payload.state().copy();
            context.client().execute(() -> {
                // 断开连接前排队的包不能重新激活下一次会话。
                if (connection == null || context.client().getConnection() != connection) return;
                accept(incoming);
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!autoOpenPending || !isTraining() || client.player == null || client.getOverlay() != null) return;
            if (client.screen instanceof TrainingScreen) {
                autoOpened = true;
                autoOpenPending = false;
            } else if (client.screen == null || client.screen instanceof PauseScreen) {
                autoOpened = true;
                autoOpenPending = false;
                openPanel(client.screen);
            }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            // 自定义标题屏与暂停屏各自添加，其他模组的 Screen 子类不重复注入。
            if (screen.getClass() != TitleScreen.class && screen.getClass() != PauseScreen.class) return;
            var buttons = Screens.getButtons(screen);
            if (buttons.stream().anyMatch(TrainingEntryButton.class::isInstance)) return;
            Component anchorLabel = Component.translatable(screen instanceof TitleScreen
                    ? "menu.singleplayer" : "menu.returnToGame");
            AbstractWidget anchor = buttons.stream().filter(b -> b.getMessage().equals(anchorLabel)).findFirst().orElse(null);
            if (anchor == null || anchor.getWidth() < 100) return;
            // 复用一行，避免在 GUI 高缩放时把断开连接按钮挤出屏幕。
            int oldWidth = anchor.getWidth();
            boolean compact = screen instanceof PauseScreen;
            int leftWidth = compact ? oldWidth - 24 : (oldWidth - 4) / 2;
            anchor.setWidth(leftWidth);
            Button entry = compact ? createEntryIconButton(screen)
                    : createEntryButton(screen, oldWidth - leftWidth - 4);
            entry.setX(anchor.getX() + leftWidth + 4);
            entry.setY(anchor.getY());
            entry.setHeight(anchor.getHeight());
            buttons.add(entry);
            ScreenEvents.afterTick(screen).register(s -> updateEntry(entry));
        });
    }

    private static void accept(CompoundTag incoming) {
        Minecraft client = Minecraft.getInstance();
        if (!isLocalTrainingServer() && !(client.level != null && client.getConnection() != null
                && !client.hasSingleplayerServer() && incoming.getBoolean("dedicated"))) return;
        if (incoming.getBoolean("full")) {
            hasCatalogue = true;
        } else if (hasCatalogue) {
            // 普通确认只更新权威状态，不把之前的物品/职业/修饰符目录清空。
            if (state.contains("items")) incoming.put("items", state.get("items").copy());
            if (state.contains("roles")) incoming.put("roles", state.get("roles").copy());
            if (state.contains("modifierCatalogue")) incoming.put("modifierCatalogue", state.get("modifierCatalogue").copy());
        }
        state = incoming;
        revision++;
        if (incoming.getBoolean("active") && incoming.getBoolean("full") && !autoOpened) autoOpenPending = true;
        if (!incoming.getBoolean("active")) autoOpenPending = false;
        if (client.screen instanceof TrainingScreen panel) panel.acceptState();
    }

    /** 本地集成服（含已开放到局域网的主机）继续使用训练状态；客人仍由服务端状态过滤。 */
    private static boolean isLocalTrainingServer() {
        Minecraft client = Minecraft.getInstance();
        return client.level != null && client.hasSingleplayerServer()
                && client.getSingleplayerServer() != null;
    }

    public static boolean isTraining() {
        Minecraft client = Minecraft.getInstance();
        return state.getBoolean("active") && (isLocalTrainingServer()
                || state.getBoolean("dedicated") && !client.hasSingleplayerServer() && client.level != null && client.getConnection() != null);
    }

    public static boolean isDedicated() { return isTraining() && state.getBoolean("dedicated"); }

    public static void openPanel(Screen parent) {
        Minecraft client = Minecraft.getInstance();
        if (!isTraining()) {
            if (client.player != null) client.player.displayClientMessage(Component.translatable("training.sre.ui.not_training"), false);
            return;
        }
        autoOpened = true;
        autoOpenPending = false;
        if (client.screen instanceof TrainingScreen) return;
        client.setScreen(new TrainingScreen(parent));
    }

    /** 与标题屏、原版菜单、定制暂停菜单共用，加载中禁止重复启动。 */
    public static Component buttonLabel() {
        return Component.translatable(TrainingWorldLauncher.isBusy() ? "training.sre.ui.preparing"
                : isTraining() ? "training.sre.ui.panel" : "training.sre.ui.enter");
    }

    public static void openEntry(Screen parent) {
        if (TrainingWorldLauncher.isBusy()) return;
        if (isTraining()) openPanel(parent);
        else TrainingWorldLauncher.open(parent);
    }

    public static Button createEntryButton(Screen parent, int width) {
        TrainingEntryButton button = new TrainingEntryButton(parent);
        button.setWidth(width);
        button.setHeight(20);
        updateEntry(button);
        return button;
    }

    /** 暂停菜单的紧凑入口，保留完整名称供悬停提示与无障碍朗读使用。 */
    public static Button createEntryIconButton(Screen parent) {
        Button button = new TrainingEntryIconButton(parent);
        updateEntry(button);
        return button;
    }

    private static void updateEntry(Button button) {
        button.setMessage(buttonLabel());
        button.active = !TrainingWorldLauncher.isBusy();
    }

    public static void reset() {
        state = new CompoundTag();
        revision++;
        hasCatalogue = false;
        autoOpened = false;
        autoOpenPending = false;
    }

    static CompoundTag state() { return state.copy(); }
    static long revision() { return revision; }
    static boolean hasCatalogue() { return hasCatalogue; }

    static boolean send(String action, CompoundTag data) {
        if (!isTraining() || TrainingWorldLauncher.isBusy() || !ClientPlayNetworking.canSend(TrainingActionPayload.ID)) return false;
        ClientPlayNetworking.send(new TrainingActionPayload(action, data));
        return true;
    }

    private static final class TrainingEntryIconButton extends Button {
        private final ItemStack icon = new ItemStack(Items.TARGET);

        TrainingEntryIconButton(Screen parent) {
            super(0, 0, 20, 20, buttonLabel(), b -> openEntry(parent), DEFAULT_NARRATION);
            setTooltip(Tooltip.create(getMessage()));
        }

        @Override
        public void setMessage(Component message) {
            if (message.equals(getMessage())) return;
            super.setMessage(message);
            setTooltip(Tooltip.create(message));
        }

        @Override
        public void renderString(GuiGraphics graphics, Font font, int color) {
            graphics.renderItem(icon, getX() + (getWidth() - 16) / 2,
                    getY() + (getHeight() - 16) / 2);
        }
    }

    private static final class TrainingEntryButton extends SreButton {
        TrainingEntryButton(Screen parent) {
            super(buttonLabel(), b -> openEntry(parent));
            setTooltipText(Component.translatable("training.sre.ui.entry_hint"));
        }
    }
}
