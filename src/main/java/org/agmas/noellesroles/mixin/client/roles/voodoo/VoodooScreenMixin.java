package org.agmas.noellesroles.mixin.client.roles.voodoo;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedHandledScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.client.PlayerPaginationHelper;
import org.agmas.noellesroles.client.RoleScreenHelper;
import org.agmas.noellesroles.client.widget.VoodooPlayerWidget;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.Color;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;

@Mixin(LimitedInventoryScreen.class)
public abstract class VoodooScreenMixin extends LimitedHandledScreen<InventoryMenu> implements PlayerPaginationHelper.ScreenWithChildren {
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

    @Shadow @Final
    public LocalPlayer player;

    @Unique
    private RoleScreenHelper<UUID> roleScreenHelper;

    public VoodooScreenMixin(InventoryMenu handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }


    @Unique
    private RoleScreenHelper<UUID> getRoleScreenHelper() {
        if (roleScreenHelper == null) {
            roleScreenHelper = new RoleScreenHelper<>(
                player,
                ModRoles.VOODOO,
                this::createVoodooWidget,
                TEXT_PROVIDER,
                this::drawVoodooTip,
                this::getEligiblePlayers
            );
        }
        return roleScreenHelper;
    }

    @Unique
    private VoodooPlayerWidget createVoodooWidget(int x, int y, UUID playerUUID, int index) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return null;
        }

        PlayerInfo playerListEntry = client.player.connection.getPlayerInfo(playerUUID);
        if (playerListEntry == null) {
            return null;
        }

        VoodooPlayerWidget widget = new VoodooPlayerWidget(
            (LimitedInventoryScreen) (Object) this,
            x, y, playerUUID, playerListEntry, player.level(),index
        );
        addDrawableChild(widget);
        return widget;
    }

    @Unique
    private void drawVoodooTip(GuiGraphics context, java.awt.Point point) {
        ConfigWorldComponent configComponent = ConfigWorldComponent.KEY.get(player.level());
        if (!configComponent.naturalVoodoosAllowed) {
            Minecraft client = Minecraft.getInstance();
            Component text = Component.translatable("hud.voodoo.tip");
            int textWidth = client.font.width(text);
            context.drawString(client.font, text,
                point.x - textWidth / 2, point.y + 40, Color.RED.getRGB());
        }
    }

    @Unique
    private List<UUID> getEligiblePlayers() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return List.of();
        }

        return client.player.connection.getOnlinePlayerIds().stream()
                .filter(uuid -> !uuid.equals(player.getUUID()))
                .filter(uuid -> {
                    PlayerInfo entry = client.player.connection.getPlayerInfo(uuid);
                    return entry != null ;
                })
                .collect(Collectors.toList());
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void noellesroles$onRender(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        getRoleScreenHelper().onRender(context, this);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void noellesroles$onInit(CallbackInfo ci) {
        getRoleScreenHelper().onInit(this);
    }
}