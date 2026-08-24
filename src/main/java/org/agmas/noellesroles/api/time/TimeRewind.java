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

package org.agmas.noellesroles.api.time;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SRERoleDataPlayerComponent;
import io.wifi.starrailexpress.compat.TrainVoicePlugin;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.api.time.TimeRewindResult.Failure;
import org.agmas.noellesroles.api.time.TimeRewindSnapshot.ComponentFormat;
import org.agmas.noellesroles.api.time.TimeRewindSnapshot.ComponentState;
import org.agmas.noellesroles.api.time.internal.TimeRewindPlayerAccess;
import org.agmas.noellesroles.api.time.internal.RoleDataTimeRewindAdapter;
import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.ComponentContainer;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentProvider;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Captures and restores a complete in-memory player state.
 *
 * <p>
 * The interface deliberately has only two main operations. Vanilla state,
 * item cooldowns, CCA format selection, component synchronization and
 * cross-dimension restoration are implementation details hidden behind this
 * module.
 *
 * <p>
 * Both capture and restore must be called on the server thread.
 */
public final class TimeRewind {
    private static final String CCA_ROOT_TAG = "cardinal_components";
    private static final ResourceLocation VANILLA_STATE_ID = Noellesroles.id("vanilla_player_state");
    private static final Map<ResourceLocation, RegisteredAdapter<?>> COMPONENT_ADAPTERS = new ConcurrentHashMap<>();
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    private TimeRewind() {
    }

