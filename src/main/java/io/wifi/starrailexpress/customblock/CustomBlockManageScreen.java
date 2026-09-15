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

package io.wifi.starrailexpress.customblock;

import io.wifi.starrailexpress.client.gui.screen.CustomContentManageScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.function.Supplier;

/**
 * 自定义方块总列表（列表 / 搜索 / 新建 / 删除）。
 *
 * <p>
 * 布局、滚动、搜索等都在 {@link CustomContentManageScreen} 里，这里只提供方块的数据源与回调。
 */
@Environment(EnvType.CLIENT)
public class CustomBlockManageScreen extends CustomContentManageScreen<CustomBlockData> {

    /** 开界面时读一次配置（避免每次重建都重新读盘）。 */
    private CustomBlockConfig config;
    private List<CustomBlockData> cached;

    public CustomBlockManageScreen(Supplier<Screen> backSupplier) {
        super(Component.translatable("sre.custom_block.manage.title"), backSupplier);
    }

    public CustomBlockManageScreen(Screen backScreen) {
        this(() -> backScreen);
    }

    @Override
    protected List<CustomBlockData> rows() {
        if (cached == null) {
            config = CustomBlockConfig
                    .loadPreferWorldPath(minecraft == null ? null : minecraft.getSingleplayerServer());
            cached = config.blocks;
        }
        return cached;
    }

    @Override
    protected String rowId(CustomBlockData row) {
        return row.id;
    }

    @Override
    protected String rowName(CustomBlockData row) {
        return row.displayName;
    }

    @Override
    protected Component rowSummary(CustomBlockData row) {
        return summary(row);
    }

    @Override
    protected Screen editorFor(CustomBlockData row) {
        return new CustomBlockScreen(row);
    }

    @Override
    protected Screen newEditor() {
        return new CustomBlockScreen();
    }

    @Override
    protected void removeRow(CustomBlockData row) {
        rows().remove(row);
    }

    @Override
    protected void saveConfig(MinecraftServer server) {
        if (config != null) {
            config.savePreferWorldPath(server);
        }
    }

    @Override
    protected void reloadServer(MinecraftServer server) {
        CustomBlockReloadCommand.reload(server);
    }

    @Override
    protected String emptyKey() {
        return "sre.custom_block.manage.empty";
    }

    @Override
    protected String backKey() {
        return "sre.custom_block.back";
    }

    @Override
    protected String newKey() {
        return "sre.custom_block.new";
    }

    /** 列表右侧摘要（全部走翻译键）。 */
    private static Component summary(CustomBlockData block) {
        Component appearance;
        if (block.packTexturePath != null && !block.packTexturePath.isBlank()) {
            appearance = Component.translatable("sre.custom_block.summary.texture");
        } else if (block.inheritBlock != null && !block.inheritBlock.isBlank()) {
            appearance = Component.translatable("sre.custom_block.summary.inherit", block.inheritBlock);
        } else {
            appearance = Component.translatable("sre.custom_block.summary.none");
        }
        Component eventCount = Component.translatable("sre.custom_block.summary.events",
                block.events == null ? 0 : block.events.size());
        return appearance.copy().append(Component.literal("  ")).append(eventCount);
    }
}
