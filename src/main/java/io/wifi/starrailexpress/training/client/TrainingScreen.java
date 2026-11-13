package io.wifi.starrailexpress.training.client;

import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics;
import io.wifi.starrailexpress.client.gui.widget.SreButton;
import io.wifi.starrailexpress.client.gui.widget.SreSwitchButton;
import io.wifi.starrailexpress.client.gui.widget.SwitchState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.utils.RoleUtils;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** 原生训练操作台：本人配装总览、明确目标的目录/详情、即时权威配置。 */
@Environment(EnvType.CLIENT)
public final class TrainingScreen extends Screen {
    private static final String P = "training.sre.ui.";
    private static final int HOME = 0, ITEMS = 1, ROLES = 2, ENEMIES = 3, SETTINGS = 4;
    private static final String[] TABS = {"overview", "items", "roles", "enemies", "settings"};
    private static final String[] CATEGORIES = {"all", "weapons", "ammo", "consumables", "functional", "role"};
    private static final String[] TEAMS = {"all", "killer", "innocent", "sheriff", "neutral"};
    private static final String[] BEHAVIORS = {"stationary", "moving", "combat"};
    private static final String[] EQUIPMENT = {"knife", "gun"};
    private static final String[] AREAS = {"range", "arena", "train"};
    private final Screen parent;
    private CompoundTag state = new CompoundTag();
    private long revision = -1;
    private final List<CatalogueItem> items = new ArrayList<>();
    private final List<CompoundTag> roles = new ArrayList<>();
    private final List<CompoundTag> modifiers = new ArrayList<>();
    private final Map<String, EditBox> searches = new HashMap<>();
    private final Map<String, EditBox> numberInputs = new HashMap<>();
    private int shownHitRate = -1;
    private final Map<String, Integer> pages = new HashMap<>();
    private final Map<String, AbstractWidget> keyed = new LinkedHashMap<>();
    private final List<Runnable> bindings = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private final List<Rect> cards = new ArrayList<>();
    private final Map<String, Pager> pagers = new LinkedHashMap<>();
    private int tab, enemyTab, settingsTab;
    private String category = "all", team = "all", selectedRole = "", selectedItem = "";
    private String picker = "";
    private int selectedSlot = -1;
    private String modifierTarget = "", selectedModifier = "";
    private int modifierSlot = -1;
    private boolean modifierDetail;
    private boolean roleDetail, botDetail, rebuild;
    private boolean awaiting, uncertain;
    private String localMessage = "";
    private long sentAt;
    private CompoundTag expected = new CompoundTag();
    private String expectedTarget = "";
    private int expectedSlot = -1;
    private Rect panel, body;

    public TrainingScreen(Screen parent) {
        super(tr("title"));
        this.parent = parent;
    }

    @Override protected void init() {
        syncState();
        build();
        if (!TrainingClient.hasCatalogue() && !awaiting) refresh();
    }

    void acceptState() { syncState(); }

    /** 快照只刷新绑定值；目录或槽位数量变化才重排，输入框实例与翻页位置始终保留。 */
    private void syncState() {
        CompoundTag incoming = TrainingClient.state();
        boolean catalogueChanged = !incoming.getList("items", Tag.TAG_COMPOUND).equals(state.getList("items", Tag.TAG_COMPOUND))
                || !incoming.getList("roles", Tag.TAG_COMPOUND).equals(state.getList("roles", Tag.TAG_COMPOUND))
                || !incoming.getList("modifierCatalogue", Tag.TAG_COMPOUND).equals(state.getList("modifierCatalogue", Tag.TAG_COMPOUND));
        boolean rosterChanged = incoming.getList("bots", Tag.TAG_COMPOUND).size() != state.getList("bots", Tag.TAG_COMPOUND).size();
        state = incoming;
        revision = TrainingClient.revision();
        if (catalogueChanged) readCatalogues();
        if (catalogueChanged && localMessage.equals("loading")) localMessage = "";
        if (bot(selectedSlot) == null) { selectedSlot = -1; botDetail = false; }
        if (awaiting) {
            CompoundTag actual = expectedTarget.equals("settings") ? settings()
                    : expectedTarget.equals("bot") ? bot(expectedSlot) : null;
            if (expected.isEmpty() || error(state.getString("message")) || matches(actual, expected)) {
                awaiting = false;
                uncertain = false;
                localMessage = "";
                if (expected.contains("botHitRate")) shownHitRate = -1;
            }
        }
        if (catalogueChanged || rosterChanged) rebuild = true;
        syncHitRateInput();
    }

