package org.agmas.noellesroles.game.modes.repair;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModBlocks;
import org.agmas.noellesroles.init.ModItems;

import java.util.Map;
import java.util.WeakHashMap;

public final class RepairEventSystem {
    private static final int MIN_DELAY_TICKS = 20 * 70;
    private static final int RANDOM_DELAY_TICKS = 20 * 55;
    private static final Map<ServerLevel, EventState> STATES = new WeakHashMap<>();

    private RepairEventSystem() {
    }

    public static void reset(ServerLevel level) {
        STATES.remove(level);
        for (ServerPlayer player : level.players()) {
            clearPlayerEvent(player);
        }
    }

    public static void tick(ServerLevel level) {
        EventState state = STATES.computeIfAbsent(level, ignored -> EventState.waiting(level.getGameTime() + nextDelay(level.random)));
        long now = level.getGameTime();
        if (state.active == RepairEvent.NONE) {
            if (now >= state.nextEventTick) {
                start(level, state, chooseEvent(level, state, level.random));
            } else if (now % 20 == 0) {
                syncHud(level, state, 0);
            }
            return;
        }

        int remaining = Math.max(0, (int) (state.endTick - now));
        applyTickEffects(level, state.active, remaining);
        if (now % 20 == 0) {
            syncHud(level, state, remaining);
        }
        if (remaining <= 0) {
            finish(level, state.active);
            state.active = RepairEvent.NONE;
            state.nextEventTick = now + nextDelay(level.random);
            state.endTick = 0L;
            syncHud(level, state, 0);
        }
    }

    public static boolean isActive(ServerLevel level, RepairEvent event) {
        EventState state = STATES.get(level);
        return state != null && state.active == event && level.getGameTime() < state.endTick;
    }

    public static int repairProgressBonus(ServerLevel level) {
        return isActive(level, RepairEvent.MACHINE_OVERLOAD) ? 2 : 0;
    }

    public static int repairCoinBonus(ServerLevel level) {
        return isActive(level, RepairEvent.MACHINE_OVERLOAD) ? 5 : 0;
    }

