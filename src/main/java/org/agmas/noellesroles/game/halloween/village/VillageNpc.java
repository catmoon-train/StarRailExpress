package org.agmas.noellesroles.game.halloween.village;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.halloween.HalloweenVillage;

/** 活动专用、无存档 NPC；固定兑换与临时发糖共用万圣残月形象，不查询在线档案。 */
public final class VillageNpc extends PathfinderMob {
    // 仅控制领糖交互与寿命，不控制外观或显示名称。
    private static final EntityDataAccessor<Boolean> CANDY = SynchedEntityData.defineId(
            VillageNpc.class, EntityDataSerializers.BOOLEAN);
    private boolean eventManaged;

    /** 仅活动创建的实例参与活动归属清理；/summon 展示实例不应首 tick 消失。 */
    public void markEventManaged() { eventManaged = true; }

    public VillageNpc(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        setInvulnerable(true);
        setSilent(true);
        setPersistenceRequired();
        // 不受推挤由 isPushable 控制；仍须参与方块碰撞，不能用 noPhysics 禁用墙体碰撞。
        noPhysics = false;
    }

    public static AttributeSupplier.Builder attributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CANDY, false);
    }

    public boolean isCandy() { return entityData.get(CANDY); }
    public void setCandy(boolean candy) { entityData.set(CANDY, candy); }
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return HalloweenVillage.interact(this, player, hand);
    }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }

    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && eventManaged && !HalloweenVillage.owns(this)) discard();
    }
}
