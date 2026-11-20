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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static boolean registered;

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
            LOGGER.info("已向 PondererLib 注册思索扩展 {}", PonderSreExtension.EXTENSION_ID);
        } catch (Throwable error) {
            LOGGER.warn("注册 Ponderer 思索扩展失败，职业思索将只显示基础内容：{}", error.toString());
        }
    }

    /** 资源重载或退出世界时清理皮肤覆盖登记，避免残留贴图。 */
    public static void clearCaches() {
        PonderSkinAccess.clear();
    }
}