    private void readCatalogues() {
        items.clear();
        roles.clear();
        modifiers.clear();
        for (Tag value : state.getList("items", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            ItemStack stack = ItemStack.EMPTY;
            if (minecraft != null && minecraft.level != null) {
                try { stack = ItemStack.parseOptional(minecraft.level.registryAccess(), entry.getCompound("stack")); }
                catch (RuntimeException ignored) { /* 单个损坏物品不应阻断目录。 */ }
            }
            Component name = stack.isEmpty() ? Component.literal(entry.getString("name").isBlank()
                    ? entry.getString("key") : entry.getString("name")) : stack.getHoverName();
            items.add(new CatalogueItem(entry.getString("key"), entry.getString("category"), stack, name));
        }
        for (Tag value : state.getList("roles", Tag.TAG_COMPOUND)) roles.add((CompoundTag) value);
        for (Tag value : state.getList("modifierCatalogue", Tag.TAG_COMPOUND)) modifiers.add((CompoundTag) value);
    }

    private void build() {
        String focus = keyed.entrySet().stream().filter(e -> e.getValue() == getFocused()).map(Map.Entry::getKey).findFirst().orElse("");
        setFocused(null);
        clearWidgets();
        keyed.clear(); bindings.clear(); labels.clear(); cards.clear(); pagers.clear();
        int w = Math.max(1, Math.min(980, width - 16));
        panel = new Rect((width - w) / 2, 8, w, Math.max(1, height - 16));
        Rect inner = new Rect(panel.x + 8, panel.y + 8, Math.max(1, w - 16), panel.h - 16);
        int backW = Math.min(72, inner.w / 4), refreshW = Math.min(76, inner.w / 4);
        label(new Rect(inner.x, inner.y + 5, inner.w - backW - refreshW - 12, 12), () -> title, SREPanelStyle.GOLD);
        button("refresh", new Rect(inner.right() - backW - refreshW - 4, inner.y, refreshW, 20), () -> tr("manage"), tr("refresh_hint"),
                () -> TrainingClient.isTraining() && !awaiting && !TrainingWorldLauncher.isBusy(), this::refresh, false);
        button("close", new Rect(inner.right() - backW, inner.y, backW, 20), () -> tr("cancel"), null, () -> true, this::onClose, false);
        Rect nav = new Rect(inner.x, inner.y + 24, inner.w, 20);
        for (int i = 0; i < TABS.length; i++) {
            int index = i;
            select("tab" + i, cell(nav, i, TABS.length), () -> tr("tab." + TABS[index]), null, () -> tab == index, () -> {
                tab = index; picker = ""; modifierTarget = ""; rebuild = true;
            });
        }
        Rect common = new Rect(inner.x, inner.y + 48, inner.w, 20);
        action("heal", cell(common, 0, 3), "heal", "heal_hint", () -> send("heal"));
        action("cooldown", cell(common, 1, 3), "cooldown", "cooldown_hint", () -> send("cooldown"));
        action("money", cell(common, 2, 3), "money", "money_hint", () -> send("money"));
        body = new Rect(inner.x, inner.y + 72, inner.w, Math.max(1, panel.bottom() - 24 - (inner.y + 72)));
        if (!modifierTarget.isEmpty()) buildModifiers(body);
        else if (!picker.isEmpty()) buildRoles(body, true);
        else switch (tab) {
            case HOME -> buildOverview(body);
            case ITEMS -> buildItems(body);
            case ROLES -> buildRoles(body, false);
            case ENEMIES -> buildEnemies(body);
            default -> buildSettings(body);
        }
        rebuild = false;
        updateBindings();
        AbstractWidget target = keyed.get(focus);
        if (target != null && target.visible) { setFocused(target); target.setFocused(true); }
    }

    /** 320×240 时仍有五条完整轨道；本人辅助和两种假人目标无需先翻配置表。 */
    private void buildOverview(Rect r) {
        label(new Rect(r.x + 4, r.y, r.w - 8, 12),
                () -> tr("current_role", roleName(state.getString("myRole"))), SREPanelStyle.GOLD);
        Rect loadout = new Rect(r.x, r.y + 16, r.w, 20);
        button("homeRole", cell(loadout, 0, 3), () -> tr("overview.role"), tr("choose_role_hint"), () -> true,
                () -> navigate(ROLES), false);
        modifierEntry("homeModifiers", cell(loadout, 1, 3), "self", -1);
        button("homeItems", cell(loadout, 2, 3), () -> tr("overview.items"), tr("kit_hint"), () -> true,
                () -> navigate(ITEMS), false);
        Rect assists = new Rect(r.x, r.y + 40, r.w, 20);
        toggle("unlimitedStamina", cell(assists, 0, 2));
        toggle("speedBoost", cell(assists, 1, 2));
        Rect camp = new Rect(r.x, r.y + 64, r.w, 20);
        toggle("noCooldown", cell(camp, 0, 2));
        button("homeBots", cell(camp, 1, 2), () -> tr("overview.bots", settings().getInt("enemyCount")), tr("batch_hint"),
                () -> true, () -> navigate(ENEMIES), false);
        Rect shortcuts = new Rect(r.x, r.y + 88, r.w, 20);
        modifierEntry("homeBatchModifiers", cell(shortcuts, 0, 2), "batch", -1);
        button("homeTeleports", cell(shortcuts, 1, 2), () -> tr("teleports"), tr("teleport_hint"), () -> true,
                () -> { settingsTab = 1; navigate(SETTINGS); }, false);
        if (r.h >= 144) {
            Rect summary = new Rect(r.x + 4, r.y + 120, r.w - 8, r.h - 120);
            label(summary, () -> tr("overview.summary", state.getInt("alive"), state.getInt("pending"),
                    modifierIds("self", -1).size(), state.getList("myModifiers", Tag.TAG_STRING).size()), SREPanelStyle.BODY);
        }
    }

    private void navigate(int destination) { tab = destination; picker = ""; modifierTarget = ""; rebuild = true; }

    private void modifierEntry(String key, Rect r, String target, int slot) {
        button(key, r, () -> tr("modifier.entry." + target, modifierSelection(target, slot).size()),
                tr("modifier.scope." + target).append("\n").append(tr("modifier.persistence")),
                () -> !target.equals("bot") || bot(slot) != null,
                () -> { modifierTarget = target; modifierSlot = slot; modifierDetail = false; rebuild = true; }, false);
    }

    /** 所有入口共用同一目录；目标及槽位独立保存，不跟随列表中后来选中的假人。 */
    private void buildModifiers(Rect r) {
        boolean wide = r.w >= 560 && r.h >= 192;
        label(new Rect(r.x + 4, r.y, r.w - 8, 12), () -> modifierTarget.equals("batch")
                ? tr("modifier.batch_target_count", modifierIds("batch", -1).size())
                : tr("modifier.target_count", modifierTargetName(modifierTarget, modifierSlot),
                        modifierSelection(modifierTarget, modifierSlot).size()), SREPanelStyle.GOLD);
        Rect actions = new Rect(r.x, r.y + 16, r.w, 20);
        button("modifierBack", cell(actions, 0, 2), () -> tr(!wide && modifierDetail ? "back_to_list" : "modifier.back"),
                null, () -> true, () -> {
                    if (!wide && modifierDetail) modifierDetail = false;
                    else modifierTarget = "";
                    rebuild = true;
                }, false);
        button("modifierClear", cell(actions, 1, 2), () -> tr("modifier.clear"), tr("modifier.clear_hint"),
                () -> modifierReady(modifierTarget, modifierSlot) && hasModifierConfiguration(),
                this::confirmClearModifiers, true);
        Rect content = new Rect(r.x, r.y + 40, r.w, r.h - 40);
        if (!wide && modifierDetail) { buildModifierDetail(content); return; }
        Rect left = wide ? new Rect(content.x, content.y, content.w * 2 / 5, content.h) : content;
        String searchId = "modifiers:" + modifierTarget + ":" + modifierSlot;
        search(searchId, new Rect(left.x, left.y, left.w, 20));
        Rect list = new Rect(left.x, left.y + 24, left.w, left.h - 24);
        List<CompoundTag> filtered = modifiers.stream().filter(entry -> contains(query(searchId),
                modifierName(entry).getString(), entry.getString("id"), entry.getString("name"))).toList();
        int capacity = Math.max(1, (list.h - 24) / 24), start = page(searchId, filtered.size(), capacity) * capacity;
        for (int i = start; i < Math.min(filtered.size(), start + capacity); i++) {
            CompoundTag entry = filtered.get(i); String id = entry.getString("id");
            select("modifier:" + id, new Rect(list.x, list.y + (i - start) * 24, list.w, 20),
                    () -> modifierRowName(entry), modifierName(entry).copy().append("\n").append(id),
                    () -> selectedModifier.equals(id), () -> { selectedModifier = id; modifierDetail = true; rebuild = true; });
        }
        empty(list, filtered.size(), false);
        pager(searchId, new Rect(list.x, list.bottom() - 20, list.w, 20), filtered.size(), capacity);
        if (wide) buildModifierDetail(new Rect(left.right() + 8, content.y, content.w - left.w - 8, content.h));
    }

    private void buildModifierDetail(Rect r) {
        cards.add(r);
        CompoundTag entry = modifiers.stream().filter(m -> m.getString("id").equals(selectedModifier)).findFirst().orElse(null);
        if (entry == null) { label(new Rect(r.x + 8, r.y + 4, r.w - 16, 12), () -> tr("modifier.select"), SREPanelStyle.MUTED); return; }
        String id = entry.getString("id");
        label(new Rect(r.x + 8, r.y + 3, r.w - 16, 12), () -> modifierName(entry), SREPanelStyle.GOLD);
        Rect actions = new Rect(r.x + 4, r.y + 20, r.w - 8, 20);
        Component hint = tr("modifier.persistence").append("\n").append(tr("modifier.limit"));
        boolean batch = modifierTarget.equals("batch");
        if (batch) {
            // 默认配置与个体覆盖可能不同：整组必须分别提供添加/移除，不能只按默认值切换。
            button("modifierAdd", cell(actions, 0, 2), () -> tr("modifier.add"), hint,
                    () -> modifierReady(modifierTarget, modifierSlot) && (modifierConfigured(id)
                            || modifierSelection(modifierTarget, modifierSlot).size() < 16)
                            && (!modifierConfigured(id) || botModifierCount(id) < state.getList("bots", Tag.TAG_COMPOUND).size()),
                    () -> changeModifier(modifierTarget, modifierSlot, "add", id), true);
            button("modifierRemove", cell(actions, 1, 2), () -> tr("modifier.remove"), hint,
                    () -> modifierReady(modifierTarget, modifierSlot) && (modifierConfigured(id) || botModifierCount(id) > 0),
                    () -> changeModifier(modifierTarget, modifierSlot, "remove", id), true);
            label(new Rect(r.x + 8, r.y + 44, r.w - 16, 12), () -> modifierStatus(id), SREPanelStyle.BODY);
        } else {
            button("modifierApply", cell(actions, 0, 2), () -> tr(modifierSelected(id) ? "modifier.remove" : "modifier.add"), hint,
                    () -> modifierReady(modifierTarget, modifierSlot) && (modifierSelected(id)
                            || modifierSelection(modifierTarget, modifierSlot).size() < 16),
                    () -> changeModifier(modifierTarget, modifierSlot, modifierSelected(id) ? "remove" : "add", id), true);
            label(cell(new Rect(actions.x, actions.y + 4, actions.w, 12), 1, 2), () -> modifierStatus(id), SREPanelStyle.BODY);
        }
        Component description = translatedText(entry.getString("description"), P + "modifier.no_description");
        Component text = description.copy().append("\n\n").append(id).append("\n\n").append(tr("modifier.persistence"));
        String key = "modifierDescription:" + id;
        int textY = batch ? 60 : 44;
        add(key, new ScrollText(new Rect(r.x + 8, r.y + textY, r.w - 16, r.h - textY - 4), text, key));
    }

    private Component modifierRowName(CompoundTag entry) {
        String id = entry.getString("id");
        if (modifierTarget.equals("self") && hasModifier(state.getList("myModifiers", Tag.TAG_STRING), id))
            return tr("modifier.applied_entry", modifierName(entry));
        if (modifierTarget.equals("batch") && !modifierConfigured(id) && botModifierCount(id) > 0)
            return tr("modifier.partial_entry", modifierName(entry));
        return modifierConfigured(id) ? tr("modifier.configured_entry", modifierName(entry)) : modifierName(entry);
    }

    private Component modifierStatus(String id) {
        if (!modifierReady(modifierTarget, modifierSlot)) return tr("modifier.target_unavailable");
        if (modifierTarget.equals("self")) return tr("modifier.self_status",
                tr(modifierConfigured(id) ? "modifier.configured" : "modifier.not_configured"),
                tr(hasModifier(state.getList("myModifiers", Tag.TAG_STRING), id) ? "modifier.applied" : "modifier.not_applied"));
        if (modifierTarget.equals("batch")) return tr("modifier.batch_status",
                tr(modifierConfigured(id) ? "modifier.configured" : "modifier.not_configured"),
                botModifierCount(id), state.getList("bots", Tag.TAG_COMPOUND).size());
        return tr(modifierConfigured(id) ? "modifier.configured" : "modifier.not_configured");
    }

    private boolean modifierConfigured(String id) { return hasModifier(modifierIds(modifierTarget, modifierSlot), id); }
    private boolean modifierSelected(String id) { return hasModifier(modifierSelection(modifierTarget, modifierSlot), id); }
    private ListTag modifierSelection(String target, int slot) {
        // 本人请求以实际已应用列表为基底，配置列表仅用于展示持久化状态。
        return target.equals("self") ? state.getList("myModifiers", Tag.TAG_STRING) : modifierIds(target, slot);
    }
    private int botModifierCount(String id) {
        int count = 0;
        for (Tag value : state.getList("bots", Tag.TAG_COMPOUND))
            if (hasModifier(((CompoundTag) value).getList("modifiers", Tag.TAG_STRING), id)) count++;
        return count;
    }
    private boolean hasModifierConfiguration() {
        if (!modifierSelection(modifierTarget, modifierSlot).isEmpty() || !modifierIds(modifierTarget, modifierSlot).isEmpty()) return true;
        if (modifierTarget.equals("batch")) for (Tag value : state.getList("bots", Tag.TAG_COMPOUND))
            if (!((CompoundTag) value).getList("modifiers", Tag.TAG_STRING).isEmpty()) return true;
        return false;
    }
    private boolean modifierReady(String target, int slot) {
        return TrainingClient.hasCatalogue() && (!target.equals("bot") || bot(slot) != null && !bot(slot).getBoolean("pending"));
    }
    private ListTag modifierIds(String target, int slot) {
        if (target.equals("bot")) { CompoundTag entry = bot(slot); return entry == null ? new ListTag() : entry.getList("modifiers", Tag.TAG_STRING); }
        return settings().getList(target.equals("self") ? "myModifiers" : "enemyModifiers", Tag.TAG_STRING);
    }
    private static boolean hasModifier(ListTag list, String id) {
        for (int i = 0; i < list.size(); i++) if (list.getString(i).equals(id)) return true;
        return false;
    }
    private Component modifierTargetName(String target, int slot) { return tr("modifier.target." + target, slot + 1); }
    private Component modifierName(CompoundTag entry) { return translatedText(entry.getString("name"), entry.getString("id")); }
    private static Component translatedText(String value, String fallback) { return Component.translatable(value.isBlank() ? fallback : value); }

    private void confirmClearModifiers() {
        String target = modifierTarget; int slot = modifierSlot;
        confirm(tr("modifier.clear_confirm", modifierTargetName(target, slot)), () -> changeModifier(target, slot, "clear", ""));
    }

    /** 预期列表只用于识别权威确认，绝不乐观改写本地状态；不发送 UUID 或自由输入的 ID。 */
    private boolean changeModifier(String target, int slot, String operation, String id) {
        if (!canAct() || !modifierReady(target, slot)) return false;
        if (!operation.equals("clear") && modifiers.stream().noneMatch(m -> m.getString("id").equals(id))) return false;
        ListTag updated = new ListTag();
        if (!operation.equals("clear")) {
            ListTag current = modifierSelection(target, slot);
            for (int i = 0; i < current.size(); i++) if (!current.getString(i).equals(id)) updated.add(StringTag.valueOf(current.getString(i)));
            if (operation.equals("add")) {
                if (updated.size() >= 16) return false;
                updated.add(StringTag.valueOf(id));
            }
        }
        CompoundTag data = string("target", target); data.putString("operation", operation);
        if (target.equals("bot")) data.putInt("slot", slot);
        if (!operation.equals("clear")) data.putString("id", id);
        CompoundTag changes = new CompoundTag();
        changes.put(target.equals("bot") ? "modifiers" : target.equals("self") ? "myModifiers" : "enemyModifiers", updated);
        return request("modifier", data, target.equals("bot") ? "bot" : "settings", changes, slot);
    }

    private void buildItems(Rect r) {
        search("items", new Rect(r.x, r.y, r.w, 20));
        filter(new Rect(r.x, r.y + 24, r.w, 20), "category", CATEGORIES, () -> category, value -> {
            category = value; pages.put("items", 0); rebuild = true;
        });
        Rect kits = new Rect(r.x, r.y + 48, r.w, 20);
        String[] ids = {"melee", "ranged", "utility"};
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            action("kit" + id, cell(kits, i, 3), "kit." + id, "kit_hint", () -> send("kit", string("id", id)));
        }
        Rect list = new Rect(r.x, r.y + 72, r.w, Math.max(1, r.h - 72));
        List<CatalogueItem> filtered = items.stream().filter(item -> (category.equals("all") || category.equals(item.category))
                && contains(query("items"), item.name.getString(), item.key,
                item.stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(item.stack.getItem()).toString())).toList();
        int columns = list.w >= 700 ? 2 : 1;
        int rows = Math.max(1, (list.h - 24) / 24), capacity = rows * columns;
        int start = page("items", filtered.size(), capacity) * capacity;
        for (int i = start; i < Math.min(filtered.size(), start + capacity); i++) {
            CatalogueItem item = filtered.get(i);
            int local = i - start;
            Rect row = cell(new Rect(list.x, list.y + (local / columns) * 24, list.w, 20), local % columns, columns);
            int giveW = Math.min(54, row.w / 5);
            Rect name = new Rect(row.x, row.y, row.w - giveW * 2 - 8, 20);
            select("item" + item.key, name, () -> item.name, itemTooltip(item), () -> selectedItem.equals(item.key), () -> selectedItem = item.key);
            for (int n = 0; n < 2; n++) {
                int count = n == 0 ? 1 : Math.max(1, item.stack.getMaxStackSize());
                button("give" + item.key + n, new Rect(name.right() + 4 + n * (giveW + 4), row.y, giveW, 20), () -> tr("give_short", count),
                        tr(n == 0 ? "give_one" : "give_stack", count), () -> !item.stack.isEmpty(), () -> give(item, count), true);
            }
        }
        empty(list, filtered.size(), false);
        pager("items", new Rect(list.x, list.bottom() - 20, list.w, 20), filtered.size(), capacity);
    }

