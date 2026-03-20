package io.wifi.starrailexpress.client.gui.screen.ingame;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.StoreRenderer;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.network.original.StoreBuyPayload;
import io.wifi.starrailexpress.util.ShopEntry;
import io.wifi.ConfigCompact.ui.SettingMenuScreen;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

import org.agmas.noellesroles.client.screen.GameManagementScreen;
import org.agmas.noellesroles.client.screen.GuessRoleScreen;
import org.agmas.noellesroles.client.screen.RoleIntroduceScreen;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)

public class LimitedInventoryScreen extends LimitedHandledScreen<InventoryMenu> {


    public static final ResourceLocation BACKGROUND_TEXTURE = SRE
            .watheId("textures/gui/container/limited_inventory.png");
    // public static final @NotNull ResourceLocation ID =
    // SRE.watheId("textures/gui/game.png");
    public final LocalPlayer player;

    public LimitedInventoryScreen(@NotNull LocalPlayer player) {
        super(player.inventoryMenu, player.getInventory(), Component.empty());
        this.player = player;
    }

    public Button menuButton = null;
    public static final int menuButtonHeight = 20;
    public static final int menuButtonWidth = 100;
    public ArrayList<Button> menuSelections = new ArrayList<>();
    public boolean isMenuOpen = false;

