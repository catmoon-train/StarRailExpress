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

package io.wifi.starrailexpress.customitem;

import io.wifi.starrailexpress.client.gui.screen.CustomContentManageScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.function.Supplier;

/**
 * 自定义列车物品总列表（列表 / 搜索 / 新建 / 删除）。
 *
 * <p>
 * 布局、滚动、搜索等都在 {@link CustomContentManageScreen} 里，这里只提供列车物品的数据源与回调。
 */
@Environment(EnvType.CLIENT)
public class CustomItemManageScreen extends CustomContentManageScreen<CustomItemData> {

    /** 开界面时读一次配置（避免每次重建都重新读盘）。 */
    private CustomItemConfig config;
    private List<CustomItemData> cached;

    public CustomItemManageScreen(Supplier<Screen> backSupplier) {
        super(Component.translatable("sre.custom_item.manage.title"), backSupplier);
    }

    public CustomItemManageScreen(Screen backScreen) {
        this(() -> backScreen);
    }

    @Override
    protected List<CustomItemData> rows() {
        if (cached == null) {
            config = CustomItemConfig.loadPreferWorldPath(minecraft == null ? null : minecraft.getSingleplayerServer());
            cached = config.items;
        }
        return cached;
    }

    @Override
    protected String rowId(CustomItemData row) {
        return row.id;
    }

    @Override
    protected String rowName(CustomItemData row) {
        return row.displayName;
    }

    @Override
    protected Component rowSummary(CustomItemData row) {
        return Component.translatable("sre.custom_item.kind." + row.kind().name().toLowerCase())
                .append(Component.literal("  ").append(summary(row)));
    }

    @Override
    protected Screen editorFor(CustomItemData row) {
        return new CustomItemScreen(row);
    }

    @Override
    protected Screen newEditor() {
        return new CustomItemScreen();
    }

    @Override
    protected void removeRow(CustomItemData row) {
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
        CustomItemReloadCommand.reload(server);
    }

    @Override
    protected String emptyKey() {
        return "sre.custom_item.manage.empty";
    }

    @Override
    protected String backKey() {
        return "sre.custom_item.back";
    }

    @Override
    protected String newKey() {
        return "sre.custom_item.new";
    }

    /** 列表右侧摘要（全部走翻译键）。 */
    private static Component summary(CustomItemData item) {
        return switch (item.kind()) {
            case BASIC -> Component.translatable("sre.custom_item.summary.commands", item.commands.size());
            case CHARGE -> Component
                    .translatable("sre.custom_item.summary.charge", item.chargeTicks)
                    .append(item.affectOthers ? Component.translatable("sre.custom_item.summary.multi")
                            : Component.empty());
            case GUN -> Component
                    .translatable(item.autoFire ? "sre.custom_item.summary.auto" : "sre.custom_item.summary.manual")
                    .append(item.ammoSystem
                            ? Component.translatable("sre.custom_item.summary.ammo", item.maxAmmo)
                            : Component.empty());
            case VANILLA_WEAPON -> Component.translatable("sre.custom_item.summary.damage", item.virtualDamage);
            case FOOD -> Component
                    .translatable("sre.custom_item.summary.nutrition", item.nutrition)
                    .append(item.isDrink ? Component.translatable("sre.custom_item.summary.drink")
                            : Component.empty());
            case CUFF -> Component.translatable("sre.custom_item.summary.cuff",
                    Component.translatable("sre.custom_item.cuff_pose." + item.cuffPose().name().toLowerCase()));
            case THROWABLE -> Component.translatable("sre.custom_item.summary.throwable",
                    (int) item.throwRadius, item.throwExplode
                            ? Component.translatable("sre.custom_item.value.yes")
                            : Component.translatable("sre.custom_item.value.no"));
        };
    }
}