    private void give(CatalogueItem item, int count) {
        CompoundTag data = string("key", item.key); data.putInt("count", count); send("give", data);
    }

    private void buildRoles(Rect r, boolean picking) {
        boolean wide = r.w >= 560 && r.h >= 192;
        if (!wide && roleDetail) { buildRoleDetail(r, picking, true); return; }
        String searchId = picking ? "picker" : "roles";
        search(searchId, new Rect(r.x, r.y, r.w, 20));
        filter(new Rect(r.x, r.y + 24, r.w, 20), "team", TEAMS, () -> team, value -> {
            team = value; pages.put(searchId, 0); rebuild = true;
        });
        Rect heading = new Rect(r.x, r.y + 48, r.w, 20);
        if (picking) {
            button("pickerBack", cell(heading, 0, 2), () -> tr("back_to_bots"), null, () -> true, () -> { picker = ""; rebuild = true; }, false);
            label(cell(heading, 1, 2), () -> tr(picker.equals("batch") ? "pick_batch_role" : "pick_bot_role"), SREPanelStyle.GOLD);
        } else {
            label(cell(heading, 0, 3), () -> tr("current_role", roleName(state.getString("myRole"))), SREPanelStyle.BODY);
            action("resetSelf", cell(heading, 1, 3), "reset_self", "reset_self_hint", () -> send("reset_self"));
            modifierEntry("roleModifiers", cell(heading, 2, 3), "self", -1);
        }
        Rect content = new Rect(r.x, r.y + 72, r.w, Math.max(1, r.h - 72));
        Rect list = wide ? new Rect(content.x, content.y, content.w * 2 / 5, content.h) : content;
        if (wide || !roleDetail) {
            List<CompoundTag> filtered = roles.stream().filter(entry -> (team.equals("all") || team.equals(entry.getString("team")))
                    && contains(query(searchId), roleName(entry.getString("id")).getString(), entry.getString("id"), entry.getString("name"))).toList();
            int capacity = Math.max(1, (list.h - 24) / 24);
            int start = page(searchId, filtered.size(), capacity) * capacity;
            for (int i = start; i < Math.min(filtered.size(), start + capacity); i++) {
                CompoundTag entry = filtered.get(i); String id = entry.getString("id");
                select("role" + id, new Rect(list.x, list.y + (i - start) * 24, list.w, 20), () -> roleName(id), roleTooltip(entry),
                        () -> selectedRole.equals(id), () -> { selectedRole = id; roleDetail = true; rebuild = true; });
            }
            empty(list, filtered.size(), false);
            pager(searchId, new Rect(list.x, list.bottom() - 20, list.w, 20), filtered.size(), capacity);
        }
        if (wide || roleDetail) {
            Rect detail = wide ? new Rect(list.right() + 8, content.y, content.w - list.w - 8, content.h) : content;
            // 窄屏详情让出顶部搜索与筛选占用的空间，避免简介只剩一行。
            if (!wide) detail = new Rect(r.x, r.y, r.w, r.h);
            buildRoleDetail(detail, picking, !wide);
        }
    }

