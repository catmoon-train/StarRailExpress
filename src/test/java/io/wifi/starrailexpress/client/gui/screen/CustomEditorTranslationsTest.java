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

package io.wifi.starrailexpress.client.gui.screen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.wifi.starrailexpress.api.RoleTeam;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.customblock.CustomBlockData;
import io.wifi.starrailexpress.customitem.CustomItemData;
import io.wifi.starrailexpress.custommodifier.CustomModifierData;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * 四个自定义内容编辑器用到的翻译键覆盖率。
 *
 * <p>
 * 这些键大多是<b>运行时拼出来</b>的（{@code translationPrefix() + ".save"}、{@code ".condition." + 枚举名}、
 * {@code ".kind." + 小写枚举名}），静态扫描代码根本看不出缺哪条 —— 曾经就漏过
 * {@code sre.custom_modifier.save/.cancel}：界面上的「保存 / 取消」直接显示原始键名。
 * 这里把「代码能拼出来的键」逐条对着三个语言文件核一遍。
 */
class CustomEditorTranslationsTest {

    private static final String LANG_DIR = "src/main/resources/assets/starrailexpress/lang/";
    private static final String[] LANGS = { "zh_cn", "zh_tw", "en_us" };

    /** 四个编辑器的翻译键前缀（= 各界面 CustomEditorScreen#translationPrefix）。 */
    private static final String[] PREFIXES = {
            "sre.custom_role", "sre.custom_modifier", "sre.custom_item", "sre.custom_block"
    };

    /** 基类 {@code buildFooter} / 标题 / 列表删除按钮会生成的固定后缀。 */
    private static final String[] CHROME_SUFFIXES = { "title", "save", "manage", "cancel", "remove" };

    /** 各界面自己的页签名（= CustomEditorScreen#tabKeys）。 */
    private static final String[][] TABS = {
            { "basic", "advanced", "ability", "generation", "shop" },
            { "basic", "relations", "generation", "restriction", "trigger_content" },
            { "basic", "kind" },
            { "basic", "appearance", "properties", "events" },
    };

    private static JsonObject load(String lang) throws Exception {
        Path path = Path.of(LANG_DIR + lang + ".json");
        String raw = Files.readString(path, StandardCharsets.UTF_8);
        if (raw.startsWith("\uFEFF")) {
            raw = raw.substring(1);
        }
        return JsonParser.parseString(raw).getAsJsonObject();
    }

    private static List<JsonObject> allLanguages() throws Exception {
        List<JsonObject> result = new ArrayList<>();
        for (String lang : LANGS) {
            result.add(load(lang));
        }
        return result;
    }

    private static void assertHas(List<JsonObject> langs, String key) {
        for (int i = 0; i < langs.size(); i++) {
            assertTrue(langs.get(i).has(key), LANGS[i] + " 缺少翻译键 " + key);
            assertTrue(!langs.get(i).get(key).getAsString().isBlank(), LANGS[i] + " 的 " + key + " 是空值");
        }
    }

