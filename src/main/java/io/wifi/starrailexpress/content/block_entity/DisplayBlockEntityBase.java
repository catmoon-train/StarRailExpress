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

import com.mojang.math.Transformation;
import io.wifi.starrailexpress.network.DisplayBlockServerNetwork;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 展示方块（文本展示 / 方块展示）的公共方块实体。
 *
 * <p>存储格式与原版展示实体（{@code minecraft:text_display} / {@code block_display}）的 NBT
 * 完全一致，并且直接写在方块实体根上，所以可以直接抄展示实体的 NBT：
 *
 * <pre>{@code
 * /data merge block ~ ~ ~ {text:'{"text":"hi"}', billboard:"center", view_range:2.0f}
 * }</pre>
 *
 * <p>键的含义与默认值见原版 {@code net.minecraft.world.entity.Display}：
 * transformation / interpolation_duration / start_interpolation / teleport_duration / billboard /
 * view_range / shadow_radius / shadow_strength / width / height / glow_color_override / brightness。
 */
public abstract class DisplayBlockEntityBase extends BlockEntity {

    // ───────── 原版 Display 的 NBT 键 ─────────
    public static final String TAG_TRANSFORMATION = "transformation";
    public static final String TAG_INTERPOLATION_DURATION = "interpolation_duration";
    public static final String TAG_START_INTERPOLATION = "start_interpolation";
    public static final String TAG_TELEPORT_DURATION = "teleport_duration";
    public static final String TAG_BILLBOARD = "billboard";
    public static final String TAG_VIEW_RANGE = "view_range";
    public static final String TAG_SHADOW_RADIUS = "shadow_radius";
    public static final String TAG_SHADOW_STRENGTH = "shadow_strength";
    public static final String TAG_WIDTH = "width";
    public static final String TAG_HEIGHT = "height";
    public static final String TAG_GLOW_COLOR_OVERRIDE = "glow_color_override";
    public static final String TAG_BRIGHTNESS = "brightness";

    // ───────── 默认值（与原版 Display 一致） ─────────
    public static final float DEFAULT_VIEW_RANGE = 1.0F;
    public static final float DEFAULT_SHADOW_RADIUS = 0.0F;
    public static final float DEFAULT_SHADOW_STRENGTH = 1.0F;
    public static final int NO_BRIGHTNESS_OVERRIDE = -1;
    public static final int NO_GLOW_COLOR_OVERRIDE = -1;

    /** 编辑器里允许设置的最大观察距离倍率，实际渲染距离 = view_range * 64 格。 */
    public static final float MAX_VIEW_RANGE = 8.0F;
    public static final float MIN_VIEW_RANGE = 0.05F;
    /** 渲染器的观察距离上限（格），避免 view_range 过大拖慢渲染。 */
    public static final int MAX_RENDER_DISTANCE = 512;

    private static final List<String> COMMON_KEYS = List.of(
            TAG_TRANSFORMATION,
            TAG_INTERPOLATION_DURATION,
            TAG_START_INTERPOLATION,
            TAG_TELEPORT_DURATION,
            TAG_BILLBOARD,
            TAG_VIEW_RANGE,
            TAG_SHADOW_RADIUS,
            TAG_SHADOW_STRENGTH,
            TAG_WIDTH,
            TAG_HEIGHT,
            TAG_GLOW_COLOR_OVERRIDE,
            TAG_BRIGHTNESS,
            // 本地关键帧动画（自定义键，原版不认识）
            DisplayAnimation.TAG_ENABLED,
            DisplayAnimation.TAG_LENGTH,
            DisplayAnimation.TAG_LOOP,
            DisplayAnimation.TAG_START,
            DisplayAnimation.TAG_KEYFRAMES);

    /**
     * 展示数据唯一来源。只放"与原版展示实体同名"的键，序列化时整体写到方块实体根上，
     * 因此保留"键不存在 = 用默认值"的语义（例如没有 {@code brightness} 就是不覆盖亮度）。
     */
    private final CompoundTag displayData = new CompoundTag();

