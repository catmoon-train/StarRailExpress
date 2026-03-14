package org.agmas.noellesroles.entity;

import com.mojang.authlib.GameProfile;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.component.PuppeteerPlayerComponent;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 傀儡本体实体
 * 
 * 当傀儡师使用假人技能时，本体会在原位置生成一个本体实体。
 * 这个实体使用玩家的模型和皮肤，可以被攻击。
 * 如果本体被杀死，傀儡师也会死亡。
 */
public class PuppeteerBodyEntity extends LivingEntity {

    /** 所有者 UUID */
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(
            PuppeteerBodyEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /** 皮肤 GameProfile（用于渲染玩家皮肤） */
    private GameProfile skinProfile = null;

    /** 所有者玩家名称 */
    private String ownerName = "";

    /** 最大存活时间（10分钟 = 12000 tick），防止无限存在 */
    public static final int MAX_LIFETIME = 12000;

    /** 存活时间计数器 */
    private int lifetime = 0;

    /** 所有者玩家引用（缓存） */
    private Player ownerCache = null;

    public PuppeteerBodyEntity(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
        this.setNoGravity(false); // 有重力
        this.setHealth(20.0F); // 20点生命值（和玩家一样）
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID, Optional.empty());
    }

    /**
     * 设置所有者
     */
    public void setOwner(Player owner) {
        if (owner != null) {
            this.entityData.set(OWNER_UUID, Optional.of(owner.getUUID()));
            this.ownerCache = owner;
            this.ownerName = owner.getName().getString();

            // 设置皮肤（获取玩家的 GameProfile）
            if (owner instanceof ServerPlayer serverPlayer) {
                this.skinProfile = serverPlayer.getGameProfile();
            }

            // 设置自定义名称
            this.setCustomName(Component.translatable("entity.manipulator_body.name", owner.getName()));
            this.setCustomNameVisible(false);
        }
    }

    /**
     * 获取所有者 UUID
     */
    public Optional<UUID> getOwnerUuid() {
        return this.entityData.get(OWNER_UUID);
    }

    /**
     * 获取所有者玩家
     */
    public Player getOwner() {
        if (ownerCache != null && ownerCache.isAlive()) {
            return ownerCache;
        }

        Optional<UUID> ownerUuid = getOwnerUuid();
        if (ownerUuid.isPresent()) {
            ownerCache = level().getPlayerByUUID(ownerUuid.get());
            return ownerCache;
        }
        return null;
    }

    /**
     * 获取皮肤 GameProfile（用于客户端渲染）
     */
    public GameProfile getSkinProfile() {
        return skinProfile;
    }

    /**
     * 获取所有者名称
     */
    public String getOwnerName() {
        return ownerName;
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide())
            return;

        final var gameWorldComponent = SREGameWorldComponent.KEY.get(level());
        if (gameWorldComponent != null) {
            if (!gameWorldComponent.isRunning()) {
                discard();

            }
        }
        // 增加存活时间
        lifetime++;
        if (lifetime > MAX_LIFETIME) {
            this.discard();
            return;
        }

        // 检查所有者是否还存在
        Player owner = getOwner();
        if (owner == null || !owner.isAlive()) {
            this.discard();
            return;
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide())
            return false;

        // 调用父类处理伤害
        boolean result = super.hurt(source, amount);

        // 如果死亡，通知傀儡师
        if (this.isDeadOrDying()) {
            Player owner = getOwner();
            if (owner != null) {
                // 通知傀儡师组件本体死亡
                PuppeteerPlayerComponent puppeteerComp = ModComponents.PUPPETEER.get(owner);
                puppeteerComp.onBodyDeath();
            }
        }

        return result;
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);

        // 确保通知傀儡师
        Player owner = getOwner();
        if (owner != null) {
            PuppeteerPlayerComponent puppeteerComp = ModComponents.PUPPETEER.get(owner);
            puppeteerComp.onBodyDeath();
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);

        if (nbt.contains("OwnerUUID")) {
            this.entityData.set(OWNER_UUID, Optional.of(nbt.getUUID("OwnerUUID")));
        }
        if (nbt.contains("OwnerName")) {
            this.ownerName = nbt.getString("OwnerName");
        }
        // SkinProfile 通过 OwnerUUID 在客户端动态获取，不需要从 NBT 加载
        this.lifetime = nbt.contains("Lifetime") ? nbt.getInt("Lifetime") : 0;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);

        Optional<UUID> ownerUuid = getOwnerUuid();
        ownerUuid.ifPresent(uuid -> nbt.putUUID("OwnerUUID", uuid));
        nbt.putString("OwnerName", this.ownerName);
        // SkinProfile 通过 OwnerUUID 在客户端动态获取，不需要保存到 NBT
        nbt.putInt("Lifetime", this.lifetime);
    }

    @Override
    public boolean isPickable() {
        return true; // 可以被击中
    }

    @Override
    public boolean isPushable() {
        return false; // 不能被推动
    }

    @Override
    public boolean isAttackable() {
        return true; // 可以被攻击
    }

    @Override
    public boolean canBeHitByProjectile() {
        return true; // 可以被远程武器击中（手枪、弓箭等）
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        // 只对虚空伤害免疫（防止掉入虚空时不死亡）
        if (source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)) {
            return false; // 不免疫虚空伤害，让实体正常死亡
        }
        // 对其他所有伤害都不免疫
        return false;
    }

    @Override
    public Iterable<net.minecraft.world.item.ItemStack> getArmorSlots() {
        return java.util.Collections.emptyList();
    }

    @Override
    public net.minecraft.world.item.ItemStack getItemBySlot(net.minecraft.world.entity.EquipmentSlot slot) {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.world.item.ItemStack stack) {
        // 不装备任何物品
    }

    @Override
    public net.minecraft.world.entity.HumanoidArm getMainArm() {
        return net.minecraft.world.entity.HumanoidArm.RIGHT;
    }
}