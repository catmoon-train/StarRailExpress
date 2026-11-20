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

package io.wifi.starrailexpress.index;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 列车指令手册的目录数据。
 *
 * <p>
 * 本类只保存分类顺序、指令字面量与文案键，不写死任何显示文本；
 * 所有名称 / 用法 / 功能文案都放在独立命名空间
 * {@code assets/train_manual/lang/} 下：
 * <ul>
 *     <li>{@code train_manual.category.<分类id>} —— 分类标题</li>
 *     <li>{@code train_manual.entry.<键>.name} —— 指令名称</li>
 *     <li>{@code train_manual.entry.<键>.usage} —— 用法</li>
 *     <li>{@code train_manual.entry.<键>.desc} —— 功能说明</li>
 * </ul>
 * 新增指令只需在对应分类里加一行，并补上语言文件中的三个键。
 */
public final class TrainCommandManualEntries {

    public static final String NAMESPACE = "train_manual";

    /** 一条指令：文案键 + 点击时复制的字面量（含斜杠）。 */
    public record Entry(String key, String command) {
    }

    /** 分类：id + 主题色 + 指令列表。 */
    public record Category(String id, int color, List<Entry> entries) {
    }

    private static Entry e(String key, String command) {
        return new Entry(key, command);
    }

    /** 分类顺序即界面中的展示顺序，常用指令永远排在第一个。 */
    public static final List<Category> CATEGORIES = List.of(
            new Category("common", 0xFFE8B64C, List.of(
                    e("sre.help", "/sre:help"),
                    e("tmm.start", "/tmm:start"),
                    e("tmm.stop", "/tmm:stop"),
                    e("setrolecount", "/setRoleCount"),
                    e("tmm.switchmap", "/tmm:switchmap"),
                    e("tmm.votemap", "/tmm:votemap"),
                    e("forceteam", "/forceTeam"),
                    e("forcerole", "/forceRole"),
                    e("listroles", "/listRoles"),
                    e("tmm.game.time", "/tmm:game time"),
                    e("tmm.game.visual", "/tmm:game visual time"),
                    e("tmm.autostart", "/tmm:autoStart"))),

            new Category("core", 0xFF55BBCC, List.of(
                    e("sre.help", "/sre:help"),
                    e("tmm.start", "/tmm:start"),
                    e("tmm.stop", "/tmm:stop"),
                    e("tmm.config", "/tmm:config"),
                    e("tmm.afk", "/tmm:afk"),
                    e("tmm.money", "/tmm:money"),
                    e("tmm.mood", "/tmm:mood"),
                    e("tmm.autostart", "/tmm:autoStart"),
                    e("tmm.votemap", "/tmm:votemap"),
                    e("tmm.switchmap", "/tmm:switchmap"),
                    e("tmm.fourthroom", "/tmm:fourthroom"),
                    e("tmm.createpoint", "/tmm:createpoint"),
                    e("tmm.togglewaypoints", "/tmm:togglewaypoints"),
                    e("tmm.showstats", "/tmm:showStats"),
                    e("tmm.showselectedmapui", "/tmm:showSelectedMapUI"),
                    e("tmm.netstats", "/tmm:netstats"),
                    e("tmm.netstatsc", "/tmm:netstatsc"),
                    e("tmm.giveroomkey", "/tmm:giveRoomKey"),
                    e("tmm.participate", "/tmm:participate"),
                    e("tmm.entity_interact_cmd", "/tmm:entity_interact_cmd"),
                    e("tmm.reload", "/tmm:reload"),
                    e("tmm.skinsync", "/tmm:skinsync"),
                    e("tmm.skins", "/tmm:skins"),
                    e("forceteam", "/forceTeam"),
                    e("listgameroles", "/listGameRoles"),
                    e("stop_when_over", "/stop_when_over"),
                    e("sre.narrator", "/sre:narrator"),
                    e("sre.custom_replay", "/sre:custom_replay"),
                    e("sre.show_replay", "/sre:show_replay"),
                    e("sre.replay_screen", "/sre:replay_screen"),
                    e("sre.kick", "/sre:kick"),
                    e("sre.shield", "/sre:shield"),
                    e("sre.poison", "/sre:poison"),
                    e("sre.stamina", "/sre:stamina"),
                    e("sre.entitydisguise", "/sre:disguise"),
                    e("sre.morph", "/sre:morph"),
                    e("sre.inventory", "/sre:inventory"),
                    e("sre.invsee", "/sre:invsee"),
                    e("sre.monitor", "/sre:monitor"),
                    e("sre.vote", "/sre:vote"),
                    e("sre.reloadroleconfig", "/sre:reloadroleconfig"),
                    e("sre.area_manager", "/sre:area_manager"),
                    e("sre.pass", "/sre:pass"),
                    e("sre.roster", "/sre:roster"),
                    e("sre.scene", "/sre:scene"),
                    e("sre.camera", "/sre:camera"),
                    e("sre.subtitle", "/sre:subtitle"),
                    e("sre.eggclear", "/sre:eggclear"),
                    e("sre.infected", "/sre:infected"),
                    e("sre.helium", "/sre:helium"),
                    e("sre.block_cmd_perm", "/sre:block_cmd_perm"))),

            new Category("game", 0xFF77CC88, List.of(
                    e("tmm.game", "/tmm:game"),
                    e("tmm.game.visual", "/tmm:game visual"),
                    e("tmm.game.time", "/tmm:game time"),
                    e("tmm.game.penalty", "/tmm:game penalty"),
                    e("tmm.game.bounds", "/tmm:game bounds"),
                    e("tmm.game.abilities", "/tmm:game abilities"),
                    e("tmm.game.role", "/tmm:game role"),
                    e("tmm.game.murder_time", "/tmm:game murder_time"),
                    e("tmm.game.tests", "/tmm:game tests"),
                    e("tmm.game.tasks", "/tmm:game tasks"),
                    e("tmm.game.win", "/tmm:game win"),
                    e("tmm.game.reset", "/tmm:game reset"),
                    e("tmm.game.scan", "/tmm:game scan"),
                    e("tmm.game.blackout", "/tmm:game blackout"),
                    e("tmm.game.monitor_broken", "/tmm:game monitor_broken"),
                    e("tmm.game.psycho", "/tmm:game psycho"),
                    e("tmm.game.body", "/tmm:game body"),
                    e("tmm.game.revive", "/tmm:game revive"),
                    e("tmm.game.kill", "/tmm:game kill"),
                    e("tmm.game.use_skill", "/tmm:game use_skill"),
                    e("tmm.game.timestop", "/tmm:game timestop"))),

            new Category("role", 0xFFCC8844, List.of(
                    e("changerole", "/changeRole"),
                    e("changemodifier", "/changeModifier"),
                    e("forcerole", "/forceRole"),
                    e("forcemodifier", "/forceModifier"),
                    e("setrolecount", "/setRoleCount"),
                    e("setroleweight", "/setRoleWeight"),
                    e("myroleweight", "/myRoleWeight"),
                    e("playerroleweight", "/playerRoleWeight"),
                    e("togglecustomroleweights", "/toggleCustomRoleWeights"),
                    e("setoccupationrole", "/sre:occupation_role"),
                    e("setenabledrole", "/setEnabledRole"),
                    e("setenabledmodifier", "/setEnabledModifier"),
                    e("setcompanionrole", "/setCompanionRole"),
                    e("listroles", "/listRoles"),
                    e("managerolesui", "/manageRolesUI"))),

            new Category("noelle", 0xFFBB77CC, List.of(
                    e("broadcast", "/broadcast"),
                    e("noellesroles.preset", "/noellesroles preset"),
                    e("noellesroles.setmax", "/noellesroles setmax"),
                    e("room", "/room"),
                    e("stuck", "/stuck"),
                    e("vt_mode", "/vt_mode"),
                    e("nr_free_cam", "/nr_free_cam"),
                    e("displayitem", "/item_display"),
                    e("cooldown", "/cooldown"),
                    e("item", "/item"),
                    e("goods.add", "/goods:add"),
                    e("goods.remove", "/goods:remove"),
                    e("goods.list", "/goods:list"))),

            new Category("content", 0xFF66AADD, List.of(
                    e("sre.give", "/sre:give"),
                    e("sre.setblock", "/sre:setblock"),
                    e("sre.clone", "/sre:clone"),
                    e("sre.reload", "/sre:reload"),
                    e("sre.reload.custom_roles", "/sre:reload custom_roles"),
                    e("sre.reload.custom_modifiers", "/sre:reload custom_modifiers"),
                    e("sre.reload.custom_items", "/sre:reload custom_items"),
                    e("sre.reload.custom_blocks", "/sre:reload custom_blocks"))),

            new Category("misc", 0xFF8899AA, List.of(
                    e("nametag.add", "/nametag:add"),
                    e("nametag.remove", "/nametag:remove"),
                    e("nametag.set", "/nametag:set"),
                    e("nametag.get", "/nametag:get"),
                    e("nametag.list", "/nametag:list"),
                    e("nametag.clear", "/nametag:clear"),
                    e("nametag.sync", "/nametag:sync"))),

            new Category("client", 0xFF77DDAA, List.of(
                    e("sre.client", "/sre:client"),
                    e("sreclient.scene", "/sre:client scene"))),

            new Category("mixin", 0xFFCC6666, List.of(
                    e("kill", "/kill"))));

    private static final Map<String, Entry> BY_KEY = buildKeyMap();

    private static Map<String, Entry> buildKeyMap() {
        Map<String, Entry> map = new LinkedHashMap<>();
        for (Category category : CATEGORIES) {
            for (Entry entry : category.entries()) {
                map.putIfAbsent(entry.key(), entry);
            }
        }
        return map;
    }

    /** 同一条指令会出现在多个分类中，取第一次出现的字面量。 */
    public static Entry get(String key) {
        return BY_KEY.get(key);
    }

    private TrainCommandManualEntries() {
    }
}
