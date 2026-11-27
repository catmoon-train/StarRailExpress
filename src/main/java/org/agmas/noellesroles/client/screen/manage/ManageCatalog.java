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

package org.agmas.noellesroles.client.screen.manage;

import io.wifi.ConfigCompact.ui.RoleManageConfigUI;
import io.wifi.ConfigCompact.ui.SettingMenuScreen;
import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.api.RoleTeam;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.client.gui.screen.MapIntroduceScreen;
import io.wifi.starrailexpress.client.gui.screen.map_dev.MapBuildHelperScreen;
import io.wifi.starrailexpress.client.gui.screen.maprotation.MapRotationScreen;
import io.wifi.starrailexpress.client.gui.screen.roster.RoleRosterEditScreen;
import io.wifi.starrailexpress.client.gui.screen.roster.RoleRosterViewScreen;
import io.wifi.starrailexpress.client.gui.widget.SwitchState;
import io.wifi.starrailexpress.client.ui.SRETheme;
import io.wifi.starrailexpress.content.command.MapVoteCommand;
import io.wifi.starrailexpress.customblock.CustomBlockManageScreen;
import io.wifi.starrailexpress.customblock.CustomBlockScreen;
import io.wifi.starrailexpress.customitem.CustomItemManageScreen;
import io.wifi.starrailexpress.customitem.CustomItemScreen;
import io.wifi.starrailexpress.custommodifier.CustomModifierManageScreen;
import io.wifi.starrailexpress.custommodifier.CustomModifierScreen;
import io.wifi.starrailexpress.customrole.CustomRoleManageScreen;
import io.wifi.starrailexpress.customrole.CustomRoleScreen;
import io.wifi.starrailexpress.training.TrainingGameMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.commands.GameUtilsCommand;
import org.agmas.noellesroles.utils.RoleUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 游戏设置中心的<b>数据表</b>（{@code docs/ui_style.md} §8.7）。
 *
 * <p>
 * 这里只有「有哪些分类、每个分类里有哪些行」，没有一行绘制代码：布局、滚动、命中测试
 * 全在 {@code GameManagementScreen} 里。要加一项设置，往对应分类里 {@code add} 一行即可。
 *
 * <h2>整合原则（重要）</h2>
 * <ol>
 * <li><b>能复用就复用，不重写</b>：职业轮选名单、地图轮换、自定义内容编辑器都是现成的界面，
 * 用 {@link ManageEntry.Nav} 打开它们（传 supplier），不要在本页复制一遍编辑逻辑。</li>
 * <li><b>只有「服务端有对应指令」的东西才做成开关</b>：职业 / 修饰符开关下发
 * {@code setEnabledRole} / {@code setEnabledModifier}，人数下发 {@code setRoleCount}，
 * 地图开关交给现成的 {@code MapRotationScreen}（它有自己的 S2C/C2S 链路）。</li>
 * <li><b>客户端镜像只用于显示</b>：职业 / 修饰符的开关状态来自
 * {@link RoleManageConfigUI} 里由服务端同步下来的镜像（{@code /manageRolesUI silent} 触发），
 * 没同步到就显示「未设置」三态中的中间态，而不是猜一个值。</li>
 * </ol>
 */
@Environment(EnvType.CLIENT)
public final class ManageCatalog {

    /** 一个分类（页签）。 */
    public record Category(String labelKey, int color) {
    }

    /** 分类顺序 = 页签顺序。 */
    public static final List<Category> CATEGORIES = List.of(
            new Category("screen.game_manage.category.game", SRETheme.BLUE),
            new Category("screen.game_manage.category.roles", SRETheme.GOLD),
            new Category("screen.game_manage.category.modifiers", SRETheme.GOLD_DIM),
            new Category("screen.game_manage.category.maps", SRETheme.AMBER),
            new Category("screen.game_manage.category.world", SRETheme.GREEN),
            new Category("screen.game_manage.category.misc", SRETheme.MUTED));

