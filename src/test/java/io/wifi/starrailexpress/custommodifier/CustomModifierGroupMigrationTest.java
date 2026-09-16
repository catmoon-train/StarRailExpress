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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 修饰符「多组触发」的旧数据迁移。
 *
 * <p>
 * 老 JSON 只有一份顶层 {@code conditions / commands / effects / attributes / removeModifierOnTrigger}，
 * 模型改成 {@code groups} 之后必须能自动把老文件读成「一个组」，否则所有现存自定义修饰符会在
 * 重载后变成「什么都没有」（条件丢失、指令不执行）。这里把迁移规则钉住。
 */
class CustomModifierGroupMigrationTest {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static CustomModifierData parse(String json) {
        return GSON.fromJson(json, CustomModifierData.class);
    }

    @Test
    void legacyTopLevelFieldsBecomeExactlyOneGroup() {
        String json = """
                {
                  "englishId": "old_modifier",
                  "displayName": "老修饰符",
                  "conditions": [
                    { "type": "TIMER", "value": 30, "comparison": "EQUALS", "logic": "AND" },
                    { "type": "COIN_AMOUNT", "value": 100, "comparison": "GREATER_EQUAL", "logic": "AND" }
                  ],
                  "removeModifierOnTrigger": true,
                  "commands": ["say hi <player>"],
                  "effects": [{ "effectId": "minecraft:speed", "amplifier": 1, "durationSeconds": 10 }],
                  "attributes": [{ "attributeId": "minecraft:generic.scale", "value": 1.5 }]
                }
                """;

        CustomModifierData data = parse(json);
        List<CustomModifierData.TriggerGroupData> groups = data.effectiveGroups();

        assertEquals(1, groups.size(), "老 JSON 应该迁移成一个组");
        assertEquals(1, data.groupCount());
        CustomModifierData.TriggerGroupData group = groups.get(0);
        assertEquals(2, group.conditions.size());
        assertEquals("TIMER", group.conditions.get(0).type);
        assertEquals(100D, group.conditions.get(1).value);
        assertEquals(List.of("say hi <player>"), group.commands);
        assertEquals(1, group.effects.size());
        assertEquals("minecraft:speed", group.effects.get(0).effectId);
        assertEquals(1, group.attributes.size());
        assertTrue(group.removeModifierOnTrigger, "移除修饰符的开关也要跟着搬进组里");
        assertFalse(group.isGlobal(), "有条件 = 条件组");

        // 迁移后就地清空旧字段：再保存时写出的只有 groups
        assertTrue(data.conditions.isEmpty());
        assertTrue(data.commands.isEmpty());
        assertTrue(data.effects.isEmpty());
        assertTrue(data.attributes.isEmpty());
        assertFalse(data.removeModifierOnTrigger);

        // 幂等：再取一次还是同一份，不会又迁出一个组
        assertSame(groups, data.effectiveGroups());
        assertEquals(1, data.groupCount());
    }

    @Test
    void legacyGlobalModifierKeepsItsContent() {
        // 老「全局修饰符」：没有条件，只有持续生效的效果
        String json = """
                {
                  "englishId": "global_modifier",
                  "effects": [{ "effectId": "minecraft:night_vision", "amplifier": 0, "durationSeconds": 5 }],
                  "attributes": [{ "attributeId": "minecraft:generic.max_health", "value": 4.0 }]
                }
                """;
        CustomModifierData data = parse(json);
        List<CustomModifierData.TriggerGroupData> groups = data.effectiveGroups();

        assertEquals(1, groups.size());
        assertTrue(groups.get(0).isGlobal(), "没有条件 = 全局组");
        assertEquals(1, groups.get(0).effects.size());
        assertEquals(1, groups.get(0).attributes.size());
        assertTrue(data.isGlobalTrigger());
    }

    @Test
    void emptyModifierHasNoGroupsButCountsAsGlobal() {
        CustomModifierData data = parse("{ \"englishId\": \"empty\" }");
        assertTrue(data.effectiveGroups().isEmpty());
        assertEquals(0, data.groupCount());
        // 没有任何组时保持旧语义：视为全局（运行时不判条件）
        assertTrue(data.isGlobalTrigger());
    }

    @Test
    void explicitGroupsAreUsedAsIsAndLegacyFieldsIgnored() {
        String json = """
                {
                  "englishId": "new_modifier",
                  "conditions": [{ "type": "TIMER", "value": 5 }],
                  "commands": ["legacy command"],
                  "groups": [
                    {
                      "conditions": [{ "type": "HAS_ITEM", "stringValue": "minecraft:iron_ingot" }],
                      "commands": ["give reward"],
                      "effects": [{ "effectId": "minecraft:speed", "amplifier": 0, "durationSeconds": 3 }],
                      "removeModifierOnTrigger": false
                    },
                    {
                      "conditions": [],
                      "effects": [{ "effectId": "minecraft:resistance", "amplifier": 2, "durationSeconds": 1 }],
                      "attributes": [{ "attributeId": "minecraft:generic.movement_speed", "value": 0.1 }]
                    }
                  ]
                }
                """;
        CustomModifierData data = parse(json);
        List<CustomModifierData.TriggerGroupData> groups = data.effectiveGroups();

        assertEquals(2, groups.size(), "已有 groups 时不再迁移，旧字段直接忽略");
        assertEquals(1, groups.get(0).conditions.size());
        assertEquals("HAS_ITEM", groups.get(0).conditions.get(0).type);
        assertEquals(List.of("give reward"), groups.get(0).commands);
        assertFalse(groups.get(0).isGlobal());
        assertTrue(groups.get(1).isGlobal());
        assertEquals(1, groups.get(1).attributes.size());
        // 有一组带条件 → 整体不是全局触发
        assertFalse(data.isGlobalTrigger());
        // 旧字段没被搬动（因为它们本来就没被采用）
        assertEquals(1, data.conditions.size());
    }

    @Test
    void groupsSurviveAJsonRoundTrip() {
        CustomModifierData data = parse("""
                {
                  "englishId": "round_trip",
                  "groups": [
                    { "conditions": [{ "type": "PLAYER_COUNT", "value": 6, "comparison": "GREATER_EQUAL" }],
                      "commands": ["say enough players"], "removeModifierOnTrigger": true },
                    { "conditions": [], "effects": [{ "effectId": "minecraft:speed", "amplifier": 3, "durationSeconds": 30 }] }
                  ]
                }
                """);
        String written = GSON.toJson(data);
        CustomModifierData reloaded = parse(written);

        assertEquals(2, reloaded.groupCount());
        assertEquals("PLAYER_COUNT", reloaded.effectiveGroups().get(0).conditions.get(0).type);
        assertEquals(6D, reloaded.effectiveGroups().get(0).conditions.get(0).value);
        assertTrue(reloaded.effectiveGroups().get(0).removeModifierOnTrigger);
        assertEquals(3, reloaded.effectiveGroups().get(1).effects.get(0).amplifier);
    }

    @Test
    void migrationHappensEvenWhenOnlyTheRemoveFlagWasSet() {
        // 极端情况：老修饰符只勾了「触发后移除」而没有任何内容
        CustomModifierData data = parse("{ \"englishId\": \"flag_only\", \"removeModifierOnTrigger\": true }");
        assertFalse(data.effectiveGroups().isEmpty(), "只设了移除开关也要迁移，否则等于配置丢失");
        assertTrue(data.effectiveGroups().get(0).removeModifierOnTrigger);
        assertTrue(data.effectiveGroups().get(0).isGlobal());
    }
}
