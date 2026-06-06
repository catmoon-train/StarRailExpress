package io.wifi.starrailexpress.customrole;

import io.wifi.starrailexpress.customrole.CustomRoleData.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton.AccentSide;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.Path;

public class CustomRoleScreen extends Screen {
    private static final int PANEL_WIDTH = 380, PANEL_HEIGHT = 460;
    private int panelLeftX, panelTopY, activeTab = 0;
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

    // Toggles - directly correspond to data fields
    private int moodIndex; // 0=REAL, 1=FAKE

    public CustomRoleScreen() { super(Component.translatable("sre.custom_role.title")); syncToggles(); }
    public CustomRoleScreen(CustomRoleData d) { super(Component.translatable("sre.custom_role.title")); this.data = d; this.originalEnglishId = d.englishId == null ? "" : d.englishId; syncToggles(); }

    private void syncToggles() {
        moodIndex = "FAKE".equalsIgnoreCase(data.moodType) ? 1 : 0;
    }

    @Override
    protected void init() {
        clearTabs();
        tabLabels0.clear(); tabLabels1.clear(); tabLabels2.clear();
        tabLabels3.clear(); tabLabels4.clear();
        panelLeftX = (width - PANEL_WIDTH) / 2;
        panelTopY = (height - PANEL_HEIGHT) / 2;
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
        syncTabVisibility();
    }

    private void clearTabs() { tabWidgets0.clear(); tabWidgets1.clear(); tabWidgets2.clear(); tabWidgets3.clear(); tabWidgets4.clear(); }
    private int fieldX() { return panelLeftX + FIELD_LEFT; }
    private int labelX() { return panelLeftX + 4; }
    private int rowY(int i) { return panelTopY + 34 + i * 22; }

    private void addLabel(List<LabelEntry> l, String key, int r) { l.add(new LabelEntry(key, labelX(), rowY(r))); }
    private EditBox makeLabeledBox(List<AbstractWidget> wl, List<LabelEntry> ll, int r, int w, String key, String val, java.util.function.Consumer<String> cb) {
        addLabel(ll, key, r); EditBox b = makeBox(fieldX(), rowY(r), w, 18, val, cb); wl.add(b); return b;
    }
    private EditBox makeLabeledHintBox(List<AbstractWidget> wl, List<LabelEntry> ll, int r, int w, String key, String val, String hint, java.util.function.Consumer<String> cb) {
        EditBox b = makeLabeledBox(wl, ll, r, w, key, val, cb); b.setHint(Component.literal(hint)); return b;
    }