    /**
     * 设置页需要外界提供的能力（由 {@code GameManagementScreen} 实现）。
     *
     * <p>
     * 行本身只声明「要做的事」，不直接碰网络与界面栈，这样这张数据表才能被单元测试式地读一遍。
     */
    public interface Context {
        /** 把一条指令（不带斜杠）发给服务端。 */
        void command(String command);

        /** 打开另一个界面。 */
        void open(Supplier<Screen> screen);

        /** 向服务端请求一次状态同步并重建列表。 */
        void refresh();

        /** 本页自己（作为子界面的 parent）。 */
        Screen self();

        /** 「开始游戏」用的补充时长（分钟，0 = 用服务端默认）。 */
        int startMinutes();

        void setStartMinutes(int minutes);
    }

    /** 职业分组顺序：与名单界面（{@code RoleRosterEditScreen}）保持一致。 */
    private static final List<RoleTeam> ROLE_TEAM_ORDER = List.of(
            RoleTeam.CIVILIAN,
            RoleTeam.SHERIFF,
            RoleTeam.NEUTRAL_INNOCENT,
            RoleTeam.NEUTRAL_KILLER,
            RoleTeam.NEUTRAL_EVENT,
            RoleTeam.NEUTRAL_SPECIAL,
            RoleTeam.NEUTRAL_INDEPENDENT_WIN,
            RoleTeam.KILLER);

    private ManageCatalog() {
    }

    // ══════════════════════════════════════════════════════════════════
    // 入口
    // ══════════════════════════════════════════════════════════════════

    /**
     * 构建某个分类的行列表。
     *
     * @param category 分类下标（见 {@link #CATEGORIES}）
     * @param filter   搜索词（空串表示不过滤）；过滤时只保留命中的行与它们的分组标题
     */
    public static List<ManageEntry> build(int category, String filter, Context ctx) {
        List<ManageEntry> out = new ArrayList<>();
        switch (category) {
            case 0 -> buildGame(out, ctx);
            case 1 -> buildRoles(out, ctx);
            case 2 -> buildModifiers(out, ctx);
            case 3 -> buildMaps(out, ctx);
            case 4 -> buildWorld(out, ctx);
            default -> buildTools(out, ctx);
        }
        return filter == null || filter.isBlank() ? out : applyFilter(out, filter.strip().toLowerCase(Locale.ROOT));
    }

    /** 过滤：命中的行留下；一整组都没命中就连分组标题一起丢掉（分隔线在过滤时不再保留）。 */
    private static List<ManageEntry> applyFilter(List<ManageEntry> in, String needle) {
        List<ManageEntry> out = new ArrayList<>();
        ManageEntry.Label pendingLabel = null;
        List<ManageEntry> pendingRows = new ArrayList<>();
        for (ManageEntry entry : in) {
            if (entry instanceof ManageEntry.Label label) {
                flush(out, pendingLabel, pendingRows);
                pendingLabel = label;
                continue;
            }
            if (entry instanceof ManageEntry.Divider) {
                continue;
            }
            String text = entry.searchText();
            if (text != null && text.toLowerCase(Locale.ROOT).contains(needle)) {
                pendingRows.add(entry);
            }
        }
        flush(out, pendingLabel, pendingRows);
        return out;
    }

    private static void flush(List<ManageEntry> out, ManageEntry.Label label, List<ManageEntry> rows) {
        if (rows.isEmpty()) {
            return;
        }
        if (label != null) {
            out.add(label);
        }
        out.addAll(rows);
        rows.clear();
    }

    // ══════════════════════════════════════════════════════════════════
    // 0 · 游戏流程（开局 / 结算 / 投票）
    // ══════════════════════════════════════════════════════════════════

