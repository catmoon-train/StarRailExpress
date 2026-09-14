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

package io.wifi.starrailexpress.content.block_entity;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.index.SREDisplayBlocks;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 实体展示方块的方块实体：展示任意实体（原版没有对应的展示实体，这是本模组扩展）。
 *
 * <p>NBT 用原版 {@code EntityTag} 的格式，也就是 {@code {id: "minecraft:armor_stand", ...}}，
 * 后面的键直接喂给 {@code EntityType.create(tag, level)}，所以装备、颜色、名字之类的实体数据
 * 都能直接写进来（可以先用「存准心实体」按钮把眼前那只实体的完整数据抓过来）。
 */
public class EntityDisplayBlockEntity extends DisplayBlockEntityBase {

    public static final String TAG_ENTITY = "entity";
    public static final String TAG_ID = "id";

    /** 没有 entity 键时渲染的占位实体：盔甲架最中性，也最容易看出朝向。 */
    private static final String DEFAULT_ENTITY_ID = "minecraft:armor_stand";

    public EntityDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(SREDisplayBlocks.ENTITY_DISPLAY_BLOCK_ENTITY, pos, state);
    }

    @Override
    protected List<String> contentKeys() {
        return List.of(TAG_ENTITY);
    }

    @Override
    protected void contentFillDefaults(CompoundTag tag) {
        if (!tag.contains(TAG_ENTITY, Tag.TAG_COMPOUND)) {
            tag.put(TAG_ENTITY, entityTagOf(DEFAULT_ENTITY_ID));
        }
    }

    @Override
    protected void contentSanitize() {
        CompoundTag entity = getTag().getCompound(TAG_ENTITY);
        if (!entity.isEmpty() && !entity.contains(TAG_ID, Tag.TAG_STRING)) {
            // 缺 id 的实体数据没法实例化，补回默认值，免得渲染器一直失败
            entity.putString(TAG_ID, DEFAULT_ENTITY_ID);
            getTag().put(TAG_ENTITY, entity);
        }
    }

    /** 展示的实体数据（EntityTag 格式）。 */
    public CompoundTag getEntityTag() {
        CompoundTag entity = getTag().getCompound(TAG_ENTITY);
        return entity.isEmpty() ? entityTagOf(DEFAULT_ENTITY_ID) : entity;
    }

    /** 展示的实体类型 ID，取不到返回 null。 */
    @Nullable
    public ResourceLocation getEntityTypeId() {
        CompoundTag entity = getEntityTag();
        if (!entity.contains(TAG_ID, Tag.TAG_STRING)) {
            return null;
        }
        return ResourceLocation.tryParse(entity.getString(TAG_ID));
    }

    @Nullable
    public EntityType<?> getEntityType() {
        ResourceLocation id = getEntityTypeId();
        return id == null ? null : BuiltInRegistries.ENTITY_TYPE.get(id);
    }

    /**
     * 解析实体数据里的 {@code Rotation: [yaw, pitch]}（{@code /summon} 与 F3+I 都会输出），
     * 拿它当实体的基础朝向，贴进来的实体就会保持原来的朝向，再被展示数据的变换继续旋转。
     */
    public static float[] rotationOf(CompoundTag entityTag) {
        if (!entityTag.contains("Rotation", Tag.TAG_LIST)) {
            return new float[] { 0.0F, 0.0F };
        }
        ListTag list = entityTag.getList("Rotation", Tag.TAG_FLOAT);
        if (list.size() < 2) {
            return new float[] { 0.0F, 0.0F };
        }
        return new float[] { list.getFloat(0), list.getFloat(1) };
    }

    /**
     * 解析 F3+I / {@code /summon} 拷出来的文本，得到可以直接用的实体 NBT。
     *
     * <p>能吃下这些形式：
     * <pre>
     * /summon minecraft:pig 8.26 -2.88 42.72 {Brain:{...}, Rotation:[227.6f, 0.0f], ...}
     * minecraft:pig 8.26 -2.88 42.72 {Rotation:[227.6f, 0.0f]}
     * minecraft:pig {Saddle:1b}
     * minecraft:pig
     * {Rotation:[227.6f, 0.0f]}          // 类型取调用方给的现状值
     * </pre>
     *
     * <p>坐标会被丢掉（位置跟着方块走），{@code Pos}/{@code Motion}/{@code UUID} 也一并去掉，
     * 免得贴进来的实体带着旧的坐标和速度。
     *
     * @param fallbackType 文本里没带类型时用它（通常是界面上当前已选的类型）
     * @throws com.mojang.brigadier.exceptions.CommandSyntaxException SNBT 语法错误
     */
    public static CompoundTag parseSummonData(String text, @Nullable ResourceLocation fallbackType)
            throws CommandSyntaxException {
        String remaining = text == null ? "" : text.trim();
        if (remaining.startsWith("/")) {
            remaining = remaining.substring(1).trim();
        }
        if (remaining.startsWith("summon")) {
            remaining = remaining.substring("summon".length()).trim();
        }

        String typeId = null;
        if (!remaining.startsWith("{")) {
            // 第一个 token 若是合法 ID 就当类型；后面最多跳过 3 个坐标
            int split = indexOfWhitespace(remaining);
            String head = split < 0 ? remaining : remaining.substring(0, split);
            if (ResourceLocation.tryParse(head) != null) {
                typeId = head;
                remaining = split < 0 ? "" : remaining.substring(split).trim();
            }
            for (int i = 0; i < 3; i++) {
                int next = indexOfWhitespace(remaining);
                String token = next < 0 ? remaining : remaining.substring(0, next);
                if (!isCoordinate(token)) {
                    break;
                }
                remaining = next < 0 ? "" : remaining.substring(next).trim();
            }
        }

        CompoundTag result = remaining.isEmpty() ? new CompoundTag() : TagParser.parseTag(remaining);
        if (typeId != null) {
            result.putString(TAG_ID, typeId);
        } else if (!result.contains(TAG_ID, Tag.TAG_STRING) && fallbackType != null) {
            result.putString(TAG_ID, fallbackType.toString());
        }
        result.remove("Pos");
        result.remove("Motion");
        result.remove("UUID");
        return result;
    }

    private static int indexOfWhitespace(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    /** 形如 {@code 8.26} / {@code -2.88} / {@code ~2} 的都算坐标。 */
    private static boolean isCoordinate(String token) {
        if (token.isEmpty()) {
            return false;
        }
        String body = token.startsWith("~") ? token.substring(1) : token;
        if (body.isEmpty()) {
            return true;
        }
        try {
            Double.parseDouble(body);
            return true;
        } catch (NumberFormatException failure) {
            return false;
        }
    }

    /** 造一个只带 id 的实体 NBT（界面里选类型时用）。 */
    public static CompoundTag entityTagOf(String entityId) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_ID, entityId);
        return tag;
    }
}
