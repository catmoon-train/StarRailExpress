package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.api.hit.IsTargetObject;
import io.wifi.starrailexpress.api.hit.HitType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.command_block.CommandBlockCracks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** 命令方块裂缝。外形由客户端渲染器画，支持通用武器命中结算。 */
public class CommandBlockCrackEntity extends Entity implements IsTargetObject {
    /** 刚刷出来的高亮窗口。服务端置位，五秒后清掉；渲染器据此加一圈白光。 */
    private static final EntityDataAccessor<Boolean> HIGHLIGHTED = SynchedEntityData
            .defineId(CommandBlockCrackEntity.class, EntityDataSerializers.BOOLEAN);

    public CommandBlockCrackEntity(EntityType<? extends CommandBlockCrackEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(HIGHLIGHTED, false);
    }

    public void setHighlighted(boolean highlighted) {
        this.entityData.set(HIGHLIGHTED, highlighted);
    }

    public boolean isHighlighted() {
        return this.entityData.get(HIGHLIGHTED);
    }

    @Override
    public void tick() {
        super.tick();
        this.setDeltaMovement(0.0D, 0.0D, 0.0D);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isValidTarget(Player attacker, HitType type) {
        return !isRemoved();
    }

    @Override
    public boolean onWeaponHit(Player attacker, HitType type) {
        return attacker instanceof ServerPlayer player && CommandBlockCracks.tryShatterWithWeapon(player, this);
    }

    /** 接收箭矢等实际投射物的命中；不走原版生命值和死亡流程。 */
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (amount <= 0.0F || !(source.getEntity() instanceof ServerPlayer player)) {
            return false;
        }
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
            return CommandBlockCracks.tryShatterWithWeapon(player, this);
        }
        return player.distanceTo(this) <= 4.0D
                && CommandBlockCracks.isMeleeWeapon(player.getMainHandItem())
                && !player.getCooldowns().isOnCooldown(player.getMainHandItem().getItem())
                && CommandBlockCracks.tryShatterWithWeapon(player, this);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** 自定义名只是裂缝的识别标识，不要以名牌形式悬浮在实体上方。 */
    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
