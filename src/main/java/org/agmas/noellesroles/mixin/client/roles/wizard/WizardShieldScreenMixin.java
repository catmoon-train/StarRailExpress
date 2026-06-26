package org.agmas.noellesroles.mixin.client.roles.wizard;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedHandledScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.GameType;
import org.agmas.noellesroles.client.PlayerPaginationHelper;
import org.agmas.noellesroles.client.RoleScreenHelper;
import org.agmas.noellesroles.client.widget.WizardShieldWidget;
import org.agmas.noellesroles.game.roles.killer.wizard.WizardPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 巫师“盔甲护身”物品栏屏幕 Mixin：选中该法术时，在背包显示可赋予护盾的玩家列表。
 */
@Mixin(LimitedInventoryScreen.class)
public abstract class WizardShieldScreenMixin extends LimitedHandledScreen<InventoryMenu>
        implements PlayerPaginationHelper.ScreenWithChildren {

    @Unique
    private static final PlayerPaginationHelper.PaginationTextProvider WIZARD_TEXT_PROVIDER =
            new PlayerPaginationHelper.PaginationTextProvider() {
                @Override
                public String getPageTranslationKey() {
                    return "hud.pagination.page";
                }

                @Override
                public String getPrevTranslationKey() {
                    return "hud.pagination.prev";
                }

                @Override
                public String getNextTranslationKey() {
                    return "hud.pagination.next";
                }
            };

    @Shadow
    @Final
    public LocalPlayer player;

    @Unique
    private RoleScreenHelper<PlayerInfo> wizardScreenHelper;

    public WizardShieldScreenMixin(InventoryMenu handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    @Unique
    private RoleScreenHelper<PlayerInfo> getWizardScreenHelper() {
        if (wizardScreenHelper == null) {
            wizardScreenHelper = new RoleScreenHelper<>(
                    player,
                    ModRoles.WIZARD,
                    this::createWizardWidget,
                    WIZARD_TEXT_PROVIDER,
                    this::drawWizardSelectionHint,
                    this::getEligiblePlayers);
        }
        return wizardScreenHelper;
    }

    @Unique
    private net.minecraft.client.gui.components.Button createWizardWidget(int x, int y, PlayerInfo playerEntity,
            int index) {
        WizardShieldWidget widget = new WizardShieldWidget((LimitedInventoryScreen) (Object) this, x, y, playerEntity);
        addDrawableChild(widget);
        return widget;
    }

    @Unique
    private void drawWizardSelectionHint(GuiGraphics context, Point point) {
        Minecraft client = Minecraft.getInstance();
        Component text = Component.translatable("hud.wizard.player_selection");
        int textWidth = client.font.width(text);
        context.drawString(client.font, text, point.x - textWidth / 2, point.y + 40, Color.CYAN.getRGB());
    }

    @Unique
    private List<PlayerInfo> getEligiblePlayers() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return List.of();
        }
        // 仅当选中“盔甲护身”法术时显示
        WizardPlayerComponent comp = WizardPlayerComponent.KEY.get(player);
        if (comp.selectedSpell != WizardPlayerComponent.Spell.ARMOR) {
            return List.of();
        }
        return client.getConnection().getOnlinePlayers().stream()
                .filter(a -> !a.getProfile().getId().equals(player.getUUID())
                        && a.getGameMode() == GameType.ADVENTURE)
                .collect(Collectors.toList());
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void noellesroles$onWizardRender(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        getWizardScreenHelper().onRender(context, this);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void noellesroles$onWizardInit(CallbackInfo ci) {
        if (wizardScreenHelper != null) {
            wizardScreenHelper.getPaginationHelper().clearManagedWidgets(this);
        }
        getWizardScreenHelper().onInit(this);
    }

    @Override
    public void addDrawableChild(net.minecraft.client.gui.components.Button button) {
        super.addRenderableWidget(button);
    }

    @Override
    public void removeDrawableChild(net.minecraft.client.gui.components.Button button) {
        super.removeWidget(button);
    }

    @Override
    public void clearWidgets() {
        super.clearWidgets();
    }

    @Override
    public void clearChildren() {
        super.clearWidgets();
    }
}
