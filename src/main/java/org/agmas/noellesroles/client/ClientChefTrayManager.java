package org.agmas.noellesroles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
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
 * 盘子本体只有「空盘」一种模型；<b>盘子里装的东西不用方块模型表达</b>，
 * 而是由 {@link #renderTrayContents} 用物品渲染器把厨师放进去的那个食物 / 饮料的
 * <b>真实物品模型</b>画在盘子上（会随时间缓慢自转），这样盘中物品的贴图、模型变体
 * （烹饪食物有 cooked_food/1~4 多个模型）都由物品系统自己解析，不会退化成「一块贴了物品贴图的方块」。
 *
 * <p>另外每 tick 把被破坏 / 被服务端区块更新覆盖的盘子恢复回来，避免玩家左键拆盘子。
 */
@Environment(EnvType.CLIENT)
public class ClientChefTrayManager {

    private static final Map<UUID, ClientTray> TRAYS = new LinkedHashMap<>();
    /** 坐标 → 盘 id，让「右键命中的是不是盘子」变成 O(1) 查询。 */
    private static final Map<BlockPos, UUID> INDEX = new HashMap<>();

    /*
     * 盘内物品的摆放参数：完全沿用本模组原有食物盘 / 饮料盘的渲染器
     * （io.wifi.starrailexpress.client.render.block_entity.PlateBlockEntityRenderer），
     * 唯一的区别是物品摆在盘子正中间，而不是像原版那样沿半径 0.25 的圆周排开。
     */

    /** 盘内物品的缩放，与原版 PlateBlockEntityRenderer 一致。 */
    private static final float ITEM_SCALE = 0.4F;
    /** 食物在盘里平躺的角度，与原版一致；饮料保持直立。 */
    private static final float FOOD_TILT_DEGREES = 75.0F;
    /** 盘内物品相对方块底部的摆放高度，与原版一致：食物贴着盘面，饮料杯更高一些。 */
    private static final double FOOD_ITEM_Y = 0.0375D;
    private static final double DRINK_ITEM_Y = 0.225D;
    /** 距离剔除平方上限，与原版一致（16 格）。 */
    private static final double MAX_RENDER_DISTANCE_SQ = 16.0D * 16.0D;

    private ClientChefTrayManager() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ClientChefTrayManager::renderTrayContents);
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
        applyState(level, tray);
        if (content != null && !content.isEmpty()) {
            setContent(id, content);
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
        if (level != null && !tray.content.isEmpty()) {
            // 盘子里有货时冒一点点热气，方便远处辨认
            level.addAlwaysVisibleParticle(ParticleTypes.HAPPY_VILLAGER, false,
                    tray.pos.getX() + 0.5D, tray.pos.getY() + 0.4D, tray.pos.getZ() + 0.5D,
                    0.01D, 0.02D, 0.02D);
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
                // → 按盘子本体重新画回来。盘内物品由渲染器负责，不受这里影响。
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

    /**
     * 把盘子里的食物 / 饮料按<b>真实物品模型</b>画在盘子正中间。
     *
     * <p>
     * 摆放参数（缩放、离盘面的高度、食物的平躺角度、距离剔除、光照）全部对齐本模组原有的
     * {@code PlateBlockEntityRenderer}，所以盘内物品既不会浮空、也不会自转，
     * 看上去和原版食物盘 / 饮料盘里摆的东西一致；只是把原版沿圆周排开的布局改成了居中一排。
     */
    public static void renderTrayContents(WorldRenderContext context) {
        if (TRAYS.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null || client.player == null) {
            return;
        }
        PoseStack poseStack = context.matrixStack();
        MultiBufferSource buffers = context.consumers();
        if (poseStack == null || buffers == null) {
            return;
        }
        ItemRenderer itemRenderer = client.getItemRenderer();
        if (itemRenderer == null) {
            return;
        }

        for (ClientTray tray : TRAYS.values()) {
            if (tray.content.isEmpty()) {
                continue;
            }
            // 距离剔除：与原版盘子渲染器一致
            if (client.player.distanceToSqr(Vec3.atCenterOf(tray.pos)) > MAX_RENDER_DISTANCE_SQ) {
                continue;
            }

            // 沿用原版高度：食物贴着盘面，饮料杯摆在稍高的地方
            double itemY = tray.drink ? DRINK_ITEM_Y : FOOD_ITEM_Y;
            int light = LightTexture.pack(level.getBrightness(LightLayer.BLOCK, tray.pos),
                    level.getBrightness(LightLayer.SKY, tray.pos));

            poseStack.pushPose();
            /*
             * 直接用绝对世界坐标摆放：WorldRenderEvents 阶段的矩阵已经带上了相机平移，
             * 这里如果再减一次 camera，物品就会被额外偏移「盘子位置 - 玩家位置」，
             * 于是它在世界里相对玩家静止，看上去就是「跟着玩家一起动」。
             * （原版 PlateBlockEntityRenderer 之所以只写 0.5 / centerY 这种局部坐标，
             *   也正是因为 BlockEntityRenderDispatcher 已经把矩阵平移到了方块位置。）
             */
            poseStack.translate(tray.pos.getX() + 0.5D, tray.pos.getY() + itemY, tray.pos.getZ() + 0.5D);
            if (!tray.drink) {
                // 食物平躺在盘里（和原版一样）；饮料保持直立。均为固定朝向，不做任何自转。
                poseStack.mulPose(Axis.XP.rotationDegrees(FOOD_TILT_DEGREES));
            }
            poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            itemRenderer.renderStatic(tray.content, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                    poseStack, buffers, level, 0);
            poseStack.popPose();
        }
    }

    private static void applyState(ClientLevel level, ClientTray tray) {
        level.setBlock(tray.pos, tray.selfState(), 3);
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
