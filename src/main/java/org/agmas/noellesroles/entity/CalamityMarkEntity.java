package org.agmas.noellesroles.entity;


import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.component.TrapperPlayerComponent;
import  org.agmas.noellesroles.role.ModRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 灾厄印记实体
 * 
 * 隐形陷阱，当非设陷者玩家踩中时触发：
 * - 发出巨响暴露位置
 * - 给予发光效果
 * - 囚禁玩家（时间随触发次数递增）
 */
public class CalamityMarkEntity extends Entity {
    
    /** 所有者 UUID */
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(
        CalamityMarkEntity.class, EntityDataSerializers.OPTIONAL_UUID
    );
    
    /** 陷阱触发半径（格） */
    public static final double TRIGGER_RADIUS = 1.0;
    
    /** 陷阱存在时间上限（5分钟 = 6000 tick），防止无限存在 */
    public static final int MAX_LIFETIME = 6000;
    
    /** 存活时间计数器 */
    private int lifetime = 0;
    
    /** 所有者玩家引用（缓存） */
    private Player ownerCache = null;
    
    public CalamityMarkEntity(EntityType<?> type, Level world) {
        super(type, world);
        this.setInvisible(true); // 对所有人隐形
        this.setNoGravity(true); // 无重力
    }
    
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER_UUID, Optional.empty());
    }
    
    /**
     * 设置所有者
     */
    public void setOwner(Player owner) {
        if (owner != null) {
            this.entityData.set(OWNER_UUID, Optional.of(owner.getUUID()));
            this.ownerCache = owner;
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
    
    @Override
    public void tick() {
        super.tick();
        
        if (level().isClientSide()) return;
        
        // 增加存活时间
        lifetime++;
        if (lifetime > MAX_LIFETIME) {
            this.discard();
            return;
        }
        
        // 检查所有者是否还是设陷者
        Player owner = getOwner();
        if (owner == null) {
            this.discard();
            return;
        }
        
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level());
        if (!gameWorld.isRole(owner, ModRoles.TRAPPER)) {
            this.discard();
            return;
        }
        
        // 检测触发
        checkTrigger();
    }
    
    /**
     * 检测是否有玩家触发陷阱
     */
    private void checkTrigger() {
        Level world = level();
        Vec3 pos = this.position();
        
        // 创建检测区域
        AABB detectionBox = new AABB(
            pos.x - TRIGGER_RADIUS, pos.y - 0.5, pos.z - TRIGGER_RADIUS,
            pos.x + TRIGGER_RADIUS, pos.y + 2.0, pos.z + TRIGGER_RADIUS
        );
        
        // 获取区域内的所有玩家
        List<Player> players = world.getEntitiesOfClass(
            Player.class, detectionBox,
            player -> {
                // 排除所有者
                Optional<UUID> ownerUuid = getOwnerUuid();
                if (ownerUuid.isPresent() && player.getUUID().equals(ownerUuid.get())) {
                    return false;
                }
                
                // 排除死亡或观察者模式的玩家
                if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                    return false;
                }
                
                // 排除其他杀手阵营玩家（同阵营不触发）
                SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(world);
                if (gameWorld.isKillerTeam(player)) {
                    return false;
                }
                
                return true;
            }
        );
        
        // 如果有玩家触发
        if (!players.isEmpty()) {
            Player victim = players.getFirst();
            triggerTrap(victim);
        }
    }
    
    /**
     * 触发陷阱
     */
    private void triggerTrap(Player victim) {
        Player owner = getOwner();
        if (owner == null) {
            this.discard();
            return;
        }
        
        // 获取设陷者组件并触发效果
        TrapperPlayerComponent trapperComp = ModComponents.TRAPPER.get(owner);
        trapperComp.onTrapTriggered(victim, this.position());
        
        // 陷阱触发后消失（一次性）
        this.discard();
    }
    
    /**
     * 该实体是否对指定玩家可见
     * 只有设陷者本人可以看到自己的陷阱（半透明效果在客户端渲染）
     */
    public boolean isVisibleTo(Player player) {
        Optional<UUID> ownerUuid = getOwnerUuid();
        return ownerUuid.isPresent() && player.getUUID().equals(ownerUuid.get());
    }
    
    @Override
    public boolean isInvisibleTo(Player player) {
        // 对所有非所有者玩家隐形
        return !isVisibleTo(player);
    }
    
    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        if (nbt.contains("OwnerUUID")) {
            try {
                UUID uuid = UUID.fromString(nbt.getString("OwnerUUID"));
                this.entityData.set(OWNER_UUID, Optional.of(uuid));
            } catch (IllegalArgumentException ignored) {}
        }
        this.lifetime = nbt.contains("Lifetime") ? nbt.getInt("Lifetime") : 0;
    }
    
    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        Optional<UUID> ownerUuid = getOwnerUuid();
        ownerUuid.ifPresent(uuid -> nbt.putString("OwnerUUID", uuid.toString()));
        nbt.putInt("Lifetime", this.lifetime);
    }
    
    @Override
    public boolean isPickable() {
        return false; // 不能被点击
    }
    
    @Override
    public boolean isPushable() {
        return false; // 不能被推动
    }
    
    @Override
    public boolean canBeCollidedWith() {
        return false; // 无碰撞
    }
}