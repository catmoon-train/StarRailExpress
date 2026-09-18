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
import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import io.wifi.starrailexpress.client.gui.screen.CustomEditorScreen;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * 自定义列车物品编辑界面（两页：基础数据 / 物品性质）。
 *
 * <p>
 * 布局、页签、滚动、渲染与鼠标键盘全部交给 {@link CustomEditorScreen}；这里只描述字段。
 * 行内多控件（效果 id + 等级 + 时长 + 删除）用 {@link #cluster} 声明，窄屏自动折行，
 * 不会再像以前那样按写死偏移戳出面板。材质预览交给基类的右侧专属列
 * （窄屏自动落回内容区里的一行），因此永远压在字段上。
 *
 * <p>
 * 所有文案均走翻译键（{@code sre.custom_item.*}），代码中不出现硬编码文案。
 */
@Environment(EnvType.CLIENT)
public class CustomItemScreen extends CustomEditorScreen {

    private static final String PREFIX = "sre.custom_item";
    private static final String[] TABS = { "basic", "kind" };
    private static final String REMOVE = PREFIX + ".remove_command";
    private static final int PREVIEW_SIZE = 56;

    private static final Kind[] KINDS = Kind.values();
    private static final TextureMode[] TEXTURE_MODES = TextureMode.values();
    private static final ChargeAnim[] CHARGE_ANIMS = ChargeAnim.values();
    private static final ThirdPose[] THIRD_POSES = ThirdPose.values();
    private static final TargetMode[] TARGET_MODES = TargetMode.values();
    private static final HoldPose[] HOLD_POSES = HoldPose.values();
    private static final HoldOrientation[] HOLD_ORIENTATIONS = HoldOrientation.values();
    private static final FireButton[] FIRE_BUTTONS = FireButton.values();
    private static final TracerStyle[] TRACER_STYLES = TracerStyle.values();
    private static final CustomItemData.CuffWearMode[] CUFF_WEAR_MODES = CustomItemData.CuffWearMode.values();
    private static final CustomItemData.CuffPose[] CUFF_POSES = CustomItemData.CuffPose.values();
    private static final RoleTeam[] TEAMS = RoleTeam.values();

    private CustomItemData data = new CustomItemData();
    private String originalId = "";

    public CustomItemScreen() {
        super(Component.translatable(PREFIX + ".title"));
    }

    public CustomItemScreen(CustomItemData source) {
        super(Component.translatable(PREFIX + ".title"));
        if (source != null) {
            // 用拷贝编辑：直接改列表里的对象会让自己和重名检查冲突（改 id 永远提示已被占用）
            this.data = source.copy();
            this.originalId = this.data.id == null ? "" : this.data.id;
        }
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
        if (tab == 0) {
            buildBasicTab();
        } else {
            buildKindTab();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 行包装（沿用原来的名字，字段语义一目了然）
    // ══════════════════════════════════════════════════════════════════

    private int textRow(int r, String labelKey, String value, int limit, Component hint,
            Consumer<String> setter) {
        return field(r, labelKey, value, limit, hint, setter);
    }

    private int numRow(int r, String labelKey, double value, String unitKey, DoubleConsumer setter) {
        return number(r, labelKey, num(value), unitKey, text -> setter.accept(parseDouble(text, value)));
    }

    private int intRow(int r, String labelKey, int value, String unitKey, Consumer<Integer> setter) {
        return number(r, labelKey, String.valueOf(value), unitKey, text -> setter.accept(parseInt(text, value)));
    }

    /** 开关行：标签在标签列、小方块左对齐在字段区（这类行不参与自动并排，一行一个更清楚）。 */
    private int boolRow(int r, String labelKey, boolean current, Consumer<Boolean> setter) {
        return cluster(r, labelKey, yesNoCell(current, setter, false));
    }

    /** 会影响「后面显示哪些字段」的开关：切换后重建界面。 */
    private int boolRowGatesFields(int r, String labelKey, boolean current, Consumer<Boolean> setter) {
        return cluster(r, labelKey, yesNoCell(current, setter, true));
    }

    /** 「是 / 否」开关：标签在标签列上，按钮里是带颜色的状态符号 + 是/否（文案键沿用本界面的）。 */
    private Cell yesNoCell(boolean current, Consumer<Boolean> setter, boolean rebuild) {
        return yesNoSwitchCell(current,
                on -> Component.translatable(Boolean.TRUE.equals(on) ? PREFIX + ".value.yes" : PREFIX + ".value.no"),
                setter, rebuild);
    }

    private int enumRow(int r, String labelKey, String keyPrefix, Enum<?> current, Consumer<Integer> setter) {
        return choice(r, labelKey, keyPrefix, current, setter);
    }

    private int enumRowGatesFields(int r, String labelKey, String keyPrefix, Enum<?> current,
            Consumer<Integer> setter) {
        return choice(r, labelKey, keyPrefix, current, setter, true);
    }

    private int commandList(int r, String labelKey, List<String> list) {
        return lines(r, labelKey, list, LIMIT_COMMAND, PREFIX + ".hint.commands", PREFIX + ".hint.text_line",
                PREFIX + ".add_command", REMOVE, Integer.MAX_VALUE);
    }

    private int textLines(int r, String labelKey, List<String> list, int limit, String hintKey, String addKey) {
        return lines(r, labelKey, list, limit, hintKey, PREFIX + ".hint.text_line", addKey, REMOVE,
                Integer.MAX_VALUE);
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 0：基础数据
    // ══════════════════════════════════════════════════════════════════

    private void buildBasicTab() {
        int r = 0;
        r = field(r, PREFIX + ".label.id", data.id, LIMIT_ID, Component.translatable(PREFIX + ".hint.id"),
                value -> data.id = value.toLowerCase());
        r = textRow(r, PREFIX + ".label.display_name", data.displayName, LIMIT_NAME,
                Component.translatable(PREFIX + ".hint.display_name"), value -> data.displayName = value);

        // 材质来源：三选一按钮；下面只显示当前来源相关的设置（切换不会清空其它来源已填的值）
        r = enumRowGatesFields(r, PREFIX + ".label.texture_mode", PREFIX + ".texture_mode", data.textureMode(),
                index -> data.textureMode = TEXTURE_MODES[index].name());
        switch (data.textureMode()) {
            case PACK -> {
                r = textRow(r, PREFIX + ".label.texture_path", data.packTexturePath, LIMIT_PATH,
                        Component.translatable(PREFIX + ".hint.texture_path"),
                        value -> data.packTexturePath = value);
                // 贴图来源也支持填模型地址当外壳（留空走默认模型）
                r = modelPathRow(r, true);
            }
            case ANIMATED -> {
                r = textLines(r, PREFIX + ".label.animated_textures", data.animatedTextures, LIMIT_PATH,
                        PREFIX + ".hint.animated_textures", PREFIX + ".add_texture");
                r = intRow(r, PREFIX + ".label.animated_frame_ticks", data.animatedFrameTicks, PREFIX + ".unit.tick",
                        value -> data.animatedFrameTicks = value);
                r = modelPathRow(r, true);
            }
            case MODEL -> {
                r = modelPathRow(r, false);
                // 模型也能配贴图：填了就给模型换皮（几何用模型、贴图用这张）
                r = textRow(r, PREFIX + ".label.texture_path", data.packTexturePath, LIMIT_PATH,
                        Component.translatable(PREFIX + ".hint.model_texture"),
                        value -> data.packTexturePath = value);
                r = textRow(r, PREFIX + ".label.inherit_item", data.inheritItemTexture, LIMIT_PATH,
                        Component.translatable(PREFIX + ".hint.inherit_item"),
                        value -> data.inheritItemTexture = value);
            }
            case INHERIT -> {
                // 只填物品 id：模型与材质整份借用它
                r = textRow(r, PREFIX + ".label.inherit_item", data.inheritItemTexture, LIMIT_PATH,
                        Component.translatable(PREFIX + ".hint.inherit_texture"),
                        value -> data.inheritItemTexture = value);
            }
        }
        r = note(r, PREFIX + ".hint.texture_mode", SREPanelStyle.GOLD_DIM);
        // 预览：宽屏贴右侧专属列，窄屏落回这里占一行
        r = previewRow(r, PREFIX + ".label.preview");

        // 手持方向：竖着拿（默认，物品模型自带 display）/ 横着拿（同原版普通物品）
        r = enumRow(r, PREFIX + ".label.hold_orientation", PREFIX + ".hold_orientation", data.holdOrientation(),
                index -> data.holdOrientation = HOLD_ORIENTATIONS[index].name());
        r = note(r, PREFIX + ".hint.hold_orientation", SREPanelStyle.GOLD_DIM);

        // 手持时他人不可见（同占卜师的水晶球：别人看不到你手上拿的这件物品）
        r = boolRow(r, PREFIX + ".label.invisible_in_hand", data.invisibleInHand,
                value -> data.invisibleInHand = value);
        r = note(r, PREFIX + ".hint.invisible_in_hand", SREPanelStyle.MUTED);

        // 手持微调：在贴图自己的坐标系里平移 / 旋转，用来把「枪柄浮在手外面」这类问题调到位
        r = section(r, PREFIX + ".section.hold_tuning");
        r = note(r, PREFIX + ".hint.hold_tuning", SREPanelStyle.GOLD_DIM, 3);
        r = numRow(r, PREFIX + ".label.hold_offset_x", data.holdOffsetX, PREFIX + ".unit.pixel",
                value -> data.holdOffsetX = value);
        r = numRow(r, PREFIX + ".label.hold_offset_y", data.holdOffsetY, PREFIX + ".unit.pixel",
                value -> data.holdOffsetY = value);
        r = numRow(r, PREFIX + ".label.hold_offset_z", data.holdOffsetZ, PREFIX + ".unit.pixel",
                value -> data.holdOffsetZ = value);
        r = numRow(r, PREFIX + ".label.hold_rotate_x", data.holdRotateX, PREFIX + ".unit.degree",
                value -> data.holdRotateX = value);
        r = numRow(r, PREFIX + ".label.hold_rotate_z", data.holdRotateZ, PREFIX + ".unit.degree",
                value -> data.holdRotateZ = value);

        // 战斗设置：是否允许左键攻击玩家（默认关）
        r = boolRow(r, PREFIX + ".label.allow_left_click_attack", data.allowLeftClickAttack,
                value -> data.allowLeftClickAttack = value);
        r = note(r, PREFIX + ".hint.allow_left_click_attack", SREPanelStyle.MUTED);

        // 物品 tooltip：多行文本，每行一个输入框，＋ 追加一行
        r = textLines(r, PREFIX + ".label.tooltip", data.tooltip, LIMIT_TEXT, PREFIX + ".hint.tooltip",
                PREFIX + ".add_tooltip");

        // 丢弃 / 死亡规则
        r = section(r, PREFIX + ".section.drop");
        r = boolRow(r, PREFIX + ".label.can_drop", data.canDropItem, value -> data.canDropItem = value);
        r = textRow(r, PREFIX + ".label.drop_only_role", data.dropOnlyRole, LIMIT_ID,
                Component.translatable(PREFIX + ".hint.drop_only_role"), value -> data.dropOnlyRole = value);
        r = boolRow(r, PREFIX + ".label.drop_on_death", data.dropOnDeath, value -> data.dropOnDeath = value);
        r = textRow(r, PREFIX + ".label.pass_on_death_role", data.passOnDeathRole, LIMIT_ID,
                Component.translatable(PREFIX + ".hint.pass_on_death_role"),
                value -> data.passOnDeathRole = value);
        r = enumRow(r, PREFIX + ".label.pass_on_death_team", PREFIX + ".team", data.passOnDeathTeam(),
                index -> data.passOnDeathTeam = TEAMS[index].name());

        // 小偷
        r = section(r, PREFIX + ".section.steal");
        r = boolRow(r, PREFIX + ".label.stealable", data.stealable, value -> data.stealable = value);

        // 使用限制：仅指定职业 / 修饰符 / 阵营可用
        r = section(r, PREFIX + ".section.use_limit");
        r = textRow(r, PREFIX + ".label.use_only_roles", data.useOnlyRoles, LIMIT_TEXT,
                Component.translatable(PREFIX + ".hint.use_only_roles"), value -> data.useOnlyRoles = value);
        r = textRow(r, PREFIX + ".label.use_only_modifiers", data.useOnlyModifiers, LIMIT_TEXT,
                Component.translatable(PREFIX + ".hint.use_only_modifiers"),
                value -> data.useOnlyModifiers = value);
        r = teamList(r, PREFIX + ".label.use_only_teams", data.useOnlyTeams);
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
        r = textRow(r, PREFIX + ".label.model_path", data.modelPath, LIMIT_PATH,
                Component.translatable(PREFIX + ".hint.model_path"), value -> data.modelPath = value);
        if (shellHint) {
            r = note(r, PREFIX + ".hint.model_shell", SREPanelStyle.MUTED);
        }
        return r;
    }

    // ══════════════════════════════════════════════════════════════════
    // 页 1：物品性质
    // ══════════════════════════════════════════════════════════════════

    private void buildKindTab() {
        int r = 0;
        // 换类型后字段整批换掉：回到顶部重新看（只在点击时重置，普通重建不动滚动位置）
        r = cluster(r, PREFIX + ".label.kind",
                stateButtonCell(() -> Component.translatable(PREFIX + ".kind." + data.kind().name().toLowerCase()),
                        () -> {
                            data.kind = KINDS[(data.kind().ordinal() + 1) % KINDS.length].name();
                            resetScroll();
                        }, true));
        r = gap(r);

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
        r = commandList(r, PREFIX + ".label.commands", data.commands);
        r = numRow(r, PREFIX + ".label.cooldown", data.cooldownTicks, PREFIX + ".unit.tick",
                value -> data.cooldownTicks = (int) value);
        return boolRow(r, PREFIX + ".label.consume", data.consumeItem, value -> data.consumeItem = value);
    }

    private int buildChargeKind(int r) {
        // 蓄力动作分两套：第一人称（自己屏幕上看到的手部动作，走原版 UseAnim）与
        // 第三人称（别人看到的手臂姿势）。第三人称那行显示的是「当前生效值」——
        // JSON 里留空时它跟随第一人称，点过按钮后就是单独设置的那个。
        r = enumRow(r, PREFIX + ".label.charge_anim_first", PREFIX + ".charge_anim", data.chargeAnim(),
                index -> data.chargeAnim = CHARGE_ANIMS[index].name());
        r = enumRow(r, PREFIX + ".label.charge_anim_third", PREFIX + ".third_pose", data.thirdPose(),
                index -> data.chargeAnimThird = THIRD_POSES[index].name());
        r = note(r, PREFIX + ".hint.charge_anim_split", SREPanelStyle.GOLD_DIM);
        r = numRow(r, PREFIX + ".label.charge_time", data.chargeTicks, PREFIX + ".unit.tick",
                value -> data.chargeTicks = (int) value);
        r = commandList(r, PREFIX + ".label.self_commands", data.selfCommands);
        r = numRow(r, PREFIX + ".label.cooldown", data.cooldownTicks, PREFIX + ".unit.tick",
                value -> data.cooldownTicks = (int) value);
        r = boolRow(r, PREFIX + ".label.consume", data.consumeItem, value -> data.consumeItem = value);
        r = boolRowGatesFields(r, PREFIX + ".label.affect_others", data.affectOthers,
                value -> data.affectOthers = value);
        if (data.affectOthers) {
            r = enumRowGatesFields(r, PREFIX + ".label.target_mode", PREFIX + ".target_mode", data.targetMode(),
                    index -> data.targetMode = TARGET_MODES[index].name());
            if (data.targetMode() == TargetMode.CONE) {
                r = numRow(r, PREFIX + ".label.cone_angle", data.coneAngle, PREFIX + ".unit.degree",
                        value -> data.coneAngle = value);
            }
            r = numRow(r, PREFIX + ".label.range", data.range, PREFIX + ".unit.blocks",
                    value -> data.range = value);
            r = commandList(r, PREFIX + ".label.target_commands", data.targetCommands);
            // 空放（蓄力完成但没命中玩家）：独立冷却数值，只有会打人的道具才有这个概念
            r = numRow(r, PREFIX + ".label.empty_fire_cooldown", data.emptyFireCooldownTicks,
                    PREFIX + ".unit.tick", value -> data.emptyFireCooldownTicks = (int) value);
            r = note(r, PREFIX + ".hint.empty_fire_cooldown", SREPanelStyle.MUTED);
            r = boolRowGatesFields(r, PREFIX + ".label.empty_fire_message_enabled", data.emptyFireMessageEnabled,
                    value -> data.emptyFireMessageEnabled = value);
            if (data.emptyFireMessageEnabled) {
                r = textRow(r, PREFIX + ".label.empty_fire_message", data.emptyFireMessage, LIMIT_TEXT,
                        Component.translatable(PREFIX + ".hint.empty_fire_message"),
                        value -> data.emptyFireMessage = value);
            }
        }
        return r;
    }

    private int buildGunKind(int r) {
        // 发射按键：右键（默认，同左轮）/ 左键（同狙击枪）
        r = enumRow(r, PREFIX + ".label.fire_button", PREFIX + ".fire_button", data.fireButton(),
                index -> data.fireButton = FIRE_BUTTONS[index].name());
        r = textRow(r, PREFIX + ".label.fire_sound", data.fireSound, LIMIT_PATH,
                Component.translatable(PREFIX + ".hint.fire_sound"), value -> data.fireSound = value);
        r = boolRowGatesFields(r, PREFIX + ".label.show_tracer", data.showTracer, value -> data.showTracer = value);
        if (data.showTracer) {
            // 射线效果：黄色射线（默认）/ 狙击枪射线（仅开启射线时可选）
            r = enumRow(r, PREFIX + ".label.tracer_style", PREFIX + ".tracer_style", data.tracerStyle(),
                    index -> data.tracerStyle = TRACER_STYLES[index].name());
        }
        // 射线是否允许穿过屏障（参考狙击枪：只忽略屏障类方块）
        r = boolRow(r, PREFIX + ".label.tracer_through_barrier", data.tracerThroughBarrier,
                value -> data.tracerThroughBarrier = value);
        r = numRow(r, PREFIX + ".label.gun_range", data.gunRange, PREFIX + ".unit.blocks",
                value -> data.gunRange = value);
        r = numRow(r, PREFIX + ".label.recoil", data.recoil, PREFIX + ".unit.degree", value -> data.recoil = value);
        r = enumRow(r, PREFIX + ".label.hold_pose", PREFIX + ".hold_pose", data.holdPose(),
                index -> data.holdPose = HOLD_POSES[index].name());
        r = numRow(r, PREFIX + ".label.hits_to_final", data.hitsToFinal, PREFIX + ".unit.times",
                value -> data.hitsToFinal = (int) value);
        r = numRow(r, PREFIX + ".label.hit_marker_ticks", data.hitMarkerTicks, PREFIX + ".unit.tick",
                value -> data.hitMarkerTicks = (int) value);
        r = note(r, PREFIX + ".hint.hit_marker_ticks", SREPanelStyle.GOLD_DIM);
        r = numRow(r, PREFIX + ".label.shot_cooldown", data.shotCooldownTicks, PREFIX + ".unit.tick",
                value -> data.shotCooldownTicks = (int) value);
        r = numRow(r, PREFIX + ".label.final_cooldown", data.finalCooldownTicks, PREFIX + ".unit.tick",
                value -> data.finalCooldownTicks = (int) value);
        r = commandList(r, PREFIX + ".label.shoot_commands", data.shootCommands);
        r = commandList(r, PREFIX + ".label.hit_commands", data.hitCommands);
        r = distanceRules(r, data.distanceRules);
        r = commandList(r, PREFIX + ".label.final_hit_commands", data.finalHitCommands);
        r = boolRow(r, PREFIX + ".label.knockback", data.knockbackOnHit, value -> data.knockbackOnHit = value);
        // 致死拆成两个独立开关：射线命中即致死 / 只有触发最终效果时才致死
        r = boolRow(r, PREFIX + ".label.lethal_on_ray_hit", data.lethalOnRayHit,
                value -> data.lethalOnRayHit = value);
        r = note(r, PREFIX + ".hint.lethal_on_ray_hit", SREPanelStyle.MUTED);
        r = boolRow(r, PREFIX + ".label.lethal_on_final", data.lethalOnFinal,
                value -> data.lethalOnFinal = value);
        r = note(r, PREFIX + ".hint.lethal_on_final", SREPanelStyle.MUTED);
        // 两个开关都关着时不需要死因（关掉后仍保留已填的值）
        if (data.lethalOnRayHit || data.lethalOnFinal) {
            r = deathReasonRow(r, PREFIX + ".label.lethal_death_reason", data.lethalDeathReason,
                    value -> data.lethalDeathReason = value);
            r = note(r, PREFIX + ".hint.lethal_death_reason", SREPanelStyle.GOLD_DIM);
        }
        r = boolRowGatesFields(r, PREFIX + ".label.auto_fire", data.autoFire, value -> data.autoFire = value);
        if (data.autoFire) {
            r = numRow(r, PREFIX + ".label.auto_shots", data.autoShots, PREFIX + ".unit.times",
                    value -> data.autoShots = (int) value);
            r = numRow(r, PREFIX + ".label.auto_shots_to_final", data.autoShotsToFinal, PREFIX + ".unit.times",
                    value -> data.autoShotsToFinal = (int) value);
            r = numRow(r, PREFIX + ".label.auto_interval", data.autoShotIntervalTicks, PREFIX + ".unit.tick",
                    value -> data.autoShotIntervalTicks = (int) value);
            r = commandList(r, PREFIX + ".label.auto_commands", data.autoShotCommands);
        }
        r = boolRowGatesFields(r, PREFIX + ".label.ammo_system", data.ammoSystem, value -> data.ammoSystem = value);
        if (data.ammoSystem) {
            r = numRow(r, PREFIX + ".label.max_ammo", data.maxAmmo, PREFIX + ".unit.count",
                    value -> data.maxAmmo = (int) value);
            r = boolRow(r, PREFIX + ".label.refill_on_hit", data.refillOnHit, value -> data.refillOnHit = value);
            r = boolRow(r, PREFIX + ".label.bullet_support", data.bulletItemSupport,
                    value -> data.bulletItemSupport = value);
        }
        return r;
    }

    private int buildWeaponKind(int r) {
        r = numRow(r, PREFIX + ".label.durability", data.durability, PREFIX + ".unit.times",
                value -> data.durability = (int) value);
        r = numRow(r, PREFIX + ".label.attack_speed", data.attackSpeed, PREFIX + ".unit.attack_speed",
                value -> data.attackSpeed = value);
        r = numRow(r, PREFIX + ".label.virtual_damage", data.virtualDamage, PREFIX + ".unit.point",
                value -> data.virtualDamage = (int) value);
        r = commandList(r, PREFIX + ".label.weapon_commands", data.weaponRightClickCommands);
        r = numRow(r, PREFIX + ".label.weapon_cooldown", data.weaponRightClickCooldownTicks, PREFIX + ".unit.tick",
                value -> data.weaponRightClickCooldownTicks = (int) value);
        r = numRow(r, PREFIX + ".label.kill_cooldown", data.killCooldownTicks, PREFIX + ".unit.tick",
                value -> data.killCooldownTicks = (int) value);
        r = deathReasonRow(r, PREFIX + ".label.kill_death_reason", data.killDeathReason,
                value -> data.killDeathReason = value);
        r = commandList(r, PREFIX + ".label.victim_commands", data.victimCommands);
        r = commandList(r, PREFIX + ".label.attacker_commands", data.attackerHitCommands);
        return r;
    }

    private int buildFoodKind(int r) {
        r = numRow(r, PREFIX + ".label.nutrition", data.nutrition, PREFIX + ".unit.point",
                value -> data.nutrition = (int) value);
        r = numRow(r, PREFIX + ".label.saturation", data.saturation, PREFIX + ".unit.value",
                value -> data.saturation = value);
        r = boolRow(r, PREFIX + ".label.is_drink", data.isDrink, value -> data.isDrink = value);
        r = numRow(r, PREFIX + ".label.eat_ticks", data.eatTicks, PREFIX + ".unit.tick",
                value -> data.eatTicks = (int) value);
        r = commandList(r, PREFIX + ".label.eat_commands", data.eatCommands);
        r = boolRow(r, PREFIX + ".label.consume_on_eat", data.consumeOnEat, value -> data.consumeOnEat = value);
        r = numRow(r, PREFIX + ".label.eat_cooldown", data.eatCooldownTicks, PREFIX + ".unit.tick",
                value -> data.eatCooldownTicks = (int) value);
        return r;
    }

    private int buildCuffKind(int r) {
        r = numRow(r, PREFIX + ".label.durability", data.durability, PREFIX + ".unit.times",
                value -> data.durability = (int) value);
        r = enumRow(r, PREFIX + ".label.cuff_wear_mode", PREFIX + ".cuff_wear_mode", data.cuffWearMode(),
                index -> data.cuffWearMode = CUFF_WEAR_MODES[index].name());
        r = enumRow(r, PREFIX + ".label.cuff_pose", PREFIX + ".cuff_pose", data.cuffPose(),
                index -> data.cuffPose = CUFF_POSES[index].name());
        r = boolRow(r, PREFIX + ".label.cuff_restrict", data.cuffRestrict, value -> data.cuffRestrict = value);

        r = section(r, PREFIX + ".section.cuff_take_off");
        r = teamRow(r, PREFIX + ".label.cuff_take_off_team", data.cuffTakeOffTeam,
                value -> data.cuffTakeOffTeam = value);
        r = textLines(r, PREFIX + ".label.cuff_take_off_roles", data.cuffTakeOffRoles, LIMIT_ID,
                PREFIX + ".hint.role_id", PREFIX + ".add_role");
        r = textLines(r, PREFIX + ".label.cuff_take_off_modifiers", data.cuffTakeOffModifiers, LIMIT_ID,
                PREFIX + ".hint.modifier_id", PREFIX + ".add_modifier");

        r = section(r, PREFIX + ".section.cuff_effects");
        r = effectList(r, PREFIX + ".label.cuff_effects", data.cuffEffects);
        r = numRow(r, PREFIX + ".label.cuff_command_interval", data.cuffCommandIntervalTicks, PREFIX + ".unit.tick",
                value -> data.cuffCommandIntervalTicks = (int) value);
        r = commandList(r, PREFIX + ".label.cuff_commands", data.cuffCommands);
        return r;
    }

    private int buildThrowableKind(int r) {
        r = numRow(r, PREFIX + ".label.durability", data.durability, PREFIX + ".unit.times",
                value -> data.durability = (int) value);

        // 投掷方式
        r = boolRowGatesFields(r, PREFIX + ".label.throw_need_pin", data.throwNeedPin,
                value -> data.throwNeedPin = value);
        if (data.throwNeedPin) {
            r = numRow(r, PREFIX + ".label.throw_pin_ticks", data.throwPinTicks, PREFIX + ".unit.tick",
                    value -> data.throwPinTicks = (int) value);
        }
        r = boolRowGatesFields(r, PREFIX + ".label.throw_sticky", data.throwSticky,
                value -> data.throwSticky = value);
        if (data.throwSticky) {
            r = numRow(r, PREFIX + ".label.throw_stick_ticks", data.throwStickTicks, PREFIX + ".unit.tick",
                    value -> data.throwStickTicks = (int) value);
        }
        r = boolRowGatesFields(r, PREFIX + ".label.throw_defusable", data.throwDefusable,
                value -> data.throwDefusable = value);
        if (data.throwDefusable) {
            r = numRow(r, PREFIX + ".label.throw_defuse_ticks", data.throwDefuseTicks, PREFIX + ".unit.tick",
                    value -> data.throwDefuseTicks = (int) value);
            r = defuseRules(r, data.throwDefuseFailRules);
        }
        r = boolRowGatesFields(r, PREFIX + ".label.throw_delayed", data.throwDelayed,
                value -> data.throwDelayed = value);
        if (data.throwDelayed) {
            r = numRow(r, PREFIX + ".label.throw_delay_seconds", data.throwDelaySeconds, PREFIX + ".unit.second",
                    value -> data.throwDelaySeconds = value);
        }

        // 触发效果
        r = section(r, PREFIX + ".section.throw_trigger");
        r = numRow(r, PREFIX + ".label.throw_radius", data.throwRadius, PREFIX + ".unit.blocks",
                value -> data.throwRadius = value);
        r = boolRowGatesFields(r, PREFIX + ".label.throw_explode", data.throwExplode,
                value -> data.throwExplode = value);
        if (data.throwExplode) {
            r = boolRowGatesFields(r, PREFIX + ".label.throw_ignore_walls", data.throwIgnoreWalls,
                    value -> data.throwIgnoreWalls = value);
            if (data.throwIgnoreWalls) {
                r = numRow(r, PREFIX + ".label.throw_wall_blocks", data.throwWallIgnoreBlocks,
                        PREFIX + ".unit.blocks", value -> data.throwWallIgnoreBlocks = (int) value);
            }
            r = textRow(r, PREFIX + ".label.throw_particle", data.throwExplosionParticle, LIMIT_PATH,
                    Component.translatable(PREFIX + ".hint.particle"), value -> data.throwExplosionParticle = value);
            r = textRow(r, PREFIX + ".label.throw_sound", data.throwExplosionSound, LIMIT_PATH,
                    Component.translatable(PREFIX + ".hint.sound"), value -> data.throwExplosionSound = value);
            r = deathReasonRow(r, PREFIX + ".label.throw_death_reason", data.throwDeathReason,
                    value -> data.throwDeathReason = value);
        }
        r = commandList(r, PREFIX + ".label.throw_hit_commands", data.throwHitCommands);
        r = effectList(r, PREFIX + ".label.throw_hit_effects", data.throwHitEffects);

        // 粒子区域
        r = boolRowGatesFields(r, PREFIX + ".label.throw_particle_area", data.throwParticleArea,
                value -> data.throwParticleArea = value);
        if (data.throwParticleArea) {
            r = textRow(r, PREFIX + ".label.throw_particle_id", data.throwParticleAreaId, LIMIT_PATH,
                    Component.translatable(PREFIX + ".hint.particle"), value -> data.throwParticleAreaId = value);
            r = numRow(r, PREFIX + ".label.throw_particle_ticks", data.throwParticleAreaTicks, PREFIX + ".unit.tick",
                    value -> data.throwParticleAreaTicks = (int) value);
        }

        // 持续生效区域（燃烧弹式，平面）
        r = boolRowGatesFields(r, PREFIX + ".label.throw_persistent_area", data.throwPersistentArea,
                value -> data.throwPersistentArea = value);
        if (data.throwPersistentArea) {
            r = textRow(r, PREFIX + ".label.throw_area_particle", data.throwAreaParticleId, LIMIT_PATH,
                    Component.translatable(PREFIX + ".hint.particle"), value -> data.throwAreaParticleId = value);
            r = numRow(r, PREFIX + ".label.throw_area_duration", data.throwAreaDurationSeconds,
                    PREFIX + ".unit.second", value -> data.throwAreaDurationSeconds = (int) value);
            r = numRow(r, PREFIX + ".label.throw_area_stay", data.throwAreaStayTicks, PREFIX + ".unit.tick",
                    value -> data.throwAreaStayTicks = (int) value);
            r = effectList(r, PREFIX + ".label.throw_area_effects", data.throwAreaEffects);
            r = commandList(r, PREFIX + ".label.throw_area_commands", data.throwAreaCommands);
        }
        return r;
    }

    // ══════════════════════════════════════════════════════════════════
    // 控件：阵营轮回 / 阵营列表 / 药水效果列表 / 距离与拆除规则
    // ══════════════════════════════════════════════════════════════════

    /** 「标签 + 阵营轮回」按钮行，第一项是「未限制」（空串）。 */
    private int teamRow(int r, String labelKey, String current, Consumer<String> setter) {
        String[] holder = { current };
        return stateButton(r, labelKey, () -> teamName(holder[0]), () -> {
            String now = holder[0];
            if (now == null || now.isBlank()) {
                holder[0] = TEAMS[0].name();
            } else {
                int next = TEAMS.length;
                for (int i = 0; i < TEAMS.length; i++) {
                    if (TEAMS[i].name().equalsIgnoreCase(now)) {
                        next = i + 1;
                        break;
                    }
                }
                holder[0] = next >= TEAMS.length ? "" : TEAMS[next].name();
            }
            setter.accept(holder[0]);
        });
    }

    private static Component teamName(String team) {
        if (team == null || team.isBlank()) {
            return Component.translatable(PREFIX + ".value.none");
        }
        return Component.translatable(PREFIX + ".team." + team.toLowerCase());
    }

    /**
     * 阵营多选列表块：每行一个「阵营轮回按钮 + ×」，末尾 ＋ 添加一行。
     *
     * <p>
     * 点击按钮切换该行阵营；＋ 可继续添加多个阵营（多项之间是「或」的关系）。
     */
    private int teamList(int r, String labelKey, List<String> teams) {
        r = labelRow(r, labelKey);
        r = note(r, PREFIX + ".hint.use_only_teams", SREPanelStyle.MUTED);
        for (int i = 0; i < teams.size(); i++) {
            final int index = i;
            r = cluster(r, null,
                    stateButtonCell(() -> teamName(teams.get(index)),
                            () -> teams.set(index, nextTeam(teams.get(index))), false),
                    removeButton(Component.translatable(REMOVE), () -> {
                        teams.remove(index);
                        requestRebuild();
                    }));
        }
        return cluster(r, null, fixedButton(Component.translatable(PREFIX + ".add_team"), 160, () -> {
            teams.add(TEAMS[0].name());
            requestRebuild();
        }));
    }

    /** 轮回取下一个阵营名。 */
    private static String nextTeam(String current) {
        for (int i = 0; i < TEAMS.length; i++) {
            if (TEAMS[i].name().equalsIgnoreCase(current)) {
                return TEAMS[(i + 1) % TEAMS.length].name();
            }
        }
        return TEAMS[0].name();
    }

    /**
     * 药水效果列表块：每行「效果 id + 等级 + 持续秒数」+ ×，末尾 ＋ 添加。
     *
     * <p>
     * 手铐用它的「id + 等级」（持续时间为「直到解除」）；投掷物用完整的三个字段。
     */
    private int effectList(int r, String labelKey, List<CustomItemData.EffectData> effects) {
        r = labelRow(r, labelKey);
        r = note(r, PREFIX + ".hint.effects", SREPanelStyle.MUTED);
        if (effects.isEmpty()) {
            effects.add(new CustomItemData.EffectData());
        }
        for (int i = 0; i < effects.size(); i++) {
            final int index = i;
            CustomItemData.EffectData effect = effects.get(i);
            r = cluster(r, null,
                    box(effect.effectId, LIMIT_ID, 120, Component.translatable(PREFIX + ".hint.effect_id"),
                            value -> effect.effectId = value),
                    fixedBox(num(effect.amplifier), LIMIT_NUMBER, 48,
                            Component.translatable(PREFIX + ".hint.amplifier"),
                            value -> effect.amplifier = parseInt(value, effect.amplifier)),
                    fixedBox(num(effect.durationSeconds), LIMIT_NUMBER, 54,
                            Component.translatable(PREFIX + ".hint.duration"),
                            value -> effect.durationSeconds = parseInt(value, effect.durationSeconds)),
                    removeButton(Component.translatable(REMOVE), () -> {
                        effects.remove(index);
                        requestRebuild();
                    }));
        }
        return cluster(r, null, fixedButton(Component.translatable(PREFIX + ".add_effect"), 160, () -> {
            effects.add(new CustomItemData.EffectData());
            requestRebuild();
        }));
    }

    /** 距离检测列表块：每行「距离（格）+ 指令」+ ×，末尾 ＋ 添加。 */
    private int distanceRules(int r, List<CustomItemData.DistanceRule> rules) {
        r = labelRow(r, PREFIX + ".label.distance_rules");
        r = note(r, PREFIX + ".hint.distance_rules", SREPanelStyle.MUTED);
        if (rules.isEmpty()) {
            rules.add(new CustomItemData.DistanceRule());
        }
        for (int i = 0; i < rules.size(); i++) {
            final int index = i;
            CustomItemData.DistanceRule rule = rules.get(i);
            r = cluster(r, null,
                    fixedBox(num(rule.distance), LIMIT_NUMBER, 68,
                            Component.translatable(PREFIX + ".hint.rule_distance"),
                            value -> rule.distance = parseDouble(value, rule.distance)),
                    box(rule.command, LIMIT_COMMAND, 120,
                            Component.translatable(PREFIX + ".hint.rule_command"),
                            value -> rule.command = value),
                    removeButton(Component.translatable(REMOVE), () -> {
                        rules.remove(index);
                        requestRebuild();
                    }));
        }
        return cluster(r, null,
                fixedButton(Component.translatable(PREFIX + ".add_distance_rule"), 160, () -> {
                    rules.add(new CustomItemData.DistanceRule());
                    requestRebuild();
                }));
    }

    /** 拆除失败概率列表块：每行「职业 id + 失败百分比」+ ×，末尾 ＋ 添加。 */
    private int defuseRules(int r, List<CustomItemData.DefuseFailRule> rules) {
        r = labelRow(r, PREFIX + ".label.throw_defuse_fail");
        r = note(r, PREFIX + ".hint.defuse_fail", SREPanelStyle.MUTED);
        if (rules.isEmpty()) {
            rules.add(new CustomItemData.DefuseFailRule());
        }
        for (int i = 0; i < rules.size(); i++) {
            final int index = i;
            CustomItemData.DefuseFailRule rule = rules.get(i);
            r = cluster(r, null,
                    box(rule.roleId, LIMIT_ID, 120, Component.translatable(PREFIX + ".hint.role_id"),
                            value -> rule.roleId = value),
                    fixedBox(num(rule.failPercent), LIMIT_NUMBER, 68,
                            Component.translatable(PREFIX + ".hint.fail_percent"),
                            value -> rule.failPercent = parseInt(value, rule.failPercent)),
                    removeButton(Component.translatable(REMOVE), () -> {
                        rules.remove(index);
                        requestRebuild();
                    }));
        }
        return cluster(r, null, fixedButton(Component.translatable(PREFIX + ".add_defuse_rule"), 160, () -> {
            rules.add(new CustomItemData.DefuseFailRule());
            requestRebuild();
        }));
    }

    /** 「死亡原因轮回」按钮行（取自项目内死亡原因列表）。 */
    private int deathReasonRow(int r, String labelKey, String current, Consumer<String> setter) {
        List<String> ids = GameConstants.DeathReasons.getAllDeathReasonIds();
        if (ids.isEmpty()) {
            return labelRow(r, labelKey);
        }
        String[] holder = { current };
        return stateButton(r, labelKey, () -> deathReasonName(holder[0]), () -> {
            int index = ids.indexOf(holder[0]);
            holder[0] = ids.get((index + 1) % ids.size());
            setter.accept(holder[0]);
        });
    }

    private static Component deathReasonName(String id) {
        if (id == null || id.isBlank()) {
            return Component.translatable(PREFIX + ".value.none");
        }
        return Component.translatable("death_reason." + id.replace(':', '.'));
    }

    // ══════════════════════════════════════════════════════════════════
    // 预览
    // ══════════════════════════════════════════════════════════════════

    @Override
    protected int previewSize() {
        return PREVIEW_SIZE;
    }

    @Override
    protected boolean showPreview(int tab) {
        return tab == 0;
    }

    @Override
    protected void renderPreviewContent(GuiGraphics g, int x, int y, int size) {
        ResourceLocation packTexture = resolvePreviewTexture(data);
        if (packTexture != null) {
            g.blit(packTexture, x, y, size, size, 0.0F, 0.0F, 16, 16, 16, 16);
            return;
        }
        TextureAtlasSprite sprite = CustomItemRenderer.resolveInheritedSprite(data.inheritItemTexture);
        if (sprite != null) {
            g.blit(x, y, 0, size, size, sprite);
        }
    }

    /**
     * 预览用的平面贴图：按材质来源挑。
     *
     * <p>
     * PACK 用 {@code packTexturePath}；ANIMATED 用当前帧；MODEL / INHERIT 返回 null（交给
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

    // ══════════════════════════════════════════════════════════════════
    // 保存 / 管理
    // ══════════════════════════════════════════════════════════════════

    @Override
    protected void onSave() {
        if (data.id == null || data.id.isBlank()) {
            showMessage(Component.translatable(PREFIX + ".error.empty_id"));
            return;
        }
        data.sanitize();
        CustomItemConfig config = CustomItemConfig.getInstance();
        if (config.isIdTaken(data.id, originalId, data)) {
            showMessage(Component.translatable(PREFIX + ".error.duplicate_id", data.id));
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
                    // 走重载命令的路径：除了重建物品索引与重新握手，还会连带重新注册引用这些物品的
                    // 自定义职业（初始物品 / 任务奖励 / 商店条目）与修饰符
                    CustomItemReloadCommand.reload(server);
                } catch (Exception ignored) {
                }
            });
        }
        originalId = data.id;
        showMessage(Component.translatable(PREFIX + ".saved", data.id));
        onClose();
    }

    @Override
    protected void onOpenManage() {
        CustomItemConfig config = CustomItemConfig.getInstance();
        config.savePreferWorldPath(minecraft.getSingleplayerServer());
        minecraft.setScreen(new CustomItemManageScreen(() -> new CustomItemScreen()));
    }

    private void showMessage(Component message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(message, false);
        }
    }

    /** 供外部（指令补全等）复用的整数解析。 */
    public static int parseIntSafe(String value, int fallback) {
        return parseInt(value, fallback);
    }
}
