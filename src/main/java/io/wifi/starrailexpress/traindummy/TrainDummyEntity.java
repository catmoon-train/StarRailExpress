/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package io.wifi.starrailexpress.traindummy;

import com.mojang.authlib.GameProfile;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.DevItems;
import io.wifi.starrailexpress.index.TMMEntities;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

 import java.util.ArrayList;
 import java.util.HashMap;
 import java.util.HashSet;
 import java.util.List;
 import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

/**
 * 列车假人：使用玩家模型和皮肤，但不是玩家。
 * 虚拟血量、护盾、中毒都用实体自己的计时器，不跟对局计时。
 */
public class TrainDummyEntity extends LivingEntity {

    public static final int VANILLA_HEALTH = 100;
    public static final int REVIVE_TICKS = 100;
    public static final int CORPSE_TICKS = 600;
    public static final int HIT_FLASH_TICKS = 60;
    public static final float MIN_SCALE = 0.0625F;
    public static final float MAX_SCALE = 16.0F;

    private static final EntityDataAccessor<String> SKIN_NAME = SynchedEntityData.defineId(TrainDummyEntity.class,
            EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> SKIN_UUID = SynchedEntityData.defineId(
            TrainDummyEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> SLIM = SynchedEntityData.defineId(TrainDummyEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<ItemStack> HELD = SynchedEntityData.defineId(TrainDummyEntity.class,
            EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> HIDDEN = SynchedEntityData.defineId(TrainDummyEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> VIRTUAL_HP = SynchedEntityData.defineId(TrainDummyEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VIRTUAL_MAX = SynchedEntityData.defineId(TrainDummyEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VIRTUAL_SHIELD = SynchedEntityData.defineId(TrainDummyEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SHIELD = SynchedEntityData.defineId(TrainDummyEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TIMED_LAYERS = SynchedEntityData.defineId(TrainDummyEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TIMED_TICKS = SynchedEntityData.defineId(TrainDummyEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> WEAK_LAYERS = SynchedEntityData.defineId(TrainDummyEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> WEAK_TICKS = SynchedEntityData.defineId(TrainDummyEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> WEAK_REASONS = SynchedEntityData.defineId(TrainDummyEntity.class,
            EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> POISON_TICKS = SynchedEntityData.defineId(TrainDummyEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FAKE_POISON = SynchedEntityData.defineId(TrainDummyEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> HIT_FLASH = SynchedEntityData.defineId(TrainDummyEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DEATH_REASON = SynchedEntityData.defineId(TrainDummyEntity.class,
            EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DISTANCE_TENTHS = SynchedEntityData.defineId(TrainDummyEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SHIELD_BROKEN_FLASH = SynchedEntityData.defineId(
            TrainDummyEntity.class, EntityDataSerializers.INT);

    public static final EntityType<TrainDummyEntity> TYPE = registerType();

    private boolean corpseOnDeath;
    private int reviveTicks;
     private int hiddenTicks;
     private boolean corpseSpawnedThisDown;
     private int zoneDwell;
     private long zoneSeenGameTime = -1L;
     private final Map<UUID, Integer> nunchuckHits = new HashMap<>();
     private final Set<UUID> nunchuckNearBlock = new HashSet<>();
    @Nullable
    private UUID poisonerId;

    private boolean moving;
    private int direction;
    private boolean loop = true;
    private int moveBlocks = 1;
    private boolean randomMove;
    private boolean blink;
    private int breakpoints = 1;
    private int blinkInterval = 20;
    private Vec3 origin = Vec3.ZERO;
    private double travel;
    private boolean forward = true;
    private int blinkCooldown;
    private int randomCooldown = 40;
    private boolean vanillaDeath;

    public TrainDummyEntity(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setCustomNameVisible(true);
        if (this.getAttribute(Attributes.MAX_HEALTH) != null) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(VANILLA_HEALTH);
            this.setHealth(VANILLA_HEALTH);
        }
    }

    private static boolean ready;

    public static void init() {
        if (ready) {
            return;
        }
        ready = true;
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(TYPE,
                createAttributes());
        TrainDummyCorpses.init();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.ATTACK_DAMAGE, 0.0D);
    }

    public static EntityType<TrainDummyEntity> registerType() {
        return Registry.register(
                BuiltInRegistries.ENTITY_TYPE,
                SRE.id("train_dummy"),
                EntityType.Builder.of(TrainDummyEntity::new, MobCategory.MISC)
                        .sized(0.6F, 1.8F)
                        .eyeHeight(1.62F)
                        .clientTrackingRange(64)
                        .updateInterval(2)
                        .build("train_dummy"));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN_NAME, "");
        builder.define(SKIN_UUID, Optional.empty());
        builder.define(SLIM, false);
        builder.define(HELD, ItemStack.EMPTY);
        builder.define(HIDDEN, false);
        builder.define(VIRTUAL_HP, 20);
        builder.define(VIRTUAL_MAX, 20);
        builder.define(VIRTUAL_SHIELD, 0);
        builder.define(SHIELD, 0);
        builder.define(TIMED_LAYERS, 0);
        builder.define(TIMED_TICKS, 0);
        builder.define(WEAK_LAYERS, 0);
        builder.define(WEAK_TICKS, 0);
        builder.define(WEAK_REASONS, "");
        builder.define(POISON_TICKS, 0);
        builder.define(FAKE_POISON, false);
        builder.define(HIT_FLASH, 0);
        builder.define(DEATH_REASON, "");
        builder.define(DISTANCE_TENTHS, -1);
        builder.define(SHIELD_BROKEN_FLASH, 0);
    }

    public void applySettings(TrainDummySettings settings, ItemStack held) {
        this.corpseOnDeath = settings.corpseOnDeath();
        int max = Math.max(1, settings.virtualHp());
        this.entityData.set(VIRTUAL_MAX, max);
        this.entityData.set(VIRTUAL_HP, max);
        this.entityData.set(VIRTUAL_SHIELD, Math.max(0, settings.virtualShield()));
        this.entityData.set(SHIELD, Math.max(0, settings.shieldLayers()));
        int timedLayers = Math.max(0, settings.timedLayers());
        this.entityData.set(TIMED_LAYERS, timedLayers);
        this.entityData.set(TIMED_TICKS, timedLayers > 0 ? Math.max(0, settings.timedTicks()) : 0);
        int weakLayers = Math.max(0, settings.weakLayers());
        this.entityData.set(WEAK_LAYERS, weakLayers);
        this.entityData.set(WEAK_TICKS, weakLayers > 0 ? Math.max(0, settings.weakTicks()) : 0);
        this.entityData.set(WEAK_REASONS, settings.weakReason() == null ? "" : settings.weakReason());
        this.entityData.set(HELD, held == null ? ItemStack.EMPTY : held.copy());
        this.moving = settings.moving();
        this.direction = settings.direction();
        this.loop = settings.loop();
        this.moveBlocks = Math.max(1, Math.min(50, settings.distance()));
        this.randomMove = settings.randomMove();
        this.blink = settings.blink();
        this.breakpoints = Math.max(1, settings.breakpoints());
        this.blinkInterval = Math.max(1, settings.blinkInterval());
        this.origin = this.position();
        this.travel = 0.0D;
        this.forward = true;
        float scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, settings.scale()));
        if (this.getAttribute(Attributes.SCALE) != null) {
            this.getAttribute(Attributes.SCALE).setBaseValue(scale);
        }
        if (this.getAttribute(Attributes.MAX_HEALTH) != null) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(VANILLA_HEALTH);
        }
        if (this.getAttribute(Attributes.GRAVITY) != null) {
            this.getAttribute(Attributes.GRAVITY).setBaseValue(0.0D);
        }
        if (this.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }
        this.setHealth(VANILLA_HEALTH);
        this.refreshDimensions();
        this.setNoGravity(true);
        this.setCustomName(Component.translatable("entity.starrailexpress.train_dummy"));
        this.setCustomNameVisible(true);
        if (settings.skinName() != null && !settings.skinName().isBlank() && this.level() instanceof ServerLevel server) {
            TrainDummySkins.fetch(server.getServer(), settings.skinName().trim(), this::applySkin);
        }
    }

    public void applySkin(GameProfile profile, boolean slim) {
        if (profile == null) {
            return;
        }
        this.entityData.set(SKIN_NAME, profile.getName() == null ? "" : profile.getName());
        this.entityData.set(SKIN_UUID, Optional.ofNullable(profile.getId()));
        this.entityData.set(SLIM, slim);
    }

    public boolean canReceiveHits() {
        return this.isAlive() && !this.isHidden() && !this.vanillaDeath;
    }

    public boolean isHidden() {
        return this.entityData.get(HIDDEN);
    }

    public void setHidden(boolean hidden) {
        this.entityData.set(HIDDEN, hidden);
        this.setInvisible(hidden);
        this.noPhysics = hidden;
        this.setCustomNameVisible(!hidden);
    }

    public int virtualHp() {
        return this.entityData.get(VIRTUAL_HP);
    }

    public int virtualMax() {
        return Math.max(1, this.entityData.get(VIRTUAL_MAX));
    }

    public void setVirtualHp(int hp) {
        this.entityData.set(VIRTUAL_HP, Math.max(0, Math.min(this.virtualMax(), hp)));
    }

    public int virtualShield() {
        return this.entityData.get(VIRTUAL_SHIELD);
    }

    public int absorbVirtualShield(int damage) {
        int shield = this.virtualShield();
        if (damage <= 0 || shield <= 0) {
            return damage;
        }
        int absorbed = Math.min(shield, damage);
        this.entityData.set(VIRTUAL_SHIELD, shield - absorbed);
        return damage - absorbed;
    }

    public int shieldLayers() {
        return this.entityData.get(SHIELD);
    }

    public int timedLayers() {
        return this.entityData.get(TIMED_LAYERS);
    }

    public int timedTicks() {
        return this.entityData.get(TIMED_TICKS);
    }

    public int totalShieldLayers() {
        return this.shieldLayers() + this.timedLayers();
    }

    public boolean consumeShieldLayer() {
        int timed = this.timedLayers();
        if (timed > 0) {
            this.entityData.set(TIMED_LAYERS, timed - 1);
            if (timed - 1 <= 0) {
                this.entityData.set(TIMED_TICKS, 0);
            }
            return true;
        }
        int shield = this.shieldLayers();
        if (shield > 0) {
            this.entityData.set(SHIELD, shield - 1);
            return true;
        }
        return false;
    }

    public void clearShields() {
        this.entityData.set(SHIELD, 0);
        this.entityData.set(TIMED_LAYERS, 0);
        this.entityData.set(TIMED_TICKS, 0);
        this.entityData.set(WEAK_LAYERS, 0);
        this.entityData.set(WEAK_TICKS, 0);
        this.entityData.set(WEAK_REASONS, "");
    }

    public int weakLayers() {
        return this.entityData.get(WEAK_LAYERS);
    }

    public int weakTicks() {
        return this.entityData.get(WEAK_TICKS);
    }

    public String weakReasons() {
        return this.entityData.get(WEAK_REASONS);
    }

    public boolean weakCanBlock(ResourceLocation reason) {
        if (this.weakLayers() <= 0 || this.weakTicks() <= 0 || reason == null) {
            return false;
        }
        String raw = this.weakReasons();
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String id = reason.toString();
        for (String part : raw.split(",")) {
            if (id.equals(part.trim())) {
                return true;
            }
        }
        return false;
    }

    public void consumeWeakLayer() {
        int layers = Math.max(0, this.weakLayers() - 1);
        this.entityData.set(WEAK_LAYERS, layers);
        if (layers <= 0) {
            this.entityData.set(WEAK_TICKS, 0);
            this.entityData.set(WEAK_REASONS, "");
        }
    }

    public int poisonTicks() {
        return this.entityData.get(POISON_TICKS);
    }

    public boolean fakePoison() {
        return this.entityData.get(FAKE_POISON);
    }

    public void setPoison(int ticks, boolean fake, @Nullable UUID poisoner) {
        if (ticks <= 0) {
            this.entityData.set(POISON_TICKS, 0);
            this.entityData.set(FAKE_POISON, false);
            this.poisonerId = null;
            return;
        }
        if (!fake) {
            this.entityData.set(FAKE_POISON, false);
        } else if (this.poisonTicks() <= 0) {
            this.entityData.set(FAKE_POISON, true);
        }
        this.entityData.set(POISON_TICKS, ticks);
        this.poisonerId = poisoner;
    }
 
     /** 毒区停留。中断一 tick 就重新计；同一 tick 重复调用不累加。 */
     public int noteZoneDwell() {
         long now = this.level().getGameTime();
         if (this.zoneSeenGameTime == now) {
             return this.zoneDwell;
         }
         if (this.zoneSeenGameTime != now - 1L) {
             this.zoneDwell = 0;
         }
         this.zoneSeenGameTime = now;
         return ++this.zoneDwell;
     }
 
     public void resetZoneDwell() {
         this.zoneDwell = 0;
         this.zoneSeenGameTime = this.level().getGameTime();
     }
 
     /** 毒区内额外衰减。减到 0 时立刻结算，避免和实体 tick 各触发一次。 */
     public void acceleratePoison(int extra) {
         int ticks = this.poisonTicks();
         if (ticks <= 0 || extra <= 0) {
             return;
         }
         int next = ticks - extra;
         if (next > 0) {
             this.entityData.set(POISON_TICKS, next);
             return;
         }
         this.finishPoison();
     }
 
     public void finishPoison() {
         if (this.poisonTicks() <= 0 && this.poisonerId == null && !this.fakePoison()) {
             return;
         }
         boolean fake = this.fakePoison();
         UUID poisoner = this.poisonerId;
         this.entityData.set(POISON_TICKS, 0);
         this.entityData.set(FAKE_POISON, false);
         this.poisonerId = null;
         if (!fake) {
             Player source = poisoner == null || this.level().getServer() == null
                     ? null
                     : this.level().getServer().getPlayerList().getPlayer(poisoner);
             TrainDummyCombat.applyLethal(this, source, GameConstants.DeathReasons.POISON);
         }
     }
 
     /**
      * 双节棍命中计数，规则与玩家组件一致：第四下，或贴墙后再打一下，才致死。
      *
      * @return 这一下是否应该致死
      */
     public boolean noteNunchuckHit(UUID attacker, boolean nearBlock) {
         if (attacker == null) {
             return false;
         }
         Integer previous = this.nunchuckHits.get(attacker);
         boolean wasNear = this.nunchuckNearBlock.contains(attacker);
         if (previous == null) {
             this.nunchuckHits.put(attacker, 1);
             if (nearBlock) {
                 this.nunchuckNearBlock.add(attacker);
             }
             return false;
         }
         if (previous >= 3 || wasNear) {
             this.nunchuckHits.remove(attacker);
             this.nunchuckNearBlock.remove(attacker);
             return true;
         }
         int next = previous + 1;
         this.nunchuckHits.put(attacker, next);
         if (nearBlock) {
             this.nunchuckNearBlock.add(attacker);
         } else {
             this.nunchuckNearBlock.remove(attacker);
         }
         return next >= 3;
     }

    public int hitFlash() {
        return this.entityData.get(HIT_FLASH);
    }

    public String deathReasonId() {
        return this.entityData.get(DEATH_REASON);
    }

    public int distanceTenths() {
        return this.entityData.get(DISTANCE_TENTHS);
    }

    public int shieldBrokenFlash() {
        return this.entityData.get(SHIELD_BROKEN_FLASH);
    }

    public String skinName() {
        return this.entityData.get(SKIN_NAME);
    }

    public Optional<UUID> skinUuid() {
        return this.entityData.get(SKIN_UUID);
    }

    public boolean slim() {
        return this.entityData.get(SLIM);
    }

    public void showHit(@Nullable Entity attacker) {
        this.entityData.set(HIT_FLASH, HIT_FLASH_TICKS);
        if (attacker instanceof Player player) {
            this.entityData.set(DISTANCE_TENTHS, (int) Math.round(player.distanceTo(this) * 10.0D));
        } else {
            this.entityData.set(DISTANCE_TENTHS, -1);
        }
    }

    public void showShieldBroken() {
        this.entityData.set(SHIELD_BROKEN_FLASH, HIT_FLASH_TICKS);
    }

    public void killByReason(@Nullable Player attacker, ResourceLocation reason) {
        boolean alreadyDown = this.virtualHp() <= 0 && this.reviveTicks > 0;
        this.setVirtualHp(0);
        this.entityData.set(DEATH_REASON, reason == null ? "" : reason.toString());
        this.reviveTicks = REVIVE_TICKS;
        if (this.corpseOnDeath && !alreadyDown && !this.corpseSpawnedThisDown) {
            this.spawnCorpse(attacker, reason);
            this.corpseSpawnedThisDown = true;
            this.hiddenTicks = CORPSE_TICKS;
            this.setHidden(true);
        }
    }

    public void restoreVirtualHp() {
        this.setVirtualHp(this.virtualMax());
        this.entityData.set(DEATH_REASON, "");
        this.reviveTicks = 0;
        this.corpseSpawnedThisDown = false;
    }

    private void spawnCorpse(@Nullable Player attacker, ResourceLocation reason) {
        if (!(this.level() instanceof ServerLevel level) || reason == null) {
            return;
        }
        var body = TMMEntities.PLAYER_BODY.create(level);
        if (body == null) {
            return;
        }
        body.setCustomName(Component.translatable("entity.starrailexpress.train_dummy"));
        body.setCustomNameVisible(true);
        this.skinUuid().ifPresent(body::setPlayerUuid);
        var component = body.getComponent();
        component.setDeathReason(reason.toString(), false);
        if (attacker != null) {
            component.setKillerUuid(attacker.getUUID(), false);
        }
        component.setOwnerName("trl:entity.starrailexpress.train_dummy", false);
        if (body.getAttribute(Attributes.SCALE) != null) {
            body.getAttribute(Attributes.SCALE).setBaseValue(this.getScale());
        }
        body.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
        level.addFreshEntity(body);
        component.sync();
        TrainDummyCorpses.track(body, CORPSE_TICKS);
    }

    @Override
    public void tick() {
        this.setNoGravity(true);
        if (!this.level().isClientSide()) {
            this.setDeltaMovement(Vec3.ZERO);
            if (!this.vanillaDeath && this.getHealth() < this.getMaxHealth()) {
                this.setHealth(this.getMaxHealth());
            }
            this.tickTimers();
            if (this.canReceiveHits()) {
                this.tickMovement();
            }
        }
        super.tick();
        if (!this.level().isClientSide()) {
            this.setDeltaMovement(Vec3.ZERO);
            this.fallDistance = 0.0F;
        }
    }

    private void tickTimers() {
        int hit = this.hitFlash();
        if (hit > 0) {
            this.entityData.set(HIT_FLASH, hit - 1);
        }
        int broken = this.shieldBrokenFlash();
        if (broken > 0) {
            this.entityData.set(SHIELD_BROKEN_FLASH, broken - 1);
        }
        if (this.timedLayers() > 0) {
            int ticks = this.timedTicks() - 1;
            if (ticks <= 0) {
                this.entityData.set(TIMED_LAYERS, 0);
                this.entityData.set(TIMED_TICKS, 0);
            } else {
                this.entityData.set(TIMED_TICKS, ticks);
            }
        }
        if (this.weakLayers() > 0) {
            int ticks = this.weakTicks() - 1;
            if (ticks <= 0) {
                this.entityData.set(WEAK_LAYERS, 0);
                this.entityData.set(WEAK_TICKS, 0);
                this.entityData.set(WEAK_REASONS, "");
            } else {
                this.entityData.set(WEAK_TICKS, ticks);
            }
        }
         if (this.poisonTicks() > 0) {
             int ticks = this.poisonTicks() - 1;
             this.entityData.set(POISON_TICKS, Math.max(0, ticks));
             if (ticks <= 0) {
                 this.finishPoison();
             }
         }
        if (this.reviveTicks > 0) {
            this.reviveTicks--;
            if (this.reviveTicks <= 0) {
                this.restoreVirtualHp();
            }
        }
        if (this.hiddenTicks > 0) {
            this.hiddenTicks--;
            if (this.hiddenTicks <= 0) {
                this.setHidden(false);
            }
        }
    }

    private void tickMovement() {
        if (!this.moving) {
            return;
        }
        Vec3 axis = axis(this.direction);
        if (this.blink) {
            this.blinkCooldown--;
            if (this.blinkCooldown > 0) {
                return;
            }
            this.blinkCooldown = this.blinkInterval;
            double step = (double) this.moveBlocks / this.breakpoints;
            this.advance(step);
            this.teleportAlong(axis, true);
            return;
        }
        this.advance(0.1D);
        if (this.randomMove) {
            this.randomCooldown--;
            if (this.randomCooldown <= 0) {
                this.randomCooldown = 40;
                this.nudgeHorizontal();
            }
        }
        this.teleportAlong(axis, false);
    }

    private void advance(double step) {
        this.travel += this.forward ? step : -step;
        if (this.travel >= this.moveBlocks) {
            this.travel = this.moveBlocks;
            if (this.loop) {
                this.forward = false;
            } else {
                this.moving = false;
            }
        } else if (this.travel <= 0.0D) {
            this.travel = 0.0D;
            this.forward = true;
        }
    }

    private void teleportAlong(Vec3 axis, boolean randomBlink) {
        Vec3 dest = this.origin.add(axis.scale(this.travel));
        if (randomBlink && this.randomMove) {
            int amount = 1 + this.random.nextInt(3);
            if (this.random.nextBoolean()) {
                amount = -amount;
            }
            if (this.random.nextBoolean()) {
                dest = dest.add(amount, 0.0D, 0.0D);
            } else {
                dest = dest.add(0.0D, 0.0D, amount);
            }
        }
        this.teleportTo(dest.x, dest.y, dest.z);
        this.face(axis);
    }

    private void nudgeHorizontal() {
        int amount = 1 + this.random.nextInt(3);
        if (this.random.nextBoolean()) {
            amount = -amount;
        }
        boolean alongX = this.random.nextBoolean();
        double dx = alongX ? amount : 0.0D;
        double dz = alongX ? 0.0D : amount;
        this.teleportTo(this.getX() + dx, this.getY(), this.getZ() + dz);
        this.origin = new Vec3(this.origin.x + dx, this.origin.y, this.origin.z + dz);
    }

    private void face(Vec3 axis) {
        if (axis.y != 0.0D) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-axis.x, axis.z));
        this.setYRot(yaw);
        this.setYBodyRot(yaw);
        this.setYHeadRot(yaw);
    }

    private static Vec3 axis(int direction) {
        return switch (Math.floorMod(direction, 6)) {
            case 0 -> new Vec3(1, 0, 0);
            case 1 -> new Vec3(0, 0, 1);
            case 2 -> new Vec3(-1, 0, 0);
            case 3 -> new Vec3(0, 0, -1);
            case 4 -> new Vec3(0, 1, 0);
            default -> new Vec3(0, -1, 0);
        };
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.vanillaDeath || this.isHidden()) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (attacker instanceof Player player && player.getMainHandItem().is(DevItems.TRAIN_DUMMY_TOOL)) {
            this.discard();
            return true;
        }
        if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            this.vanillaDeath = true;
            this.setHealth(0.0F);
            this.die(source);
            return true;
        }
        this.setHealth(this.getMaxHealth());
        this.invulnerableTime = 0;
        this.level().broadcastEntityEvent(this, (byte) 2);
        if (attacker instanceof Player) {
            this.showHit(attacker);
        }
        return true;
    }

    @Override
    public void die(DamageSource source) {
        if (!this.vanillaDeath) {
            this.setHealth(this.getMaxHealth());
            return;
        }
        super.die(source);
        this.discard();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return !this.isHidden() && !this.vanillaDeath;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return this.canReceiveHits();
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (this.isHidden()) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return List.of();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND) {
            return this.entityData.get(HELD);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        if (slot == EquipmentSlot.MAINHAND) {
            this.entityData.set(HELD, stack == null ? ItemStack.EMPTY : stack.copy());
        }
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    protected EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
        return EntityDimensions.scalable(0.6F, 1.8F);
    }

    public static int clearNear(ServerPlayer player, double radius) {
        if (player == null) {
            return 0;
        }
        List<TrainDummyEntity> found = player.serverLevel().getEntitiesOfClass(TrainDummyEntity.class,
                player.getBoundingBox().inflate(radius), dummy -> dummy.distanceTo(player) <= radius);
        for (TrainDummyEntity dummy : new ArrayList<>(found)) {
            dummy.discard();
        }
        return found.size();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
         tag.putBoolean("CorpseOnDeath", this.corpseOnDeath);
         tag.putBoolean("CorpseSpawned", this.corpseSpawnedThisDown);
         tag.putInt("Revive", this.reviveTicks);
         tag.putInt("HiddenTicks", this.hiddenTicks);
        tag.putBoolean("Moving", this.moving);
        tag.putInt("Direction", this.direction);
        tag.putBoolean("Loop", this.loop);
        tag.putInt("MoveBlocks", this.moveBlocks);
        tag.putBoolean("RandomMove", this.randomMove);
        tag.putBoolean("Blink", this.blink);
        tag.putInt("Breakpoints", this.breakpoints);
        tag.putInt("BlinkInterval", this.blinkInterval);
        tag.putDouble("Travel", this.travel);
        tag.putBoolean("Forward", this.forward);
        tag.putDouble("OriginX", this.origin.x);
        tag.putDouble("OriginY", this.origin.y);
        tag.putDouble("OriginZ", this.origin.z);
        if (this.poisonerId != null) {
            tag.putUUID("Poisoner", this.poisonerId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
         this.corpseOnDeath = tag.getBoolean("CorpseOnDeath");
         this.corpseSpawnedThisDown = tag.getBoolean("CorpseSpawned");
         this.reviveTicks = tag.getInt("Revive");
         this.hiddenTicks = tag.getInt("HiddenTicks");
        this.moving = tag.getBoolean("Moving");
        this.direction = tag.getInt("Direction");
        this.loop = !tag.contains("Loop") || tag.getBoolean("Loop");
        this.moveBlocks = Math.max(1, tag.getInt("MoveBlocks"));
        this.randomMove = tag.getBoolean("RandomMove");
        this.blink = tag.getBoolean("Blink");
        this.breakpoints = Math.max(1, tag.getInt("Breakpoints"));
        this.blinkInterval = Math.max(1, tag.getInt("BlinkInterval"));
        this.travel = tag.getDouble("Travel");
        this.forward = !tag.contains("Forward") || tag.getBoolean("Forward");
        this.origin = new Vec3(tag.getDouble("OriginX"), tag.getDouble("OriginY"), tag.getDouble("OriginZ"));
        this.poisonerId = tag.hasUUID("Poisoner") ? tag.getUUID("Poisoner") : null;
        this.setHidden(this.entityData.get(HIDDEN));
        this.setNoGravity(true);
    }
}
