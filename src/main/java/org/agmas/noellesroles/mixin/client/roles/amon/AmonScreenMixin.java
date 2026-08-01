/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.mixin.client.roles.amon;

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
import org.agmas.noellesroles.client.widget.AmonPlayerWidget;
import org.agmas.noellesroles.game.roles.neutral.amon.AmonPlayerComponent;
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
 * 阿蒙物品栏屏幕 Mixin：在背包界面列出可夺舍的成熟宿主供点选锁定（「操纵」）。
 * 复用操纵师同款分页/选人组件（{@link RoleScreenHelper}）。
 */
@Mixin(LimitedInventoryScreen.class)
public abstract class AmonScreenMixin extends LimitedHandledScreen<InventoryMenu> implements PlayerPaginationHelper.ScreenWithChildren {
    @Unique
    private static final PlayerPaginationHelper.PaginationTextProvider AMON_TEXT_PROVIDER = new PlayerPaginationHelper.PaginationTextProvider() {
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

    @Shadow @Final
    public LocalPlayer player;

    @Unique
    private RoleScreenHelper<PlayerInfo> amonRoleScreenHelper;

    public AmonScreenMixin(InventoryMenu handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    @Unique
    private RoleScreenHelper<PlayerInfo> getAmonRoleScreenHelper() {
        if (amonRoleScreenHelper == null) {
            amonRoleScreenHelper = new RoleScreenHelper<PlayerInfo>(
                    player,
                    ModRoles.AMON,
                    this::createAmonWidget,
                    AMON_TEXT_PROVIDER,
                    this::drawAmonSelectionHint,
                    this::getAmonEligiblePlayers
            );
        }
        return amonRoleScreenHelper;
    }

    @Unique
    private AmonPlayerWidget createAmonWidget(int x, int y, PlayerInfo playerEntity, int index) {
        AmonPlayerWidget widget = new AmonPlayerWidget(
                (LimitedInventoryScreen) (Object) this,
                x, y, playerEntity
        );
        addDrawableChild(widget);
        return widget;
    }

    @Unique
    private void drawAmonSelectionHint(GuiGraphics context, java.awt.Point point) {
        Minecraft client = Minecraft.getInstance();
        Component text = Component.translatable("hud.amon.player_selection");
        int color = new Color(170, 0, 170).getRGB();

        int textWidth = client.font.width(text);
        context.drawString(client.font, text,
                point.x - textWidth / 2, point.y + 40, color);
    }

    @Unique
    private List<PlayerInfo> getAmonEligiblePlayers() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return List.of();
        }

        // 只显示已成熟、可夺舍的宿主（成熟宿主 UUID 仅同步给阿蒙本人）。
        AmonPlayerComponent comp = AmonPlayerComponent.KEY.get(player);
        java.util.Set<java.util.UUID> matured = comp.clientMaturedHosts;
        if (matured.isEmpty()) {
            return List.of();
        }
        return client.getConnection().getOnlinePlayers().stream()
                .filter(a -> matured.contains(a.getProfile().getId())
                        && a.getProfile().getId() != player.getUUID()
                        && a.getGameMode() == GameType.ADVENTURE)
                .collect(Collectors.toList());
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void noellesroles$onAmonRender(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        getAmonRoleScreenHelper().onRender(context, this);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void noellesroles$onAmonInit(CallbackInfo ci) {
        if (amonRoleScreenHelper != null) {
            amonRoleScreenHelper.getPaginationHelper().clearManagedWidgets(this);
        }
        getAmonRoleScreenHelper().onInit(this);
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
