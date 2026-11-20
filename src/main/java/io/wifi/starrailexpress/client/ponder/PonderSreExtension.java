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

package io.wifi.starrailexpress.client.ponder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;
import com.nododiiiii.ponderer.runtime.api.StepBuildContext;
import com.nododiiiii.ponderer.runtime.api.StepCodec;
import com.nododiiiii.ponderer.runtime.api.StepType;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.scene.SceneBuilder;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 本项目为 Ponderer 注册的内容扩展。
 *
 * <p>场景清单通过 {@code extensions} 声明 {@link #EXTENSION_ID} 后，
 * 该场景即可使用以下步骤：
 *
 * <ul>
 *   <li>{@code starrailexpress:role_player} —— 创建可指定皮肤与本能透视边框的玩家模型；</li>
 *   <li>{@code starrailexpress:role_domain} —— 把场景切换成领域技对应的六个领域场景之一；</li>
 *   <li>{@code starrailexpress:role_shop} —— 显示该职业的实际商店陈列；</li>
 *   <li>{@code starrailexpress:role_walk} —— 让玩家模型走到指定坐标（占时间轴）；</li>
 *   <li>{@code starrailexpress:role_teleport} —— 把玩家模型传送到指定坐标；</li>
 *   <li>{@code starrailexpress:role_key} —— 屏幕下方显示按下技能键之类的按键提示。</li>
 * </ul>
 *
 * <p>扩展在场景注册与编译之前安装，注册失败只影响扩展步骤，不影响普通场景。
 */
public final class PonderSreExtension {

    /** 场景清单里引用的扩展 ID。 */
    public static final String EXTENSION_ID = "starrailexpress:role_tutorial";

    /** 创建职业演示玩家模型（皮肤 + 本能透视边框）。 */
    public static final String STEP_ROLE_PLAYER = "starrailexpress:role_player";

    /** 把场景切换成某个领域（领域技演示）。 */
    public static final String STEP_ROLE_DOMAIN = "starrailexpress:role_domain";

    /** 显示职业商店陈列。 */
    public static final String STEP_ROLE_SHOP = "starrailexpress:role_shop";

    /** 显示心情值（san 值）变化。 */
    public static final String STEP_ROLE_MOOD = "starrailexpress:role_mood";

    /** 显示技能释放与对应效果。 */
    public static final String STEP_ROLE_SKILL = "starrailexpress:role_skill";

    /** 显示职业能力渲染出的文字（例如尸体信息）。 */
    public static final String STEP_ROLE_RENDER = "starrailexpress:role_render";

    /** 显示伪装 / 变形形态标签。 */
    public static final String STEP_ROLE_DISGUISE = "starrailexpress:role_disguise";

    /** 显示虚拟血量 / 护盾等数值条。 */
    public static final String STEP_ROLE_VITALS = "starrailexpress:role_vitals";

    /** 显示金币 / 代币的数量与变化。 */
    public static final String STEP_ROLE_CURRENCY = "starrailexpress:role_currency";

    /** 显示技能冷却、持续时间等时间进度。 */
    public static final String STEP_ROLE_TIMER = "starrailexpress:role_timer";

    /** 显示速度、药水等状态效果及层数。 */
    public static final String STEP_ROLE_EFFECTS = "starrailexpress:role_effects";

    /** 显示绑定生成、队伍关系等形式的关系标签。 */
    public static final String STEP_ROLE_BOND = "starrailexpress:role_bond";

    /** 显示信件、笔记、任务清单、身份揭示等文书界面。 */
    public static final String STEP_ROLE_PANEL = "starrailexpress:role_panel";

    /** 让职业玩家模型走到指定位置（带迈腿动作，占时间轴）。 */
    public static final String STEP_ROLE_WALK = "starrailexpress:role_walk";

    /** 把职业玩家模型传送到指定位置（配合粒子演示传送 / 召回）。 */
    public static final String STEP_ROLE_TELEPORT = "starrailexpress:role_teleport";

    /** 屏幕下方显示按键提示（键帽 + 说明），用于技能触发。 */
    public static final String STEP_ROLE_KEY = "starrailexpress:role_key";

    private static final Logger LOGGER = LoggerFactory.getLogger(PonderSreExtension.class);

    /** 扩展步骤在编辑器与覆盖层里显示的文本全部走语言文件。 */
    private static final String KEY_PREFIX = "ponder.starrailexpress.step.";

    private PonderSreExtension() {
    }

    /** 把翻译键解析成当前语言的文本；缺失时由 Minecraft 回退为键名。 */
    static String text(String key) {
        return Component.translatable(key).getString();
    }

    /** 把扩展安装到引擎；由 {@code ResourceSceneContents} 按清单声明调用。 */
    public static void install(RuntimeEngine engine) {
        engine.steps().register(STEP_ROLE_PLAYER, new RolePlayerStep());
        engine.steps().register(STEP_ROLE_DOMAIN, new RoleDomainStep());
        engine.steps().register(STEP_ROLE_SHOP, new RoleShopStep());
        engine.steps().register(STEP_ROLE_MOOD, new RoleInfoStep(KEY_PREFIX + "role_mood", PonderInfoOverlayElement.Kind.MOOD));
        engine.steps().register(STEP_ROLE_SKILL, new RoleInfoStep(KEY_PREFIX + "role_skill", PonderInfoOverlayElement.Kind.SKILL));
        engine.steps().register(STEP_ROLE_RENDER, new RoleInfoStep(KEY_PREFIX + "role_render", PonderInfoOverlayElement.Kind.LINES));
        engine.steps().register(STEP_ROLE_DISGUISE, new RoleInfoStep(KEY_PREFIX + "role_disguise", PonderInfoOverlayElement.Kind.BADGE));
        engine.steps().register(STEP_ROLE_VITALS, new RoleStateStep(KEY_PREFIX + "role_vitals"));
        engine.steps().register(STEP_ROLE_CURRENCY, new RoleStateStep(KEY_PREFIX + "role_currency"));
        engine.steps().register(STEP_ROLE_TIMER, new RoleStateStep(KEY_PREFIX + "role_timer"));
        engine.steps().register(STEP_ROLE_EFFECTS, new RoleStateStep(KEY_PREFIX + "role_effects"));
        engine.steps().register(STEP_ROLE_BOND, new RoleStateStep(KEY_PREFIX + "role_bond"));
        engine.steps().register(STEP_ROLE_PANEL, new RoleStateStep(KEY_PREFIX + "role_panel"));
        engine.steps().register(STEP_ROLE_WALK, new RoleWalkStep());
        engine.steps().register(STEP_ROLE_TELEPORT, new RoleTeleportStep());
        engine.steps().register(STEP_ROLE_KEY, new RoleKeyStep());
        LOGGER.info("StarRailExpress Ponderer extension installed: {}", EXTENSION_ID);
    }

    /** {@code role_player} 的参数。 */
    record RolePlayerParams(Vec3 pos, Float yaw, ResourceLocation skin, Integer outline,
                            String linkId, Float lineWidth, Float expand, String heldItem, Pose pose,
                            Vec3 walkTo, Integer walkDelay, Integer walkDuration) {
    }

    /** 玩家模型步骤：可选皮肤与本能透视边框。 */
    static final class RolePlayerStep implements StepType<RolePlayerParams> {
        @Override
        public StepCodec<RolePlayerParams> codec() {
            return new StepCodec<>() {
                @Override
                public RolePlayerParams decode(JsonObject data) {
                    return new RolePlayerParams(
                            readVec(data, "pos", new Vec3(3.0, 1.0, 3.0)),
                            readFloat(data, "yaw"),
                            readSkin(data),
                            readInt(data, "outline"),
                            readString(data, "linkId"),
                            readFloat(data, "lineWidth"),
                            readFloat(data, "expand"),
                            readString(data, "heldItem"),
                            readPose(data),
                            readVec(data, "walkTo", null),
                            readInt(data, "walkDelay"),
                            readInt(data, "walkDuration"));
                }

                @Override
                public JsonObject encode(RolePlayerParams value) {
                    JsonObject data = new JsonObject();
                    data.addProperty("pos",
                            value.pos().x + "," + value.pos().y + "," + value.pos().z);
                    if (value.yaw() != null) {
                        data.addProperty("yaw", value.yaw());
                    }
                    if (value.skin() != null) {
                        data.addProperty("skin", value.skin().toString());
                    }
                    if (value.outline() != null) {
                        data.addProperty("outline", value.outline());
                    }
                    if (value.linkId() != null) {
                        data.addProperty("linkId", value.linkId());
                    }
                    if (value.lineWidth() != null) {
                        data.addProperty("lineWidth", value.lineWidth());
                    }
                    if (value.expand() != null) {
                        data.addProperty("expand", value.expand());
                    }
                    if (value.heldItem() != null) {
                        data.addProperty("heldItem", value.heldItem());
                    }
                    if (value.pose() != null && value.pose() != Pose.STANDING) {
                        data.addProperty("pose", poseName(value.pose()));
                    }
                    if (value.walkTo() != null) {
                        data.addProperty("walkTo", value.walkTo().x + "," + value.walkTo().y
                                + "," + value.walkTo().z);
                    }
                    if (value.walkDelay() != null) {
                        data.addProperty("walkDelay", value.walkDelay());
                    }
                    if (value.walkDuration() != null) {
                        data.addProperty("walkDuration", value.walkDuration());
                    }
                    return data;
                }
            };
        }

        @Override
        public String editorLabel() {
            return text(KEY_PREFIX + "role_player");
        }

        @Override
        public void build(RolePlayerParams params, StepBuildContext context) {
            SceneBuilder scene = context.builder();
            PonderPlayerPreset.Builder builder = PonderPlayerPreset.at(params.pos());
            if (params.yaw() != null) {
                builder.facing(params.yaw());
            }
            if (params.skin() != null) {
                builder.skin(params.skin());
            }
            if (params.outline() != null) {
                builder.outline(params.outline());
            }
            if (params.lineWidth() != null) {
                builder.outlineLineWidth(params.lineWidth());
            }
            if (params.expand() != null) {
                builder.outlineExpand(params.expand());
            }
            if (params.heldItem() != null) {
                ResourceLocation itemId = ResourceLocation.tryParse(params.heldItem());
                if (itemId != null) {
                    builder.heldItem(new ItemStack(BuiltInRegistries.ITEM.get(itemId)));
                }
            }
            builder.pose(params.pose());
            String linkId = params.linkId();
            Vec3 walkTo = params.walkTo();
            int walkDelay = params.walkDelay() == null ? 0 : Math.max(0, params.walkDelay());
            int walkTicks = params.walkDuration() == null ? 40 : Math.max(1, params.walkDuration());
            builder.addTo(scene, element -> {
                // 直接把元素实例交给后续步骤用，不经过 ps.resolve()，少一层可能失败的查找。
                if (linkId != null && !linkId.isBlank()) {
                    context.state().put(linkId, element);
                }
                if (walkTo != null) {
                    // 同一段里「先站住讲两句、再走过去」，免得再拆一个步骤来驱动模型。
                    if (walkDelay > 0) {
                        scene.idle(walkDelay);
                    }
                    scene.addInstruction(new PonderWalkInstruction(element, walkTo, walkTicks));
                }
            });
        }
    }

    /** {@code role_walk} 的参数。 */
    record RoleWalkParams(Vec3 from, Vec3 to, Integer duration, Float yaw, ResourceLocation skin,
                          Integer outline, String linkId, String heldItem, Pose pose) {
    }

    /**
     * 走动步骤：让职业玩家模型走到指定的绝对坐标，并占掉 {@code duration} 个 tick。
     *
     * <p>{@code linkId} 能命中之前步骤登记的模型时，直接驱动那个模型；
     * 没有命中时在 {@code from} 新建一个模型再走过去，方便单独演示「走向某处」。
     */
    static final class RoleWalkStep implements StepType<RoleWalkParams> {
        @Override
        public StepCodec<RoleWalkParams> codec() {
            return new StepCodec<>() {
                @Override
                public RoleWalkParams decode(JsonObject data) {
                    Vec3 from = readVec(data, "from", new Vec3(2.0, 1.0, 4.0));
                    return new RoleWalkParams(from, readVec(data, "to", from), readInt(data, "duration"),
                            readFloat(data, "yaw"), readSkin(data), readInt(data, "outline"),
                            readString(data, "linkId"), readString(data, "heldItem"), readPose(data));
                }

                @Override
                public JsonObject encode(RoleWalkParams value) {
                    JsonObject data = new JsonObject();
                    data.addProperty("from",
                            value.from().x + "," + value.from().y + "," + value.from().z);
                    data.addProperty("to", value.to().x + "," + value.to().y + "," + value.to().z);
                    if (value.duration() != null) {
                        data.addProperty("duration", value.duration());
                    }
                    if (value.yaw() != null) {
                        data.addProperty("yaw", value.yaw());
                    }
                    if (value.skin() != null) {
                        data.addProperty("skin", value.skin().toString());
                    }
                    if (value.outline() != null) {
                        data.addProperty("outline", value.outline());
                    }
                    if (value.linkId() != null) {
                        data.addProperty("linkId", value.linkId());
                    }
                    if (value.heldItem() != null) {
                        data.addProperty("heldItem", value.heldItem());
                    }
                    if (value.pose() != null && value.pose() != Pose.STANDING) {
                        data.addProperty("pose", poseName(value.pose()));
                    }
                    return data;
                }
            };
        }

        @Override
        public String editorLabel() {
            return text(KEY_PREFIX + "role_walk");
        }

        @Override
        public void build(RoleWalkParams params, StepBuildContext context) {
            SceneBuilder scene = context.builder();
            int ticks = params.duration() == null ? 40 : Math.max(1, params.duration());
            Vec3 to = params.to();
            String linkId = params.linkId();

            PonderPlayerElement existing = playerElement(context, linkId);
            if (existing != null) {
                // 走动指令本身是阻塞的，后面的动作会等模型走到位。
                scene.addInstruction(new PonderWalkInstruction(existing, to, ticks));
                return;
            }

            Vec3 from = params.from();
            PonderPlayerPreset.Builder builder = PonderPlayerPreset.at(from)
                    .facing(params.yaw() == null ? yawToward(from, to) : params.yaw());
            if (params.skin() != null) {
                builder.skin(params.skin());
            }
            if (params.outline() != null) {
                builder.outline(params.outline());
            }
            if (params.heldItem() != null) {
                ResourceLocation itemId = ResourceLocation.tryParse(params.heldItem());
                if (itemId != null) {
                    builder.heldItem(new ItemStack(BuiltInRegistries.ITEM.get(itemId)));
                }
            }
            builder.pose(params.pose());
            builder.addTo(scene, element -> {
                if (linkId != null && !linkId.isBlank()) {
                    context.state().put(linkId, element);
                }
                scene.addInstruction(new PonderWalkInstruction(element, to, ticks));
            });
        }
    }

    /** {@code role_teleport} 的参数。 */
    record RoleTeleportParams(String linkId, Vec3 pos) {
    }

    /**
     * 传送步骤：把之前登记的玩家模型瞬间挪到 {@code pos}。
     *
     * <p>只负责落位，前后的粒子爆发与音效由场景里的 {@code spawn_particles} / {@code play_sound}
     * 显式写出——游戏内这些效果本来就是传送调用的副作用，写出来更容易调整观感。
     */
    static final class RoleTeleportStep implements StepType<RoleTeleportParams> {
        @Override
        public StepCodec<RoleTeleportParams> codec() {
            return new StepCodec<>() {
                @Override
                public RoleTeleportParams decode(JsonObject data) {
                    return new RoleTeleportParams(readString(data, "linkId"),
                            readVec(data, "pos", Vec3.ZERO));
                }

                @Override
                public JsonObject encode(RoleTeleportParams value) {
                    JsonObject data = new JsonObject();
                    if (value.linkId() != null) {
                        data.addProperty("linkId", value.linkId());
                    }
                    data.addProperty("pos", value.pos().x + "," + value.pos().y + "," + value.pos().z);
                    return data;
                }
            };
        }

        @Override
        public String editorLabel() {
            return text(KEY_PREFIX + "role_teleport");
        }

        @Override
        public void build(RoleTeleportParams params, StepBuildContext context) {
            PonderPlayerElement element = playerElement(context, params.linkId());
            if (element == null) {
                LOGGER.warn("role_teleport 找不到 linkId 对应的玩家模型：{}", params.linkId());
                return;
            }
            Vec3 pos = params.pos();
            context.builder().addInstruction(ps -> element.teleportTo(pos));
        }
    }

    /** {@code role_key} 的参数。 */
    record RoleKeyParams(Vec3 point, String direction, String keyBind, String key, String text,
                         Integer duration, Integer color) {
    }

    /**
     * 按键提示步骤：键帽挂在世界坐标上，走 Ponder 自带的 {@code showControls} 控件。
     *
     * <p>和鼠标左/右键提示完全同一套摆放与对话框绘制，只是把图标换成了键帽，
     * 所以它会贴着文本框、随镜头自动挪位，而不是自己算屏幕位置。
     */
    static final class RoleKeyStep implements StepType<RoleKeyParams> {
        @Override
        public StepCodec<RoleKeyParams> codec() {
            return new StepCodec<>() {
                @Override
                public RoleKeyParams decode(JsonObject data) {
                    String keyBind = readString(data, "keyBind");
                    String text = readString(data, "text");
                    return new RoleKeyParams(
                            readVec(data, "point", null),
                            readString(data, "direction"),
                            keyBind == null || keyBind.isBlank() ? "ability" : keyBind,
                            readString(data, "key"),
                            text == null || text.isBlank() ? "ponder.starrailexpress.ponder.press_skill" : text,
                            readInt(data, "duration"),
                            readInt(data, "color"));
                }

                @Override
                public JsonObject encode(RoleKeyParams value) {
                    JsonObject data = new JsonObject();
                    if (value.point() != null) {
                        data.addProperty("point",
                                value.point().x + "," + value.point().y + "," + value.point().z);
                    }
                    if (value.direction() != null) {
                        data.addProperty("direction", value.direction());
                    }
                    data.addProperty("keyBind", value.keyBind());
                    if (value.key() != null) {
                        data.addProperty("key", value.key());
                    }
                    data.addProperty("text", value.text());
                    if (value.duration() != null) {
                        data.addProperty("duration", value.duration());
                    }
                    if (value.color() != null) {
                        data.addProperty("color", value.color());
                    }
                    return data;
                }
            };
        }

        @Override
        public String editorLabel() {
            return text(KEY_PREFIX + "role_key");
        }

        @Override
        public void build(RoleKeyParams params, StepBuildContext context) {
            String keyName = resolveKeyName(params.keyBind(), params.key());
            String caption = resolveText(params.text());
            int accent = params.color() == null ? 0xFFFFD700 : params.color();
            int lifeTicks = params.duration() == null ? 60 : params.duration();
            Vec3 point = params.point() == null ? Vec3.ZERO : params.point();
            context.builder().overlay()
                    .showControls(point, parseDirection(params.direction()), lifeTicks)
                    .showing(new PonderKeyCapScreenElement(keyName, caption, accent));
        }
    }

    /** 控件的指向：默认从上方指下来。 */
    private static Pointing parseDirection(String raw) {
        if (raw == null || raw.isBlank()) {
            return Pointing.DOWN;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "up" -> Pointing.UP;
            case "left" -> Pointing.LEFT;
            case "right" -> Pointing.RIGHT;
            default -> Pointing.DOWN;
        };
    }

    /** 读当前实际绑定的按键名；{@code key} 显式写了按键时优先用它。 */
    private static String resolveKeyName(String keyBind, String key) {
        if (key != null && !key.isBlank()) {
            return key;
        }
        KeyMapping mapping = switch (keyBind == null ? "" : keyBind.toLowerCase(java.util.Locale.ROOT)) {
            case "next_ability", "next" -> NoellesrolesClient.nextAbilityBind;
            case "guess_role_note" -> NoellesrolesClient.roleGuessNoteClientBind;
            case "map_intro" -> NoellesrolesClient.mapIntroClientBind;
            case "task_instinct" -> NoellesrolesClient.taskInstinctBind;
            default -> NoellesrolesClient.abilityBind;
        };
        if (mapping == null) {
            return "?";
        }
        String keyName = mapping.getTranslatedKeyMessage().getString();
        return keyName.isBlank() ? "?" : keyName;
    }

    /** 说明文字：翻译键写法按当前语言解析，其余按原文显示。 */
    private static String resolveText(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        if (raw.startsWith("ponder.") || raw.startsWith("key.")) {
            return Component.translatable(raw).getString();
        }
        return raw;
    }

    /** 按 {@code linkId} 取出之前步骤登记的玩家模型实例。 */
    private static PonderPlayerElement playerElement(StepBuildContext context, String linkId) {
        if (linkId == null || linkId.isBlank()) {
            return null;
        }
        Object stored = context.state().get(linkId);
        return stored instanceof PonderPlayerElement element ? element : null;
    }

    /** 按 A→B 的水平方向求模型朝向（Minecraft 的 yaw：0 朝 +Z，180 朝 -Z）。 */
    private static float yawToward(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        if (Math.abs(dx) < 1.0E-4 && Math.abs(dz) < 1.0E-4) {
            return 180.0F;
        }
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /**
     * 模型姿态：{@code standing} / {@code crouching}（蹲下）/ {@code swimming}（趴下）/
     * {@code sleeping}（躺下）/ {@code spin_attack} / {@code fall_flying}（鞘翅）。
     *
     * <p>游戏内玩家蹲下就是这个姿态，趴下用的是游泳姿态，这里与之一致，
     * 用到蹲下、趴下、躺下演示的职业直接写 {@code pose} 即可。
     */
    private static Pose readPose(JsonObject data) {
        String raw = readString(data, "pose");
        if (raw == null || raw.isBlank()) {
            return Pose.STANDING;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "crouching", "crouch", "sneaking", "sneak" -> Pose.CROUCHING;
            case "swimming", "crawling", "crawl", "prone" -> Pose.SWIMMING;
            case "sleeping", "sleep", "lying" -> Pose.SLEEPING;
            case "spin_attack", "spin" -> Pose.SPIN_ATTACK;
            case "fall_flying", "elytra", "flying" -> Pose.FALL_FLYING;
            default -> Pose.STANDING;
        };
    }

    /** {@link #readPose} 的反向映射，供编辑器回写。 */
    private static String poseName(Pose pose) {
        return switch (pose) {
            case CROUCHING -> "crouching";
            case SWIMMING -> "swimming";
            case SLEEPING -> "sleeping";
            case SPIN_ATTACK -> "spin_attack";
            case FALL_FLYING -> "fall_flying";
            default -> "standing";
        };
    }

    /** {@code role_domain} 的参数。 */
    record RoleDomainParams(PonderDomains domain, Vec3 pos, Float scale) {
    }

    /**
     * 领域场景步骤：演示到领域技时，把教程场景切换成对应领域。
     *
     * <p>场景由 {@link PonderDomainScenes} 在客户端重建，开头会先清掉以 {@code pos}
     * 为中心的一大块区域，所以不需要额外写清场步骤。
     */
    static final class RoleDomainStep implements StepType<RoleDomainParams> {
        @Override
        public StepCodec<RoleDomainParams> codec() {
            return new StepCodec<>() {
                @Override
                public RoleDomainParams decode(JsonObject data) {
                    String raw = readString(data, "domain");
                    PonderDomains domain = PonderDomains.byId(raw);
                    if (domain == null) {
                        LOGGER.warn("role_domain step has an unknown domain id: {}", raw);
                    }
                    return new RoleDomainParams(domain, readVec(data, "pos", Vec3.ZERO),
                            readFloat(data, "scale"));
                }

                @Override
                public JsonObject encode(RoleDomainParams value) {
                    JsonObject data = new JsonObject();
                    if (value.domain() != null) {
                        data.addProperty("domain", value.domain().id());
                    }
                    data.addProperty("pos",
                            value.pos().x + "," + value.pos().y + "," + value.pos().z);
                    if (value.scale() != null) {
                        data.addProperty("scale", value.scale());
                    }
                    return data;
                }
            };
        }

        @Override
        public String editorLabel() {
            return text(KEY_PREFIX + "role_domain");
        }

        @Override
        public void build(RoleDomainParams params, StepBuildContext context) {
            if (params.domain() == null) {
                return;
            }
            SceneBuilder scene = context.builder();
            if (params.scale() != null) {
                // 领域比普通教程房间大，需要时可以用 scale 把取景框拉远（作用于整个场景）。
                scene.scaleSceneView(params.scale());
            }
            BlockPos center = BlockPos.containing(params.pos().x, params.pos().y, params.pos().z);
            params.domain().build(scene, context.util(), center);
        }
    }

    /** {@code role_shop} 的静态兜底商品；动态商店数据为空时使用。 */
    record ShopFallback(String item, int price, String currency) {
    }

    /** {@code role_shop} 的参数。 */
    record RoleShopParams(ResourceLocation role, Integer columns, List<ShopFallback> items) {
    }

    /** 职业商店步骤：按职业实际商店数据绘制陈列。 */
    static final class RoleShopStep implements StepType<RoleShopParams> {
        @Override
        public StepCodec<RoleShopParams> codec() {
            return new StepCodec<>() {
                @Override
                public RoleShopParams decode(JsonObject data) {
                    String raw = readString(data, "role");
                    ResourceLocation role = raw == null ? null : ResourceLocation.tryParse(raw);
                    return new RoleShopParams(role, readInt(data, "columns"), readFallbackItems(data));
                }

                @Override
                public JsonObject encode(RoleShopParams value) {
                    JsonObject data = new JsonObject();
                    if (value.role() != null) {
                        data.addProperty("role", value.role().toString());
                    }
                    if (value.columns() != null) {
                        data.addProperty("columns", value.columns());
                    }
                    JsonArray items = new JsonArray();
                    for (ShopFallback item : value.items()) {
                        JsonObject entry = new JsonObject();
                        entry.addProperty("item", item.item());
                        entry.addProperty("price", item.price());
                        entry.addProperty("currency", item.currency());
                        items.add(entry);
                    }
                    data.add("items", items);
                    return data;
                }
            };
        }

        @Override
        public String editorLabel() {
            return text(KEY_PREFIX + "role_shop");
        }

        @Override
        public void build(RoleShopParams params, StepBuildContext context) {
            var role = PonderRoles.byId(params.role());
            if (role == null) {
                return;
            }
            int columns = params.columns() == null ? 4 : params.columns();
            List<PonderShopOverlayElement.Entry> entries = params.items().isEmpty()
                    ? PonderShopEntries.build(role)
                    : buildFallbackEntries(params.items());
            if (entries.isEmpty()) {
                entries = PonderShopEntries.build(role);
            }
            if (!entries.isEmpty()) {
                new PonderShopOverlayElement(entries,
                        Component.translatable("gui.starrailexpress.shop.role_shop"), columns)
                        .addTo(context.builder());
            }
        }

        private static List<PonderShopOverlayElement.Entry> buildFallbackEntries(List<ShopFallback> items) {
            List<PonderShopOverlayElement.Entry> result = new ArrayList<>();
            for (ShopFallback item : items) {
                ResourceLocation id = ResourceLocation.tryParse(item.item());
                if (id == null) {
                    continue;
                }
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
                if (stack.isEmpty()) {
                    continue;
                }
                ShopEntry.Currency currency = ShopEntry.Currency.fromSerializedName(item.currency());
                if (currency == null) {
                    currency = ShopEntry.Currency.MONEY;
                }
                result.add(new PonderShopOverlayElement.Entry(stack, item.price(),
                        item.price() + currency.iconText(), currency.color()));
            }
            return List.copyOf(result);
        }

        private static List<ShopFallback> readFallbackItems(JsonObject data) {
            if (data == null || !data.has("items") || !data.get("items").isJsonArray()) {
                return List.of();
            }
            List<ShopFallback> result = new ArrayList<>();
            for (JsonElement element : data.getAsJsonArray("items")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                String item = readString(object, "item");
                if (item == null || item.isBlank()) {
                    continue;
                }
                Integer price = readInt(object, "price");
                String currency = readString(object, "currency");
                result.add(new ShopFallback(item, price == null ? 0 : price,
                        currency == null ? "money" : currency));
            }
            return List.copyOf(result);
        }
    }

    /** {@code role_mood/role_skill/role_render/role_disguise} 的通用参数。 */
    record RoleInfoParams(PonderInfoOverlayElement.Kind kind, String label, List<String> lines,
                          float before, float after, int color) {
    }

    /** 把 JSON 数据画成职业状态覆盖层；四种步骤共用同一实现，仅默认标签与类型不同。 */
    static final class RoleInfoStep implements StepType<RoleInfoParams> {
        private final String defaultLabel;
        private final PonderInfoOverlayElement.Kind kind;

        RoleInfoStep(String defaultLabel, PonderInfoOverlayElement.Kind kind) {
            this.defaultLabel = defaultLabel;
            this.kind = kind;
        }

        @Override
        public StepCodec<RoleInfoParams> codec() {
            return new StepCodec<>() {
                @Override
                public RoleInfoParams decode(JsonObject data) {
                    String label = readString(data, "label");
                    Float before = readFloat(data, "before");
                    Float after = readFloat(data, "after");
                    Integer color = readInt(data, "color");
                    float start = before == null ? 0.0F : before;
                    return new RoleInfoParams(kind,
                            label == null || label.isBlank() ? defaultLabel : label,
                            readLines(data), start, after == null ? start : after,
                            color == null ? 0xFFD700 : color);
                }

                @Override
                public JsonObject encode(RoleInfoParams value) {
                    JsonObject data = new JsonObject();
                    data.addProperty("label", value.label());
                    JsonArray lines = new JsonArray();
                    value.lines().forEach(lines::add);
                    data.add("lines", lines);
                    data.addProperty("before", value.before());
                    data.addProperty("after", value.after());
                    data.addProperty("color", value.color());
                    return data;
                }
            };
        }

        @Override
        public String editorLabel() {
            return text(defaultLabel);
        }

        @Override
        public void build(RoleInfoParams params, StepBuildContext context) {
            new PonderInfoOverlayElement(params.kind(), params.label(), params.lines(),
                    params.before(), params.after(), params.color()).addTo(context.builder());
        }

        private static List<String> readLines(JsonObject data) {
            if (data == null || !data.has("lines") || data.get("lines").isJsonNull()) {
                return List.of();
            }
            JsonElement element = data.get("lines");
            if (element.isJsonArray()) {
                List<String> result = new ArrayList<>();
                for (JsonElement line : element.getAsJsonArray()) {
                    result.add(line.getAsString());
                }
                return result;
            }
            return List.of(element.getAsString().split("\\n", -1));
        }
    }

    /** 通用状态面板步骤的参数。 */
    record RoleStateParams(String title, List<PonderStateOverlayElement.Bar> bars,
                           Float progressBefore, Float progressAfter, List<String> lines, int color) {
    }

    /** 虚拟血量、金币、冷却、效果、绑定与文书等步骤共用的状态面板。 */
    static final class RoleStateStep implements StepType<RoleStateParams> {
        private final String defaultTitle;

        RoleStateStep(String defaultTitle) {
            this.defaultTitle = defaultTitle;
        }

        @Override
        public StepCodec<RoleStateParams> codec() {
            return new StepCodec<>() {
                @Override
                public RoleStateParams decode(JsonObject data) {
                    String title = readString(data, "title");
                    Integer color = readInt(data, "color");
                    JsonElement progress = data == null ? null : data.get("progress");
                    Float before = null;
                    Float after = null;
                    if (progress != null && !progress.isJsonNull()) {
                        if (progress.isJsonArray() && progress.getAsJsonArray().size() >= 2) {
                            before = parseFloat(progress.getAsJsonArray().get(0));
                            after = parseFloat(progress.getAsJsonArray().get(1));
                        } else if (progress.isJsonObject()) {
                            before = readFloat(progress.getAsJsonObject(), "before");
                            after = readFloat(progress.getAsJsonObject(), "after");
                        }
                    }
                    return new RoleStateParams(
                            title == null || title.isBlank() ? defaultTitle : title,
                            readBars(data), before, after, readLines(data),
                            color == null ? 0xFFD700 : color);
                }

                @Override
                public JsonObject encode(RoleStateParams value) {
                    JsonObject data = new JsonObject();
                    data.addProperty("title", value.title());
                    JsonArray bars = new JsonArray();
                    for (PonderStateOverlayElement.Bar bar : value.bars()) {
                        JsonObject item = new JsonObject();
                        item.addProperty("label", bar.label());
                        item.addProperty("before", bar.before());
                        item.addProperty("after", bar.after());
                        item.addProperty("color", bar.color());
                        bars.add(item);
                    }
                    data.add("bars", bars);
                    if (value.progressBefore() != null || value.progressAfter() != null) {
                        JsonObject progress = new JsonObject();
                        progress.addProperty("before", value.progressBefore());
                        progress.addProperty("after", value.progressAfter());
                        data.add("progress", progress);
                    }
                    JsonArray lines = new JsonArray();
                    value.lines().forEach(lines::add);
                    data.add("lines", lines);
                    data.addProperty("color", value.color());
                    return data;
                }
            };
        }

        @Override
        public String editorLabel() {
            return text(defaultTitle);
        }

        @Override
        public void build(RoleStateParams params, StepBuildContext context) {
            new PonderStateOverlayElement(params.title(), params.bars(), params.progressBefore(),
                    params.progressAfter(), params.lines(), params.color()).addTo(context.builder());
        }

        private static List<PonderStateOverlayElement.Bar> readBars(JsonObject data) {
            if (data == null || !data.has("bars") || data.get("bars").isJsonNull()
                    || !data.get("bars").isJsonArray()) {
                return List.of();
            }
            List<PonderStateOverlayElement.Bar> result = new ArrayList<>();
            for (JsonElement element : data.getAsJsonArray("bars")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                Float before = readFloat(object, "before");
                Float after = readFloat(object, "after");
                Integer color = readInt(object, "color");
                String label = readString(object, "label");
                result.add(new PonderStateOverlayElement.Bar(
                        label == null ? "" : label,
                        before == null ? 0.0F : before,
                        after == null ? (before == null ? 0.0F : before) : after,
                        color == null ? 0xFF55FF : color));
            }
            return result;
        }

        private static List<String> readLines(JsonObject data) {
            if (data == null || !data.has("lines") || data.get("lines").isJsonNull()) {
                return List.of();
            }
            JsonElement element = data.get("lines");
            if (element.isJsonArray()) {
                List<String> result = new ArrayList<>();
                for (JsonElement line : element.getAsJsonArray()) {
                    result.add(line.getAsString());
                }
                return result;
            }
            return List.of(element.getAsString().split("\\n", -1));
        }

        private static Float parseFloat(JsonElement element) {
            try {
                return element.getAsFloat();
            } catch (RuntimeException error) {
                return null;
            }
        }
    }

    // ==== JSON 参数读取：同时接受字符串与数字写法 ====

    private static String readString(JsonObject data, String key) {
        if (data == null || !data.has(key) || data.get(key).isJsonNull()) {
            return null;
        }
        try {
            return data.get(key).getAsString();
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static Integer readInt(JsonObject data, String key) {
        String raw = readString(data, key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.decode(raw.trim());
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static Float readFloat(JsonObject data, String key) {
        String raw = readString(data, key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Float.parseFloat(raw.trim());
        } catch (RuntimeException error) {
            return null;
        }
    }

    /** 皮肤支持 {@code namespace:path} 写法，自动补全 {@code textures/} 与 {@code .png}。 */
    private static ResourceLocation readSkin(JsonObject data) {
        String raw = readString(data, "skin");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(raw.trim());
        if (id == null) {
            return null;
        }
        String path = id.getPath();
        if (path.startsWith("textures/") && path.endsWith(".png")) {
            return id;
        }
        if (!path.startsWith("textures/")) {
            path = "textures/" + path;
        }
        if (!path.endsWith(".png")) {
            path = path + ".png";
        }
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path);
    }

    /** 坐标接受 {@code [x,y,z]} 数组或 {@code "x,y,z"} 字符串。 */
    private static Vec3 readVec(JsonObject data, String key, Vec3 fallback) {
        if (data == null || !data.has(key) || data.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            var element = data.get(key);
            if (element.isJsonArray()) {
                var array = element.getAsJsonArray();
                if (array.size() >= 3) {
                    return new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(),
                            array.get(2).getAsDouble());
                }
                return fallback;
            }
            String[] parts = element.getAsString().split(",");
            if (parts.length < 3) {
                return fallback;
            }
            return new Vec3(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()));
        } catch (RuntimeException error) {
            return fallback;
        }
    }
}
