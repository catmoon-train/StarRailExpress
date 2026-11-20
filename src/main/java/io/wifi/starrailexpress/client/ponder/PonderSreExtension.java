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

import com.google.gson.JsonObject;
import com.nododiiiii.ponderer.runtime.api.RuntimeEngine;
import com.nododiiiii.ponderer.runtime.api.StepBuildContext;
import com.nododiiiii.ponderer.runtime.api.StepCodec;
import com.nododiiiii.ponderer.runtime.api.StepType;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(PonderSreExtension.class);

    private PonderSreExtension() {
    }

    /** 把扩展安装到引擎；由 {@code ResourceSceneContents} 按清单声明调用。 */
    public static void install(RuntimeEngine engine) {
        engine.steps().register(STEP_ROLE_PLAYER, new RolePlayerStep());
        engine.steps().register(STEP_ROLE_SHOP, new RoleShopStep());
        LOGGER.info("StarRailExpress Ponderer 扩展已安装：{}", EXTENSION_ID);
    }

    /** {@code role_player} 的参数。 */
    record RolePlayerParams(Vec3 pos, Float yaw, ResourceLocation skin, Integer outline,
                            String linkId, Float lineWidth, Float expand) {
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
                            readFloat(data, "expand"));
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
                    return data;
                }
            };
        }

        @Override
        public String editorLabel() {
            return "职业玩家模型";
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
            ElementLink<PonderPlayerElement> link = builder.addTo(scene);
            String linkId = params.linkId();
            if (linkId != null && !linkId.isBlank()) {
                // 供后续步骤按 linkId 淡出或复用同一模型。
                context.state().put(linkId, link);
            }
        }
    }

    /** {@code role_shop} 的参数。 */
    record RoleShopParams(ResourceLocation role, Integer columns) {
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
                    return new RoleShopParams(role, readInt(data, "columns"));
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
                    return data;
                }
            };
        }

        @Override
        public String editorLabel() {
            return "职业商店";
        }

        @Override
        public void build(RoleShopParams params, StepBuildContext context) {
            var role = PonderRoles.byId(params.role());
            if (role == null) {
                return;
            }
            int columns = params.columns() == null ? 4 : params.columns();
            PonderShopOverlayElement overlay = PonderShopOverlayElement.forRole(role, columns);
            if (overlay != null) {
                overlay.addTo(context.builder());
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
