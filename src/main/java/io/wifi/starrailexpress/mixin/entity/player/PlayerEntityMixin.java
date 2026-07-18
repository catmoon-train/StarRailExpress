package io.wifi.starrailexpress.mixin.entity.player;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.util.Either;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.cca.SREWeakArmorPlayerComponent;
import io.wifi.starrailexpress.content.item.CocktailItem;
import io.wifi.starrailexpress.content.item.api.SREItemProperties;
import io.wifi.starrailexpress.event.AllowPlayerPunching;
import io.wifi.starrailexpress.event.IsPlayerPunchable;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.PlayerStaminaGetter;
import io.wifi.starrailexpress.util.PoisonComponentUtils;
import io.wifi.starrailexpress.util.Scheduler;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.HoneyBottleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.level.Level;

import org.agmas.noellesroles.content.entity.WheelchairEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(Player.class)
public abstract class PlayerEntityMixin extends LivingEntity implements PlayerStaminaGetter {

    @Shadow
    public abstract float getAttackStrengthScale(float baseTime);

    @Override
    public float starrailexpress$getStamina() {
        return sprintingTicks;
    }

    @Override
    public void starrailexpress$setStamina(float value) {
        this.sprintingTicks = value;
    }

    @Unique
    public float sprintingTicks;
    @Unique
    private Scheduler.ScheduledTask poisonSleepTask;

    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
    }

    @ModifyReturnValue(method = "getSpeed", at = @At("RETURN"))
    public float tmm$overrideMovementSpeed(float original) {
        if (SRE.isLobby) {
            return original;
        }
        final var player = (Player) (Object) this;
        if (GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)) {
            float speedModifier = 1.0f;

            SREPlayerMoodComponent srePlayerMoodComponent = SREPlayerMoodComponent.KEY.get(player);
            if (srePlayerMoodComponent.isLowerThanDepressed()) {
                speedModifier *= 0.8f;
            } else if (srePlayerMoodComponent.isHigherThanAngry()) {
                speedModifier *= 1.2f;
            }
            if (player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
                final var speedEffect = player.getEffect(MobEffects.MOVEMENT_SPEED);
                speedModifier *= (1f + (speedEffect.getAmplifier() + 1) * 0.25f);
            }

            if (player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                final var slowEffect = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
                speedModifier *= (1 - (slowEffect.getAmplifier() + 1) * 0.2f);
                if (speedModifier < 0)
                    speedModifier = 0;
            }

            return this.isSprinting() ? 0.1f * speedModifier : 0.07f * speedModifier;
        } else {
            return original;
        }
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    public void tmm$limitSprint(CallbackInfo ci) {
        if (SRE.isLobby) {
            return;
        }
        SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(this.level());
        // 60s 模式不启用体力系统
        if (SixtySecondsMod.MODE != null && gameComponent != null && gameComponent.getGameMode() == SixtySecondsMod.MODE) {
            return;
        }
        final var player = (Player) (Object) this;
        if (GameUtils.isPlayerAliveAndSurvival(player) && gameComponent != null && gameComponent.isRunning()) {
            SRERole role = gameComponent.getRole(player);
            int maxSprintTime = Integer.MAX_VALUE;
            if (role != null) {
                maxSprintTime = role.getMaxSprintTime(player);
            }
            boolean hasInfiniteStaminaEffect = ModEffects.hasInfiniteStamina(player);
            if (role != null && (maxSprintTime == Integer.MAX_VALUE || hasInfiniteStaminaEffect)) {
                return;
            }
            if (role != null && maxSprintTime >= 0) {
                if (sprintingTicks == -1) {
                    sprintingTicks = maxSprintTime;
                }
                float maxStaminaMultiplier = ModEffects.getStaminaCapacityMultiplier(player);
                float maxSprintTimeWithEffects = maxSprintTime * maxStaminaMultiplier;
                float staminaRecoveryRate = 0.4f * ModEffects.getStaminaRecoveryMultiplier(player);

                if (this.isSprinting()) {
                    sprintingTicks = Math.max(sprintingTicks - 1, 0);
                } else {
                    sprintingTicks = Math.min(sprintingTicks + staminaRecoveryRate, maxSprintTimeWithEffects);
                }

                sprintingTicks = Math.min(sprintingTicks, maxSprintTimeWithEffects);

                if (sprintingTicks <= 0) {
                    this.setSprinting(false);
                }
            }
        }

    }

    @WrapMethod(method = "attack")
    public void attack(Entity ttarget, Operation<Void> original) {
        if (SRE.isLobby) {
            original.call(ttarget);
            return;
        }
        Player self = (Player) (Object) this;
        Entity target = ttarget;
        if (!self.isCreative()) {
            if (target instanceof WheelchairEntity wc) {
                if (wc.getRider() != null) {
                    target = wc.getRider();
                }
            }
        }
        if ((self.isSpectator() || self.isCreative()) || this.getMainHandItem().is(TMMItems.KNIFE)
                || this.getMainHandItem().getItem() instanceof SREItemProperties.LeftClickHurtable
                || IsPlayerPunchable.EVENT.invoker().gotPunchable(target)
                || AllowPlayerPunching.EVENT.invoker().allowPunching(self)) {
            // 在攻击实体之前调用角色的左键点击实体方法
            if (this.getMainHandItem().getItem() instanceof SREItemProperties.LeftClickHurtable itt) {
                var result = itt.onTryHurt(self, target, this.getMainHandItem());
                if (result == InteractionResult.CONSUME || result == InteractionResult.FAIL) {
                    return;
                }
            }
            var result = io.wifi.starrailexpress.api.RoleMethodDispatcher.callLeftClickEntity(self, target);
            if (result != InteractionResult.CONSUME)
                original.call(target);
        }
    }

    @Inject(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/food/FoodProperties;)V", shift = At.Shift.AFTER))
    private void tmm$poisonedFoodEffect(@NotNull Level world, ItemStack stack, FoodProperties foodComponent,
            CallbackInfoReturnable<ItemStack> cir) {
        if (world.isClientSide)
            return;
        if (SRE.isLobby)
            return;
        var player = (Player) (Object) this;
        String poisoner = stack.getOrDefault(SREDataComponentTypes.POISONER, null);
        String armorer = stack.getOrDefault(SREDataComponentTypes.ARMORER, null);
        String weakArmorer = stack.getOrDefault(SREDataComponentTypes.WEAK_ARMORER, null);
        boolean isFakePoison = stack.getOrDefault(SREDataComponentTypes.FAKE_POISON, false);
        if (poisoner != null) {
            int poisonTicks = SREPlayerPoisonComponent.KEY.get(this).poisonTicks;
            if (SRE.REPLAY_MANAGER != null) {
                if (armorer == null) {
                    SRE.REPLAY_MANAGER.recordItemEatFlaggedItem(player, stack.getItem(), "poison");
                }
            }
            UUID poisonerUUID = UUID.fromString(poisoner);
            if (poisonTicks == -1) {
                int randomTicks = world.getRandom().nextIntBetweenInclusive(SREPlayerPoisonComponent.clampTime.getA(),
                        SREPlayerPoisonComponent.clampTime.getB());
                if (isFakePoison) {
                    SREPlayerPoisonComponent.KEY.get(this).setFakePoisonTicks(randomTicks, poisonerUUID);
                } else {
                    SREPlayerPoisonComponent.KEY.get(this).setPoisonTicks(randomTicks, poisonerUUID);
                }
            } else {
                // 如果玩家当前处于假毒状态，且受到真毒，则真毒直接覆盖假毒（重新计时）
                boolean currentlyFakePoisoned = SREPlayerPoisonComponent.KEY.get(this).fakePoison;
                if (currentlyFakePoisoned && !isFakePoison) {
                    // 真毒覆盖假毒，使用全新计时
                    int randomTicks = world.getRandom().nextIntBetweenInclusive(SREPlayerPoisonComponent.clampTime.getA(),
                            SREPlayerPoisonComponent.clampTime.getB());
                    SREPlayerPoisonComponent.KEY.get(this).setPoisonTicks(randomTicks, poisonerUUID);
                } else {
                    int reducedTicks = Mth.clamp(poisonTicks - world.getRandom().nextIntBetweenInclusive(100, 300), 0,
                            SREPlayerPoisonComponent.clampTime.getB());
                    if (isFakePoison) {
                        SREPlayerPoisonComponent.KEY.get(this).setFakePoisonTicks(reducedTicks, poisonerUUID);
                    } else {
                        SREPlayerPoisonComponent.KEY.get(this).setPoisonTicks(reducedTicks, poisonerUUID);
                    }
                }
            }
            // this.playSound(SoundEvents.WITCH_DRINK, 1f, 1f);
        }
        if (armorer != null) {
            if (SRE.REPLAY_MANAGER != null) {
                if (poisoner == null) {
                    SRE.REPLAY_MANAGER.recordItemEatFlaggedItem(player, stack.getItem(), "armor");
                }
            }
            SREArmorPlayerComponent bartenderPlayerComponent = SREArmorPlayerComponent.KEY.get(this);
            // this.playSound(SoundEvents.SHIELD_BLOCK, 1f, 1f);
            bartenderPlayerComponent.giveArmor();
        }
        if (weakArmorer != null) {
            if (SRE.REPLAY_MANAGER != null) {
                SRE.REPLAY_MANAGER.recordItemEatFlaggedItem(player, stack.getItem(), "weak_armor");
            }
            SREWeakArmorPlayerComponent weakComponent = SREWeakArmorPlayerComponent.KEY.get(this);
            weakComponent.giveWeakArmor(SREWeakArmorPlayerComponent.VIAL_WEAK_ARMOR_DURATION,
                    new java.util.HashSet<>(java.util.Set.of(
                            io.wifi.starrailexpress.game.GameConstants.DeathReasons.KNIFE)));
        }
        if (poisoner != null && armorer != null) {
            SRE.REPLAY_MANAGER.recordItemEatFlaggedItem(player, stack.getItem(), "poison_and_armor");
        }

    }

    @Inject(method = "stopSleepInBed(ZZ)V", at = @At("HEAD"))
    private void tmm$poisonSleep(boolean skipSleepTimer, boolean updateSleepingPlayers, CallbackInfo ci) {
        if (this.poisonSleepTask != null) {
            this.poisonSleepTask.cancel();
            this.poisonSleepTask = null;
        }
    }

    @Inject(method = "startSleepInBed", at = @At("TAIL"))
    private void tmm$poisonSleepMessage(BlockPos pos,
            CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> cir) {
        Player self = (Player) (Object) (this);
        if (cir.getReturnValue().right().isPresent() && self instanceof ServerPlayer serverPlayer) {
            if (this.poisonSleepTask != null)
                this.poisonSleepTask.cancel();

            this.poisonSleepTask = Scheduler.schedule(
                    () -> PoisonComponentUtils.bedPoison(serverPlayer),
                    40);
        }
    }

    @Inject(method = "canEat(Z)Z", at = @At("HEAD"), cancellable = true)
    private void tmm$allowEatingRegardlessOfHunger(boolean ignoreHunger, @NotNull CallbackInfoReturnable<Boolean> cir) {
        if (SRE.isLobby) {
            return;
        }

        cir.setReturnValue(true);
    }

    @Inject(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"))
    private void tmm$eat(Level world, ItemStack stack, FoodProperties foodComponent,
            @NotNull CallbackInfoReturnable<ItemStack> cir) {
        if (SRE.isLobby) {
            return;
        }
        if (stack.getItem() instanceof CocktailItem) {
            return;
        }
        if (stack.getItem() instanceof PotionItem || stack.getItem() instanceof HoneyBottleItem) {
            SREPlayerMoodComponent.KEY.get(this).drinkCocktail();
            return;
        }
        if (stack.get(DataComponents.FOOD) != null) {
            SREPlayerMoodComponent.KEY.get(this).eatFood();
            return;
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void tmm$saveData(CompoundTag nbt, CallbackInfo ci) {
        nbt.putFloat("sprintingTicks", this.sprintingTicks);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void tmm$readData(CompoundTag nbt, CallbackInfo ci) {
        this.sprintingTicks = nbt.getFloat("sprintingTicks");
    }
}