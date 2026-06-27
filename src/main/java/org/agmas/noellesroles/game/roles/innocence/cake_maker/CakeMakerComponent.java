package org.agmas.noellesroles.game.roles.innocence.cake_maker;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.util.PlayerStaminaGetter;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.CakeMakerBlockS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.scene.MapStatusBarRuntime;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

    private static final int CAKE_PLACEMENT_TICKS = 600 * 20;
    private static final int EAT_COOLDOWN_TICKS   = 600; // 30 s
    private static final int SPEED_DURATION_TICKS = 20 * 20;
    private static final int MAX_CAKE_BITES       = 6;
    private static final int MOOD_RESTORE_PCT     = 30;
    private static final int STATUS_BAR_MAX       = 20;
    private static final double INTERACT_DISTANCE_SQ = 16.0;

    private static final int WHEAT_NEEDED  = 3;
    private static final int SUGAR_NEEDED  = 2;
    private static final int MILK_NEEDED   = 3;

    // ── State ──────────────────────────────────────────────────
    private final Player player;
    public int cooldown;
    public int smokerTicks;
    public int lockedTicks;
    public int stage;
    public int wheat;
    public int sugar;
    public int milk;
    public BlockPos smokerPos;
    public UUID smokerId;
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
        cooldown    = 0;
        smokerTicks = 0;
        lockedTicks = 0;
        stage       = 0;
        wheat       = 0;
        sugar       = 0;
        milk        = 0;
        smokerPos   = null;
        smokerId    = null;
    }

    @Override
    public void clear() {
        // Remove the deployed smoker and any placed cakes from all clients so they
        // don't linger in the world (and into the next round).
        removeSmoker();
        for (Map.Entry<UUID, Cake> entry : cakes.entrySet()) {
            Cake cake = entry.getValue();
            broadcast(new CakeMakerBlockS2CPacket(entry.getKey(), cake.pos, true, cake.bites, 0, true));
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
     *   <li>If holding a smoker and off cooldown → deploy client-side smoker.</li>
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
        stage       = 0;
        wheat       = 0;
        sugar       = 0;
        milk        = 0;
        lockedTicks = 0;

        // Smoker is placed but cooking hasn't started — send a long-lived client block
        broadcast(new CakeMakerBlockS2CPacket(smokerId, smokerPos, false, 0, Integer.MAX_VALUE, false));
        sp.displayClientMessage(
                Component.translatable("message.noellesroles.cake_maker.smoker_ready")
                        .withStyle(ChatFormatting.GOLD),
                true);
        return true;
    }

    /** Places a client-side cake block at the player's feet. Requires a solid block below. */
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
        cakes.put(id, new Cake(pos));
        sp.getMainHandItem().shrink(1);
        broadcast(new CakeMakerBlockS2CPacket(id, pos, true, 0, CAKE_PLACEMENT_TICKS, false));
        return true;
    }

    // ── Eating a placed cake ──────────────────────────────────

    /**
     * Attempt to let {@code eater} take a bite from the cake identified by {@code id}.
     * Restores stamina, mood, status bars, and grants Speed I. Cooldown: 5 s per player.
     */
    public boolean eat(UUID id, ServerPlayer eater) {
        Cake cake = cakes.get(id);
        if (cake == null
                || eater.distanceToSqr(cake.pos.getCenter()) > INTERACT_DISTANCE_SQ
                || eatCooldowns.getOrDefault(eater.getUUID(), 0) > 0) {
            return false;
        }

        eatCooldowns.put(eater.getUUID(), EAT_COOLDOWN_TICKS);

        // Speed I for 20 seconds
        eater.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SPEED_DURATION_TICKS, 0));

        // Restore 30 % mood
        var mood = SREPlayerMoodComponent.KEY.get(eater);
        mood.setMood(Math.min(1.0F, mood.getMood() + MOOD_RESTORE_PCT / 100.0F));

        // Restore sprint stamina to full
        ((PlayerStaminaGetter) eater).starrailexpress$setStamina(Float.MAX_VALUE);

        // Fill whichever MapStatusBar is active for the current scene
        MapStatusBarRuntime.addWarmth(eater, STATUS_BAR_MAX);
        MapStatusBarRuntime.addThirst(eater, STATUS_BAR_MAX);
        MapStatusBarRuntime.addHunger(eater, STATUS_BAR_MAX);

        // Advance bites; remove cake once fully eaten
        if (++cake.bites > MAX_CAKE_BITES) {
            cakes.remove(id);
            broadcast(new CakeMakerBlockS2CPacket(id, cake.pos, true, cake.bites, 0, true));
        } else {
            broadcast(new CakeMakerBlockS2CPacket(id, cake.pos, true, cake.bites, CAKE_PLACEMENT_TICKS, false));
        }
        return true;
    }

    // ── Ingredient input ──────────────────────────────────────

    /**
     * Try to add the player's held item as an ingredient into the active smoker.
     * Only accepts items in the correct order for the current baking stage.
     */
    public boolean addIngredient(Player p) {
        if (smokerId == null
                || smokerPos == null
                || p.distanceToSqr(smokerPos.getCenter()) > INTERACT_DISTANCE_SQ
                || lockedTicks > 0) {
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
        return true;
    }

    /** Lock ingredient input for {@code ticks} ticks, then advance to {@code nextStage}. */
    private void waitFor(int nextStage, int ticks) {
        stage       = nextStage;
        lockedTicks = ticks;
    }

    /** Start the 40-second cooking timer and sync the client block. */
    private void startCookingTimer() {
        smokerTicks = SMOKER_DURATION_TICKS;
        broadcast(new CakeMakerBlockS2CPacket(smokerId, smokerPos, false, 0, smokerTicks, false));
    }

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
            broadcast(new CakeMakerBlockS2CPacket(smokerId, smokerPos, false, 0, 0, true));
        }
        smokerId  = null;
        smokerPos = null;
    }

    private void broadcast(CakeMakerBlockS2CPacket packet) {
        if (player instanceof ServerPlayer sp) {
            for (ServerPlayer target : sp.serverLevel().players()) {
                ServerPlayNetworking.send(target, packet);
            }
        }
    }

    // ── Inner type — placed cake record ───────────────────────

    /** Tracks a single placed cake: its position and how many bites have been taken. */
    public static final class Cake {
        final BlockPos pos;
        int bites;

        Cake(BlockPos pos) {
            this.pos = pos;
        }
    }
}
