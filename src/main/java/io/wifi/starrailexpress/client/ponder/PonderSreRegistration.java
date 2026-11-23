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

package io.wifi.starrailexpress.client.ponder;

import com.nododiiiii.ponderer.runtime.api.ResourceSceneContents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 客户端初始化时把 {@link PonderSreExtension} 注册进 PondererLib。
 *
 * <p>注册必须在场景注册与编译之前完成，否则引用扩展的场景会被判为
 * 「缺少已编译扩展」并整体跳过。{@code ResourceSceneContents} 在资源重载时
 * 按清单 {@code extensions} 查找本注册，因此这里只需注册一次。
 *
 * <p>Ponderer 与 PondererLib 是本模组的必需前置，但为了在缺失前置时给出
 * 明确日志而不是崩溃，注册过程整体包在 try/catch 中。
 */
public final class PonderSreRegistration {

    private static final Logger LOGGER = LoggerFactory.getLogger(PonderSreRegistration.class);

    /** 资源重载完成后再烘焙 Flywheel 模型，避免模型管理器尚未就绪。 */
    private static final AtomicBoolean PARTIAL_MODELS_DIRTY = new AtomicBoolean(true);

    private static boolean registered;
    private static boolean partialModelFixWarned;

    private PonderSreRegistration() {
    }

    /** 注册思索扩展；重复调用只生效一次。 */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        try {
            ResourceSceneContents.registerExtension(PonderSreExtension.EXTENSION_ID,
                    PonderSreExtension::install);
            installFlywheelPartialModelFix();
            LOGGER.info("已向 PondererLib 注册思索扩展 {}", PonderSreExtension.EXTENSION_ID);
        } catch (Throwable error) {
            LOGGER.warn("注册 Ponderer 思索扩展失败，职业思索将只显示基础内容：{}", error.toString());
        }
    }

    /**
     * 某些整合包里 Flywheel 的 {@code flywheel:partial_models} 监听器会因依赖缺失被跳过，
     * PondererLib 的归属图标 {@code PartialModel} 因此一直没有烘焙模型；Ponder 界面渲染该图标时
     * 会把 null 模型交给 {@code GuiGameElement}，在 Sodium 下抛 wrapped 空指针。
     *
     * <p>这里在每次客户端资源重载后的下一个 tick 手动烘焙一遍。上游监听器正常时这一步只会重复
     * 写入相同模型，没有副作用；{@code populateOnInit} 置位后新创建的 PartialModel 也会自动烘焙。
     * 用反射调用是为了不引入对 Flywheel 的编译期硬依赖。
     */
    private static void installFlywheelPartialModelFix() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return ResourceLocation.fromNamespaceAndPath(
                                "starrailexpress", "ponderer_partial_models_fix");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager manager) {
                        PARTIAL_MODELS_DIRTY.set(true);
                    }
                });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!PARTIAL_MODELS_DIRTY.get() || client.getModelManager() == null) {
                return;
            }
            PARTIAL_MODELS_DIRTY.set(false);
            try {
                Class<?> handler = Class.forName(
                        "dev.engine_room.flywheel.lib.model.baked.PartialModelEventHandler");
                handler.getMethod("onBakingCompleted", ModelManager.class)
                        .invoke(null, client.getModelManager());
            } catch (Throwable error) {
                if (!partialModelFixWarned) {
                    partialModelFixWarned = true;
                    LOGGER.warn("手动烘焙 Flywheel PartialModel 失败，Ponder 归属图标可能不显示：{}",
                            error.toString());
                }
            }
        });
    }

    /** 资源重载或退出世界时清理皮肤覆盖登记，避免残留贴图。 */
    public static void clearCaches() {
        PonderSkinAccess.clear();
    }
}
