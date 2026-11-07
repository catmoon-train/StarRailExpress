/*
 * 探囊取物功能移植自 Su_Crispy 的 Su's Instant Swap 2.0.0 (LGPL-3.0)。
 * 原项目：https://github.com/SuCrispy/Sus-InstantSwap
 * 本移植重写输入状态与槽位操作，适配 SRE Pack Compat。
 */
package io.wifi.srepackcompat.swap;

import com.mojang.blaze3d.platform.InputConstants;
import io.wifi.srepackcompat.mixin.swap.ContainerScreenAccessor;
import io.wifi.srepackcompat.mixin.swap.CreativeScreenAccessor;
import io.wifi.srepackcompat.mixin.swap.MouseHandlerAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class InstantSwapClient implements ClientModInitializer {
    public static final KeyMapping GUI_SWAP = new KeyMapping("key.susinstantswap.swap_in_gui",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.susinstantswap");
    private static final SwapGesture GESTURE = new SwapGesture();
    private static SwapConfig config;
    private static Screen gestureScreen;
    private static Screen closeScreen;
    private static int closeTicks;
    private static boolean openConfig;

    public static SwapConfig config() { return config; }

    @Override
    public void onInitializeClient() {
        config = SwapConfig.load();
        KeyBindingHelper.registerKeyBinding(GUI_SWAP);
        ClientTickEvents.END_CLIENT_TICK.register(InstantSwapClient::tick);
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (config.modEnabled && supported(screen)) {
                if (GESTURE.isHeld() && gestureScreen == null) gestureScreen = screen;
                if (config.mouseReposition) reposition(mc, screen);
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
                literal("sreinstantswap").executes(context -> {
                    openConfig = true;
                    return 1;
                })));
    }

    /** 只追踪从游戏内开始的背包键；不开背包，不绕过核心的按键限制。 */
    public static boolean key(long window, int key, int scanCode, int action) {
        Minecraft mc = Minecraft.getInstance();
        if (window != mc.getWindow().getWindow()) return false;
        if (config == null || !config.modEnabled || mc.player == null || mc.gameMode == null) {
            reset();
            return false;
        }
        if (mc.options.keyInventory.matches(key, scanCode)) {
            if (action == GLFW.GLFW_PRESS && mc.screen == null) {
                GESTURE.press(System.nanoTime());
                gestureScreen = null;
            } else if (action == GLFW.GLFW_REPEAT && GESTURE.isHeld()) {
                return true;
            } else if (action == GLFW.GLFW_RELEASE) {
                release(mc);
            } else if (action == GLFW.GLFW_PRESS && supported(mc.screen)
                    && config.guiSwapEnabled && GUI_SWAP.isUnbound() && !editing(mc.screen)) {
                // 未单独绑定时，复用背包键进行界面内交换；失败时保留原版关闭行为。
                return swap(mc, mc.screen);
            }
        }
        if (action == GLFW.GLFW_PRESS && config.guiSwapEnabled && GUI_SWAP.matches(key, scanCode)
                && supported(mc.screen) && !editing(mc.screen)) {
            swap(mc, mc.screen);
            return true;
        }
        return false;
    }

    private static void tick(Minecraft mc) {
        if (openConfig) {
            openConfig = false;
            mc.setScreen(new SwapConfigScreen(mc.screen));
        }
        if (config == null || !config.modEnabled || mc.player == null || mc.gameMode == null
                || !mc.isWindowActive()) {
            reset();
            return;
        }
        if (closeTicks > 0 && --closeTicks == 0) {
            if (mc.screen == closeScreen) closeScreen.onClose();
            closeScreen = null;
        }
        if (GESTURE.isHeld()) {
            if (gestureScreen == null || mc.screen != gestureScreen) {
                GESTURE.reset();
                gestureScreen = null;
            } else {
                // 某些界面会消费 release；物理状态兜底，兼容键盘和鼠标绑定。
                InputConstants.Key bound = KeyBindingHelper.getBoundKeyOf(mc.options.keyInventory);
                boolean down = switch (bound.getType()) {
                    case KEYSYM -> bound.getValue() != GLFW.GLFW_KEY_UNKNOWN
                            && InputConstants.isKeyDown(mc.getWindow().getWindow(), bound.getValue());
                    case MOUSE -> GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), bound.getValue()) == GLFW.GLFW_PRESS;
                    case SCANCODE -> mc.options.keyInventory.isDown();
                };
                if (!down) release(mc);
            }
        }
    }

    private static void release(Minecraft mc) {
        boolean longPress = GESTURE.release(System.nanoTime(), config.holdThresholdMs);
        if (longPress && gestureScreen != null && mc.screen == gestureScreen) {
            if (!swap(mc, gestureScreen)) scheduleClose(gestureScreen);
        }
        gestureScreen = null;
    }

    private static boolean supported(Screen screen) {
        return screen instanceof AbstractContainerScreen<?> || screen instanceof LimitedSwapScreen;
    }

    private static boolean swap(Minecraft mc, Screen screen) {
        var menu = ((MenuAccess<?>) screen).getMenu();
        if (mc.player.isSpectator() || !menu.getCarried().isEmpty() || editing(screen)) return false;
        double x = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double y = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        Slot target = null;
        if (screen instanceof LimitedSwapScreen limited) {
            target = limited.sreSwap$slotAt(x, y);
        } else {
            ContainerScreenAccessor access = (ContainerScreenAccessor) screen;
            for (Slot slot : menu.slots) {
                if (slot.isActive() && access.sreSwap$isHovering(slot, x, y)) {
                    target = slot;
                    break;
                }
            }
        }
        if (target == null || (!target.hasItem() && !config.emptySlotSwapEnabled)
                || !target.mayPickup(mc.player)) return false;
        int selected = mc.player.getInventory().selected;
        ItemStack hand = mc.player.getInventory().getItem(selected);
        if (!target.hasItem() && hand.isEmpty()) return false;
        if (target.container == mc.player.getInventory() && target.getContainerSlot() == selected) return false;
        if (screen instanceof InventoryScreen && target.container != mc.player.getInventory()) return false;
        if (screen instanceof CreativeModeInventoryScreen creative
                && ((CreativeScreenAccessor) creative).sreSwap$isCatalogSlot(target)) {
            if (!target.hasItem()) return false;
            // 创造列表取一个物品；先安置原手持物，满背包时拒绝而不是覆盖。
            int free = -1;
            if (!hand.isEmpty()) {
                for (int i = 0; i < 36; i++) {
                    if (i != selected && mc.player.getInventory().getItem(i).isEmpty()) { free = i; break; }
                }
                if (free < 0) return false;
                setCreative(mc, free, hand.copy());
            }
            setCreative(mc, selected, target.getItem().copyWithCount(1));
        } else {
            if (!hand.isEmpty() && !target.mayPlace(hand)) return false;
            // 动态分派到创造界面的原版覆写，正确处理包装槽位、盔甲与副手。
            if (screen instanceof LimitedSwapScreen limited) {
                if (!limited.sreSwap$swap(target, selected)) return false;
            } else {
                ((ContainerScreenAccessor) screen).sreSwap$click(target, target.index, selected, ClickType.SWAP);
            }
        }
        if (config.soundEnabled) mc.player.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8F, 1.0F);
        if (config.debug) LoggerFactory.getLogger(InstantSwapClient.class).info("探囊取物: menu={} slot={} hotbar={}",
                menu.containerId, target.index, selected);
        scheduleClose(screen);
        return true;
    }

    private static void setCreative(Minecraft mc, int index, ItemStack stack) {
        mc.player.getInventory().setItem(index, stack);
        mc.gameMode.handleCreativeModeItemAdd(stack, SwapGesture.inventoryMenuSlot(index));
    }

    private static boolean editing(Screen screen) {
        return screen.getFocused() instanceof EditBox box && box.isVisible();
    }

    private static void reposition(Minecraft mc, Screen screen) {
        double scale = mc.getWindow().getGuiScale();
        double x;
        double y;
        if (screen instanceof LimitedSwapScreen limited) {
            double[] corner = limited.sreSwap$cursorCorner();
            if (corner == null) return;
            x = corner[0] * scale;
            y = corner[1] * scale;
        } else {
            ContainerScreenAccessor access = (ContainerScreenAccessor) screen;
            x = (access.sreSwap$getLeft() + access.sreSwap$getWidth()) * scale - 5;
            y = (access.sreSwap$getTop() + access.sreSwap$getHeight()) * scale - 5;
        }
        GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), x, y);
        // 同步本帧坐标，不需要通过抑制槽位悬停来掩盖旧鼠标位置。
        ((MouseHandlerAccessor) mc.mouseHandler).sreSwap$setX(x);
        ((MouseHandlerAccessor) mc.mouseHandler).sreSwap$setY(y);
    }

    private static void scheduleClose(Screen screen) {
        closeScreen = screen;
        closeTicks = screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen
                || screen instanceof LimitedSwapScreen ? 1 : 2;
    }

    private static void reset() {
        GESTURE.reset();
        gestureScreen = null;
        closeScreen = null;
        closeTicks = 0;
    }
}
