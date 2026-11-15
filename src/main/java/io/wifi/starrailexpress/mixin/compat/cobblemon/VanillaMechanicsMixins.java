package io.wifi.starrailexpress.mixin.compat.cobblemon;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.injection.Coerce;
import net.minecraft.world.item.crafting.RecipeManager;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.minecraft.core.BlockPos;
import io.wifi.starrailexpress.compat.cobblemon.CobblemonGatedGoal;
import io.wifi.starrailexpress.compat.cobblemon.CobblemonMechanics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 只提前退出上游 handler；原版目标方法本身不取消，duck 接口和数据保存继续保留。 */
public final class VanillaMechanicsMixins {
    @Mixin(targets = "net.minecraft.world.entity.animal.Bee$BeeGrowCropGoal", priority = 1500)
    public static class Bee {
        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.BeeEntityMixin", name = "cobblemon$growBonemealableBlocks")
        @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
        private void sre$crop(CallbackInfoReturnable<BlockState> cir, @Local(argsOnly = true, ordinal = 0) BlockState original) {
            if (!CobblemonMechanics.enabled()) cir.setReturnValue(original);
        }
    }

    @Mixin(value = PointedDripstoneBlock.class, priority = 1500)
    public static class Dripstone {
        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.PointedDripstoneBlockMixin", name = "cobblemon$validateMoonStoneDripstone")
        @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
        private static void sre$dripstone(BlockState state, Block block, CallbackInfoReturnable<Boolean> cir) {
            if (!CobblemonMechanics.enabled()) cir.setReturnValue(state.is(block));
        }
    }

    @Mixin(value = Player.class, priority = 1500)
    public static class PlayerHooks {
        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.PlayerMixin", name = "onEatFood")
        @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
        private void sre$food(CallbackInfo ci) { if (!CobblemonMechanics.enabled()) ci.cancel(); }

        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.PlayerMixin", name = "isInvulnerableTo")
        @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
        private void sre$invulnerability(CallbackInfo ci) { if (!CobblemonMechanics.enabled()) ci.cancel(); }

        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.PlayerMixin", name = "cobblemon$considerHoverOnGround")
        @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
        private void sre$onGround(Player player, CallbackInfoReturnable<Boolean> cir) {
            if (!CobblemonMechanics.enabled()) cir.setReturnValue(player.onGround());
        }

        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.PlayerMixin", name = "delegateDismountToController")
        @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
        private void sre$dismount(Player player, Operation<Boolean> original, CallbackInfoReturnable<Boolean> cir) {
            if (!CobblemonMechanics.enabled()) cir.setReturnValue(original.call(player));
        }
    }

    @Mixin(value = TargetingConditions.class, priority = 1500)
    public static class Targeting {
        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.TargetingConditionsMixin", name = "test")
        @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
        private void sre$target(CallbackInfo ci) { if (!CobblemonMechanics.enabled()) ci.cancel(); }
    }

    @Mixin(value = Villager.class, priority = 1500)
    public static class Villagers {
        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.EntityVillagerMixin", name = "cobblemon$canGather")
        @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
        private void sre$gather(CallbackInfo ci) { if (!CobblemonMechanics.enabled()) ci.cancel(); }
    }

    @Mixin(value = PiglinAi.class, priority = 1500)
    public static class Bartering {
        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.PiglinBarterMixin", name = "cobblemon$acceptsForBarter")
        @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
        private static void sre$currency(CallbackInfo ci) { if (!CobblemonMechanics.enabled()) ci.cancel(); }
    }

    @Mixin(value = Piglin.class, priority = 1500)
    public static class Piglins {
        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.PiglinEntityMixin", name = "cobblemon$isValidBarteringItem")
        @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
        private void sre$hold(CallbackInfo ci) { if (!CobblemonMechanics.enabled()) ci.cancel(); }
    }

    @Mixin(value = EnderpearlItem.class, priority = 1500)
    public static class Pearls {
        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.EnderPearlItemMixin", name = "cobblemon$use")
        @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
        private void sre$pearl(CallbackInfo ci) { if (!CobblemonMechanics.enabled()) ci.cancel(); }
    }

    @Mixin(value = LecternBlock.class, priority = 1500)
    public static class Lecterns {
        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.LecternBlockMixin", name = "cobblemon$useItemOn")
        @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
        private void sre$lectern(CallbackInfo ci) { if (!CobblemonMechanics.enabled()) ci.cancel(); }
    }

    @Mixin(value = NoteBlock.class, priority = 1500)
    public static class NoteBlocks {
        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.NoteBlockMixin", name = "cobblemon$triggerTMShelfSequencer")
        @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
        private void sre$note(CallbackInfo ci) { if (!CobblemonMechanics.enabled()) ci.cancel(); }
    }

    @Mixin(value = ServerLevel.class, priority = 1500)
    public static class LightningRods {
        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.ServerLevelMixin", name = "cobblemon$findLightningRod")
        @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
        private void sre$lightning(CallbackInfo ci) { if (!CobblemonMechanics.enabled()) ci.cancel(); }
    }

    @Mixin(value = LightningBolt.class, priority = 1500)
    public static class LightningFire {
        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.LightningBoltMixin", name = "cobblemon$spawnFire")
        @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
        private void sre$fire(CallbackInfo ci) { if (!CobblemonMechanics.enabled()) ci.cancel(); }
    }

