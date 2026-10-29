package org.agmas.noellesroles.game.halloween.village.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.game.halloween.village.VillageNpc;

/** 固定 64x64 本地皮肤和标准玩家 UV，模型包含双臂双腿与外层，不走 Skull/Profile 下载。 */
@Environment(EnvType.CLIENT)
public final class VillageNpcRenderer extends HumanoidMobRenderer<VillageNpc, PlayerModel<VillageNpc>> {
    private static final ResourceLocation SKIN = ResourceLocation.fromNamespaceAndPath(
            "starrailexpress", "textures/block/plush/canyuesama.png");
    public VillageNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), .35F);
    }
    @Override public ResourceLocation getTextureLocation(VillageNpc entity) { return SKIN; }
}
