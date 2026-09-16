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

import io.wifi.starrailexpress.api.AreasSettingUtils.MapSpecialFeatures;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import io.wifi.starrailexpress.client.gui.screen.CustomEditorScreen;
import io.wifi.starrailexpress.customrole.CustomRoleData.EffectEntry;
import io.wifi.starrailexpress.customrole.CustomRoleData.InstinctModeData;
import io.wifi.starrailexpress.customrole.CustomRoleData.InitialItemEntry;
import io.wifi.starrailexpress.customrole.CustomRoleData.ShopEntryData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 自定义职业编辑界面（5 页：基础 / 进阶 / 能力 / 生成 / 商店）。
 *
 * <p>
 * 布局、页签、滚动、渲染与鼠标键盘全部交给 {@link CustomEditorScreen}。这次改造修掉了旧实现里
 * 三个会直接影响使用的问题：
 * <ol>
 * <li>旧实现把 5 个页签的控件<b>全部</b>注册进事件系统，只绘制当前页签 —— 各页签的同坐标控件完全重合，
 * 点击会命中别的页签里看不见的输入框，打字会写进别的页签的数据；现在只注册当前页签；</li>
 * <li>旧实现用 {@code init(minecraft, width, height)} 原地重建（37 处），绕过了 {@code clearWidgets()}，
 * 每次重建都往事件系统里再注册一遍控件、只增不减；现在统一走 {@link #requestRebuild()}；</li>
 * <li>行内控件靠 {@code fieldX() + 220} 这类写死偏移横向拼接，面板一窄就戳出面板、被裁掉还点不到；
 * 现在用 {@link #cluster} 声明，装不下自动折行。</li>
 * </ol>
 *
 * <p>
 * 文案一律走翻译键（{@code sre.custom_role.*}）。
 */
@Environment(EnvType.CLIENT)
public class CustomRoleScreen extends CustomEditorScreen {

    private static final String PREFIX = "sre.custom_role";
    private static final String[] TABS = { "basic", "advanced", "ability", "generation", "shop" };

    private CustomRoleData data = new CustomRoleData();
    private String originalEnglishId = "";
    private int moodIndex;

    /** 任务列表编辑器里「候选类型」的下标持有者（点按钮循环切换，重建界面后保持）。 */
    private final int[] unrefreshableTaskCursor = { 0 };
    private final int[] onlyRefreshableTaskCursor = { 0 };

    public CustomRoleScreen() {
        super(Component.translatable(PREFIX + ".title"));
        syncToggles();
    }

    public CustomRoleScreen(CustomRoleData d) {
        super(Component.translatable(PREFIX + ".title"));
        if (d != null) {
            this.data = d;
            this.originalEnglishId = d.englishId == null ? "" : d.englishId;
        }
        syncToggles();
    }

    private void syncToggles() {
        moodIndex = "FAKE".equalsIgnoreCase(data.moodType) ? 1 : 0;
    }

    @Override
    protected String translationPrefix() {
        return PREFIX;
    }

    @Override
    protected String[] tabKeys() {
        return TABS;
    }

    @Override
    protected void buildTab(int tab) {
        switch (tab) {
            case 0 -> buildBasicTab();
            case 1 -> buildAdvancedTab();
            case 2 -> buildAbilityTab();
            case 3 -> buildGenerationTab();
            case 4 -> buildShopTab();
            default -> {
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 行包装
    // ══════════════════════════════════════════════════════════════════

    private static Component hint(String key) {
        return key == null || key.isEmpty() ? null : Component.translatable(key);
    }

    /** 标签列 + 字段区撑满的文本行（限长 + 占位提示 + 悬停全文）。 */
    private int textRow(int r, String labelKey, String value, int limit, String hintKey, Consumer<String> setter) {
        return field(r, labelKey, value, limit, hint(hintKey), setter);
    }

    /** 标签列 + 定宽数值框（数值解析、越界钳制都在调用方的 setter 里）。 */
    private int numRow(int r, String labelKey, String value, int width, String hintKey, Consumer<String> setter) {
        return cluster(r, labelKey, fixedBox(value, LIMIT_NUMBER, width, hint(hintKey), setter));
    }

    /** 一行只有一个「＋」式的按钮（列表尾部追加用）。 */
    private int addRow(int r, Component text, Runnable onClick) {
        return cluster(r, null, fixedButton(text, 160, onClick));
    }

    /**
     * 三态开关（是 / 否 / 未设置）：一行一个或一行两个都用它。
     *
     * <p>
     * 点击**不重建界面**（只有影响显示哪些字段的才传 {@code rebuild}），切换后不会丢焦点、也不会跳回顶部。
     */
    private Cell triCell(String key, Boolean current, Consumer<Boolean> setter, boolean rebuild) {
        return triSwitchCell(key, current, setter, rebuild);
    }

    /** 每行一个「×」的删除行（列表项用）。 */
    private int removeRow(int r, Component text, Runnable onClick) {
        return cluster(r, null, fixedButton(text, 200, onClick));
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 0：基础
    // ══════════════════════════════════════════════════════════════════
    private void buildBasicTab() {
        int r = 0;
        r = field(r, PREFIX + ".label.english_id", data.englishId, 64, hint(PREFIX + ".hint.english_id"),
                value -> data.englishId = value.toLowerCase());
        r = textRow(r, PREFIX + ".label.display_name", data.displayName, LIMIT_NAME,
                PREFIX + ".hint.display_name", value -> data.displayName = value);
        r = textRow(r, PREFIX + ".label.goals", data.goals, LIMIT_TEXT, PREFIX + ".hint.goals",
                value -> data.goals = value);
        r = textRow(r, PREFIX + ".label.description", data.description, LIMIT_TEXT,
                PREFIX + ".hint.description", value -> data.description = value);

        // 自定义标签：挂成运行时职业的 flags，介绍页就能按它筛选
        r = lines(r, PREFIX + ".label.tags", data.tags, LIMIT_ID, PREFIX + ".hint.tags", null,
                PREFIX + ".add_tag", PREFIX + ".remove", Integer.MAX_VALUE);

        StringBuilder effects = new StringBuilder();
        for (EffectEntry entry : data.initialEffects) {
            if (!effects.isEmpty()) {
                effects.append(";");
            }
            effects.append(entry.effectId).append(",").append(entry.amplifier);
        }
        r = cluster(r, PREFIX + ".label.effects",
                box(effects.toString(), LIMIT_COMMAND, 200, hint(PREFIX + ".hint.effects_example"), value -> {
                    data.initialEffects.clear();
                    if (value == null || value.isBlank()) {
                        return;
                    }
                    java.util.regex.Matcher matcher = java.util.regex.Pattern
                            .compile("([a-z0-9_\\-.:]+)[,:](\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                            .matcher(value);
                    while (matcher.find()) {
                        int level = 0;
                        try {
                            level = Integer.parseInt(matcher.group(2));
                        } catch (Exception ignored) {
                        }
                        data.initialEffects.add(new EffectEntry(matcher.group(1).trim(), level));
                    }
                }));

        r = rgb(r, PREFIX + ".label.color_rgb", null,
                new int[] { data.colorR, data.colorG, data.colorB }, value -> {
                    data.colorR = value[0];
                    data.colorG = value[1];
                    data.colorB = value[2];
                });

        r = cluster(r, null,
                toggleCell(PREFIX + ".is_innocent", data.isInnocent, value -> data.isInnocent = value, false),
                toggleCell(PREFIX + ".can_use_killer", data.canUseKiller, value -> data.canUseKiller = value, false));

        // 心情类型：真实 / 伪装（按钮文字就地刷新，不用重建）
        r = cluster(r, PREFIX + ".label.mood",
                stateButtonCell(() -> Component.translatable(PREFIX + ".mood.current").append(": ")
                        .append(Component.translatable(PREFIX + ".mood." + (moodIndex == 0 ? "real" : "fake"))),
                        () -> {
                            moodIndex = (moodIndex + 1) % 2;
                            data.moodType = moodIndex == 0 ? "REAL" : "FAKE";
                        }, false));

        // 心情颜色覆盖（R/G/B，任一 <0 视为不覆盖）
        r = numRow(r, PREFIX + ".label.mood_color_r", String.valueOf(data.moodColorR), 60,
                PREFIX + ".hint.minus_one_default", value -> {
                    try {
                        data.moodColorR = Integer.parseInt(value);
                    } catch (Exception ignored) {
                    }
                });
        r = numRow(r, PREFIX + ".label.mood_color_g", String.valueOf(data.moodColorG), 60,
                PREFIX + ".hint.minus_one_default", value -> {
                    try {
                        data.moodColorG = Integer.parseInt(value);
                    } catch (Exception ignored) {
                    }
                });
        r = numRow(r, PREFIX + ".label.mood_color_b", String.valueOf(data.moodColorB), 60,
                PREFIX + ".hint.minus_one_default", value -> {
                    try {
                        data.moodColorB = Integer.parseInt(value);
                    } catch (Exception ignored) {
                    }
                });

        r = numRow(r, PREFIX + ".label.sprint_mult", String.valueOf(data.sprintMultiplier), 80,
                PREFIX + ".hint.default_one", value -> {
                    try {
                        data.sprintMultiplier = Double.parseDouble(value);
                    } catch (Exception ignored) {
                    }
                });
        r = cluster(r, null,
                toggleCell(PREFIX + ".infinite_sprint", data.infiniteSprint, value -> data.infiniteSprint = value,
                        false),
                toggleCell(PREFIX + ".can_see_time", data.canSeeTime, value -> data.canSeeTime = value, false));
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 1：进阶
    // ══════════════════════════════════════════════════════════════════
    private void buildAdvancedTab() {
        int r = 0;
        r = cluster(r, null,
                toggleCell(PREFIX + ".can_see_coin", data.canSeeCoin, value -> data.canSeeCoin = value, false));
        r = cluster(r, null,
                triCell(PREFIX + ".able_pickup_revolver", data.ableToPickUpRevolver,
                        value -> data.ableToPickUpRevolver = value, false),
                triCell(PREFIX + ".set_neutrals", data.setNeutrals, value -> data.setNeutrals = value, true));
        r = cluster(r, null,
                triCell(PREFIX + ".set_neutral_for_killer", data.setNeutralForKiller,
                        value -> data.setNeutralForKiller = value, true),
                triCell(PREFIX + ".set_vigilante_team", data.setVigilanteTeam,
                        value -> data.setVigilanteTeam = value, false));
        r = cluster(r, null,
                triCell(PREFIX + ".can_see_teammate_killer", data.canSeeTeammateKiller,
                        value -> data.canSeeTeammateKiller = value, false));

        r = numRow(r, PREFIX + ".label.occupied_role_count", String.valueOf(data.occupiedRoleCount), 80,
                PREFIX + ".hint.default_one", value -> {
                    try {
                        data.occupiedRoleCount = Integer.parseInt(value);
                    } catch (Exception ignored) {
                    }
                });
        r = numRow(r, PREFIX + ".label.max_count", String.valueOf(data.maxCount), 80,
                PREFIX + ".hint.default_one", value -> {
                    try {
                        data.maxCount = Integer.parseInt(value);
                    } catch (Exception ignored) {
                    }
                });

        r = cluster(r, null,
                triCell(PREFIX + ".can_auto_add_money", data.canAutoAddMoney,
                        value -> data.canAutoAddMoney = value, false));
        r = cluster(r, null,
                toggleCell(PREFIX + ".can_be_randomed", data.canBeRandomedByOtherRoles,
                        value -> data.canBeRandomedByOtherRoles = value, false),
                triCell(PREFIX + ".can_ignore_blackout", data.canIgnoreBlackout,
                        value -> data.canIgnoreBlackout = value, false));
        r = cluster(r, null,
                triCell(PREFIX + ".can_see_body_items", data.canSeeBodyItems,
                        value -> data.canSeeBodyItems = value, false),
                triCell(PREFIX + ".can_see_body_role_info", data.canSeeBodyRoleInfo,
                        value -> data.canSeeBodyRoleInfo = value, false));
        r = cluster(r, null,
                triCell(PREFIX + ".can_see_body_death_reason", data.canSeeBodyDeathReason,
                        value -> data.canSeeBodyDeathReason = value, false),
                triCell(PREFIX + ".can_see_body_killer", data.canSeeBodyKiller,
                        value -> data.canSeeBodyKiller = value, false));

        // === 职业通用属性补全 ===
        r = gap(r);
        r = cluster(r, null,
                triCell(PREFIX + ".neutral_for_innocent", data.neutralForInnocent,
                        value -> data.neutralForInnocent = value, false),
                triCell(PREFIX + ".mafia_team", data.mafiaTeam, value -> data.mafiaTeam = value, false));
        r = cluster(r, null,
                triCell(PREFIX + ".can_see_body_name", data.canSeeBodyName, value -> data.canSeeBodyName = value,
                        false),
                triCell(PREFIX + ".can_use_skill_while_spectator", data.canUseSkillWhileSpectator,
                        value -> data.canUseSkillWhileSpectator = value, false));
        r = cluster(r, null,
                triCell(PREFIX + ".can_be_poisoned", data.canBePoisoned, value -> data.canBePoisoned = value, false),
                triCell(PREFIX + ".hidden_for_role_rotation", data.hiddenForRoleRotation,
                        value -> data.hiddenForRoleRotation = value, false));
        r = cluster(r, null,
                triCell(PREFIX + ".special_vigilante", data.specialVigilante,
                        value -> data.specialVigilante = value, false),
                triCell(PREFIX + ".refreshable_special_vigilante", data.refreshableSpecialVigilante,
                        value -> data.refreshableSpecialVigilante = value, false));
        r = numRow(r, PREFIX + ".label.refresh_special_vigilante_chance",
                String.valueOf(data.refreshableSpecialVigilanteChance), 80, PREFIX + ".hint.chance_range_10000",
                value -> {
                    try {
                        data.refreshableSpecialVigilanteChance = Math.min(10000, Math.max(0,
                                Integer.parseInt(value)));
                    } catch (Exception ignored) {
                    }
                });
        r = cluster(r, null,
                triCell(PREFIX + ".can_jump_manhole", data.canJumpManhole, value -> data.canJumpManhole = value,
                        false),
                triCell(PREFIX + ".can_across_fog", data.canAcrossFog, value -> data.canAcrossFog = value, false));
        r = cluster(r, null,
                triCell(PREFIX + ".can_use_sabotage", data.canUseSabotage, value -> data.canUseSabotage = value,
                        false));

        // === 免疫 / 经济 / 战斗 / 杀手同伙 ===
        r = gap(r);
        r = cluster(r, null,
                triCell(PREFIX + ".fall_damage_immune", data.fallDamageImmune,
                        value -> data.fallDamageImmune = value, false),
                triCell(PREFIX + ".darkness_immune", data.darknessImmune, value -> data.darknessImmune = value,
                        false));
        r = cluster(r, null,
                triCell(PREFIX + ".environmental_immune", data.environmentalImmune,
                        value -> data.environmentalImmune = value, false),
                triCell(PREFIX + ".no_coin_system", data.noCoinSystem, value -> data.noCoinSystem = value, false));
        r = numRow(r, PREFIX + ".label.initial_coin_count", String.valueOf(data.initialCoinCount), 80,
                PREFIX + ".hint.minus_one_no_change", value -> {
                    try {
                        data.initialCoinCount = Integer.parseInt(value);
                    } catch (Exception ignored) {
                    }
                });
        r = cluster(r, null,
                triCell(PREFIX + ".cannot_earn_coin_from_kills", data.cannotEarnCoinFromKills,
                        value -> data.cannotEarnCoinFromKills = value, false));
        r = numRow(r, PREFIX + ".label.neutral_kill_coin", String.valueOf(data.neutralKillCoin), 80,
                PREFIX + ".hint.zero_no_give", value -> {
                    try {
                        data.neutralKillCoin = Math.max(0, Integer.parseInt(value));
                    } catch (Exception ignored) {
                    }
                });
        r = cluster(r, null,
                triCell(PREFIX + ".can_kill_with_bow_crossbow", data.canKillWithBowAndCrossbow,
                        value -> data.canKillWithBowAndCrossbow = value, false),
                triCell(PREFIX + ".can_kill_with_trident", data.canKillWithTrident,
                        value -> data.canKillWithTrident = value, false));
        r = cluster(r, null,
                triCell(PREFIX + ".cannot_knife_left_click", data.cannotKnifeLeftClick,
                        value -> data.cannotKnifeLeftClick = value, false));
        r = cluster(r, null,
                triCell(PREFIX + ".can_use_dream_axe", data.canUseDreamAxe, value -> data.canUseDreamAxe = value,
                        false),
                triCell(PREFIX + ".killer_teammate_visibility_enabled", data.killerTeammateVisibilityEnabled,
                        value -> data.killerTeammateVisibilityEnabled = value, false));
        r = cluster(r, null,
                triCell(PREFIX + ".can_be_seen_as_killer_teammate", data.canBeSeenAsKillerTeammate,
                        value -> data.canBeSeenAsKillerTeammate = value, false));

        // === 自定义独立胜利 (仅中立 && !杀手方中立时可用) ===
        boolean isNeutral = data.setNeutrals != null && data.setNeutrals;
        boolean isNotKillerNeutral = data.setNeutralForKiller != null && !data.setNeutralForKiller;
        if (isNeutral && isNotKillerNeutral) {
            r = section(r, PREFIX + ".custom_win_section");
            r = cluster(r, null,
                    toggleCell(PREFIX + ".enable_custom_win", data.enableCustomWin,
                            value -> data.enableCustomWin = value, true));
            if (data.enableCustomWin) {
                r = textRow(r, PREFIX + ".custom_win_title", data.customWinTitle, LIMIT_NAME, null,
                        value -> data.customWinTitle = value);
                r = textRow(r, PREFIX + ".custom_win_subtitle", data.customWinSubtitle, LIMIT_NAME, null,
                        value -> data.customWinSubtitle = value);
                // 这两项互斥：切换一个要同时刷新另一个的文案，所以重建
                r = cluster(r, null,
                        toggleCell(PREFIX + ".custom_win_survive", data.customWinSurviveToLast, value -> {
                            data.customWinSurviveToLast = value;
                            if (value) {
                                data.customWinLastAlive = false;
                            }
                        }, true),
                        toggleCell(PREFIX + ".custom_win_last_alive", data.customWinLastAlive, value -> {
                            data.customWinLastAlive = value;
                            if (value) {
                                data.customWinSurviveToLast = false;
                            }
                        }, true));
                r = textRow(r, PREFIX + ".custom_win_with_roles",
                        String.join(";", data.customWinLastWithRoles), LIMIT_TEXT,
                        PREFIX + ".hint.role_semicolon_list", value -> {
                            data.customWinLastWithRoles.clear();
                            for (String part : value.split(";")) {
                                String trimmed = part.trim();
                                if (!trimmed.isEmpty()) {
                                    data.customWinLastWithRoles.add(trimmed);
                                }
                            }
                        });
                r = textRow(r, PREFIX + ".custom_win_tag_sleep", data.customWinTagSleep, LIMIT_TEXT,
                        PREFIX + ".hint.customwin_tag", value -> data.customWinTagSleep = value.trim());
                r = textRow(r, PREFIX + ".custom_win_held_item", data.customWinHeldItem, LIMIT_PATH,
                        PREFIX + ".hint.item_example", value -> data.customWinHeldItem = value.trim());
            }
        }

        // 特殊地图类型限制（枚举按钮，默认 ALL）
        r = cluster(r, PREFIX + ".special_map_role",
                stateButtonCell(() -> Component.translatable(PREFIX + ".special_map_role.current").append(": ")
                        .append(Component.literal(data.specialMapRole)), () -> {
                            // 直接取自枚举，避免像以前那样漏掉 HORSE 之类的特性
                            String[] values = java.util.Arrays.stream(MapSpecialFeatures.values())
                                    .map(MapSpecialFeatures::name).toArray(String[]::new);
                            int index = java.util.Arrays.asList(values).indexOf(data.specialMapRole);
                            if (index < 0) {
                                // 配置里是非法值（或大小写不符）：先回到 ALL，避免按一下按钮就跳到别的特性上
                                data.specialMapRole = "ALL";
                            } else {
                                data.specialMapRole = values[(index + 1) % values.length];
                            }
                        }, false));

        // 小游戏任务独立计时 / 被透视时隐藏职业信息
        r = cluster(r, null,
                triCell(PREFIX + ".independent_minigame_timing", data.independentMinigameTiming,
                        value -> data.independentMinigameTiming = value, false),
                triCell(PREFIX + ".hide_role_info_when_seen", data.hideRoleInfoWhenSeen,
                        value -> data.hideRoleInfoWhenSeen = value, false));
        // 能否"小脑"别人 / 能否被别人"小脑"
        r = cluster(r, null,
                triCell(PREFIX + ".can_xiaonao", data.canXiaonao, value -> data.canXiaonao = value, false),
                triCell(PREFIX + ".can_be_xiaonao", data.canBeXiaonao, value -> data.canBeXiaonao = value, false));
        // 结算时计入"存活好人" / "存活杀手"计数
        r = cluster(r, null,
                triCell(PREFIX + ".can_increase_surviving_innocents", data.canIncreaseSurvivingInnocents,
                        value -> data.canIncreaseSurvivingInnocents = value, false),
                triCell(PREFIX + ".can_increase_surviving_killers", data.canIncreaseSurvivingKillers,
                        value -> data.canIncreaseSurvivingKillers = value, false));
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 2：能力
    // ══════════════════════════════════════════════════════════════════
    private void buildAbilityTab() {
        int r = 0;

        // ═══ 初始物品 ═══
        r = gap(r);
        if (data.initialItems.isEmpty()) {
            data.initialItems.add(new InitialItemEntry());
        }
        for (InitialItemEntry entry : data.initialItems) {
            r = itemEntryRow(r, PREFIX + ".label.initial_items", data.initialItems, entry);
        }

        // ═══ 任务奖励（完成 N 个任务给物品） ═══
        r = section(r, PREFIX + ".task_reward_section");
        r = numRow(r, PREFIX + ".label.task_reward_count", String.valueOf(data.taskRewardCount), 60,
                PREFIX + ".hint.zero_off", value -> {
                    try {
                        data.taskRewardCount = Integer.parseInt(value);
                    } catch (Exception ignored) {
                    }
                });
        r = numRow(r, PREFIX + ".label.task_reward_max_triggers",
                String.valueOf(data.taskRewardMaxTriggers), 60, PREFIX + ".hint.default_one", value -> {
                    try {
                        data.taskRewardMaxTriggers = Integer.parseInt(value);
                    } catch (Exception ignored) {
                    }
                });
        r = cluster(r, null,
                toggleCell(PREFIX + ".task_reward_unlimited", data.taskRewardUnlimited,
                        value -> data.taskRewardUnlimited = value, false),
                toggleCell(PREFIX + ".task_reward_silent", data.taskRewardSilent,
                        value -> data.taskRewardSilent = value, false));
        r = textRow(r, PREFIX + ".task_reward_message", data.taskRewardMessage, LIMIT_TEXT, null,
                value -> data.taskRewardMessage = value);
        if (data.taskRewardItems.isEmpty()) {
            data.taskRewardItems.add(new InitialItemEntry());
        }
        for (InitialItemEntry entry : data.taskRewardItems) {
            r = itemEntryRow(r, PREFIX + ".label.task_reward_items", data.taskRewardItems, entry);
        }

        // ═══ 直觉系统 ═══
        r = section(r, PREFIX + ".instinct_section");
        r = cluster(r, null,
                toggleCell(PREFIX + ".can_use_instinct", data.canUseInstinct, value -> {
                    data.canUseInstinct = value;
                    if (!value) {
                        data.instinctModes.clear();
                    }
                }, true),
                triCell(PREFIX + ".instinct_night_vision", data.instinctNightVision,
                        value -> data.instinctNightVision = value, false));

        if (data.canUseInstinct) {
            ensureInstinctMode();
            InstinctModeData mode = data.instinctModes.get(0);

            // 看别人
            r = cluster(r, PREFIX + ".instinct_seeing",
                    instinctCell(() -> mode.seeingOff, value -> mode.seeingOff = value),
                    instinctCell(() -> mode.seeingOn, value -> mode.seeingOn = value));
            // 被看
            r = cluster(r, PREFIX + ".instinct_be_seen",
                    instinctCell(() -> mode.beSeenOff, value -> mode.beSeenOff = value),
                    instinctCell(() -> mode.beSeenOn, value -> mode.beSeenOn = value));

            // 自定义颜色输入（当任一字段为 CUSTOM 时显示）
            boolean hasCustom = isCustomType(mode.seeingOff) || isCustomType(mode.seeingOn)
                    || isCustomType(mode.beSeenOff) || isCustomType(mode.beSeenOn);
            if (hasCustom) {
                // 收集所有 CUSTOM 字段，使用同一个颜色输入
                r = cluster(r, PREFIX + ".instinct_custom_color",
                        fixedBox(findFirstCustomHex(mode), 32, 80, hint(PREFIX + ".hint.hex"), value -> {
                            String hex = value.trim().replaceAll("[^0-9a-fA-F]", "");
                            if (hex.isEmpty()) {
                                hex = "FF0000";
                            }
                            String newValue = "CUSTOM(0x" + hex + ")";
                            if (isCustomType(mode.seeingOff)) {
                                mode.seeingOff = newValue;
                            }
                            if (isCustomType(mode.seeingOn)) {
                                mode.seeingOn = newValue;
                            }
                            if (isCustomType(mode.beSeenOff)) {
                                mode.beSeenOff = newValue;
                            }
                            if (isCustomType(mode.beSeenOn)) {
                                mode.beSeenOn = newValue;
                            }
                        }));
            }

            // 看别人最大距离（透视范围）
            r = cluster(r, PREFIX + ".label.instinct_range",
                    fixedBox(mode.maxRange, LIMIT_TEXT, 80, hint(PREFIX + ".hint.any_range"),
                            value -> mode.maxRange = value));
            // 被透视最大距离（被别人看范围，独立）
            r = cluster(r, PREFIX + ".label.instinct_beseen_range",
                    fixedBox(mode.beSeenMaxRange == null ? "*" : mode.beSeenMaxRange, LIMIT_TEXT, 80,
                            hint(PREFIX + ".hint.any_range_beseen"), value -> mode.beSeenMaxRange = value));
            r = cluster(r, null,
                    toggleCell(PREFIX + ".instinct_unlimited_teammate", mode.unlimitedTeammate,
                            value -> mode.unlimitedTeammate = value, false));
        }

        // ═══ 技能 ═══
        r = gap(r);
        r = cluster(r, null,
                toggleCell(PREFIX + ".enable_ability", data.enableAbility, value -> data.enableAbility = value,
                        true));
        if (data.enableAbility) {
            // 是否启用切换技能
            r = cluster(r, null,
                    toggleCell(PREFIX + ".enable_skill_switch", data.enableSkillSwitch,
                            value -> data.enableSkillSwitch = value, true));

            if (data.enableSkillSwitch) {
                // ===== 多技能模块（技能1、技能2…） =====
                if (data.skillModules.isEmpty()) {
                    data.skillModules.add(new CustomRoleData.SkillData());
                }
                for (int m = 0; m < data.skillModules.size(); m++) {
                    final int moduleIndex = m;
                    CustomRoleData.SkillData skill = data.skillModules.get(m);
                    // 一个技能模块一张卡片：标题「技能 N」+ 技能名徽标，卡片头右侧删除模块
                    r = cardBegin(r, "role_skill_" + moduleIndex,
                            Component.translatable(PREFIX + ".skill_module_title", moduleIndex + 1),
                            skill.name == null || skill.name.isBlank() ? null : Component.literal(skill.name),
                            () -> data.skillModules.remove(moduleIndex));
                    r = textRow(r, PREFIX + ".skill_name", skill.name, LIMIT_NAME, null,
                            value -> skill.name = value);
                    r = stringList(r, skill.commands, PREFIX + ".label.ability_commands", 220,
                            PREFIX + ".hint.command");
                    r = numRow(r, PREFIX + ".label.ability_cooldown", String.valueOf(skill.cooldownSeconds), 80,
                            PREFIX + ".hint.cooldown_seconds", value -> {
                                try {
                                    skill.cooldownSeconds = Integer.parseInt(value);
                                } catch (Exception ignored) {
                                }
                            });
                    r = numRow(r, PREFIX + ".label.ability_initial_cooldown",
                            String.valueOf(skill.initialCooldownSeconds), 80,
                            PREFIX + ".hint.initial_cooldown_seconds", value -> {
                                try {
                                    skill.initialCooldownSeconds = Integer.parseInt(value);
                                } catch (Exception ignored) {
                                }
                            });
                    r = numRow(r, PREFIX + ".label.ability_delay_seconds",
                            String.valueOf(skill.delaySeconds), 80, PREFIX + ".hint.delay_seconds", value -> {
                                try {
                                    skill.delaySeconds = Integer.parseInt(value);
                                } catch (Exception ignored) {
                                }
                            });
                    r = stringList(r, skill.delayedCommands, PREFIX + ".label.ability_delayed_commands", 220,
                            PREFIX + ".hint.command_no_slash");
                    r = stringList(r, skill.gameEndCommands, PREFIX + ".label.game_end_commands", 220,
                            PREFIX + ".hint.command_no_slash");
                    r = gap(cardEnd(gap(r)));
                }
                r = addRow(r, Component.translatable(PREFIX + ".add_skill_module"),
                        () -> {
                            data.skillModules.add(new CustomRoleData.SkillData());
                            requestRebuild();
                        });
            } else {
                // ===== 单技能（旧字段，向后兼容） =====
                r = textRow(r, PREFIX + ".ability_name", data.abilityName, LIMIT_NAME, null,
                        value -> data.abilityName = value);
                r = stringList(r, data.abilitySkillCommands, PREFIX + ".label.ability_commands", 220,
                        PREFIX + ".hint.command");
                r = numRow(r, PREFIX + ".label.ability_cooldown",
                        String.valueOf(data.abilityCooldownSeconds), 80, PREFIX + ".hint.cooldown_seconds",
                        value -> {
                            try {
                                data.abilityCooldownSeconds = Integer.parseInt(value);
                            } catch (Exception ignored) {
                            }
                        });
                r = numRow(r, PREFIX + ".label.ability_initial_cooldown",
                        String.valueOf(data.abilityInitialCooldownSeconds), 80,
                        PREFIX + ".hint.initial_cooldown_seconds", value -> {
                            try {
                                data.abilityInitialCooldownSeconds = Integer.parseInt(value);
                            } catch (Exception ignored) {
                            }
                        });
                r = numRow(r, PREFIX + ".label.ability_delay_seconds",
                        String.valueOf(data.abilityDelaySeconds), 80, PREFIX + ".hint.delay_seconds", value -> {
                            try {
                                data.abilityDelaySeconds = Integer.parseInt(value);
                            } catch (Exception ignored) {
                            }
                        });
                r = stringList(r, data.abilityDelayedCommands, PREFIX + ".label.ability_delayed_commands", 220,
                        PREFIX + ".hint.command_no_slash");
                r = stringList(r, data.gameEndCommands, PREFIX + ".label.game_end_commands", 220,
                        PREFIX + ".hint.command_no_slash");
            }
        }
    }

    /** 「物品 id + 数量 ±」条目行。 */
    private int itemEntryRow(int r, String labelKey, List<InitialItemEntry> list, InitialItemEntry entry) {
        return cluster(r, labelKey,
                box(entry.itemId, LIMIT_PATH, 130, hint(PREFIX + ".hint.item_id"), value -> entry.itemId = value),
                fixedBox(String.valueOf(entry.count), LIMIT_NUMBER, 50, hint(PREFIX + ".hint.count"), value -> {
                    try {
                        entry.count = Integer.parseInt(value);
                    } catch (Exception ignored) {
                    }
                }),
                fixedButton(Component.literal("+"), 22, () -> {
                    list.add(new InitialItemEntry());
                    requestRebuild();
                }),
                fixedButton(Component.literal("-"), 22, () -> {
                    list.remove(entry);
                    requestRebuild();
                }));
    }

    /** 「文本 id + 数量 ±」式字符串列表（指挥令行）。 */
    private int stringList(int r, List<String> list, String labelKey, int width, String hintKey) {
        r = labelRow(r, labelKey);
        if (list.isEmpty()) {
            list.add("");
        }
        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            r = cluster(r, null,
                    box(list.get(i), LIMIT_COMMAND, width, hint(hintKey), value -> list.set(index, value)),
                    fixedButton(Component.literal("+"), 22, () -> {
                        list.add("");
                        requestRebuild();
                    }),
                    fixedButton(Component.literal("-"), 22, () -> {
                        list.remove(index);
                        requestRebuild();
                    }));
        }
        return addRow(r, Component.literal("+"), () -> {
            list.add("");
            requestRebuild();
        });
    }

    /** 直觉类型轮回按钮（点了就地刷新文字；切到 CUSTOM 会多出一个颜色输入框，所以重建）。 */
    private Cell instinctCell(Supplier<String> getter, Consumer<String> setter) {
        return stateButtonCell(
                () -> Component.literal(instinctTypeDisplay(getter.get()))
                        .append(Component.literal(" ↻").withStyle(style -> style.withColor(SREPanelStyle.MUTED))),
                () -> setter.accept(cycleInstinctTypeStr(getter.get())),
                true);
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 3：生成
    // ══════════════════════════════════════════════════════════════════
    private void buildGenerationTab() {
        int r = 0;
        r = textRow(r, PREFIX + ".label.two_way_opposing", String.join(",", data.twoWayOpposingJobs), LIMIT_TEXT,
                PREFIX + ".hint.role_list", value -> replaceList(data.twoWayOpposingJobs, value));
        r = textRow(r, PREFIX + ".label.opposing", String.join(",", data.opposingJobs), LIMIT_TEXT,
                PREFIX + ".hint.role_list", value -> replaceList(data.opposingJobs, value));
        r = textRow(r, PREFIX + ".label.bind_with", String.join(",", data.bindWithRoles), LIMIT_TEXT,
                PREFIX + ".hint.role_list", value -> replaceList(data.bindWithRoles, value));

        // 相关职业 / 相关修饰符（介绍页展示）
        r = textRow(r, PREFIX + ".label.both_related_roles", String.join(",", data.bothRelatedRoles), LIMIT_TEXT,
                PREFIX + ".hint.role_list", value -> replaceList(data.bothRelatedRoles, value));
        r = textRow(r, PREFIX + ".label.related_roles", String.join(",", data.relatedRoles), LIMIT_TEXT,
                PREFIX + ".hint.role_list", value -> replaceList(data.relatedRoles, value));
        r = textRow(r, PREFIX + ".label.remove_related_roles", String.join(",", data.removeRelatedRoles),
                LIMIT_TEXT, PREFIX + ".hint.role_list", value -> replaceList(data.removeRelatedRoles, value));
        r = textRow(r, PREFIX + ".label.both_related_modifiers", String.join(",", data.bothRelatedModifiers),
                LIMIT_TEXT, PREFIX + ".hint.modifier_list", value -> replaceList(data.bothRelatedModifiers, value));
        r = textRow(r, PREFIX + ".label.related_modifiers", String.join(",", data.relatedModifiers), LIMIT_TEXT,
                PREFIX + ".hint.modifier_list", value -> replaceList(data.relatedModifiers, value));
        r = textRow(r, PREFIX + ".label.remove_related_modifiers", String.join(",", data.removeRelatedModifiers),
                LIMIT_TEXT, PREFIX + ".hint.modifier_list", value -> replaceList(data.removeRelatedModifiers, value));

        // 关联（绑定生成）职业的移除 / 清空（清空在添加之前生效）
        r = textRow(r, PREFIX + ".label.remove_occupation_roles", String.join(",", data.removeOccupationRoles),
                LIMIT_TEXT, PREFIX + ".hint.role_list", value -> replaceList(data.removeOccupationRoles, value));
        r = cluster(r, null,
                toggleCell(PREFIX + ".label.clear_occupation_roles", data.clearOccupationRoles,
                        value -> data.clearOccupationRoles = value, false));

        // 任务刷新黑 / 白名单（按钮选择任务类型）
        r = buildTaskListEditor(r, PREFIX + ".label.unrefreshable_tasks", data.unrefreshableTasks,
                unrefreshableTaskCursor);
        r = buildTaskListEditor(r, PREFIX + ".label.only_refreshable_tasks", data.onlyRefreshableTasks,
                onlyRefreshableTaskCursor);

        r = textRow(r, PREFIX + ".label.map_restrict", String.join(",", data.mapRestrictedTo), LIMIT_TEXT,
                PREFIX + ".hint.map_list", value -> replaceList(data.mapRestrictedTo, value));
        // 组合地图特性条件（setSpecialMapRolesCondition）
        r = textRow(r, PREFIX + ".label.special_map_roles", String.join(",", data.specialMapRoles), LIMIT_TEXT,
                PREFIX + ".hint.special_map_roles", value -> replaceList(data.specialMapRoles, value));
        r = cluster(r, null,
                toggleCell(PREFIX + ".label.special_map_roles_match_all", data.specialMapRolesMatchAll,
                        value -> data.specialMapRolesMatchAll = value, false));
        // 自定义生成条件（setCanSpawnInMap）
        r = textRow(r, PREFIX + ".label.spawn_conditions", String.join(",", data.canSpawnInMapConditions),
                LIMIT_TEXT, PREFIX + ".hint.spawn_conditions",
                value -> replaceList(data.canSpawnInMapConditions, value));
        r = cluster(r, null,
                toggleCell(PREFIX + ".label.spawn_conditions_match_all", data.canSpawnInMapMatchAll,
                        value -> data.canSpawnInMapMatchAll = value, false));
        r = cluster(r, null,
                toggleCell(PREFIX + ".use_rare_chance", data.useRareChance, value -> data.useRareChance = value,
                        true));
        if (data.useRareChance) {
            r = numRow(r, PREFIX + ".label.enable_rare_chance", String.valueOf(data.enableRareChance), 80,
                    PREFIX + ".hint.chance_range_10000", value -> {
                        try {
                            data.enableRareChance = Math.min(10000, Math.max(0, Integer.parseInt(value)));
                        } catch (Exception ignored) {
                        }
                    });
        } else {
            r = numRow(r, PREFIX + ".label.enable_chance", String.valueOf(data.enableChance), 80,
                    PREFIX + ".hint.chance_range_100", value -> {
                        try {
                            data.enableChance = Math.min(100, Math.max(0, Integer.parseInt(value)));
                        } catch (Exception ignored) {
                        }
                    });
        }
        r = numRow(r, PREFIX + ".label.min_players", String.valueOf(data.enableNeededPlayerCount), 80,
                PREFIX + ".hint.minus_one_no_threshold", value -> {
                    try {
                        data.enableNeededPlayerCount = Integer.parseInt(value);
                    } catch (Exception ignored) {
                    }
                });
        r = numRow(r, PREFIX + ".label.max_players", String.valueOf(data.defaultEnableMaxPlayerCount), 80,
                PREFIX + ".hint.minus_one_no_limit", value -> {
                    try {
                        data.defaultEnableMaxPlayerCount = Integer.parseInt(value);
                    } catch (Exception ignored) {
                    }
                });
    }

    /**
     * 任务类型列表编辑器：一行「候选类型（点击循环切换）+ 添加」，之后每个已选类型一行（点击移除）。
     *
     * @param cursor 候选类型下标的持有者
     * @return 下一行行号
     */
    private int buildTaskListEditor(int r, String labelKey, List<String> list, int[] cursor) {
        SREPlayerTaskComponent.Task[] tasks = SREPlayerTaskComponent.Task.values();
        if (tasks.length == 0) {
            return r;
        }
        if (cursor[0] < 0 || cursor[0] >= tasks.length) {
            cursor[0] = 0;
        }
        r = cluster(r, labelKey,
                stateButtonCell(
                        () -> Component.translatable(PREFIX + ".task_candidate", taskName(tasks[cursor[0]])),
                        () -> cursor[0] = (cursor[0] + 1) % tasks.length, false),
                fixedButton(Component.translatable(PREFIX + ".task_add"), 64, () -> {
                    String name = tasks[cursor[0]].name();
                    if (!list.contains(name)) {
                        list.add(name);
                    }
                    requestRebuild();
                }));
        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            r = removeRow(r, Component.translatable(PREFIX + ".task_remove", taskNameOf(list.get(i))), () -> {
                list.remove(index);
                requestRebuild();
            });
        }
        return r;
    }

    /** 任务显示名：优先 {@code task.<小写枚举名>} 翻译键，缺失时回退枚举名。 */
    private static Component taskName(SREPlayerTaskComponent.Task task) {
        String name = task.name().toLowerCase(java.util.Locale.ROOT);
        if ("raed_book".equals(name)) {
            name = "read_book"; // 枚举名是历史拼写，语言文件里的键是 read_book
        }
        String key = "task." + name;
        return net.minecraft.locale.Language.getInstance().has(key)
                ? Component.translatable(key)
                : Component.literal(task.name());
    }

    /** 配置里存的枚举名 → 显示名（解析失败时按原样显示）。 */
    private static Component taskNameOf(String raw) {
        try {
            return taskName(SREPlayerTaskComponent.Task.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Component.literal(raw);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 4：商店
    // ══════════════════════════════════════════════════════════════════
    private static final String[] SHOP_TYPES = { "item", "psycho", "blackout", "monitor_fail", "custom" };

    private void buildShopTab() {
        int r = 0;
        for (int i = 0; i < data.shopEntries.size(); i++) {
            final int index = i;
            ShopEntryData entry = data.shopEntries.get(i);
            boolean isItem = "item".equals(entry.type);
            boolean isCustom = "custom".equals(entry.type);

            List<Cell> cells = new ArrayList<>();
            // 类型按钮：切换后显示的字段会变，所以重建
            cells.add(stateButtonCell(() -> Component.literal("[" + entry.type + "]"), () -> {
                int next = java.util.Arrays.asList(SHOP_TYPES).indexOf(entry.type) + 1;
                if (next < 0) {
                    next = 0;
                }
                entry.type = SHOP_TYPES[next % SHOP_TYPES.length];
            }, true));
            // 价格
            cells.add(fixedBox(String.valueOf(entry.price), LIMIT_NUMBER, 55, hint(PREFIX + ".hint.price"),
                    value -> {
                        try {
                            entry.price = Math.max(0, Integer.parseInt(value));
                        } catch (Exception ignored) {
                        }
                    }));
            // 冷却(仅 item 和 custom)
            if (isItem || isCustom) {
                cells.add(fixedBox(String.valueOf(entry.cooldownSeconds), LIMIT_NUMBER, 50,
                        hint(PREFIX + ".hint.cd_seconds"), value -> {
                            try {
                                entry.cooldownSeconds = Math.max(0, Integer.parseInt(value));
                            } catch (Exception ignored) {
                            }
                        }));
            }
            // 禁止重复(item)
            if (isItem) {
                cells.add(stateButtonCell(
                        () -> Component.translatable(entry.allowDuplicate
                                ? PREFIX + ".shop.dup_allowed"
                                : PREFIX + ".shop.dup_forbidden"),
                        () -> entry.allowDuplicate = !entry.allowDuplicate, false));
            }
            cells.add(fixedButton(Component.literal("X"), 22, () -> {
                data.shopEntries.remove(index);
                requestRebuild();
            }));
            r = cluster(r, null, cells.toArray(new Cell[0]));

            if (isItem) {
                r = cluster(r, PREFIX + ".label.shop_item_id",
                        box(entry.itemId, LIMIT_PATH, 160, hint(PREFIX + ".hint.item_id"),
                                value -> entry.itemId = value));
            }
            if (isCustom) {
                r = cluster(r, PREFIX + ".label.shop_custom_name",
                        box(entry.displayName, LIMIT_NAME, 130, hint(PREFIX + ".hint.shop_name"),
                                value -> entry.displayName = value));
                r = cluster(r, PREFIX + ".label.shop_custom_icon",
                        box(entry.itemId, LIMIT_PATH, 130, hint(PREFIX + ".hint.shop_icon"),
                                value -> entry.itemId = value));
                r = stringList(r, entry.commands, PREFIX + ".label.shop_custom_cmd", 200,
                        PREFIX + ".hint.command");
            }
        }
        r = addRow(r, Component.translatable(PREFIX + ".add_shop_entry"), () -> {
            data.shopEntries.add(new ShopEntryData());
            requestRebuild();
        });
    }

    // ══════════════════════════════════════════════════════════════════
    // 直觉系统工具
    // ══════════════════════════════════════════════════════════════════
    private static final String[] INSTINCT_TYPE_NAMES = {
            "DEFAULT", "NONE", "KILLER_INSTINCT", "OBSERVER_ROLE_COLOR", "TARGET_ROLE_COLOR"
    };

    /** 确保 data.instinctModes 存在至少一个模式，否则从旧字段自动补全 */
    private void ensureInstinctMode() {
        if (data.instinctModes.isEmpty()) {
            InstinctModeData mode = new InstinctModeData();
            if (data.instinctSameColorFrame) {
                mode.seeingOn = "OBSERVER_ROLE_COLOR";
            }
            if (!"*".equals(data.instinctMaxRange)) {
                mode.maxRange = data.instinctMaxRange;
            }
            mode.unlimitedTeammate = data.instinctUnlimitedTeammate;
            data.instinctModes.add(mode);
        }
    }

    /** 将类型字符串循环到下一个预定义类型 */
    private String cycleInstinctTypeStr(String current) {
        String upper = current.toUpperCase().trim();
        if (upper.startsWith("CUSTOM(")) {
            return "DEFAULT";
        }
        for (int i = 0; i < INSTINCT_TYPE_NAMES.length; i++) {
            if (INSTINCT_TYPE_NAMES[i].equals(upper)) {
                return i + 1 < INSTINCT_TYPE_NAMES.length ? INSTINCT_TYPE_NAMES[i + 1] : "CUSTOM(0xFFE06B65)";
            }
        }
        return "DEFAULT";
    }

    /** 获取类型字符串的显示名 */
    private String instinctTypeDisplay(String s) {
        if (s == null || s.isEmpty()) {
            return "DEFAULT";
        }
        String upper = s.toUpperCase().trim();
        if (upper.startsWith("CUSTOM(")) {
            String hex = upper.substring(7, upper.length() - 1).trim();
            try {
                return "CUSTOM(#" + Integer.toHexString(Long.decode(hex).intValue()).toUpperCase().substring(2) + ")";
            } catch (Exception e) {
                return "CUSTOM(???)";
            }
        }
        return upper;
    }

    /** 提取 CUSTOM 颜色字符串中的 hex 部分（不含 0x 前缀） */
    private String extractCustomHex(String s) {
        if (s == null) {
            return "FF0000";
        }
        String upper = s.toUpperCase().trim();
        if (upper.startsWith("CUSTOM(") && upper.endsWith(")")) {
            String hex = upper.substring(7, upper.length() - 1).trim();
            try {
                return Integer.toHexString(Long.decode(hex).intValue()).toUpperCase().substring(2);
            } catch (Exception e) {
                return "FF0000";
            }
        }
        return "FF0000";
    }

    /** 检查类型字符串是否为 CUSTOM */
    private boolean isCustomType(String s) {
        return s != null && s.toUpperCase().trim().startsWith("CUSTOM(");
    }

    /** 从模式中提取第一个 CUSTOM 类型的 hex 颜色字符串 */
    private String findFirstCustomHex(InstinctModeData mode) {
        for (String s : new String[] { mode.seeingOff, mode.seeingOn, mode.beSeenOff, mode.beSeenOn }) {
            if (isCustomType(s)) {
                return extractCustomHex(s);
            }
        }
        return "FF0000";
    }

    // ══════════════════════════════════════════════════════════════════
    // 保存 / 管理
    // ══════════════════════════════════════════════════════════════════

    @Override
    protected void onSave() {
        CustomRoleConfig config = CustomRoleConfig.getInstance();
        if (originalEnglishId != null && !originalEnglishId.isBlank()) {
            config.removeRole(originalEnglishId);
        }
        config.removeRole(data.englishId);
        config.addRole(data);
        config.savePreferWorldPath(minecraft.getSingleplayerServer());
        var server = minecraft.getSingleplayerServer();
        if (server != null) {
            try {
                config.saveToDefaultPath();
                CustomRoleLoader.reloadClient();
            } catch (Exception ignored) {
            }

            server.execute(() -> {
                try {
                    // 走重载命令的路径：除了重建服务端索引，还会给在线客户端重新握手
                    CustomRoleReloadCommand.reload(server);
                } catch (Exception ignored) {
                }
            });
        }
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.translatable(PREFIX + ".saved", data.englishId), false);
        }
        onClose();
    }

    @Override
    protected void onOpenManage() {
        CustomRoleConfig config = CustomRoleConfig.getInstance();
        config.savePreferWorldPath(minecraft.getSingleplayerServer());
        minecraft.setScreen(new CustomRoleManageScreen(new CustomRoleScreen()));
    }
}
