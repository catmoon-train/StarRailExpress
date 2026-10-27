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

package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.model.entity.CustomPlayerPlushModel;
import io.wifi.starrailexpress.client.util.ClientSkinCache;
import io.wifi.starrailexpress.client.util.SREClientUtils;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.content.block.CustomPlayerPlushBlock;
import org.agmas.noellesroles.content.block.SREPlushBlock;
import org.agmas.noellesroles.content.block_entity.SREPlushBlockEntity;
import org.jetbrains.annotations.Nullable;

public class SREPlushBlockEntityRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    private final BlockRenderDispatcher renderer;

    public SREPlushBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.renderer = ctx.getBlockRenderDispatcher();
    }

    @Override
    public void render(T blockEntity, float delta, PoseStack poseStack, MultiBufferSource consumer, int light,
            int overlay) {
        BlockState state = blockEntity.getBlockState();

        float squash = computeSquash(blockEntity, delta);
        if (state.getBlock() instanceof CustomPlayerPlushBlock) {
            renderCustomPlayerPlush(blockEntity, state, squash, poseStack, consumer, light, overlay);
            return;
        }
        renderBakedPlush(state, squash, poseStack, consumer, light, overlay);
    }

    /** 原版 plush：渲染烘焙方块模型，带挤压动画。 */
    private void renderBakedPlush(BlockState state, float squash, PoseStack poseStack, MultiBufferSource consumer,
            int light, int overlay) {
        poseStack.pushPose();
        poseStack.scale(1, 1F - squash, 1);
        poseStack.translate(0.5, 0, 0.5);
        poseStack.scale(1F + squash / 2F, 1, 1F + squash / 2F);
        poseStack.translate(-0.5, 0, -0.5);
        var bakedModel = this.renderer.getBlockModel(state);
        this.renderer.getModelRenderer().renderModel(poseStack.last(),
                consumer.getBuffer(ItemBlockRenderTypes.getRenderType(state, false)), state, bakedModel, 1F, 1F, 1F,
                light, overlay);
        poseStack.popPose();
    }

    /** 自定义玩家 plush：用绑定玩家的皮肤渲染 plush 形状模型。 */
    private void renderCustomPlayerPlush(T blockEntity, BlockState state, float squash, PoseStack poseStack,
            MultiBufferSource consumer, int light, int overlay) {
        RenderType renderType = null;
        if (blockEntity instanceof SREPlushBlockEntity plush) {
            if (plush.getCustomTexture() != null) {
                renderType = getRenderType(plush.getCustomTexture());
            } else {
                ResolvableProfile profile = plush.getOwnerProfile();
                renderType = getRenderType(profile);
            }
        }

        Direction facing = state.hasProperty(SREPlushBlock.FACING) ? state.getValue(SREPlushBlock.FACING)
                : Direction.NORTH;
        facing = facing.getOpposite();

        // 兜底：拿不到渲染类型时退回到半透明默认纹理，避免 getBuffer(null) 崩溃
        if (renderType == null) {
            renderType = RenderType.entityTranslucent(SRE.id("textures/entity/custom_psycho/th_sariel.png"));
        }

        poseStack.pushPose();
        // 挤压动画（绕方块底面，水平鼓起、竖直压扁）
        poseStack.scale(1, 1F - squash, 1);
        poseStack.translate(0.5, 0, 0.5);
        poseStack.scale(1F + squash / 2F, 1, 1F + squash / 2F);
        poseStack.translate(-0.5, 0, -0.5);
        // 实体模型 -> 方块空间：移到方块顶面中心并翻转 Y（ModelPart 内部已 ÷16）
        poseStack.translate(0.5, 1.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(-0.5, -1.5, -0.5);

        // poseStack.scale(1, -1, 1);

        CustomPlayerPlushModel.render(poseStack, consumer.getBuffer(renderType), light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private float computeSquash(T blockEntity, float delta) {
        double squish = blockEntity instanceof SREPlushBlockEntity plushie ? plushie.squash : 0;
        double lastSquish = squish * 3;
        return (float) Math.pow(1.0 - 1.0 / (1.0 + Mth.lerp((double) delta, lastSquish, squish)), 2.0);
    }

    public static RenderType getRenderType(@Nullable ResourceLocation res) {
        if (res == null)
            return null;
        return RenderType.entityTranslucent(res);
    }

    public static RenderType getRenderType(@Nullable ResolvableProfile resolvableProfile) {
        ResourceLocation resourceLocation = SRE.id("textures/entity/custom_psycho/th_sariel.png");
        if (resolvableProfile == null || resolvableProfile.gameProfile() == null) {
            return RenderType.entityCutoutNoCullZOffset(resourceLocation);
        }
        ResourceLocation liveSkin = findLiveSkin(resolvableProfile);
        if (liveSkin != null) {
            return RenderType.entityTranslucent(liveSkin);
        }
        SkinManager skinManager = Minecraft.getInstance().getSkinManager();
        PlayerSkin insecure = skinManager.getInsecureSkin(resolvableProfile.gameProfile());
        if (insecure != null && insecure.texture() != null) {
            return RenderType.entityTranslucent(insecure.texture());
        }
        return RenderType.entityCutoutNoCullZOffset(resourceLocation);
    }

    /**
     * 离线档案没有 textures，{@code SkinManager.getInsecureSkin} 会按 UUID 退回史蒂夫/艾利克斯。
     * 先对上在线/标签页里的目标玩家，用其真实皮肤。
     */
    @Nullable
    private static ResourceLocation findLiveSkin(ResolvableProfile profile) {
        Minecraft client = Minecraft.getInstance();
        UUID id = profile.id().orElse(null);
        if (id == null && profile.gameProfile() != null) {
            id = profile.gameProfile().getId();
        }
        String name = profile.name().orElse(null);
        if ((name == null || name.isBlank()) && profile.gameProfile() != null) {
            name = profile.gameProfile().getName();
        }
        name = stripSupporterIcon(name);

        PlayerInfo info = findPlayerInfo(client, id, name);
        ResourceLocation tabSkin = skinTexture(info == null ? null : info.getSkin());
        if (isCustomSkin(tabSkin)) {
            return tabSkin;
        }

        AbstractClientPlayer player = findLoadedPlayer(client, id, name);
        if (player != null) {
            ResourceLocation original = skinTexture(SREClientUtils.getPlayerOriginalSkin(player));
            if (isCustomSkin(original)) {
                return original;
            }
            // 标签页仍是默认皮肤时，用身体上实际显示的皮肤（换肤模组只改 getSkin 的情况）。
            ResourceLocation shown = skinTexture(player.getSkin());
            if (isCustomSkin(shown)) {
                return shown;
            }
        }
        // 只有默认皮肤时交给 getInsecureSkin，好让带 textures 的正版档案继续异步加载。
        return null;
    }

    @Nullable
    private static PlayerInfo findPlayerInfo(Minecraft client, @Nullable UUID id, @Nullable String name) {
        if (client.getConnection() != null) {
            if (isRealId(id)) {
                PlayerInfo byId = client.getConnection().getPlayerInfo(id);
                if (byId != null) {
                    return byId;
                }
            }
            if (name != null && !name.isBlank()) {
                PlayerInfo byName = client.getConnection().getPlayerInfo(name);
                if (byName != null) {
                    return byName;
                }
                for (PlayerInfo candidate : client.getConnection().getOnlinePlayers()) {
                    if (candidate.getProfile() != null && namesMatch(candidate.getProfile().getName(), name)) {
                        return candidate;
                    }
                }
            }
        }
        if (isRealId(id)) {
            PlayerInfo cached = ClientSkinCache.getCachedPlayerInfo(id);
            if (cached != null) {
                return cached;
            }
        }
        if (name != null && !name.isBlank()) {
            for (PlayerInfo cached : ClientSkinCache.PLAYER_ENTRIES_CACHE.values()) {
                if (cached.getProfile() != null && namesMatch(cached.getProfile().getName(), name)) {
                    return cached;
                }
            }
        }
        return null;
    }

    @Nullable
    private static AbstractClientPlayer findLoadedPlayer(Minecraft client, @Nullable UUID id, @Nullable String name) {
        if (client.level == null) {
            return null;
        }
        if (isRealId(id)) {
            var byId = client.level.getPlayerByUUID(id);
            if (byId instanceof AbstractClientPlayer player) {
                return player;
            }
        }
        if (name == null || name.isBlank()) {
            return null;
        }
        for (var loaded : client.level.players()) {
            if (loaded instanceof AbstractClientPlayer player && namesMatch(player.getGameProfile().getName(), name)) {
                return player;
            }
        }
        return null;
    }

    private static boolean isRealId(@Nullable UUID id) {
        return id != null && (id.getMostSignificantBits() != 0L || id.getLeastSignificantBits() != 0L);
    }

    private static boolean namesMatch(@Nullable String candidate, String wanted) {
        candidate = stripSupporterIcon(candidate);
        return candidate != null && candidate.equalsIgnoreCase(wanted);
    }

    @Nullable
    private static String stripSupporterIcon(@Nullable String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.charAt(name.length() - 1) == (char) 0xE780) {
            return name.substring(0, name.length() - 1);
        }
        return name;
    }

    @Nullable
    private static ResourceLocation skinTexture(@Nullable PlayerSkin skin) {
        return skin == null ? null : skin.texture();
    }

    private static boolean isCustomSkin(@Nullable ResourceLocation texture) {
        if (texture == null || !"minecraft".equals(texture.getNamespace())) {
            return texture != null;
        }
        String path = texture.getPath();
        return !path.equals("textures/entity/player/wide/steve.png")
                && !path.equals("textures/entity/player/slim/alex.png");
    }
}
