package org.agmas.noellesroles.utils;

import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import org.agmas.noellesroles.content.entity.DoomedSinnerBodyEntity;
import org.agmas.noellesroles.content.entity.SaltedFishBodyEntity;
import org.agmas.noellesroles.role_data.killer.InsaneKillerRoleData;
import org.jetbrains.annotations.Nullable;

/**
 * 「伪装尸体」判定：某些职业会以一具「假尸体」示人（本人仍存活/仍可行动），
 * 这类尸体不代表真实的死亡，因此不能被用于召开紧急会议。
 *
 * <p>覆盖范围：
 * <ul>
 * <li>咸鱼「晒咸鱼」——服务端生成的 {@link SaltedFishBodyEntity} 假尸体；</li>
 * <li>宿命的罪人——服务端生成的 {@link DoomedSinnerBodyEntity} 尸体，
 *     本人每次死亡后会复活，该尸体只是死亡留下的残骸，15 秒后消失；</li>
 * <li>亡语杀手「假扮尸体」——由客户端渲染 Mixin 生成的假尸体，
 *     本体仍在场且处于假扮状态时登记在 {@link InsaneKillerRoleData#isPlayerBodyEntity}。</li>
 * </ul>
 *
 * <p>刻意<b>不</b>包含葬仪等职业的「伪造尸体」：那些尸体是真实的死亡结果，
 * 依旧可以被验尸、报告与召开会议。
 */
public final class DisguisedBodyUtils {

    private DisguisedBodyUtils() {
    }

    /**
     * 该尸体是否为「不可报告」的伪装尸体。
     *
     * <p>客户端与服务端均可调用：亡语杀手的登记表只在客户端有值，
     * 服务端侧该尸体本身也不存在，双端判定互不干扰。
     */
    public static boolean isUnreportableDisguisedBody(@Nullable PlayerBodyEntity body) {
        if (body == null) {
            return false;
        }
        // 咸鱼「晒咸鱼」的假尸体
        if (body instanceof SaltedFishBodyEntity) {
            return true;
        }
        // 宿命的罪人死亡后留下的尸体（本人会复活，尸体 15 秒后消失）
        if (body instanceof DoomedSinnerBodyEntity) {
            return true;
        }
        // 亡语杀手「假扮尸体」：本体仍在场且正处于假扮状态
        var owner = body.getPlayerUuid();
        return owner != null && InsaneKillerRoleData.isPlayerBodyEntity.getOrDefault(owner, false);
    }
}
