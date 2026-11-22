package org.agmas.noellesroles.client.screen;

import com.nododiiiii.ponderer.application.api.RuntimeAccess;
import com.nododiiiii.ponderer.scene.DslScene;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.client.ponder.RolePonderIsolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 打开 Ponderer 的职业思索场景。
 * Ponderer 与 PondererLib 已经是本模组的前置，因此直接调用它们的公开 API。
 */
public final class RolePonderTutorial {
    private static final Logger LOGGER = LoggerFactory.getLogger(RolePonderTutorial.class);

    private static final String SCENE_PREFIX = "starrailexpress:role_tutorial.";

    private RolePonderTutorial() {
    }

    /** 打开职业对应的动态教程；找不到场景或 Ponderer 不可用时返回 false。 */
    public static boolean open(SRERole role) {
        if (role == null) {
            return false;
        }
        String wantedId = SCENE_PREFIX + role.identifier().getPath();
        try {
            List<DslScene> scenes = RuntimeAccess.scenes();
            DslScene scene = scenes.stream()
                    .filter(candidate -> wantedId.equals(candidate.id))
                    .findFirst()
                    .orElse(null);
            if (scene == null) {
                LOGGER.warn("未找到职业思索场景 {}，Ponderer 当前已加载 {} 个场景", wantedId, scenes.size());
                return false;
            }
            if (!RolePonderIsolation.open(scene, 0)) {
                LOGGER.warn("Ponderer 未能打开职业思索场景 {}", wantedId);
                return false;
            }
            return true;
        } catch (Throwable throwable) {
            LOGGER.warn("打开职业 {} 的思索场景失败：{}", role.identifier(), throwable.toString());
            return false;
        }
    }
}