    private void buildTabBar() {
        int tw = 68, th = 20, tg = 4;
        int sx = panelLeftX + (PANEL_WIDTH - (tw * 5 + tg * 4)) / 2;
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            var b = ModernButton.builder(Component.translatable("sre.custom_role.tab." + TAB_NAMES[i]),
                btn -> { activeTab = idx; init(minecraft, width, height); })
                .bounds(sx + i * (tw + tg), panelTopY + 8, tw, th);
            if (activeTab == i) b.accentBar(AccentSide.BOTTOM); else b.accentBar();
            addRenderableWidget(b.build());
        }
    }

    // ---- TAB 0: Basic ----
    private void buildBasicTab() {
        int r = 0;
        makeLabeledHintBox(tabWidgets0, tabLabels0, r++, FIELD_W, "sre.custom_role.label.english_id", data.englishId, "my_custom_role", v -> data.englishId = v).setMaxLength(64);
        makeLabeledHintBox(tabWidgets0, tabLabels0, r++, FIELD_W, "sre.custom_role.label.display_name", data.displayName, "职业显示的名字", v -> data.displayName = v);
        makeLabeledHintBox(tabWidgets0, tabLabels0, r++, FIELD_W, "sre.custom_role.label.goals", data.goals, "职业的胜利目标", v -> data.goals = v);
        makeLabeledHintBox(tabWidgets0, tabLabels0, r++, FIELD_W, "sre.custom_role.label.description", data.description, "职业描述", v -> data.description = v);

        // 药水效果 - 重要: responder 直接更新 data.initialEffects
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
        tabWidgets0.add(effectsBox);
        r++;

        // RGB
        addLabel(tabLabels0, "sre.custom_role.label.color_rgb", r++);
        EditBox rBox = makeBox(fieldX(), rowY(r-1), 40, 18, String.valueOf(data.colorR), v -> { try { data.colorR=clamp(v,255); } catch(Exception e){} });
        EditBox gBox = makeBox(fieldX()+48, rowY(r-1), 40, 18, String.valueOf(data.colorG), v -> { try { data.colorG=clamp(v,255); } catch(Exception e){} });
        EditBox bBox = makeBox(fieldX()+96, rowY(r-1), 40, 18, String.valueOf(data.colorB), v -> { try { data.colorB=clamp(v,255); } catch(Exception e){} });
        tabWidgets0.addAll(List.of(rBox, gBox, bBox));

        addBoolBtn(tabWidgets0, r++, "sre.custom_role.is_innocent", data.isInnocent, v -> data.isInnocent = v, true);
        addBoolBtn(tabWidgets0, r++, "sre.custom_role.can_use_killer", data.canUseKiller, v -> data.canUseKiller = v, true);

        Component ml = Component.translatable("sre.custom_role.mood." + (moodIndex == 0 ? "real" : "fake"));
        tabWidgets0.add(ModernButton.builder(Component.translatable("sre.custom_role.mood.current").append(": ").append(ml),
            b -> { moodIndex = (moodIndex + 1) % 2; data.moodType = moodIndex == 0 ? "REAL" : "FAKE"; init(minecraft, width, height); })
            .bounds(fieldX(), rowY(r), FIELD_W, 18).accentBar(AccentSide.LEFT).build());
        addLabel(tabLabels0, "sre.custom_role.label.mood", r++);

        makeLabeledHintBox(tabWidgets0, tabLabels0, r++, 80, "sre.custom_role.label.sprint_mult", String.valueOf(data.sprintMultiplier), "默认1",
            v -> { try { data.sprintMultiplier = Double.parseDouble(v); } catch(Exception ignored){} });
        addBoolBtn(tabWidgets0, r++, "sre.custom_role.infinite_sprint", data.infiniteSprint, v -> data.infiniteSprint = v, true);
        addBoolBtn(tabWidgets0, r++, "sre.custom_role.can_see_time", data.canSeeTime, v -> data.canSeeTime = v, true);
    }

    // ---- TAB 1: Advanced ----
    private void buildAdvancedTab() {
        int r = 0;
        addBoolBtn(tabWidgets1, r, "sre.custom_role.can_see_coin", data.canSeeCoin, v -> data.canSeeCoin = v, true);
        addBoolBtnX(tabWidgets1, r++, "sre.custom_role.can_use_instinct", data.canUseInstinct, v -> data.canUseInstinct = v, true);
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
            tabWidgets2.addAll(List.of(ib, cb));
            tabWidgets2.add(ModernButton.builder(Component.literal("+"), b3 -> { data.initialItems.add(new InitialItemEntry()); init(minecraft, width, height); }).bounds(fieldX()+196, y, 20, 18).accentBar().build());
            if (data.initialItems.size() > 1) tabWidgets2.add(ModernButton.builder(Component.literal("-"), b3 -> { data.initialItems.remove(idx); init(minecraft, width, height); }).bounds(fieldX()+220, y, 20, 18).accentBar().build());
            r++;
        }
        r++;
        addBoolBtn(tabWidgets2, r++, "sre.custom_role.instinct_same_color", data.instinctSameColorFrame, v -> data.instinctSameColorFrame = v, false);
        makeLabeledHintBox(tabWidgets2, tabLabels2, r++, 80, "sre.custom_role.label.instinct_range", data.instinctMaxRange, "* = 不限", v -> data.instinctMaxRange = v);
        addBoolBtn(tabWidgets2, r++, "sre.custom_role.enable_ability", data.enableAbility, v -> data.enableAbility = v, true);
        if (data.enableAbility) {
            if (data.abilitySkillCommands.isEmpty()) data.abilitySkillCommands.add("");
            for (int i = 0; i < data.abilitySkillCommands.size(); i++) {
                final int idx = i; int y = rowY(r);
                addLabel(tabLabels2, "sre.custom_role.label.ability_commands", r);
                EditBox cmdBox = makeBox(fieldX(), y, 250, 18, data.abilitySkillCommands.get(i), v -> data.abilitySkillCommands.set(idx, v));
                cmdBox.setHint(Component.literal("不需/ 例: say <player>"));
                tabWidgets2.add(cmdBox);
                tabWidgets2.add(ModernButton.builder(Component.literal("+"), b3 -> { data.abilitySkillCommands.add(""); init(minecraft, width, height); }).bounds(fieldX()+258, y, 20, 18).accentBar().build());
                if (data.abilitySkillCommands.size() > 1) tabWidgets2.add(ModernButton.builder(Component.literal("-"), b3 -> { data.abilitySkillCommands.remove(idx); init(minecraft, width, height); }).bounds(fieldX()+282, y, 20, 18).accentBar().build());
                r++;
            }
            makeLabeledHintBox(tabWidgets2, tabLabels2, r++, 80, "sre.custom_role.label.ability_cooldown", String.valueOf(data.abilityCooldownSeconds), "冷却秒数",
                v -> { try { data.abilityCooldownSeconds = Integer.parseInt(v); } catch(Exception ignored){} });
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
    }

    // ---- TAB 4: Shop ----
    private void buildShopTab() {
        int lx = fieldX(), bh = 18, r = 0;
        String[] types = {"item", "psycho", "blackout", "monitor_fail", "custom"};
        for (int i = 0; i < data.shopEntries.size(); i++) {
            final int idx = i; ShopEntryData en = data.shopEntries.get(i);
            int yr = rowY(r);
            // 类型
            tabWidgets4.add(ModernButton.builder(Component.literal("[" + en.type + "]"), b -> {
                int next = (java.util.Arrays.asList(types).indexOf(en.type) + 1) % types.length;
                if (next < 0) next = 0; en.type = types[next]; init(minecraft, width, height);
            }).bounds(lx, yr, 75, bh).accentBar(AccentSide.LEFT).build());
            // 价格
            EditBox pb = makeBox(lx + 83, yr, 55, bh, String.valueOf(en.price), v -> { try { en.price = Math.max(0, Integer.parseInt(v)); } catch(Exception ignored){} });
            pb.setHint(Component.literal("价格")); tabWidgets4.add(pb);
            // 冷却(仅 item 和 custom)
            if ("item".equals(en.type) || "custom".equals(en.type)) {
                EditBox cd = makeBox(lx + 146, yr, 45, bh, String.valueOf(en.cooldownSeconds), v -> { try { en.cooldownSeconds = Math.max(0, Integer.parseInt(v)); } catch(Exception ignored){} });
                cd.setHint(Component.literal("CD秒")); tabWidgets4.add(cd);
            }
            // 禁止重复(item)
            if ("item".equals(en.type)) {
                boolean nd = !en.allowDuplicate;
                tabWidgets4.add(ModernButton.builder(Component.literal(nd ? "禁重复" : "允重复"), b -> { en.allowDuplicate = !en.allowDuplicate; init(minecraft, width, height); })
                    .bounds(lx + 199, yr, 55, bh).accentBar(nd ? AccentSide.RIGHT : AccentSide.LEFT).build());
                tabWidgets4.add(ModernButton.builder(Component.literal("X"), b -> { data.shopEntries.remove(idx); init(minecraft, width, height); })
                    .bounds(lx + 260, yr, 20, bh).accentBar(AccentSide.RIGHT).build());
            } else {
                tabWidgets4.add(ModernButton.builder(Component.literal("X"), b -> { data.shopEntries.remove(idx); init(minecraft, width, height); })
                    .bounds(lx + 199, yr, 20, bh).accentBar(AccentSide.RIGHT).build());
            }
            r++;
            if ("item".equals(en.type)) {
                addLabel(tabLabels4, "sre.custom_role.label.shop_item_id", r);
                EditBox ib2 = makeBox(lx, rowY(r), 160, bh, en.itemId, v -> en.itemId = v); ib2.setHint(Component.literal("物品id")); tabWidgets4.add(ib2); r++;
            }
            if ("custom".equals(en.type)) {
                addLabel(tabLabels4, "sre.custom_role.label.shop_custom_name", r);
                EditBox nb = makeBox(lx, rowY(r), 130, bh, en.displayName, v -> en.displayName = v); nb.setHint(Component.literal("商品显示名称")); tabWidgets4.add(nb); r++;
                addLabel(tabLabels4, "sre.custom_role.label.shop_custom_icon", r);
                EditBox ib3 = makeBox(lx, rowY(r), 130, bh, en.itemId, v -> en.itemId = v); ib3.setHint(Component.literal("物品图标id")); tabWidgets4.add(ib3); r++;
                if (en.commands.isEmpty()) en.commands.add("");
                for (int c = 0; c < en.commands.size(); c++) {
                    final int cdx = c; int y = rowY(r);
                    addLabel(tabLabels4, "sre.custom_role.label.shop_custom_cmd", r);
                    EditBox cm = makeBox(lx, y, 230, bh, en.commands.get(c), v -> en.commands.set(cdx, v)); cm.setHint(Component.literal("不需/ 例: say <player>")); tabWidgets4.add(cm);
                    tabWidgets4.add(ModernButton.builder(Component.literal("+"), b2 -> { en.commands.add(""); init(minecraft, width, height); }).bounds(lx+238, y, 20, bh).accentBar().build());
                    if (en.commands.size() > 1) tabWidgets4.add(ModernButton.builder(Component.literal("-"), b2 -> { en.commands.remove(cdx); init(minecraft, width, height); }).bounds(lx+262, y, 20, bh).accentBar().build());
                    r++;
                }
            }
        }
        tabWidgets4.add(ModernButton.builder(Component.translatable("sre.custom_role.add_shop_entry"), b -> { data.shopEntries.add(new ShopEntryData()); init(minecraft, width, height); })
            .bounds(lx, rowY(r), 140, bh).accentBar(AccentSide.BOTTOM).build());
    }

    // ---- Bottom ----
    private void buildBottomButtons() {
        int by = panelTopY + PANEL_HEIGHT - 26, bw = 100, gap = 8;
        int sx = panelLeftX + (PANEL_WIDTH - (bw * 3 + gap * 2)) / 2;
        addRenderableWidget(ModernButton.builder(Component.translatable("sre.custom_role.save"), b -> saveRole()).bounds(sx, by, bw, 20).accentBar(AccentSide.BOTTOM).build());
        addRenderableWidget(ModernButton.builder(Component.translatable("sre.custom_role.manage"), b -> {
            CustomRoleConfig config = CustomRoleConfig.getInstance();
            config.savePreferWorldPath(minecraft.getSingleplayerServer());
            minecraft.setScreen(new CustomRoleManageScreen(new CustomRoleScreen()));
        }).bounds(sx + bw + gap, by, bw, 20).accentBar(AccentSide.BOTTOM).build());
        addRenderableWidget(ModernButton.builder(Component.translatable("sre.custom_role.cancel"), b -> onClose()).bounds(sx + (bw + gap) * 2, by, bw, 20).accentBar(AccentSide.BOTTOM).build());
    }

    private void saveRole() {
        // Already saved via responders. Just persist to file.
        CustomRoleConfig config = CustomRoleConfig.getInstance();
        // remove previous entry if editing englishId
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

    // ---- Toggle Helpers (直接更新 data, 只在需要时 rebuild) ----
    private void addBoolBtn(List<AbstractWidget> l, int r, String key, boolean cur, java.util.function.Consumer<Boolean> toggle, boolean rebuild) {
        Component st = cur ? Component.literal(" [✓]").withStyle(s -> s.withColor(0x55FF55)) : Component.literal(" [✗]").withStyle(s -> s.withColor(0xFF5555));
        l.add(ModernButton.builder(Component.translatable(key).copy().append(st), b -> {
            toggle.accept(!cur); if (rebuild) init(minecraft, width, height);
        }).bounds(fieldX(), rowY(r), FIELD_W, 18).accentBar(cur ? AccentSide.LEFT : AccentSide.RIGHT).build());
    }
    private void addBoolBtnX(List<AbstractWidget> l, int r, String key, boolean cur, java.util.function.Consumer<Boolean> toggle, boolean rebuild) {
        Component st = cur ? Component.literal(" [✓]").withStyle(s -> s.withColor(0x55FF55)) : Component.literal(" [✗]").withStyle(s -> s.withColor(0xFF5555));
        l.add(ModernButton.builder(Component.translatable(key).copy().append(st), b -> {
            toggle.accept(!cur); if (rebuild) init(minecraft, width, height);
        }).bounds(fieldX()+170, rowY(r), 150, 18).accentBar(cur ? AccentSide.LEFT : AccentSide.RIGHT).build());
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
        l.add(ModernButton.builder(Component.translatable(key).append(Component.literal(ss).withStyle(s -> s.withColor(safeColor(captured)))), b -> {
            toggle.accept(safeNext(captured)); if (rebuild) init(minecraft, width, height);
        }).bounds(fieldX(), rowY(r), 150, 18).accentBar(as).build());
    }
    private void addTriBtnX(List<AbstractWidget> l, int r, String key, Boolean cur, java.util.function.Consumer<Boolean> toggle, boolean rebuild) {
        String ss; AccentSide as;
        if (cur == null) { ss = " (--)"; as = AccentSide.TOP; } else if (cur.booleanValue()) { ss = " [✓]"; as = AccentSide.LEFT; } else { ss = " [✗]"; as = AccentSide.RIGHT; }
        final Boolean captured = cur;
        l.add(ModernButton.builder(Component.translatable(key).append(Component.literal(ss).withStyle(s -> s.withColor(safeColor(captured)))), b -> {
            toggle.accept(safeNext(captured)); if (rebuild) init(minecraft, width, height);
        }).bounds(fieldX()+170, rowY(r), 150, 18).accentBar(as).build());
    }

    // ---- Misc ----
    private void flushTabWidgets() { tabWidgets0.forEach(this::addRenderableWidget); tabWidgets1.forEach(this::addRenderableWidget); tabWidgets2.forEach(this::addRenderableWidget); tabWidgets3.forEach(this::addRenderableWidget); tabWidgets4.forEach(this::addRenderableWidget); }
    private void syncTabVisibility() {
        tabWidgets0.forEach(w -> w.visible = (activeTab == 0)); tabWidgets1.forEach(w -> w.visible = (activeTab == 1));
        tabWidgets2.forEach(w -> w.visible = (activeTab == 2)); tabWidgets3.forEach(w -> w.visible = (activeTab == 3));
        tabWidgets4.forEach(w -> w.visible = (activeTab == 4));
    }
    private EditBox makeBox(int x, int y, int w, int h, String text, java.util.function.Consumer<String> cb) {
        EditBox box = new EditBox(font, x, y, w, h, Component.empty()); box.setValue(text); box.setMaxLength(256); box.setResponder(cb); return box;
    }
    private int clamp(String v, int max) { return Math.min(max, Math.max(0, Integer.parseInt(v))); }

    @Override public void renderBackground(GuiGraphics g, int i, int j, float f) {
        g.fill(panelLeftX-6, panelTopY-3, panelLeftX+PANEL_WIDTH+6, panelTopY+PANEL_HEIGHT+3, 0xCC080C18);
        g.fill(panelLeftX-6, panelTopY-3, panelLeftX+PANEL_WIDTH+6, panelTopY-2, 0xFF5577CC);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, Component.translatable("sre.custom_role.title").withStyle(s -> s.withColor(0x55BBFF).withBold(true)), panelLeftX+PANEL_WIDTH/2, panelTopY+18, 0xFFFFFF);
        List<LabelEntry> al = switch(activeTab) { case 0->tabLabels0; case 1->tabLabels1; case 2->tabLabels2; case 3->tabLabels3; case 4->tabLabels4; default->List.of(); };
        for (LabelEntry e : al) g.drawString(font, Component.translatable(e.key), e.x, e.y+4, 0xAABBCC, false);
    }
    @Override public boolean isPauseScreen() { return false; }
}
