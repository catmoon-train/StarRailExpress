package net.exmo.sre.sixtyseconds.config;

import com.google.gson.annotations.SerializedName;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * 末日60秒模式的地图配置（Gson 序列化，存世界存档目录 JSON，见 {@link SixtySecondsConfigStore}）。
 * <p>
 * 授权模型：管理员手搭<b>一份</b>住宅 / 避难所 / 搜索区模板并登记其 AABB 与出生点；开局
 * {@code SixtySecondsArena} 对每队按网格偏移 {@code teamBase + idx*teamGridSpacing} 用
 * {@code BlockCopyUtils.copyLayer} 克隆出一份。门 / 物资箱等方块直接建在模板里，随克隆自动复制，无需在此登记。
 */
public class SixtySecondsConfig {

    /** 第一支队伍相对模板的克隆偏移。 */
    @SerializedName("teamBase")
    public Vec teamBase = new Vec(2048, 0, 0);

    /** 每支队伍在 X 轴上的额外偏移间距（须大于模板尺寸，避免重叠）。 */
    @SerializedName("teamGridSpacing")
    public int teamGridSpacing = 512;

    @SerializedName("residentialTemplate")
    public Region residentialTemplate;

    @SerializedName("shelterTemplate")
    public Region shelterTemplate;

    @SerializedName("searchZoneTemplate")
    public Region searchZoneTemplate;

    /** 以下均为“模板内的绝对坐标”，每队 = 该坐标 + 网格偏移。 */
    @SerializedName("residentialSpawn")
    public Vec residentialSpawn;

    @SerializedName("shelterSpawn")
    public Vec shelterSpawn;

    @SerializedName("searchZoneSpawn")
    public Vec searchZoneSpawn;

    /** 第 index（从 0 起）支队伍的网格偏移。 */
    public BlockPos teamOffset(int index) {
        return new BlockPos(teamBase.x + index * teamGridSpacing, teamBase.y, teamBase.z);
    }

    public boolean isComplete() {
        return residentialTemplate != null && shelterTemplate != null && searchZoneTemplate != null
                && residentialSpawn != null && shelterSpawn != null && searchZoneSpawn != null;
    }

    public static class Vec {
        @SerializedName("x")
        public int x;
        @SerializedName("y")
        public int y;
        @SerializedName("z")
        public int z;

        public Vec() {
        }

        public Vec(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public BlockPos toBlockPos() {
            return new BlockPos(x, y, z);
        }
    }

    public static class Region {
        @SerializedName("min")
        public Vec min = new Vec();
        /** 尺寸（各轴方块数，>=1）。 */
        @SerializedName("size")
        public Vec size = new Vec(1, 1, 1);

        public Region() {
        }

        public Region(Vec min, Vec size) {
            this.min = min;
            this.size = size;
        }

        public BoundingBox toBox() {
            int sx = Math.max(1, size.x);
            int sy = Math.max(1, size.y);
            int sz = Math.max(1, size.z);
            return new BoundingBox(min.x, min.y, min.z, min.x + sx - 1, min.y + sy - 1, min.z + sz - 1);
        }
    }
}
