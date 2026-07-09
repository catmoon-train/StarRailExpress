package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.block_entity.DoorBlockEntity;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.CommonTickingComponent;

import java.util.*;

public class MurderTimeEventComponent implements AutoSyncedComponent, CommonTickingComponent {
    public static final String GOLD_AMOUNT_TAG = "sre_murder_gold";
    public static final ComponentKey<MurderTimeEventComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("murder_time_events"), MurderTimeEventComponent.class);

    private static final int DEFAULT_BLACKOUT_DURATION = 35 * 20;
    private static final int DEFAULT_SECOND_BLACKOUT_DURATION = 45 * 20;
    private static final int DEFAULT_WARNING_TICKS = 30 * 20;
    private static final int DEFAULT_STATUS_TICKS = 30 * 20;

    private final Level world;
    private final ArrayList<MurderTimeEvent> events = new ArrayList<>();
    private boolean enabled = true;
    private boolean hudEnabled = true;
    private int lastElapsedTicks = 0;

    public MurderTimeEventComponent(Level world) {
        this.world = world;
    }

    public void sync() {
        KEY.sync(this.world);
    }

    public List<MurderTimeEvent> getEvents() {
        return events;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        sync();
    }

    public boolean isHudEnabled() {
        return hudEnabled;
    }

    public void setHudEnabled(boolean hudEnabled) {
        this.hudEnabled = hudEnabled;
        sync();
    }

    public void clear() {
        this.events.clear();
        this.lastElapsedTicks = 0;
        sync();
    }

    public void resetTriggered() {
        this.events.forEach(event -> {
            event.triggered = false;
            event.visibleUntilElapsedTicks = -1;
        });
        this.lastElapsedTicks = 0;
        sync();
    }

    public void initializeDefaults() {
        this.events.clear();
        this.enabled = false;
        this.hudEnabled = false;
        this.lastElapsedTicks = 0;
        maybeAddDefault("opening_blackout", "murder_time.event.opening_blackout",
                75, 240, 0.45F, MurderTimeAction.BLACKOUT, DEFAULT_BLACKOUT_DURATION, 0, DEFAULT_WARNING_TICKS);
        maybeAddDefault("damaged_locks", "murder_time.event.damaged_locks",
                180, 420, 0.35F, MurderTimeAction.DAMAGE_DOOR_LOCKS, DEFAULT_STATUS_TICKS, 8, DEFAULT_WARNING_TICKS);
        maybeAddDefault("scattered_gold", "murder_time.event.scattered_gold",
                240, 540, 0.45F, MurderTimeAction.DROP_GOLD, 8, 15, DEFAULT_WARNING_TICKS);
        maybeAddDefault("second_blackout", "murder_time.event.second_blackout",
                420, 720, 0.30F, MurderTimeAction.BLACKOUT, DEFAULT_SECOND_BLACKOUT_DURATION, 0, DEFAULT_WARNING_TICKS);
        maybeAddDefault("late_gold", "murder_time.event.late_gold",
                540, 900, 0.35F, MurderTimeAction.DROP_GOLD, 10, 20, DEFAULT_WARNING_TICKS);
        sync();
    }

    private void maybeAddDefault(String id, String nameKey, int minSeconds, int maxSeconds, float chance,
            MurderTimeAction action, int durationTicks, int amount, int warningTicks) {
        if (world.random.nextFloat() >= chance) {
            return;
        }
        int seconds = minSeconds + world.random.nextInt(Math.max(1, maxSeconds - minSeconds + 1));
        this.events.add(
                new MurderTimeEvent(id, nameKey, seconds * 20, action, durationTicks, amount, warningTicks, false, -1));
        this.events.sort(Comparator.comparingInt(MurderTimeEvent::elapsedTicks));
    }

    public void addEvent(MurderTimeEvent event) {
        removeEvent(event.id);
        this.events.add(event);
        this.events.sort(Comparator.comparingInt(MurderTimeEvent::elapsedTicks));
        sync();
    }

    public boolean removeEvent(String id) {
        boolean removed = this.events.removeIf(event -> event.id.equals(id));
        if (removed) {
            sync();
        }
        return removed;
    }

    public MurderTimeEvent getEvent(String id) {
        return this.events.stream().filter(event -> event.id.equals(id)).findFirst().orElse(null);
    }

    @Override
    public void tick() {
        if (world.isClientSide || !enabled || !(world instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!SREGameWorldComponent.KEY.get(world).isRunning()) {
            return;
        }

        SREGameTimeComponent time = SREGameTimeComponent.KEY.get(world);
        int elapsedTicks = Math.max(0, time.getResetTime() - time.getTime());
        if (elapsedTicks < lastElapsedTicks) {
            lastElapsedTicks = elapsedTicks;
            return;
        }

        boolean changed = false;
        for (MurderTimeEvent event : events) {
            if (!event.triggered && lastElapsedTicks < event.elapsedTicks && elapsedTicks >= event.elapsedTicks) {
                triggerEvent(serverLevel, event, true);
                changed = true;
            }
        }
        lastElapsedTicks = elapsedTicks;
        if (changed || elapsedTicks % 200 == 0) {
            sync();
        }
    }

    public boolean triggerEvent(ServerLevel level, MurderTimeEvent event, boolean markTriggered) {
        if (event == null) {
            return false;
        }
        boolean success = switch (event.action) {
            case BLACKOUT -> triggerBlackout(level, event.durationTicks);
            case DAMAGE_DOOR_LOCKS -> damageDoorLocks(level, event.amount);
            case DROP_GOLD -> dropGold(level, event.amount, event.durationTicks);
            case ANNOUNCE -> true;
        };
        if (success) {
            if (markTriggered) {
                event.triggered = true;
                int displayTicks = event.action == MurderTimeAction.DROP_GOLD
                        ? Math.max(DEFAULT_STATUS_TICKS, event.durationTicks * 20)
                        : Math.max(DEFAULT_STATUS_TICKS, event.durationTicks);
                event.visibleUntilElapsedTicks = getElapsedTicks();
                event.visibleUntilElapsedTicks += displayTicks;
            }
            sync();
        }
        return success;
    }

    private int getElapsedTicks() {
        SREGameTimeComponent time = SREGameTimeComponent.KEY.get(world);
        return Math.max(0, time.getResetTime() - time.getTime());
    }

    private boolean triggerBlackout(ServerLevel level, int durationTicks) {
        int duration = durationTicks > 0 ? durationTicks : DEFAULT_BLACKOUT_DURATION;
        return SREWorldBlackoutComponent.KEY.get(level).triggerBlackout(true, duration);
    }

    private boolean damageDoorLocks(ServerLevel level, int targetCount) {
        int amount = targetCount > 0 ? targetCount : 6;
        ArrayList<DoorBlockEntity> doors = collectLoadedDoors(level);
        if (doors.isEmpty()) {
            return false;
        }
        java.util.Collections.shuffle(doors);
        int damaged = 0;
        for (DoorBlockEntity door : doors) {
            if (damaged >= amount) {
                break;
            }
            if (door.isBlasted()) {
                continue;
            }
            door.jam();
            door.setChanged();
            damaged++;
        }
        return damaged > 0;
    }

    private ArrayList<DoorBlockEntity> collectLoadedDoors(ServerLevel level) {
        ArrayList<DoorBlockEntity> doors = new ArrayList<>();
        Set<ChunkPos> visited = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            ChunkPos center = new ChunkPos(player.blockPosition());
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    ChunkPos chunkPos = new ChunkPos(center.x + dx, center.z + dz);
                    if (!visited.add(chunkPos)) {
                        continue;
                    }
                    LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
                    for (var blockEntity : chunk.getBlockEntities().values()) {
                        if (blockEntity instanceof DoorBlockEntity door) {
                            doors.add(door);
                        }
                    }
                }
            }
        }
        return doors;
    }

    private boolean dropGold(ServerLevel level, int amountPerPile, int pileCount) {
        List<ServerPlayer> players = level.players().stream()
                .filter(GameUtils::isPlayerAliveAndSurvival)
                .toList();
        if (players.isEmpty()) {
            return false;
        }
        int piles = pileCount > 0 ? pileCount : Math.max(4, players.size());
        int amount = amountPerPile > 0 ? amountPerPile : 10;
        for (int i = 0; i < piles; i++) {
            ServerPlayer anchor = players.get(level.random.nextInt(players.size()));
            BlockPos pos = randomGoldPosNear(level, anchor.blockPosition());
            ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.25D, pos.getZ() + 0.5D,
                    createGoldStack(amount));
            entity.setPickUpDelay(20);
            level.addFreshEntity(entity);
        }
        return true;
    }

    private BlockPos randomGoldPosNear(ServerLevel level, BlockPos anchor) {
        for (int tries = 0; tries < 8; tries++) {
            int x = anchor.getX() + level.random.nextInt(33) - 16;
            int z = anchor.getZ() + level.random.nextInt(33) - 16;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, anchor.getY() + 8, z);
            while (pos.getY() > level.getMinBuildHeight() + 1 && level.getBlockState(pos).isAir()) {
                pos.move(0, -1, 0);
            }
            pos.move(0, 1, 0);
            if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isSolidRender(level, pos)) {
                return pos.immutable();
            }
        }
        return anchor;
    }

    public static ItemStack createGoldStack(int amount) {
        ItemStack stack = new ItemStack(Items.GOLD_NUGGET);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.translatable("item.starrailexpress.murder_gold", amount).withStyle(ChatFormatting.GOLD));
        CompoundTag tag = new CompoundTag();
        tag.putInt(GOLD_AMOUNT_TAG, Math.max(1, amount));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static int getMurderGoldAmount(ItemStack stack) {
        if (!stack.is(Items.GOLD_NUGGET)) {
            return 0;
        }
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        return tag.contains(GOLD_AMOUNT_TAG) ? Math.max(0, tag.getInt(GOLD_AMOUNT_TAG)) : 0;
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("Enabled", enabled);
        tag.putBoolean("HudEnabled", hudEnabled);
        tag.putInt("LastElapsedTicks", lastElapsedTicks);
        ListTag eventList = new ListTag();
        for (MurderTimeEvent event : events) {
            eventList.add(event.toNbt());
        }
        tag.put("Events", eventList);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
        hudEnabled = !tag.contains("HudEnabled") || tag.getBoolean("HudEnabled");
        lastElapsedTicks = tag.getInt("LastElapsedTicks");
        events.clear();
        ListTag eventList = tag.getList("Events", Tag.TAG_COMPOUND);
        for (int i = 0; i < eventList.size(); i++) {
            events.add(MurderTimeEvent.fromNbt(eventList.getCompound(i)));
        }
        events.sort(Comparator.comparingInt(MurderTimeEvent::elapsedTicks));
    }

    public enum MurderTimeAction {
        BLACKOUT,
        DAMAGE_DOOR_LOCKS,
        DROP_GOLD,
        ANNOUNCE;

        public static MurderTimeAction byName(String name) {
            return MurderTimeAction.valueOf(name.toUpperCase(Locale.ROOT));
        }
    }

    public static class MurderTimeEvent {
        private final String id;
        private final String nameKey;
        private final int elapsedTicks;
        private final MurderTimeAction action;
        private final int durationTicks;
        private final int amount;
        private final int warningTicks;
        private boolean triggered;
        private int visibleUntilElapsedTicks;

        public MurderTimeEvent(String id, String nameKey, int elapsedTicks, MurderTimeAction action,
                int durationTicks, int amount, int warningTicks, boolean triggered,
                int visibleUntilElapsedTicks) {
            this.id = id;
            this.nameKey = nameKey;
            this.elapsedTicks = Math.max(0, elapsedTicks);
            this.action = action;
            this.durationTicks = Math.max(0, durationTicks);
            this.amount = Math.max(0, amount);
            this.warningTicks = Math.max(0, warningTicks);
            this.triggered = triggered;
            this.visibleUntilElapsedTicks = visibleUntilElapsedTicks;
        }

        public String id() {
            return id;
        }

        public int elapsedTicks() {
            return elapsedTicks;
        }

        public MurderTimeAction action() {
            return action;
        }

        public int durationTicks() {
            return durationTicks;
        }

        public int amount() {
            return amount;
        }

        public int warningTicks() {
            return warningTicks;
        }

        public boolean triggered() {
            return triggered;
        }

        public boolean shouldShowInHud(int elapsedTicks) {
            if (!triggered) {
                return elapsedTicks >= elapsedTicks() - warningTicks && elapsedTicks <= elapsedTicks();
            }
            return visibleUntilElapsedTicks > elapsedTicks;
        }

        public float hudProgress(int elapsedTicks) {
            if (!triggered) {
                if (warningTicks <= 0) {
                    return 1.0F;
                }
                return 1.0F - Math.max(0, elapsedTicks() - elapsedTicks) / (float) warningTicks;
            }
            int activeTicks = Math.max(1, visibleUntilElapsedTicks - elapsedTicks());
            return Math.max(0.0F, Math.min(1.0F, (visibleUntilElapsedTicks - elapsedTicks) / (float) activeTicks));
        }

        public boolean isWarning(int elapsedTicks) {
            return !triggered && shouldShowInHud(elapsedTicks);
        }

        public Component displayName() {
            return Component.translatableWithFallback(nameKey, id);
        }

        private CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", id);
            tag.putString("NameKey", nameKey);
            tag.putInt("ElapsedTicks", elapsedTicks);
            tag.putString("Action", action.name());
            tag.putInt("DurationTicks", durationTicks);
            tag.putInt("Amount", amount);
            tag.putInt("WarningTicks", warningTicks);
            tag.putBoolean("Triggered", triggered);
            tag.putInt("VisibleUntilElapsedTicks", visibleUntilElapsedTicks);
            return tag;
        }

        private static MurderTimeEvent fromNbt(CompoundTag tag) {
            MurderTimeAction action = MurderTimeAction.byName(tag.getString("Action"));
            return new MurderTimeEvent(
                    tag.getString("Id"),
                    tag.getString("NameKey"),
                    tag.getInt("ElapsedTicks"),
                    action,
                    tag.getInt("DurationTicks"),
                    tag.getInt("Amount"),
                    tag.contains("WarningTicks") ? tag.getInt("WarningTicks") : DEFAULT_WARNING_TICKS,
                    tag.getBoolean("Triggered"),
                    tag.contains("VisibleUntilElapsedTicks") ? tag.getInt("VisibleUntilElapsedTicks") : -1);
        }
    }
}
