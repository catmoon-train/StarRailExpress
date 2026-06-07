package io.wifi.starrailexpress.customrole;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton.AccentSide;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class CustomRoleManageScreen extends Screen {

    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 380;
    private int panelLeftX, panelTopY;
    private final java.util.function.Supplier<Screen> backScreenSupplier;
    private List<CustomRoleData> roles = new ArrayList<>();

    public CustomRoleManageScreen(java.util.function.Supplier<Screen> backSupplier) {
        super(Component.translatable("sre.custom_role.manage.title"));
        this.backScreenSupplier = backSupplier;
    }

    public CustomRoleManageScreen(Screen backScreen) {
        super(Component.translatable("sre.custom_role.manage.title"));
        this.backScreenSupplier = () -> backScreen;
    }

    @Override
    protected void init() {
        panelLeftX = (width - PANEL_WIDTH) / 2;
        panelTopY = (height - PANEL_HEIGHT) / 2;

        CustomRoleConfig config = CustomRoleConfig.loadPreferWorldPath(minecraft.getSingleplayerServer());
        roles = config.roles;

        int yOffset = panelTopY + 34;

        for (int i = 0; i < Math.min(roles.size(), 12); i++) {
            final int idx = i;
            CustomRoleData role = roles.get(i);
            int y = yOffset + i * 24;

            String displayText = role.englishId + (role.displayName.isEmpty() ? "" : " (" + role.displayName + ")");
            ModernButton nameBtn = ModernButton.builder(
                Component.literal(displayText), b -> {
                    minecraft.setScreen(new CustomRoleScreen(role));
                }).bounds(panelLeftX + 10, y, 200, 20).accentBar(AccentSide.LEFT).build();
            addRenderableWidget(nameBtn);

            ModernButton delBtn = ModernButton.builder(Component.literal("X"), b -> {
                config.roles.remove(idx);
                config.savePreferWorldPath(minecraft.getSingleplayerServer());
                var server = minecraft.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> { try { io.wifi.starrailexpress.customrole.CustomRoleLoader.reload(server); } catch (Exception ignored) {} });
                }
                init(minecraft, width, height);
            }).bounds(panelLeftX + 220, y, 30, 20).accentBar(AccentSide.RIGHT).build();
            addRenderableWidget(delBtn);
        }

        ModernButton backBtn = ModernButton.builder(Component.translatable("sre.custom_role.back"), b -> {
            minecraft.setScreen(backScreenSupplier.get());
        }).bounds(panelLeftX + 10, panelTopY + PANEL_HEIGHT - 28, 80, 20)
            .accentBar(AccentSide.BOTTOM).build();
        addRenderableWidget(backBtn);

        ModernButton newBtn = ModernButton.builder(Component.translatable("sre.custom_role.new"), b -> {
            minecraft.setScreen(new CustomRoleScreen());
        }).bounds(panelLeftX + 100, panelTopY + PANEL_HEIGHT - 28, 80, 20)
            .accentBar(AccentSide.BOTTOM).build();
        addRenderableWidget(newBtn);
    }

    @Override
    public void renderBackground(GuiGraphics g, int i, int j, float f) {
        g.fill(panelLeftX - 6, panelTopY - 3, panelLeftX + PANEL_WIDTH + 6, panelTopY + PANEL_HEIGHT + 3, 0xCC080C18);
        g.fill(panelLeftX - 6, panelTopY - 3, panelLeftX + PANEL_WIDTH + 6, panelTopY - 2, 0xFF5577CC);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int cx = panelLeftX + PANEL_WIDTH / 2;
        g.drawCenteredString(font,
            Component.translatable("sre.custom_role.manage.title")
                .withStyle(s -> s.withColor(0x55BBFF).withBold(true)),
            cx, panelTopY + 10, 0xFFFFFF);

        // Draw role info (color preview & type)
        int yOffset = panelTopY + 34;
        for (int i = 0; i < Math.min(roles.size(), 12); i++) {
            CustomRoleData role = roles.get(i);
            int y = yOffset + i * 24;
            int color = 0xFF000000 | (role.colorR << 16) | (role.colorG << 8) | role.colorB;
            String info = (role.isInnocent ? "[平民]" : (role.canUseKiller ? "[杀手]" : "[中立]"))
                + " " + role.moodType + " max:" + role.maxCount;
            g.drawString(font, Component.literal(info).withStyle(Style.EMPTY.withColor(color)),
                panelLeftX + 260, y + 4, 0xFFFFFF, false);
        }

        if (roles.isEmpty()) {
            g.drawCenteredString(font,
                Component.translatable("sre.custom_role.manage.empty")
                    .withStyle(s -> s.withColor(0x778899)),
                cx, panelTopY + PANEL_HEIGHT / 2, 0xFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
