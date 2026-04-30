package org.agmas.noellesroles.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class DnfHellTrailSceneManager {
    public static final DnfHellTrailSceneManager INSTANCE = new DnfHellTrailSceneManager();
    private final Map<BlockPos, BlockState> original = new HashMap<>();
    private final Map<BlockPos, Integer> ttl = new HashMap<>();
    private final Random random = new Random();

    private static final BlockState[] HELL = new BlockState[]{
            Blocks.NETHERRACK.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState(),
            Blocks.SOUL_SAND.defaultBlockState(), Blocks.RED_NETHER_BRICKS.defaultBlockState()
    };

    public void tick(boolean active) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ClientLevel level = mc.level;
        if (active) {
            BlockPos c = mc.player.blockPosition();
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
                if (x * x + z * z > 10) continue;
                BlockPos p = c.offset(x, -1, z);
                if (!level.isLoaded(p)) continue;
                BlockState st = level.getBlockState(p);
                if (st.isAir() || !st.isSolidRender(level, p)) continue;
                if (!original.containsKey(p)) original.put(p.immutable(), st);
                level.setBlock(p, HELL[random.nextInt(HELL.length)], 3);
                ttl.put(p.immutable(), 80);
            }
        }
        Iterator<Map.Entry<BlockPos, Integer>> it = ttl.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            int t = e.getValue() - 1;
            if (t <= 0) {
                BlockState st = original.remove(e.getKey());
                if (st != null && level.isLoaded(e.getKey())) level.setBlock(e.getKey(), st, 3);
                it.remove();
            } else e.setValue(t);
        }
    }
}
