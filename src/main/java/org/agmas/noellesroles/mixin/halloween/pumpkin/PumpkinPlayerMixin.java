package org.agmas.noellesroles.mixin.halloween.pumpkin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.entity.UndeadEntity;
import org.agmas.noellesroles.game.halloween.HalloweenRuntime;
import org.agmas.noellesroles.game.halloween.pumpkin.PumpkinHeads;
import org.agmas.noellesroles.game.halloween.pumpkin.PumpkinSpiritHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 保护原版伤害与实际丢出的分栈；不是只处理快捷丢弃键。 */
@Mixin(Player.class)
public abstract class PumpkinPlayerMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void pumpkin$damage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Player victim = (Player) (Object) this;
        if (!HalloweenRuntime.isEnabled(victim.level())) return;
        Entity attacker = source.getEntity();
        for (int depth = 0; depth < 4 && attacker != null && !(attacker instanceof Player); depth++) {
            Entity previous = attacker;
            if (attacker instanceof Projectile projectile) attacker = projectile.getOwner();
            else if (attacker instanceof UndeadEntity undead) attacker = undead.getOwner();
            else if (attacker instanceof OwnableEntity owned) attacker = owned.getOwner();
            else break;
            if (attacker == previous) break;
        }
        if (attacker instanceof Player killer && PumpkinSpiritHandler.preventsKill(victim, killer)) {
            if (killer instanceof ServerPlayer actor && victim instanceof ServerPlayer target)
                PumpkinSpiritHandler.tryPumpkinAttack(actor, target);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void pumpkin$restoreOnDeath(DamageSource source, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer sp) PumpkinHeads.restore(sp);
    }

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void pumpkin$dropHead(ItemStack stack, boolean random, boolean retainOwner,
            CallbackInfoReturnable<ItemEntity> cir) {
        if (PumpkinHeads.temporary(stack)) cir.setReturnValue(null);
        else if ((Object) this instanceof ServerPlayer sp) PumpkinSpiritHandler.onRewardStack(sp, stack);
    }

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("RETURN"))
    private void pumpkin$actualCandyDrop(ItemStack stack, boolean random, boolean retainOwner,
            CallbackInfoReturnable<ItemEntity> cir) {
        if ((Object) this instanceof ServerPlayer sp && cir.getReturnValue() != null) {
            ItemEntity entity = cir.getReturnValue();
            PumpkinSpiritHandler.markThrownCandy(sp, entity.getItem());
            // setItem 标记实体同步数据为 dirty，不能只原地改栈而不通知追踪客户端。
            entity.setItem(entity.getItem());
        }
    }
}
