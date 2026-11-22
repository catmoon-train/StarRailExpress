package io.wifi.starrailexpress.client.ponder;

import com.nododiiiii.ponderer.application.api.RuntimeAccess;
import com.nododiiiii.ponderer.runtime.api.SceneCatalog;
import com.nododiiiii.ponderer.runtime.mixin.PonderUIAccessor;
import com.nododiiiii.ponderer.scene.DslScene;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 把职业思索从物品 Ponder 索引中隔离出来。
 *
 * <p>职业场景在 JSON 里仍然保留 items 作为文案演示用的载体，但注册到 Ponder 时改为挂在
 * 每个职业专属的虚拟组件上。这样物品不会触发职业思索，翻页也只会看到同一职业的分段。
 * 该类不能放进 mixin 包，否则会被注入代码当作 mixin 类加载。</p>
 */
public final class RolePonderIsolation {
    private static final Logger LOGGER = LoggerFactory.getLogger(RolePonderIsolation.class);

    /** 与 RolePonderTutorial.SCENE_PREFIX 保持一致。 */
    public static final String SCENE_PREFIX = "starrailexpress:role_tutorial.";

    /** 虚拟组件前缀，只存在于 Ponder 注册表，不对应任何真实物品。 */
    private static final String COMPONENT_PREFIX = "starrailexpress:role_ponder_";

    private static final ThreadLocal<List<String>> ORIGINAL_ITEMS = new ThreadLocal<>();

    private RolePonderIsolation() {
    }

    /** 是否是需要隔离的职业思索场景。 */
    public static boolean isRoleScene(DslScene scene) {
        return scene != null && scene.id != null && scene.id.startsWith(SCENE_PREFIX);
    }

    /** 该职业场景在 Ponder 注册表中使用的专属组件 id。 */
    public static String componentBinding(DslScene scene) {
        String suffix = scene.id.substring(SCENE_PREFIX.length()).replaceAll("[^a-z0-9_./-]", "_");
        return COMPONENT_PREFIX + suffix;
    }

    /** 是否为隔离用的虚拟组件绑定。 */
    public static boolean isSyntheticBinding(String binding) {
        return binding != null && binding.startsWith(COMPONENT_PREFIX);
    }

    /** 注册阶段临时替换场景载体，避免职业场景占用真实物品的 Ponder 索引。 */
    public static void detachForRegistration(DslScene scene) {
        if (!isRoleScene(scene)) {
            return;
        }
        ORIGINAL_ITEMS.set(scene.items);
        scene.items = List.of(componentBinding(scene));
    }

    /** 注册结束后还原快照，保证目录中的场景定义仍是原样。 */
    public static void restoreAfterRegistration(DslScene scene) {
        List<String> original = ORIGINAL_ITEMS.get();
        ORIGINAL_ITEMS.remove();
        if (original != null && isRoleScene(scene)) {
            scene.items = original;
        }
    }

    /**
     * 从专属组件打开职业思索。
     *
     * <p>与 Ponderer 的 {@code openPonderUIForScene} 一样使用官方 PonderUI 和分段定位，
     * 只是入口从物品栈换成虚拟组件 id，以便 UI 的场景列表只包含当前职业。</p>
     */
    public static boolean open(DslScene scene, int sceneIndex) {
        if (!isRoleScene(scene)) {
            return false;
        }
        ResourceLocation component = ResourceLocation.tryParse(componentBinding(scene));
        if (component == null) {
            LOGGER.warn("职业思索场景 {} 生成了无效的虚拟组件 id", scene.id);
            return false;
        }

        try {
            PonderUI ui = PonderUI.of(component);
            PonderUIAccessor accessor = (PonderUIAccessor) ui;
            List<PonderScene> ponderScenes = accessor.ponderer$getScenes();
            if (ponderScenes == null || ponderScenes.isEmpty()) {
                LOGGER.warn("职业思索场景 {} 没有已注册的 Ponder 分段", scene.id);
                return false;
            }

            int target = 0;
            for (int i = 0; i < ponderScenes.size(); i++) {
                SceneCatalog.SceneMatch match = RuntimeAccess.findBySceneId(ponderScenes.get(i).getId(), -1);
                if (match != null && match.scene() != null && scene.id.equals(match.scene().id)
                        && match.sceneIndex() == sceneIndex) {
                    target = i;
                    break;
                }
            }

            accessor.ponderer$setIndex(target);
            accessor.ponderer$getLazyIndex().startWithValue(target);
            ponderScenes.get(target).begin();
            Minecraft.getInstance().setScreen(ui);
            return true;
        } catch (Throwable error) {
            LOGGER.warn("打开职业思索场景 {} 失败：{}", scene.id, error.toString());
            return false;
        }
    }
}
