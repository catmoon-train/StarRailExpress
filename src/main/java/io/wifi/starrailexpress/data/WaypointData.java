package io.wifi.starrailexpress.data;

import com.google.gson.annotations.SerializedName;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

public class WaypointData {

    @SerializedName("path")
    private String path;
    
    @SerializedName("name")
    private String name;
    
    @SerializedName("x")
    private int x;
    
    @SerializedName("y")
    private int y;
    
    @SerializedName("z")
    private int z;
    
    @SerializedName("color")
    private int color;

    public static final StreamCodec<ByteBuf, WaypointData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs::optional).map(
                opt -> opt.orElse("default"), 
                val -> val != null && !val.isEmpty() ? java.util.Optional.of(val) : java.util.Optional.empty()
            ), WaypointData::getPath,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs::optional).map(
                opt -> opt.orElse("default"), 
                val -> val != null && !val.isEmpty() ? java.util.Optional.of(val) : java.util.Optional.empty()
            ), WaypointData::getName,
            BlockPos.STREAM_CODEC, WaypointData::getPos,
            ByteBufCodecs.INT, WaypointData::getColor,
            WaypointData::new
    );
    
    public WaypointData(String path, String name, BlockPos pos, int color) {
        this.path = path;
        this.name = name;
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
        this.color = color;
    }
    
    // 为Gson序列化添加getter方法
    public String getPath() {
        return path;
    }
    
    public String getName() {
        return name;
    }
    
    public int getX() {
        return x;
    }
    
    public int getY() {
        return y;
    }
    
    public int getZ() {
        return z;
    }
    
    public BlockPos getPos() {
        return new BlockPos(x, y, z);
    }
    
    public int getColor() {
        return color;
    }
    
    // 为Gson反序列化添加setter方法
    public void setPath(String path) {
        this.path = path;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public void setX(int x) {
        this.x = x;
    }
    
    public void setY(int y) {
        this.y = y;
    }
    
    public void setZ(int z) {
        this.z = z;
    }
    
    public void setColor(int color) {
        this.color = color;
    }
}