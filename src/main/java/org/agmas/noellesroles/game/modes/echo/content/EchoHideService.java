package org.agmas.noellesroles.game.modes.echo.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 草丛隐身的**纯判定**逻辑：不写任何玩家状态，服务端与客户端共用同一份规则。
 *
 * <p>规则（已与设计确认）：
 * <ul>
 * <li>站在草丛里的玩家，对**不在草丛里的截流者**隐身；</li>
 * <li>截流者自己站进草丛后，能看见草丛里的所有人；</li>
 * <li>托运者/投机客之间始终互相可见，不受草丛影响。</li>
 * </ul>
 *
 * <p>为什么不用原版 {@code Invisible}：那是全局标记，一旦打上，**所有人**都看不见，
 * 无法表达「同一目标对 A 可见、对 B 不可见」。所以这里只在客户端渲染层按观察者裁剪，
 * 服务端不修改任何实体标记，也不新增同步字段。
 */
public final class EchoHideService {
    /** 判定「在草丛里」时脚部向上抬一点，避免站在半砖上被误判为不在草丛。 */
    private static final double FOOT_LIFT = 0.35D;

    private EchoHideService() { }

    /** 玩家身体是否与任意一格草丛重叠（脚部以上到胸部）。 */
    public static boolean hiding(Player player) {
        if (player == null) return false;
        Level level = player.level();
        if (level == null) return false;
        AABB box = player.getBoundingBox();
        int minX = (int) Math.floor(box.minX + 1.0E-4D), maxX = (int) Math.floor(box.maxX - 1.0E-4D);
        int minZ = (int) Math.floor(box.minZ + 1.0E-4D), maxZ = (int) Math.floor(box.maxZ - 1.0E-4D);
        int minY = (int) Math.floor(box.minY + FOOT_LIFT);
        int maxY = (int) Math.floor(box.minY + 1.4D);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.hasChunkAt(pos)) continue;
                    if (level.getBlockState(pos).getBlock() instanceof EchoHideBlock) return true;
                }
            }
        }
        return false;
    }

    /**
     * 观察者能否看见目标。{@code viewerHiding}/{@code targetHiding} 由 {@link #hiding(Player)} 得到，
     * {@code viewerIsInterceptor} 由阵营查询得到。
     *
     * <p>这是整条链路的**唯一**规则来源：服务端校验与客户端渲染都调用它，
     * 避免出现「服务端说看不见、客户端还在渲染」这类漂移。
     */
    public static boolean visible(boolean viewerIsInterceptor, boolean viewerHiding, boolean targetHiding) {
        if (!targetHiding) return true;      // 目标不在草丛：谁都看得见
        if (viewerHiding) return true;       // 双方都在草丛（或观察者也在草丛）：看得见
        return !viewerIsInterceptor;         // 只有「草丛外的截流者」看不见
    }

    /** 便捷重载：直接按两名玩家计算。 */
    public static boolean visible(Player viewer, Player target) {
        if (viewer == null || target == null) return true;
        if (viewer == target) return true;
        return visible(EchoHideRoles.isInterceptor(viewer), hiding(viewer), hiding(target));
    }

    /**
     * 客户端渲染用的裁剪判定。
     *
     * <p>只有「目标是回声对局玩家、观察者是回声截流者、观察者不在草丛、目标在草丛」时才隐藏。
     * 旁观者与创造模式不隐藏，方便管理员查图。
     */
    public static boolean hiddenFor(Player viewer, Player target) {
        if (viewer == null || target == null || viewer == target) return false;
        if (viewer.isSpectator() || viewer.isCreative()) return false;
        if (!EchoHideRoles.isEchoPlayer(target)) return false;
        return !visible(viewer, target);
    }
}