    private void buildRoleDetail(Rect r, boolean picking, boolean narrow) {
        cards.add(r);
        int y = r.y + 4;
        if (narrow) {
            button("roleList", new Rect(r.x + 4, y, r.w - 8, 20), () -> tr("back_to_list"), null, () -> true,
                    () -> { roleDetail = false; rebuild = true; }, false);
            y += 24;
        }
        CompoundTag entry = role(selectedRole);
        if (entry == null) { label(new Rect(r.x + 8, y + 4, r.w - 16, 14), () -> tr("select_role"), SREPanelStyle.MUTED); return; }
        Component name = roleName(selectedRole);
        label(new Rect(r.x + 8, y + 3, r.w - 16, 12), () -> name, teamColor(entry.getString("team")));
        y += 16;
        button("applyRole", new Rect(r.x + 4, y, r.w - 8, 20), () -> tr(picking ? "use_role" : "apply_role"), roleTooltip(entry),
                () -> !picking || !picker.equals("bot") || bot(selectedSlot) != null && !bot(selectedSlot).getBoolean("pending"), () -> {
                    if (!picking) send("role", string("id", selectedRole));
                    else {
                        boolean sent = picker.equals("batch") ? configure(string("enemyRole", selectedRole))
                                : botConfigure(string("role", selectedRole));
                        if (sent) { picker = ""; roleDetail = false; rebuild = true; }
                    }
                }, true);
        y += 24;
        Component description = Component.translatable(entry.getString("description").isBlank() ? P + "no_description" : entry.getString("description"));
        if (entry.getBoolean("restricted")) description = tr("restricted_hint").append("\n\n").append(description);
        if (r.bottom() - y >= 12) {
            String key = "description:" + selectedRole;
            ScrollText text = new ScrollText(new Rect(r.x + 8, y, r.w - 16, r.bottom() - y - 4), description, key);
            add(key, text);
        }
    }

    private void buildEnemies(Rect r) {
        boolean narrowDetail = (r.w < 560 || r.h < 212) && enemyTab == 1 && botDetail && bot(selectedSlot) != null;
        int y = r.y;
        if (!narrowDetail) {
            Rect count = new Rect(r.x, y, r.w, 20);
            if (enemyTab == 1) search("bots", count);
            else {
                int[] presets = {0, 1, 5, 10, 32};
                button("countMinus", cell(count, 0, 8), () -> Component.literal("−"), tr("enemy_count"), () -> settings().getInt("enemyCount") > 0,
                        () -> configure(integer("enemyCount", settings().getInt("enemyCount") - 1)), true);
                button("countValue", cell(count, 1, 8), () -> tr("count_value", settings().getInt("enemyCount")), tr("number_hint", 0, 32), () -> false, () -> {}, false);
                button("countPlus", cell(count, 2, 8), () -> Component.literal("+"), tr("enemy_count"), () -> settings().getInt("enemyCount") < 32,
                        () -> configure(integer("enemyCount", settings().getInt("enemyCount") + 1)), true);
                for (int i = 0; i < presets.length; i++) {
                    int value = presets[i];
                    button("preset" + value, cell(count, i + 3, 8), () -> Component.literal(Integer.toString(value)), tr("count_preset", value),
                            () -> settings().getInt("enemyCount") != value, () -> configure(integer("enemyCount", value)), true);
                }
            }
            y += 24;
            Rect quick = new Rect(r.x, y, r.w, 20);
            toggle("paused", cell(quick, 0, 3)); toggle("autoRespawn", cell(quick, 1, 3));
            action("reviveAll", cell(quick, 2, 3), "revive_all", "revive_all_hint", () -> send("revive_bots"));
            y += 24;
        }
        Rect nav = new Rect(r.x, y, r.w, 20);
        select("batchTab", cell(nav, 0, 3), () -> tr("batch"), tr("batch_hint"), () -> enemyTab == 0,
                () -> { enemyTab = 0; botDetail = false; rebuild = true; });
        select("rosterTab", cell(nav, 1, 3), () -> tr(narrowDetail ? "back_to_list" : "roster"), tr("ai_hint"), () -> enemyTab == 1,
                () -> { enemyTab = 1; botDetail = false; rebuild = true; });
        modifierEntry("enemyModifiers", cell(nav, 2, 3), "batch", -1);
        Rect content = new Rect(r.x, y + 24, r.w, Math.max(1, r.bottom() - y - 24));
        if (enemyTab == 0) {
            List<FormRow> rows = new ArrayList<>();
            rows.add(new FormRow("enemyRole", tr("batch_hint"), () -> tr("field_value", tr("enemy_role"), roleName(settings().getString("enemyRole"))), () -> openPicker("batch")));
            rows.add(choiceRow("behavior", "behavior", BEHAVIORS, false));
            rows.add(choiceRow("equipment", "equipment", EQUIPMENT, false));
            rows.add(choiceRow("area", "area", AREAS, false));
            form("batch", content, rows);
        } else buildRoster(content, narrowDetail);
    }

