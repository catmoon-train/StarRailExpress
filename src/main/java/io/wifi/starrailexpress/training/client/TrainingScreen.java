package io.wifi.starrailexpress.training.client;

import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import io.wifi.starrailexpress.client.gui.screen.CustomEditorScreen;
import io.wifi.starrailexpress.client.gui.screen.EditorLayout;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics;
import io.wifi.starrailexpress.client.gui.widget.SreButton;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.utils.RoleUtils;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 原生训练控制台：复用现有编辑器的折行布局、滚动裁剪、焦点高亮和三态控件。 */
@Environment(EnvType.CLIENT)
public final class TrainingScreen extends CustomEditorScreen {
    private static final String P = "training.sre.ui";
    private static final String[] TABS = {"items", "roles", "enemies", "settings"};
    private static final String[] CATEGORIES = {"all", "weapons", "ammo", "consumables", "functional", "role"};
    private static final String[] TEAMS = {"all", "killer", "innocent", "sheriff", "neutral"};
    private static final String[] BEHAVIORS = {"stationary", "moving", "combat"};
    private static final String[] EQUIPMENT = {"knife", "gun"};
    private static final String[] AREAS = {"range", "arena", "train"};
    private final Screen parent;
    private CompoundTag state;
    private CompoundTag draft;
    private boolean dirty;
    private boolean awaiting;
    private boolean requestedCatalogue;
    private long sentAt;
    private long stateRevision = -1;
    private String localMessage = "";
    private String countText;
    private String delayText;
    private String itemSearch = "", roleSearch = "", botSearch = "";
    private String category = "all", team = "all";
    private String selectedItem = "", selectedRole = "";
    private int selectedSlot = -1;
    private CompoundTag botDraft = new CompoundTag();
    private boolean botEdited;
    private String pickingRole = "";
    private final List<CatalogueItem> items = new ArrayList<>();
    private final List<CompoundTag> roles = new ArrayList<>();
    private final List<Control> guarded = new ArrayList<>();
    private final List<AbstractWidget> contentControls = new ArrayList<>();
    private final List<NumericControl> numbers = new ArrayList<>();

    public TrainingScreen(Screen parent) {
        super(tr("title"));
        this.parent = parent;
        state = TrainingClient.state();
        readDraft();
    }

    @Override protected String translationPrefix() { return P; }
    @Override protected String[] tabKeys() { return TABS; }
    @Override protected EditorLayout.Config layoutConfig() {
        return EditorLayout.Config.defaults().labelColumn(120).headerExtra(24);
    }

    @Override protected void init() {
        if (stateRevision != TrainingClient.revision()) syncState();
        super.init();
        updateControls();
        if (!TrainingClient.hasCatalogue() && !requestedCatalogue) {
            requestedCatalogue = true;
            onOpenManage();
        }
    }

    void acceptState() {
        syncState();
        requestRebuild();
    }

    private void syncState() {
        state = TrainingClient.state();
        stateRevision = TrainingClient.revision();
        awaiting = false;
        localMessage = "";
        // 批量应用只提交部分字段时，不能把另一页未提交的草稿一起抹掉。
        if (validDraft() && matches(state.getCompound("settings"), draft)) dirty = false;
        CompoundTag selected = bot(selectedSlot);
        if (selected == null) { selectedSlot = -1; botEdited = false; }
        else {
            if (matches(selected, botDraft)) botEdited = false;
            if (!botEdited) botDraft = botConfiguration(selected);
        }
        if (!dirty) readDraft();
    }

    private static boolean matches(CompoundTag actual, CompoundTag expected) {
        for (String key : expected.getAllKeys()) {
            if (!expected.get(key).equals(actual.get(key))) return false;
        }
        return true;
    }

