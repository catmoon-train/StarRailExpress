package org.agmas.noellesroles.game.roles.innocence.cake_maker;

import com.mojang.math.Transformation;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.util.PlayerStaminaGetter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.scene.MapStatusBarRuntime;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cake Maker role component — manages the smoker, cake-baking process, and placed cakes.
 * <p>
 * Smoker placement and cooking are separated:
 * placing the smoker only deploys the block visually; the 40-second cooking timer
 * starts when the first wheat is inserted.
 * <p>
 * Baking stages:
 * <ol>
 *   <li>Stage 0 — add up to 3 wheat (first wheat starts timer), then wait 3s</li>
 *   <li>Stage 2 — add 1 egg, then add up to 2 sugar, then wait 3s</li>
 *   <li>Stage 4 — add up to 3 milk buckets, then wait 5s</li>
 *   <li>Stage 5 — cake complete (handled in server tick)</li>
 * </ol>
 */
public final class CakeMakerComponent implements RoleComponent, ServerTickingComponent {

    // ── Constants ──────────────────────────────────────────────
    public static final ComponentKey<CakeMakerComponent> KEY =
            ComponentRegistry.getOrCreate(Noellesroles.id("cake_maker"), CakeMakerComponent.class);

    private static final int SMOKER_DURATION_TICKS  = 40 * 20;
    private static final int SMOKER_COOLDOWN_TICKS  = 60 * 20;

    /** 3-second lock between ingredient batches */
    private static final int WAIT_SHORT_TICKS = 60;
    /** 5-second lock after final ingredients */
    private static final int WAIT_LONG_TICKS  = 100;

    private static final int CAKE_PLACEMENT_TICKS = 60 * 20;  // 1 minute
    private static final int EAT_COOLDOWN_TICKS   = 600; // 30 s
    private static final int SPEED_DURATION_TICKS = 20 * 20;
    private static final int MAX_CAKE_BITES       = 6;
    private static final int MOOD_RESTORE_PCT     = 30;
    private static final int STATUS_BAR_MAX       = 20;
    private static final double INTERACT_DISTANCE_SQ = 16.0;

    private static final int WHEAT_NEEDED  = 3;
    private static final int SUGAR_NEEDED  = 2;
    private static final int MILK_NEEDED   = 3;

    /** Tag applied to cake maker smoker block-display and interaction entities */
    public static final String SMOKER_ENTITY_TAG = "cake_maker_smoker";

    /** Interaction entity is slightly larger than the smoker block (1.0×1.0) to be easy to click. */
    private static final double INTERACTION_WIDTH  = 1.2;
    private static final double INTERACTION_HEIGHT = 1.2;

    /**
     * Server-side smoker entity registry — maps interaction entity UUID to owner info.
     * Similar to {@code CuckooEggData}, avoids needing persistent-data on the entity.
     */
    public static final class SmokerEntityInfo {
        public final UUID ownerUuid;
        public Display.BlockDisplay displayEntity;
        public Interaction interactionEntity;

        public SmokerEntityInfo(UUID ownerUuid) {
            this.ownerUuid = ownerUuid;
        }
    }

    /** interactionEntity UUID → SmokerEntityInfo */
    private static final Map<UUID, SmokerEntityInfo> SMOKER_ENTITIES = new ConcurrentHashMap<>();

    // ── Cake entity constants ──────────────────────────────────

    /** Tag applied to cake maker cake block-display and interaction entities */
    public static final String CAKE_ENTITY_TAG = "cake_maker_cake";

    /** Cake block is 0.5 blocks tall, interaction slightly larger */
    private static final double CAKE_INTERACTION_WIDTH  = 1.1;
    private static final double CAKE_INTERACTION_HEIGHT = 0.7;

    /**
     * Server-side cake entity registry — maps interaction entity UUID to cake info.
     */
    public static final class CakeEntityInfo {
        public final UUID cakeId;
        public final UUID ownerUuid;
        public Display.BlockDisplay displayEntity;
        public Interaction interactionEntity;

        public CakeEntityInfo(UUID cakeId, UUID ownerUuid) {
            this.cakeId = cakeId;
            this.ownerUuid = ownerUuid;
        }
    }

    /** interactionEntity UUID → CakeEntityInfo */
    private static final Map<UUID, CakeEntityInfo> CAKE_ENTITIES = new ConcurrentHashMap<>();

