/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;

public class ThrowingKnifeEntity extends AbstractArrow {

    private ItemStack it = null;    
    private UUID ownerUuid = null;

    public ThrowingKnifeEntity(EntityType<? extends AbstractArrow> entityType, Level level) {
        super(entityType, level);
        it = ModItems.THROWING_KNIFE.getDefaultInstance();
        this.setNoGravity(true);
        this.pickup = AbstractArrow.Pickup.DISALLOWED; // 不可被拾取
    }

    public ThrowingKnifeEntity(EntityType<? extends AbstractArrow> entityType, LivingEntity livingEntity, Level level,
            ItemStack itemStack) {
        super(entityType, livingEntity, level, itemStack, null);
        it = itemStack.copy();
        if (livingEntity != null) {
            this.ownerUuid = livingEntity.getUUID();
        }
        this.setNoGravity(true);
        this.pickup = AbstractArrow.Pickup.DISALLOWED; // 不可被拾取
    }

    @Override
    protected boolean tryPickup(Player player) {
        return false;
    }

    @Override
    public void playerTouch(Player player) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && Math.random() < 0.2) {
            level().addParticle(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
        }
        if (this.tickCount > 20 * 8) {
            this.remove(RemovalReason.DISCARDED);

        }
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);
        Player owner = getOwner() instanceof Player p ? p : null;
        if (owner == null && ownerUuid != null && level() instanceof ServerLevel serverLevel) {
            owner = serverLevel.getServer().getPlayerList().getPlayer(ownerUuid);
        }
        if (entityHitResult.getEntity() instanceof ServerPlayer serverPlayer) {
            if (owner == null || !serverPlayer.getUUID().equals(owner.getUUID())) {
                Vec3 location = entityHitResult.getLocation();
                ServerLevel serverLevel = serverPlayer.serverLevel();
                serverLevel.sendParticles(ParticleTypes.CRIT, location.x, location.y + 1.25f, location.z, 10, 0.3, 0.3,
                        0.3, 0.15);
                serverLevel.players().forEach(player -> {
                    serverLevel.playSound(player, location.x, location.y, location.z, SoundEvents.CHAIN_HIT,
                            SoundSource.PLAYERS, 1.0f, 1.0f);
                });
                GameUtils.killPlayer(serverPlayer, true,
                        owner instanceof ServerPlayer sp ? sp : null, this.deathReason());
                this.remove(RemovalReason.KILLED);
            }
        }
    }

    /** 死因归属：统一使用飞刀物品自身的注册 id 作为死亡原因。 */
    private ResourceLocation deathReason() {
        if (it != null && !it.isEmpty()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(it.getItem());
            if (id != null) {
                return id;
            }
        }
        return Noellesroles.id("throwing_knife");
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return Items.BEEF.getDefaultInstance(); // 不生成掉落物
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        if (ownerUuid != null) {
            compoundTag.putUUID("OwnerUuid", ownerUuid);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        if (compoundTag.hasUUID("OwnerUuid")) {
            ownerUuid = compoundTag.getUUID("OwnerUuid");
        }
    }
    // @Override
    // protected Item getDefaultItem() {
    // return ModItems.THROWING_KNIFE;
    // }
}
