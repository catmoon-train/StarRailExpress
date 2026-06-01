package org.agmas.noellesroles.game.roles.killer.silencer;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.PlayerStaminaGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentProvider;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

public class SilencerPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SilencerPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "silencer"), SilencerPlayerComponent.class);

    private final Player player;
    private SREGameWorldComponent gameWorldComponent = null;

    // Skill cooldown ticks: 0 = ready, negative = cooling down
    public int skillCooldownTicks = 0;
    // Target player UUID for active skill
    public UUID targetUUID = null;
    // Current phase: 0 = idle, 1 = silence, 2 = help, 3 = punishment
    public int phase = 0;
    // Phase timer (ticks remaining in current phase)
    public int phaseTimer = 0;
    // Whether this silencer has already received coins in this skill cycle
    public boolean coinsReceived = false;
    // Periodic sync counter (like Morphling)
    public int tickR = 0;

    // Phase duration constants
    public static final int PHASE1_DURATION = 45 * 20;
    public static final int PHASE2_DURATION = 30 * 20;
    public static final int SKILL_COOLDOWN = 130 * 20;
    public static final int INITIAL_COOLDOWN = 155 * 20;
    // Reduced phase 1 when few innocents
    public static final int PHASE1_REDUCED_DURATION = 40 * 20;

    @Override
    public void init() {
        this.phase = 0;
        this.phaseTimer = 0;
        this.targetUUID = null;
        this.coinsReceived = false;
        this.skillCooldownTicks = -INITIAL_COOLDOWN;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    public SilencerPlayerComponent(Player player) {
        this.player = player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    private boolean checkIsGameRunning() {
        if (gameWorldComponent == null) {
            gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
        }
        return gameWorldComponent != null && gameWorldComponent.gameStatus == SREGameWorldComponent.GameStatus.ACTIVE;
    }

    /**
     * Start the silencer skill on a target player.
     */
    public boolean startSkill(UUID targetId) {
        if (skillCooldownTicks != 0) return false;
        if (phase != 0) return false;
        if (!(player instanceof ServerPlayer sp)) return false;

        ServerPlayer targetPlayer = player.level().getServer().getPlayerList().getPlayer(targetId);
        if (targetPlayer == null) return false;
        if (!GameUtils.isPlayerAliveAndSurvival(targetPlayer)) return false;

        this.targetUUID = targetId;
        this.phase = 1;
        this.coinsReceived = false;

        // Check if there are <= 2 innocent players (use reduced phase)
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        int aliveInnocents = (int) player.level().players().stream()
                .filter(p -> p instanceof ServerPlayer)
                .map(p -> (ServerPlayer) p)
                .filter(GameUtils::isPlayerAliveAndSurvival)
                .filter(p -> gameWorld.isInnocent(p))
                .count();
        boolean useReduced = aliveInnocents <= 2;

        if (useReduced) {
            this.phaseTimer = PHASE1_REDUCED_DURATION;
        } else {
            this.phaseTimer = PHASE1_DURATION;
        }

        // Apply phase 1 effects to target: voice_silence + chat_ban (45s), hidden particles/icon
        targetPlayer.addEffect(new MobEffectInstance(ModEffects.VOICE_SILENCE,
                this.phaseTimer, 0, false, false, false));
        targetPlayer.addEffect(new MobEffectInstance(ModEffects.CHAT_BAN,
                this.phaseTimer, 0, false, false, false));

        // Set skill cooldown
        this.skillCooldownTicks = -SKILL_COOLDOWN;
        this.sync();

        return true;
    }

    /**
     * Called by another player when they right-click the target during help phase.
     */
    public void helpTarget() {
        if (phase != 2) return;
        endSkill();
    }

    /**
     * End the skill, clearing all phase state but keeping cooldown running.
     */
    private void endSkill() {
        this.phase = 0;
        this.phaseTimer = 0;
        this.targetUUID = null;
        this.sync();
    }

    /**
     * Enter phase 3 - punishment phase.
     */
    private void enterPunishmentPhase(ServerPlayer targetPlayer) {
        this.phase = 3;
        this.phaseTimer = 0;
        this.sync();

        // Play warden death sound to all players
        targetPlayer.level().playSound(null, targetPlayer.blockPosition(),
                SoundEvents.WARDEN_DEATH, SoundSource.MASTER, 1.0F, 1.0F);

        // Clear target's mood (only if real mood)
        SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(targetPlayer);
        if (mood != null) {
            // Only clear real mood, not fake
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorld.getRole(targetPlayer) != null && gameWorld.getRole(targetPlayer).getMoodType() == io.wifi.starrailexpress.api.SRERole.MoodType.REAL) {
                mood.setMood(0f);
                mood.sync();
            }
        }

        // Clear stamina (only if not infinite)
        {
            var gameWorld = SREGameWorldComponent.KEY.get(targetPlayer.level());
            var targetRole = gameWorld != null ? gameWorld.getRole(targetPlayer) : null;
            if (targetRole != null && targetRole.getMaxSprintTime(targetPlayer) != Integer.MAX_VALUE) {
                if (targetPlayer instanceof PlayerStaminaGetter staminaGetter) {
                    staminaGetter.starrailexpress$setStamina(0);
                }
            }
        }

        // Give 120 coins to all living silencers
        targetPlayer.level().players().forEach(p -> {
            if (p instanceof ServerPlayer sp && GameUtils.isPlayerAliveAndSurvival(sp)) {
                if (gameWorldComponent.isRole(sp, ModRoles.SILENCER)) {
                    SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
                    shop.setBalance(shop.balance + 120);
                    shop.sync();
                }
            }
        });

        this.coinsReceived = true;
        this.phase = 0;
        this.targetUUID = null;
        this.sync();
    }

    @Override
    public void serverTick() {
        if (!checkIsGameRunning()) return;
        if (!(player instanceof ServerPlayer sp)) return;
        if (!SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.SILENCER)) return;

        // Tick cooldown
        if (skillCooldownTicks < 0) {
            skillCooldownTicks++;
            if (skillCooldownTicks > 0) skillCooldownTicks = 0;
        }

        // Periodic sync (like Morphling every 200 ticks, or when cooldown hits 0)
        tickR++;
        if (tickR % 200 == 0 || (skillCooldownTicks == 0 && tickR % 20 == 0)) {
            KEY.syncWith(sp, (ComponentProvider) sp, this, this);
        }

        // Only process if in a phase
        if (phase == 0 || targetUUID == null) return;

        ServerPlayer targetPlayer = player.level().getServer().getPlayerList().getPlayer(targetUUID);
        if (targetPlayer == null || !GameUtils.isPlayerAliveAndSurvival(targetPlayer)) {
            endSkill();
            return;
        }

        // Tick phase timer
        if (phaseTimer > 0) {
            phaseTimer--;
        }

        if (phase == 1) {
            // Phase 1: Silence phase - show actionbar "你已被禁言"
            targetPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.silencer.silenced"),
                    true);

            // Check if phase 1 should end
            if (phaseTimer <= 0) {
                // Check if reduced mode (skip phases 2 and 3)
                SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
                int aliveInnocents = (int) player.level().players().stream()
                        .filter(p -> p instanceof ServerPlayer)
                        .map(p -> (ServerPlayer) p)
                        .filter(GameUtils::isPlayerAliveAndSurvival)
                        .filter(p -> gameWorld.isInnocent(p))
                        .count();
                boolean useReduced = aliveInnocents <= 2;

                // Check if target is isKillerTeam — skip punishment phase
                var targetRole = gameWorld.getRole(targetPlayer);
                boolean targetIsKillerTeam = targetRole != null && (targetRole.isKillerTeam() || targetRole.isKiller());

                if (useReduced && !targetIsKillerTeam) {
                    // Skip phases 2 and 3, just end
                    endSkill();
                } else if (targetIsKillerTeam) {
                    // isKillerTeam 目标：进入求助阶段但不进入惩罚阶段
                    this.phase = 2;
                    this.phaseTimer = PHASE2_DURATION;
                    this.sync();
                    targetPlayer.removeEffect(ModEffects.VOICE_SILENCE);
                    targetPlayer.addEffect(new MobEffectInstance(ModEffects.CHAT_BAN,
                            PHASE2_DURATION, 0, false, false, false));
                    BroadcastCommand.BroadcastMessage(targetPlayer,
                            Component.translatable("message.noellesroles.silencer.help_phase"));
                } else {
                    // Enter phase 2: Help phase
                    this.phase = 2;
                    this.phaseTimer = PHASE2_DURATION;
                    this.sync();

                    // Remove voice_silence but keep chat_ban
                    targetPlayer.removeEffect(ModEffects.VOICE_SILENCE);
                    // Re-apply chat_ban for 30s
                    targetPlayer.addEffect(new MobEffectInstance(ModEffects.CHAT_BAN,
                            PHASE2_DURATION, 0, false, false, false));

                    // Notify target
                    BroadcastCommand.BroadcastMessage(targetPlayer,
                            Component.translatable("message.noellesroles.silencer.help_phase"));
                }
            }
        } else if (phase == 2) {
            // Phase 2: Help phase - show actionbar "你已被禁言"
            targetPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.silencer.silenced"),
                    true);

            if (phaseTimer <= 0) {
                // Check if target is isKillerTeam — skip punishment
                var targetRole = gameWorldComponent.getRole(targetPlayer);
                boolean targetIsKillerTeam = targetRole != null && (targetRole.isKillerTeam() || targetRole.isKiller());
                if (targetIsKillerTeam) {
                    // isKillerTeam 目标不受惩罚，直接结束
                    endSkill();
                } else {
                    enterPunishmentPhase(targetPlayer);
                }
            }
        }
    }

    @Override
    public void clientTick() {
        if (!checkIsGameRunning()) {
            this.skillCooldownTicks = 0;
            this.phase = 0;
            this.phaseTimer = 0;
            return;
        }
        // Simulate cooldown countdown on client for smooth display
        if (this.skillCooldownTicks < 0) {
            this.skillCooldownTicks++;
        } else if (this.skillCooldownTicks > 0) {
            this.skillCooldownTicks--;
        }
        if (this.phaseTimer > 0) {
            this.phaseTimer--;
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("skillCooldownTicks", this.skillCooldownTicks);
        if (this.targetUUID != null) {
            tag.putUUID("targetUUID", this.targetUUID);
        }
        tag.putInt("phase", this.phase);
        tag.putInt("phaseTimer", this.phaseTimer);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.skillCooldownTicks = tag.getInt("skillCooldownTicks");
        this.targetUUID = tag.contains("targetUUID") ? tag.getUUID("targetUUID") : null;
        this.phase = tag.getInt("phase");
        this.phaseTimer = tag.getInt("phaseTimer");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
