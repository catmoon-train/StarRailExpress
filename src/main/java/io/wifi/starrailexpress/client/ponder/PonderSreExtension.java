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
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.scene.SceneBuilder;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
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
 *   <li>{@code starrailexpress:role_shop} —— 显示该职业的实际商店陈列。</li>
 * </ul>
 *
 * <p>扩展在场景注册与编译之前安装，注册失败只影响扩展步骤，不影响普通场景。
 */
public final class PonderSreExtension {

    /** 场景清单里引用的扩展 ID。 */
    public static final String EXTENSION_ID = "starrailexpress:role_tutorial";

    /** 创建职业演示玩家模型（皮肤 + 本能透视边框）。 */
    public static final String STEP_ROLE_PLAYER = "starrailexpress:role_player";

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
        LOGGER.info("StarRailExpress Ponderer extension installed: {}", EXTENSION_ID);
    }

    /** {@code role_player} 的参数。 */
    record RolePlayerParams(Vec3 pos, Float yaw, ResourceLocation skin, Integer outline,
                            String linkId, Float lineWidth, Float expand, String heldItem) {
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
                            readString(data, "heldItem"));
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
            ElementLink<PonderPlayerElement> link = builder.addTo(scene);
            String linkId = params.linkId();
            if (linkId != null && !linkId.isBlank()) {
                // 供后续步骤按 linkId 淡出或复用同一模型。
                context.state().put(linkId, link);
            }
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
