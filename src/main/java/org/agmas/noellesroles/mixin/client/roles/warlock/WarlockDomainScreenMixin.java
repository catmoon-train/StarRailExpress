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

package org.agmas.noellesroles.mixin.client.roles.warlock;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import org.agmas.noellesroles.client.PlayerPaginationHelper;
import org.agmas.noellesroles.client.RoleScreenHelper;
import org.agmas.noellesroles.client.widget.WarlockDomainWidget;
import org.agmas.noellesroles.game.roles.killer.warlock.WarlockPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.Color;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 咒术师·领域展开选择屏幕 Mixin。
 * 在背包界面列出"已被诅咒且存活"的候选玩家，点选即对其展开领域。
 *
 * <p>复用 {@code ManipulatorScreenMixin} 为 {@link LimitedInventoryScreen} 注入的
 * {@link PlayerPaginationHelper.ScreenWithChildren} 接口（该接口无条件加到类上），
 * 因此本 mixin 不再重复实现该接口，避免方法冲突。
 */
@Mixin(LimitedInventoryScreen.class)
public abstract class WarlockDomainScreenMixin {

    @Unique
    private static final PlayerPaginationHelper.PaginationTextProvider WARLOCK_TEXT_PROVIDER =
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

    @Unique
    private RoleScreenHelper<PlayerInfo> noellesroles$warlockHelper;

    @Unique
    private RoleScreenHelper<PlayerInfo> noellesroles$getWarlockHelper() {
        if (noellesroles$warlockHelper == null) {
            noellesroles$warlockHelper = new RoleScreenHelper<>(
                    Minecraft.getInstance().player,
                    ModRoles.WARLOCK,
                    this::noellesroles$createWarlockWidget,
                    WARLOCK_TEXT_PROVIDER,
                    this::noellesroles$drawWarlockHint,
                    this::noellesroles$getEligibleVictims);
        }
        return noellesroles$warlockHelper;
    }

    @Unique
    private WarlockDomainWidget noellesroles$createWarlockWidget(int x, int y, PlayerInfo playerEntity, int index) {
        WarlockDomainWidget widget = new WarlockDomainWidget(
                (LimitedInventoryScreen) (Object) this, x, y, playerEntity);
        ((PlayerPaginationHelper.ScreenWithChildren) this).addDrawableChild(widget);
        return widget;
    }

    @Unique
    private void noellesroles$drawWarlockHint(GuiGraphics context, java.awt.Point point) {
        Minecraft client = Minecraft.getInstance();
        Component text = Component.translatable("hud.warlock.domain_selection");
        int textWidth = client.font.width(text);
        context.drawString(client.font, text, point.x - textWidth / 2, point.y + 40, Color.RED.getRGB());
    }

    @Unique
    private List<PlayerInfo> noellesroles$getEligibleVictims() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer self = client.player;
        if (client.level == null || self == null) {
            return List.of();
        }
        WarlockPlayerComponent comp = WarlockPlayerComponent.KEY.get(self);
        if (comp == null) {
            return List.of();
        }
        long gameTime = client.level.getGameTime();
        return client.getConnection().getOnlinePlayers().stream()
                .filter(info -> info.getGameMode() == GameType.ADVENTURE)
                .filter(info -> {
                    Long end = comp.cursedPlayers.get(info.getProfile().getId());
                    return end != null && end > gameTime;
                })
                .collect(Collectors.toList());
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void noellesroles$onWarlockRender(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        noellesroles$getWarlockHelper().onRender(context, (PlayerPaginationHelper.ScreenWithChildren) this);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void noellesroles$onWarlockInit(CallbackInfo ci) {
        if (noellesroles$warlockHelper != null) {
            noellesroles$warlockHelper.getPaginationHelper()
                    .clearManagedWidgets((PlayerPaginationHelper.ScreenWithChildren) this);
        }
        noellesroles$getWarlockHelper().onInit((PlayerPaginationHelper.ScreenWithChildren) this);
    }
}
