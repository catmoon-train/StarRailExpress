package org.agmas.noellesroles.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.content.block_entity.ChefPlateBlockEntity;
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
 * <p>
 * <b>盘子里装的东西不在这里渲染</b>：这里只把服务端下发的那个 {@link ItemStack} 存下来，
 * 再挂一个客户端本地的 {@link BeveragePlateBlockEntity}，由本模组原有的
 * {@code io.wifi.starrailexpress.client.render.block_entity.PlateBlockEntityRenderer}
 * 负责绘制——和原版食物盘 / 饮料盘走完全同一条渲染管线（同一个 BE 类型、同一个渲染器），
 * 所以坐标、光照、可见性剔除、遮挡关系都由引擎处理，不会再出现物品漂移或不渲染。
 * 该渲染器会识别厨师盘子并把物品改为<b>居中</b>摆放。
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
                    packet.content());
            case ChefTrayS2CPacket.ACTION_UPDATE -> setContent(packet.trayId(), packet.content());
            case ChefTrayS2CPacket.ACTION_REMOVE -> removeTray(packet.trayId());
            default -> {
            }
        }
    }

    /** 新建一个盘子。 */
    public static void createTray(UUID id, BlockPos pos, boolean drink, ItemStack content) {
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
        level.setBlock(immutable, tray.selfState(), 3);
        if (content != null && !content.isEmpty()) {
            setContent(id, content);
        } else {
            attachBlockEntity(level, tray);
        }
    }

    /** 更新盘子里要渲染的那一份物品。 */
    public static void setContent(UUID id, ItemStack content) {
        ClientTray tray = TRAYS.get(id);
        if (tray == null) {
            return;
        }
        tray.content = content == null ? ItemStack.EMPTY : content.copy();
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        if (!tray.content.isEmpty()) {
            // 盘子里有货时冒一点点热气，方便远处辨认
            level.addAlwaysVisibleParticle(ParticleTypes.HAPPY_VILLAGER, false,
                    tray.pos.getX() + 0.5D, tray.pos.getY() + 0.4D, tray.pos.getZ() + 0.5D,
                    0.01D, 0.02D, 0.02D);
        }
        attachBlockEntity(level, tray);
    }

    /**
     * 给盘子挂上（客户端本地的）盘面方块实体，物品渲染交给 {@code ChefPlateRenderer}。
     *
     * <p>已存在则复用并同步内容物——厨师放入第二份时 {@link #setContent} 会更新
     * {@code tray.content}，必须跟着写进 BE，否则渲染的还是旧物品。
     * {@code setDrink} / {@code addItem} 内部的同步在客户端会被跳过，调用是安全的。
     */
    private static void attachBlockEntity(ClientLevel level, ClientTray tray) {
        ChefPlateBlockEntity plate;
        if (level.getBlockEntity(tray.pos) instanceof ChefPlateBlockEntity existing) {
            plate = existing;
        } else {
            plate = new ChefPlateBlockEntity(tray.pos, tray.selfState());
            plate.setDrink(tray.drink);
            level.setBlockEntity(plate);
        }
        plate.getStoredItems().clear();
        if (!tray.content.isEmpty()) {
            plate.addItem(tray.content.copy());
        }
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
            level.removeBlockEntity(tray.pos);
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
                // 被左键拆掉，或被服务端下发的区块数据刷掉（服务端并不承认这个方块）
                // → 按盘子本体重新画回来。注意 setBlock 会连带清掉该位置的方块实体，
                //   所以下面必须重新挂一次。
                level.setBlock(tray.pos, tray.selfState(), 3);
            }
            attachBlockEntity(level, tray);
        }
    }

    /** 清除所有盘子（游戏结束时调用）。 */
    public static void clearAll() {
        ClientLevel level = Minecraft.getInstance().level;
        for (ClientTray tray : TRAYS.values()) {
            if (level != null && level.getBlockState(tray.pos).is(tray.selfState().getBlock())) {
                level.removeBlockEntity(tray.pos);
                level.setBlock(tray.pos, tray.origin, 3);
            }
        }
        TRAYS.clear();
        INDEX.clear();
    }

    /** 一个客户端盘子的数据。 */
    private static final class ClientTray {
        final UUID id;
        final BlockPos pos;
        final boolean drink;
        final BlockState origin;
        /** 盘子里摆放的那一份物品，由服务端下发；只用于渲染，不参与任何交互判定。 */
        ItemStack content = ItemStack.EMPTY;

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
