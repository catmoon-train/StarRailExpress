// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package io.wifi.starrailexpress.content.entity.no_water_influenced;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public abstract class NoHeavyWaterInfluencedThrowableItemProjectile extends NoHeavyWaterInfluencedThrowableProjectile implements ItemSupplier {
   private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK;

   public NoHeavyWaterInfluencedThrowableItemProjectile(EntityType<? extends NoHeavyWaterInfluencedThrowableItemProjectile> entityType, Level level) {
      super(entityType, level);
   }

   public NoHeavyWaterInfluencedThrowableItemProjectile(EntityType<? extends NoHeavyWaterInfluencedThrowableItemProjectile> entityType, double d, double e, double f, Level level) {
      super(entityType, d, e, f, level);
   }

   public NoHeavyWaterInfluencedThrowableItemProjectile(EntityType<? extends NoHeavyWaterInfluencedThrowableItemProjectile> entityType, LivingEntity livingEntity, Level level) {
      super(entityType, livingEntity, level);
   }

   public void setItem(ItemStack itemStack) {
      this.getEntityData().set(DATA_ITEM_STACK, itemStack.copyWithCount(1));
   }

   protected abstract Item getDefaultItem();

   public ItemStack getItem() {
      return (ItemStack)this.getEntityData().get(DATA_ITEM_STACK);
   }

   protected void defineSynchedData(SynchedEntityData.Builder builder) {
      builder.define(DATA_ITEM_STACK, new ItemStack(this.getDefaultItem()));
   }

   public void addAdditionalSaveData(CompoundTag compoundTag) {
      super.addAdditionalSaveData(compoundTag);
      compoundTag.put("Item", this.getItem().save(this.registryAccess()));
   }

   public void readAdditionalSaveData(CompoundTag compoundTag) {
      super.readAdditionalSaveData(compoundTag);
      if (compoundTag.contains("Item", 10)) {
         this.setItem((ItemStack)ItemStack.parse(this.registryAccess(), compoundTag.getCompound("Item")).orElseGet(() -> new ItemStack(this.getDefaultItem())));
      } else {
         this.setItem(new ItemStack(this.getDefaultItem()));
      }

   }

   static {
      DATA_ITEM_STACK = SynchedEntityData.defineId(NoHeavyWaterInfluencedThrowableItemProjectile.class, EntityDataSerializers.ITEM_STACK);
   }
}