    // ── State ──────────────────────────────────────────────────
    private final Player player;
    public int cooldown;
    public int smokerTicks;
    public int smokerIdle;
    public int lockedTicks;
    public int stage;
    public int wheat;
    public int sugar;
    public int milk;
    public BlockPos smokerPos;
    public UUID smokerId;
    /** UUID of the interaction entity for this player's active smoker */
    public UUID interactionEntityId;
    public final Map<UUID, Cake> cakes = new HashMap<>();
    public final Map<UUID, Integer> eatCooldowns = new HashMap<>();

    // ── Construction ───────────────────────────────────────────
    public CakeMakerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    // ── CCA lifecycle ─────────────────────────────────────────

    @Override
    public void init() {
        cooldown           = 0;
        smokerTicks        = 0;
        smokerIdle         = 0;
        lockedTicks        = 0;
        stage              = 0;
        wheat              = 0;
        sugar              = 0;
        milk               = 0;
        smokerPos          = null;
        smokerId           = null;
        interactionEntityId = null;
    }

    @Override
    public void clear() {
        // Remove the deployed smoker entities and all placed cake entities
        // so they don't linger in the world (and into the next round).
        removeSmoker();
        for (Map.Entry<UUID, Cake> entry : cakes.entrySet()) {
            removeCakeEntities(entry.getKey(), entry.getValue());
        }
        cakes.clear();
        eatCooldowns.clear();
        init();
    }

    /**
     * Called when the cake maker dies. Cancels any in-progress baking and removes the
     * deployed smoker so it doesn't linger in the world (and into the next round).
     * Placed cakes are left for others to eat.
     */
    public void onDeath() {
        smokerTicks = 0;
        lockedTicks = 0;
        stage       = 0;
        wheat       = 0;
        sugar       = 0;
        milk        = 0;
        removeSmoker();
    }

