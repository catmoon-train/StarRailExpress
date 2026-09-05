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
 * along with this program.  If you did not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.client.model;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.index.SREDoorBlocks;
import io.wifi.starrailexpress.index.TMMBlocks;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class ClosedSmallDoorModels {
    /**
     * 方块 id → TESR 贴图。OnLoad 返回真正的 {@link net.minecraft.client.renderer.block.model.BlockModel}
     * 以便图集收集贴图，AfterBake 再换成按 facing/half 的网格。
     */
    public static final Map<ResourceLocation, ResourceLocation> DOOR_TEXTURES = new LinkedHashMap<>();

    private ClosedSmallDoorModels() {
    }

    public static void register(SREClient.CustomModelProvider provider) {
        List<Block> doors = new ArrayList<>();
        for (SREDoorBlocks.CustomDoorBlockAndEntity entry : SREDoorBlocks.DOOR_BLOCK_AND_ENTITIES.values()) {
            registerDoor(provider, entry.block, entry.texture);
            doors.add(entry.block);
        }
        registerDoor(provider, TMMBlocks.SMALL_GLASS_DOOR, SRE.watheId("textures/entity/small_glass_door.png"));
        registerDoor(provider, TMMBlocks.SMALL_WOOD_DOOR, SRE.watheId("textures/entity/small_wood_door.png"));
        registerDoor(provider, TMMBlocks.ANTHRACITE_STEEL_DOOR, SRE.watheId("textures/entity/anthracite_steel_door.png"));
        registerDoor(provider, TMMBlocks.KHAKI_STEEL_DOOR, SRE.watheId("textures/entity/khaki_steel_door.png"));
        registerDoor(provider, TMMBlocks.MAROON_STEEL_DOOR, SRE.watheId("textures/entity/maroon_steel_door.png"));
        registerDoor(provider, TMMBlocks.MUNTZ_STEEL_DOOR, SRE.watheId("textures/entity/muntz_steel_door.png"));
        registerDoor(provider, TMMBlocks.NAVY_STEEL_DOOR, SRE.watheId("textures/entity/navy_steel_door.png"));
        registerDoor(provider, SREDoorBlocks.PLANE_GLASS_DOOR, SRE.watheId("textures/entity/small_glass_door.png"));
        registerDoor(provider, SREDoorBlocks.PLANE_WOOD_DOOR, SRE.watheId("textures/entity/small_wood_door.png"));
        registerDoor(provider, SREDoorBlocks.PLANE_STEEL_DOOR, SRE.id("textures/item/doors/up_steel_door.png"));
        registerDoor(provider, SREDoorBlocks.UP_GLASS_DOOR, SRE.watheId("textures/entity/small_glass_door.png"));
        registerDoor(provider, SREDoorBlocks.UP_WOOD_DOOR, SRE.watheId("textures/entity/small_wood_door.png"));
        registerDoor(provider, SREDoorBlocks.UP_STEEL_DOOR, SRE.id("textures/item/doors/up_steel_door.png"));

        doors.add(TMMBlocks.SMALL_GLASS_DOOR);
        doors.add(TMMBlocks.SMALL_WOOD_DOOR);
        doors.add(TMMBlocks.ANTHRACITE_STEEL_DOOR);
        doors.add(TMMBlocks.KHAKI_STEEL_DOOR);
        doors.add(TMMBlocks.MAROON_STEEL_DOOR);
        doors.add(TMMBlocks.MUNTZ_STEEL_DOOR);
        doors.add(TMMBlocks.NAVY_STEEL_DOOR);
        doors.add(SREDoorBlocks.PLANE_GLASS_DOOR);
        doors.add(SREDoorBlocks.PLANE_WOOD_DOOR);
        doors.add(SREDoorBlocks.PLANE_STEEL_DOOR);
        doors.add(SREDoorBlocks.UP_GLASS_DOOR);
        doors.add(SREDoorBlocks.UP_WOOD_DOOR);
        doors.add(SREDoorBlocks.UP_STEEL_DOOR);
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.cutout(), doors.toArray(Block[]::new));
    }

    public static final ResourceLocation DUMMY_MODEL_ID = SRE.id("block/closed_door_atlas_dummy");

    public static void addAtlasModels(ModelLoadingPlugin.Context ctx) {
        ctx.addModels(DUMMY_MODEL_ID);
    }

    public static UnbakedModel stitchForResource(ResourceLocation resourceId) {
        if (!DUMMY_MODEL_ID.equals(resourceId)) {
            return null;
        }
        return ClosedSmallDoorUnbakedModel.stitchModels(new LinkedHashSet<>(DOOR_TEXTURES.values()));
    }

    public static BakedModel wrapAfterBake(BakedModel baked, ModelResourceLocation topLevelId,
            Function<Material, TextureAtlasSprite> textureGetter) {
        if (baked == null || topLevelId == null || "inventory".equals(topLevelId.getVariant())) {
            return baked;
        }
        ResourceLocation tesrTexture = DOOR_TEXTURES.get(topLevelId.id());
        if (tesrTexture == null) {
            return baked;
        }
        TextureAtlasSprite sprite = textureGetter.apply(
                new Material(InventoryMenu.BLOCK_ATLAS, ClosedSmallDoorUnbakedModel.atlasId(tesrTexture)));
        BakedModel wrapped = ClosedSmallDoorUnbakedModel.bake(sprite);
        return wrapped != null ? wrapped : baked;
    }

    private static void registerDoor(SREClient.CustomModelProvider provider, Block block, ResourceLocation tesrTexture) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        DOOR_TEXTURES.put(blockId, tesrTexture);
        provider.register(block, ClosedSmallDoorUnbakedModel.stitchModel(tesrTexture));
    }
}