    /** 每次数据被重新读取 / 写入时自增，客户端渲染器靠它判断是否需要重启插值。 */
    private int dataRevision;

    /**
     * 客户端渲染器的插值/缓存状态，服务端恒为 null。
     * 类型写成 Object 是为了不让服务端类依赖客户端类；渲染器侧强转成自己的状态类。
     * 放在方块实体上而不是全局 Map 里：跟着方块实体一起被回收，而且渲染热路径上没有锁。
     */
    @Nullable
    private Object clientRenderState;

    protected DisplayBlockEntityBase(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** 客户端渲染器专用，服务端永远返回 null。 */
    @Nullable
    public final Object clientRenderState() {
        return this.clientRenderState;
    }

    /** 客户端渲染器专用。 */
    public final void setClientRenderState(@Nullable Object state) {
        this.clientRenderState = state;
    }

    /**
     * 第二个客户端槽位，给需要额外缓存（例如实体展示的假实体实例）的渲染器用。
     * 同样只在客户端写，服务端恒为 null。
     */
    @Nullable
    private Object clientContentState;

    @Nullable
    public final Object clientContentState() {
        return this.clientContentState;
    }

    public final void setClientContentState(@Nullable Object state) {
        this.clientContentState = state;
    }

    /** 该展示方块自己特有的 NBT 键（文本方块是 text 等，方块方块是 block_state）。 */
    protected abstract List<String> contentKeys();

    // ───────────────────────── 序列化 ─────────────────────────

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        CompoundTag incoming = new CompoundTag();
        for (String key : allKeys()) {
            if (tag.contains(key)) {
                incoming.put(key, tag.get(key).copy());
            }
        }
        replaceData(incoming);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (String key : displayData.getAllKeys()) {
            tag.put(key, displayData.get(key).copy());
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    private List<String> allKeys() {
        List<String> keys = new ArrayList<>(COMMON_KEYS);
        keys.addAll(contentKeys());
        return keys;
    }

    private void replaceData(CompoundTag incoming) {
        for (String key : new ArrayList<>(displayData.getAllKeys())) {
            displayData.remove(key);
        }
        displayData.merge(incoming);
        sanitize();
        dataRevision++;
    }

    /** 把越界或非法的字段夹回合理范围，避免 /data merge 写入垃圾值后渲染爆炸。 */
    protected void sanitize() {
        clampFloat(TAG_VIEW_RANGE, MIN_VIEW_RANGE, MAX_VIEW_RANGE);
        clampFloat(TAG_SHADOW_RADIUS, 0.0F, 64.0F);
        clampFloat(TAG_SHADOW_STRENGTH, 0.0F, 1.0F);
        clampFloat(TAG_WIDTH, 0.0F, 64.0F);
        clampFloat(TAG_HEIGHT, 0.0F, 64.0F);
        clampInt(TAG_INTERPOLATION_DURATION, 0, 3600);
        clampInt(TAG_START_INTERPOLATION, 0, 3600);
        clampInt(TAG_TELEPORT_DURATION, 0, 59);
        contentSanitize();
        // 关键帧列表要排序、夹起点、截断，交给动画这边统一处理。
        DisplayAnimation.sanitize(displayData);
    }

    /** 子类补充自己的字段校验。 */
    protected void contentSanitize() {
    }

    /** 夹取一个浮点字段；NaN 直接丢掉该键（回落到默认值）。 */
    protected final void clampFloat(String key, float min, float max) {
        if (displayData.contains(key, Tag.TAG_ANY_NUMERIC)) {
            float value = displayData.getFloat(key);
            if (Float.isNaN(value)) {
                displayData.remove(key);
                return;
            }
            displayData.putFloat(key, Math.max(min, Math.min(max, value)));
        }
    }

    /** 夹取一个整型字段。 */
    protected final void clampInt(String key, int min, int max) {
        if (displayData.contains(key, Tag.TAG_ANY_NUMERIC)) {
            displayData.putInt(key, Math.max(min, Math.min(max, displayData.getInt(key))));
        }
    }

    /** 子类读写自己字段用的可变内部标签，不要外传。 */
    protected final CompoundTag getTag() {
        return displayData;
    }

    // ───────────────────────── 对外访问 ─────────────────────────

    /**
     * 编辑器用的展示数据副本：缺失的键会被补上当前生效的默认值，这样界面里看到的就是实际渲染效果。
     */
    public CompoundTag getDisplayData() {
        CompoundTag tag = displayData.copy();
        fillDefaults(tag);
        return tag;
    }

    /**
     * 内部数据的只读视图，保留"键不存在 = 不覆盖"语义，渲染器每帧都要读所以不做拷贝。
     * <b>不要修改返回的标签</b>，要改数据请用 {@link #setDisplayData}。
     */
    public CompoundTag getRawDisplayData() {
        return displayData;
    }

    /**
     * 用界面传回来的数据整体替换展示数据。
     *
     * @return 内容是否真的变了。没变时调用方可以跳过方块实体更新广播（少发一次全量 NBT），
     *         也避免把区块无谓地标脏。
     */
    public boolean setDisplayData(CompoundTag incoming) {
        CompoundTag filtered = new CompoundTag();
        for (String key : allKeys()) {
            if (incoming.contains(key)) {
                filtered.put(key, incoming.get(key).copy());
            }
        }
        if (filtered.equals(displayData)) {
            return false;
        }
        replaceData(filtered);
        setChanged();
        return true;
    }

    /** 数据版本号：客户端渲染器用它判断"数据变了，需要重启插值/重建文本缓存"。 */
    public int getDataRevision() {
        return dataRevision;
    }

    /** 服务端：把展示数据发给玩家并请求打开编辑界面。 */
    public void openEditScreen(ServerPlayer player) {
        DisplayBlockServerNetwork.sendOpenUI(player, this.worldPosition, this);
    }

    /**
     * 服务端保存后把新数据推给所有正在追踪这个区块的客户端。
     * 用原版方块实体更新包（{@code ClientboundBlockEntityDataPacket}）全量同步，
     * 只有保存时才会触发，不存在逐 tick 同步。
     */
    public void syncToClients() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private void fillDefaults(CompoundTag tag) {
        if (!tag.contains(TAG_TRANSFORMATION)) {
            writeTransformation(tag, Transformation.identity());
        }
        if (!tag.contains(TAG_BILLBOARD)) {
            tag.putString(TAG_BILLBOARD, Display.BillboardConstraints.FIXED.getSerializedName());
        }
        if (!tag.contains(TAG_VIEW_RANGE)) {
            tag.putFloat(TAG_VIEW_RANGE, DEFAULT_VIEW_RANGE);
        }
        if (!tag.contains(TAG_SHADOW_RADIUS)) {
            tag.putFloat(TAG_SHADOW_RADIUS, DEFAULT_SHADOW_RADIUS);
        }
        if (!tag.contains(TAG_SHADOW_STRENGTH)) {
            tag.putFloat(TAG_SHADOW_STRENGTH, DEFAULT_SHADOW_STRENGTH);
        }
        if (!tag.contains(TAG_WIDTH)) {
            tag.putFloat(TAG_WIDTH, 0.0F);
        }
        if (!tag.contains(TAG_HEIGHT)) {
            tag.putFloat(TAG_HEIGHT, 0.0F);
        }
        if (!tag.contains(TAG_GLOW_COLOR_OVERRIDE)) {
            tag.putInt(TAG_GLOW_COLOR_OVERRIDE, NO_GLOW_COLOR_OVERRIDE);
        }
        if (!tag.contains(TAG_INTERPOLATION_DURATION)) {
            tag.putInt(TAG_INTERPOLATION_DURATION, 0);
        }
        if (!tag.contains(TAG_START_INTERPOLATION)) {
            tag.putInt(TAG_START_INTERPOLATION, 0);
        }
        if (!tag.contains(TAG_TELEPORT_DURATION)) {
            tag.putInt(TAG_TELEPORT_DURATION, 0);
        }
        if (!tag.contains(DisplayAnimation.TAG_ENABLED)) {
            tag.putBoolean(DisplayAnimation.TAG_ENABLED, false);
        }
        if (!tag.contains(DisplayAnimation.TAG_LENGTH)) {
            tag.putInt(DisplayAnimation.TAG_LENGTH, DisplayAnimation.DEFAULT_LENGTH);
        }
        if (!tag.contains(DisplayAnimation.TAG_LOOP)) {
            tag.putBoolean(DisplayAnimation.TAG_LOOP, true);
        }
        contentFillDefaults(tag);
    }

    /** 子类补自己的默认值。 */
    protected void contentFillDefaults(CompoundTag tag) {
    }

    // ───────────────────────── 静态读取工具（渲染器与界面共用） ─────────────────────────

    public static Transformation readTransformation(CompoundTag tag) {
        if (!tag.contains(TAG_TRANSFORMATION)) {
            return Transformation.identity();
        }
        return Transformation.EXTENDED_CODEC.parse(NbtOps.INSTANCE, tag.get(TAG_TRANSFORMATION))
                .resultOrPartial()
                .orElseGet(Transformation::identity);
    }

    public static void writeTransformation(CompoundTag tag, Transformation transformation) {
        Transformation.EXTENDED_CODEC.encodeStart(NbtOps.INSTANCE, transformation)
                .resultOrPartial()
                .ifPresent(encoded -> tag.put(TAG_TRANSFORMATION, encoded));
    }

    public static Display.BillboardConstraints readBillboard(CompoundTag tag) {
        if (!tag.contains(TAG_BILLBOARD, Tag.TAG_STRING)) {
            return Display.BillboardConstraints.FIXED;
        }
        return Display.BillboardConstraints.CODEC.parse(NbtOps.INSTANCE, tag.get(TAG_BILLBOARD))
                .resultOrPartial()
                .orElse(Display.BillboardConstraints.FIXED);
    }

    public static void writeBillboard(CompoundTag tag, Display.BillboardConstraints billboard) {
        Display.BillboardConstraints.CODEC.encodeStart(NbtOps.INSTANCE, billboard)
                .resultOrPartial()
                .ifPresent(encoded -> tag.put(TAG_BILLBOARD, encoded));
    }

    /** 返回打包后的亮度覆盖值，{@link #NO_BRIGHTNESS_OVERRIDE} 表示不覆盖（用方块所在位置的光照）。 */
    public static int readPackedBrightness(CompoundTag tag) {
        if (!tag.contains(TAG_BRIGHTNESS, Tag.TAG_COMPOUND)) {
            return NO_BRIGHTNESS_OVERRIDE;
        }
        return Brightness.CODEC.parse(NbtOps.INSTANCE, tag.get(TAG_BRIGHTNESS))
                .resultOrPartial()
                .map(Brightness::pack)
                .orElse(NO_BRIGHTNESS_OVERRIDE);
    }

    public static float readFloat(CompoundTag tag, String key, float fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getFloat(key) : fallback;
    }

    public static int readInt(CompoundTag tag, String key, int fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getInt(key) : fallback;
    }

    /** 游戏时间是 long（会一直增长），动画起点这类绝对值必须按 long 读，别用 int 截断。 */
    public static long readLong(CompoundTag tag, String key, long fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getLong(key) : fallback;
    }

    public static boolean readBoolean(CompoundTag tag, String key, boolean fallback) {
        return tag.contains(key, Tag.TAG_BYTE) ? tag.getBoolean(key) : fallback;
    }

    public static String readString(CompoundTag tag, String key, String fallback) {
        return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : fallback;
    }
}
