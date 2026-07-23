package io.wifi.starrailexpress.customrole;

import io.wifi.starrailexpress.customrole.CustomRoleData.EffectEntry;
import io.wifi.starrailexpress.customrole.CustomRoleData.InstinctModeData;
import io.wifi.starrailexpress.customrole.CustomRoleData.InitialItemEntry;
import io.wifi.starrailexpress.customrole.CustomRoleData.ShopEntryData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton.AccentSide;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class CustomRoleScreen extends Screen {
    // 面板尺寸 - 自适应屏幕（参考 RoleIntroduceScreen）
    private static final float USABLE_RATIO = 0.85f;
    private static final int MAX_PANEL_WIDTH = 520;
    private static final int MAX_PANEL_HEIGHT = 520;
    private static final int MIN_PANEL_HEIGHT = 320;

    private int panelWidth, panelHeight;
    private int panelLeftX, panelTopY, activeTab = 0;

    // 滚动常量
    private static final int SCROLL_W = 7;
    private static final int SCROLL_MIN_THUMB = 20;

    // 滚动状态
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private boolean isDraggingScroll = false;
    private double dragScrollStartY = 0;
    private int dragScrollStartOffset = 0;

    private static final String[] TAB_NAMES = {"basic","advanced","ability","generation","shop"};
    private static final int FIELD_LEFT = 140, FIELD_W = 200;
    private CustomRoleData data = new CustomRoleData();
    private String originalEnglishId = "";

    private final List<AbstractWidget> tabWidgets0 = new ArrayList<>();
    private final List<AbstractWidget> tabWidgets1 = new ArrayList<>();
    private final List<AbstractWidget> tabWidgets2 = new ArrayList<>();
    private final List<AbstractWidget> tabWidgets3 = new ArrayList<>();
    private final List<AbstractWidget> tabWidgets4 = new ArrayList<>();
    private record LabelEntry(String key, int x, int y) {}
    private final List<LabelEntry> tabLabels0 = new ArrayList<>();
    private final List<LabelEntry> tabLabels1 = new ArrayList<>();
    private final List<LabelEntry> tabLabels2 = new ArrayList<>();
    private final List<LabelEntry> tabLabels3 = new ArrayList<>();
    private final List<LabelEntry> tabLabels4 = new ArrayList<>();

    // 滚动支持：记录每个内容 widget 的基础 Y 坐标
    private final Map<AbstractWidget, Integer> widgetBaseY = new IdentityHashMap<>();

    // 固定 widget 分组（不被滚动影响）
    private final List<AbstractWidget> tabBarButtons = new ArrayList<>();
    private final List<AbstractWidget> bottomButtons = new ArrayList<>();

    // Toggles
    private int moodIndex;

    public CustomRoleScreen() { super(Component.translatable("sre.custom_role.title")); syncToggles(); }
    public CustomRoleScreen(CustomRoleData d) { super(Component.translatable("sre.custom_role.title")); this.data = d; this.originalEnglishId = d.englishId == null ? "" : d.englishId; syncToggles(); }

    private void syncToggles() {
        moodIndex = "FAKE".equalsIgnoreCase(data.moodType) ? 1 : 0;
    }

    // ══════════════════════════════════════════════════════════════════
    // 布局计算
    // ══════════════════════════════════════════════════════════════════
    private void computeLayout() {
        panelWidth = Math.min((int) (width * USABLE_RATIO), MAX_PANEL_WIDTH);
        int rawH = Math.min((int) (height * USABLE_RATIO), MAX_PANEL_HEIGHT);
        panelHeight = Math.max(rawH, MIN_PANEL_HEIGHT);
        panelLeftX = (width - panelWidth) / 2;
        panelTopY = (height - panelHeight) / 2;
    }

    /** 内容区域顶部 Y */
    private int contentTop() { return panelTopY + 34; }

    /** 内容区域底部 Y */
    private int contentBottom() { return panelTopY + panelHeight - 30; }

    /** 内容区域可用高度 */
    private int contentHeight() { return contentBottom() - contentTop(); }

    /** 基础行 Y（scrollOffset=0） */
    private int baseRowY(int i) { return panelTopY + 34 + i * 22; }

    /** 实际行 Y */
    private int rowY(int i) { return baseRowY(i) - scrollOffset; }

    private int fieldX() { return panelLeftX + FIELD_LEFT; }
    private int labelX() { return panelLeftX + 4; }

    // ══════════════════════════════════════════════════════════════════
    // init
    // ══════════════════════════════════════════════════════════════════
    @Override
    protected void init() {
        clearTabs();
        widgetBaseY.clear();
        tabBarButtons.clear();
        bottomButtons.clear();
        tabLabels0.clear(); tabLabels1.clear(); tabLabels2.clear();
        tabLabels3.clear(); tabLabels4.clear();

        computeLayout();
        buildTabBar();

        switch (activeTab) {
            case 0: buildBasicTab(); break;
            case 1: buildAdvancedTab(); break;
            case 2: buildAbilityTab(); break;
            case 3: buildGenerationTab(); break;
            case 4: buildShopTab(); break;
        }
        flushTabWidgets();
        buildBottomButtons();

        computeMaxScroll();
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
        applyScrollOffsets();
    }

    private void clearTabs() {
        tabWidgets0.clear(); tabWidgets1.clear(); tabWidgets2.clear();
        tabWidgets3.clear(); tabWidgets4.clear();
    }

    private void computeMaxScroll() {
        int maxY = contentTop();
        for (AbstractWidget w : getActiveTabWidgets()) {
            Integer baseY = widgetBaseY.get(w);
            if (baseY != null) {
                maxY = Math.max(maxY, baseY + w.getHeight());
            }
        }
        var labels = getActiveLabels();
        for (LabelEntry e : labels) {
            maxY = Math.max(maxY, e.y() + 10);
        }
        maxScroll = Math.max(0, maxY - contentBottom());
    }

    private void applyScrollOffsets() {
        for (AbstractWidget w : getActiveTabWidgets()) {
            Integer baseY = widgetBaseY.get(w);
            if (baseY != null) {
                w.setY(baseY - scrollOffset);
            }
        }
    }

    private List<AbstractWidget> getActiveTabWidgets() {
        return switch (activeTab) {
            case 0 -> tabWidgets0;
            case 1 -> tabWidgets1;
            case 2 -> tabWidgets2;
            case 3 -> tabWidgets3;
            case 4 -> tabWidgets4;
            default -> List.of();
        };
    }

    private List<LabelEntry> getActiveLabels() {
        return switch (activeTab) {
            case 0 -> tabLabels0;
            case 1 -> tabLabels1;
            case 2 -> tabLabels2;
            case 3 -> tabLabels3;
            case 4 -> tabLabels4;
            default -> List.of();
        };
    }

    private void addLabel(List<LabelEntry> l, String key, int r) {
        l.add(new LabelEntry(key, labelX(), baseRowY(r)));
    }

    private EditBox makeLabeledBox(List<AbstractWidget> wl, List<LabelEntry> ll, int r, int w, String key, String val, java.util.function.Consumer<String> cb) {
        addLabel(ll, key, r);
        EditBox b = makeBox(fieldX(), rowY(r), w, 18, val, cb);
        recordWidgetBase(b, baseRowY(r));
        wl.add(b);
        return b;
    }
    private EditBox makeLabeledHintBox(List<AbstractWidget> wl, List<LabelEntry> ll, int r, int w, String key, String val, String hint, java.util.function.Consumer<String> cb) {
        EditBox b = makeLabeledBox(wl, ll, r, w, key, val, cb);
        b.setHint(Component.literal(hint));
        return b;
    }

    private void recordWidgetBase(AbstractWidget w, int baseY) {
        widgetBaseY.put(w, baseY);
    }

    private AbstractWidget makeModernButton(int x, int baseY, int w, int h, Component text, Runnable onClick, AccentSide accent) {
        var btn = ModernButton.builder(text, b -> { onClick.run(); })
                .bounds(x, baseY, w, h).accentBar(accent).build();
        recordWidgetBase(btn, baseY);
        return btn;
    }

    private void buildTabBar() {
        int tw = 68, th = 20, tg = 4;
        int sx = panelLeftX + (panelWidth - (tw * 5 + tg * 4)) / 2;
        tabBarButtons.clear();
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            var b = ModernButton.builder(Component.translatable("sre.custom_role.tab." + TAB_NAMES[i]),
                btn -> { activeTab = idx; init(minecraft, width, height); })
                .bounds(sx + i * (tw + tg), panelTopY + 8, tw, th);
            if (activeTab == i) b.accentBar(AccentSide.BOTTOM); else b.accentBar();
            var btn = b.build();
            addRenderableWidget(btn);
            tabBarButtons.add(btn);
        }
    }

    // ---- TAB 0: Basic ----
    private void buildBasicTab() {
        int r = 0;
        makeLabeledHintBox(tabWidgets0, tabLabels0, r++, FIELD_W, "sre.custom_role.label.english_id", data.englishId, "my_custom_role", v -> data.englishId = v.toLowerCase()).setMaxLength(64);
        makeLabeledHintBox(tabWidgets0, tabLabels0, r++, FIELD_W, "sre.custom_role.label.display_name", data.displayName, "职业显示的名字", v -> data.displayName = v);
        makeLabeledHintBox(tabWidgets0, tabLabels0, r++, FIELD_W, "sre.custom_role.label.goals", data.goals, "职业的胜利目标", v -> data.goals = v);
        makeLabeledHintBox(tabWidgets0, tabLabels0, r++, FIELD_W, "sre.custom_role.label.description", data.description, "职业描述", v -> data.description = v);

        StringBuilder efSb = new StringBuilder();
        for (EffectEntry e : data.initialEffects) {
            if (!efSb.isEmpty()) efSb.append(";");
            efSb.append(e.effectId).append(",").append(e.amplifier);
        }
        addLabel(tabLabels0, "sre.custom_role.label.effects", r);
        EditBox effectsBox = makeBox(fieldX(), rowY(r), FIELD_W + 40, 18, efSb.toString(), v -> {
            data.initialEffects.clear();
            if (v == null || v.isBlank()) return;
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("([a-z0-9_\\-.:]+)[,:](\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(v);
            while (m.find()) {
                String id = m.group(1).trim();
                int lvl = 0;
                try { lvl = Integer.parseInt(m.group(2)); } catch (Exception ignored) {}
                data.initialEffects.add(new EffectEntry(id, lvl));
            }
        });
        effectsBox.setHint(Component.literal("minecraft:conduit_power,1; minecraft:speed,2"));
        recordWidgetBase(effectsBox, baseRowY(r));
        tabWidgets0.add(effectsBox);
        r++;

        addLabel(tabLabels0, "sre.custom_role.label.color_rgb", r++);
        int colorRow = r - 1;
        EditBox rBox = makeBox(fieldX(), rowY(colorRow), 40, 18, String.valueOf(data.colorR), v -> { try { data.colorR=clamp(v,255); } catch(Exception e){} });
        EditBox gBox = makeBox(fieldX()+48, rowY(colorRow), 40, 18, String.valueOf(data.colorG), v -> { try { data.colorG=clamp(v,255); } catch(Exception e){} });
        EditBox bBox = makeBox(fieldX()+96, rowY(colorRow), 40, 18, String.valueOf(data.colorB), v -> { try { data.colorB=clamp(v,255); } catch(Exception e){} });
        recordWidgetBase(rBox, baseRowY(colorRow));
        recordWidgetBase(gBox, baseRowY(colorRow));
        recordWidgetBase(bBox, baseRowY(colorRow));
        tabWidgets0.addAll(List.of(rBox, gBox, bBox));

        addBoolBtn(tabWidgets0, r++, "sre.custom_role.is_innocent", data.isInnocent, v -> data.isInnocent = v, true);
        addBoolBtn(tabWidgets0, r++, "sre.custom_role.can_use_killer", data.canUseKiller, v -> data.canUseKiller = v, true);

        Component ml = Component.translatable("sre.custom_role.mood." + (moodIndex == 0 ? "real" : "fake"));
        int moodRow = r++;
        addLabel(tabLabels0, "sre.custom_role.label.mood", moodRow);
        var moodBtn = makeModernButton(fieldX(), baseRowY(moodRow), FIELD_W, 18,
                Component.translatable("sre.custom_role.mood.current").append(": ").append(ml),
                () -> { moodIndex = (moodIndex + 1) % 2; data.moodType = moodIndex == 0 ? "REAL" : "FAKE"; init(minecraft, width, height); },
                AccentSide.LEFT);
        tabWidgets0.add(moodBtn);

        // 心情颜色覆盖（R/G/B，任一 <0 视为不覆盖）
        makeLabeledHintBox(tabWidgets0, tabLabels0, r++, 60, "sre.custom_role.label.mood_color_r", String.valueOf(data.moodColorR), "-1=默认",
            v -> { try { data.moodColorR = Integer.parseInt(v); } catch(Exception ignored){} });
        makeLabeledHintBox(tabWidgets0, tabLabels0, r++, 60, "sre.custom_role.label.mood_color_g", String.valueOf(data.moodColorG), "-1=默认",
            v -> { try { data.moodColorG = Integer.parseInt(v); } catch(Exception ignored){} });
        makeLabeledHintBox(tabWidgets0, tabLabels0, r++, 60, "sre.custom_role.label.mood_color_b", String.valueOf(data.moodColorB), "-1=默认",
            v -> { try { data.moodColorB = Integer.parseInt(v); } catch(Exception ignored){} });

        makeLabeledHintBox(tabWidgets0, tabLabels0, r++, 80, "sre.custom_role.label.sprint_mult", String.valueOf(data.sprintMultiplier), "默认1",
            v -> { try { data.sprintMultiplier = Double.parseDouble(v); } catch(Exception ignored){} });
        addBoolBtn(tabWidgets0, r++, "sre.custom_role.infinite_sprint", data.infiniteSprint, v -> data.infiniteSprint = v, true);
        addBoolBtn(tabWidgets0, r++, "sre.custom_role.can_see_time", data.canSeeTime, v -> data.canSeeTime = v, true);
    }

    // ---- TAB 1: Advanced ----
    private void buildAdvancedTab() {
        int r = 0;
        addBoolBtn(tabWidgets1, r++, "sre.custom_role.can_see_coin", data.canSeeCoin, v -> data.canSeeCoin = v, true);
        addTriBtn(tabWidgets1, r, "sre.custom_role.able_pickup_revolver", data.ableToPickUpRevolver, v -> data.ableToPickUpRevolver = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.set_neutrals", data.setNeutrals, v -> data.setNeutrals = v, true);
        addTriBtn(tabWidgets1, r, "sre.custom_role.set_neutral_for_killer", data.setNeutralForKiller, v -> data.setNeutralForKiller = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.set_vigilante_team", data.setVigilanteTeam, v -> data.setVigilanteTeam = v, true);
        addTriBtn(tabWidgets1, r++, "sre.custom_role.can_see_teammate_killer", data.canSeeTeammateKiller, v -> data.canSeeTeammateKiller = v, true);
        makeLabeledHintBox(tabWidgets1, tabLabels1, r++, 80, "sre.custom_role.label.occupied_role_count", String.valueOf(data.occupiedRoleCount), "默认1",
            v -> { try { data.occupiedRoleCount = Integer.parseInt(v); } catch(Exception ignored){} });
        makeLabeledHintBox(tabWidgets1, tabLabels1, r++, 80, "sre.custom_role.label.max_count", String.valueOf(data.maxCount), "默认1",
            v -> { try { data.maxCount = Integer.parseInt(v); } catch(Exception ignored){} });
        addTriBtn(tabWidgets1, r++, "sre.custom_role.can_auto_add_money", data.canAutoAddMoney, v -> data.canAutoAddMoney = v, true);
        addBoolBtn(tabWidgets1, r, "sre.custom_role.can_be_randomed", data.canBeRandomedByOtherRoles, v -> data.canBeRandomedByOtherRoles = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.can_ignore_blackout", data.canIgnoreBlackout, v -> data.canIgnoreBlackout = v, true);
        addTriBtn(tabWidgets1, r, "sre.custom_role.can_see_body_items", data.canSeeBodyItems, v -> data.canSeeBodyItems = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.can_see_body_role_info", data.canSeeBodyRoleInfo, v -> data.canSeeBodyRoleInfo = v, true);
        addTriBtn(tabWidgets1, r, "sre.custom_role.can_see_body_death_reason", data.canSeeBodyDeathReason, v -> data.canSeeBodyDeathReason = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.can_see_body_killer", data.canSeeBodyKiller, v -> data.canSeeBodyKiller = v, true);

        // === 职业通用属性补全 ===
        addTriBtn(tabWidgets1, r, "sre.custom_role.neutral_for_innocent", data.neutralForInnocent, v -> data.neutralForInnocent = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.mafia_team", data.mafiaTeam, v -> data.mafiaTeam = v, true);
        addTriBtn(tabWidgets1, r, "sre.custom_role.can_see_body_name", data.canSeeBodyName, v -> data.canSeeBodyName = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.can_use_skill_while_spectator", data.canUseSkillWhileSpectator, v -> data.canUseSkillWhileSpectator = v, true);
        addTriBtn(tabWidgets1, r, "sre.custom_role.can_be_poisoned", data.canBePoisoned, v -> data.canBePoisoned = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.hidden_for_role_rotation", data.hiddenForRoleRotation, v -> data.hiddenForRoleRotation = v, true);
        addTriBtn(tabWidgets1, r, "sre.custom_role.special_vigilante", data.specialVigilante, v -> data.specialVigilante = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.refreshable_special_vigilante", data.refreshableSpecialVigilante, v -> data.refreshableSpecialVigilante = v, true);
        makeLabeledHintBox(tabWidgets1, tabLabels1, r++, 80, "sre.custom_role.label.refresh_special_vigilante_chance", String.valueOf(data.refreshableSpecialVigilanteChance), "0-10000",
            v -> { try { data.refreshableSpecialVigilanteChance = Math.min(10000, Math.max(0, Integer.parseInt(v))); } catch(Exception ignored){} });
        addTriBtn(tabWidgets1, r, "sre.custom_role.can_jump_manhole", data.canJumpManhole, v -> data.canJumpManhole = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.can_across_fog", data.canAcrossFog, v -> data.canAcrossFog = v, true);
        addTriBtn(tabWidgets1, r++, "sre.custom_role.can_use_sabotage", data.canUseSabotage, v -> data.canUseSabotage = v, true);

        // === 免疫 / 经济 / 战斗 / 杀手同伙 ===
        addTriBtn(tabWidgets1, r, "sre.custom_role.fall_damage_immune", data.fallDamageImmune, v -> data.fallDamageImmune = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.darkness_immune", data.darknessImmune, v -> data.darknessImmune = v, true);
        addTriBtn(tabWidgets1, r, "sre.custom_role.environmental_immune", data.environmentalImmune, v -> data.environmentalImmune = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.no_coin_system", data.noCoinSystem, v -> data.noCoinSystem = v, true);
        makeLabeledHintBox(tabWidgets1, tabLabels1, r++, 80, "sre.custom_role.label.initial_coin_count", String.valueOf(data.initialCoinCount), "-1=不改",
            v -> { try { data.initialCoinCount = Integer.parseInt(v); } catch(Exception ignored){} });
        addTriBtn(tabWidgets1, r, "sre.custom_role.cannot_earn_coin_from_kills", data.cannotEarnCoinFromKills, v -> data.cannotEarnCoinFromKills = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.can_kill_with_bow_crossbow", data.canKillWithBowAndCrossbow, v -> data.canKillWithBowAndCrossbow = v, true);
        addTriBtn(tabWidgets1, r, "sre.custom_role.can_kill_with_trident", data.canKillWithTrident, v -> data.canKillWithTrident = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.cannot_knife_left_click", data.cannotKnifeLeftClick, v -> data.cannotKnifeLeftClick = v, true);
        addTriBtn(tabWidgets1, r, "sre.custom_role.killer_teammate_visibility_enabled", data.killerTeammateVisibilityEnabled, v -> data.killerTeammateVisibilityEnabled = v, true);
        addTriBtnX(tabWidgets1, r++, "sre.custom_role.can_be_seen_as_killer_teammate", data.canBeSeenAsKillerTeammate, v -> data.canBeSeenAsKillerTeammate = v, true);

        // === 自定义独立胜利 (仅中立 && !杀手方中立时可用) ===
        boolean isNeutral = data.setNeutrals != null && data.setNeutrals;
        boolean isNotKillerNeutral = data.setNeutralForKiller != null && !data.setNeutralForKiller;
        if (isNeutral && isNotKillerNeutral) {
            addLabel(tabLabels1, "sre.custom_role.custom_win_section", r++);
            addBoolBtnX(tabWidgets1, r++, "sre.custom_role.enable_custom_win", data.enableCustomWin, v -> data.enableCustomWin = v, true);
            if (data.enableCustomWin) {
                makeLabeledBox(tabWidgets1, tabLabels1, r++, FIELD_W, "sre.custom_role.custom_win_title", data.customWinTitle, v -> data.customWinTitle = v);
                makeLabeledBox(tabWidgets1, tabLabels1, r++, FIELD_W, "sre.custom_role.custom_win_subtitle", data.customWinSubtitle, v -> data.customWinSubtitle = v);
                addBoolBtn(tabWidgets1, r, "sre.custom_role.custom_win_survive", data.customWinSurviveToLast, v -> { data.customWinSurviveToLast = v; if (v) data.customWinLastAlive = false; }, true);
                addBoolBtnX(tabWidgets1, r++, "sre.custom_role.custom_win_last_alive", data.customWinLastAlive, v -> { data.customWinLastAlive = v; if (v) data.customWinSurviveToLast = false; }, true);
                makeLabeledHintBox(tabWidgets1, tabLabels1, r++, FIELD_W, "sre.custom_role.custom_win_with_roles", String.join(";", data.customWinLastWithRoles), "职业id;职业id...", v -> {
                    data.customWinLastWithRoles.clear();
                    for (String s : v.split(";")) { String t = s.trim(); if (!t.isEmpty()) data.customWinLastWithRoles.add(t); }
                });
                makeLabeledHintBox(tabWidgets1, tabLabels1, r++, FIELD_W, "sre.custom_role.custom_win_tag_sleep", data.customWinTagSleep, "customwin标签名", v -> data.customWinTagSleep = v.trim());
                makeLabeledHintBox(tabWidgets1, tabLabels1, r++, FIELD_W, "sre.custom_role.custom_win_held_item", data.customWinHeldItem, "minecraft:diamond", v -> data.customWinHeldItem = v.trim());
            }
        }

        // 特殊地图类型限制（枚举按钮，默认 ALL）
        int smRow = r++;
        addLabel(tabLabels1, "sre.custom_role.special_map_role", smRow);
        var smBtn = makeModernButton(fieldX(), baseRowY(smRow), FIELD_W, 18,
            Component.translatable("sre.custom_role.special_map_role.current").append(": ").append(Component.literal(data.specialMapRole)),
            () -> {
                String[] vals = {"ALL","QIYUCUN","BIGMAP","UNDERWATER","FLY","TRAP","CAN_JUMP","MEETING","MEETING_VOTE","MINIGAME_QUEST","MAP_STATUS_BAR"};
                int idx = java.util.Arrays.asList(vals).indexOf(data.specialMapRole);
                if (idx < 0) idx = 0;
                idx = (idx + 1) % vals.length;
                data.specialMapRole = vals[idx];
                init(minecraft, width, height);
            },
            AccentSide.LEFT);
        tabWidgets1.add(smBtn);
    }

    // ---- TAB 2: Ability ----
    private void buildAbilityTab() {
        int r = 0;
        if (data.initialItems.isEmpty()) data.initialItems.add(new InitialItemEntry());
        for (int i = 0; i < data.initialItems.size(); i++) {
            final int idx = i; InitialItemEntry en = data.initialItems.get(i); int y = rowY(r);
            addLabel(tabLabels2, "sre.custom_role.label.initial_items", r);
            EditBox ib = makeBox(fieldX(), y, 130, 18, en.itemId, v -> en.itemId = v); ib.setHint(Component.literal("物品id"));
            EditBox cb = makeBox(fieldX() + 138, y, 50, 18, String.valueOf(en.count), v -> { try { en.count = Integer.parseInt(v); } catch(Exception ignored){} }); cb.setHint(Component.literal("数量"));
            recordWidgetBase(ib, baseRowY(r));
            recordWidgetBase(cb, baseRowY(r));
            tabWidgets2.addAll(List.of(ib, cb));
            var plusBtn = makeModernButton(fieldX()+196, baseRowY(r), 20, 18, Component.literal("+"),
                    () -> { data.initialItems.add(new InitialItemEntry()); init(minecraft, width, height); },
                    AccentSide.TOP);
            tabWidgets2.add(plusBtn);
            if (data.initialItems.size() > 1) {
                var minusBtn = makeModernButton(fieldX()+220, baseRowY(r), 20, 18, Component.literal("-"),
                        () -> { data.initialItems.remove(idx); init(minecraft, width, height); },
                        AccentSide.TOP);
                tabWidgets2.add(minusBtn);
            }
            r++;
        }
        r++; // spacer

        // ═══ 任务奖励（完成 N 个任务给物品） ═══
        addLabel(tabLabels2, "sre.custom_role.task_reward_section", r++);
        makeLabeledHintBox(tabWidgets2, tabLabels2, r++, 60, "sre.custom_role.label.task_reward_count", String.valueOf(data.taskRewardCount), "0=关闭",
            v -> { try { data.taskRewardCount = Integer.parseInt(v); } catch(Exception ignored){} });
        makeLabeledHintBox(tabWidgets2, tabLabels2, r++, 60, "sre.custom_role.label.task_reward_max_triggers", String.valueOf(data.taskRewardMaxTriggers), "默认1",
            v -> { try { data.taskRewardMaxTriggers = Integer.parseInt(v); } catch(Exception ignored){} });
        addBoolBtn(tabWidgets2, r++, "sre.custom_role.task_reward_unlimited", data.taskRewardUnlimited, v -> data.taskRewardUnlimited = v, true);
        addBoolBtn(tabWidgets2, r++, "sre.custom_role.task_reward_silent", data.taskRewardSilent, v -> data.taskRewardSilent = v, true);
        makeLabeledBox(tabWidgets2, tabLabels2, r++, FIELD_W, "sre.custom_role.task_reward_message", data.taskRewardMessage, v -> data.taskRewardMessage = v);
        if (data.taskRewardItems.isEmpty()) data.taskRewardItems.add(new InitialItemEntry());
        for (int i = 0; i < data.taskRewardItems.size(); i++) {
            final int idx = i; InitialItemEntry en = data.taskRewardItems.get(i); int y = rowY(r);
            addLabel(tabLabels2, "sre.custom_role.label.task_reward_items", r);
            EditBox ib = makeBox(fieldX(), y, 130, 18, en.itemId, v -> en.itemId = v); ib.setHint(Component.literal("物品id"));
            EditBox cb = makeBox(fieldX() + 138, y, 50, 18, String.valueOf(en.count), v -> { try { en.count = Integer.parseInt(v); } catch(Exception ignored){} }); cb.setHint(Component.literal("数量"));
            recordWidgetBase(ib, baseRowY(r));
            recordWidgetBase(cb, baseRowY(r));
            tabWidgets2.addAll(List.of(ib, cb));
            var plusBtn = makeModernButton(fieldX()+196, baseRowY(r), 20, 18, Component.literal("+"),
                    () -> { data.taskRewardItems.add(new InitialItemEntry()); init(minecraft, width, height); },
                    AccentSide.TOP);
            tabWidgets2.add(plusBtn);
            if (data.taskRewardItems.size() > 1) {
                var minusBtn = makeModernButton(fieldX()+220, baseRowY(r), 20, 18, Component.literal("-"),
                        () -> { data.taskRewardItems.remove(idx); init(minecraft, width, height); },
                        AccentSide.TOP);
                tabWidgets2.add(minusBtn);
            }
            r++;
        }
        r++; // spacer

        // ═══ 直觉系统 ═══
        addLabel(tabLabels2, "sre.custom_role.instinct_section", r++);

        // 启用直觉 + 夜视
        addBoolBtn(tabWidgets2, r, "sre.custom_role.can_use_instinct", data.canUseInstinct,
                v -> { data.canUseInstinct = v; if (!v) data.instinctModes.clear(); init(minecraft, width, height); }, true);
        addTriBtnX(tabWidgets2, r++, "sre.custom_role.instinct_night_vision", data.instinctNightVision,
                v -> data.instinctNightVision = v, true);

        if (data.canUseInstinct) {
            ensureInstinctMode();
            InstinctModeData mode = data.instinctModes.get(0);

            // 看别人
            addLabel(tabLabels2, "sre.custom_role.instinct_seeing", r);
            var seeingOffBtn = makeInstinctTypeBtn(fieldX(), baseRowY(r), 155, 18,
                    () -> mode.seeingOff, v -> mode.seeingOff = v);
            var seeingOnBtn  = makeInstinctTypeBtn(fieldX() + 163, baseRowY(r), 155, 18,
                    () -> mode.seeingOn, v -> mode.seeingOn = v);
            tabWidgets2.add(seeingOffBtn);
            tabWidgets2.add(seeingOnBtn);
            r++;

            // 被看
            addLabel(tabLabels2, "sre.custom_role.instinct_be_seen", r);
            var beSeenOffBtn = makeInstinctTypeBtn(fieldX(), baseRowY(r), 155, 18,
                    () -> mode.beSeenOff, v -> mode.beSeenOff = v);
            var beSeenOnBtn  = makeInstinctTypeBtn(fieldX() + 163, baseRowY(r), 155, 18,
                    () -> mode.beSeenOn, v -> mode.beSeenOn = v);
            tabWidgets2.add(beSeenOffBtn);
            tabWidgets2.add(beSeenOnBtn);
            r++;

            // 自定义颜色输入（当任一字段为 CUSTOM 时显示）
            boolean hasCustom = isCustomType(mode.seeingOff) || isCustomType(mode.seeingOn)
                    || isCustomType(mode.beSeenOff) || isCustomType(mode.beSeenOn);
            if (hasCustom) {
                // 收集所有 CUSTOM 字段，使用同一个颜色输入
                final String sharedHex = findFirstCustomHex(mode);
                addLabel(tabLabels2, "sre.custom_role.instinct_custom_color", r);
                EditBox colorBox = makeBox(fieldX(), rowY(r), 80, 18, sharedHex, v -> {
                    String hex = v.trim().replaceAll("[^0-9a-fA-F]", "");
                    if (hex.isEmpty()) hex = "FF0000";
                    String newVal = "CUSTOM(0x" + hex + ")";
                    if (isCustomType(mode.seeingOff)) mode.seeingOff = newVal;
                    if (isCustomType(mode.seeingOn)) mode.seeingOn = newVal;
                    if (isCustomType(mode.beSeenOff)) mode.beSeenOff = newVal;
                    if (isCustomType(mode.beSeenOn)) mode.beSeenOn = newVal;
                });
                colorBox.setHint(Component.literal("RRGGBB hex"));
                recordWidgetBase(colorBox, baseRowY(r));
                tabWidgets2.add(colorBox);
                r++;
            }

            // 最大距离
            makeLabeledHintBox(tabWidgets2, tabLabels2, r++, 80, "sre.custom_role.label.instinct_range",
                    mode.maxRange, "* = 不限", v -> mode.maxRange = v);
            addBoolBtn(tabWidgets2, r++, "sre.custom_role.instinct_unlimited_teammate", mode.unlimitedTeammate,
                    v -> mode.unlimitedTeammate = v, false);
        }

        r++;
        addBoolBtn(tabWidgets2, r++, "sre.custom_role.enable_ability", data.enableAbility, v -> data.enableAbility = v, true);
        if (data.enableAbility) {
            if (data.abilitySkillCommands.isEmpty()) data.abilitySkillCommands.add("");
            for (int i = 0; i < data.abilitySkillCommands.size(); i++) {
                final int idx = i; int y = rowY(r);
                addLabel(tabLabels2, "sre.custom_role.label.ability_commands", r);
                EditBox cmdBox = makeBox(fieldX(), y, 250, 18, data.abilitySkillCommands.get(i), v -> data.abilitySkillCommands.set(idx, v));
                cmdBox.setHint(Component.literal("不需/ 例: say <player>"));
                recordWidgetBase(cmdBox, baseRowY(r));
                tabWidgets2.add(cmdBox);
                var plusBtn2 = makeModernButton(fieldX()+258, baseRowY(r), 20, 18, Component.literal("+"),
                        () -> { data.abilitySkillCommands.add(""); init(minecraft, width, height); },
                        AccentSide.TOP);
                tabWidgets2.add(plusBtn2);
                if (data.abilitySkillCommands.size() > 1) {
                    var minusBtn2 = makeModernButton(fieldX()+282, baseRowY(r), 20, 18, Component.literal("-"),
                            () -> { data.abilitySkillCommands.remove(idx); init(minecraft, width, height); },
                            AccentSide.TOP);
                    tabWidgets2.add(minusBtn2);
                }
                r++;
            }
            makeLabeledHintBox(tabWidgets2, tabLabels2, r++, 80, "sre.custom_role.label.ability_cooldown", String.valueOf(data.abilityCooldownSeconds), "冷却秒数",
                v -> { try { data.abilityCooldownSeconds = Integer.parseInt(v); } catch(Exception ignored){} });
            makeLabeledHintBox(tabWidgets2, tabLabels2, r++, 80, "sre.custom_role.label.ability_initial_cooldown", String.valueOf(data.abilityInitialCooldownSeconds), "初始冷却秒数",
                v -> { try { data.abilityInitialCooldownSeconds = Integer.parseInt(v); } catch(Exception ignored){} });

            // 延迟执行指令
            makeLabeledHintBox(tabWidgets2, tabLabels2, r++, 80, "sre.custom_role.label.ability_delay_seconds", String.valueOf(data.abilityDelaySeconds), "延迟秒数",
                v -> { try { data.abilityDelaySeconds = Integer.parseInt(v); } catch(Exception ignored){} });
            if (data.abilityDelayedCommands.isEmpty()) data.abilityDelayedCommands.add("");
            for (int i = 0; i < data.abilityDelayedCommands.size(); i++) {
                final int idx = i; int y = rowY(r);
                addLabel(tabLabels2, "sre.custom_role.label.ability_delayed_commands", r);
                EditBox dcBox = makeBox(fieldX(), y, 250, 18, data.abilityDelayedCommands.get(i), v -> data.abilityDelayedCommands.set(idx, v));
                dcBox.setHint(Component.literal("不需要 /"));
                recordWidgetBase(dcBox, baseRowY(r));
                tabWidgets2.add(dcBox);
                var dplus = makeModernButton(fieldX()+258, baseRowY(r), 20, 18, Component.literal("+"),
                        () -> { data.abilityDelayedCommands.add(""); init(minecraft, width, height); },
                        AccentSide.TOP);
                tabWidgets2.add(dplus);
                if (data.abilityDelayedCommands.size() > 1) {
                    var dminus = makeModernButton(fieldX()+282, baseRowY(r), 20, 18, Component.literal("-"),
                            () -> { data.abilityDelayedCommands.remove(idx); init(minecraft, width, height); },
                            AccentSide.TOP);
                    tabWidgets2.add(dminus);
                }
                r++;
            }

            // 游戏结束执行指令
            if (data.gameEndCommands.isEmpty()) data.gameEndCommands.add("");
            for (int i = 0; i < data.gameEndCommands.size(); i++) {
                final int idx = i; int y = rowY(r);
                addLabel(tabLabels2, "sre.custom_role.label.game_end_commands", r);
                EditBox geBox = makeBox(fieldX(), y, 250, 18, data.gameEndCommands.get(i), v -> data.gameEndCommands.set(idx, v));
                geBox.setHint(Component.literal("不需要 /"));
                recordWidgetBase(geBox, baseRowY(r));
                tabWidgets2.add(geBox);
                var gePlus = makeModernButton(fieldX()+258, baseRowY(r), 20, 18, Component.literal("+"),
                        () -> { data.gameEndCommands.add(""); init(minecraft, width, height); },
                        AccentSide.TOP);
                tabWidgets2.add(gePlus);
                if (data.gameEndCommands.size() > 1) {
                    var geMinus = makeModernButton(fieldX()+282, baseRowY(r), 20, 18, Component.literal("-"),
                            () -> { data.gameEndCommands.remove(idx); init(minecraft, width, height); },
                            AccentSide.TOP);
                    tabWidgets2.add(geMinus);
                }
                r++;
            }
        }
    }

    // ---- TAB 3: Generation ----
    private void buildGenerationTab() {
        int r = 0;
        makeLabeledHintBox(tabWidgets3, tabLabels3, r++, FIELD_W, "sre.custom_role.label.two_way_opposing", String.join(",", data.twoWayOpposingJobs), "roleId1,roleId2",
            v -> { data.twoWayOpposingJobs.clear(); for (String s : v.split(",")) { String t = s.trim(); if (!t.isEmpty()) data.twoWayOpposingJobs.add(t); } });
        makeLabeledHintBox(tabWidgets3, tabLabels3, r++, FIELD_W, "sre.custom_role.label.opposing", String.join(",", data.opposingJobs), "roleId1,roleId2",
            v -> { data.opposingJobs.clear(); for (String s : v.split(",")) { String t = s.trim(); if (!t.isEmpty()) data.opposingJobs.add(t); } });
        makeLabeledHintBox(tabWidgets3, tabLabels3, r++, FIELD_W, "sre.custom_role.label.bind_with", String.join(",", data.bindWithRoles), "roleId1,roleId2",
            v -> { data.bindWithRoles.clear(); for (String s : v.split(",")) { String t = s.trim(); if (!t.isEmpty()) data.bindWithRoles.add(t); } });
        makeLabeledHintBox(tabWidgets3, tabLabels3, r++, FIELD_W, "sre.custom_role.label.map_restrict", String.join(",", data.mapRestrictedTo), "mapId1,mapId2",
            v -> { data.mapRestrictedTo.clear(); for (String s : v.split(",")) { String t = s.trim(); if (!t.isEmpty()) data.mapRestrictedTo.add(t); } });
        addBoolBtn(tabWidgets3, r++, "sre.custom_role.use_rare_chance", data.useRareChance, v -> data.useRareChance = v, true);
        if (data.useRareChance)
            makeLabeledHintBox(tabWidgets3, tabLabels3, r++, 80, "sre.custom_role.label.enable_rare_chance", String.valueOf(data.enableRareChance), "0-10000",
                v -> { try { data.enableRareChance = Math.min(10000, Math.max(0, Integer.parseInt(v))); } catch(Exception ignored){} });
        else
            makeLabeledHintBox(tabWidgets3, tabLabels3, r++, 80, "sre.custom_role.label.enable_chance", String.valueOf(data.enableChance), "0-100",
                v -> { try { data.enableChance = Math.min(100, Math.max(0, Integer.parseInt(v))); } catch(Exception ignored){} });
        makeLabeledHintBox(tabWidgets3, tabLabels3, r++, 80, "sre.custom_role.label.min_players", String.valueOf(data.enableNeededPlayerCount), "-1=无门槛",
            v -> { try { data.enableNeededPlayerCount = Integer.parseInt(v); } catch(Exception ignored){} });
        makeLabeledHintBox(tabWidgets3, tabLabels3, r++, 80, "sre.custom_role.label.max_players", String.valueOf(data.defaultEnableMaxPlayerCount), "-1=无上限",
            v -> { try { data.defaultEnableMaxPlayerCount = Integer.parseInt(v); } catch(Exception ignored){} });
    }

    // ---- TAB 4: Shop ----
    private void buildShopTab() {
        int lx = fieldX(), bh = 18, r = 0;
        String[] types = {"item", "psycho", "blackout", "monitor_fail", "custom"};
        for (int i = 0; i < data.shopEntries.size(); i++) {
            final int idx = i; ShopEntryData en = data.shopEntries.get(i);
            // 类型按钮
            var typeBtn = makeModernButton(lx, baseRowY(r), 75, bh, Component.literal("[" + en.type + "]"),
                    () -> {
                        int next = (java.util.Arrays.asList(types).indexOf(en.type) + 1) % types.length;
                        if (next < 0) next = 0; en.type = types[next]; init(minecraft, width, height);
                    },
                    AccentSide.LEFT);
            tabWidgets4.add(typeBtn);
            // 价格
            EditBox pb = makeBox(lx + 83, rowY(r), 55, bh, String.valueOf(en.price), v -> { try { en.price = Math.max(0, Integer.parseInt(v)); } catch(Exception ignored){} });
            pb.setHint(Component.literal("价格"));
            recordWidgetBase(pb, baseRowY(r));
            tabWidgets4.add(pb);
            // 冷却(仅 item 和 custom)
            if ("item".equals(en.type) || "custom".equals(en.type)) {
                EditBox cd = makeBox(lx + 146, rowY(r), 45, bh, String.valueOf(en.cooldownSeconds), v -> { try { en.cooldownSeconds = Math.max(0, Integer.parseInt(v)); } catch(Exception ignored){} });
                cd.setHint(Component.literal("CD秒"));
                recordWidgetBase(cd, baseRowY(r));
                tabWidgets4.add(cd);
            }
            // 禁止重复(item)
            if ("item".equals(en.type)) {
                boolean nd = !en.allowDuplicate;
                var dupBtn = makeModernButton(lx + 199, baseRowY(r), 55, bh,
                        Component.literal(nd ? "禁重复" : "允重复"),
                        () -> { en.allowDuplicate = !en.allowDuplicate; init(minecraft, width, height); },
                        nd ? AccentSide.RIGHT : AccentSide.LEFT);
                tabWidgets4.add(dupBtn);
                var delBtn = makeModernButton(lx + 260, baseRowY(r), 20, bh, Component.literal("X"),
                        () -> { data.shopEntries.remove(idx); init(minecraft, width, height); },
                        AccentSide.RIGHT);
                tabWidgets4.add(delBtn);
            } else {
                var delBtn = makeModernButton(lx + 199, baseRowY(r), 20, bh, Component.literal("X"),
                        () -> { data.shopEntries.remove(idx); init(minecraft, width, height); },
                        AccentSide.RIGHT);
                tabWidgets4.add(delBtn);
            }
            r++;
            if ("item".equals(en.type)) {
                addLabel(tabLabels4, "sre.custom_role.label.shop_item_id", r);
                EditBox ib2 = makeBox(lx, rowY(r), 160, bh, en.itemId, v -> en.itemId = v);
                ib2.setHint(Component.literal("物品id"));
                recordWidgetBase(ib2, baseRowY(r));
                tabWidgets4.add(ib2);
                r++;
            }
            if ("custom".equals(en.type)) {
                addLabel(tabLabels4, "sre.custom_role.label.shop_custom_name", r);
                EditBox nb = makeBox(lx, rowY(r), 130, bh, en.displayName, v -> en.displayName = v);
                nb.setHint(Component.literal("商品显示名称"));
                recordWidgetBase(nb, baseRowY(r));
                tabWidgets4.add(nb);
                r++;
                addLabel(tabLabels4, "sre.custom_role.label.shop_custom_icon", r);
                EditBox ib3 = makeBox(lx, rowY(r), 130, bh, en.itemId, v -> en.itemId = v);
                ib3.setHint(Component.literal("物品图标id"));
                recordWidgetBase(ib3, baseRowY(r));
                tabWidgets4.add(ib3);
                r++;
                if (en.commands.isEmpty()) en.commands.add("");
                for (int c = 0; c < en.commands.size(); c++) {
                    final int cdx = c; int y = rowY(r);
                    addLabel(tabLabels4, "sre.custom_role.label.shop_custom_cmd", r);
                    EditBox cm = makeBox(lx, y, 230, bh, en.commands.get(c), v -> en.commands.set(cdx, v));
                    cm.setHint(Component.literal("不需/ 例: say <player>"));
                    recordWidgetBase(cm, baseRowY(r));
                    tabWidgets4.add(cm);
                    var plusBtn = makeModernButton(lx+238, baseRowY(r), 20, bh, Component.literal("+"),
                            () -> { en.commands.add(""); init(minecraft, width, height); },
                            AccentSide.TOP);
                    tabWidgets4.add(plusBtn);
                    if (en.commands.size() > 1) {
                        var minusBtn = makeModernButton(lx+262, baseRowY(r), 20, bh, Component.literal("-"),
                                () -> { en.commands.remove(cdx); init(minecraft, width, height); },
                                AccentSide.TOP);
                        tabWidgets4.add(minusBtn);
                    }
                    r++;
                }
            }
        }
        var addEntryBtn = makeModernButton(lx, baseRowY(r), 140, bh,
                Component.translatable("sre.custom_role.add_shop_entry"),
                () -> { data.shopEntries.add(new ShopEntryData()); init(minecraft, width, height); },
                AccentSide.BOTTOM);
        tabWidgets4.add(addEntryBtn);
    }

    // ══════════════════════════════════════════════════════════════════
    // 新直觉系统 GUI 辅助方法
    // ══════════════════════════════════════════════════════════════════
    private static final String[] INSTINCT_TYPE_NAMES = {
        "DEFAULT", "NONE", "KILLER_INSTINCT", "OBSERVER_ROLE_COLOR", "TARGET_ROLE_COLOR"
    };

    /** 确保 data.instinctModes 存在至少一个模式，否则从旧字段自动补全 */
    private void ensureInstinctMode() {
        if (data.instinctModes.isEmpty()) {
            InstinctModeData m = new InstinctModeData();
            if (data.instinctSameColorFrame) m.seeingOn = "OBSERVER_ROLE_COLOR";
            if (!"*".equals(data.instinctMaxRange)) m.maxRange = data.instinctMaxRange;
            m.unlimitedTeammate = data.instinctUnlimitedTeammate;
            data.instinctModes.add(m);
        }
    }

    /** 将类型字符串循环到下一个预定义类型 */
    private String cycleInstinctTypeStr(String current) {
        String upper = current.toUpperCase().trim();
        if (upper.startsWith("CUSTOM(")) return "DEFAULT";
        for (int i = 0; i < INSTINCT_TYPE_NAMES.length; i++) {
            if (INSTINCT_TYPE_NAMES[i].equals(upper))
                return (i + 1 < INSTINCT_TYPE_NAMES.length) ? INSTINCT_TYPE_NAMES[i + 1] : "CUSTOM(0xFFFF0000)";
        }
        return "DEFAULT";
    }

    /** 获取类型字符串的显示名 */
    private String instinctTypeDisplay(String s) {
        if (s == null || s.isEmpty()) return "DEFAULT";
        String upper = s.toUpperCase().trim();
        if (upper.startsWith("CUSTOM(")) {
            String hex = upper.substring(7, upper.length() - 1).trim();
            try { return "CUSTOM(#" + Integer.toHexString(Long.decode(hex).intValue()).toUpperCase().substring(2) + ")"; }
            catch (Exception e) { return "CUSTOM(???)"; }
        }
        return upper;
    }

    /** 提取 CUSTOM 颜色字符串中的 hex 部分（不含 0x 前缀） */
    private String extractCustomHex(String s) {
        if (s == null) return "FF0000";
        String upper = s.toUpperCase().trim();
        if (upper.startsWith("CUSTOM(") && upper.endsWith(")")) {
            String hex = upper.substring(7, upper.length() - 1).trim();
            try { return Integer.toHexString(Long.decode(hex).intValue()).toUpperCase().substring(2); }
            catch (Exception e) { return "FF0000"; }
        }
        return "FF0000";
    }

    /** 检查类型字符串是否为 CUSTOM */
    private boolean isCustomType(String s) {
        return s != null && s.toUpperCase().trim().startsWith("CUSTOM(");
    }

    /** 从模式中提取第一个 CUSTOM 类型的 hex 颜色字符串 */
    private String findFirstCustomHex(InstinctModeData m) {
        for (String s : new String[]{m.seeingOff, m.seeingOn, m.beSeenOff, m.beSeenOn}) {
            if (isCustomType(s)) return extractCustomHex(s);
        }
        return "FF0000";
    }

    /** 构建一个点击循环的类型按钮，并返回 */
    private ModernButton makeInstinctTypeBtn(int x, int baseY, int w, int h, java.util.function.Supplier<String> getter, java.util.function.Consumer<String> setter) {
        String cur = getter.get();
        String display = instinctTypeDisplay(cur);
        ModernButton btn = ModernButton.builder(
                Component.literal(display).append(Component.literal(" ↻").withStyle(s -> s.withColor(0x8899AA))),
                b -> {
                    setter.accept(cycleInstinctTypeStr(getter.get()));
                    init(minecraft, width, height);
                }).bounds(x, baseY, w, h).accentBar(AccentSide.LEFT).build();
        recordWidgetBase(btn, baseY);
        return btn;
    }

    // ---- Bottom ----
    private void buildBottomButtons() {
        int by = panelTopY + panelHeight - 26, bw = 100, gap = 8;
        int sx = panelLeftX + (panelWidth - (bw * 3 + gap * 2)) / 2;
        var btn1 = ModernButton.builder(Component.translatable("sre.custom_role.save"), b -> saveRole())
                .bounds(sx, by, bw, 20).accentBar(AccentSide.BOTTOM).build();
        var btn2 = ModernButton.builder(Component.translatable("sre.custom_role.manage"), b -> {
            CustomRoleConfig config = CustomRoleConfig.getInstance();
            config.savePreferWorldPath(minecraft.getSingleplayerServer());
            minecraft.setScreen(new CustomRoleManageScreen(new CustomRoleScreen()));
        }).bounds(sx + bw + gap, by, bw, 20).accentBar(AccentSide.BOTTOM).build();
        var btn3 = ModernButton.builder(Component.translatable("sre.custom_role.cancel"), b -> onClose())
                .bounds(sx + (bw + gap) * 2, by, bw, 20).accentBar(AccentSide.BOTTOM).build();
        addRenderableWidget(btn1);
        addRenderableWidget(btn2);
        addRenderableWidget(btn3);
        bottomButtons.add(btn1);
        bottomButtons.add(btn2);
        bottomButtons.add(btn3);
    }

    private void saveRole() {
        CustomRoleConfig config = CustomRoleConfig.getInstance();
        if (originalEnglishId != null && !originalEnglishId.isBlank()) config.removeRole(originalEnglishId);
        config.removeRole(data.englishId);
        config.addRole(data);
        config.savePreferWorldPath(minecraft.getSingleplayerServer());
        var server = minecraft.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                try { io.wifi.starrailexpress.customrole.CustomRoleLoader.reload(server); } catch (Exception ignored) {}
            });
        }
        if (minecraft.player != null) minecraft.player.displayClientMessage(Component.translatable("sre.custom_role.saved", data.englishId), false);
        onClose();
    }

    // ══════════════════════════════════════════════════════════════════
    // Toggle Helpers
    // ══════════════════════════════════════════════════════════════════
    private void addBoolBtn(List<AbstractWidget> l, int r, String key, boolean cur, java.util.function.Consumer<Boolean> toggle, boolean rebuild) {
        Component st = cur ? Component.literal(" [✓]").withStyle(s -> s.withColor(0x55FF55)) : Component.literal(" [✗]").withStyle(s -> s.withColor(0xFF5555));
        var btn = ModernButton.builder(Component.translatable(key).copy().append(st), b -> {
            toggle.accept(!cur); if (rebuild) init(minecraft, width, height);
        }).bounds(fieldX(), baseRowY(r), FIELD_W, 18).accentBar(cur ? AccentSide.LEFT : AccentSide.RIGHT).build();
        recordWidgetBase(btn, baseRowY(r));
        l.add(btn);
    }
    private void addBoolBtnX(List<AbstractWidget> l, int r, String key, boolean cur, java.util.function.Consumer<Boolean> toggle, boolean rebuild) {
        Component st = cur ? Component.literal(" [✓]").withStyle(s -> s.withColor(0x55FF55)) : Component.literal(" [✗]").withStyle(s -> s.withColor(0xFF5555));
        var btn = ModernButton.builder(Component.translatable(key).copy().append(st), b -> {
            toggle.accept(!cur); if (rebuild) init(minecraft, width, height);
        }).bounds(fieldX()+170, baseRowY(r), 150, 18).accentBar(cur ? AccentSide.LEFT : AccentSide.RIGHT).build();
        recordWidgetBase(btn, baseRowY(r));
        l.add(btn);
    }
    private static int safeColor(Boolean b) {
        if (b == null) return 0x778899;
        return b.booleanValue() ? 0x55FF55 : 0xFF5555;
    }
    private static Boolean safeNext(Boolean cur) {
        if (cur == null) return Boolean.TRUE;
        return cur.booleanValue() ? Boolean.FALSE : null;
    }

    private void addTriBtn(List<AbstractWidget> l, int r, String key, Boolean cur, java.util.function.Consumer<Boolean> toggle, boolean rebuild) {
        String ss; AccentSide as;
        if (cur == null) { ss = " (--)"; as = AccentSide.TOP; } else if (cur.booleanValue()) { ss = " [✓]"; as = AccentSide.LEFT; } else { ss = " [✗]"; as = AccentSide.RIGHT; }
        final Boolean captured = cur;
        var btn = ModernButton.builder(Component.translatable(key).append(Component.literal(ss).withStyle(s -> s.withColor(safeColor(captured)))), b -> {
            toggle.accept(safeNext(captured)); if (rebuild) init(minecraft, width, height);
        }).bounds(fieldX(), baseRowY(r), 150, 18).accentBar(as).build();
        recordWidgetBase(btn, baseRowY(r));
        l.add(btn);
    }
    private void addTriBtnX(List<AbstractWidget> l, int r, String key, Boolean cur, java.util.function.Consumer<Boolean> toggle, boolean rebuild) {
        String ss; AccentSide as;
        if (cur == null) { ss = " (--)"; as = AccentSide.TOP; } else if (cur.booleanValue()) { ss = " [✓]"; as = AccentSide.LEFT; } else { ss = " [✗]"; as = AccentSide.RIGHT; }
        final Boolean captured = cur;
        var btn = ModernButton.builder(Component.translatable(key).append(Component.literal(ss).withStyle(s -> s.withColor(safeColor(captured)))), b -> {
            toggle.accept(safeNext(captured)); if (rebuild) init(minecraft, width, height);
        }).bounds(fieldX()+170, baseRowY(r), 150, 18).accentBar(as).build();
        recordWidgetBase(btn, baseRowY(r));
        l.add(btn);
    }

    // ---- Misc ----
    private void flushTabWidgets() {
        // 通过 addRenderableWidget 注册到事件系统，但不用于渲染
        // 渲染由 render() 手动遍历 getActiveTabWidgets() 完成
        tabWidgets0.forEach(w -> addRenderableWidget(w));
        tabWidgets1.forEach(w -> addRenderableWidget(w));
        tabWidgets2.forEach(w -> addRenderableWidget(w));
        tabWidgets3.forEach(w -> addRenderableWidget(w));
        tabWidgets4.forEach(w -> addRenderableWidget(w));
    }

    private EditBox makeBox(int x, int y, int w, int h, String text, java.util.function.Consumer<String> cb) {
        EditBox box = new EditBox(font, x, y, w, h, Component.empty()); box.setValue(text); box.setMaxLength(256); box.setResponder(cb); return box;
    }
    private int clamp(String v, int max) { return Math.min(max, Math.max(0, Integer.parseInt(v))); }

    // ══════════════════════════════════════════════════════════════════
    // 渲染（参考 RoleIntroduceScreen：scissor 裁剪 + 滚动条）
    // ══════════════════════════════════════════════════════════════════
    @Override
    public void renderBackground(GuiGraphics g, int i, int j, float f) {
        // 面板背景
        g.fill(panelLeftX-6, panelTopY-3, panelLeftX+panelWidth+6, panelTopY+panelHeight+3, 0xCC080C18);
        // 面板顶部高亮边框
        g.fill(panelLeftX-6, panelTopY-3, panelLeftX+panelWidth+6, panelTopY-2, 0xFF5577CC);
        // 内容区域下方填充（覆盖超出内容的 widget 绘制）
        g.fill(panelLeftX-6, contentBottom(), panelLeftX+panelWidth+6, panelTopY+panelHeight+3, 0xCC080C18);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // 1. 面板背景
        renderBackground(g, mx, my, pt);

        // 2. 固定 widget：标签栏按钮（不裁剪）
        for (var w : tabBarButtons) {
            w.render(g, mx, my, pt);
        }

        // 3. 启用 scissor 裁剪内容区域
        g.enableScissor(panelLeftX, contentTop(), panelLeftX + panelWidth, contentBottom());

        // 4. 内容 widget
        for (var w : getActiveTabWidgets()) {
            w.render(g, mx, my, pt);
        }

        // 5. 内容标签（受 scrollOffset 影响）
        List<LabelEntry> al = getActiveLabels();
        for (LabelEntry e : al) {
            int labelY = e.y() - scrollOffset;
            g.drawString(font, Component.translatable(e.key), e.x(), labelY + 4, 0xAABBCC, false);
        }

        g.disableScissor();

        // 6. 滚动条（覆盖在面板右侧）
        if (maxScroll > 0) {
            renderVScrollbar(g, mx, my);
        }

        // 7. 底部按钮（不裁剪）
        for (var w : bottomButtons) {
            w.render(g, mx, my, pt);
        }

        // 8. 标题
        g.drawCenteredString(font,
                Component.translatable("sre.custom_role.title").withStyle(s -> s.withColor(0x55BBFF).withBold(true)),
                panelLeftX + panelWidth / 2, panelTopY + 18, 0xFFFFFF);
    }

    // ══════════════════════════════════════════════════════════════════
    // 滚动条渲染
    // ══════════════════════════════════════════════════════════════════
    private void renderVScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        int sbX = panelLeftX + panelWidth - 6 - SCROLL_W;
        int sbY = contentTop();
        int sbH = contentHeight();

        // 轨道
        g.fill(sbX, sbY, sbX + SCROLL_W, sbY + sbH, 0xFF111828);
        g.fill(sbX + 1, sbY + 1, sbX + SCROLL_W - 1, sbY + sbH - 1, 0x55334466);

        // 滑块
        int totalContentH = sbH + maxScroll;
        float ratio = Math.min(1f, (float) sbH / Math.max(1, totalContentH));
        int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (sbH * ratio));
        int thumbY = sbY + (int) ((sbH - thumbH) * ((float) scrollOffset / maxScroll));

        boolean hl = isDraggingScroll || isInRect(mouseX, mouseY, sbX, thumbY, SCROLL_W, thumbH);
        g.fill(sbX, thumbY, sbX + SCROLL_W, thumbY + thumbH,
                hl ? 0xFF8899CC : 0xFF556699);
        g.fill(sbX + 1, thumbY + 1, sbX + SCROLL_W - 1, thumbY + thumbH - 1,
                hl ? 0xFFAABBEE : 0xFF7788BB);
        g.fill(sbX + 1, thumbY + 1, sbX + SCROLL_W - 1, thumbY + 3, 0x44FFFFFF);
    }

    // ══════════════════════════════════════════════════════════════════
    // 鼠标事件
    // ══════════════════════════════════════════════════════════════════
    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx >= panelLeftX && mx < panelLeftX + panelWidth
                && my >= contentTop() && my < contentBottom()
                && maxScroll > 0) {
            scrollOffset = Mth.clamp(
                    (int) (scrollOffset - scrollY * 22),
                    0, maxScroll);
            applyScrollOffsets();
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int sbX = panelLeftX + panelWidth - 6 - SCROLL_W;
            int sbY = contentTop();
            int sbH = contentHeight();
            if (isInRect((int) mx, (int) my, sbX, sbY, SCROLL_W, sbH) && maxScroll > 0) {
                isDraggingScroll = true;
                dragScrollStartY = my;
                dragScrollStartOffset = scrollOffset;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (isDraggingScroll && maxScroll > 0) {
            int sbH = contentHeight();
            int totalContentH = sbH + maxScroll;
            float ratio = Math.min(1f, (float) sbH / Math.max(1, totalContentH));
            int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (sbH * ratio));
            double trackH = sbH - thumbH;
            if (trackH > 0) {
                scrollOffset = Mth.clamp(
                        (int) (dragScrollStartOffset + (my - dragScrollStartY) / trackH * maxScroll),
                        0, maxScroll);
                applyScrollOffsets();
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        isDraggingScroll = false;
        return super.mouseReleased(mx, my, button);
    }

    // ══════════════════════════════════════════════════════════════════
    // 工具
    // ══════════════════════════════════════════════════════════════════
    private static boolean isInRect(int px, int py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