    private static void buildGame(List<ManageEntry> out, Context ctx) {
        out.add(label("screen.game_manage.section.start", SRETheme.BLUE));
        out.add(new ManageEntry.Counter(tr("screen.game_manage.row.minutes"), SRETheme.TEXT,
                ctx::startMinutes, () -> 0, ctx::setStartMinutes, null, 0, 180,
                tr("screen.game_manage.unit.minute")));
        for (ResourceLocation mode : new ArrayList<>(SREGameModes.GAME_MODES.keySet())) {
            if (mode.equals(TrainingGameMode.ID)) {
                continue;
            }
            int minutes = ctx.startMinutes();
            // 时长 > 0 时补在指令后面（/tmm:start <mode> [minutes]）
            String command = "tmm:start " + mode + (minutes > 0 ? " " + minutes : "");
            out.add(new ManageEntry.Command(MapVoteCommand.getLocalizedGameModeName(mode.getPath()), command));
        }

        out.add(label("screen.game_manage.section.win", SRETheme.GOLD_DIM));
        for (var status : GameUtilsCommand.WinStatusSuggestions.allWinStatus) {
            String name = status.toString().toLowerCase(Locale.ROOT);
            out.add(new ManageEntry.Command(
                    Component.translatable("announcement.star.win." + name)
                            .append(Component.literal(" " + name).withStyle(net.minecraft.ChatFormatting.GRAY)),
                    "tmm:game win " + name));
        }

        out.add(label("screen.game_manage.section.stop", SRETheme.GOLD_DIM));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.stop"), "tmm:stop"));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.stop_force"), "tmm:stop force"));

        out.add(label("screen.game_manage.section.vote", SRETheme.GOLD_DIM));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.vote_10s"), "tmm:votemap 200"));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.vote_20s"), "tmm:votemap 400"));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.vote_30s"), "tmm:votemap 600"));
    }

    // ══════════════════════════════════════════════════════════════════
    // 1 · 职业（人数 / 开关 / 名单）
    // ══════════════════════════════════════════════════════════════════

    private static void buildRoles(List<ManageEntry> out, Context ctx) {
        out.add(label("screen.game_manage.section.role_counts", SRETheme.GOLD));
        out.add(counter("screen.game_manage.row.killer_count", RoleTeam.KILLER.color(), ctx,
                () -> RoleManageConfigUI.forcedKillerCount, () -> RoleManageConfigUI.autoKillerCount,
                n -> ctx.command("setRoleCount killer " + n)));
        out.add(counter("screen.game_manage.row.vigilante_count", RoleTeam.SHERIFF.color(), ctx,
                () -> RoleManageConfigUI.forcedVigilanteCount, () -> RoleManageConfigUI.autoVigilanteCount,
                n -> ctx.command("setRoleCount vigilante " + n)));
        out.add(counter("screen.game_manage.row.neutral_count", RoleTeam.NEUTRAL.color(), ctx,
                () -> RoleManageConfigUI.forcedNeutralCount, () -> RoleManageConfigUI.autoNeutralCount,
                n -> ctx.command("setRoleCount neutral " + n)));

        out.add(label("screen.game_manage.section.role_switch", SRETheme.GOLD));
        out.add(new ManageEntry.Command(tr("screen.game_manage.row.enable_all"), "setEnabledRole enableAll"));
        out.add(new ManageEntry.Command(tr("screen.game_manage.row.disable_all"), "setEnabledRole disableAll"));
        out.add(new ManageEntry.Command(tr("screen.game_manage.row.resync"), "manageRolesUI silent"));

        out.add(label("screen.game_manage.section.role_list", SRETheme.GOLD));
        List<SRERole> roles = sortedRoles();
        for (RoleTeam team : ROLE_TEAM_ORDER) {
            List<SRERole> group = roles.stream().filter(team::matches).toList();
            if (group.isEmpty()) {
                continue;
            }
            out.add(new ManageEntry.Label(Component.translatable(team.displayKey()), team.color()));
            for (SRERole role : group) {
                out.add(roleToggle(role, ctx));
            }
        }
        List<SRERole> rest = roles.stream().filter(role -> ROLE_TEAM_ORDER.stream().noneMatch(t -> t.matches(role)))
                .toList();
        if (!rest.isEmpty()) {
            out.add(new ManageEntry.Label(Component.translatable("display.type.role"), SRETheme.GOLD_DIM));
            for (SRERole role : rest) {
                out.add(roleToggle(role, ctx));
            }
        }

        out.add(label("screen.game_manage.section.role_entry", SRETheme.GOLD_DIM));
        out.add(new ManageEntry.Nav(tr("screen.game_manage.nav.roster_edit"), RoleRosterEditScreen::new));
        out.add(new ManageEntry.Nav(tr("screen.game_manage.nav.roster_view"), RoleRosterViewScreen::new));
        out.add(new ManageEntry.Command(tr("screen.game_manage.nav.role_advanced"), "manageRolesUI"));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.list_roles"), "listRoles"));
        out.add(new ManageEntry.Nav(tr("screen.game_manage.nav.custom_role"), ManageCatalog::customRoleManage));
    }

    // ══════════════════════════════════════════════════════════════════
    // 2 · 修饰符
    // ══════════════════════════════════════════════════════════════════

    private static void buildModifiers(List<ManageEntry> out, Context ctx) {
        out.add(label("screen.game_manage.section.modifier_switch", SRETheme.GOLD_DIM));
        out.add(new ManageEntry.Command(tr("screen.game_manage.row.enable_all"), "setEnabledModifier enableAll"));
        out.add(new ManageEntry.Command(tr("screen.game_manage.row.disable_all"), "setEnabledModifier disableAll"));
        out.add(new ManageEntry.Command(tr("screen.game_manage.row.resync"), "manageRolesUI silent"));

        out.add(label("screen.game_manage.section.modifier_list", SRETheme.GOLD_DIM));
        for (SREModifier modifier : sortedModifiers()) {
            out.add(modifierToggle(modifier, ctx));
        }

        out.add(label("screen.game_manage.section.modifier_entry", SRETheme.GOLD_DIM));
        out.add(new ManageEntry.Nav(tr("screen.game_manage.nav.custom_modifier"),
                () -> new CustomModifierManageScreen(() -> new CustomModifierScreen())));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.list_roles"), "listRoles"));
    }

    // ══════════════════════════════════════════════════════════════════
    // 3 · 地图
    // ══════════════════════════════════════════════════════════════════

    private static void buildMaps(List<ManageEntry> out, Context ctx) {
        out.add(label("screen.game_manage.section.map_rotation", SRETheme.AMBER));
        // 地图开关 / 轮换列表是现成的界面，这里只做入口（它自带 S2C/C2S 增量同步）
        out.add(new ManageEntry.Nav(tr("screen.game_manage.nav.map_rotation"), MapRotationScreen::new));
        out.add(new ManageEntry.Nav(tr("screen.game_manage.nav.map_intro"), () -> new MapIntroduceScreen(ctx.self())));
        addIfPresent(out, mapToolEntry());

        out.add(label("screen.game_manage.section.switchmap", SRETheme.AMBER));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.switchmap_info"), "tmm:switchmap"));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.switchmap_list"), "tmm:switchmap list"));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.switchmap_random"), "tmm:switchmap random"));

        out.add(label("screen.game_manage.section.reset_map", SRETheme.AMBER));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.simple_reset_map"),
                "tmm:game reset blocks simple"));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.full_reset_map"), "tmm:game reset blocks copy"));

        out.add(label("screen.game_manage.section.map_scanner", SRETheme.AMBER));
        out.add(new ManageEntry.Command(tr("screen.game_manage.section.scan_reset"),
                "tmm:game scan reset_points"));
        out.add(new ManageEntry.Command(tr("screen.game_manage.section.scan_task"), "tmm:game scan task_points"));
    }

    // ══════════════════════════════════════════════════════════════════
    // 4 · 世界与效果
    // ══════════════════════════════════════════════════════════════════

    private static void buildWorld(List<ManageEntry> out, Context ctx) {
        out.add(label("screen.game_manage.section.time", SRETheme.GREEN));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.time_day"), "time set day"));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.time_noon"), "time set noon"));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.time_night"), "time set night"));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.time_midnight"), "time set midnight"));

        out.add(label("screen.game_manage.section.weather", SRETheme.GREEN));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.weather_clear"), "weather clear"));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.weather_rain"), "weather rain"));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.weather_thunder"), "weather thunder"));

        out.add(label("screen.game_manage.section.blackout", SRETheme.GREEN));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.trigger_blackout"), "tmm:game blackout"));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.stop_blackout"), "tmm:game blackout stop"));

        out.add(label("screen.game_manage.section.psycho", SRETheme.RED));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.trigger_psycho"), "tmm:game psycho"));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.stop_psycho"), "tmm:game psycho stop"));

        out.add(label("screen.game_manage.section.timestop", SRETheme.RED));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.trigger_timestop_5s"),
                "tmm:game timestop 100 \"5s Time Stop\""));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.trigger_timestop_10s"),
                "tmm:game timestop 200 \"10s Time Stop\""));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.trigger_timestop_20s"),
                "tmm:game timestop 400 \"20s Time Stop\""));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.trigger_timestop_30s"),
                "tmm:game timestop 600 \"30s Time Stop\""));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.stop_timestop"), "tmm:game timestop stop"));
    }

    // ══════════════════════════════════════════════════════════════════
    // 5 · 工具与内容
    // ══════════════════════════════════════════════════════════════════

    private static void buildTools(List<ManageEntry> out, Context ctx) {
        out.add(label("screen.game_manage.section.content", SRETheme.MUTED));
        out.add(new ManageEntry.Nav(tr("screen.game_manage.nav.custom_role"), ManageCatalog::customRoleManage));
        out.add(new ManageEntry.Nav(tr("screen.game_manage.nav.custom_item"),
                () -> new CustomItemManageScreen(() -> new CustomItemScreen())));
        out.add(new ManageEntry.Nav(tr("screen.game_manage.nav.custom_block"),
                () -> new CustomBlockManageScreen(() -> new CustomBlockScreen())));
        out.add(new ManageEntry.Nav(tr("screen.game_manage.nav.custom_modifier"),
                () -> new CustomModifierManageScreen(() -> new CustomModifierScreen())));
        addIfPresent(out, mapToolEntry());

        out.add(label("screen.game_manage.section.settings", SRETheme.MUTED));
        out.add(new ManageEntry.Nav(tr("screen.game_manage.nav.client_options"),
                () -> SREClientConfig.HANDLER.generateGui().generateScreen(ctx.self())));
        out.add(new ManageEntry.Nav(tr("screen.game_manage.nav.mod_settings"),
                () -> new SettingMenuScreen(ctx.self())));
        out.add(new ManageEntry.Nav(tr("screen.game_manage.nav.role_advanced_ui"),
                () -> RoleManageConfigUI.getScreen(ctx.self())));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.reload_config"), "tmm:config reload"));

        out.add(label("screen.game_manage.section.infos", SRETheme.MUTED));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.list_roles"), "listRoles"));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.list_game_roles"), "listGameRoles"));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.list_rooms"), "room"));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.show_game_time"), "tmm:game time"));
        out.add(new ManageEntry.Command(tr("screen.game_manage.btn.list_tasks"), "tmm:game tasks list"));
    }

    // ══════════════════════════════════════════════════════════════════
    // 行的构造工具
    // ══════════════════════════════════════════════════════════════════

    /**
     * 把一个「可能没有」的行加进列表（例如地图工具需要玩家坐标，没进世界时就没有这一行）。
     *
     * <p>
     * 用它而不是在调用点写 {@code if (x != null)}：空行的成因五花八门，集中在这里更容易看出来。
     */
    private static void addIfPresent(List<ManageEntry> out, ManageEntry entry) {
        if (entry != null) {
            out.add(entry);
        }
    }

    private static ManageEntry.Label label(String key, int color) {
        return new ManageEntry.Label(Component.translatable(key), color);
    }

    private static Component tr(String key) {
        return Component.translatable(key);
    }

    /** 人数行：值取服务端同步下来的数字，{@code -1} = 自动。 */
    private static ManageEntry.Counter counter(String labelKey, int color, Context ctx,
            java.util.function.IntSupplier forced, java.util.function.IntSupplier auto, java.util.function.IntConsumer apply) {
        return new ManageEntry.Counter(tr(labelKey), color, forced, auto, apply,
                () -> ctx.command("setRoleCount reset"), 0, 99, tr("screen.game_manage.unit.player"));
    }

    /** 职业开关：状态来自客户端镜像（未同步 = 三态中间态），点一下下发 {@code setEnabledRole}。 */
    private static ManageEntry.Toggle roleToggle(SRERole role, Context ctx) {
        String id = role.identifier().toString();
        return new ManageEntry.Toggle(RoleUtils.getRoleNameWithColor(role.identifier()), role.color(),
                () -> SwitchState.of(RoleManageConfigUI.RoleEnableStatus.get(id)),
                enabled -> {
                    // 乐观更新：本地镜像先变，服务端下一次同步（manageRolesUI silent）会覆盖成真值
                    RoleManageConfigUI.RoleEnableStatus.put(id, enabled);
                    ctx.command("setEnabledRole " + id + " " + enabled);
                });
    }

    /** 修饰符开关：同上，走 {@code setEnabledModifier}。 */
    private static ManageEntry.Toggle modifierToggle(SREModifier modifier, Context ctx) {
        String id = modifier.identifier().toString();
        return new ManageEntry.Toggle(RoleUtils.getModifierName(modifier), modifier.color(),
                () -> SwitchState.of(RoleManageConfigUI.ModifierEnableStatus.get(id)),
                enabled -> {
                    RoleManageConfigUI.ModifierEnableStatus.put(id, enabled);
                    ctx.command("setEnabledModifier " + id + " " + enabled);
                });
    }

    /**
     * 地图工具需要一个方块坐标当锚点：用玩家脚下。
     *
     * <p>
     * 没有玩家（还没进世界）时返回 {@code null}，调用方跳过这一行 —— 编造一个锚点坐标
     * 会让地图工具把偏移写到错误的方块上，比少一行危险得多。
     */
    private static ManageEntry mapToolEntry() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return null;
        }
        var anchor = client.player.blockPosition().below();
        return new ManageEntry.Nav(tr("screen.game_manage.nav.map_build"), () -> new MapBuildHelperScreen(anchor));
    }

    /** 自定义职业的入口（列表 → 编辑器）。 */
    private static Screen customRoleManage() {
        return new CustomRoleManageScreen(() -> new CustomRoleScreen());
    }

    private static List<SRERole> sortedRoles() {
        List<SRERole> roles = new ArrayList<>(TMMRoles.ROLES.values());
        Collator collator = Collator.getInstance();
        roles.sort(Comparator.comparing(role -> role.getName().getString(), collator));
        return roles;
    }

    private static List<SREModifier> sortedModifiers() {
        // 取快照再排序：MODIFIERS 在运行期可能被其它模组的注册流程改写
        List<SREModifier> modifiers = new ArrayList<>(HMLModifiers.MODIFIERS);
        Collator collator = Collator.getInstance();
        modifiers.sort(Comparator.comparing(modifier -> RoleUtils.getModifierName(modifier).getString(), collator));
        return modifiers;
    }
}