    private void buildRoster(Rect r, boolean narrowDetail) {
        boolean wide = !narrowDetail && r.w >= 560 && r.h >= 140;
        Rect list = wide ? new Rect(r.x, r.y, r.w * 2 / 5, r.h) : r;
        if (!narrowDetail) {
            List<CompoundTag> bots = new ArrayList<>();
            for (Tag value : state.getList("bots", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) value;
                if (contains(query("bots"), botLabel(entry).getString(), entry.getString("name"), entry.getString("uuid"),
                        Integer.toString(entry.getInt("slot") + 1))) bots.add(entry);
            }
            int capacity = Math.max(1, (list.h - 24) / 24), start = page("bots", bots.size(), capacity) * capacity;
            for (int i = start; i < Math.min(bots.size(), start + capacity); i++) {
                int slot = bots.get(i).getInt("slot");
                select("bot" + slot, new Rect(list.x, list.y + (i - start) * 24, list.w, 20), () -> botLabel(bot(slot)), null,
                        () -> selectedSlot == slot, () -> { selectedSlot = slot; botDetail = true; rebuild = true; });
                SreButton row = (SreButton) keyed.get("bot" + slot);
                bindings.add(() -> row.setTooltipText(botTooltip(bot(slot))));
            }
            empty(list, bots.size(), true);
            pager("bots", new Rect(list.x, list.bottom() - 20, list.w, 20), bots.size(), capacity);
        }
        if (wide || narrowDetail) {
            Rect detail = wide ? new Rect(list.right() + 8, r.y, r.w - list.w - 8, r.h) : r;
            buildBotDetail(detail);
        }
    }

    private void buildBotDetail(Rect r) {
        cards.add(r);
        if (bot(selectedSlot) == null) { label(r, () -> tr("select_bot"), SREPanelStyle.MUTED); return; }
        Rect heading = new Rect(r.x + 4, r.y + 3, r.w - 8, 12);
        label(heading, () -> botLabel(bot(selectedSlot)), SREPanelStyle.GOLD);
        Rect actions = new Rect(r.x + 4, r.y + 18, r.w - 8, 20);
        button("revive", cell(actions, 0, 4), () -> tr("revive"), tr("revive_hint"),
                () -> botReady() && !bot(selectedSlot).getBoolean("alive"), () -> botAction("revive", new CompoundTag()), true);
        button("retry", cell(actions, 1, 4), () -> tr("retry"), tr("retry_hint"),
                () -> botReady() && !bot(selectedSlot).getString("error").isBlank(), () -> botAction("retry", new CompoundTag()), true);
        button("remove", cell(actions, 2, 4), () -> tr("remove"), tr("remove_hint"), () -> bot(selectedSlot) != null,
                () -> botAction("remove", new CompoundTag()), true);
        modifierEntry("botModifiers", cell(actions, 3, 4), "bot", selectedSlot);
        List<FormRow> rows = new ArrayList<>();
        rows.add(new FormRow("botRole", tr("choose_role_hint"), () -> tr("field_value", tr("enemy_role"), roleName(botString("role"))), () -> openPicker("bot")));
        rows.add(choiceRow("behavior", "behavior", BEHAVIORS, true));
        rows.add(choiceRow("equipment", "equipment", EQUIPMENT, true));
        Rect fields = new Rect(r.x + 4, r.y + 42, r.w - 8, Math.max(1, r.h - 46));
        form("botFields", fields, rows);
        // 错误全文也在假人短名的 tooltip；不把 UUID 或内部账号放进布局轨道。
    }

    private FormRow choiceRow(String label, String field, String[] values, boolean single) {
        return new FormRow((single ? "bot" : "batch") + field, tr(single ? "instant_hint" : "batch_hint"),
                () -> tr("field_value", tr(label), tr(label + "." + (single ? botString(field) : settings().getString(field)))), () -> {
            String current = single ? botString(field) : settings().getString(field);
            int index = 0;
            for (int i = 0; i < values.length; i++) if (values[i].equals(current)) index = i;
            CompoundTag change = string(field, values[(index + 1) % values.length]);
            if (single) botConfigure(change); else configure(change);
        });
    }

    private void openPicker(String target) {
        if (!TrainingClient.hasCatalogue()) { localMessage = "loading"; return; }
        picker = target; selectedRole = target.equals("batch") ? settings().getString("enemyRole") : botString("role");
        roleDetail = false; rebuild = true;
    }

    private void buildSettings(Rect r) {
        String[] sections = {"session_settings", "teleports", "maintenance"};
        Rect nav = new Rect(r.x, r.y, r.w, 20);
        for (int i = 0; i < sections.length; i++) {
            int index = i;
            select("settingsTab" + i, cell(nav, i, 3), () -> tr(sections[index]), null, () -> settingsTab == index,
                    () -> { settingsTab = index; rebuild = true; });
        }
        Rect content = new Rect(r.x, r.y + 24, r.w, Math.max(1, r.h - 24));
        if (settingsTab == 0) {
            List<FormRow> rows = new ArrayList<>();
            for (String field : new String[]{"unlimitedStamina", "speedBoost", "noCooldown", "paused", "autoRespawn", "friendlyFirePenalty"})
                rows.add(new FormRow("toggle:" + field, tr(toggleHint(field)), () -> tr(field), () -> {}));
            rows.add(new FormRow("delay", tr("number_hint", 1, 30), () -> tr("delay_value", settings().getInt("respawnDelay")), () -> {}));
            rows.add(new FormRow("hitRate", tr("bot_hit_rate_hint"),
                    () -> tr("bot_hit_rate", settings().getInt("botHitRate")), () -> {}));
            form("rules", content, rows);
        } else {
            List<FormRow> rows = new ArrayList<>();
            if (settingsTab == 1) {
                for (String area : new String[]{"lobby", "range", "arena", "train"})
                    rows.add(new FormRow("area" + area, tr("teleport_hint"), () -> tr("area." + area), () -> send("teleport", string("area", area))));
            } else {
                rows.add(new FormRow("spawn_body", tr("spawn_body_hint"), () -> tr("spawn_body"), () -> send("spawn_body")));
                rows.add(new FormRow("cleanup", tr("cleanup_hint"), () -> tr("cleanup"), () -> send("cleanup")));
                rows.add(new FormRow("reset", tr("reset_hint"), () -> tr("reset"), () -> confirm("reset_confirm", () -> send("reset"))));
                rows.add(new FormRow("rebuild", tr(TrainingClient.isDedicated() ? "server_rebuild" : "rebuild_hint"), () -> tr("rebuild"),
                        () -> { if (!TrainingClient.isDedicated()) TrainingWorldLauncher.rebuild(this); }));
            }
            form("settings" + settingsTab, content, rows);
        }
    }

