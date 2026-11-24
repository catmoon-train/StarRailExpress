package io.wifi.starrailexpress.client.ponder;

import com.nododiiiii.ponderer.application.api.RuntimeAccess;
import com.nododiiiii.ponderer.runtime.api.CompilationResult;
import com.nododiiiii.ponderer.runtime.api.CompileRequest;
import com.nododiiiii.ponderer.runtime.api.PlaybackSession;
import com.nododiiiii.ponderer.runtime.api.SceneCatalog;
import com.nododiiiii.ponderer.runtime.mixin.PonderUIAccessor;
import com.nododiiiii.ponderer.scene.DslScene;
import com.nododiiiii.ponderer.scene.SceneSnapshot;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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
                LOGGER.info("虚拟组件 {} 没有注册到 Ponder 分段，改用直接编译打开 {}", component, scene.id);
                return openDirect(scene, sceneIndex);
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
            LOGGER.info("通过虚拟组件打开职业思索 {} 失败，改用直接编译：{}", scene.id, error.toString());
            return openDirect(scene, sceneIndex);
        }
    }

    /**
     * 不走 Ponder 注册表，直接把职业场景的每个分段编译成 PonderScene 后打开。
     *
     * <p>隔离后虚拟组件未必能被 Ponder 的场景索引查到，但场景定义本身仍然可用。
     * 这里用 Ponderer 的公开编译 API 重建分段，行为和 Ponderer 自己的草稿入口一致，
     * 只是在退出界面时关闭每一个编译产生的播放会话。</p>
     */
    private static boolean openDirect(DslScene scene, int sceneIndex) {
        try {
            List<DslScene.SceneSegment> segments = scene.scenes == null ? List.of() : scene.scenes;
            List<PonderScene> ponderScenes = new ArrayList<>();
            List<PlaybackSession> sessions = new ArrayList<>();
            int target = 0;
            for (int i = 0; i < segments.size(); i++) {
                DslScene.SceneSegment segment = segments.get(i);
                if (segment == null || segment.steps == null || segment.steps.isEmpty()) {
                    continue;
                }
                CompilationResult compiled = RuntimeAccess.engineFor(scene)
                        .compile(new CompileRequest(SceneSnapshot.of(scene), i));
                if (compiled == null || compiled.scene() == null) {
                    continue;
                }
                if (i == sceneIndex) {
                    target = ponderScenes.size();
                }
                ponderScenes.add(compiled.scene());
                if (compiled.playback() != null) {
                    sessions.add(compiled.playback());
                }
            }
            if (ponderScenes.isEmpty()) {
                LOGGER.warn("职业思索场景 {} 直接编译后没有任何分段", scene.id);
                return false;
            }

            RolePonderUI ui = new RolePonderUI(ponderScenes, sessions);
            PonderUIAccessor accessor = (PonderUIAccessor) ui;
            accessor.ponderer$setIndex(target);
            accessor.ponderer$getLazyIndex().startWithValue(target);
            ponderScenes.get(target).begin();
            Minecraft.getInstance().setScreen(ui);
            return true;
        } catch (Throwable error) {
            LOGGER.warn("直接编译打开职业思索场景 {} 失败：{}", scene.id, error.toString());
            return false;
        }
    }

    /** 持有编译产生的播放会话，关闭界面时一并释放。 */
    private static final class RolePonderUI extends PonderUI {
        private final List<PlaybackSession> sessions;

        private RolePonderUI(List<PonderScene> scenes, List<PlaybackSession> sessions) {
            super(scenes);
            this.sessions = sessions;
        }

        @Override
        public void removed() {
            try {
                super.removed();
            } finally {
                for (PlaybackSession session : sessions) {
                    try {
                        session.close();
                    } catch (Throwable ignored) {
                        // 会话释放失败不影响界面关闭
                    }
                }
            }
        }
    }
}
