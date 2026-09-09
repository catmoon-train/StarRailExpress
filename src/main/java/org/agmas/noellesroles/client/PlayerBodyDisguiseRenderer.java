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

package org.agmas.noellesroles.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.exmo.sre.meeting.MeetingManager;
import net.exmo.sre.meeting.client.MeetingClientHandler;
import org.agmas.noellesroles.game.roles.innocence.fool.TarotAssemblyManager;

/**
 * 假尸体伪装渲染器：把玩家渲染成尸体。
 *
 * <p>与 {@link LeatherPigDisguiseRenderer} 同一套路——每个伪装玩家持有一只「不入世界」的客户端尸体实体，
 * 逐帧复制玩家位置与姿态后交给尸体渲染器绘制。
 *
 * <p>刻意不把尸体加入世界：{@link PlayerBodyEntity#tick()} 会 discard 会议区/游记放逐区内的尸体，
 * 换图清场也会清掉它，而尸体一旦被清除就不会再生成（咸鱼、亡语杀手的伪装会永久消失）。
 * 纯渲染的伪装不受任何世界清理影响，进会议、用游记传送后回来都照常显示。
 *
 * <p>会议厅内不渲染假尸体（见 {@link TarotAssemblyManager#isInMeetingArea}），此时本体按正常玩家渲染。
 */
public class PlayerBodyDisguiseRenderer {
    /**
     * 尸体渲染后其中心相对锚点沿朝向的偏移量（实测值）。
     * 渲染时反向补偿，让尸体落在玩家判定盒里。
     */
    private static final double CORPSE_CENTER_OFFSET = 0.53;

    private static final Map<UUID, DisguisedBody> BODIES = new HashMap<>();

    /** 缓存条目：尸体实体 + 伪装开始时的本体 tick，用来给尸体一个「死亡时长」。 */
    private static final class DisguisedBody {
        final PlayerBodyEntity body;
        final int startTick;

        DisguisedBody(PlayerBodyEntity body, int startTick) {
            this.body = body;
            this.startTick = startTick;
        }
    }

    /**
     * 渲染某玩家的假尸体。
     *
     * @param forgedMarker 是否标记为「伪造的尸体」——验尸官（能看到死因的职业）一眼就能看穿
     * @return 是否成功绘制（调用方据此决定是否取消本体渲染）
     */
    public static boolean render(AbstractClientPlayer player, EntityType<? extends PlayerBodyEntity> bodyType,
            float yaw, float bodyYaw, float headYaw, float tickDelta, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, boolean forgedMarker) {
        // 塔罗会/紧急会议期间不允许出现尸体：伪装停用，本体按正常玩家渲染
        if (isDisguiseSuspended(player)) {
            return false;
        }
        DisguisedBody disguised = getBody(player, bodyType, forgedMarker);
        if (disguised == null) {
            return false;
        }
        // 尸体渲染器在未进入对局时会直接跳过绘制，此时谎报成功会让本体被隐藏成透明人
        if (SREClient.moodComponent == null) {
            return false;
        }
        PlayerBodyEntity body = disguised.body;
        // 逐帧复制本体位置与姿态（含插值用的旧坐标），尸体因此始终贴在本体身上。
        //
        // 但尸体渲染器会把模型转成躺姿，尸体中心相对锚点沿朝向偏移约 0.53 格（实测：
        // 锚点+bodyYaw 方向的 0.53 格处才是尸体中心）。这里反向补偿掉这个偏移，
        // 让尸体正好躺在玩家身上——也就是躺在玩家的判定盒里，
        // 这样客户端准星（client.hitResult）才会像打真尸体一样直接命中，不需要特殊判定。
        double corpseOffset = Math.toRadians(bodyYaw);
        double offsetX = -Math.sin(corpseOffset) * CORPSE_CENTER_OFFSET;
        double offsetZ = Math.cos(corpseOffset) * CORPSE_CENTER_OFFSET;
        body.setPos(player.getX() + offsetX, player.getY(), player.getZ() + offsetZ);
        body.xo = body.getX();
        body.yo = body.getY();
        body.zo = body.getZ();
        body.setYRot(bodyYaw);
        body.yRotO = bodyYaw;
        body.setYBodyRot(bodyYaw);
        body.yBodyRotO = bodyYaw;
        body.setYHeadRot(headYaw);
        body.yHeadRotO = headYaw;
        body.setXRot(0.0F);
        body.xRotO = 0.0F;
        body.setDeltaMovement(0.0, 0.0, 0.0);
        // 让尸体的「死亡时长」随伪装时间增长（验尸 HUD 会读 tickCount）。
        // 咸鱼最长 80s、亡语杀手 60s，都远小于开始腐化的 1 分钟阈值，外观不受影响。
        body.tickCount = Math.max(0, player.tickCount - disguised.startTick);

        EntityRenderer<? super PlayerBodyEntity> renderer = Minecraft.getInstance()
                .getEntityRenderDispatcher().getRenderer(body);
        if (renderer == null) {
            return false;
        }
        renderer.render(body, yaw, tickDelta, poseStack, bufferSource, packedLight);
        return true;
    }