    private void readDraft() {
        draft = new CompoundTag();
        CompoundTag actual = state.getCompound("settings");
        for (String key : new String[]{"enemyCount", "enemyRole", "behavior", "equipment", "area", "paused", "autoRespawn", "respawnDelay", "friendlyFirePenalty"}) {
            if (actual.contains(key)) draft.put(key, actual.get(key).copy());
        }
        if (!draft.contains("enemyCount")) draft.putInt("enemyCount", 5);
        if (!draft.contains("enemyRole")) draft.putString("enemyRole", "trainmurdermystery:killer");
        if (!draft.contains("behavior")) draft.putString("behavior", "stationary");
        if (!draft.contains("equipment")) draft.putString("equipment", "knife");
        if (!draft.contains("area")) draft.putString("area", "range");
        if (!draft.contains("autoRespawn")) draft.putBoolean("autoRespawn", true);
        if (!draft.contains("respawnDelay")) draft.putInt("respawnDelay", 3);
        countText = Integer.toString(draft.getInt("enemyCount"));
        delayText = Integer.toString(draft.getInt("respawnDelay"));
        dirty = false;
    }

    @Override protected void buildTab(int tab) {
        guarded.clear();
        contentControls.clear();
        numbers.clear();
        readCatalogues();
        switch (tab) {
            case 0 -> buildItems();
            case 1 -> buildRoles();
            case 2 -> { if (pickingRole.isEmpty()) buildEnemies(); else buildRolePicker(); }
            default -> buildSettings();
        }
    }

    private void readCatalogues() {
        items.clear();
        roles.clear();
        for (Tag value : state.getList("items", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) value;
            ItemStack stack = ItemStack.EMPTY;
            if (minecraft.level != null) {
                try { stack = ItemStack.parseOptional(minecraft.level.registryAccess(), tag.getCompound("stack")); }
                catch (RuntimeException ignored) { /* 损坏条目保持可见并禁用，不能阻断整个面板。 */ }
            }
            Component name = stack.isEmpty() ? Component.literal(tag.getString("name").isBlank()
                    ? tag.getString("key") : tag.getString("name")) : stack.getHoverName();
            items.add(new CatalogueItem(tag.getString("key"), tag.getString("category"), stack, name));
        }
        for (Tag value : state.getList("roles", Tag.TAG_COMPOUND)) roles.add((CompoundTag) value);
    }

    private int search(int r, String label, String value, Consumer<String> setter) {
        return field(r, P + "." + label, value, LIMIT_NAME, SREPanelStyle.hint(tr("search_hint")), v -> {
            setter.accept(v);
            resetScroll();
            requestRebuild();
        });
    }

    private int filters(int r, String prefix, String[] options, String selected, Consumer<String> setter) {
        List<Cell> cells = new ArrayList<>();
        for (String option : options) {
            SelectionButton button = new SelectionButton(tr(prefix + "." + option), option.equals(selected), () -> {
                setter.accept(option);
                resetScroll();
                requestRebuild();
            });
            cells.add(cell(button, Math.min(100, Math.max(48, font.width(button.getMessage()) + 14)), 0, 1F));
        }
        return cluster(r, null, cells.toArray(Cell[]::new));
    }

