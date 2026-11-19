package org.agmas.noellesroles.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 把钢卷模型登记成 Fabric extra model。
 *
 * <p>资源包里没有被任何物品 / 方块引用的模型不会进入 {@code ModelBakery}，也就不会被烘焙；
 * 登记之后就能在渲染时用 {@code ModelResourceLocation(id, "fabric_resource")} 取到 BakedModel。
 */
@Environment(EnvType.CLIENT)
public final class SteelCoilModelPlugin implements ModelLoadingPlugin {

    /** 对应 assets/noellesroles/models/entity/steel_coil.json */
    public static final ResourceLocation MODEL_ID = Noellesroles.id("entity/steel_coil");

    @Override
    public void onInitializeModelLoader(Context context) {
        context.addModels(MODEL_ID);
    }
}
