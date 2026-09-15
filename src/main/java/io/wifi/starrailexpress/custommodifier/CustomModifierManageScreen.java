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

package io.wifi.starrailexpress.custommodifier;

import io.wifi.starrailexpress.client.gui.screen.CustomContentManageScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.function.Supplier;

/**
 * 自定义修饰符总列表（列表 / 搜索 / 新建 / 删除）。
 *
 * <p>
 * 布局、滚动、搜索等都在 {@link CustomContentManageScreen} 里，这里只提供修饰符的数据源与回调。
 */
@Environment(EnvType.CLIENT)
public class CustomModifierManageScreen extends CustomContentManageScreen<CustomModifierData> {

    /** 开界面时读一次配置（避免每次重建都重新读盘）。 */
    private CustomModifierConfig config;
    private List<CustomModifierData> cached;

    public CustomModifierManageScreen(Supplier<Screen> backSupplier) {
        super(Component.translatable("sre.custom_modifier.manage.title"), backSupplier);
    }

    public CustomModifierManageScreen(Screen backScreen) {
        this(() -> backScreen);
    }

    @Override
    protected List<CustomModifierData> rows() {
        if (cached == null) {
            config = CustomModifierConfig
                    .loadPreferWorldPath(minecraft == null ? null : minecraft.getSingleplayerServer());
            cached = config.modifiers;
        }
        return cached;
    }

    @Override
    protected String rowId(CustomModifierData row) {
        return row.englishId;
    }

    @Override
    protected String rowName(CustomModifierData row) {
        return row.displayName;
    }

    @Override
    protected Component rowSummary(CustomModifierData row) {
        int conditionCount = row.conditions == null ? 0 : row.conditions.size();
        MutableComponent info = Component.empty()
                .append(row.hidden ? Component.translatable("sre.custom_modifier.manage.hidden") : Component.empty())
                .append(row.markerOnly ? Component.translatable("sre.custom_modifier.manage.marker")
                        : Component.empty())
                .append(Component.translatable("sre.custom_modifier.manage.summary", row.defaultMax,
                        row.defaultEnableChance))
                .append(row.markerOnly ? Component.empty()
                        : row.isGlobalTrigger()
                                ? Component.translatable("sre.custom_modifier.manage.global")
                                : Component.translatable("sre.custom_modifier.manage.conditions", conditionCount));
        return info;
    }

    @Override
    protected int rowColor(CustomModifierData row) {
        return row.getColor();
    }

    @Override
    protected Screen editorFor(CustomModifierData row) {
        return new CustomModifierScreen(row);
    }

    @Override
    protected Screen newEditor() {
        return new CustomModifierScreen();
    }

    @Override
    protected void removeRow(CustomModifierData row) {
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
        CustomModifierReloadCommand.reload(server);
    }

    @Override
    protected String emptyKey() {
        return "sre.custom_modifier.manage.empty";
    }

    @Override
    protected String backKey() {
        return "sre.custom_modifier.back";
    }

    @Override
    protected String newKey() {
        return "sre.custom_modifier.new";
    }
}
