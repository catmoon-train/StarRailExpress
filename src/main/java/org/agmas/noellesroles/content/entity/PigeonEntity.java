package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.item.CourierMailData;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/** 信鸽实体：手动飞行递送信件，可被击杀。 */
public class PigeonEntity extends LivingEntity {
    private static final double FLY_SPEED = 0.4;
    private static final EntityDataAccessor<Boolean> HAS_DELIVERED = SynchedEntityData.defineId(PigeonEntity.class, EntityDataSerializers.BOOLEAN);

    @Nullable private UUID targetUuid;
    @Nullable private UUID ownerUuid;
    private int life = 1200;
    private byte[] mailMessage;
    private int mailEffect;
    private int mailCost;
    @Nullable private CompoundTag attachmentItemTag;
    private boolean isReply;

    public PigeonEntity(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 10.0);
    }

    public void setTargetPlayer(Player target, Player owner, byte[] message, int effect,
            @Nullable CompoundTag attachmentTag, boolean reply, int cost) {
        this.targetUuid = target.getUUID();
        this.ownerUuid = owner.getUUID();
        this.mailMessage = message;
        this.mailEffect = effect;
        this.mailCost = cost;
        this.attachmentItemTag = attachmentTag;
        this.isReply = reply;
        this.entityData.set(HAS_DELIVERED, false);
        if (level() instanceof ServerLevel sl) {
            sl.playSound(null, this.blockPosition(), SoundEvents.PARROT_AMBIENT, SoundSource.MASTER, 1.0F, 1.0F);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(HAS_DELIVERED, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        if (--life <= 0 && !entityData.get(HAS_DELIVERED)) {
            onFailed();
            return;
        }

        // 飞向目标
        if (targetUuid != null && level() instanceof ServerLevel sl && !entityData.get(HAS_DELIVERED)) {
            Player target = sl.getPlayerByUUID(targetUuid);

            // 收件人死亡/离线/旁观者 → 退款并清除
            if (target == null || !target.isAlive() || target.isSpectator()) {
                onTargetDead(sl);
                return;
            }

            // 信使死亡/旁观者 → 直接清除信鸽
            if (ownerUuid != null) {
                Player owner = sl.getPlayerByUUID(ownerUuid);
                if (owner == null || !owner.isAlive() || owner.isSpectator()) {
                    this.discard();
                    return;
                }
            }

            if (this.distanceToSqr(target) < 3.0) {
                deliver(target);
                return;
            }
            Vec3 dir = target.position().add(0, 1.5, 0).subtract(this.position()).normalize().scale(FLY_SPEED);
            this.setPos(this.getX() + dir.x, this.getY() + dir.y, this.getZ() + dir.z);
            this.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        }

        // 飞行音效
        if (tickCount % 15 == 0) {
            level().playSound(null, this.blockPosition(), SoundEvents.PARROT_FLY, SoundSource.NEUTRAL, 0.4F, 1.0F);
        }
    }

    private void deliver(Player target) {
        this.entityData.set(HAS_DELIVERED, true);
        if (level() instanceof ServerLevel sl) {
            sl.playSound(null, target.blockPosition(), SoundEvents.PARROT_AMBIENT, SoundSource.MASTER, 1.0F, 1.2F);
        }
        // 回信用 RECEIVED_MAIL，送达后直接消失；普通信用 COURIER_MAIL
        Item itemType = isReply ? ModItems.RECEIVED_MAIL : ModItems.COURIER_MAIL;
        ItemStack mailStack = new ItemStack(itemType);
        CourierMailData.setMessage(mailStack, mailMessage != null ? new String(mailMessage, java.nio.charset.StandardCharsets.UTF_8) : "");
        CourierMailData.setEffect(mailStack, mailEffect);
        CourierMailData.setAttached(mailStack, attachmentItemTag != null);
        CourierMailData.setSender(mailStack, isReply ? "" : (ownerUuid != null ? ownerUuid.toString() : ""));
        CourierMailData.setReply(mailStack, isReply);

        // 附件物品存入信件NBT，等收信人点击"领取"时才给予
        if (attachmentItemTag != null && level() instanceof ServerLevel sl) {
            Optional<ItemStack> parsed = ItemStack.parse(sl.registryAccess(), attachmentItemTag);
            if (parsed.isPresent() && !parsed.get().isEmpty()) {
                ItemStack attached = parsed.get();
                attached.setCount(1);
                CourierMailData.setAttachmentName(mailStack, attached.getHoverName().getString());
                CourierMailData.setAttachmentItem(mailStack, attachmentItemTag);
            }
        }
        if (target.getInventory().getFreeSlot() >= 0) {
            target.getInventory().add(mailStack);
        } else {
            target.drop(mailStack, false);
        }
        this.discard();
    }

    /** 收件人死亡/离线 → 清除信鸽，返还金币、附件物品和冷却 */
    private void onTargetDead(ServerLevel sl) {
        if (ownerUuid != null) {
            Player owner = sl.getPlayerByUUID(ownerUuid);
            if (owner instanceof ServerPlayer sp) {
                // 返还金币
                if (mailCost > 0) {
                    SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
                    if (shop != null) {
                        shop.addToBalance(mailCost);
                    }
                }
                // 清除冷却
                sp.getCooldowns().removeCooldown(ModItems.COURIER_MAIL);
                // 返还附件物品
                if (attachmentItemTag != null) {
                    Optional<ItemStack> parsed = ItemStack.parse(sl.registryAccess(), attachmentItemTag);
                    if (parsed.isPresent() && !parsed.get().isEmpty()) {
                        ItemStack refund = parsed.get();
                        refund.setCount(1);
                        if (!sp.getInventory().add(refund)) {
                            sp.drop(refund, false);
                        }
                    }
                }
                sp.displayClientMessage(
                        Component.translatable("message.noellesroles.pigeon.target_dead"), true);
            }
        }
        this.discard();
    }

    private void onFailed() {
        if (ownerUuid != null && level() instanceof ServerLevel sl) {
            Player owner = sl.getPlayerByUUID(ownerUuid);
            if (owner != null) {
                owner.displayClientMessage(Component.translatable("message.noellesroles.pigeon.failed"), true);
            }
        }
        this.discard();
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        // 不受窒息、溺水等环境伤害
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_FREEZING)) {
            return true;
        }
        return source.equals(this.damageSources().inWall());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        if (this.isInvulnerableTo(source)) return false;
        boolean damaged = super.hurt(source, amount);
        if (this.getHealth() <= 0 || this.isDeadOrDying()) {
            level().playSound(null, this.blockPosition(), SoundEvents.PARROT_DEATH, SoundSource.NEUTRAL, 1.0F, 1.0F);
            onFailed();
            return true;
        }
        return damaged;
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {}
    @Override public void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return true; }
    @Override public Iterable<ItemStack> getArmorSlots() { return java.util.List.of(); }
    @Override public ItemStack getItemBySlot(EquipmentSlot slot) { return ItemStack.EMPTY; }
    @Override public void setItemSlot(EquipmentSlot slot, ItemStack stack) {}
    @Override public HumanoidArm getMainArm() { return HumanoidArm.RIGHT; }
}
