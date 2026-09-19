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

package io.wifi.starrailexpress.client.model;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.customitem.CustomItemData;
import io.wifi.starrailexpress.customitem.CustomItemLoader;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 让自定义列车物品「模型地址」指向的模型也能被烘焙。
 *
 * <p>
 * 资源包里<b>没有被任何物品 / 方块引用</b>的模型 json，默认不会被 {@code ModelBakery} 烘焙；
 * 这里在模型加载阶段把它们登记成 Fabric extra model（与 {@link GeneralModelLoadingPlugin}
 * 给皮肤模型做的事完全一样），之后就能用 {@code ModelResourceLocation.standalone(id)} 取到。
 *
 * <p>
 * 注意：模型烘焙只发生在资源加载阶段，所以编辑界面里改完「模型地址」后要重载一次资源（F3+T）
 * 才会生效；重载后 {@link io.wifi.starrailexpress.client.render.item.CustomItemRenderer}
 * 会拿缓存里的烘焙结果直接画。
 */
@Environment(EnvType.CLIENT)
public class CustomItemModelPlugin implements ModelLoadingPlugin {

    @Override
    public void onInitializeModelLoader(Context pluginContext) {
        Set<ResourceLocation> ids = collectModelIds();
        if (ids.isEmpty()) {
            return;
        }
        pluginContext.addModels(ids.toArray(new ResourceLocation[0]));
        SRE.LOGGER.info("[CustomItem] Registered {} extra item model(s)", ids.size());
    }

    /** 所有自定义物品里填了「模型地址」的模型 id（去重，保持配置顺序）。 */
    public static Set<ResourceLocation> collectModelIds() {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (CustomItemData data : CustomItemLoader.getAllData()) {
            if (data == null || data.modelPath == null || data.modelPath.isBlank()) {
                continue;
            }
            ResourceLocation id = CustomItemData.resolveModelId(data.modelPath);
            if (id != null) {
                ids.add(id);
            }
            for (String modelPath : data.animatedModelFramePaths()) {
                ResourceLocation frameId = CustomItemData.resolveModelId(modelPath);
                if (frameId != null) {
                    ids.add(frameId);
                }
            }
        }
        return ids;
    }
}
