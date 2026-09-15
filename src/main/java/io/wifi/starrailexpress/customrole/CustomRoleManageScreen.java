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

package io.wifi.starrailexpress.customrole;

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
 * 自定义职业总列表（列表 / 搜索 / 新建 / 删除）。
 *
 * <p>
 * 布局、滚动、搜索等都在 {@link CustomContentManageScreen} 里，这里只提供职业的数据源与回调。
 */
@Environment(EnvType.CLIENT)
public class CustomRoleManageScreen extends CustomContentManageScreen<CustomRoleData> {

    /** 开界面时读一次配置（避免每次重建都重新读盘）。 */
    private CustomRoleConfig config;
    private List<CustomRoleData> cached;

    public CustomRoleManageScreen(Supplier<Screen> backSupplier) {
        super(Component.translatable("sre.custom_role.manage.title"), backSupplier);
    }

    public CustomRoleManageScreen(Screen backScreen) {
        this(() -> backScreen);
    }

    @Override
    protected List<CustomRoleData> rows() {
        if (cached == null) {
            config = CustomRoleConfig.loadPreferWorldPath(minecraft == null ? null : minecraft.getSingleplayerServer());
            cached = config.roles;
        }
        return cached;
    }

    @Override
    protected String rowId(CustomRoleData row) {
        return row.englishId;
    }

    @Override
    protected String rowName(CustomRoleData row) {
        return row.displayName;
    }

    @Override
    protected Component rowSummary(CustomRoleData row) {
        Component teamTag = Component.translatable(row.isInnocent ? "sre.custom_role.manage.team.innocent"
                : (row.canUseKiller ? "sre.custom_role.manage.team.killer"
                        : "sre.custom_role.manage.team.neutral"));
        MutableComponent info = teamTag.copy()
                .append(Component.translatable("sre.custom_role.manage.summary", row.moodType, row.maxCount));
        return info;
    }

    @Override
    protected int rowColor(CustomRoleData row) {
        return 0xFF000000 | (row.colorR << 16) | (row.colorG << 8) | row.colorB;
    }

    @Override
    protected Screen editorFor(CustomRoleData row) {
        return new CustomRoleScreen(row);
    }

    @Override
    protected Screen newEditor() {
        return new CustomRoleScreen();
    }

    @Override
    protected void removeRow(CustomRoleData row) {
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
        CustomRoleReloadCommand.reload(server);
    }

    @Override
    protected String emptyKey() {
        return "sre.custom_role.manage.empty";
    }

    @Override
    protected String backKey() {
        return "sre.custom_role.back";
    }

    @Override
    protected String newKey() {
        return "sre.custom_role.new";
    }
}
