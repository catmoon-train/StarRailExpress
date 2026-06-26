package org.agmas.noellesroles.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Client-only visual blocks for Cake Maker.
 * <p>
 * Smoker and cake blocks are never added to collision tracking, and are
 * restored every tick to prevent server block updates / player interactions
 * from overwriting them.
 */
@Environment(EnvType.CLIENT)
public final class ClientCakeMakerBlocks {

    private static final Map<UUID, Entry> BLOCKS = new HashMap<>();

    private ClientCakeMakerBlocks() {
    }

    // ── Placement ─────────────────────────────────────────────

    /**
     * Places a client-side smoker or cake at {@code pos}.
     *
     * @param id    unique instance ID
     * @param pos   block position
     * @param cake  {@code true} = cake, {@code false} = smoker
     * @param bites cake bite count (0–6), ignored for smoker
     * @param ticks remaining lifetime in ticks
     */
    public static void put(UUID id, BlockPos pos, boolean cake, int bites, int ticks) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        BlockState existing = level.getBlockState(pos);
        BlockState state = cake
                ? Blocks.CAKE.defaultBlockState().setValue(CakeBlock.BITES, bites)
                : Blocks.SMOKER.defaultBlockState();

        // Only allow placement on air, or update of our own existing block
        if (!existing.isAir() && !existing.is(state.getBlock())) {
            return;
        }
        level.setBlock(pos, state, 3);
        BLOCKS.put(id, new Entry(pos, state, ticks));
    }

    // ── Lifecycle ─────────────────────────────────────────────

    /** Removes all client blocks. Call when the game ends. */
    public static void clearAll() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            for (Entry entry : BLOCKS.values()) {
                level.setBlock(entry.pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        BLOCKS.clear();
    }

    // ── Removal ───────────────────────────────────────────────

    /** Removes a client block by ID, restoring the position to air. */
    public static void remove(UUID id) {
        Entry entry = BLOCKS.remove(id);
        ClientLevel level = Minecraft.getInstance().level;
        if (entry != null && level != null) {
            level.setBlock(entry.pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    // ── Queries ───────────────────────────────────────────────

    /** Returns {@code true} if any cake maker block exists at the given position. */
    public static boolean isAt(BlockPos pos) {
        for (Entry entry : BLOCKS.values()) {
            if (entry.pos.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    /** Returns {@code true} if the block at {@code pos} is a cake (not a smoker). */
    public static boolean isCake(BlockPos pos) {
        for (Entry entry : BLOCKS.values()) {
            if (entry.pos.equals(pos) && entry.state.is(Blocks.CAKE)) {
                return true;
            }
        }
        return false;
    }

    /** Returns the cake ID at {@code pos}, or {@code null} if none. */
    public static UUID cakeId(BlockPos pos) {
        return BLOCKS.entrySet().stream()
                .filter(e -> e.getValue().pos.equals(pos) && e.getValue().state.is(Blocks.CAKE))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    // ── Tick ──────────────────────────────────────────────────

    /** Call every client tick. Expires blocks and restores overwritten ones. */
    public static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        Iterator<Map.Entry<UUID, Entry>> iterator = BLOCKS.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();

            // Lifetime expired
            if (--entry.ticks <= 0) {
                level.setBlock(entry.pos, Blocks.AIR.defaultBlockState(), 3);
                iterator.remove();
                continue;
            }

            // Restore if overwritten by a server block update or player interaction
            BlockState current = level.getBlockState(entry.pos);
            if (!current.is(entry.state.getBlock())) {
                level.setBlock(entry.pos, entry.state, 3);
            }

            // Update cake bite count if changed
            if (entry.state.is(Blocks.CAKE) && current.is(Blocks.CAKE)) {
                int currentBites = current.getValue(CakeBlock.BITES);
                if (currentBites != entry.state.getValue(CakeBlock.BITES)) {
                    entry.state = entry.state.setValue(CakeBlock.BITES, currentBites);
                }
            }
        }
    }

    // ── Inner type ────────────────────────────────────────────

    private static final class Entry {
        final BlockPos pos;
        BlockState state;
        int ticks;

        Entry(BlockPos pos, BlockState state, int ticks) {
            this.pos = pos;
            this.state = state;
            this.ticks = ticks;
        }
    }
}
