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

package io.wifi.starrailexpress.client.gui.screen.map_dev.modules;

import io.wifi.starrailexpress.scenery.client.SceneAssetClient;
import io.wifi.starrailexpress.client.gui.widget.SreButton;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.screen.map_dev.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import java.util.List;

/** 场景页：绑定/解绑场景 id、场景库、以及客户端场景预览的各种开关。 */
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
        int half = layout.columnWidth(2, gap);
        AreasWorldComponent areas = SREClient.areaComponent;

        // 第一行：场景 id 输入框 + 两个按钮。按钮宽度按文字实测，剩下的给输入框，
        // 不再像以前那样写死 190/64/64（窄面板时右端会钻到滚动条底下）
        int assignW = Mth.clamp(layout.font.width(Component.translatable("sre.map_helper.assign_scene")) + 14, 56, 96);
        int editorW = Mth.clamp(layout.font.width(Component.translatable("sre.map_helper.scene_editor")) + 14, 56, 96);
        int sceneBoxWidth = Math.max(60, layout.contentWidth() - assignW - editorW - 2 * gap);
        sceneIdBox = new EditBox(layout.font, leftX, y, sceneBoxWidth, bh,
                Component.translatable("sre.map_helper.scene_id"));
        sceneIdBox.setMaxLength(128);
        if (areas != null)
            sceneIdBox.setValue(areas.getSceneId());
        placements.add(new WidgetPlacement(sceneIdBox, y));

        placements.add(new WidgetPlacement(
                SreButton.create(Component.translatable("sre.map_helper.assign_scene"), b -> {
                    String id = sceneIdBox.getValue().trim();
                    if (!id.isEmpty())
                        ctx.sendOnly("sre:scene library assign " + ctx.quoteCommandArgument(id));
                }).bounds(leftX + sceneBoxWidth + gap, y, assignW, bh).build(),
                y));
        placements.add(new WidgetPlacement(
                SreButton.create(Component.translatable("sre.map_helper.scene_editor"),
                        b -> ctx.sendOnly("sre:scene manager"))
                        .bounds(leftX + sceneBoxWidth + gap + assignW + gap, y, editorW, bh)
.build(),
                y));

        int row1 = y + bh + gap;
        placements.add(new WidgetPlacement(
                SreButton.create(Component.translatable("sre.map_helper.detach_scene"),
                        b -> ctx.sendOnly("sre:scene library detach"))
                        .bounds(leftX, row1, half, bh)
.build(),
                row1));
        placements.add(new WidgetPlacement(SreButton
                .builder(Component.translatable("sre.map_helper.list_scene_library"),
                        b -> ctx.sendOnly("sre:scene library list"))
                .bounds(rightX, row1, half, bh)
                .build(), row1));

        int row2 = row1 + bh + gap;
        placements.add(new WidgetPlacement(
                SreButton.create(togglePreviewLabel(),
                        b -> {
                            SceneAssetClient.setPreviewEnabled(!SceneAssetClient.isPreviewEnabled());
                            b.setMessage(togglePreviewLabel());
                        })
                        .bounds(leftX, row2, half, bh)
.build(),
                row2));
        placements.add(new WidgetPlacement(SreButton
                .builder(toggleScrollLabel(),
                        b -> {
                            SceneAssetClient.setPreviewPaused(!SceneAssetClient.isPreviewPaused());
                            b.setMessage(toggleScrollLabel());
                        })
                .bounds(rightX, row2, half, bh)
                .build(), row2));

        // 透明度：数值直接写在「＋」按钮上，省得点了不知道现在是几
        int row3 = row2 + bh + gap;
        SreButton alphaUp = SreButton.create(alphaLabel(),
                b -> {
                    SceneAssetClient.setPreviewAlpha(SceneAssetClient.getPreviewAlpha() + 0.05F);
                    b.setMessage(alphaLabel());
                }).bounds(rightX, row3, half, bh).build();
        placements.add(new WidgetPlacement(
                SreButton.create(Component.translatable("sre.map_helper.preview_alpha_down"),
                        b -> {
                            SceneAssetClient.setPreviewAlpha(SceneAssetClient.getPreviewAlpha() - 0.05F);
                            alphaUp.setMessage(alphaLabel());
                        })
                        .bounds(leftX, row3, half, bh)
.build(),
                row3));
        placements.add(new WidgetPlacement(alphaUp, row3));

        // 播放速度：同样是「＋」按钮带上当前值
        int row4 = row3 + bh + gap;
        SreButton speedUp = SreButton.create(speedLabel(),
                b -> {
                    SceneAssetClient.setPreviewSpeed(SceneAssetClient.getPreviewSpeed() + 0.25F);
                    b.setMessage(speedLabel());
                }).bounds(rightX, row4, half, bh).build();
        placements.add(new WidgetPlacement(
                SreButton.create(Component.translatable("sre.map_helper.preview_speed_down"),
                        b -> {
                            SceneAssetClient.setPreviewSpeed(SceneAssetClient.getPreviewSpeed() - 0.25F);
                            speedUp.setMessage(speedLabel());
                        })
                        .bounds(leftX, row4, half, bh)
.build(),
                row4));
        placements.add(new WidgetPlacement(speedUp, row4));

        int row5 = row4 + bh + gap;
        placements.add(new WidgetPlacement(
                SreButton.create(Component.translatable("sre.map_helper.refresh_preview"),
                        b -> SceneAssetClient.refreshPreview())
                        .bounds(leftX, row5, half, bh)
.build(),
                row5));
        // 「客户端场景」开关：点了只刷新自己的文案，不再整屏重建（以前会把整页输入都清掉）
        placements.add(new WidgetPlacement(SreButton.create(clientSceneLabel(),
                b -> {
                    SceneAssetClient.setMovingSceneEnabled(!SceneAssetClient.isMovingSceneEnabled());
                    b.setMessage(clientSceneLabel());
                })
                .bounds(rightX, row5, half, bh)
                .build(), row5));
    }

    private static Component togglePreviewLabel() {
        return Component.translatable("sre.map_helper.toggle_preview").copy().append(": ")
                .append(onOff(SceneAssetClient.isPreviewEnabled()));
    }

    private static Component toggleScrollLabel() {
        return Component.translatable("sre.map_helper.toggle_scroll").copy().append(": ")
                .append(onOff(SceneAssetClient.isPreviewPaused()));
    }

    private static Component clientSceneLabel() {
        return Component.translatable("sre.map_helper.toggle_client_scene").copy().append(": ")
                .append(onOff(SceneAssetClient.isMovingSceneEnabled()));
    }

    private static Component alphaLabel() {
        return Component.translatable("sre.map_helper.preview_alpha_up").copy().append(" ")
                .append(Component.literal(Math.round(SceneAssetClient.getPreviewAlpha() * 100F) + "%"));
    }

    private static Component speedLabel() {
        return Component.translatable("sre.map_helper.preview_speed_up").copy().append(" ")
                .append(Component.literal(String.format("%.2fx", SceneAssetClient.getPreviewSpeed())));
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "sre.map_helper.on" : "sre.map_helper.off");
    }

    @Override
    public int getContentHeight() {
        return 6 * 32;
    }
}