    @Mixin(value = BrewingStandBlockEntity.class, priority = 1500)
    public static class Brewing {
        @Inject(method = "cobblemon$fetchBrewingRecipe", remap = false, at = @At("HEAD"), cancellable = true)
        private static void sre$recipe(CallbackInfoReturnable<Object> cir) {
            if (!CobblemonMechanics.enabled()) cir.setReturnValue(null);
        }
        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.brewing.BrewingStandBlockEntityMixin", name = "cobblemon$canPlaceItem")
        @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
        private void sre$automation(CallbackInfo ci) { if (!CobblemonMechanics.enabled()) ci.cancel(); }
    }

    @Mixin(targets = "net.minecraft.world.inventory.BrewingStandMenu$IngredientsSlot", priority = 1500)
    public static class BrewingIngredient {
        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.brewing.IngredientSlotMixin", name = "cobblemon$mayPlace")
        @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
        private void sre$ingredient(CallbackInfo ci) { if (!CobblemonMechanics.enabled()) ci.cancel(); }
    }

    @Mixin(targets = "net.minecraft.world.inventory.BrewingStandMenu$PotionSlot", priority = 1500)
    public static class BrewingBottle {
        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.brewing.PotionSlotMixin", name = "cobblemon$mayPlace")
        @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
        private void sre$bottle(CallbackInfo ci) { if (!CobblemonMechanics.enabled()) ci.cancel(); }
    }

    @Mixin(value = BrewingStandMenu.class, priority = 1500)
    public static class BrewingTransfer {
        @TargetHandler(mixin = "com.cobblemon.mod.fabric.mixin.FabricBrewingStandMenuMixin", name = "cobblemon$isPotionBottle")
        @WrapOperation(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = "Lcom/cobblemon/mod/common/item/crafting/brewingstand/BrewingStandRecipe$Companion;isBottle(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/crafting/RecipeManager;)Z"))
        private boolean sre$shift(@Coerce Object companion, ItemStack stack, RecipeManager recipes, Operation<Boolean> original) {
            return CobblemonMechanics.enabled() && original.call(companion, stack, recipes);
        }
    }

    @Mixin(value = Creeper.class, priority = 1500)
    public static class Creepers {
        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.EntityCreeperMixin", name = "cobblemon$initGoals")
        @ModifyArg(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;addGoal(ILnet/minecraft/world/entity/ai/goal/Goal;)V"), index = 1)
        private Goal sre$avoid(Goal goal) { return new CobblemonGatedGoal(goal); }
    }

    @Mixin(value = Fox.class, priority = 1500)
    public static class Foxes {
        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.EntityFoxMixin", name = "cobblemon$initGoals")
        @ModifyArg(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;addGoal(ILnet/minecraft/world/entity/ai/goal/Goal;)V"), index = 1)
        private Goal sre$avoid(Goal goal) { return new CobblemonGatedGoal(goal); }
    }

    @Mixin(value = net.minecraft.world.entity.monster.AbstractSkeleton.class, priority = 1500)
    public static class Skeletons {
        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.EntitySkeletonMixin", name = "cobblemon$initGoals")
        @ModifyArg(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;addGoal(ILnet/minecraft/world/entity/ai/goal/Goal;)V"), index = 1)
        private Goal sre$avoid(Goal goal) { return new CobblemonGatedGoal(goal); }
    }

    @Mixin(targets = "net.minecraft.world.entity.monster.Phantom$PhantomSweepAttackGoal", priority = 1500)
    public static class Phantoms {
        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.PhantomSweepAttackGoalMixin", name = "modifyCanContinueToUse")
        @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
        private void sre$fear(CallbackInfo ci) { if (!CobblemonMechanics.enabled()) ci.cancel(); }
    }

    @Mixin(value = LivingEntity.class, priority = 1500)
    public static class Living {
        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.LivingEntityMixin", name = "cobblemon$conditionalWaterBreathing")
        @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
        private void sre$air(CallbackInfo ci) { if (!CobblemonMechanics.enabled()) ci.cancel(); }

        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.LivingEntityMixin", name = "cobblemon$preventMountSuffocation")
        @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
        private void sre$wall(LivingEntity entity, Operation<Boolean> original, CallbackInfoReturnable<Boolean> cir) {
            if (!CobblemonMechanics.enabled()) cir.setReturnValue(original.call(entity));
        }
    }

    @Mixin(value = Entity.class, priority = 1500)
    public static class Collisions {
        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.entity.EntityMixin", name = "cobblemon$customPusherLogic")
        @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
        private void sre$pusher(Entity entity, double x, double y, double z, Operation<Void> original,
                                Entity pushee, double e, double d, LocalBooleanRef pusherIsPlayer,
                                LocalBooleanRef pusheeIsPlayer, LocalFloatRef force, CallbackInfo ci) {
            if (!CobblemonMechanics.enabled()) { original.call(entity, x, y, z); ci.cancel(); }
        }

        @TargetHandler(mixin = "com.cobblemon.mod.common.mixin.entity.EntityMixin", name = "cobblemon$customPusheeLogic")
        @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
        private void sre$pushee(Entity entity, double x, double y, double z, Operation<Void> original,
                                double e, double d, LocalBooleanRef pusherIsPlayer,
                                LocalBooleanRef pusheeIsPlayer, LocalFloatRef force, CallbackInfo ci) {
            if (!CobblemonMechanics.enabled()) { original.call(entity, x, y, z); ci.cancel(); }
        }
    }
}
