package io.wifi.starrailexpress.client.gui.screen.map_dev.modules;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.scenery.client.SceneAssetClient;
import io.wifi.starrailexpress.client.gui.screen.map_dev.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton.AccentSide;
import java.util.List;

public class SceneModule implements TabModule {
    private EditBox sceneIdBox;

    @Override
    public Component getTabTitle() {
        return Component.translatable("sre.map_helper.tab.scene");
    }

    @Override
    public void init(LayoutContext layout, ModuleContext ctx, List<WidgetPlacement> placements) {
        int y = 0, gap = 10, bh = 22;
        int leftX = layout.leftColumnX(), rightX = layout.rightColumnX(2, gap);
        int fullWidth = layout.contentWidth();
        AreasWorldComponent areas = SREClient.areaComponent;

        int sceneBoxWidth = Math.min(190, fullWidth - 64 - 64 - 2 * gap);
        sceneIdBox = new EditBox(layout.font, leftX, y, sceneBoxWidth, bh,
                Component.translatable("sre.map_helper.scene_id"));
        sceneIdBox.setMaxLength(128);
        if (areas != null)
            sceneIdBox.setValue(areas.getSceneId());
        placements.add(new WidgetPlacement(sceneIdBox, y));

        placements.add(new WidgetPlacement(
                ModernButton.builder(Component.translatable("sre.map_helper.assign_scene"), b -> {
                    String id = sceneIdBox.getValue().trim();
                    if (!id.isEmpty())
                        ctx.sendOnly("sre:scene library assign "
                                + ctx.quoteCommandArgument(id));
                }).bounds(leftX + sceneBoxWidth + gap, y, 64, bh).accentBar(AccentSide.BOTTOM).build(),
                y));
        placements.add(new WidgetPlacement(
                ModernButton.builder(Component.translatable("sre.map_helper.scene_editor"),
                        b -> ctx.sendOnly("sre:scene manager"))
                        .bounds(leftX + sceneBoxWidth + gap + 64 + gap, y, 64, bh)
                        .accentBar(AccentSide.RIGHT).build(),
                y));

        int row1 = y + bh + gap;
        placements.add(new WidgetPlacement(
                ModernButton.builder(Component.translatable("sre.map_helper.detach_scene"),
                        b -> ctx.sendOnly("sre:scene library detach"))
                        .bounds(leftX, row1, layout.columnWidth(2, gap), bh)
                        .accentBar(AccentSide.LEFT).build(),
                row1));
        placements.add(new WidgetPlacement(ModernButton
                .builder(Component.translatable("sre.map_helper.list_scene_library"),
                        b -> ctx.sendOnly("sre:scene library list"))
                .bounds(rightX, row1, layout.columnWidth(2, gap), bh).accentBar(AccentSide.RIGHT)
                .build(), row1));

        int row2 = row1 + bh + gap;
        placements.add(new WidgetPlacement(
                ModernButton.builder(Component.translatable("sre.map_helper.toggle_preview"),
                        b -> SceneAssetClient.setPreviewEnabled(
                                !SceneAssetClient.isPreviewEnabled()))
                        .bounds(leftX, row2, layout.columnWidth(2, gap), bh)
                        .accentBar(AccentSide.LEFT).build(),
                row2));
        placements.add(new WidgetPlacement(ModernButton
                .builder(Component.translatable("sre.map_helper.toggle_scroll"),
                        b -> SceneAssetClient
                                .setPreviewPaused(!SceneAssetClient.isPreviewPaused()))
                .bounds(rightX, row2, layout.columnWidth(2, gap), bh).accentBar(AccentSide.RIGHT)
                .build(), row2));

        int row3 = row2 + bh + gap;
        placements.add(new WidgetPlacement(
                ModernButton.builder(Component.translatable("sre.map_helper.preview_alpha_down"),
                        b -> SceneAssetClient.setPreviewAlpha(
                                SceneAssetClient.getPreviewAlpha() - 0.05F))
                        .bounds(leftX, row3, layout.columnWidth(2, gap), bh)
                        .accentBar(AccentSide.LEFT).build(),
                row3));
        placements.add(new WidgetPlacement(ModernButton
                .builder(Component.translatable("sre.map_helper.preview_alpha_up"),
                        b -> SceneAssetClient.setPreviewAlpha(
                                SceneAssetClient.getPreviewAlpha() + 0.05F))
                .bounds(rightX, row3, layout.columnWidth(2, gap), bh).accentBar(AccentSide.RIGHT)
                .build(), row3));

        int row4 = row3 + bh + gap;
        placements.add(new WidgetPlacement(
                ModernButton.builder(Component.translatable("sre.map_helper.preview_speed_down"),
                        b -> SceneAssetClient.setPreviewSpeed(
                                SceneAssetClient.getPreviewSpeed() - 0.25F))
                        .bounds(leftX, row4, layout.columnWidth(2, gap), bh)
                        .accentBar(AccentSide.LEFT).build(),
                row4));
        placements.add(new WidgetPlacement(ModernButton
                .builder(Component.translatable("sre.map_helper.preview_speed_up"),
                        b -> SceneAssetClient.setPreviewSpeed(
                                SceneAssetClient.getPreviewSpeed() + 0.25F))
                .bounds(rightX, row4, layout.columnWidth(2, gap), bh).accentBar(AccentSide.RIGHT)
                .build(), row4));

        int row5 = row4 + bh + gap;
        placements.add(new WidgetPlacement(
                ModernButton.builder(Component.translatable("sre.map_helper.refresh_preview"),
                        b -> SceneAssetClient.refreshPreview())
                        .bounds(leftX, row5, layout.columnWidth(2, gap), bh)
                        .accentBar(AccentSide.LEFT).build(),
                row5));
        placements.add(new WidgetPlacement(ModernButton.builder(
                Component.translatable("sre.map_helper.toggle_client_scene",
                        SceneAssetClient.isMovingSceneEnabled()
                                ? Component.translatable("sre.map_helper.on")
                                : Component.translatable("sre.map_helper.off")),
                b -> {
                    SceneAssetClient.setMovingSceneEnabled(
                            !SceneAssetClient.isMovingSceneEnabled());
                    ctx.refreshScreen();
                })
                .bounds(rightX, row5, layout.columnWidth(2, gap), bh).accentBar(AccentSide.RIGHT)
                .build(), row5));
    }

    @Override
    public int getContentHeight() {
        return 6 * 32;
    }

    @Override
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // Scene summary rendering is now handled in MapBuildHelperScreen, but we can
        // also provide extra info here if needed.
    }
}