    public void toggleViewMenu(boolean flag) {
        this.isMenuOpen = flag;
        menuButton.setMessage(
                Component.translatable("screen.limited_inventory.button.menu." + (!isMenuOpen ? "show" : "hide")));

        {
            for (var ms : menuSelections) {
                ms.visible = this.isMenuOpen;
                ms.active = this.isMenuOpen;
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        initMenuSelections();
        List<ShopEntry> entries = getShopEntries();
        if (entries.isEmpty())
            return;
        int apart = 38;
        int x = this.width / 2 - entries.size() * apart / 2 + 9;
        int y = this.y - 46;
        final var gameComponent = SREClient.gameComponent;
        if (gameComponent != null) {
            final var role = gameComponent.getRole(player);
            if (role.getAddChild() != null) {
                role.getAddChild().accept(this);
            }
        }
        for (int i = 0; i < entries.size(); i++) {
            this.addRenderableWidget(new StoreItemWidget(this, x + apart * i, y, entries.get(i), i));
        }
    }

    public void initMenuSelections() {
        menuButton = Button.builder(Component.translatable("screen.limited_inventory.button.menu"), (btn) -> {
            toggleViewMenu(!this.isMenuOpen);
        }).bounds(width - menuButtonWidth, height - menuButtonHeight, menuButtonWidth, menuButtonHeight).build();
        this.addRenderableWidget(menuButton);

        this.menuSelections.clear();
        {
            int startY = height - menuButtonHeight;
            // 添加菜单按钮
            {
                // 职业介绍
                var btn1 = Button
                        .builder(Component.translatable("screen.limited_inventory.menu.introduction"), (btn) -> {
                            var role = SREGameWorldComponent.KEY.get(this.minecraft.level).getRole(this.minecraft.player);
                            var screen = new RoleIntroduceScreen(this, role);
                            this.minecraft.setScreen(screen);
                            toggleViewMenu(false);
                        }).bounds(width - menuButtonWidth, startY - menuButtonHeight, menuButtonWidth, menuButtonHeight)
                        .build();
                this.menuSelections.add(btn1);
                startY -= menuButtonHeight;
            }
            {
                // 职业猜测
                var btn1 = Button
                        .builder(Component.translatable("screen.limited_inventory.menu.role_guess"), (btn) -> {
                            var screen = new GuessRoleScreen(this);
                            this.minecraft.setScreen(screen);
                            toggleViewMenu(false);
                        }).bounds(width - menuButtonWidth, startY - menuButtonHeight, menuButtonWidth, menuButtonHeight)
                        .build();
                this.menuSelections.add(btn1);
                startY -= menuButtonHeight;
            }
            if (this.minecraft.player.hasPermissions(2)) {
                // mod_settings
                var btn1 = Button
                        .builder(Component.translatable("screen.limited_inventory.menu.mod_settings")
                                .withStyle(ChatFormatting.RED), (btn) -> {
                                    var screen = new SettingMenuScreen(this);
                                    this.minecraft.setScreen(screen);
                                    toggleViewMenu(false);
                                })
                        .bounds(width - menuButtonWidth, startY - menuButtonHeight, menuButtonWidth, menuButtonHeight)
                        .build();
                this.menuSelections.add(btn1);
                startY -= menuButtonHeight;
            }
            if (this.minecraft.player.hasPermissions(2)) {
                // game_menu
                var btn1 = Button
                        .builder(Component.translatable("screen.limited_inventory.menu.game_menu")
                                .withStyle(ChatFormatting.RED), (btn) -> {
                                    var screen = new GameManagementScreen(this);
                                    this.minecraft.setScreen(screen);
                                    toggleViewMenu(false);
                                })
                        .bounds(width - menuButtonWidth, startY - menuButtonHeight, menuButtonWidth, menuButtonHeight)
                        .build();
                this.menuSelections.add(btn1);
                startY -= menuButtonHeight;
            }
        }

        for (var ms : menuSelections) {
            this.addRenderableWidget(ms);
        }
        toggleViewMenu(false);
    }

    public <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T drawableElement) {
        ((DrawableGet) this).getDrawable().add(drawableElement);
        return (T) this.addWidget(drawableElement);
    }

    public static List<ShopEntry> getRoleShopEntries(SRERole role) {
        if (role == null)
            return List.of();
        final var shopEntries = ShopContent.getShopEntries(
                role.getIdentifier());
        if (!shopEntries.isEmpty()) {
            return shopEntries;
        }
        if (role.canUseKiller()) {
            return ShopContent.defaultEntries;
        }
        return List.of();
    }

    public List<ShopEntry> getShopEntries() {
        final var player = Minecraft.getInstance().player;
        var gameWorldComponent = SREClient.gameComponent;
        if (gameWorldComponent == null)
            return List.of();
        if (SREClient.gameComponent != null && SREClient.isPlayerAliveAndInSurvival()) {
            final var role = gameWorldComponent.getRole(player);
            if (role == null)
                return List.of();
            final var shopEntries = ShopContent.getShopEntries(
                    role.getIdentifier());
            if (!shopEntries.isEmpty()) {
                return shopEntries;
            }
        }
        if (gameWorldComponent.canUseKillerFeatures(player)) {
            return ShopContent.defaultEntries;
        }
        return List.of();
    }

    @Override
    protected void drawBackground(@NotNull GuiGraphics context, float delta, int mouseX, int mouseY) {
        context.blit(BACKGROUND_TEXTURE, this.x, this.y, 0, 0, this.backgroundWidth, this.backgroundHeight);

        context.pose().pushPose();
        context.pose().translate(context.guiWidth() / 2f, context.guiHeight(), 0);
        // float scale = 0.28f;
        // context.pose().scale(scale, scale, 1f);
        // int height = 254;
        // int width = 497;
        // context.pose().translate(0, -230, 0);
        // int xOffset = 0;
        // int yOffset = 0;
        // // context.innerBlit(ID, (int) (xOffset - width / 2f), (int) (xOffset + width
        // / 2f), (int) (yOffset - height / 2f),
        // (int) (yOffset + height / 2f), 0, 0, 1f, 0, 1f, 1f, 1f, 1f, 1f);
        context.pose().popPose();
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
        StoreRenderer.renderHud(this.font, this.player, context, delta);
    }

    public static class StoreItemWidget extends Button {
        public final LimitedInventoryScreen screen;
        public final ShopEntry entry;

        public StoreItemWidget(LimitedInventoryScreen screen, int x, int y, @NotNull ShopEntry entry, int index) {
            super(x, y, 16, 16, entry.stack().getHoverName(),
                    (a) -> ClientPlayNetworking.send(new StoreBuyPayload(index)), DEFAULT_NARRATION);
            this.screen = screen;
            this.entry = entry;
        }

        @Override
        protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
            super.renderWidget(context, mouseX, mouseY, delta);
            context.blitSprite(entry.type().getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);

            // - 7, 30, 30);
            context.renderItem(this.entry.stack(), this.getX(), this.getY());
            if (this.isHovered()) {
                this.screen.renderLimitedInventoryTooltip(context, this.entry.stack());
                drawShopSlotHighlight(context, this.getX(), this.getY(), 0);
            }
            MutableComponent price = Component.literal(this.entry.price() + "\uE781");
            context.renderTooltip(this.screen.font, price, this.getX() - 4 - this.screen.font.width(price) / 2,
                    this.getY() - 9);
        }

        private void drawShopSlotHighlight(GuiGraphics context, int x, int y, int z) {
            int color = 0x90FFBF49;
            // context.fillGradient(RenderLayer.getGuiOverlay(), x, y, x + 16, y + 16,
            // color, color, z);
            context.fillGradient(RenderType.guiOverlay(), x, y, x + 16, y + 14, color, color, z);
            context.fillGradient(RenderType.guiOverlay(), x, y + 14, x + 15, y + 15, color, color, z);
            context.fillGradient(RenderType.guiOverlay(), x, y + 15, x + 14, y + 16, color, color, z);
        }

        @Override
        public void renderString(GuiGraphics context, Font textRenderer, int color) {
        }
    }
}