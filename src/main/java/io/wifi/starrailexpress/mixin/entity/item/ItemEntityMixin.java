package io.wifi.starrailexpress.mixin.entity.item;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleMethodDispatcher;
import io.wifi.starrailexpress.cca.MurderTimeEventComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.util.BrokenGunDropUtils;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Shadow
    public abstract @Nullable Entity getOwner();

    @Shadow
    private @Nullable UUID thrower;

    @Shadow
    public abstract ItemStack getItem();

    @WrapMethod(method = "playerTouch")
    public void tmm$preventGunPickup(Player player, Operation<Void> original) {
        if (BrokenGunDropUtils.isBrokenGun(this.getItem())) {
            return;
        }
        int murderGoldAmount = MurderTimeEventComponent.getMurderGoldAmount(this.getItem());
        if (murderGoldAmount > 0) {
            if (!GameUtils.isGameRunning(player)) {
                original.call(player);
                return;
            }
            if (player instanceof ServerPlayer serverPlayer && GameUtils.isGameRunning(player)) {
                SREPlayerShopComponent.KEY.get(player).addToBalance(murderGoldAmount);
                serverPlayer.displayClientMessage(Component.translatable("message.starrailexpress.murder_gold.pickup",
                        murderGoldAmount).withStyle(ChatFormatting.GOLD), true);
                ((ItemEntity) (Object) this).discard();
            }
            return;
        }
        if (player.isCreative() || SRE.isLobby) {
            original.call(player);
            return;
        }
        if (!GameUtils.isGameRunning(player)) {
            original.call(player);
            return;
        }

        InteractionResult result = RoleMethodDispatcher.callOnPickupItem(player,
                this.getItem());
        if (result == InteractionResult.CONSUME || result == InteractionResult.FAIL
                || result == InteractionResult.CONSUME_PARTIAL) {
            return;
        } else if (result == InteractionResult.SUCCESS || result == InteractionResult.SUCCESS_NO_ITEM_USED) {
            original.call(player);
            return;
        }
        // InteractionResult.PASS (默认)时走此逻辑
        if (net.exmo.sre.sixtyseconds.SixtySecondsMod.isActive(player.level())) {
            // 60s 模式：只要背包中任意已解锁槽位有空位就能捡
            if (!sre$hasAnyFreeInventorySlot(player)) {
                return;
            }
        } else if (!MCItemsUtils.hasHotbarFreeSlot(player)) {
            // 装不下了，不准继续~
            return;
        }

        if (!this.getItem().is(TMMItemTags.GUNS)) {
            original.call(player);
            return;
        }
        if ((SREGameWorldComponent.KEY.get(player.level()).canPickUpRevolver(player)
                && !player.equals(this.getOwner()))) {
            // 在拾取物品之前调用角色的onPickupItem方法
            if (SREItemUtils.countItem(player, TMMItemTags.GUNS) > 0) {
                return;
            }
            original.call(player);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tmm$tickBrokenGunEffects(CallbackInfo ci) {
        if (!BrokenGunDropUtils.isBrokenGun(this.getItem())) {
            return;
        }
        ItemEntity item = (ItemEntity) (Object) this;
        if (!(item.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (item.tickCount >= BrokenGunDropUtils.DESPAWN_TICKS) {
            item.discard();
            return;
        }
        // 只在落地后冒少量粒子，掉落途中不显示
        if (item.onGround() && item.tickCount % 8 == 0) {
            tmm$spawnBrokenGunParticles(serverLevel, item);
        }
    }

    @Unique
    private void tmm$spawnBrokenGunParticles(ServerLevel level, ItemEntity item) {
        level.sendParticles(ParticleTypes.SMOKE,
                item.getX(), item.getY() + 0.15D, item.getZ(),
                2, 0.08D, 0.05D, 0.08D, 0.01D);
        level.sendParticles(ParticleTypes.CRIT,
                item.getX(), item.getY() + 0.1D, item.getZ(),
                1, 0.05D, 0.03D, 0.05D, 0.02D);
    }

    /** 60s 模式：检查玩家全部已解锁背包槽位是否有空位（而非仅快捷栏）。 */
    @Unique
    private static boolean sre$hasAnyFreeInventorySlot(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return true; // 客户端侧不限制
        }
        net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent stats =
                net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent.KEY.get(player);
        var phase = net.exmo.sre.sixtyseconds.state.SixtySecondsState.get(serverLevel).phase;
        int allowed;
        if (phase == net.exmo.sre.sixtyseconds.SixtySecondsPhase.PREPARATION) {
            var pos = stats.familyPosition;
            allowed = pos == null
                    ? net.exmo.sre.sixtyseconds.component.FamilyPosition.MOTHER.carryLimit
                    : pos.carryLimit;
        } else {
            allowed = stats.familyPosition == net.exmo.sre.sixtyseconds.component.FamilyPosition.FATHER
                    ? net.exmo.sre.sixtyseconds.logic.SixtySecondsInventoryLimit.DAY_ALLOWED_SLOTS
                    : net.exmo.sre.sixtyseconds.logic.SixtySecondsInventoryLimit.NON_FATHER_DAY_SLOTS;
        }
        allowed = Math.min(allowed + stats.extraUnlockedSlots,
                net.exmo.sre.sixtyseconds.logic.SixtySecondsInventoryLimit.LAST_MAIN_SLOT + 1);
        for (int i = 0; i < allowed; i++) {
            if (player.getInventory().getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
