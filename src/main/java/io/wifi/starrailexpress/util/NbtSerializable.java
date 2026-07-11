package io.wifi.starrailexpress.util;

import net.minecraft.nbt.CompoundTag;

/**
 * 实现此接口的类可以自定义 NBT 序列化和反序列化行为。
 * 优先于默认的反射遍历，但低于通过 Builder 注册的适配器。
 */
public interface NbtSerializable {
    /**
     * 将对象的数据写入到给定的 CompoundTag 中。
     * @param tag 写入目标
     */
    void writeNbt(CompoundTag tag);

    /**
     * 从给定的 CompoundTag 中读取数据并填充到当前对象。
     * @param tag 数据来源
     */
    void readNbt(CompoundTag tag);
}