    // ── NBT serialisation ─────────────────────────────────────

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("cooldown", cooldown);
        tag.putInt("smoker", smokerTicks);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        cooldown    = tag.getInt("cooldown");
        smokerTicks = tag.getInt("smoker");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) { }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) { }

    // ── Server tick ────────────────────────────────────────────

    @Override
    public void serverTick() {
        if (!SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.CAKE_MAKER)) {
            return;
        }

        // Tick cooldowns
        if (cooldown > 0) {
            cooldown--;
            if (cooldown % 20 == 0 || cooldown == 0) sync();
        }

        // Idle timeout: 40 seconds without ingredients → smoker disappears.
        // Skips while locked (waiting period between stages) since input is blocked.
        if (smokerId != null && lockedTicks == 0 && smokerIdle > 0) {
            smokerIdle--;
            if (smokerIdle == 0) {
                if (player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(
                            Component.translatable("message.noellesroles.cake_maker.smoker_idle")
                                    .withStyle(ChatFormatting.RED),
                            true);
                }
                removeSmoker();
            }
        }

        // Cooking timer: only ticks after the first ingredient has been inserted
        if (smokerTicks > 0) {
            smokerTicks--;
            // Smoker expired mid-cooking
            if (smokerTicks == 0 && smokerId != null) {
                removeSmoker();
            }
        }

        eatCooldowns.replaceAll((id, t) -> Math.max(0, t - 1));

        // Expire cakes whose placement lifetime has ended
        long now = player.level().getGameTime();
        boolean cakeRemoved = cakes.entrySet().removeIf(entry -> {
            Cake cake = entry.getValue();
            if (now - cake.placedGameTime >= CAKE_PLACEMENT_TICKS) {
                removeCakeEntities(entry.getKey(), cake);
                return true;
            }
            return false;
        });
        if (cakeRemoved) {
            sendMessage("message.noellesroles.cake_maker.cake_disappeared");
        }

        // Locked period elapsed → advance baking stage
        if (lockedTicks > 0 && --lockedTicks == 0) {
            onLockedPeriodEnd();
        }
    }

    /** Fired when the ingredient-input lock expires. Advances the baking sequence. */
    private void onLockedPeriodEnd() {
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 1.0F);

        if (stage == 1) {
            stage = 2;
            sendMessage("message.noellesroles.cake_maker.add_egg_sugar");
        } else if (stage == 3) {
            stage = 4;
            sendMessage("message.noellesroles.cake_maker.add_milk");
        } else if (stage == 5) {
            giveCakeAndCleanUp();
        }
    }

    private void giveCakeAndCleanUp() {
        player.getInventory().add(Items.CAKE.getDefaultInstance());
        smokerTicks = 0;
        removeSmoker();
        stage = 0;
        sendMessage("message.noellesroles.cake_maker.complete");
    }

    // ── Skill key — deploy smoker / place cake ────────────────

    /**
     * Called when the player presses the skill key.
     * <ul>
     *   <li>If holding a cake → place it on the ground.</li>
     *   <li>If holding a smoker and off cooldown → deploy server-side smoker entities.</li>
     * </ul>
     */
    public boolean useSmoker() {
        if (player.getMainHandItem().is(Items.CAKE)) {
            return placeCake();
        }
        if (!(player instanceof ServerPlayer sp)
                || cooldown > 0
                || smokerId != null
                || !sp.getMainHandItem().is(Items.SMOKER)) {
            return false;
        }

        smokerPos   = sp.blockPosition();
        smokerId    = UUID.randomUUID();
        cooldown    = SMOKER_COOLDOWN_TICKS;
        smokerIdle  = 40 * 20;  // 40 seconds idle timeout
        stage       = 0;
        wheat       = 0;
        sugar       = 0;
        milk        = 0;
        lockedTicks = 0;

        var level = sp.serverLevel();
        double x = smokerPos.getX() + 0.5;
        double y = smokerPos.getY();
        double z = smokerPos.getZ() + 0.5;

        // 1. Create block display entity (smoker model)
        var displayEntity = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        displayEntity.setBlockState(Blocks.SMOKER.defaultBlockState());
        displayEntity.setPos(x, y, z);
        displayEntity.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new Quaternionf(),
                new Vector3f(1.0f, 1.0f, 1.0f),
                new Quaternionf()
        ));
        displayEntity.addTag(SMOKER_ENTITY_TAG);
        level.addFreshEntity(displayEntity);

        // 2. Create interaction entity (slightly larger than the smoker)
        var interactionEntity = new Interaction(EntityType.INTERACTION, level);
        interactionEntity.setPos(x, y + INTERACTION_HEIGHT / 2.0, z);
        // Set bounding box to match the desired interaction area
        interactionEntity.setBoundingBox(new AABB(
                x - INTERACTION_WIDTH / 2.0, y,
                z - INTERACTION_WIDTH / 2.0,
                x + INTERACTION_WIDTH / 2.0, y + INTERACTION_HEIGHT,
                z + INTERACTION_WIDTH / 2.0
        ));
        interactionEntity.addTag(SMOKER_ENTITY_TAG);
        level.addFreshEntity(interactionEntity);

        // 3. Register in the server-side map
        SmokerEntityInfo info = new SmokerEntityInfo(sp.getUUID());
        info.displayEntity = displayEntity;
        info.interactionEntity = interactionEntity;
        SMOKER_ENTITIES.put(interactionEntity.getUUID(), info);
        interactionEntityId = interactionEntity.getUUID();

        sync(); // 立即同步冷却到客户端
        sp.displayClientMessage(
                Component.translatable("message.noellesroles.cake_maker.smoker_ready")
                        .withStyle(ChatFormatting.GOLD),
                true);
        return true;
    }

    /** Places a cake as server-side entities at the player's feet. Requires a solid block below. */
    private boolean placeCake() {
        if (!(player instanceof ServerPlayer sp)) {
            return false;
        }
        BlockPos pos = sp.blockPosition();

        // Must have a solid block underneath, and the position must be air
        if (!sp.serverLevel().getBlockState(pos.below()).isSolidRender(sp.serverLevel(), pos.below())
                || !sp.serverLevel().getBlockState(pos).isAir()) {
            return false;
        }

        UUID id = UUID.randomUUID();
        var level = sp.serverLevel();
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;

        // 1. Create block display entity (cake model, 0 bites)
        var displayEntity = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        displayEntity.setBlockState(Blocks.CAKE.defaultBlockState().setValue(CakeBlock.BITES, 0));
        displayEntity.setPos(x, y, z);
        displayEntity.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new Quaternionf(),
                new Vector3f(1.0f, 1.0f, 1.0f),
                new Quaternionf()
        ));
        displayEntity.addTag(CAKE_ENTITY_TAG);
        level.addFreshEntity(displayEntity);

        // 2. Create interaction entity (slightly larger than half-block cake)
        var interactionEntity = new Interaction(EntityType.INTERACTION, level);
        interactionEntity.setPos(x, y + CAKE_INTERACTION_HEIGHT / 2.0, z);
        interactionEntity.setBoundingBox(new AABB(
                x - CAKE_INTERACTION_WIDTH / 2.0, y,
                z - CAKE_INTERACTION_WIDTH / 2.0,
                x + CAKE_INTERACTION_WIDTH / 2.0, y + CAKE_INTERACTION_HEIGHT,
                z + CAKE_INTERACTION_WIDTH / 2.0
        ));
        interactionEntity.addTag(CAKE_ENTITY_TAG);
        level.addFreshEntity(interactionEntity);

        // 3. Register in the server-side map
        CakeEntityInfo info = new CakeEntityInfo(id, sp.getUUID());
        info.displayEntity = displayEntity;
        info.interactionEntity = interactionEntity;
        CAKE_ENTITIES.put(interactionEntity.getUUID(), info);

        // 4. Track in the instance-level cakes map
        Cake cake = new Cake(pos, sp.level().getGameTime());
        cake.displayEntity = displayEntity;
        cake.interactionEntityId = interactionEntity.getUUID();
        cakes.put(id, cake);

        sp.getMainHandItem().shrink(1);
        return true;
    }

    // ── Eating a placed cake ──────────────────────────────────

    /**
     * Attempt to let {@code eater} take a bite from the cake identified by the clicked interaction entity.
     * Restores stamina, mood, status bars, and grants Speed I. Cooldown: 30 s per player.
     */
    public boolean eat(Entity clickedEntity, ServerPlayer eater) {
        if (eatCooldowns.getOrDefault(eater.getUUID(), 0) > 0) {
            return false;
        }
        // Look up the cake by the interaction entity
        CakeEntityInfo info = CAKE_ENTITIES.get(clickedEntity.getUUID());
        if (info == null) return false;
        Cake cake = cakes.get(info.cakeId);
        if (cake == null) return false;

        eatCooldowns.put(eater.getUUID(), EAT_COOLDOWN_TICKS);

        // Speed I for 20 seconds
        eater.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SPEED_DURATION_TICKS, 0));

        // Restore 30 % mood
        var mood = SREPlayerMoodComponent.KEY.get(eater);
        mood.setMood(Math.min(1.0F, mood.getMood() + MOOD_RESTORE_PCT / 100.0F));

        // Restore sprint stamina to full (capped to player's max sprint time)
        int maxSprint = SREGameWorldComponent.KEY.get(eater.level()).getRole(eater).getMaxSprintTime(eater);
        ((PlayerStaminaGetter) eater).starrailexpress$setStamina(maxSprint);

        // Fill whichever MapStatusBar is active for the current scene
        MapStatusBarRuntime.addWarmth(eater, STATUS_BAR_MAX);
        MapStatusBarRuntime.addThirst(eater, STATUS_BAR_MAX);
        MapStatusBarRuntime.addHunger(eater, STATUS_BAR_MAX);

        // Advance bites; update display entity block state
        cake.bites++;
        if (cake.bites > MAX_CAKE_BITES) {
            // Cake fully eaten — remove entities
            removeCakeEntities(info.cakeId, cake);
            cakes.remove(info.cakeId);
        } else if (cake.displayEntity != null && !cake.displayEntity.isRemoved()) {
            // Update the display entity to show new bite count
            cake.displayEntity.setBlockState(
                    Blocks.CAKE.defaultBlockState().setValue(CakeBlock.BITES, Math.min(cake.bites, MAX_CAKE_BITES)));
        }
        return true;
    }

    // ── Ingredient input ──────────────────────────────────────

    /**
     * Try to add the player's held item as an ingredient into the active smoker.
     * Only accepts items in the correct order for the current baking stage.
     * @param clickedEntity the interaction entity the player right-clicked (must match this smoker's entity)
     */
    public boolean addIngredient(Player p, Entity clickedEntity) {
        if (smokerId == null
                || smokerPos == null
                || lockedTicks > 0) {
            return false;
        }
        // Verify the clicked entity is our interaction entity
        if (interactionEntityId == null || !interactionEntityId.equals(clickedEntity.getUUID())) {
            return false;
        }

        var held = p.getMainHandItem();
        boolean accepted = false;

        // Stage 0 — wheat (×3).  First wheat starts the 40 s cooking timer.
        if (stage == 0 && held.is(Items.WHEAT) && wheat < WHEAT_NEEDED) {
            if (wheat == 0) {
                startCookingTimer();
            }
            wheat++;
            accepted = true;
            if (wheat == WHEAT_NEEDED) {
                waitFor(1, WAIT_SHORT_TICKS);
            }
        }
        // Stage 2 — egg (×1, must come before sugar)
        else if (stage == 2 && held.is(ModItems.CAKE_EGG) && sugar == 0) {
            stage = 21; // sub-stage: waiting for sugar
            accepted = true;
        }
        // Stage 21 — sugar (×2)
        else if (stage == 21 && held.is(Items.SUGAR) && sugar < SUGAR_NEEDED) {
            sugar++;
            accepted = true;
            if (sugar == SUGAR_NEEDED) {
                waitFor(3, WAIT_SHORT_TICKS);
            }
        }
        // Stage 4 — milk buckets (×3)
        else if (stage == 4 && held.is(ModItems.CAKE_MILK_BUCKET) && milk < MILK_NEEDED) {
            milk++;
            accepted = true;
            if (milk == MILK_NEEDED) {
                waitFor(5, WAIT_LONG_TICKS);
            }
        }

        if (!accepted) {
            p.displayClientMessage(
                    Component.translatable("message.noellesroles.cake_maker.wrong_ingredient")
                            .withStyle(ChatFormatting.RED),
                    true);
            return true;
        }

        held.shrink(1);
        smokerIdle = 40 * 20; // 投入食材重置40秒空闲计时
        return true;
    }

    /** Lock ingredient input for {@code ticks} ticks, then advance to {@code nextStage}. */
    private void waitFor(int nextStage, int ticks) {
        stage       = nextStage;
        lockedTicks = ticks;
    }

    /** Start the 40-second cooking timer. */
    private void startCookingTimer() {
        smokerTicks = SMOKER_DURATION_TICKS;
    }

    // ── Sync ───────────────────────────────────────────────────

    public void sync() { KEY.sync(player); }

    @Override
    public boolean shouldSyncWith(ServerPlayer target) { return target == this.player; }

    // ── Network helpers ───────────────────────────────────────

    private void sendMessage(String key) {
        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(
                    Component.translatable(key).withStyle(ChatFormatting.GREEN),
                    true);
        }
    }

    private void removeSmoker() {
        if (smokerId != null) {
            // Remove entities from the server-side registry and world
            if (interactionEntityId != null) {
                SmokerEntityInfo info = SMOKER_ENTITIES.remove(interactionEntityId);
                if (info != null) {
                    if (info.displayEntity != null && !info.displayEntity.isRemoved()) {
                        info.displayEntity.remove(Entity.RemovalReason.DISCARDED);
                    }
                    if (info.interactionEntity != null && !info.interactionEntity.isRemoved()) {
                        info.interactionEntity.remove(Entity.RemovalReason.DISCARDED);
                    }
                }
            }
        }
        smokerId            = null;
        smokerPos           = null;
        interactionEntityId = null;
        smokerIdle          = 0;
        smokerTicks         = 0;
    }

    // ── Static helpers for external access ───────────────────

    /** Check if an interaction entity belongs to a cake maker smoker. */
    public static boolean isSmokerInteractionEntity(Entity entity) {
        return entity instanceof Interaction
                && SMOKER_ENTITIES.containsKey(entity.getUUID());
    }

    /** Get the owner UUID for a smoker interaction entity. */
    public static UUID getSmokerOwner(Entity entity) {
        SmokerEntityInfo info = SMOKER_ENTITIES.get(entity.getUUID());
        return info != null ? info.ownerUuid : null;
    }

    /** Remove all cake maker smoker entities in the world (for game end / eggclear). */
    public static void removeAllSmokerEntities(net.minecraft.server.level.ServerLevel level) {
        for (var entry : SMOKER_ENTITIES.entrySet()) {
            SmokerEntityInfo info = entry.getValue();
            if (info.displayEntity != null && !info.displayEntity.isRemoved()) {
                info.displayEntity.remove(Entity.RemovalReason.DISCARDED);
            }
            if (info.interactionEntity != null && !info.interactionEntity.isRemoved()) {
                info.interactionEntity.remove(Entity.RemovalReason.DISCARDED);
            }
        }
        SMOKER_ENTITIES.clear();
    }

    /** Remove smoker entities within a range around a position. */
    public static int removeSmokerEntitiesInRange(net.minecraft.server.level.ServerLevel level, BlockPos origin, float range) {
        int cleared = 0;
        double rangeSq = range * range;
        var iter = SMOKER_ENTITIES.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            SmokerEntityInfo info = entry.getValue();
            if (info.displayEntity != null && !info.displayEntity.isRemoved()) {
                double dx = info.displayEntity.getX() - origin.getX();
                double dy = info.displayEntity.getY() - origin.getY();
                double dz = info.displayEntity.getZ() - origin.getZ();
                if (dx * dx + dy * dy + dz * dz <= rangeSq) {
                    info.displayEntity.remove(Entity.RemovalReason.DISCARDED);
                    if (info.interactionEntity != null && !info.interactionEntity.isRemoved()) {
                        info.interactionEntity.remove(Entity.RemovalReason.DISCARDED);
                    }
                    iter.remove();
                    cleared++;
                }
            } else {
                // Clean up stale entries
                if (info.interactionEntity != null && !info.interactionEntity.isRemoved()) {
                    info.interactionEntity.remove(Entity.RemovalReason.DISCARDED);
                }
                iter.remove();
            }
        }
        return cleared;
    }

    // ── Inner type — placed cake record ───────────────────────

    /** Tracks a single placed cake: its position, bites, and entity references. */
    public static final class Cake {
        final BlockPos pos;
        int bites;
        long placedGameTime;
        Display.BlockDisplay displayEntity;
        UUID interactionEntityId;

        Cake(BlockPos pos, long gameTime) {
            this.pos = pos;
            this.placedGameTime = gameTime;
        }
    }

    // ── Cake entity helpers (static) ──────────────────────────

    /** Remove a specific cake's entities from the world and the registry. */
    private static void removeCakeEntities(UUID cakeId, Cake cake) {
        if (cake.interactionEntityId != null) {
            CakeEntityInfo info = CAKE_ENTITIES.remove(cake.interactionEntityId);
            if (info != null) {
                if (info.displayEntity != null && !info.displayEntity.isRemoved()) {
                    info.displayEntity.remove(Entity.RemovalReason.DISCARDED);
                }
                if (info.interactionEntity != null && !info.interactionEntity.isRemoved()) {
                    info.interactionEntity.remove(Entity.RemovalReason.DISCARDED);
                }
            }
        }
    }

    /** Check if an interaction entity belongs to a cake maker cake. */
    public static boolean isCakeInteractionEntity(Entity entity) {
        return entity instanceof Interaction
                && CAKE_ENTITIES.containsKey(entity.getUUID());
    }

    /** Get the owner UUID for a cake interaction entity. */
    public static UUID getCakeOwner(Entity entity) {
        CakeEntityInfo info = CAKE_ENTITIES.get(entity.getUUID());
        return info != null ? info.ownerUuid : null;
    }

    /** Get the cake ID for a cake interaction entity. */
    public static UUID getCakeId(Entity entity) {
        CakeEntityInfo info = CAKE_ENTITIES.get(entity.getUUID());
        return info != null ? info.cakeId : null;
    }

    /** Remove all cake maker cake entities in the world (for game end / eggclear). */
    public static void removeAllCakeEntities(net.minecraft.server.level.ServerLevel level) {
        for (var entry : CAKE_ENTITIES.entrySet()) {
            CakeEntityInfo info = entry.getValue();
            if (info.displayEntity != null && !info.displayEntity.isRemoved()) {
                info.displayEntity.remove(Entity.RemovalReason.DISCARDED);
            }
            if (info.interactionEntity != null && !info.interactionEntity.isRemoved()) {
                info.interactionEntity.remove(Entity.RemovalReason.DISCARDED);
            }
        }
        CAKE_ENTITIES.clear();
    }

    /** Remove cake entities within a range around a position. */
    public static int removeCakeEntitiesInRange(net.minecraft.server.level.ServerLevel level, BlockPos origin, float range) {
        int cleared = 0;
        double rangeSq = range * range;
        var iter = CAKE_ENTITIES.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            CakeEntityInfo info = entry.getValue();
            if (info.displayEntity != null && !info.displayEntity.isRemoved()) {
                double dx = info.displayEntity.getX() - origin.getX();
                double dy = info.displayEntity.getY() - origin.getY();
                double dz = info.displayEntity.getZ() - origin.getZ();
                if (dx * dx + dy * dy + dz * dz <= rangeSq) {
                    info.displayEntity.remove(Entity.RemovalReason.DISCARDED);
                    if (info.interactionEntity != null && !info.interactionEntity.isRemoved()) {
                        info.interactionEntity.remove(Entity.RemovalReason.DISCARDED);
                    }
                    iter.remove();
                    cleared++;
                }
            } else {
                if (info.interactionEntity != null && !info.interactionEntity.isRemoved()) {
                    info.interactionEntity.remove(Entity.RemovalReason.DISCARDED);
                }
                iter.remove();
            }
        }
        return cleared;
    }
}