    /** 固定轨道分页：最小 GUI 高度也不会把配置按钮推到页脚或依靠裁剪掩盖重叠。 */
    private void form(String id, Rect r, List<FormRow> rows) {
        int columns = r.w >= 480 ? 2 : 1;
        int fullCapacity = Math.max(1, r.h / 24) * columns;
        boolean paginate = rows.size() > fullCapacity;
        int capacity = paginate ? Math.max(1, (r.h - 24) / 24) * columns : fullCapacity;
        int start = page(id, rows.size(), capacity) * capacity;
        for (int i = start; i < Math.min(rows.size(), start + capacity); i++) {
            FormRow row = rows.get(i); int local = i - start;
            Rect pos = cell(new Rect(r.x, r.y + (local / columns) * 24, r.w, 20), local % columns, columns);
            if (row.id.startsWith("toggle:")) toggle(row.id.substring(7), pos);
            else if (row.id.equals("delay")) {
                button("delayMinus", new Rect(pos.x, pos.y, 24, 20), () -> Component.literal("−"), row.hint,
                        () -> settings().getInt("respawnDelay") > 1, () -> configure(integer("respawnDelay", settings().getInt("respawnDelay") - 1)), true);
                button("delayValue", new Rect(pos.x + 28, pos.y, pos.w - 56, 20), row.text, row.hint, () -> false, () -> {}, false);
                button("delayPlus", new Rect(pos.right() - 24, pos.y, 24, 20), () -> Component.literal("+"), row.hint,
                        () -> settings().getInt("respawnDelay") < 30, () -> configure(integer("respawnDelay", settings().getInt("respawnDelay") + 1)), true);
            } else if (row.id.equals("hitRate")) botHitRateInput(pos);
            else button(row.id, pos, row.text, row.hint,
                    () -> (!row.id.equals("rebuild") || !TrainingClient.isDedicated()) && (!id.equals("botFields") || botReady()), row.run, true);
        }
        if (paginate) pager(id, new Rect(r.x, r.bottom() - 20, r.w, 20), rows.size(), capacity);
    }

    private static String toggleHint(String field) {
        if (field.equals("noCooldown")) return "no_cooldown_hint";
        return field.equals("unlimitedStamina") || field.equals("speedBoost") ? "self_effects_hint" : "instant_hint";
    }

    private void toggle(String field, Rect r) {
        SreSwitchButton widget = new SreSwitchButton(font, tr(field), () -> SwitchState.of(settings().getBoolean(field)), null,
                b -> { CompoundTag data = new CompoundTag(); data.putBoolean(field, !settings().getBoolean(field)); configure(data); });
        widget.setTooltipText(tr(toggleHint(field)));
        widget.at(r.x, r.y, r.w, r.h);
        add("toggle" + field, widget);
        bindings.add(() -> widget.active = canAct());
    }

    private void search(String id, Rect r) {
        EditBox box = searches.get(id);
        if (box == null) {
            box = new EditBox(font, r.x, r.y, r.w, r.h, tr("search"));
            box.setMaxLength(256);
            box.setTextColor(SREPanelStyle.TEXT);
            box.setResponder(value -> { pages.put(id, 0); rebuild = true; });
            searches.put(id, box);
        }
        box.setX(r.x); box.setY(r.y); box.setWidth(r.w); box.setHeight(r.h);
        Component hint = tr(id.startsWith("modifiers:") ? "modifier.search_hint" : "search_hint");
        box.setHint(SREPanelStyle.hint(Component.literal(MapUiGraphics.clip(font, hint.getString(), r.w - 12))));
        box.setTooltip(Tooltip.create(hint));
        add("search" + id, box);
    }