    private void buildItems() {
        int r = search(0, "search", itemSearch, v -> itemSearch = v);
        r = filters(r, "category", CATEGORIES, category, v -> category = v);
        r = cluster(r, null,
                action("kit.melee", "kit_hint", () -> send("kit", string("id", "melee"))),
                action("kit.ranged", "kit_hint", () -> send("kit", string("id", "ranged"))),
                action("kit.utility", "kit_hint", () -> send("kit", string("id", "utility"))));
        CatalogueItem selected = items.stream().filter(i -> i.key.equals(selectedItem)).findFirst().orElse(null);
        if (selected != null) {
            r = section(r, P + ".selected_item");
            r = note(r, selected.name, SREPanelStyle.TEXT, 8);
            r = note(r, Component.literal(selected.key), SREPanelStyle.MUTED, 4);
            if (selected.stack.isEmpty()) r = note(r, P + ".invalid_item", SREPanelStyle.RED);
            r = cluster(r, null,
                    action(tr("give_one"), tr("give_hint"), () -> !selected.stack.isEmpty(), () -> give(selected, 1)),
                    action(tr("give_stack", Math.max(1, selected.stack.getMaxStackSize())), tr("give_hint"),
                            () -> !selected.stack.isEmpty(), () -> give(selected, selected.stack.getMaxStackSize())));
        }
        r = section(r, P + ".catalogue");
        int found = 0;
        for (CatalogueItem item : items) {
            String id = item.stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(item.stack.getItem()).toString();
            if ((!category.equals("all") && !category.equals(item.category))
                    || !contains(itemSearch, item.name.getString(), item.key, id)) continue;
            MutableComponent tooltip = item.name.copy().append("\n").append(item.key);
            if (!item.stack.isEmpty()) {
                for (Component line : Screen.getTooltipFromItem(minecraft, item.stack)) tooltip.append("\n").append(line);
            }
            r = cluster(r, null, selection(item.name, tooltip, item.key.equals(selectedItem), () -> {
                selectedItem = item.key;
                resetScroll();
                requestRebuild();
            }));
            found++;
        }
        empty(r, found);
    }

    private void give(CatalogueItem item, int count) {
        CompoundTag data = string("key", item.key);
        data.putInt("count", Math.max(1, count));
        send("give", data);
    }

    private void buildRoles() {
        int r = note(0, tr("current_role", roleName(state.getString("myRole"))), SREPanelStyle.GOLD, 4);
        r = cluster(r, null,
                action("apply_role", "apply_role_hint", () -> send("role", string("id", selectedRole)), () -> role(selectedRole) != null),
                action("reset_self", "reset_self_hint", () -> send("reset_self")),
                action("heal", "heal_hint", () -> send("heal")),
                action("money", "money_hint", () -> send("money")));
        CompoundTag selected = role(selectedRole);
        if (selected != null) r = roleDetails(r, selected);
        r = search(r, "search", roleSearch, v -> roleSearch = v);
        r = filters(r, "team", TEAMS, team, v -> team = v);
        int found = 0;
        for (CompoundTag entry : roles) {
            if (!matchesRole(entry)) continue;
            String id = entry.getString("id");
            r = cluster(r, null, selection(roleLabel(entry), roleTooltip(entry), id.equals(selectedRole), () -> {
                selectedRole = id;
                resetScroll();
                requestRebuild();
            }));
            found++;
        }
        empty(r, found);
    }

    private int roleDetails(int r, CompoundTag role) {
        r = section(r, P + ".role_details");
        r = note(r, roleLabel(role), teamColor(role.getString("team")), 8);
        r = note(r, Component.literal(role.getString("id")), SREPanelStyle.MUTED, 4);
        String description = role.getString("description");
        r = note(r, description.isBlank() ? tr("no_description") : Component.translatable(description), SREPanelStyle.BODY, 64);
        if (role.getBoolean("restricted")) r = note(r, P + ".restricted_hint", SREPanelStyle.RED, 16);
        return r;
    }

