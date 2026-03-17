package org.agmas.noellesroles.mixin.client.roles.exampler;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedHandledScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import org.agmas.noellesroles.client.PlayerPaginationHelper;
import org.agmas.noellesroles.client.RoleScreenHelper;
import org.agmas.noellesroles.client.widget.ExamplerPlayerWidget;
import org.agmas.noellesroles.client.widget.SwapperPlayerWidget;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.GameType;

@Mixin(LimitedInventoryScreen.class)
public abstract class ExamplerScreenMixin extends LimitedHandledScreen<InventoryMenu>
        implements PlayerPaginationHelper.ScreenWithChildren {
    @Unique
    private static final PlayerPaginationHelper.PaginationTextProvider TEXT_PROVIDER = new PlayerPaginationHelper.PaginationTextProvider() {
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
    private RoleScreenHelper<PlayerInfo> roleScreenHelper;

    public ExamplerScreenMixin(InventoryMenu handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    @Unique
    private RoleScreenHelper<PlayerInfo> getRoleScreenHelper() {
        if (roleScreenHelper == null) {
            roleScreenHelper = new RoleScreenHelper<>(
                    player,
                    ModRoles.EXAMPLER,
                    this::createExamplerWidget,
                    TEXT_PROVIDER,
                    this::drawSwapperSelectionHint,
                    this::getEligiblePlayers);
        }
        return roleScreenHelper;
    }

    @Unique
    private ExamplerPlayerWidget createExamplerWidget(int x, int y, PlayerInfo playerEntity, int index) {
        ExamplerPlayerWidget widget = new ExamplerPlayerWidget(
                (LimitedInventoryScreen) (Object) this,
                x, y, playerEntity);
        addDrawableChild(widget);
        return widget;
    }

    @Unique
    private void drawSwapperSelectionHint(GuiGraphics context, java.awt.Point point) {
        Minecraft client = Minecraft.getInstance();
        Component text;
        int color;

        text = Component.translatable("screen.exampler.select_player");
        color = Color.CYAN.getRGB();

        int textWidth = client.font.width(text);
        context.drawString(client.font, text,
                point.x - textWidth / 2, point.y + 40, color);
    }

    @Unique
    private List<PlayerInfo> getEligiblePlayers() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return List.of();
        }

        return client.getConnection().getListedOnlinePlayers().stream()
                .filter(a -> a.getProfile().getId() != player.getUUID() && a.getGameMode() == GameType.ADVENTURE)
                .collect(Collectors.toList());
    }

    @Unique
    private boolean isPlayerInAdventureMode(AbstractClientPlayer targetPlayer) {
        Minecraft client = Minecraft.getInstance();
        PlayerInfo entry = client.player.connection.getPlayerInfo(targetPlayer.getUUID());
        return entry != null && entry.getGameMode() == GameType.ADVENTURE;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void noellesroles$onRender(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        getRoleScreenHelper().onRender(context, this);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void noellesroles$onInit(CallbackInfo ci) {
        SwapperPlayerWidget.playerChoiceOne = null;
        getRoleScreenHelper().onInit(this);

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