    /**
     * 取某玩家当前缓存中的假尸体（没有伪装时返回 null）。
     * 供 HUD 在准星命中伪装玩家时，按真尸体的方式显示验尸面板。
     */
    public static PlayerBodyEntity getCachedBody(Player player) {
        // 与渲染保持一致：伪装停用时 HUD 也不该显示尸体信息
        if (isDisguiseSuspended(player)) {
            return null;
        }
        DisguisedBody disguised = BODIES.get(player.getUUID());
        return disguised == null ? null : disguised.body;
    }

    /**
     * 假尸体伪装是否应当停用。
     *
     * <ul>
     * <li>塔罗会会议厅（愚者会议）：厅内不允许出现尸体；</li>
     * <li>紧急会议（{@link MeetingManager}）：会议期间参会者被集中到会议桌，同样不显示尸体。</li>
     * </ul>
     */
    private static boolean isDisguiseSuspended(Player player) {
        if (TarotAssemblyManager.isInMeetingArea(player.getX(), player.getZ())) {
            return true;
        }
        return MeetingClientHandler.phase != MeetingManager.PHASE_NONE
                && MeetingClientHandler.participants.contains(player.getUUID());
    }

    private static DisguisedBody getBody(AbstractClientPlayer player,
            EntityType<? extends PlayerBodyEntity> bodyType, boolean forgedMarker) {
        DisguisedBody disguised = BODIES.get(player.getUUID());
        if (disguised == null || disguised.body.level() != player.level()) {
            PlayerBodyEntity body = bodyType.create(player.level());
            if (body == null) {
                return null;
            }
            body.setPlayerUuid(player.getUUID());
            fillCorpseData(body, player, forgedMarker);
            disguised = new DisguisedBody(body, player.tickCount);
            BODIES.put(player.getUUID(), disguised);
        }
        return disguised;
    }

    /**
     * 把假尸体填成和真尸体一样的数据。
     *
     * <p>{@code forgedMarker} 为真时额外打上「伪造的尸体」标记（验尸官可察觉）；
     * 为假时验尸 HUD 的显示与真尸体完全一致。
     */
    private static void fillCorpseData(PlayerBodyEntity body, AbstractClientPlayer player, boolean forgedMarker) {
        PlayerBodyEntityComponent cca = body.getComponent();
        // 伪造标记：验尸官看这具尸体时会显示「伪造的尸体」而不是死因/身份
        cca.isFakeBody = forgedMarker;
        cca.setOwnerName(player.getScoreboardName(), false);
        cca.setDeathReason(GameConstants.DeathReasons.GENERIC.toString(), false);
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld != null) {
            SRERole role = gameWorld.getRole(player);
            if (role != null) {
                cca.playerRole = role.identifier();
            }
        }
    }

    /** 伪装结束时丢弃缓存的客户端尸体（实体从未入世界，无需 removeEntity）。 */
    public static void discard(UUID playerId) {
        BODIES.remove(playerId);
    }
}