    private void buildEnemies() {
        int r = note(0, P + ".ai_hint", SREPanelStyle.BODY, 8);
        r = section(r, P + ".batch");
        r = numeric(r, "enemy_count", countText, 0, 32, v -> { countText = v; draft.putInt("enemyCount", validNumber(v, 0, 32) ? Integer.parseInt(v) : 5); });
        r = cluster(r, P + ".enemy_role", pickerButton(roleName(draft.getString("enemyRole")), "batch"));
        r = choice(r, "behavior", draft, "behavior", BEHAVIORS, true);
        r = choice(r, "equipment", draft, "equipment", EQUIPMENT, true);
        r = choice(r, "area", draft, "area", AREAS, true);
        r = cluster(r, null, action(tr("apply_batch"), tr("batch_hint"), this::validDraft, this::applyBatch),
                action(tr("discard"), tr("unsaved"), () -> dirty, this::discardDraft));
        if (dirty) r = note(r, P + ".unsaved", SREPanelStyle.GOLD);
        CompoundTag selected = bot(selectedSlot);
        if (selected != null) {
            r = section(r, P + ".bot_details");
            r = note(r, P + ".bot_apply_hint", botEdited ? SREPanelStyle.GOLD : SREPanelStyle.MUTED, 8);
            r = note(r, botLabel(selected), SREPanelStyle.TEXT, 8);
            r = note(r, Component.literal(selected.getString("uuid")), SREPanelStyle.MUTED, 4);
            if (!selected.getString("error").isBlank()) r = note(r, Component.translatable(selected.getString("error")), SREPanelStyle.RED, 12);
            r = cluster(r, P + ".enemy_role", pickerButton(roleName(botDraft.getString("role")), "bot"));
            r = choice(r, "behavior", botDraft, "behavior", BEHAVIORS, false);
            r = choice(r, "equipment", botDraft, "equipment", EQUIPMENT, false);
            r = cluster(r, null,
                    action(tr("apply_bot"), tr("bot_apply_hint"), () -> !selected.getBoolean("pending"), () -> botAction("configure")),
                    action(tr("revive"), tr("revive_hint"), () -> !selected.getBoolean("pending") && !selected.getBoolean("alive"), () -> botAction("revive")),
                    action("remove", "remove_hint", () -> botAction("remove")));
        }
        r = section(r, P + ".roster");
        r = search(r, "search", botSearch, v -> botSearch = v);
        int found = 0;
        for (Tag value : state.getList("bots", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            if (!contains(botSearch, entry.getString("name"), entry.getString("uuid"), Integer.toString(entry.getInt("slot")))) continue;
            r = cluster(r, null, selection(botLabel(entry), botTooltip(entry), selectedSlot == entry.getInt("slot"), () -> {
                selectedSlot = entry.getInt("slot");
                botDraft = botConfiguration(entry);
                botEdited = false;
                resetScroll();
                requestRebuild();
            }));
            found++;
        }
        if (found == 0) note(r, P + (botSearch.isBlank() ? ".no_bots" : ".empty"), SREPanelStyle.MUTED, 8);
    }

    private Cell pickerButton(Component name, String target) {
        return action(tr("choose_role", name), tr("choose_role_hint"), () -> TrainingClient.hasCatalogue(), () -> {
            pickingRole = target;
            roleSearch = "";
            team = "all";
            resetScroll();
            requestRebuild();
        });
    }

    private void buildRolePicker() {
        int r = cluster(0, null, button(tr("back_to_bots"), () -> {
            pickingRole = "";
            resetScroll();
            requestRebuild();
        }));
        r = note(r, P + (pickingRole.equals("batch") ? ".pick_batch_role" : ".pick_bot_role"), SREPanelStyle.GOLD, 8);
        r = search(r, "search", roleSearch, v -> roleSearch = v);
        r = filters(r, "team", TEAMS, team, v -> team = v);
        int found = 0;
        for (CompoundTag entry : roles) {
            if (!matchesRole(entry)) continue;
            String id = entry.getString("id");
            String current = pickingRole.equals("batch") ? draft.getString("enemyRole") : botDraft.getString("role");
            r = cluster(r, null, selection(roleLabel(entry), roleTooltip(entry), current.equals(id), () -> {
                if (pickingRole.equals("batch")) { draft.putString("enemyRole", id); dirty = true; }
                else { botDraft.putString("role", id); botEdited = true; }
                pickingRole = "";
                resetScroll();
                requestRebuild();
            }));
            found++;
        }
        empty(r, found);
    }

    private void buildSettings() {
        int r = note(0, P + ".real_rules", SREPanelStyle.BODY, 8);
        r = section(r, P + ".session_settings");
        r = settingToggle(r, "paused");
        r = settingToggle(r, "autoRespawn");
        r = numeric(r, "respawn_delay", delayText, 1, 30, v -> { delayText = v; draft.putInt("respawnDelay", validNumber(v, 1, 30) ? Integer.parseInt(v) : 3); });
        r = settingToggle(r, "friendlyFirePenalty");
        r = cluster(r, null, action(tr("apply_settings"), tr("apply_settings_hint"), () -> dirty && validDraft(), this::onSave),
                action(tr("discard"), tr("unsaved"), () -> dirty, this::discardDraft));
        if (dirty) r = note(r, P + ".unsaved", SREPanelStyle.GOLD);
        r = section(r, P + ".teleports");
        List<Cell> teleports = new ArrayList<>();
        for (String area : new String[]{"lobby", "range", "arena", "train"}) {
            teleports.add(action("area." + area, "teleport_hint", () -> send("teleport", string("area", area))));
        }
        r = cluster(r, null, teleports.toArray(Cell[]::new));
        r = section(r, P + ".maintenance");
        r = cluster(r, null,
                action("cooldown", "cooldown_hint", () -> send("cooldown")),
                action("cleanup", "cleanup_hint", () -> send("cleanup")));
        r = cluster(r, null,
                action("reset", "reset_hint", () -> confirm("reset_confirm", () -> send("reset"))),
                action(tr("rebuild"), tr(TrainingClient.isDedicated() ? "server_rebuild" : "rebuild_hint"),
                        () -> !TrainingClient.isDedicated(), () -> TrainingWorldLauncher.rebuild(this)));
        note(r, P + (TrainingClient.isDedicated() ? ".server_rebuild" : ".rebuild_hint"), SREPanelStyle.MUTED, 12);
    }

    private int settingToggle(int r, String field) {
        int next = toggle(r, P + "." + field, draft.getBoolean(field), value -> {
            draft.putBoolean(field, value);
            dirty = true;
        });
        // toggle() 内部同样经由 cell() 注册，最后一枚即本次设置控件。
        if (!contentControls.isEmpty()) guard(contentControls.getLast(), () -> true);
        return next;
    }

    private int numeric(int r, String label, String value, int min, int max, Consumer<String> setter) {
        EditBox box = editBox(value, 2, 64, SREPanelStyle.hint(tr("number_hint", min, max)), v -> {
            setter.accept(v);
            dirty = true;
        });
        box.setFilter(v -> v.matches("[0-9]{0,2}"));
        box.setTextColor(validNumber(value, min, max) ? SREPanelStyle.TEXT : SREPanelStyle.RED);
        guard(box, () -> true);
        numbers.add(new NumericControl(box, min, max));
        r = cluster(r, P + "." + label, cell(box, 64, 100, 1F), tag(tr("bounds", min, max)));
        if (!validNumber(value, min, max)) r = note(r, tr("number_hint", min, max), SREPanelStyle.RED);
        return r;
    }

    private int choice(int r, String label, CompoundTag target, String field, String[] values, boolean batch) {
        SreButton button = new SreButton(font, tr(label + "." + target.getString(field)), b -> {
            String old = target.getString(field);
            int index = 0;
            for (int i = 0; i < values.length; i++) if (values[i].equals(old)) index = i;
            target.putString(field, values[(index + 1) % values.length]);
            if (batch) dirty = true;
            else botEdited = true;
            b.setMessage(tr(label + "." + target.getString(field)));
        });
        button.setTooltipText(tr("cycle_hint"));
        guard(button, () -> true);
        return cluster(r, P + "." + label, cell(button, 100, 0, 1F));
    }

    private void applyBatch() {
        if (!validDraft()) return;
        CompoundTag data = new CompoundTag();
        for (String key : new String[]{"enemyCount", "enemyRole", "behavior", "equipment", "area"}) data.put(key, draft.get(key).copy());
        configure(data);
    }

    @Override protected void onSave() {
        if (!dirty || !validDraft()) return;
        CompoundTag changes = new CompoundTag();
        CompoundTag actual = state.getCompound("settings");
        for (String key : draft.getAllKeys()) {
            if (!draft.get(key).equals(actual.get(key))) changes.put(key, draft.get(key).copy());
        }
        // 仅提交改变的字段：暂停 AI 不应顺带覆盖逐个假人的职业设置。
        if (changes.isEmpty()) { dirty = false; requestRebuild(); }
        else configure(changes);
    }

    private void configure(CompoundTag settings) {
        send("configure", settings);
    }

    private void discardDraft() {
        readDraft();
        requestRebuild();
    }

    @Override protected void onOpenManage() {
        CompoundTag data = new CompoundTag();
        data.putBoolean("full", true);
        send("snapshot", data);
    }

    private void botAction(String operation) {
        CompoundTag data = botDraft.copy();
        data.putInt("slot", selectedSlot);
        data.putString("operation", operation);
        send("bot", data);
    }

    private void send(String action) { send(action, new CompoundTag()); }

    private boolean send(String action, CompoundTag data) {
        if (awaiting) return false;
        if (!TrainingClient.send(action, data)) {
            localMessage = "unavailable";
            return false;
        }
        awaiting = true;
        sentAt = Util.getMillis();
        localMessage = "waiting";
        updateControls();
        return true;
    }

    private void confirm(String key, Runnable yes) {
        minecraft.setScreen(new ConfirmScreen(result -> {
            minecraft.setScreen(this);
            if (result && TrainingClient.isTraining()) yes.run();
        }, tr("confirm_title"), tr(key)));
    }

    @Override public void onClose() {
        if (dirty || botEdited) {
            minecraft.setScreen(new ConfirmScreen(result -> {
                if (result) minecraft.setScreen(TrainingClient.isTraining() ? parent : null);
                else minecraft.setScreen(this);
            }, tr("confirm_title"), tr("discard_confirm")));
        } else minecraft.setScreen(TrainingClient.isTraining() ? parent : null);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void tick() {
        if (awaiting && Util.getMillis() - sentAt > 10_000L) {
            awaiting = false;
            localMessage = "timeout";
        }
        updateControls();
    }

    private void updateControls() {
        boolean enabled = TrainingClient.isTraining() && !awaiting && !TrainingWorldLauncher.isBusy();
        for (Control control : guarded) control.widget.active = enabled && control.enabled.getAsBoolean();
        for (var child : children()) {
            if (!(child instanceof SreButton button)) continue;
            if (button.getMessage().equals(tr("save"))) {
                button.active = enabled && dirty && validDraft();
                button.setTooltipText(tr(validDraft() ? "save_hint" : "invalid_numbers"));
            } else if (button.getMessage().equals(tr("manage"))) {
                button.active = enabled;
                button.setTooltipText(tr("refresh_hint"));
            }
        }
        for (NumericControl control : numbers) {
            control.box.setTextColor(validNumber(control.box.getValue(), control.min, control.max) ? SREPanelStyle.TEXT : SREPanelStyle.RED);
        }
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        updateControls();
        super.render(g, mouseX, mouseY, partialTick);
        if (mouseX >= layout().contentX() && mouseX < layout().contentRight()
                && mouseY >= layout().headerTop() && mouseY < layout().headerBottom()) {
            g.renderTooltip(font, status().copy().append("\n").append(tr("summary", state.getInt("alive"), state.getInt("pending"), roleName(state.getString("myRole")))), mouseX, mouseY);
        }
    }

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        int y = layout().headerTop() + 1;
        Component summary = tr("summary", state.getInt("alive"), state.getInt("pending"), roleName(state.getString("myRole")));
        g.drawString(font, MapUiGraphics.clip(font, summary.getString(), layout().textW()), layout().contentX(), y, SREPanelStyle.MUTED, false);
        int color = !TrainingClient.isTraining() || !validDraft() || localMessage.equals("timeout") || localMessage.equals("unavailable")
                || state.getString("message").contains(".error.") ? SREPanelStyle.RED
                : awaiting || dirty || botEdited ? SREPanelStyle.GOLD : SREPanelStyle.GREEN;
        g.drawString(font, MapUiGraphics.clip(font, status().getString(), layout().textW()), layout().contentX(), y + 12, color, false);
    }

    private Component status() {
        if (!TrainingClient.isTraining()) return tr("not_training");
        if (!localMessage.isEmpty()) return tr(localMessage);
        if (!validDraft()) return tr("invalid_numbers");
        String message = state.getString("message");
        if (message.isBlank()) return tr(dirty || botEdited ? "unsaved" : "ready");
        MutableComponent feedback = Component.translatable(message);
        return dirty || botEdited ? feedback.append(" · ").append(tr("unsaved")) : feedback;
    }

    /** 覆盖编辑器的「仅输入框循环」：Tab 可到达页签、按钮与屏幕外的列表项。 */
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            List<AbstractWidget> controls = new ArrayList<>();
            // 导航栏/页脚优先，长目录不应阻挡切换页签。
            for (var child : children()) if (child instanceof AbstractWidget w && !contentControls.contains(w) && w.active) controls.add(w);
            for (AbstractWidget w : contentControls) if (w.active) controls.add(w);
            if (!controls.isEmpty()) {
                int current = controls.indexOf(getFocused());
                int index = current < 0 ? (hasShiftDown() ? controls.size() - 1 : 0)
                        : Math.floorMod(current + (hasShiftDown() ? -1 : 1), controls.size());
                AbstractWidget target = controls.get(index);
                if (contentControls.contains(target)) {
                    int delta = target.getY() < layout().contentY() ? target.getY() - layout().contentY()
                            : Math.max(0, target.getBottom() - layout().contentBottom());
                    if (delta != 0) super.mouseScrolled(layout().contentX() + 1, layout().contentY() + 1, 0, -delta / (double) EditorLayout.ROW_H);
                }
                setFocused(target);
                target.setFocused(true);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override protected Cell cell(AbstractWidget widget, int minWidth, int maxWidth, float weight) {
        contentControls.add(widget);
        return super.cell(widget, minWidth, maxWidth, weight);
    }

    private Cell action(String label, String hint, Runnable run) { return action(label, hint, run, () -> true); }
    private Cell action(String label, String hint, Runnable run, BooleanSupplier enabled) { return action(tr(label), tr(hint), enabled, run); }
    private Cell action(Component label, Component hint, BooleanSupplier enabled, Runnable run) {
        SreButton button = new SreButton(font, label, b -> run.run());
        button.setTooltipText(hint);
        guard(button, enabled);
        return cell(button, Math.min(140, Math.max(64, font.width(label) + 12)), 0, 1F);
    }

    private Cell selection(Component label, Component hint, boolean selected, Runnable run) {
        SelectionButton button = new SelectionButton(label, selected, run);
        button.setTooltipText(hint);
        return cell(button, 100, 0, 1F);
    }

    private void guard(AbstractWidget widget, BooleanSupplier enabled) { guarded.add(new Control(widget, enabled)); }
    private boolean validDraft() { return validNumber(countText, 0, 32) && validNumber(delayText, 1, 30); }
    private static boolean validNumber(String value, int min, int max) {
        try { int n = Integer.parseInt(value); return n >= min && n <= max; }
        catch (NumberFormatException e) { return false; }
    }
    private void empty(int r, int count) {
        if (count == 0) note(r, P + (TrainingClient.hasCatalogue() ? ".empty" : ".loading"), SREPanelStyle.MUTED, 8);
    }
    private boolean matchesRole(CompoundTag entry) {
        return (team.equals("all") || team.equals(entry.getString("team")))
                && contains(roleSearch, roleName(entry.getString("id")).getString(), entry.getString("id"), entry.getString("name"));
    }
    private CompoundTag role(String id) { return roles.stream().filter(r -> r.getString("id").equals(id)).findFirst().orElse(null); }
    private CompoundTag bot(int slot) {
        for (Tag tag : state.getList("bots", Tag.TAG_COMPOUND)) if (((CompoundTag) tag).getInt("slot") == slot) return (CompoundTag) tag;
        return null;
    }
    private Component roleName(String id) {
        var registered = RoleUtils.getRole(id);
        if (registered != null) return registered.getName();
        CompoundTag entry = role(id);
        return Component.literal(entry == null || entry.getString("name").isBlank() ? id : entry.getString("name"));
    }
    private Component roleLabel(CompoundTag entry) {
        return tr("role_line", roleName(entry.getString("id")), tr("team." + entry.getString("team")),
                entry.getBoolean("restricted") ? tr("restricted") : Component.empty()).withColor(teamColor(entry.getString("team")));
    }
    private Component roleTooltip(CompoundTag entry) {
        MutableComponent tip = roleLabel(entry).copy().append("\n").append(entry.getString("id"));
        if (!entry.getString("description").isBlank()) tip.append("\n").append(Component.translatable(entry.getString("description")));
        if (entry.getBoolean("restricted")) tip.append("\n").append(tr("restricted_hint"));
        return tip;
    }
    private Component botLabel(CompoundTag entry) {
        String status = !entry.getString("error").isBlank() ? "failed" : entry.getBoolean("pending") ? "pending" : entry.getBoolean("alive") ? "alive" : "dead";
        return tr("bot_line", entry.getInt("slot"), entry.getString("name"), tr("bot_status." + status));
    }
    private Component botTooltip(CompoundTag entry) {
        MutableComponent tip = botLabel(entry).copy().append("\n").append(roleName(entry.getString("role")))
                .append("\n").append(tr("behavior." + entry.getString("behavior")))
                .append(" / ").append(tr("equipment." + entry.getString("equipment")));
        if (!entry.getString("error").isBlank()) tip.append("\n").append(Component.translatable(entry.getString("error")));
        return tip;
    }
    private static int teamColor(String team) {
        return switch (team) { case "killer" -> 0xFFCC2233; case "innocent" -> 0xFF44BB66; case "sheriff" -> 0xFF22BBCC; default -> 0xFFCCAA22; };
    }
    private static boolean contains(String search, String... values) {
        String query = search.strip().toLowerCase(Locale.ROOT);
        for (String value : values) if (value.toLowerCase(Locale.ROOT).contains(query)) return true;
        return false;
    }
    private static CompoundTag botConfiguration(CompoundTag bot) {
        CompoundTag config = new CompoundTag();
        for (String field : new String[]{"role", "behavior", "equipment"}) config.putString(field, bot.getString(field));
        return config;
    }
    private record NumericControl(EditBox box, int min, int max) { }
    private static CompoundTag string(String key, String value) { CompoundTag tag = new CompoundTag(); tag.putString(key, value); return tag; }
    private static MutableComponent tr(String key, Object... args) { return Component.translatable(P + "." + key, args); }

    private record CatalogueItem(String key, String category, ItemStack stack, Component name) { }
    private record Control(AbstractWidget widget, BooleanSupplier enabled) { }

    private final class SelectionButton extends SreButton {
        private final boolean selected;
        SelectionButton(Component label, boolean selected, Runnable run) {
            super(TrainingScreen.this.font, label, b -> run.run());
            this.selected = selected;
        }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(g, mouseX, mouseY, partialTick);
            if (selected || isFocused()) g.renderOutline(getX(), getY(), getWidth(), getHeight(), SREPanelStyle.GOLD);
            if (selected) g.fill(getX() + 1, getY() + 1, getX() + 3, getBottom() - 1, SREPanelStyle.GOLD);
        }
    }
}
