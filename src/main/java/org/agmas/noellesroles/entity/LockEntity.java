package org.agmas.noellesroles.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;

/**
 * 锁实体
 * 包含锁物品的长度、强度
 * 撬锁时需要按顺序撬动每个锁芯因此:
 * 平均尝试次数 = (n+1)/2 次/锁芯
 * 总尝试次数 = n×(n+1)/2 次
 * 强度决定了每次撬动撬锁器损坏的概率，因此完整开锁的失败概率为：
 * P(fail) = 1/n * ∑n,k=1 [1 - (1 -p) ^ k] = 1 - 1/n * （1 - p)/p * [1 - (1 -
 * p)^n]
 * 当长度为6，强度在0.05~0.1时，失败概率为15%~30%
 */
public class LockEntity extends Entity {
    public LockEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.length = 2;
        this.resistance = 0.1f;
        resetSeries();
    }

    public LockEntity(EntityType<?> entityType, Level level, int length) {
        super(entityType, level);
        this.length = length;
        this.resistance = 0.1f;
        resetSeries();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {

    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compoundTag) {

    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compoundTag) {

    }

    public void resetSeries() {
        series = new ArrayList<>(length);
        for (int i = 0; i < length; ++i)
            series.add(-1);
        RandomSource entityRandom = this.getRandom();
        for (int i = 0, randomIdx = entityRandom.nextInt(length); i < length; ++i) {
            while (series.get(randomIdx) != -1)
                randomIdx = entityRandom.nextInt(length);
            series.set(randomIdx, i);
        }
    }

    /** 获取第 idx 个该被解锁的锁索引 */
    public int getSeriesUnlockIdx(int idx) {
        return series.get(idx);
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        if (length < 2)
            return;
        this.length = length;
        resetSeries();
    }

    public float getResistance() {
        return resistance;
    }

    public void setResistance(float resistance) {
        if (resistance < 0)
            this.resistance = 0.01f;
        this.resistance = resistance;
    }

    // TODO : 该序列每次使用并不是固定的，得修一下
    private ArrayList<Integer> series;
    // 锁的长度 ：必须大于1
    private int length;
    // 锁的抗性 ：撬锁器损坏的概率
    private float resistance;
}
