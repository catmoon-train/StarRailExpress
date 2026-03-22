package io.wifi.starrailexpress.mixin.client.ui;

import java.util.ArrayList;

import org.agmas.noellesroles.client.screen.GameManagementScreen;
import org.agmas.noellesroles.client.screen.GuessRoleScreen;
import org.agmas.noellesroles.client.screen.RoleIntroduceScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.wifi.ConfigCompact.ui.SettingMenuScreen;
import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

// ✅ 必须加 abstract
@Mixin(InventoryScreen.class)
public abstract class GameMenu extends Screen {
    protected GameMenu(Component component) {
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

        int startY = height - SRE$MENU_BUTTON_HEIGHT;

        // 职业介绍
        var btn1 = org.agmas.noellesroles.client.widget.custom_button.ModernButton
                .builder(Component.translatable("screen.limited_inventory.menu.introduction"), (btn) -> {
                    // ✅ 空指针检查
                    if (this.minecraft.level == null || this.minecraft.player == null) return;
                    var role = SREGameWorldComponent.KEY.get(this.minecraft.level)
                            .getRole(this.minecraft.player);
                    this.minecraft.setScreen(new RoleIntroduceScreen(this, role));
                    sre$toggleViewMenu(false);
                })
                .bounds(width - SRE$MENU_BUTTON_WIDTH,
                        startY -= SRE$MENU_BUTTON_HEIGHT,
                        SRE$MENU_BUTTON_WIDTH,
                        SRE$MENU_BUTTON_HEIGHT)
                .build();
        sre$menuSelections.add(btn1);

        // 职业猜测
        var btn2 = org.agmas.noellesroles.client.widget.custom_button.ModernButton
                .builder(Component.translatable("screen.limited_inventory.menu.role_guess"), (btn) -> {
                    this.minecraft.setScreen(new GuessRoleScreen(this));
                    sre$toggleViewMenu(false);
                })
                .bounds(width - SRE$MENU_BUTTON_WIDTH,
                        startY -= SRE$MENU_BUTTON_HEIGHT,
                        SRE$MENU_BUTTON_WIDTH,
                        SRE$MENU_BUTTON_HEIGHT)
                .build();
        sre$menuSelections.add(btn2);

        if (this.minecraft.player != null && this.minecraft.player.hasPermissions(2)) {
            // OP: mod_settings + game_menu
            var btnSettings = org.agmas.noellesroles.client.widget.custom_button.ModernButton
                    .builder(Component.translatable("screen.limited_inventory.menu.mod_settings")
                            .withStyle(ChatFormatting.RED), (btn) -> {
                                this.minecraft.setScreen(new SettingMenuScreen(this));
                                sre$toggleViewMenu(false);
                            })
                    .bounds(width - SRE$MENU_BUTTON_WIDTH,
                            startY -= SRE$MENU_BUTTON_HEIGHT,
                            SRE$MENU_BUTTON_WIDTH,
                            SRE$MENU_BUTTON_HEIGHT)
                    .build();
            sre$menuSelections.add(btnSettings);

            var btnGame = org.agmas.noellesroles.client.widget.custom_button.ModernButton
                    .builder(Component.translatable("screen.limited_inventory.menu.game_menu")
                            .withStyle(ChatFormatting.RED), (btn) -> {
                                this.minecraft.setScreen(new GameManagementScreen(this));
                                sre$toggleViewMenu(false);
                            })
                    .bounds(width - SRE$MENU_BUTTON_WIDTH,
                            startY - SRE$MENU_BUTTON_HEIGHT,
                            SRE$MENU_BUTTON_WIDTH,
                            SRE$MENU_BUTTON_HEIGHT)
                    .build();
            sre$menuSelections.add(btnGame);
        } else {
            // 普通玩家: client settings
            var btnClient = org.agmas.noellesroles.client.widget.custom_button.ModernButton
                    .builder(Component.translatable("screen.limited_inventory.menu.mod_settings_client")
                            .withStyle(ChatFormatting.RED), (btn) -> {
                                this.minecraft.setScreen(
                                        SREClientConfig.HANDLER.generateGui().generateScreen(this));
                                sre$toggleViewMenu(false);
                            })
                    .bounds(width - SRE$MENU_BUTTON_WIDTH,
                            startY - SRE$MENU_BUTTON_HEIGHT,
                            SRE$MENU_BUTTON_WIDTH,
                            SRE$MENU_BUTTON_HEIGHT)
                    .build();
            sre$menuSelections.add(btnClient);
        }

        for (var ms : sre$menuSelections) {
            this.addRenderableWidget(ms);
        }

        // ✅ 初始化时重置菜单状态
        sre$isMenuOpen = false;
        sre$toggleViewMenu(false);
    }
}