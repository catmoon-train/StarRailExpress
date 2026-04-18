package io.wifi.starrailexpress.mixin.client.ui;

import io.wifi.starrailexpress.client.gui.screen.ingame.GameMenuEntries;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

// ✅ 必须加 abstract
@Mixin(InventoryScreen.class)
public abstract class SpectatorQuickGameMenu extends Screen {
    protected SpectatorQuickGameMenu(Component component) {
        super(component);
    }

    @Unique
    private Button sre$menuButton = null; // ✅ 加前缀防止命名冲突
    @Unique
    private static final int SRE$MENU_BUTTON_HEIGHT = 20;
    @Unique
    private static final int SRE$MENU_BUTTON_WIDTH = 100;
    @Unique
    private final ArrayList<Button> sre$menuSelections = new ArrayList<>();
    @Unique
    private boolean sre$isMenuOpen = false;

    @Unique
    private void sre$toggleViewMenu(boolean flag) {
        this.sre$isMenuOpen = flag;
        sre$menuButton.setMessage(
                Component.translatable("screen.limited_inventory.button.menu."
                        + (!sre$isMenuOpen ? "show" : "hide")));
        for (var ms : sre$menuSelections) {
            ms.visible = this.sre$isMenuOpen;
            ms.active = this.sre$isMenuOpen;
        }
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void sre$initMenu(CallbackInfo ci) {
        sre$initMenuSelections();
    }

    @Unique
    private void sre$initMenuSelections() {
        // ✅ 每次 init 清空旧引用（屏幕 resize 会重复调用）
        sre$menuSelections.clear();
        sre$menuButton = null;

        sre$menuButton = org.agmas.noellesroles.client.widget.custom_button.ModernButton
                .builder(Component.translatable("screen.limited_inventory.button.menu"), (btn) -> {
                    sre$toggleViewMenu(!this.sre$isMenuOpen);
                })
                .bounds(width - SRE$MENU_BUTTON_WIDTH,
                        height - SRE$MENU_BUTTON_HEIGHT,
                        SRE$MENU_BUTTON_WIDTH,
                        SRE$MENU_BUTTON_HEIGHT)
                .accentColor(new java.awt.Color(34, 177, 76).getRGB())
                .build();
        this.addRenderableWidget(sre$menuButton);

        sre$menuSelections.clear();
        sre$menuSelections.addAll(
                GameMenuEntries.register(width, height, minecraft, this, this::sre$toggleViewMenu));
        for (var ms : sre$menuSelections) {
            this.addRenderableWidget(ms);
        }

        // ✅ 初始化时重置菜单状态
        sre$isMenuOpen = false;
        sre$toggleViewMenu(false);
    }
}