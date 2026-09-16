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

import io.wifi.starrailexpress.api.RoleTeam;
import io.wifi.starrailexpress.client.gui.HintText;
import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import io.wifi.starrailexpress.client.render.item.CustomItemRenderer;
import io.wifi.starrailexpress.customitem.CustomItemData.ChargeAnim;
import io.wifi.starrailexpress.customitem.CustomItemData.FireButton;
import io.wifi.starrailexpress.customitem.CustomItemData.HoldOrientation;
import io.wifi.starrailexpress.customitem.CustomItemData.HoldPose;
import io.wifi.starrailexpress.customitem.CustomItemData.Kind;
import io.wifi.starrailexpress.customitem.CustomItemData.TargetMode;
import io.wifi.starrailexpress.customitem.CustomItemData.TextureMode;
import io.wifi.starrailexpress.customitem.CustomItemData.TracerStyle;
import io.wifi.starrailexpress.customitem.CustomItemData.ThirdPose;
import io.wifi.starrailexpress.game.GameConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * 自定义列车物品编辑界面（两页：基础数据 / 物品性质）。
 *
 * <p>
 * UI 风格与 {@code CustomModifierScreen} / {@code CustomRoleScreen} 一致（面板 + 页签 + 自绘标签 +
 * EditBox + Button + 滚动），重建统一走 {@link #requestRebuild()}，在 render 里执行。
 * 所有文案均走翻译键（{@code sre.custom_item.*}），代码中不出现硬编码文案。
 */
@Environment(EnvType.CLIENT)
public class CustomItemScreen extends Screen {

    private static final float USABLE_RATIO = 0.92f;
    private static final int MAX_PANEL_WIDTH = 680;
    private static final int MAX_PANEL_HEIGHT = 540;
    private static final int MIN_PANEL_HEIGHT = 340;
    private static final int SCROLL_W = 7;
    private static final int SCROLL_MIN_THUMB = 20;
    private static final int ROW_H = 22;
    private static final int LABEL_W = 176;
    private static final int PREVIEW_SIZE = 56;

    private static final String[] TAB_NAMES = { "basic", "kind" };
    private static final String[] TAB_KEYS = { "sre.custom_item.tab.basic", "sre.custom_item.tab.kind" };

    private static final Kind[] KINDS = Kind.values();
    private static final TextureMode[] TEXTURE_MODES = TextureMode.values();
    private static final ChargeAnim[] CHARGE_ANIMS = ChargeAnim.values();
    private static final ThirdPose[] THIRD_POSES = ThirdPose.values();
    private static final TargetMode[] TARGET_MODES = TargetMode.values();
    private static final HoldPose[] HOLD_POSES = HoldPose.values();
    private static final HoldOrientation[] HOLD_ORIENTATIONS = HoldOrientation.values();
    private static final FireButton[] FIRE_BUTTONS = FireButton.values();
    private static final TracerStyle[] TRACER_STYLES = TracerStyle.values();
    private static final io.wifi.starrailexpress.customitem.CustomItemData.CuffWearMode[] CUFF_WEAR_MODES = io.wifi.starrailexpress.customitem.CustomItemData.CuffWearMode
            .values();
    private static final io.wifi.starrailexpress.customitem.CustomItemData.CuffPose[] CUFF_POSES = io.wifi.starrailexpress.customitem.CustomItemData.CuffPose
            .values();
    private static final RoleTeam[] TEAMS = RoleTeam.values();

    private int panelWidth, panelHeight, panelLeftX, panelTopY, activeTab = 0;
    private int scrollOffset = 0, maxScroll = 0;
    private boolean isDraggingScroll = false;
    private double dragScrollStartY = 0;
    private int dragScrollStartOffset = 0;

    private CustomItemData data = new CustomItemData();
    private String originalId = "";

    private final List<AbstractWidget> contentWidgets = new ArrayList<>();
    private final List<LabelEntry> contentLabels = new ArrayList<>();
    private final Map<AbstractWidget, Integer> widgetBaseY = new IdentityHashMap<>();
    private final List<AbstractWidget> tabBarButtons = new ArrayList<>();
    private final List<AbstractWidget> bottomButtons = new ArrayList<>();

    private boolean pendingRebuild = false;

    private record LabelEntry(Component text, int x, int baseY, int color, int maxWidth, int maxLines) {
        /** 该条文字实际会占用的高度（换行的提示要算两行，滚动范围才不会少算）。 */
        int height() {
            return maxLines > 1 ? HintText.LINE_H * maxLines : HintText.LINE_H;
        }
    }

    public CustomItemScreen() {
        super(Component.translatable("sre.custom_item.title"));
    }

    public CustomItemScreen(CustomItemData source) {
        super(Component.translatable("sre.custom_item.title"));
        if (source != null) {
            // 用拷贝编辑：直接改列表里的对象会让自己和重名检查冲突（改 id 永远提示已被占用）
            this.data = source.copy();
            this.originalId = this.data.id == null ? "" : this.data.id;
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 布局
    // ══════════════════════════════════════════════════════════════════
    private void computeLayout() {
        panelWidth = Math.min((int) (width * USABLE_RATIO), MAX_PANEL_WIDTH);
        // 窗口很小时以窗口为准：面板必须完整落在显示区域内，超出的内容靠滚动查看
        int windowLimit = Math.max(120, height - 4);
        int rawH = Math.min((int) (height * USABLE_RATIO), MAX_PANEL_HEIGHT);
        panelHeight = Math.min(Math.max(rawH, MIN_PANEL_HEIGHT), windowLimit);
        panelLeftX = (width - panelWidth) / 2;
        panelTopY = (height - panelHeight) / 2;
    }

    private int contentTop() {
        return panelTopY + 34;
    }

    private int contentBottom() {
        return panelTopY + panelHeight - 30;
    }

    private int baseY(int row) {
        return contentTop() + row * ROW_H;
    }

    private int rowY(int baseY) {
        return baseY - scrollOffset;
    }

    private int fieldX() {
        return panelLeftX + LABEL_W;
    }

    private int labelX() {
        return panelLeftX + 6;
    }

    private int contentWidth() {
        return panelWidth - 12;
    }

    /** 字段标签列的可用宽度（旁边就是输入框，只能单行 + 省略号）。 */
    private int labelW() {
        return LABEL_W - 10;
    }

    private void requestRebuild() {
        this.pendingRebuild = true;
    }

    // ══════════════════════════════════════════════════════════════════
    // init
    // ══════════════════════════════════════════════════════════════════
    @Override
    protected void init() {
        contentWidgets.clear();
        contentLabels.clear();
        widgetBaseY.clear();
        tabBarButtons.clear();
        bottomButtons.clear();

        computeLayout();
        buildTabBar();
        if (activeTab == 0) {
            buildBasicTab();
        } else {
            buildKindTab();
        }

        for (AbstractWidget widget : contentWidgets) {
            addRenderableWidget(widget);
        }
        buildBottomButtons();

        computeMaxScroll();
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
        applyScroll();
    }

    private void computeMaxScroll() {
        int maxY = contentTop();
        for (AbstractWidget widget : contentWidgets) {
            Integer base = widgetBaseY.get(widget);
            if (base != null) {
                maxY = Math.max(maxY, base + widget.getHeight());
            }
        }
        for (LabelEntry label : contentLabels) {
            maxY = Math.max(maxY, label.baseY() + label.height());
        }
        maxScroll = Math.max(0, maxY + 4 - contentBottom());
    }

    private void applyScroll() {
        for (AbstractWidget widget : contentWidgets) {
            Integer base = widgetBaseY.get(widget);
            if (base != null) {
                widget.setY(base - scrollOffset);
            }
        }
    }

    private void buildTabBar() {
        int th = 20, tg = 4, tabs = TAB_NAMES.length;
        int tw = Math.min(90, Math.max(48, (panelWidth - 24 - tg * (tabs - 1)) / tabs));
        int total = tw * TAB_NAMES.length + tg * (TAB_NAMES.length - 1);
        int sx = panelLeftX + (panelWidth - total) / 2;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int index = i;
            var builder = Button.builder(tabLabel(i), button -> {
                activeTab = index;
                scrollOffset = 0;
                requestRebuild();
            }).bounds(sx + i * (tw + tg), panelTopY + 8, tw, th);
            var built = builder.build();
            addRenderableWidget(built);
            tabBarButtons.add(built);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 控件工具
    // ══════════════════════════════════════════════════════════════════
    private void addLabelKey(int row, String key) {
        contentLabels.add(new LabelEntry(Component.translatable(key).withStyle(s -> s.withColor(0xFFFFF4DC)),
                labelX(), baseY(row), 0xFFFFFF, labelW(), 1));
    }

    private void addHintText(int row, Component text, int color) {
        // 整段提示独占一行：按内容宽度自动换行（最多两行），再放不下靠悬停看全文
        contentLabels.add(new LabelEntry(text.copy().withStyle(s -> s.withColor(color)),
                labelX(), baseY(row), color, contentWidth(), 2));
    }

    private <T extends AbstractWidget> T track(T widget, int baseYValue) {
        widgetBaseY.put(widget, baseYValue);
        contentWidgets.add(widget);
        return widget;
    }

    private EditBox box(int row, int x, int w, String value, Component hint, Consumer<String> setter) {
        EditBox box = new EditBox(font, x, rowY(baseY(row)), w, 18, Component.empty());
        box.setValue(value == null ? "" : value);
        box.setMaxLength(512);
        box.setResponder(setter);
        if (hint != null) {
            box.setHint(hint);
            // 输入框内画不下完整提示：悬停看全文
            box.setTooltip(Tooltip.create(hint));
        }
        return track(box, baseY(row));
    }

    private AbstractWidget button(int row, int x, int w, int h, Component text, Runnable onClick) {
        var builder = Button.builder(text, b -> onClick.run()).bounds(x, rowY(baseY(row)), w, h);
        return track(builder.build(), baseY(row));
    }

    /** 输入框右侧的单位提示（跟随字段列，不占左侧标签列）。 */
    private void addFieldHint(int row, Component text, int color) {
        int x = fieldX() + 96;
        int width = Math.max(30, panelLeftX + panelWidth - 6 - x);
        contentLabels.add(new LabelEntry(text.copy().withStyle(s -> s.withColor(color)),
                x, baseY(row), color, width, 1));
    }

    /** 「标签 + 文本」输入行。 */
    private int textRow(int r, String labelKey, String value, Component hint, Consumer<String> setter) {
        addLabelKey(r, labelKey);
        box(r, fieldX(), 300, value, hint, setter);
        return r + 1;
    }

    /** 「标签 + 数值」输入行（带单位提示）。 */
    private int numRow(int r, String labelKey, double value, String unitKey, DoubleConsumer setter) {
        addLabelKey(r, labelKey);
        box(r, fieldX(), 90, num(value), null, v -> setter.accept(parseDouble(v, value)));
        addFieldHint(r, Component.translatable(unitKey), 0xFF9E8B6E);
        return r + 1;
    }

    /** 「标签 + 是/否」轮回按钮行。 */
    private int boolRow(int r, String labelKey, boolean current, Consumer<Boolean> setter) {
        addLabelKey(r, labelKey);
        button(r, fieldX(), 300, 18,
                Component.translatable(current ? "sre.custom_item.value.yes" : "sre.custom_item.value.no")
                        .withStyle(s -> s.withColor(current ? 0xFF72C17B : 0xFFE06B65)),
                () -> {
                    setter.accept(!current);
                    requestRebuild();
                });
        return r + 1;
    }

    /** 「标签 + 轮回」按钮行（枚举）。 */
    private int enumRow(int r, String labelKey, String valueKeyPrefix, Enum<?> current, Consumer<Integer> setter) {
        addLabelKey(r, labelKey);
        button(r, fieldX(), 300, 18,
                Component.translatable(valueKeyPrefix + "." + current.name().toLowerCase()),
                () -> {
                    setter.accept((current.ordinal() + 1) % current.getDeclaringClass().getEnumConstants().length);
                    requestRebuild();
                });
        return r + 1;
    }

    /** 「标签 + 死亡原因轮回」按钮行（取自项目内死亡原因列表）。 */
    private int deathReasonRow(int r, String labelKey, String current, Consumer<String> setter) {
        List<String> ids = GameConstants.DeathReasons.getAllDeathReasonIds();
        addLabelKey(r, labelKey);
        if (ids.isEmpty()) {
            return r + 1;
        }
        button(r, fieldX(), 300, 18, deathReasonName(current), () -> {
            int index = ids.indexOf(current);
            setter.accept(ids.get((index + 1) % ids.size()));
            requestRebuild();
        });
        return r + 1;
    }

    private static Component deathReasonName(String id) {
        if (id == null || id.isBlank()) {
            return Component.translatable("sre.custom_item.value.none");
        }
        return Component.translatable("death_reason." + id.replace(':', '.'));
    }

    /**
     * 通用多行文本列表块：每行一个输入框 + × 删除，末尾 ＋ 追加一行。
     *
     * <p>
     * 列表为空时也会先补一行空输入框，保证界面上一定有可以打字的地方。
     */
    private int textLines(int r, String labelKey, List<String> list, String hintKey, String addKey) {
        return textLines(r, labelKey, list, hintKey, addKey, Integer.MAX_VALUE);
    }

    /** 与上面相同，但限制最多 {@code max} 行（达到上限后不再显示 ＋ 按钮）。 */
    private int textLines(int r, String labelKey, List<String> list, String hintKey, String addKey, int max) {
        addLabelKey(r, labelKey);
        r++;
        addHintText(r++, Component.translatable(hintKey), 0xFF9E8B6E);
        if (list.isEmpty()) {
            list.add("");
        }
        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            box(r, fieldX(), 300, list.get(i), Component.translatable("sre.custom_item.hint.text_line"),
                    v -> list.set(index, v));
            button(r, fieldX() + 306, 22, 18,
                    Component.translatable("sre.custom_item.remove_command"),
                    () -> {
                        list.remove(index);
                        requestRebuild();
                    });
            r++;
        }
        if (list.size() < max) {
            button(r++, fieldX(), 160, 18, Component.translatable(addKey),
                    () -> {
                        list.add("");
                        requestRebuild();
                    });
        } else {
            r++;
        }
        return r;
    }

    /** 多指令列表块（每行输入框 + ×，末尾 ＋ 添加）。 */
    private int commandList(int r, String labelKey, List<String> list) {
        return textLines(r, labelKey, list, "sre.custom_item.hint.commands", "sre.custom_item.add_command");
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 0：基础数据
    // ══════════════════════════════════════════════════════════════════
    private void buildBasicTab() {
        int r = 0;
        addLabelKey(r, "sre.custom_item.label.id");
        box(r++, fieldX(), 300, data.id, Component.translatable("sre.custom_item.hint.id"),
                v -> data.id = v.toLowerCase());

        r = textRow(r, "sre.custom_item.label.display_name", data.displayName,
                Component.translatable("sre.custom_item.hint.display_name"), v -> data.displayName = v);

        // 材质来源：三选一按钮；下面只显示当前来源相关的设置（切换不会清空其它来源已填的值）
        r = enumRow(r, "sre.custom_item.label.texture_mode", "sre.custom_item.texture_mode",
                data.textureMode(), index -> data.textureMode = TEXTURE_MODES[index].name());
        switch (data.textureMode()) {
            case PACK -> {
                r = textRow(r, "sre.custom_item.label.texture_path", data.packTexturePath,
                        Component.translatable("sre.custom_item.hint.texture_path"), v -> data.packTexturePath = v);
                // 贴图来源也支持填模型地址当外壳（留空走默认模型）
                r = modelPathRow(r, true);
            }
            case ANIMATED -> {
                r = textLines(r, "sre.custom_item.label.animated_textures", data.animatedTextures,
                        "sre.custom_item.hint.animated_textures", "sre.custom_item.add_texture");
                r = numRow(r, "sre.custom_item.label.animated_frame_ticks", data.animatedFrameTicks,
                        "sre.custom_item.unit.tick", v -> data.animatedFrameTicks = (int) v);
                r = modelPathRow(r, true);
            }
            case MODEL -> {
                r = modelPathRow(r, false);
                // 模型也能配贴图：填了就给模型换皮（几何用模型、贴图用这张）
                r = textRow(r, "sre.custom_item.label.texture_path", data.packTexturePath,
                        Component.translatable("sre.custom_item.hint.model_texture"),
                        v -> data.packTexturePath = v);
                r = textRow(r, "sre.custom_item.label.inherit_item", data.inheritItemTexture,
                        Component.translatable("sre.custom_item.hint.inherit_item"),
                        v -> data.inheritItemTexture = v);
            }
            case INHERIT -> {
                // 只填物品 id：模型与材质整份借用它
                r = textRow(r, "sre.custom_item.label.inherit_item", data.inheritItemTexture,
                        Component.translatable("sre.custom_item.hint.inherit_texture"),
                        v -> data.inheritItemTexture = v);
            }
        }
        addHintText(r++, Component.translatable("sre.custom_item.hint.texture_mode"), 0xFFC9A84C);
        addHintText(r++, Component.translatable("sre.custom_item.label.preview"), 0xFFFFF4DC);

        // 手持方向：竖着拿（默认，物品模型自带 display）/ 横着拿（同原版普通物品）
        r = enumRow(r, "sre.custom_item.label.hold_orientation", "sre.custom_item.hold_orientation",
                data.holdOrientation(), index -> data.holdOrientation = HOLD_ORIENTATIONS[index].name());
        addHintText(r++, Component.translatable("sre.custom_item.hint.hold_orientation"), 0xFFC9A84C);

        // 战斗设置：是否允许左键攻击玩家（默认关）
        r = boolRow(r, "sre.custom_item.label.allow_left_click_attack", data.allowLeftClickAttack,
                v -> data.allowLeftClickAttack = v);
        addHintText(r++, Component.translatable("sre.custom_item.hint.allow_left_click_attack"), 0xFF9E8B6E);

        // 物品 tooltip：多行文本，每行一个输入框，＋ 追加一行
        r = textLines(r, "sre.custom_item.label.tooltip", data.tooltip,
                "sre.custom_item.hint.tooltip", "sre.custom_item.add_tooltip");

        // 丢弃 / 死亡规则
        addHintText(r++, Component.translatable("sre.custom_item.section.drop"), 0xFFD4AF37);
        r = boolRow(r, "sre.custom_item.label.can_drop", data.canDropItem, v -> data.canDropItem = v);
        r = textRow(r, "sre.custom_item.label.drop_only_role", data.dropOnlyRole,
                Component.translatable("sre.custom_item.hint.drop_only_role"), v -> data.dropOnlyRole = v);
        r = boolRow(r, "sre.custom_item.label.drop_on_death", data.dropOnDeath, v -> data.dropOnDeath = v);
        r = textRow(r, "sre.custom_item.label.pass_on_death_role", data.passOnDeathRole,
                Component.translatable("sre.custom_item.hint.pass_on_death_role"), v -> data.passOnDeathRole = v);
        r = enumRow(r, "sre.custom_item.label.pass_on_death_team", "sre.custom_item.team", data.passOnDeathTeam(),
                index -> data.passOnDeathTeam = TEAMS[index].name());

        // 小偷
        addHintText(r++, Component.translatable("sre.custom_item.section.steal"), 0xFFD4AF37);
        r = boolRow(r, "sre.custom_item.label.stealable", data.stealable, v -> data.stealable = v);
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 1：物品性质
    // ══════════════════════════════════════════════════════════════════
    private void buildKindTab() {
        int r = 0;
        addLabelKey(r, "sre.custom_item.label.kind");
        button(r, fieldX(), 300, 18,
                Component.translatable("sre.custom_item.kind." + data.kind().name().toLowerCase()),
                () -> {
                    data.kind = KINDS[(data.kind().ordinal() + 1) % KINDS.length].name();
                    scrollOffset = 0;
                    requestRebuild();
                });
        r += 2;

        r = switch (data.kind()) {
            case BASIC -> buildBasicKind(r);
            case CHARGE -> buildChargeKind(r);
            case GUN -> buildGunKind(r);
            case VANILLA_WEAPON -> buildWeaponKind(r);
            case FOOD -> buildFoodKind(r);
            case CUFF -> buildCuffKind(r);
            case THROWABLE -> buildThrowableKind(r);
        };
    }

    private int buildBasicKind(int r) {
        r = commandList(r, "sre.custom_item.label.commands", data.commands);
        r = numRow(r, "sre.custom_item.label.cooldown", data.cooldownTicks, "sre.custom_item.unit.tick",
                v -> data.cooldownTicks = (int) v);
        return boolRow(r, "sre.custom_item.label.consume", data.consumeItem, v -> data.consumeItem = v);
    }

    private int buildChargeKind(int r) {
        // 蓄力动作分两套：第一人称（自己屏幕上看到的手部动作，走原版 UseAnim）与
        // 第三人称（别人看到的手臂姿势）。第三人称那行显示的是「当前生效值」——
        // JSON 里留空时它跟随第一人称，点过按钮后就是单独设置的那个。
        r = enumRow(r, "sre.custom_item.label.charge_anim_first", "sre.custom_item.charge_anim", data.chargeAnim(),
                index -> data.chargeAnim = CHARGE_ANIMS[index].name());
        r = enumRow(r, "sre.custom_item.label.charge_anim_third", "sre.custom_item.third_pose",
                data.thirdPose(),
                index -> data.chargeAnimThird = THIRD_POSES[index].name());
        addHintText(r++, Component.translatable("sre.custom_item.hint.charge_anim_split"), 0xFFC9A84C);
        r = numRow(r, "sre.custom_item.label.charge_time", data.chargeTicks, "sre.custom_item.unit.tick",
                v -> data.chargeTicks = (int) v);
        r = commandList(r, "sre.custom_item.label.self_commands", data.selfCommands);
        r = numRow(r, "sre.custom_item.label.cooldown", data.cooldownTicks, "sre.custom_item.unit.tick",
                v -> data.cooldownTicks = (int) v);
        r = boolRow(r, "sre.custom_item.label.consume", data.consumeItem, v -> data.consumeItem = v);
        r = boolRow(r, "sre.custom_item.label.affect_others", data.affectOthers, v -> data.affectOthers = v);
        if (data.affectOthers) {
            r = enumRow(r, "sre.custom_item.label.target_mode", "sre.custom_item.target_mode", data.targetMode(),
                    index -> data.targetMode = TARGET_MODES[index].name());
            if (data.targetMode() == TargetMode.CONE) {
                r = numRow(r, "sre.custom_item.label.cone_angle", data.coneAngle, "sre.custom_item.unit.degree",
                        v -> data.coneAngle = v);
            }
            r = numRow(r, "sre.custom_item.label.range", data.range, "sre.custom_item.unit.blocks",
                    v -> data.range = v);
            r = commandList(r, "sre.custom_item.label.target_commands", data.targetCommands);
            // 空放（蓄力完成但没命中玩家）：独立冷却数值，只有会打人的道具才有这个概念
            r = numRow(r, "sre.custom_item.label.empty_fire_cooldown", data.emptyFireCooldownTicks,
                    "sre.custom_item.unit.tick", v -> data.emptyFireCooldownTicks = (int) v);
            addHintText(r++, Component.translatable("sre.custom_item.hint.empty_fire_cooldown"), 0xFF9E8B6E);
            r = boolRow(r, "sre.custom_item.label.empty_fire_message_enabled", data.emptyFireMessageEnabled,
                    v -> data.emptyFireMessageEnabled = v);
            if (data.emptyFireMessageEnabled) {
                r = textRow(r, "sre.custom_item.label.empty_fire_message", data.emptyFireMessage,
                        Component.translatable("sre.custom_item.hint.empty_fire_message"),
                        v -> data.emptyFireMessage = v);
            }
        }
        return r;
    }

    private int buildGunKind(int r) {
        // 发射按键：右键（默认，同左轮）/ 左键（同狙击枪）
        r = enumRow(r, "sre.custom_item.label.fire_button", "sre.custom_item.fire_button", data.fireButton(),
                index -> data.fireButton = FIRE_BUTTONS[index].name());
        r = textRow(r, "sre.custom_item.label.fire_sound", data.fireSound,
                Component.translatable("sre.custom_item.hint.fire_sound"), v -> data.fireSound = v);
        r = boolRow(r, "sre.custom_item.label.show_tracer", data.showTracer, v -> data.showTracer = v);
        if (data.showTracer) {
            // 射线效果：黄色射线（默认）/ 狙击枪射线（仅开启射线时可选）
            r = enumRow(r, "sre.custom_item.label.tracer_style", "sre.custom_item.tracer_style", data.tracerStyle(),
                    index -> data.tracerStyle = TRACER_STYLES[index].name());
        }
        // 射线是否允许穿过屏障（参考狙击枪：只忽略屏障类方块）
        r = boolRow(r, "sre.custom_item.label.tracer_through_barrier", data.tracerThroughBarrier,
                v -> data.tracerThroughBarrier = v);
        r = numRow(r, "sre.custom_item.label.gun_range", data.gunRange, "sre.custom_item.unit.blocks",
                v -> data.gunRange = v);
        r = numRow(r, "sre.custom_item.label.recoil", data.recoil, "sre.custom_item.unit.degree",
                v -> data.recoil = v);
        r = enumRow(r, "sre.custom_item.label.hold_pose", "sre.custom_item.hold_pose", data.holdPose(),
                index -> data.holdPose = HOLD_POSES[index].name());
        r = numRow(r, "sre.custom_item.label.hits_to_final", data.hitsToFinal, "sre.custom_item.unit.times",
                v -> data.hitsToFinal = (int) v);
        r = numRow(r, "sre.custom_item.label.hit_marker_ticks", data.hitMarkerTicks, "sre.custom_item.unit.tick",
                v -> data.hitMarkerTicks = (int) v);
        addHintText(r++, Component.translatable("sre.custom_item.hint.hit_marker_ticks"), 0xFFC9A84C);
        r = numRow(r, "sre.custom_item.label.shot_cooldown", data.shotCooldownTicks, "sre.custom_item.unit.tick",
                v -> data.shotCooldownTicks = (int) v);
        r = numRow(r, "sre.custom_item.label.final_cooldown", data.finalCooldownTicks,
                "sre.custom_item.unit.tick", v -> data.finalCooldownTicks = (int) v);
        r = commandList(r, "sre.custom_item.label.shoot_commands", data.shootCommands);
        r = commandList(r, "sre.custom_item.label.hit_commands", data.hitCommands);
        r = distanceRules(r, data.distanceRules);
        r = commandList(r, "sre.custom_item.label.final_hit_commands", data.finalHitCommands);
        r = boolRow(r, "sre.custom_item.label.knockback", data.knockbackOnHit, v -> data.knockbackOnHit = v);
        r = boolRow(r, "sre.custom_item.label.lethal", data.lethalOnHit, v -> data.lethalOnHit = v);
        if (data.lethalOnHit) {
            r = deathReasonRow(r, "sre.custom_item.label.lethal_death_reason", data.lethalDeathReason,
                    v -> data.lethalDeathReason = v);
        }
        r = boolRow(r, "sre.custom_item.label.auto_fire", data.autoFire, v -> data.autoFire = v);
        if (data.autoFire) {
            r = numRow(r, "sre.custom_item.label.auto_shots", data.autoShots, "sre.custom_item.unit.times",
                    v -> data.autoShots = (int) v);
            r = numRow(r, "sre.custom_item.label.auto_shots_to_final", data.autoShotsToFinal,
                    "sre.custom_item.unit.times", v -> data.autoShotsToFinal = (int) v);
            r = numRow(r, "sre.custom_item.label.auto_interval", data.autoShotIntervalTicks,
                    "sre.custom_item.unit.tick", v -> data.autoShotIntervalTicks = (int) v);
            r = commandList(r, "sre.custom_item.label.auto_commands", data.autoShotCommands);
        }
        r = boolRow(r, "sre.custom_item.label.ammo_system", data.ammoSystem, v -> data.ammoSystem = v);
        if (data.ammoSystem) {
            r = numRow(r, "sre.custom_item.label.max_ammo", data.maxAmmo, "sre.custom_item.unit.count",
                    v -> data.maxAmmo = (int) v);
            r = boolRow(r, "sre.custom_item.label.refill_on_hit", data.refillOnHit, v -> data.refillOnHit = v);
            r = boolRow(r, "sre.custom_item.label.bullet_support", data.bulletItemSupport,
                    v -> data.bulletItemSupport = v);
        }
        return r;
    }

    private int buildWeaponKind(int r) {
        r = numRow(r, "sre.custom_item.label.durability", data.durability, "sre.custom_item.unit.times",
                v -> data.durability = (int) v);
        r = numRow(r, "sre.custom_item.label.attack_speed", data.attackSpeed, "sre.custom_item.unit.attack_speed",
                v -> data.attackSpeed = v);
        r = numRow(r, "sre.custom_item.label.virtual_damage", data.virtualDamage, "sre.custom_item.unit.point",
                v -> data.virtualDamage = (int) v);
        r = commandList(r, "sre.custom_item.label.weapon_commands", data.weaponRightClickCommands);
        r = numRow(r, "sre.custom_item.label.weapon_cooldown", data.weaponRightClickCooldownTicks,
                "sre.custom_item.unit.tick", v -> data.weaponRightClickCooldownTicks = (int) v);
        r = numRow(r, "sre.custom_item.label.kill_cooldown", data.killCooldownTicks, "sre.custom_item.unit.tick",
                v -> data.killCooldownTicks = (int) v);
        r = deathReasonRow(r, "sre.custom_item.label.kill_death_reason", data.killDeathReason,
                v -> data.killDeathReason = v);
        r = commandList(r, "sre.custom_item.label.victim_commands", data.victimCommands);
        r = commandList(r, "sre.custom_item.label.attacker_commands", data.attackerHitCommands);
        return r;
    }

    private int buildFoodKind(int r) {
        r = numRow(r, "sre.custom_item.label.nutrition", data.nutrition, "sre.custom_item.unit.point",
                v -> data.nutrition = (int) v);
        r = numRow(r, "sre.custom_item.label.saturation", data.saturation, "sre.custom_item.unit.value",
                v -> data.saturation = v);
        r = boolRow(r, "sre.custom_item.label.is_drink", data.isDrink, v -> data.isDrink = v);
        r = numRow(r, "sre.custom_item.label.eat_ticks", data.eatTicks, "sre.custom_item.unit.tick",
                v -> data.eatTicks = (int) v);
        r = commandList(r, "sre.custom_item.label.eat_commands", data.eatCommands);
        r = boolRow(r, "sre.custom_item.label.consume_on_eat", data.consumeOnEat, v -> data.consumeOnEat = v);
        r = numRow(r, "sre.custom_item.label.eat_cooldown", data.eatCooldownTicks, "sre.custom_item.unit.tick",
                v -> data.eatCooldownTicks = (int) v);
        return r;
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 1：物品性质 —— 手铐类
    // ══════════════════════════════════════════════════════════════════
    private int buildCuffKind(int r) {
        r = numRow(r, "sre.custom_item.label.durability", data.durability, "sre.custom_item.unit.times",
                v -> data.durability = (int) v);
        r = enumRow(r, "sre.custom_item.label.cuff_wear_mode", "sre.custom_item.cuff_wear_mode",
                data.cuffWearMode(), index -> data.cuffWearMode = CUFF_WEAR_MODES[index].name());
        r = enumRow(r, "sre.custom_item.label.cuff_pose", "sre.custom_item.cuff_pose", data.cuffPose(),
                index -> data.cuffPose = CUFF_POSES[index].name());
        r = boolRow(r, "sre.custom_item.label.cuff_restrict", data.cuffRestrict, v -> data.cuffRestrict = v);

        addHintText(r++, Component.translatable("sre.custom_item.section.cuff_take_off"), 0xFFD4AF37);
        r = teamRow(r, "sre.custom_item.label.cuff_take_off_team", data.cuffTakeOffTeam,
                v -> data.cuffTakeOffTeam = v);
        r = textLines(r, "sre.custom_item.label.cuff_take_off_roles", data.cuffTakeOffRoles,
                "sre.custom_item.hint.role_id", "sre.custom_item.add_role");
        r = textLines(r, "sre.custom_item.label.cuff_take_off_modifiers", data.cuffTakeOffModifiers,
                "sre.custom_item.hint.modifier_id", "sre.custom_item.add_modifier");

        addHintText(r++, Component.translatable("sre.custom_item.section.cuff_effects"), 0xFFD4AF37);
        r = effectList(r, "sre.custom_item.label.cuff_effects", data.cuffEffects);
        r = numRow(r, "sre.custom_item.label.cuff_command_interval", data.cuffCommandIntervalTicks,
                "sre.custom_item.unit.tick", v -> data.cuffCommandIntervalTicks = (int) v);
        r = commandList(r, "sre.custom_item.label.cuff_commands", data.cuffCommands);
        return r;
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 1：物品性质 —— 投掷物
    // ══════════════════════════════════════════════════════════════════
    private int buildThrowableKind(int r) {
        r = numRow(r, "sre.custom_item.label.durability", data.durability, "sre.custom_item.unit.times",
                v -> data.durability = (int) v);

        // 投掷方式
        r = boolRow(r, "sre.custom_item.label.throw_need_pin", data.throwNeedPin, v -> data.throwNeedPin = v);
        if (data.throwNeedPin) {
            r = numRow(r, "sre.custom_item.label.throw_pin_ticks", data.throwPinTicks,
                    "sre.custom_item.unit.tick", v -> data.throwPinTicks = (int) v);
        }
        r = boolRow(r, "sre.custom_item.label.throw_sticky", data.throwSticky, v -> data.throwSticky = v);
        if (data.throwSticky) {
            r = numRow(r, "sre.custom_item.label.throw_stick_ticks", data.throwStickTicks,
                    "sre.custom_item.unit.tick", v -> data.throwStickTicks = (int) v);
        }
        r = boolRow(r, "sre.custom_item.label.throw_defusable", data.throwDefusable, v -> data.throwDefusable = v);
        if (data.throwDefusable) {
            r = numRow(r, "sre.custom_item.label.throw_defuse_ticks", data.throwDefuseTicks,
                    "sre.custom_item.unit.tick", v -> data.throwDefuseTicks = (int) v);
            r = defuseRules(r, data.throwDefuseFailRules);
        }
        r = boolRow(r, "sre.custom_item.label.throw_delayed", data.throwDelayed, v -> data.throwDelayed = v);
        if (data.throwDelayed) {
            r = numRow(r, "sre.custom_item.label.throw_delay_seconds", data.throwDelaySeconds,
                    "sre.custom_item.unit.second", v -> data.throwDelaySeconds = v);
        }

        // 触发效果
        addHintText(r++, Component.translatable("sre.custom_item.section.throw_trigger"), 0xFFD4AF37);
        r = numRow(r, "sre.custom_item.label.throw_radius", data.throwRadius, "sre.custom_item.unit.blocks",
                v -> data.throwRadius = v);
        r = boolRow(r, "sre.custom_item.label.throw_explode", data.throwExplode, v -> data.throwExplode = v);
        if (data.throwExplode) {
            r = boolRow(r, "sre.custom_item.label.throw_ignore_walls", data.throwIgnoreWalls,
                    v -> data.throwIgnoreWalls = v);
            if (data.throwIgnoreWalls) {
                r = numRow(r, "sre.custom_item.label.throw_wall_blocks", data.throwWallIgnoreBlocks,
                        "sre.custom_item.unit.blocks", v -> data.throwWallIgnoreBlocks = (int) v);
            }
            r = textRow(r, "sre.custom_item.label.throw_particle", data.throwExplosionParticle,
                    Component.translatable("sre.custom_item.hint.particle"), v -> data.throwExplosionParticle = v);
            r = textRow(r, "sre.custom_item.label.throw_sound", data.throwExplosionSound,
                    Component.translatable("sre.custom_item.hint.sound"), v -> data.throwExplosionSound = v);
            r = deathReasonRow(r, "sre.custom_item.label.throw_death_reason", data.throwDeathReason,
                    v -> data.throwDeathReason = v);
        }
        r = commandList(r, "sre.custom_item.label.throw_hit_commands", data.throwHitCommands);
        r = effectList(r, "sre.custom_item.label.throw_hit_effects", data.throwHitEffects);

        // 粒子区域
        r = boolRow(r, "sre.custom_item.label.throw_particle_area", data.throwParticleArea,
                v -> data.throwParticleArea = v);
        if (data.throwParticleArea) {
            r = textRow(r, "sre.custom_item.label.throw_particle_id", data.throwParticleAreaId,
                    Component.translatable("sre.custom_item.hint.particle"), v -> data.throwParticleAreaId = v);
            r = numRow(r, "sre.custom_item.label.throw_particle_ticks", data.throwParticleAreaTicks,
                    "sre.custom_item.unit.tick", v -> data.throwParticleAreaTicks = (int) v);
        }

        // 持续生效区域（燃烧弹式，平面）
        r = boolRow(r, "sre.custom_item.label.throw_persistent_area", data.throwPersistentArea,
                v -> data.throwPersistentArea = v);
        if (data.throwPersistentArea) {
            r = textRow(r, "sre.custom_item.label.throw_area_particle", data.throwAreaParticleId,
                    Component.translatable("sre.custom_item.hint.particle"), v -> data.throwAreaParticleId = v);
            r = numRow(r, "sre.custom_item.label.throw_area_duration", data.throwAreaDurationSeconds,
                    "sre.custom_item.unit.second", v -> data.throwAreaDurationSeconds = (int) v);
            r = numRow(r, "sre.custom_item.label.throw_area_stay", data.throwAreaStayTicks,
                    "sre.custom_item.unit.tick", v -> data.throwAreaStayTicks = (int) v);
            r = effectList(r, "sre.custom_item.label.throw_area_effects", data.throwAreaEffects);
            r = commandList(r, "sre.custom_item.label.throw_area_commands", data.throwAreaCommands);
        }
        return r;
    }

    // ══════════════════════════════════════════════════════════════════
    // 控件：阵营轮回 / 药水效果列表 / 拆除失败规则
    // ══════════════════════════════════════════════════════════════════

    /** 「标签 + 阵营轮回」按钮行，第一项是「无人能取 / 未限制」（空串）。 */
    private int teamRow(int r, String labelKey, String current, Consumer<String> setter) {
        addLabelKey(r, labelKey);
        button(r, fieldX(), 300, 18, teamName(current), () -> {
            if (current == null || current.isBlank()) {
                setter.accept(TEAMS[0].name());
            } else {
                int next = TEAMS.length;
                for (int i = 0; i < TEAMS.length; i++) {
                    if (TEAMS[i].name().equalsIgnoreCase(current)) {
                        next = i + 1;
                        break;
                    }
                }
                setter.accept(next >= TEAMS.length ? "" : TEAMS[next].name());
            }
            requestRebuild();
        });
        return r + 1;
    }

    private static Component teamName(String team) {
        if (team == null || team.isBlank()) {
            return Component.translatable("sre.custom_item.value.none");
        }
        try {
            return Component.translatable("sre.custom_item.team." + team.toLowerCase());
        } catch (Exception e) {
            return Component.literal(team);
        }
    }

    /**
     * 药水效果列表块：每行「效果 id + 等级 + 持续秒数」+ ×，末尾 ＋ 添加。
     *
     * <p>
     * 手铐用它的「id + 等级」（持续时间为「直到解除」）；投掷物用完整的三个字段。
     */
    private int effectList(int r, String labelKey, List<CustomItemData.EffectData> effects) {
        addLabelKey(r, labelKey);
        r++;
        addHintText(r++, Component.translatable("sre.custom_item.hint.effects"), 0xFF9E8B6E);
        if (effects.isEmpty()) {
            effects.add(new CustomItemData.EffectData());
        }
        for (int i = 0; i < effects.size(); i++) {
            final int index = i;
            CustomItemData.EffectData effect = effects.get(i);
            box(r, fieldX(), 180, effect.effectId, Component.translatable("sre.custom_item.hint.effect_id"),
                    v -> effect.effectId = v);
            box(r, fieldX() + 186, 48, num(effect.amplifier),
                    Component.translatable("sre.custom_item.hint.amplifier"),
                    v -> effect.amplifier = parseInt(v, effect.amplifier));
            box(r, fieldX() + 240, 54, num(effect.durationSeconds),
                    Component.translatable("sre.custom_item.hint.duration"),
                    v -> effect.durationSeconds = parseInt(v, effect.durationSeconds));
            button(r, fieldX() + 300, 22, 18, Component.translatable("sre.custom_item.remove_command"),
                    () -> {
                        effects.remove(index);
                        requestRebuild();
                    });
            r++;
        }
        button(r++, fieldX(), 160, 18, Component.translatable("sre.custom_item.add_effect"),
                () -> {
                    effects.add(new CustomItemData.EffectData());
                    requestRebuild();
                });
        return r;
    }

    /** 距离检测列表块：每行「距离（格）+ 指令」+ ×，末尾 ＋ 添加。 */
    private int distanceRules(int r, List<CustomItemData.DistanceRule> rules) {
        addLabelKey(r, "sre.custom_item.label.distance_rules");
        r++;
        addHintText(r++, Component.translatable("sre.custom_item.hint.distance_rules"), 0xFF9E8B6E);
        if (rules.isEmpty()) {
            rules.add(new CustomItemData.DistanceRule());
        }
        for (int i = 0; i < rules.size(); i++) {
            final int index = i;
            CustomItemData.DistanceRule rule = rules.get(i);
            box(r, fieldX(), 68, num(rule.distance),
                    Component.translatable("sre.custom_item.hint.rule_distance"),
                    v -> rule.distance = parseDouble(v, rule.distance));
            box(r, fieldX() + 74, 220, rule.command,
                    Component.translatable("sre.custom_item.hint.rule_command"),
                    v -> rule.command = v);
            button(r, fieldX() + 300, 22, 18, Component.translatable("sre.custom_item.remove_command"),
                    () -> {
                        rules.remove(index);
                        requestRebuild();
                    });
            r++;
        }
        button(r++, fieldX(), 160, 18, Component.translatable("sre.custom_item.add_distance_rule"),
                () -> {
                    rules.add(new CustomItemData.DistanceRule());
                    requestRebuild();
                });
        return r;
    }

    /** 拆除失败概率列表块：每行「职业 id + 失败百分比」+ ×，末尾 ＋ 添加。 */
    private int defuseRules(int r, List<CustomItemData.DefuseFailRule> rules) {
        addLabelKey(r, "sre.custom_item.label.throw_defuse_fail");
        r++;
        addHintText(r++, Component.translatable("sre.custom_item.hint.defuse_fail"), 0xFF9E8B6E);
        if (rules.isEmpty()) {
            rules.add(new CustomItemData.DefuseFailRule());
        }
        for (int i = 0; i < rules.size(); i++) {
            final int index = i;
            CustomItemData.DefuseFailRule rule = rules.get(i);
            box(r, fieldX(), 220, rule.roleId, Component.translatable("sre.custom_item.hint.role_id"),
                    v -> rule.roleId = v);
            box(r, fieldX() + 226, 68, num(rule.failPercent),
                    Component.translatable("sre.custom_item.hint.fail_percent"),
                    v -> rule.failPercent = parseInt(v, rule.failPercent));
            button(r, fieldX() + 300, 22, 18, Component.translatable("sre.custom_item.remove_command"),
                    () -> {
                        rules.remove(index);
                        requestRebuild();
                    });
            r++;
        }
        button(r++, fieldX(), 160, 18, Component.translatable("sre.custom_item.add_defuse_rule"),
                () -> {
                    rules.add(new CustomItemData.DefuseFailRule());
                    requestRebuild();
                });
        return r;
    }

    // ══════════════════════════════════════════════════════════════════
    // 底部按钮
    // ══════════════════════════════════════════════════════════════════
    private void buildBottomButtons() {
        int by = panelTopY + panelHeight - 26, bw = 110, gap = 8;
        int sx = panelLeftX + (panelWidth - (bw * 3 + gap * 2)) / 2;

        var save = Button.builder(Component.translatable("sre.custom_role.save"), b -> save())
                .bounds(sx, by, bw, 20).build();
        var manage = Button.builder(Component.translatable("sre.custom_item.manage"),
                b -> {
                    CustomItemConfig config = CustomItemConfig.getInstance();
                    config.savePreferWorldPath(minecraft.getSingleplayerServer());
                    minecraft.setScreen(new CustomItemManageScreen(() -> new CustomItemScreen()));
                }).bounds(sx + bw + gap, by, bw, 20).build();
        var cancel = Button.builder(Component.translatable("sre.custom_role.cancel"), b -> onClose())
                .bounds(sx + (bw + gap) * 2, by, bw, 20).build();

        addRenderableWidget(save);
        addRenderableWidget(manage);
        addRenderableWidget(cancel);
        bottomButtons.add(save);
        bottomButtons.add(manage);
        bottomButtons.add(cancel);
    }

    private void save() {
        if (data.id == null || data.id.isBlank()) {
            showMessage(Component.translatable("sre.custom_item.error.empty_id"));
            return;
        }
        data.sanitize();
        CustomItemConfig config = CustomItemConfig.getInstance();
        if (config.isIdTaken(data.id, originalId, data)) {
            showMessage(Component.translatable("sre.custom_item.error.duplicate_id", data.id));
            return;
        }
        if (!originalId.isBlank()) {
            config.removeItem(originalId);
        }
        config.removeItem(data.id);
        config.addItem(data);
        config.savePreferWorldPath(minecraft.getSingleplayerServer());

        try {
            config.saveToDefaultPath();
            CustomItemLoader.reloadClient();
            CustomItemRenderer.clearCache();
        } catch (Exception ignored) {
        }
        var server = minecraft.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                try {
                    CustomItemLoader.reload(server);
                    io.wifi.starrailexpress.network.CustomItemServerNetwork.clearCache();
                    io.wifi.starrailexpress.network.CustomItemServerNetwork.syncToAllPlayers(server);
                } catch (Exception ignored) {
                }
            });
        }
        originalId = data.id;
        showMessage(Component.translatable("sre.custom_item.saved", data.id));
        onClose();
    }

    private void showMessage(Component message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(message, false);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 渲染
    // ══════════════════════════════════════════════════════════════════
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 重建后不要 return —— return 会让这一帧什么都不画，切换标签时会闪一下
        if (pendingRebuild) {
            pendingRebuild = false;
            rebuildWidgets();
        }

        renderBackground(g, mouseX, mouseY, partialTick);
        for (AbstractWidget widget : tabBarButtons) {
            widget.render(g, mouseX, mouseY, partialTick);
        }

        g.enableScissor(panelLeftX, contentTop(), panelLeftX + panelWidth, contentBottom());
        for (AbstractWidget widget : contentWidgets) {
            widget.render(g, mouseX, mouseY, partialTick);
        }
        List<HintText.Line> lines = new ArrayList<>(contentLabels.size());
        for (LabelEntry label : contentLabels) {
            lines.add(new HintText.Line(label.text(), label.x(), rowY(label.baseY()), label.color(),
                    label.maxWidth(), label.maxLines()));
        }
        HintText.Line hoveredText = HintText.draw(g, font, lines, mouseX, mouseY);
        if (activeTab == 0) {
            renderPreview(g);
        }
        g.disableScissor();

        // tooltip 不能被内容区的裁剪切掉，放在关闭裁剪之后
        if (hoveredText != null) {
            HintText.drawTooltip(g, font, hoveredText, mouseX, mouseY);
        }

        for (AbstractWidget widget : bottomButtons) {
            widget.render(g, mouseX, mouseY, partialTick);
        }

        if (maxScroll > 0) {
            renderScrollbar(g, mouseX, mouseY);
        }
    }

    /**
     * 「模型地址」输入行。
     *
     * <p>
     * 三种材质来源下都能填：贴图来源（资源包贴图 / 动态贴图）下它是可选的<b>外壳模型</b>
     * —— 填了就把贴图套在模型上，留空走默认模型（平面四边形）；模型来源下它就是主设置。
     *
     * @param shellHint 是否补一行「当外壳用」的说明（贴图来源下需要，模型来源下不需要）
     */
    private int modelPathRow(int r, boolean shellHint) {
        r = textRow(r, "sre.custom_item.label.model_path", data.modelPath,
                Component.translatable("sre.custom_item.hint.model_path"), v -> data.modelPath = v);
        if (shellHint) {
            addHintText(r++, Component.translatable("sre.custom_item.hint.model_shell"), 0xFF9E8B6E);
        }
        return r;
    }

    /**
     * 预览用的平面贴图：按材质来源挑。
     *
     * <p>
     * PACK 用 {@code packTexturePath}；ANIMATED 用当前帧；MODEL / INHERIT 返回 null（交给下面的
     * 「被引用物品的主贴图」预览 —— 立体模型在 16×16 的平面预览里画不出来，预览只做近似；
     * 继承现有物品时本来就该显示被继承物品的贴图，不看 {@code packTexturePath}）。
     */
    private static ResourceLocation resolvePreviewTexture(CustomItemData data) {
        if (data.textureMode() == TextureMode.ANIMATED) {
            List<String> frames = data.animatedFramePaths();
            if (!frames.isEmpty()) {
                int frameTicks = Math.max(1, data.animatedFrameTicks);
                long now = System.currentTimeMillis() / 50L;
                return CustomItemRenderer.resolvePackTexture(frames.get((int) (now / frameTicks % frames.size())));
            }
        }
        if (data.textureMode() == TextureMode.INHERIT) {
            return null;
        }
        return CustomItemRenderer.resolvePackTexture(data.packTexturePath);
    }

    /** 右侧材质预览（资源包/动态贴图 / 立体贴图的物品主贴图 / 未配置占位）。 */
    private void renderPreview(GuiGraphics g) {
        int px = panelLeftX + panelWidth - PREVIEW_SIZE - 14;
        int py = contentTop() + 2;
        g.fill(px - 2, py - 2, px + PREVIEW_SIZE + 2, py + PREVIEW_SIZE + 2, 0xFF8B6914);
        g.fill(px, py, px + PREVIEW_SIZE, py + PREVIEW_SIZE, 0xFF120A04);

        ResourceLocation packTexture = resolvePreviewTexture(data);
        if (packTexture != null) {
            g.blit(packTexture, px, py, PREVIEW_SIZE, PREVIEW_SIZE, 0.0F, 0.0F, 16, 16, 16, 16);
            return;
        }
        TextureAtlasSprite sprite = CustomItemRenderer.resolveInheritedSprite(data.inheritItemTexture);
        if (sprite != null) {
            g.blit(px, py, 0, PREVIEW_SIZE, PREVIEW_SIZE, sprite);
            return;
        }
        // 未配置材质：紫黑棋盘占位
        int cell = 8;
        for (int cy = 0; cy < PREVIEW_SIZE / cell; cy++) {
            for (int cx = 0; cx < PREVIEW_SIZE / cell; cx++) {
                boolean dark = ((cx + cy) & 1) == 0;
                g.fill(px + cx * cell, py + cy * cell, px + (cx + 1) * cell, py + (cy + 1) * cell,
                        dark ? 0xFF1A0F1A : 0xFF2A0F2A);
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        SREPanelStyle.drawPanel(g, panelLeftX - 6, panelTopY - 3, panelWidth + 12, panelHeight + 6);
        g.fill(panelLeftX - 6, contentBottom(), panelLeftX + panelWidth + 6, panelTopY + panelHeight + 3, SREPanelStyle.PANEL_BG_BOTTOM);
        Component title = Component.translatable("sre.custom_item.title");
        g.drawString(font, title.copy().withStyle(s -> s.withBold(true)), panelLeftX - 4, panelTopY - 16, 0xFFD4AF37, false);
    }

    private void renderScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        int sbX = panelLeftX + panelWidth + 1;
        int sbY = contentTop();
        int sbH = contentBottom() - contentTop();
        int totalContentH = sbH + maxScroll;
        float ratio = Math.min(1f, (float) sbH / Math.max(1, totalContentH));
        int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (sbH * ratio));
        int thumbY = sbY + (int) ((sbH - thumbH) * ((float) scrollOffset / maxScroll));
        boolean hover = inside(mouseX, mouseY, sbX, thumbY, SCROLL_W, thumbH);
        SREPanelStyle.drawScrollbar(g, sbX, sbY, sbH, thumbY, thumbH, hover);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside(mouseX, mouseY, panelLeftX, contentTop(), panelWidth, contentBottom() - contentTop())
                && maxScroll > 0) {
            scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(scrollY) * ROW_H, 0, maxScroll);
            applyScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (maxScroll > 0 && button == 0) {
            int sbX = panelLeftX + panelWidth + 1;
            int sbY = contentTop();
            int sbH = contentBottom() - contentTop();
            if (inside(mouseX, mouseY, sbX - 2, sbY, SCROLL_W + 4, sbH)) {
                isDraggingScroll = true;
                dragScrollStartY = mouseY;
                dragScrollStartOffset = scrollOffset;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingScroll && maxScroll > 0) {
            int sbH = contentBottom() - contentTop();
            double ratio = (mouseY - dragScrollStartY) / Math.max(1, sbH - SCROLL_MIN_THUMB);
            scrollOffset = Mth.clamp(dragScrollStartOffset + (int) (ratio * maxScroll), 0, maxScroll);
            applyScroll();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDraggingScroll) {
            isDraggingScroll = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ══════════════════════════════════════════════════════════════════
    // 静态工具
    // ══════════════════════════════════════════════════════════════════
    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String num(double value) {
        return Math.abs(value - Math.rint(value)) < 0.0001D ? String.valueOf((long) Math.rint(value))
                : String.valueOf(value);
    }

    /** 供外部（指令补全等）复用的整数解析。 */
    public static int parseIntSafe(String value, int fallback) {
        return parseInt(value, fallback);
    }

    /**
     * 页签文字：活跃页签用金色粗体。
     *
     * <p>
     * 按钮已换成原版按钮（没有 accent 装饰条了），页签的活跃状态用文字区分，
     * 符合 {@code docs/ui_style.md} 第 5 节的文字层级（重点金色 / 次要土褐）。
     */
    private Component tabLabel(int index) {
        Component label = Component.translatable(TAB_KEYS[index]);
        if (index == activeTab) {
            return label.copy().withStyle(style -> style.withBold(true).withColor(SREPanelStyle.GOLD));
        }
        return label.copy().withStyle(style -> style.withColor(SREPanelStyle.MUTED));
    }
}