    /** Registers the built-in RoleData adapter and smooth playback tick handler. */
    public static void initialize() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        registerComponentAdapter(SRERoleDataPlayerComponent.KEY,
                new RoleDataTimeRewindAdapter());
        TimeRewindPlayback.initialize();
    }

    public static TimeRewindSnapshot capture(ServerPlayer player) {
        return capture(player, TimeRewindOptions.DEFAULT);
    }

    public static TimeRewindSnapshot capture(ServerPlayer player, TimeRewindOptions options) {
        initialize();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(options, "options");
        requireServerThread(player);

        TimeRewindPlayerAccess playerAccess = playerAccess(player);
        CompoundTag vanillaState = playerAccess.noellesroles$captureTimeRewindState();
        // CCA is captured separately. This prevents normal persistent CCA NBT from
        // racing with RoleComponent's richer synchronization snapshot.
        vanillaState.remove(CCA_ROOT_TAG);

        HolderLookup.Provider registryLookup = player.registryAccess();
        ComponentContainer container = ((ComponentProvider) player).getComponentContainer();
        Map<ResourceLocation, ComponentState> componentStates = new LinkedHashMap<>();
        List<TimeRewindSnapshot.Warning> warnings = new ArrayList<>();

        for (ComponentKey<?> key : container.keys()) {
            if (!options.includes(key)) {
                continue;
            }
            try {
                Component component = key.get(player);
                CompoundTag data = new CompoundTag();
                ComponentFormat format;
                RegisteredAdapter<?> registered = COMPONENT_ADAPTERS.get(key.getId());
                if (registered != null) {
                    writeWithAdapter(registered, component, data, registryLookup);
                    format = ComponentFormat.CUSTOM;
                } else if (component instanceof RoleComponent roleComponent) {
                    // RoleComponent intentionally does not persist normal NBT in this
                    // project. Its sync representation is the authoritative rewind form.
                    roleComponent.writeToSyncNbt(data, registryLookup);
                    format = ComponentFormat.ROLE_SYNC;
                } else {
                    component.writeToNbt(data, registryLookup);
                    format = ComponentFormat.PERSISTENT_NBT;
                }
                componentStates.put(key.getId(), new ComponentState(format, data));
            } catch (RuntimeException exception) {
                String message = describe(exception);
                warnings.add(new TimeRewindSnapshot.Warning(key.getId(), message));
                Noellesroles.LOGGER.warn("Failed to capture CCA component {} for time rewind",
                        key.getId(), exception);
            }
        }

        return new TimeRewindSnapshot(player.getUUID(), player.level().dimension(),
                player.level().getGameTime(), vanillaState, componentStates, warnings);
    }

    /**
     * Restores the snapshot immediately, without animation or queueing. For an
     * animated rewind use
     * {@link #smoothRestore(ServerPlayer, TimeRewindSnapshot, int, Consumer)}.
     */
    public static TimeRewindResult restore(ServerPlayer player, TimeRewindSnapshot snapshot) {
        initialize();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(snapshot, "snapshot");
        requireServerThread(player);

        List<Failure> failures = new ArrayList<>();
        if (!player.getUUID().equals(snapshot.playerId())) {
            failures.add(new Failure("player", VANILLA_STATE_ID,
                    "snapshot belongs to a different player"));
            return new TimeRewindResult(0, failures);
        }

        snapshot.warnings().forEach(warning -> failures.add(
                new Failure("capture", warning.componentId(), warning.message())));

        CompoundTag vanillaState = snapshot.rawVanillaState().copy();
        try {
            moveToSnapshotDimension(player, snapshot, vanillaState);
            playerAccess(player).noellesroles$restoreTimeRewindState(vanillaState);
        } catch (RuntimeException exception) {
            failures.add(new Failure("vanilla", VANILLA_STATE_ID, describe(exception)));
            Noellesroles.LOGGER.error("Failed to restore vanilla player state for {}",
                    player.getScoreboardName(), exception);
            return new TimeRewindResult(0, failures);
        }
        if (!player.isSpectator()) {

            var ppc = SREPlayerPsychoComponent.KEY.get(player);
            if (ppc.psychoTicks > 0) {
                ppc.stopPsycho(true);
            }

            TrainVoicePlugin.resetPlayer(player.getUUID());
        }

        HolderLookup.Provider registryLookup = player.registryAccess();
        List<ComponentKey<?>> restoredKeys = new ArrayList<>();
        List<Map.Entry<ResourceLocation, ComponentState>> componentEntries = new ArrayList<>(
                snapshot.rawComponentStates().entrySet());
        componentEntries.sort(Comparator.comparingInt(entry -> restorePriority(entry.getKey())));
        for (Map.Entry<ResourceLocation, ComponentState> entry : componentEntries) {
            ResourceLocation componentId = entry.getKey();
            ComponentKey<?> key = ComponentRegistry.get(componentId);
            if (key == null || !key.isProvidedBy(player)) {
                failures.add(new Failure("cca", componentId,
                        "component is no longer registered on this player"));
                continue;
            }

            try {
                Component component = key.get(player);
                ComponentState state = entry.getValue();
                CompoundTag data = state.rawData().copy();
                switch (state.format()) {
                    case CUSTOM -> {
                        RegisteredAdapter<?> registered = COMPONENT_ADAPTERS.get(componentId);
                        if (registered == null) {
                            throw new IllegalStateException("custom rewind adapter is no longer registered");
                        }
                        readWithAdapter(registered, component, data, registryLookup);
                    }
                    case ROLE_SYNC -> {
                        if (!(component instanceof RoleComponent roleComponent)) {
                            throw new IllegalStateException("component no longer implements RoleComponent");
                        }
                        roleComponent.readFromSyncNbt(data, registryLookup);
                    }
                    case PERSISTENT_NBT -> component.readFromNbt(data, registryLookup);
                }
                restoredKeys.add(key);
            } catch (RuntimeException exception) {
                failures.add(new Failure("cca", componentId, describe(exception)));
                Noellesroles.LOGGER.warn("Failed to restore CCA component {} for time rewind",
                        componentId, exception);
            }
        }
        // Synchronize only after every component has been restored, so clients never
        // observe a half-rewound graph of related CCA state.
        for (ComponentKey<?> key : restoredKeys) {
            try {
                key.sync(player);
            } catch (RuntimeException exception) {
                failures.add(new Failure("cca_sync", key.getId(), describe(exception)));
                Noellesroles.LOGGER.warn("Failed to synchronize rewound CCA component {}",
                        key.getId(), exception);
            }
        }

        return new TimeRewindResult(restoredKeys.size(), failures);
    }

    /**
     * Animated variant of {@link #restore(ServerPlayer, TimeRewindSnapshot)}:
     * the player is smoothly moved to the snapshot anchor, then the exact same
     * state restoration runs. Returns false only if the snapshot belongs to
     * another player; when the player already has a running rewind, the request
     * is queued and runs after the previous ones finish.
     *
     * <p>
     * Prefer the fluent {@link #smoothRewind(ServerPlayer, TimeRewindSnapshot)}
     * entry point, which exposes the same options with a shorter call chain.
     */
    public static boolean smoothRestore(ServerPlayer player, TimeRewindSnapshot snapshot,
            int durationTicks, Consumer<TimeRewindResult> completion) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(snapshot, "snapshot");
        initialize();
        return TimeRewindPlayback.begin(player, snapshot, durationTicks, completion);
    }

    public static boolean smoothRestore(ServerPlayer player, TimeRewindSnapshot snapshot,
            int durationTicks) {
        return smoothRestore(player, snapshot, durationTicks, null);
    }

    /**
     * Fluent entry point for an animated rewind:
     *
     * <pre>{@code
     * TimeRewind.smoothRewind(player, snapshot)
     *         .duration(80)
     *         .onComplete(result -> { ... })
     *         .start();
     * }</pre>
     *
     * Every option has a sensible default, so the minimal form is
     * {@code TimeRewind.smoothRewind(player, snapshot).start()}. Requests for
     * the same player are queued and run one after another. For the instant,
     * animation-free variant use
     * {@link #restore(ServerPlayer, TimeRewindSnapshot)}.
     */
    public static SmoothRewindBuilder smoothRewind(ServerPlayer player, TimeRewindSnapshot snapshot) {
        return new SmoothRewindBuilder(player, snapshot);
    }

    public static final class SmoothRewindBuilder {
        private final ServerPlayer player;
        private final TimeRewindSnapshot snapshot;
        private int durationTicks = 50;
        private Consumer<TimeRewindResult> completion;

        private SmoothRewindBuilder(ServerPlayer player, TimeRewindSnapshot snapshot) {
            this.player = Objects.requireNonNull(player, "player");
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        }

        /** Visual playback duration in ticks (clamped to [1, 600]). */
        public SmoothRewindBuilder duration(int durationTicks) {
            this.durationTicks = durationTicks;
            return this;
        }

        /**
         * Callback invoked exactly once when the rewind completes, is cancelled
         * or fails. Inspect the returned {@link TimeRewindResult} to tell
         * success from failure.
         */
        public SmoothRewindBuilder onComplete(Consumer<TimeRewindResult> completion) {
            this.completion = completion;
            return this;
        }

        /**
         * Starts the rewind. If the player already has a running or queued
         * rewind, this request joins the queue and runs when its turn comes.
         *
         * @return false only when the snapshot belongs to a different player
         */
        public boolean start() {
            return smoothRestore(player, snapshot, durationTicks, completion);
        }
    }

    /**
     * Cancels the running smooth rewind and drops any queued ones for that
     * player, without restoring the snapshot.
     */
    public static boolean cancelSmoothRestore(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        return TimeRewindPlayback.cancel(player);
    }

    /**
     * Immediately cancels every smooth rewind (running and queued) on the
     * server. Called automatically when a game starts or ends; safe to call
     * from any thread, the work is scheduled onto the server thread.
     */
    public static void cancelAllRewinds(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        TimeRewindPlayback.cancelAll(server);
    }

    public static boolean isSmoothRewinding(ServerPlayer player) {
        return TimeRewindPlayback.isActive(player);
    }

    public static int activeSmoothRewinds() {
        return TimeRewindPlayback.activeCount();
    }

    /** Plays only the client effect; useful for previews and command testing. */
    public static void playVisual(ServerPlayer player, int durationTicks) {
        Objects.requireNonNull(player, "player");
        TimeRewindPlayback.playVisual(player, durationTicks);
    }

    /** Captures dropped items and SmallDoor state inside a game area. */
    public static TimeRewindAreaSnapshot captureArea(ServerLevel level, AABB area) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(area, "area");
        requireServerThread(level);
        return TimeRewindAreaEngine.capture(level, area);
    }

    /** Restores dropped items and SmallDoor state inside a captured game area. */
    public static TimeRewindAreaResult restoreArea(ServerLevel level, TimeRewindAreaSnapshot snapshot) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(snapshot, "snapshot");
        requireServerThread(level);
        if (!level.dimension().equals(snapshot.dimension())) {
            return new TimeRewindAreaResult(0, 0, 0, List.of(
                    new TimeRewindAreaResult.Failure("area", snapshot.dimension().location().toString(),
                            "snapshot belongs to a different dimension")));
        }
        return TimeRewindAreaEngine.restore(level, snapshot);
    }

    /**
     * Registers a specialized snapshot format for one CCA key. Register during mod
     * initialization.
     */
    public static <C extends Component> void registerComponentAdapter(ComponentKey<C> key,
            TimeRewindComponentAdapter<C> adapter) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(adapter, "adapter");
        RegisteredAdapter<C> value = new RegisteredAdapter<>(key, adapter);
        if (COMPONENT_ADAPTERS.putIfAbsent(key.getId(), value) != null) {
            throw new IllegalStateException("A time rewind adapter is already registered for " + key.getId());
        }
    }

    public static void unregisterComponentAdapter(ComponentKey<?> key) {
        COMPONENT_ADAPTERS.remove(key.getId());
    }

    private static TimeRewindPlayerAccess playerAccess(ServerPlayer player) {
        if (!(player instanceof TimeRewindPlayerAccess access)) {
            throw new IllegalStateException("Time-rewind ServerPlayer mixin was not applied");
        }
        return access;
    }

    private static void requireServerThread(ServerPlayer player) {
        requireServerThread(player.serverLevel());
    }

    private static void requireServerThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("TimeRewind must be used on the server thread");
        }
    }

    private static void moveToSnapshotDimension(ServerPlayer player, TimeRewindSnapshot snapshot,
            CompoundTag vanillaState) {
        if (player.level().dimension().equals(snapshot.dimension())) {
            return;
        }
        ServerLevel targetLevel = player.server.getLevel(snapshot.dimension());
        if (targetLevel == null) {
            throw new IllegalStateException("snapshot dimension is not loaded: "
                    + snapshot.dimension().location());
        }

        ListTag pos = vanillaState.getList("Pos", Tag.TAG_DOUBLE);
        ListTag rotation = vanillaState.getList("Rotation", Tag.TAG_FLOAT);
        if (pos.size() < 3 || rotation.size() < 2) {
            throw new IllegalStateException("snapshot has invalid position or rotation");
        }
        player.teleportTo(targetLevel, pos.getDouble(0), pos.getDouble(1), pos.getDouble(2),
                rotation.getFloat(0), rotation.getFloat(1));
    }

    @SuppressWarnings("unchecked")
    private static <C extends Component> void writeWithAdapter(RegisteredAdapter<?> registered,
            Component component, CompoundTag tag, HolderLookup.Provider registryLookup) {
        RegisteredAdapter<C> typed = (RegisteredAdapter<C>) registered;
        typed.adapter().writeSnapshot(typed.key().getComponentClass().cast(component), tag, registryLookup);
    }

    @SuppressWarnings("unchecked")
    private static <C extends Component> void readWithAdapter(RegisteredAdapter<?> registered,
            Component component, CompoundTag tag, HolderLookup.Provider registryLookup) {
        RegisteredAdapter<C> typed = (RegisteredAdapter<C>) registered;
        typed.adapter().readSnapshot(typed.key().getComponentClass().cast(component), tag, registryLookup);
    }

    private static String describe(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static int restorePriority(ResourceLocation componentId) {
        RegisteredAdapter<?> adapter = COMPONENT_ADAPTERS.get(componentId);
        return adapter == null ? 0 : adapter.adapter().restorePriority();
    }

    private record RegisteredAdapter<C extends Component>(ComponentKey<C> key,
            TimeRewindComponentAdapter<C> adapter) {
    }
}
