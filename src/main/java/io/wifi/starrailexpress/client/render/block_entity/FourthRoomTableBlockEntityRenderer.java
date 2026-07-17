package io.wifi.starrailexpress.client.render.block_entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.client.fourthroom.FourthRoomClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.modes.fourthroom.block.FourthRoomTableBlock;
import org.agmas.noellesroles.game.modes.fourthroom.block.FourthRoomTableBlockEntity;
import org.agmas.noellesroles.game.modes.fourthroom.effect.TableEffectEvents;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class FourthRoomTableBlockEntityRenderer implements BlockEntityRenderer<FourthRoomTableBlockEntity> {

    private final ItemRenderer itemRenderer;

    public FourthRoomTableBlockEntityRenderer(BlockEntityRendererProvider.@NotNull Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(@NotNull FourthRoomTableBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Direction facing = entity.getBlockState().hasProperty(FourthRoomTableBlock.FACING)
                ? entity.getBlockState().getValue(FourthRoomTableBlock.FACING)
                : Direction.NORTH;
        renderPile(entity, poseStack, bufferSource, packedOverlay, facing, TableEffectEvents.TableAnchor.DRAW_PILE,
                Math.max(1, (int) Math.ceil(entity.drawPileSize() / 4.0D)), "牌库 x" + entity.drawPileSize(), false);
        renderPile(entity, poseStack, bufferSource, packedOverlay, facing, TableEffectEvents.TableAnchor.DISCARD_PILE,
                Math.max(1, Math.min(6, entity.discardPileSize())), "弃牌堆 x" + entity.discardPileSize(), true);
        renderSeatStatus(entity, poseStack, bufferSource, facing, TableEffectEvents.TableAnchor.SLOT_A,
                entity.seatAPlayerName(), entity.seatAPlayerUuid(), entity.seatAAlive(), entity.seatAHiddenIdentityCount(),
                entity.seatAIdentitySlots());
        renderSeatStatus(entity, poseStack, bufferSource, facing, TableEffectEvents.TableAnchor.SLOT_B,
                entity.seatBPlayerName(), entity.seatBPlayerUuid(), entity.seatBAlive(), entity.seatBHiddenIdentityCount(),
                entity.seatBIdentitySlots());
        renderRecentCards(entity, poseStack, bufferSource, facing);
        renderAnimatedCards(entity, poseStack, bufferSource, facing, packedOverlay);
        renderPulses(entity, poseStack, bufferSource, facing);
        renderTableLabels(entity, poseStack, bufferSource);
    }

    private void renderPile(FourthRoomTableBlockEntity entity, PoseStack poseStack, MultiBufferSource bufferSource,
                            int packedOverlay, Direction facing, TableEffectEvents.TableAnchor anchor, int cards,
                            String label, boolean highlight) {
        Vec3 offset = anchor.localOffset(facing);
        int renderCards = Math.max(1, Math.min(cards, 6));
        for (int index = 0; index < renderCards; index++) {
            renderCard(entity, poseStack, bufferSource, 0.5D + offset.x, 0.935D + index * 0.011D,
                    0.5D + offset.z, 0.27F, highlight, 0.0F, packedOverlay);
        }
        renderBillboardText(poseStack, bufferSource, Component.literal(label), offset.x, 1.16D, offset.z,
                0xFFF1F1F1, 0.018F);
    }

    private void renderSeatStatus(FourthRoomTableBlockEntity entity, PoseStack poseStack, MultiBufferSource bufferSource,
                                  Direction facing, TableEffectEvents.TableAnchor anchor, String playerName,
                                  String playerUuid, boolean alive, int hiddenIdentities, List<String> identitySlots) {
        if (playerName.isBlank()) {
            return;
        }
        renderIdentityCards(entity, poseStack, bufferSource, facing, anchor, playerUuid, identitySlots, alive);
        Vec3 offset = anchor.localOffset(facing);
        int color = alive ? 0xFFF5ECD4 : 0xFF7F7F7F;
        if (!entity.activePlayerUuid().isBlank() && entity.activePlayerUuid().equals(playerUuid)) {
            color = 0xFFFFD66B;
        }
        renderBillboardText(poseStack, bufferSource, Component.literal(playerName), offset.x, 1.05D, offset.z, color,
                0.019F);
        String status = !alive ? "已出局" : hiddenIdentities > 0 ? ("暗置身份 x" + hiddenIdentities) : "身份已翻开";
        renderBillboardText(poseStack, bufferSource, Component.literal(status), offset.x, 0.96D, offset.z,
                alive ? 0xFFB6D4FF : 0xFF969696, 0.016F);
    }

    private void renderRecentCards(FourthRoomTableBlockEntity entity, PoseStack poseStack, MultiBufferSource bufferSource,
                                   Direction facing) {
        int count = entity.tableCards().size();
        for (int index = 0; index < count; index++) {
            FourthRoomTableBlockEntity.TableCardDisplay card = entity.tableCards().get(index);
            RandomSource random = RandomSource.create(card.timestamp());
            double localX = Mth.clamp((random.nextDouble() - 0.5D) * 1.10D + (index - (count - 1) / 2.0D) * 0.05D,
                    -0.68D, 0.68D);
            double localZ = Mth.clamp((random.nextDouble() - 0.5D) * 0.64D + 0.10D, -0.18D, 0.46D);
            Vec3 offset = tableOffset(facing, localX, localZ);
            float zRot = (random.nextFloat() - 0.5F) * 54.0F;
            renderCard(entity, poseStack, bufferSource, 0.5D + offset.x, 0.94D + index * 0.004D,
                    0.5D + offset.z, card.highlight() ? 0.24F : 0.22F, card.highlight(), zRot,
                    OverlayTexture.NO_OVERLAY);
            // Render card name above the card
            renderBillboardText(poseStack, bufferSource, Component.literal(card.titleText()), offset.x, 1.08D, offset.z,
                    card.accentColor(), 0.015F);
        }
    }

    private void renderAnimatedCards(FourthRoomTableBlockEntity entity, PoseStack poseStack,
                                     MultiBufferSource bufferSource, Direction facing, int packedOverlay) {
        long now = System.currentTimeMillis();
        for (FourthRoomTableBlockEntity.ActiveCardAnimation animation : entity.activeCardAnimations()) {
            float progress = animation.progress(now);
            Vec3 from = animation.from().localOffset(facing);
            Vec3 to = animation.to().localOffset(facing);
            double x = Mth.lerp(progress, from.x, to.x);
            double z = Mth.lerp(progress, from.z, to.z);
            double y = 0.97D + Math.sin(progress * Math.PI) * 0.26D;
            float scale = 0.24F + (float) Math.sin(progress * Math.PI) * 0.04F;
            renderCard(entity, poseStack, bufferSource, 0.5D + x, y, 0.5D + z, scale, animation.gold(),
                    Mth.lerp(progress, -8.0F, 8.0F), packedOverlay);
            if (!animation.label().isBlank()) {
                renderBillboardText(poseStack, bufferSource, Component.literal(animation.label()), x, y + 0.17D, z,
                        animation.color(), 0.015F);
            }
        }
    }

    private void renderPulses(FourthRoomTableBlockEntity entity, PoseStack poseStack, MultiBufferSource bufferSource,
                              Direction facing) {
        long now = System.currentTimeMillis();
        for (FourthRoomTableBlockEntity.AnchorPulseState pulse : entity.activePulses()) {
            float progress = pulse.progress(now);
            float alpha = 1.0F - progress;
            float scale = 0.12F + progress * pulse.intensity() * 0.30F;
            Vec3 offset = pulse.anchor().localOffset(facing);
            float red = ((pulse.color() >> 16) & 0xFF) / 255.0F;
            float green = ((pulse.color() >> 8) & 0xFF) / 255.0F;
            float blue = (pulse.color() & 0xFF) / 255.0F;
            AABB box = new AABB(0.5D + offset.x - scale, 0.935D, 0.5D + offset.z - scale,
                    0.5D + offset.x + scale, 0.99D, 0.5D + offset.z + scale);
            LevelRenderer.renderLineBox(poseStack, bufferSource.getBuffer(RenderType.lines()), box, red, green, blue,
                    alpha);
        }
    }

    private void renderTableLabels(FourthRoomTableBlockEntity entity, PoseStack poseStack,
                                   MultiBufferSource bufferSource) {
        FourthRoomTableBlockEntity.BannerState banner = entity.activeBanner();
        if (banner != null) {
            long now = System.currentTimeMillis();
            float alpha = banner.alpha(now);
            int textAlpha = Math.min(255, Math.max(0, (int) (alpha * 255.0F)));
            int color = (textAlpha << 24) | (banner.color() & 0x00FFFFFF);
            renderBillboardText(poseStack, bufferSource, Component.literal(banner.text()), 0.0D,
                    1.56D + (1.0D - alpha) * 0.08D, 0.0D, color, 0.022F + alpha * 0.004F);
        }
    }

    private void renderCard(FourthRoomTableBlockEntity entity, PoseStack poseStack, MultiBufferSource bufferSource,
                            double x, double y, double z, float scale, boolean highlighted, float zRotation,
                            int packedOverlay) {
                renderItemStack(entity, poseStack, bufferSource, createCardStack(highlighted), x, y, z, scale, zRotation,
                                packedOverlay);
        }

        private void renderIdentityCards(FourthRoomTableBlockEntity entity, PoseStack poseStack,
                                                                         MultiBufferSource bufferSource, Direction facing,
                                                                         TableEffectEvents.TableAnchor anchor, String playerUuid,
                                                                         List<String> publicIdentitySlots, boolean alive) {
                List<String> visibleIdentities = resolveIdentitySlots(entity, playerUuid, publicIdentitySlots);
                if (visibleIdentities.isEmpty()) {
                        return;
                }
                double mid = (visibleIdentities.size() - 1) / 2.0D;
                for (int index = 0; index < visibleIdentities.size(); index++) {
                        String blockId = visibleIdentities.get(index);
                        Vec3 offset = tableOffset(facing,
                                        anchor.localX() + (index - mid) * 0.28D,
                                        anchor.localZ() + 0.28D);
                        float rotation = (float) ((index - mid) * 7.0D + (alive ? 0.0D : -4.0D));
                        ItemStack stack = identityStack(blockId);
                        float scale = blockId.isBlank() ? 0.18F : 0.20F;
                        renderItemStack(entity, poseStack, bufferSource, stack, 0.5D + offset.x, 0.945D + index * 0.004D,
                                        0.5D + offset.z, scale, rotation, OverlayTexture.NO_OVERLAY);
                }
        }

        private List<String> resolveIdentitySlots(FourthRoomTableBlockEntity entity, String playerUuid,
                                                                                          List<String> publicIdentitySlots) {
                var snapshot = FourthRoomClientState.snapshot();
                if (snapshot.active()
                                && snapshot.viewer().roomId() == entity.linkedRoomId()
                                && snapshot.viewer().uuid().equals(playerUuid)
                                && !snapshot.viewer().identities().isEmpty()) {
                        List<String> localSlots = new ArrayList<>();
                        snapshot.viewer().identities().forEach(identity -> localSlots.add(identity.blockId()));
                        return localSlots;
                }
                return publicIdentitySlots;
        }

        private ItemStack identityStack(String blockId) {
                if (blockId == null || blockId.isBlank()) {
                        return createCardStack(false);
                }
                ResourceLocation id = ResourceLocation.tryParse(blockId);
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                        return createCardStack(true);
                }
                Item item = BuiltInRegistries.ITEM.get(id);
                if (item == Items.AIR) {
                        return createCardStack(true);
                }
                return new ItemStack(item);
        }

        private ItemStack createCardStack(boolean highlighted) {
                ItemStack stack = new ItemStack(Items.PAPER);
        if (highlighted) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
                return stack;
        }

        private void renderItemStack(FourthRoomTableBlockEntity entity, PoseStack poseStack, MultiBufferSource bufferSource,
                                                                 ItemStack stack, double x, double y, double z, float scale, float zRotation,
                                                                 int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(zRotation));
        poseStack.scale(scale, scale, scale);
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
                packedOverlay, poseStack, bufferSource, entity.getLevel(), 0);
        poseStack.popPose();
    }

    private void renderBillboardText(PoseStack poseStack, MultiBufferSource bufferSource, Component text, double x,
                                     double y, double z, int color, float scale) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        poseStack.pushPose();
        // try/finally 保证 popPose 一定执行：ModernUI 的 SDF 文字渲染在 drawInBatch 里可能抛
        // UnsupportedOperationException（fastutil 不可变表），未兜底会漏 pop → 帧末「Pose stack not empty」崩溃。
        try {
            poseStack.translate(0.5D + x, y, 0.5D + z);
            poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
            poseStack.scale(scale, -scale, scale);
            Matrix4f matrix = poseStack.last().pose();
            float xOffset = -font.width(text) / 2.0F;
            int background = (int) (minecraft.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
            font.drawInBatch(text, xOffset, 0.0F, 0x21000000, false, matrix, bufferSource, Font.DisplayMode.SEE_THROUGH,
                    background, LightTexture.FULL_BRIGHT);
            font.drawInBatch(text, xOffset, 0.0F, color, false, matrix, bufferSource, Font.DisplayMode.NORMAL, 0,
                    LightTexture.FULL_BRIGHT);
        } finally {
            poseStack.popPose();
        }
    }

        private Vec3 tableOffset(Direction facing, double localX, double localZ) {
                return switch (facing) {
                        case SOUTH -> new Vec3(-localX, 0.0D, localZ);
                        case EAST -> new Vec3(localZ, 0.0D, localX);
                        case WEST -> new Vec3(-localZ, 0.0D, -localX);
                        case NORTH -> new Vec3(localX, 0.0D, -localZ);
                        default -> new Vec3(localX, 0.0D, localZ);
                };
        }

    @Override
    public boolean shouldRenderOffScreen(@NotNull FourthRoomTableBlockEntity blockEntity) {
        return true;
    }
}