    private static void start(ServerLevel level, EventState state, RepairEvent event) {
        state.active = event;
        state.lastEvent = event;
        state.endTick = level.getGameTime() + event.durationTicks;
        level.playSound(null, level.players().isEmpty() ? net.minecraft.core.BlockPos.ZERO : level.players().getFirst().blockPosition(),
                event.startSound, SoundSource.HOSTILE, 1.0F, event.pitch);
        for (ServerPlayer player : level.players()) {
            if (GameUtils.isPlayerEliminated(player)) {
                continue;
            }
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.event.start", event.localizedName())
                    .withStyle(event.color), false);
            applyStartReward(level, player, event);
        }
        syncHud(level, state, event.durationTicks);
    }

    private static void applyStartReward(ServerLevel level, ServerPlayer player, RepairEvent event) {
        boolean hunter = RepairModeState.isHunter(player);
        switch (event) {
            case PHANTOM_CACHE -> {
                if (hunter) {
                    player.addItem(new ItemStack(ModBlocks.HUNTER_SNARE.asItem()));
                    player.addItem(new ItemStack(ModItems.HUNTER_PULSE));
                } else {
                    ItemStack reward = switch (level.random.nextInt(4)) {
                        case 0 -> new ItemStack(ModItems.SMOKE_PELLET);
                        case 1 -> new ItemStack(ModItems.DECOY_BEACON);
                        case 2 -> new ItemStack(ModItems.ESCAPE_GRAPPLE);
                        default -> new ItemStack(ModItems.SPARE_PARTS, 2);
                    };
                    player.addItem(reward);
                    RepairModeState.awardCoins(player, 20, "repair_coin_source.event");
                }
                level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0D, player.getZ(), 18,
                        0.5D, 0.6D, 0.5D, 0.03D);
            }
            case BLOOD_MOON -> {
                if (hunter) {
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, event.durationTicks, 0, false, true, true));
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, event.durationTicks, 0, false, true, true));
                } else {
                    player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 8, 0, false, true, true));
                    player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 5, 0, false, false, true));
                }
            }
            case BLACKOUT -> {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 10, 0, false, false, true));
                if (hunter) {
                    player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 4, 0, false, true, true));
                }
            }
            case JUDGMENT_BELL -> {
                if (hunter) {
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 12, 0, false, true, true));
                } else if (ModComponents.REPAIR_ROLES.get(player).downed) {
                    player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 12, 0, false, true, true));
                }
            }
            case MACHINE_OVERLOAD -> player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 20 * 10, 0, false, true, true));
            default -> {
            }
        }
    }

    private static void applyTickEffects(ServerLevel level, RepairEvent event, int remaining) {
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            if (GameUtils.isPlayerEliminated(player)) {
                continue;
            }
            boolean hunter = RepairModeState.isHunter(player);
            switch (event) {
                case BLOOD_MOON -> {
                    if (now % 60 == 0) {
                        if (hunter) {
                            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 0, false, true, true));
                            level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0D, player.getZ(), 8,
                                    0.35D, 0.4D, 0.35D, 0.02D);
                        } else {
                            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 50, 0, false, true, true));
                            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getY() + 1.0D,
                                    player.getZ(), 2, 0.25D, 0.2D, 0.25D, 0.01D);
                        }
                    }
                }
                case BLACKOUT -> {
                    if (now % 45 == 0) {
                        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 70, 0, false, false, true));
                        if (!hunter && player.isCrouching()) {
                            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 50, 0, false, true, true));
                        }
                        level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.3D, player.getZ(), 10,
                                0.6D, 0.35D, 0.6D, 0.01D);
                    }
                }
                case MACHINE_OVERLOAD -> {
                    if (now % 30 == 0) {
                        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 50, 0, false, true, true));
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 0.8D,
                                player.getZ(), 6, 0.45D, 0.35D, 0.45D, 0.02D);
                    }
                }
                case JUDGMENT_BELL -> {
                    if (now % 50 == 0) {
                        if (hunter) {
                            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 70, 0, false, true, true));
                        } else if (ModComponents.REPAIR_ROLES.get(player).downed || ModComponents.REPAIR_ROLES.get(player).trialStand.present()) {
                            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0, false, true, true));
                            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1, false, true, true));
                        }
                    }
                }
                case PHANTOM_CACHE -> {
                    if (now % 40 == 0) {
                        level.sendParticles(ParticleTypes.WAX_ON, player.getX(), player.getY() + 1.0D, player.getZ(), 6,
                                0.45D, 0.4D, 0.45D, 0.02D);
                    }
                }
                default -> {
                }
            }
        }
        if (remaining % 100 == 0 && remaining > 0) {
            level.playSound(null, level.players().isEmpty() ? net.minecraft.core.BlockPos.ZERO : level.players().getFirst().blockPosition(),
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 0.7F, 0.8F);
        }
    }

    private static void finish(ServerLevel level, RepairEvent event) {
        int nonHunterReward = switch (event) {
            case BLOOD_MOON -> 55;
            case BLACKOUT -> 45;
            case MACHINE_OVERLOAD -> 30;
            case JUDGMENT_BELL -> 40;
            default -> 0;
        };
        int hunterReward = switch (event) {
            case BLOOD_MOON, JUDGMENT_BELL -> 25;
            default -> 0;
        };
        for (ServerPlayer player : level.players()) {
            if (GameUtils.isPlayerEliminated(player)) {
                continue;
            }
            boolean hunter = RepairModeState.isHunter(player);
            int reward = hunter ? hunterReward : nonHunterReward;
            if (reward > 0) {
                RepairModeState.awardCoins(player, reward, "repair_coin_source.event");
            }
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.event.end", event.localizedName())
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static void syncHud(ServerLevel level, EventState state, int remainingTicks) {
        for (ServerPlayer player : level.players()) {
            var component = ModComponents.REPAIR_ROLES.get(player);
            if (state.active == RepairEvent.NONE) {
                component.currentEventKey = "";
                component.currentEventRewardKey = "";
                component.currentEventTicks = 0;
                component.currentEventDanger = 0;
            } else {
                component.currentEventKey = state.active.nameKey;
                component.currentEventRewardKey = state.active.rewardKey;
                component.currentEventTicks = Math.max(0, remainingTicks);
                component.currentEventDanger = state.active.danger;
            }
            component.sync();
        }
    }

    private static void clearPlayerEvent(ServerPlayer player) {
        var component = ModComponents.REPAIR_ROLES.get(player);
        component.currentEventKey = "";
        component.currentEventRewardKey = "";
        component.currentEventTicks = 0;
        component.currentEventDanger = 0;
        component.sync();
    }

    private static RepairEvent chooseEvent(ServerLevel level, EventState state, RandomSource random) {
        RepairEvent[] values = { RepairEvent.BLOOD_MOON, RepairEvent.BLACKOUT, RepairEvent.MACHINE_OVERLOAD,
                RepairEvent.PHANTOM_CACHE, RepairEvent.JUDGMENT_BELL };
        int totalWeight = 0;
        int[] weights = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            if (values[i] == state.lastEvent && values.length > 1) {
                weights[i] = 0;
            } else {
                weights[i] = eventWeight(level, values[i]);
                totalWeight += weights[i];
            }
        }
        if (totalWeight <= 0) {
            return values[random.nextInt(values.length)];
        }
        int roll = random.nextInt(totalWeight);
        for (int i = 0; i < values.length; i++) {
            roll -= weights[i];
            if (roll < 0) {
                return values[i];
            }
        }
        return values[0];
    }

    private static int eventWeight(ServerLevel level, RepairEvent event) {
        int completed = RepairModeState.getCompletedStationCount(level);
        int nonHunters = 0;
        int pressuredNonHunters = 0;
        int hunters = 0;
        for (ServerPlayer player : level.players()) {
            if (GameUtils.isPlayerEliminated(player)) {
                continue;
            }
            boolean hunter = RepairModeState.isHunter(player);
            var component = ModComponents.REPAIR_ROLES.get(player);
            if (hunter) {
                hunters++;
            } else {
                nonHunters++;
                if (component.downed || component.trialStand.present()) {
                    pressuredNonHunters++;
                }
            }
        }
        boolean survivorsBehind = pressuredNonHunters >= Math.max(1, nonHunters / 3)
                || (completed <= 1 && nonHunters > hunters + 1);
        boolean huntersBehind = completed >= RepairModeState.REQUIRED_REPAIRED_STATIONS - 1
                || (completed >= 3 && pressuredNonHunters == 0);
        int weight = 1;
        if (survivorsBehind && (event == RepairEvent.MACHINE_OVERLOAD || event == RepairEvent.PHANTOM_CACHE)) {
            weight += 3;
        }
        if (huntersBehind && (event == RepairEvent.BLOOD_MOON || event == RepairEvent.JUDGMENT_BELL)) {
            weight += 3;
        }
        if (event == RepairEvent.BLACKOUT && !survivorsBehind && !huntersBehind) {
            weight += 1;
        }
        return weight;
    }

    private static int nextDelay(RandomSource random) {
        return MIN_DELAY_TICKS + random.nextInt(RANDOM_DELAY_TICKS);
    }

    private static final class EventState {
        private RepairEvent active;
        private RepairEvent lastEvent;
        private long endTick;
        private long nextEventTick;

        private static EventState waiting(long nextEventTick) {
            EventState state = new EventState();
            state.active = RepairEvent.NONE;
            state.lastEvent = RepairEvent.NONE;
            state.nextEventTick = nextEventTick;
            return state;
        }
    }

    public enum RepairEvent {
        NONE("", "", 0, 0, ChatFormatting.GRAY, SoundEvents.UI_BUTTON_CLICK.value(), 1.0F),
        BLOOD_MOON("hud.noellesroles.repair.event.blood_moon", "hud.noellesroles.repair.event_reward.survive", 20 * 45, 90,
                ChatFormatting.DARK_RED, SoundEvents.WARDEN_ROAR, 0.65F),
        BLACKOUT("hud.noellesroles.repair.event.blackout", "hud.noellesroles.repair.event_reward.stealth", 20 * 40, 75,
                ChatFormatting.DARK_GRAY, SoundEvents.WARDEN_NEARBY_CLOSE, 0.7F),
        MACHINE_OVERLOAD("hud.noellesroles.repair.event.machine_overload", "hud.noellesroles.repair.event_reward.fast_repair", 20 * 42, 55,
                ChatFormatting.AQUA, SoundEvents.BEACON_ACTIVATE, 1.35F),
        PHANTOM_CACHE("hud.noellesroles.repair.event.phantom_cache", "hud.noellesroles.repair.event_reward.cache", 20 * 28, 35,
                ChatFormatting.GOLD, SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, 1.2F),
        JUDGMENT_BELL("hud.noellesroles.repair.event.judgment_bell", "hud.noellesroles.repair.event_reward.rescue", 20 * 38, 80,
                ChatFormatting.RED, SoundEvents.BELL_BLOCK, 0.45F);

        private final String nameKey;
        private final String rewardKey;
        private final int durationTicks;
        private final int danger;
        private final ChatFormatting color;
        private final net.minecraft.sounds.SoundEvent startSound;
        private final float pitch;

        RepairEvent(String nameKey, String rewardKey, int durationTicks, int danger, ChatFormatting color,
                net.minecraft.sounds.SoundEvent startSound, float pitch) {
            this.nameKey = nameKey;
            this.rewardKey = rewardKey;
            this.durationTicks = durationTicks;
            this.danger = danger;
            this.color = color;
            this.startSound = startSound;
            this.pitch = pitch;
        }

        private Component localizedName() {
            return Component.translatable(nameKey);
        }
    }
}