    private void botHitRateInput(Rect r) {
        int buttonW = Math.min(44, r.w / 4);
        int inputW = Math.min(56, r.w / 3);
        int labelW = Math.max(1, r.w - inputW - buttonW - 8);
        label(new Rect(r.x, r.y + 5, labelW, 12),
                () -> tr("bot_hit_rate", settings().getInt("botHitRate")), SREPanelStyle.BODY);
        Rect input = new Rect(r.x + labelW + 4, r.y, inputW, 20);
        EditBox box = numberInputs.get("botHitRate");
        if (box == null) {
            box = new EditBox(font, input.x, input.y, input.w, input.h, tr("bot_hit_rate")) {
                @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                    if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                        submitBotHitRate();
                        return true;
                    }
                    return super.keyPressed(keyCode, scanCode, modifiers);
                }
            };
            box.setMaxLength(3);
            box.setFilter(value -> value.matches("[0-9]*"));
            box.setTextColor(SREPanelStyle.TEXT);
            box.setTooltip(Tooltip.create(tr("bot_hit_rate_hint")));
            numberInputs.put("botHitRate", box);
            syncHitRateInput();
        }
        box.setX(input.x); box.setY(input.y); box.setWidth(input.w); box.setHeight(input.h);
        add("hitRateInput", box);
        button("hitRateApply", new Rect(r.right() - buttonW, r.y, buttonW, 20),
                () -> tr("bot_hit_rate_apply"), tr("bot_hit_rate_hint"), () -> true, this::submitBotHitRate, true);
    }

    private void submitBotHitRate() {
        EditBox box = numberInputs.get("botHitRate");
        if (box == null) return;
        int current = settings().getInt("botHitRate");
        int value;
        try { value = Integer.parseInt(box.getValue().trim()); }
        catch (NumberFormatException ignored) { value = current; }
        value = Math.clamp(value, 0, 100);
        box.setValue(Integer.toString(value));
        shownHitRate = value;
        if (value != current && !configure(integer("botHitRate", value))) {
            shownHitRate = -1;
            syncHitRateInput();
        }
    }

    private void syncHitRateInput() {
        EditBox box = numberInputs.get("botHitRate");
        int value = settings().getInt("botHitRate");
        if (box == null || shownHitRate == value) return;
        box.setValue(Integer.toString(value));
        shownHitRate = value;
    }

    private void filter(Rect r, String prefix, String[] values, Supplier<String> value, java.util.function.Consumer<String> change) {
        for (int i = 0; i < values.length; i++) {
            String option = values[i];
            select("filter" + option, cell(r, i, values.length), () -> tr(prefix + "." + option), null,
                    () -> value.get().equals(option), () -> change.accept(option));
        }
    }

    private int page(String id, int count, int capacity) {
        int result = Math.max(0, Math.min(pages.getOrDefault(id, 0), Math.max(0, (count - 1) / capacity)));
        pages.put(id, result); return result;
    }

    private void pager(String id, Rect r, int count, int capacity) {
        int total = Math.max(1, (count + capacity - 1) / capacity), current = page(id, count, capacity);
        pagers.put(id, new Pager(new Rect(r.x, body.y, r.w, r.bottom() - body.y), count, capacity));
        button("prev" + id, new Rect(r.x, r.y, 32, 20), () -> Component.literal("‹"), tr("previous_page"), () -> current > 0,
                () -> { pages.put(id, current - 1); rebuild = true; }, false);
        label(new Rect(r.x + 36, r.y + 6, r.w - 72, 12), () -> tr("page", current + 1, total, count), SREPanelStyle.MUTED);
        button("next" + id, new Rect(r.right() - 32, r.y, 32, 20), () -> Component.literal("›"), tr("next_page"), () -> current + 1 < total,
                () -> { pages.put(id, current + 1); rebuild = true; }, false);
    }

    private void empty(Rect r, int count, boolean bots) {
        if (count != 0) return;
        label(new Rect(r.x + 4, r.y + 5, r.w - 8, Math.max(12, r.h - 28)),
                () -> tr(bots && state.getList("bots", Tag.TAG_COMPOUND).isEmpty() ? "no_bots" : TrainingClient.hasCatalogue() ? "empty" : "loading"), SREPanelStyle.MUTED);
    }

    private void action(String id, Rect r, String name, String hint, Runnable run) {
        button(id, r, () -> tr(name), tr(hint), () -> true, run, true);
    }

    private void button(String id, Rect r, Supplier<Component> text, Component hint, BooleanSupplier enabled, Runnable run, boolean authority) {
        SreButton widget = new FocusButton(text.get(), run, () -> false);
        widget.setX(r.x); widget.setY(r.y); widget.setWidth(Math.max(1, r.w)); widget.setHeight(Math.max(1, r.h));
        widget.setTooltipText(hint);
        add(id, widget);
        bindings.add(() -> {
            Component value = text.get();
            if (!widget.getMessage().equals(value)) widget.setMessage(value);
            widget.active = enabled.getAsBoolean() && (!authority || canAct());
        });
    }

    private void select(String id, Rect r, Supplier<Component> text, Component hint, BooleanSupplier selected, Runnable run) {
        FocusButton widget = new FocusButton(text.get(), run, selected);
        widget.setX(r.x); widget.setY(r.y); widget.setWidth(Math.max(1, r.w)); widget.setHeight(Math.max(1, r.h));
        widget.setTooltipText(hint); add(id, widget);
        bindings.add(() -> { Component value = text.get(); if (!widget.getMessage().equals(value)) widget.setMessage(value); });
    }

    private <T extends AbstractWidget> T add(String id, T widget) { keyed.put(id, widget); return addRenderableWidget(widget); }
    private void label(Rect r, Supplier<Component> text, int color) { labels.add(new Label(r, text, color)); }
    private void updateBindings() { for (Runnable binding : bindings) binding.run(); }
    private boolean canAct() { return TrainingClient.isTraining() && !awaiting && !uncertain && !TrainingWorldLauncher.isBusy(); }
    private boolean botReady() { CompoundTag bot = bot(selectedSlot); return bot != null && !bot.getBoolean("pending"); }
    private String botString(String field) { CompoundTag bot = bot(selectedSlot); return bot == null ? "" : bot.getString(field); }
    private CompoundTag settings() { return state.getCompound("settings"); }
    private String query(String id) { EditBox box = searches.get(id); return box == null ? "" : box.getValue(); }

    private void refresh() { CompoundTag data = new CompoundTag(); data.putBoolean("full", true); send("snapshot", data); }
    private boolean configure(CompoundTag change) { return request("configure", change, "settings", change, -1); }
    private boolean botConfigure(CompoundTag change) { return botAction("configure", change); }
    private boolean botAction(String operation, CompoundTag change) {
        CompoundTag data = change.copy(); data.putInt("slot", selectedSlot); data.putString("operation", operation);
        return request("bot", data, operation.equals("configure") ? "bot" : "", change, selectedSlot);
    }
    private boolean send(String action) { return send(action, new CompoundTag()); }
    private boolean send(String action, CompoundTag data) { return request(action, data, "", new CompoundTag(), -1); }
    private boolean request(String action, CompoundTag data, String target, CompoundTag changes, int slot) {
        if (awaiting || uncertain && !action.equals("snapshot")) return false;
        if (!TrainingClient.send(action, data)) { localMessage = "unavailable"; return false; }
        awaiting = true; expectedTarget = target; expected = changes.copy(); expectedSlot = slot;
        sentAt = Util.getMillis(); localMessage = "waiting"; updateBindings(); return true;
    }

    private void confirm(String key, Runnable yes) { confirm(tr(key), yes); }

    private void confirm(Component message, Runnable yes) {
        minecraft.setScreen(new ConfirmScreen(result -> {
            minecraft.setScreen(this);
            if (result && canAct()) yes.run();
        }, tr("confirm_title"), message));
    }

    @Override public void onClose() {
        if (!modifierTarget.isEmpty()) {
            if (modifierDetail && (body.w < 560 || body.h < 192)) modifierDetail = false;
            else modifierTarget = "";
            rebuild = true; return;
        }
        if (!picker.isEmpty()) {
            if (roleDetail && (body.w < 560 || body.h < 192)) roleDetail = false;
            else { picker = ""; roleDetail = false; }
            rebuild = true; return;
        }
        if (tab == ROLES && roleDetail && (body.w < 560 || body.h < 192)) { roleDetail = false; rebuild = true; return; }
        if (tab == ENEMIES && botDetail && (body.w < 560 || body.h < 212)) { botDetail = false; rebuild = true; return; }
        minecraft.setScreen(TrainingClient.isTraining() ? parent : null);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() {
        if (revision != TrainingClient.revision()) syncState();
        if (awaiting && Util.getMillis() - sentAt >= 10_000L) { awaiting = false; uncertain = true; localMessage = "timeout"; }
        updateBindings();
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (rebuild) build();
        updateBindings();
        super.render(g, mouseX, mouseY, partialTick);
        for (Label label : labels) {
            int y = label.rect.y;
            for (FormattedCharSequence line : font.split(label.text.get(), Math.max(1, label.rect.w))) {
                if (y + font.lineHeight > label.rect.bottom()) break;
                g.drawString(font, line, label.rect.x, y, label.color, false); y += 11;
            }
        }
        Component feedback = status();
        int color = !TrainingClient.isTraining() || uncertain || localMessage.equals("unavailable") || error(state.getString("message")) || hasBotError()
                ? SREPanelStyle.RED : awaiting ? SREPanelStyle.GOLD : SREPanelStyle.GREEN;
        String shown = MapUiGraphics.clip(font, feedback.getString(), panel.w - 24);
        g.drawString(font, shown, panel.x + 12, panel.bottom() - 15, color, false);
        if (mouseY >= panel.bottom() - 22 && mouseY <= panel.bottom() && mouseX >= panel.x && mouseX <= panel.right())
            g.renderTooltip(font, feedback.copy().append("\n").append(tr("summary", state.getInt("alive"), state.getInt("pending"), roleName(state.getString("myRole")))), mouseX, mouseY);
        for (Label label : labels) if (label.rect.contains(mouseX, mouseY)
                && font.wordWrapHeight(label.text.get(), Math.max(1, label.rect.w)) > label.rect.h)
            g.renderTooltip(font, label.text.get(), mouseX, mouseY);
    }

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, SREPanelStyle.SCREEN_BG_TOP, SREPanelStyle.SCREEN_BG_BOTTOM);
        if (panel == null) return;
        SREPanelStyle.drawPanel(g, panel.x, panel.y, panel.w, panel.h);
        g.fill(panel.x + 8, panel.bottom() - 22, panel.right() - 8, panel.bottom() - 21, SREPanelStyle.BORDER);
        for (Rect card : cards) SREPanelStyle.drawPanel(g, card.x, card.y, card.w, card.h);
    }

    private Component status() {
        if (!TrainingClient.isTraining()) return tr("not_training");
        if (!localMessage.isBlank()) return tr(localMessage);
        if (hasBotError()) return Component.translatable(botString("error"));
        String message = state.getString("message");
        return message.isBlank() ? tr("ready") : Component.translatable(message);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            List<AbstractWidget> controls = keyed.values().stream().filter(w -> w.active && w.visible).toList();
            if (controls.isEmpty()) return true;
            int index = controls.indexOf(getFocused());
            AbstractWidget next = controls.get(index < 0 ? (hasShiftDown() ? controls.size() - 1 : 0)
                    : Math.floorMod(index + (hasShiftDown() ? -1 : 1), controls.size()));
            setFocused(next); next.setFocused(true); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean hasBotError() { return tab == ENEMIES && picker.isEmpty() && modifierTarget.isEmpty() && enemyTab == 1 && !botString("error").isBlank(); }

    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (super.mouseScrolled(mx, my, horizontal, vertical)) return true;
        if (vertical == 0) return false;
        for (var entry : pagers.entrySet()) if (entry.getValue().rect.contains(mx, my)) {
            Pager pager = entry.getValue();
            int current = page(entry.getKey(), pager.count, pager.capacity);
            int next = Math.max(0, Math.min(current + (vertical > 0 ? -1 : 1), Math.max(0, (pager.count - 1) / pager.capacity)));
            if (current != next) { pages.put(entry.getKey(), next); rebuild = true; }
            return true;
        }
        return false;
    }

    private Component itemTooltip(CatalogueItem item) {
        MutableComponent tip = item.name.copy().append("\n").append(item.key);
        if (item.stack.isEmpty()) tip.append("\n").append(tr("invalid_item"));
        else for (Component line : Screen.getTooltipFromItem(minecraft, item.stack)) tip.append("\n").append(line);
        return tip;
    }
    private CompoundTag role(String id) { return roles.stream().filter(r -> r.getString("id").equals(id)).findFirst().orElse(null); }
    private CompoundTag bot(int slot) {
        for (Tag value : state.getList("bots", Tag.TAG_COMPOUND)) if (((CompoundTag) value).getInt("slot") == slot) return (CompoundTag) value;
        return null;
    }
    private Component roleName(String id) {
        var registered = RoleUtils.getRole(id);
        if (registered != null) return registered.getName();
        CompoundTag entry = role(id);
        return entry != null && !entry.getString("name").isBlank() ? Component.literal(entry.getString("name")) : tr("unknown_role");
    }
    private Component roleTooltip(CompoundTag entry) {
        MutableComponent tip = roleName(entry.getString("id")).copy().append(" · ").append(tr("team." + entry.getString("team")))
                .append("\n").append(entry.getString("id"));
        if (entry.getBoolean("restricted")) tip.append("\n").append(tr("restricted_hint"));
        return tip;
    }
    private Component botLabel(CompoundTag entry) {
        if (entry == null) return tr("select_bot");
        String status = !entry.getString("error").isBlank() ? "failed" : entry.getBoolean("pending") ? "pending" : entry.getBoolean("alive") ? "alive" : "dead";
        return tr("bot_short", entry.getInt("slot") + 1, tr("bot_status." + status));
    }
    private Component botTooltip(CompoundTag entry) {
        if (entry == null) return tr("select_bot");
        MutableComponent tip = botLabel(entry).copy().append("\n").append(roleName(entry.getString("role")))
                .append("\n").append(tr("behavior." + entry.getString("behavior"))).append(" / ").append(tr("equipment." + entry.getString("equipment")));
        if (!entry.getString("error").isBlank()) tip.append("\n").append(Component.translatable(entry.getString("error")));
        return tip.append("\n").append(entry.getString("name")).append("\n").append(entry.getString("uuid"));
    }
    private static boolean matches(CompoundTag actual, CompoundTag expected) {
        if (actual == null) return false;
        for (String key : expected.getAllKeys()) {
            if (key.equals("myModifiers") || key.equals("enemyModifiers") || key.equals("modifiers")) {
                // 修饰符是集合；服务端排序不应使一次已成功的请求被误判成超时。
                ListTag wanted = expected.getList(key, Tag.TAG_STRING), received = actual.getList(key, Tag.TAG_STRING);
                if (wanted.size() != received.size()) return false;
                for (int i = 0; i < wanted.size(); i++) if (!hasModifier(received, wanted.getString(i))) return false;
            } else if (!expected.get(key).equals(actual.get(key))) return false;
        }
        return true;
    }
    private static boolean error(String message) { return message.contains(".error.") || message.startsWith("training.error."); }
    private static boolean contains(String search, String... values) {
        String query = search.strip().toLowerCase(Locale.ROOT);
        for (String value : values) if (value.toLowerCase(Locale.ROOT).contains(query)) return true;
        return false;
    }
    private static int teamColor(String team) {
        return switch (team) { case "killer" -> 0xFFCC2233; case "innocent" -> 0xFF44BB66; case "sheriff" -> 0xFF22BBCC; default -> 0xFFCCAA22; };
    }
    private static CompoundTag string(String key, String value) { CompoundTag tag = new CompoundTag(); tag.putString(key, value); return tag; }
    private static CompoundTag integer(String key, int value) { CompoundTag tag = new CompoundTag(); tag.putInt(key, value); return tag; }
    private static MutableComponent tr(String key, Object... args) { return Component.translatable(P + key, args); }
    private static Rect cell(Rect row, int i, int count) {
        int start = row.w * i / count, end = row.w * (i + 1) / count;
        return new Rect(row.x + start, row.y, Math.max(1, end - start - (i + 1 == count ? 0 : 4)), row.h);
    }
    private record Rect(int x, int y, int w, int h) {
        int right() { return x + w; } int bottom() { return y + h; }
        boolean contains(double mx, double my) { return mx >= x && mx < right() && my >= y && my < bottom(); }
    }
    private record Pager(Rect rect, int count, int capacity) { }
    private record CatalogueItem(String key, String category, ItemStack stack, Component name) { }
    private record FormRow(String id, Component hint, Supplier<Component> text, Runnable run) { }
    private record Label(Rect rect, Supplier<Component> text, int color) { }

    private final class FocusButton extends SreButton {
        private final BooleanSupplier selected;
        FocusButton(Component text, Runnable run, BooleanSupplier selected) {
            super(TrainingScreen.this.font, text, b -> run.run()); this.selected = selected;
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
            super.renderWidget(g, mx, my, delta);
            if (isFocused() || selected.getAsBoolean()) g.renderOutline(getX(), getY(), getWidth(), getHeight(), SREPanelStyle.GOLD);
            if (selected.getAsBoolean()) g.fill(getX() + 1, getBottom() - 3, getRight() - 1, getBottom() - 1, SREPanelStyle.GOLD);
        }
    }

    /** 简介拥有独立滚动与键盘焦点；快照更新不重建它，也不重置阅读位置。 */
    private final class ScrollText extends AbstractWidget {
        private final List<FormattedCharSequence> lines;
        private final String id;
        private final int visibleLines;
        ScrollText(Rect r, Component text, String id) {
            super(r.x, r.y, r.w, Math.max(12, r.h), text);
            this.id = id; lines = font.split(text, Math.max(1, r.w - 10)); visibleLines = Math.max(1, getHeight() / 11);
        }
        private int offset() { return Math.max(0, Math.min(pages.getOrDefault(id, 0), Math.max(0, lines.size() - visibleLines))); }
        private void scroll(int delta) { pages.put(id, Math.max(0, Math.min(offset() + delta, Math.max(0, lines.size() - visibleLines)))); }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
            g.enableScissor(getX(), getY(), getRight(), getBottom());
            int start = offset();
            for (int i = start; i < Math.min(lines.size(), start + visibleLines); i++) g.drawString(font, lines.get(i), getX(), getY() + (i - start) * 11, SREPanelStyle.BODY, false);
            if (lines.size() > visibleLines) {
                g.fill(getRight() - 4, getY(), getRight() - 1, getBottom(), SREPanelStyle.SCROLL_TRACK);
                int thumb = Math.min(getHeight(), Math.max(8, getHeight() * visibleLines / lines.size()));
                int y = getY() + (getHeight() - thumb) * start / Math.max(1, lines.size() - visibleLines);
                g.fill(getRight() - 4, y, getRight() - 1, y + thumb, SREPanelStyle.GOLD);
            }
            g.disableScissor();
            if (isFocused()) g.renderOutline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, SREPanelStyle.GOLD);
        }
        @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) { scroll(vertical > 0 ? -3 : 3); return true; }
        @Override public boolean keyPressed(int key, int scan, int modifiers) {
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_PAGE_UP) { scroll(key == GLFW.GLFW_KEY_UP ? -1 : -visibleLines); return true; }
            if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_PAGE_DOWN) { scroll(key == GLFW.GLFW_KEY_DOWN ? 1 : visibleLines); return true; }
            return super.keyPressed(key, scan, modifiers);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
}
