package org.agmas.noellesroles.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.agmas.noellesroles.init.ModBlocks;
import org.agmas.noellesroles.packet.ChefTrayS2CPacket;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 客户端「食物盘 / 饮料盘」管理器。
 *
 * <p>盘子只存在于客户端世界里：收到服务端的 S2C 包后在这里 {@code level.setBlock} 画出来，
 * 并记住原来被覆盖的方块，移除时还原（做法与建筑师的 {@link ClientWallManager} 一致）。
 *
 * <p>另外每 tick 把被破坏 / 被服务端区块更新覆盖的盘子恢复回来，避免玩家左键拆盘子。
 */
@Environment(EnvType.CLIENT)
public class ClientChefTrayManager {

    private static final Map<UUID, ClientTray> TRAYS = new LinkedHashMap<>();
    /** 坐标 → 盘 id，让「右键命中的是不是盘子」变成 O(1) 查询。 */
    private static final Map<BlockPos, UUID> INDEX = new HashMap<>();

    private ClientChefTrayManager() {
    }

    /** 该位置是否是「客户端」盘子。 */
    public static boolean isTrayAt(BlockPos pos) {
        return INDEX.containsKey(pos);
    }

    /** 收到 S2C 包：按动作分发。 */
    public static void handle(ChefTrayS2CPacket packet) {
        switch (packet.action()) {
            case ChefTrayS2CPacket.ACTION_PLACE -> createTray(packet.trayId(), packet.pos(), packet.drink(),
                    packet.filled());
            case ChefTrayS2CPacket.ACTION_UPDATE -> setFilled(packet.trayId(), packet.filled());
            case ChefTrayS2CPacket.ACTION_REMOVE -> removeTray(packet.trayId());
            default -> {
            }
        }
    }

    /** 新建一个盘子。 */
    public static void createTray(UUID id, BlockPos pos, boolean drink, boolean filled) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || TRAYS.containsKey(id)) {
            return;
        }
        BlockState origin = level.getBlockState(pos);
        if (!origin.isAir()) {
            // 服务端只会在空气位放盘子；这里再兜一次底，避免覆盖真实方块
            return;
        }
        BlockPos immutable = pos.immutable();
        ClientTray tray = new ClientTray(id, immutable, drink, origin);
        TRAYS.put(id, tray);
        INDEX.put(immutable, id);
        applyState(level, tray, filled);
    }

    /** 更新「空 / 满」模型。 */
    public static void setFilled(UUID id, boolean filled) {
        ClientTray tray = TRAYS.get(id);
        ClientLevel level = Minecraft.getInstance().level;
        if (tray == null || level == null) {
            return;
        }
        tray.filled = filled;
        applyState(level, tray, filled);
    }

    /** 移除一个盘子并还原它占用的方块。 */
    public static void removeTray(UUID id) {
        ClientTray tray = TRAYS.remove(id);
        if (tray == null) {
            return;
        }
        INDEX.remove(tray.pos);
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.getBlockState(tray.pos).is(tray.selfState().getBlock())) {
            level.setBlock(tray.pos, tray.origin, 3);
        }
    }

    /** 每 tick：恢复被破坏 / 被覆盖的盘子。 */
    public static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || TRAYS.isEmpty()) {
            return;
        }
        for (ClientTray tray : TRAYS.values()) {
            if (!level.getBlockState(tray.pos).is(tray.selfState().getBlock())) {
                // 被左键拆掉或被别的方块顶替 → 重新画回来
                level.setBlock(tray.pos, tray.selfState(), 3);
            }
        }
    }

    /** 清除所有盘子（游戏结束时调用）。 */
    public static void clearAll() {
        ClientLevel level = Minecraft.getInstance().level;
        for (ClientTray tray : TRAYS.values()) {
            if (level != null && level.getBlockState(tray.pos).is(tray.selfState().getBlock())) {
                level.setBlock(tray.pos, tray.origin, 3);
            }
        }
        TRAYS.clear();
        INDEX.clear();
    }

    private static void applyState(ClientLevel level, ClientTray tray, boolean filled) {
        BlockState state = tray.selfState().setValue(BlockStateProperties.OCCUPIED, filled);
        level.setBlock(tray.pos, state, 3);
        if (filled) {
            // 盘子里有货时冒一点点热气，方便远处辨认
            level.addAlwaysVisibleParticle(ParticleTypes.HAPPY_VILLAGER, false,
                    tray.pos.getX() + 0.5D, tray.pos.getY() + 0.4D, tray.pos.getZ() + 0.5D,
                    0.01D, 0.02D, 0.02D);
        }
    }

    /** 一个客户端盘子的数据。 */
    private static final class ClientTray {
        final UUID id;
        final BlockPos pos;
        final boolean drink;
        final BlockState origin;
        boolean filled;

        ClientTray(UUID id, BlockPos pos, boolean drink, BlockState origin) {
            this.id = id;
            this.pos = pos;
            this.drink = drink;
            this.origin = origin;
        }

        BlockState selfState() {
            Block block = drink ? ModBlocks.CHEF_DRINK_TRAY : ModBlocks.CHEF_FOOD_TRAY;
            return block.defaultBlockState();
        }
    }
}