    private static void assertFamily(List<JsonObject> langs, String prefix, String family, String value) {
        assertHas(langs, prefix + "." + family + "." + value);
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    @Test
    void panelChromeKeysExistForEveryEditor() throws Exception {
        List<JsonObject> langs = allLanguages();
        for (String prefix : PREFIXES) {
            for (String suffix : CHROME_SUFFIXES) {
                // 保存 / 取消这两个键最容易漏：老实现里四个界面共用职业的键，
                // 换成 <前缀>.save / <前缀>.cancel 之后必须每个前缀都有自己的条目
                assertHas(langs, prefix + "." + suffix);
            }
        }
    }

    @Test
    void customTagKeysExistForRoleAndModifier() throws Exception {
        List<JsonObject> langs = allLanguages();
        for (String prefix : new String[] { "sre.custom_role", "sre.custom_modifier" }) {
            assertHas(langs, prefix + ".label.tags");
            assertHas(langs, prefix + ".hint.tags");
            assertHas(langs, prefix + ".add_tag");
        }
    }

    @Test
    void modifierTriggerGroupKeysExist() throws Exception {
        List<JsonObject> langs = allLanguages();
        String prefix = "sre.custom_modifier";
        for (String key : new String[] { "hint.groups_intro", "hint.groups_empty", "group.title",
                "group.global", "group.conditional", "group.add", "manage.groups",
                // 组类型开关（全局常驻 / 有条件触发）与「条件组还没条件」的提示
                "group.mode", "group.mode.global", "group.mode.conditional", "hint.condition_empty",
                // 阵营限制：每个阵营一个三态按钮（不限 / 仅给 / 不给）
                "hint.team_restriction", "team_mode.unset", "team_mode.only", "team_mode.deny" }) {
            assertHas(langs, prefix + "." + key);
        }
    }

    @Test
    void mapHelperKeysUsedByTheScreenExist() throws Exception {
        List<JsonObject> langs = allLanguages();
        // 地图工具新增/复用的固定文案：搜索框、无匹配提示、房间序号提示
        for (String key : new String[] { "sre.map_helper.title", "sre.map_helper.settings.search_hint",
                "sre.map_helper.settings.no_match", "sre.map_helper.room_id_hint",
                "sre.map_helper.expandable.expand", "sre.map_helper.expandable.unexpand",
                "sre.map_helper.area.set_min", "sre.map_helper.area.set_max",
                "sre.map_helper.set_room_count", "sre.map_helper.meeting.enable",
                "sre.map_helper.meeting.disable", "sre.map_helper.toggle_client_scene",
                // 开关统一成「✓ 开 / ✗ 关」之后新增的文案（地图工具与编辑器同一套观感）
                "sre.map_helper.value.on", "sre.map_helper.value.off",
                "sre.map_helper.meeting.toggle", "sre.map_helper.settings.category_count" }) {
            assertHas(langs, key);
        }
    }

    @Test
    void tabKeysExistForEveryEditor() throws Exception {
        List<JsonObject> langs = allLanguages();
        for (int i = 0; i < PREFIXES.length; i++) {
            for (String tab : TABS[i]) {
                assertHas(langs, PREFIXES[i] + ".tab." + tab);
            }
        }
    }

    @Test
    void modifierValueKeysExist() throws Exception {
        List<JsonObject> langs = allLanguages();
        String prefix = "sre.custom_modifier";
        for (CustomModifierData.ConditionType type : CustomModifierData.ConditionType.values()) {
            assertFamily(langs, prefix, "condition", type.name());
        }
        for (String comparison : new String[] { "EQUALS", "GREATER", "LESS", "GREATER_EQUAL", "LESS_EQUAL" }) {
            assertFamily(langs, prefix, "comparison", comparison);
        }
        for (String time : new String[] { "DAY", "NOON", "SUNSET", "NIGHT", "MIDNIGHT" }) {
            assertFamily(langs, prefix, "time", time);
        }
        for (RoleTeam team : RoleTeam.values()) {
            // 修饰符界面的阵营按钮用的是枚举原名（大写）
            assertFamily(langs, prefix, "team", team.name());
        }
        for (String logic : new String[] { "and", "or" }) {
            assertFamily(langs, prefix, "logic", logic);
        }
    }

    @Test
    void itemValueKeysExist() throws Exception {
        List<JsonObject> langs = allLanguages();
        String prefix = "sre.custom_item";
        // 轮回按钮显示的是小写枚举名
        for (CustomItemData.Kind value : CustomItemData.Kind.values()) {
            assertFamily(langs, prefix, "kind", lower(value));
        }
        for (CustomItemData.TextureMode value : CustomItemData.TextureMode.values()) {
            assertFamily(langs, prefix, "texture_mode", lower(value));
        }
        for (CustomItemData.ChargeAnim value : CustomItemData.ChargeAnim.values()) {
            assertFamily(langs, prefix, "charge_anim", lower(value));
        }
        for (CustomItemData.ThirdPose value : CustomItemData.ThirdPose.values()) {
            assertFamily(langs, prefix, "third_pose", lower(value));
        }
        for (CustomItemData.TargetMode value : CustomItemData.TargetMode.values()) {
            assertFamily(langs, prefix, "target_mode", lower(value));
        }
        for (CustomItemData.HoldPose value : CustomItemData.HoldPose.values()) {
            assertFamily(langs, prefix, "hold_pose", lower(value));
        }
        for (CustomItemData.HoldOrientation value : CustomItemData.HoldOrientation.values()) {
            assertFamily(langs, prefix, "hold_orientation", lower(value));
        }
        for (CustomItemData.FireButton value : CustomItemData.FireButton.values()) {
            assertFamily(langs, prefix, "fire_button", lower(value));
        }
        for (CustomItemData.TracerStyle value : CustomItemData.TracerStyle.values()) {
            assertFamily(langs, prefix, "tracer_style", lower(value));
        }
        for (CustomItemData.CuffWearMode value : CustomItemData.CuffWearMode.values()) {
            assertFamily(langs, prefix, "cuff_wear_mode", lower(value));
        }
        for (CustomItemData.CuffPose value : CustomItemData.CuffPose.values()) {
            assertFamily(langs, prefix, "cuff_pose", lower(value));
        }
        for (RoleTeam team : RoleTeam.values()) {
            assertFamily(langs, prefix, "team", team.name().toLowerCase(Locale.ROOT));
        }
        // 是/否开关（CustomEditorScreen#yesNoSwitchCell 要求子类给这两个键）
        for (String value : new String[] { "yes", "no", "none" }) {
            assertFamily(langs, prefix, "value", value);
        }
    }

    @Test
    void blockValueKeysExist() throws Exception {
        List<JsonObject> langs = allLanguages();
        String prefix = "sre.custom_block";
        for (CustomBlockData.BlockEventType value : CustomBlockData.BlockEventType.values()) {
            assertFamily(langs, prefix, "event_type", lower(value));
        }
        for (RoleTeam team : RoleTeam.values()) {
            assertFamily(langs, prefix, "team", team.name().toLowerCase(Locale.ROOT));
        }
        for (String value : new String[] { "yes", "no", "any" }) {
            assertFamily(langs, prefix, "value", value);
        }
    }

    @Test
    void roleMoodAndTaskKeysExist() throws Exception {
        List<JsonObject> langs = allLanguages();
        String prefix = "sre.custom_role";
        assertFamily(langs, prefix, "mood", "real");
        assertFamily(langs, prefix, "mood", "fake");
        // 任务类型按钮显示 task.<小写枚举名>（历史拼写 RAED_BOOK 对应语言文件里的 read_book）
        for (SREPlayerTaskComponent.Task task : SREPlayerTaskComponent.Task.values()) {
            String name = task.name().toLowerCase(Locale.ROOT);
            if ("raed_book".equals(name)) {
                name = "read_book";
            }
            assertHas(langs, "task." + name);
        }
    }
}
