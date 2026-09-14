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

package io.wifi.starrailexpress.client.render.block_entity;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.client.render.block_entity.DisplayBlockRenderSupport.InterpolationState;
import io.wifi.starrailexpress.content.block_entity.DisplayBlockEntityBase;
import io.wifi.starrailexpress.content.block_entity.EntityDisplayBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 实体展示方块的渲染器：把 {@code entity} 键里的实体数据实例化成一只**只在客户端存在的实体**，
 * 然后交给 {@code EntityRenderDispatcher} 用该实体自己的渲染器画出来。
 *
 * <p>和另外三个展示方块不同，这里必须借助实体渲染器，所以有几件事要自己兜：
 * <ul>
 *   <li>实体实例按数据版本缓存（每帧重建既浪费又会把动画重置），存在方块实体的客户端槽位里。</li>
 *   <li>不调用 {@code tick()}（避免 AI/物理副作用），只推进 {@code tickCount} 让依赖它的空闲动画动起来；
 *       朝向统一归零，位姿由展示数据的变换控制。</li>
 *   <li>挡掉阴影（和原版背包里预览实体一样），否则方块上方会飘一个黑椭圆。</li>
 *   <li>实体渲染器来自第三方，实例化或渲染炸了就记下来跳过，不每帧重试、也不让整个渲染循环崩掉。</li>
 * </ul>
 */
public class EntityDisplayBlockEntityRenderer implements BlockEntityRenderer<EntityDisplayBlockEntity> {

    public EntityDisplayBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(EntityDisplayBlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }
        CompoundTag tag = blockEntity.getRawDisplayData();
        EntityPreviewCache cache = previewCacheOf(blockEntity);
        Entity entity = cache.resolve(blockEntity, level, tag);
        if (entity == null) {
            return;
        }

        InterpolationState state = DisplayBlockRenderSupport.stateFor(blockEntity);
        state.update(tag, blockEntity.getDataRevision(), level.getGameTime(), partialTick);

        // 站姿与朝向：脚踩方块底面；基础朝向取数据里的 Rotation，展示数据的变换再叠加
        float[] rotation = EntityDisplayBlockEntity.rotationOf(blockEntity.getEntityTag());
        float yaw = rotation[0];
        float entityPitch = rotation[1];
        entity.setYRot(yaw);
        entity.yRotO = yaw;
        entity.setXRot(entityPitch);
        entity.xRotO = entityPitch;
        if (entity instanceof LivingEntity living) {
            living.yHeadRot = yaw;
            living.yHeadRotO = yaw;
            living.yBodyRot = yaw;
            living.yBodyRotO = yaw;
        }
        entity.tickCount = (int) Math.floorMod(level.getGameTime(), 100000L);
        BlockPos pos = blockEntity.getBlockPos();
        entity.setPos(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        entity.setOldPosAndRot();

        int light = DisplayBlockRenderSupport.resolveLight(state.packedBrightnessOverride(), packedLight);

        poseStack.pushPose();
        DisplayBlockRenderSupport.moveToBlockCenter(poseStack);
        DisplayBlockRenderSupport.applyDisplayTransform(poseStack, state.billboard(), state.currentTransformation());
        poseStack.translate(0.0D, -0.5D, 0.0D);

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        try {
            dispatcher.render(entity, 0.0D, 0.0D, 0.0D, 0.0F, partialTick, poseStack, bufferSource, light);
        } catch (RuntimeException | StackOverflowError | LinkageError failure) {
            // 第三方实体渲染器不一定受得了"没有世界的假实体"，失败一次就永久跳过这个实体
            cache.markFailed();
        } finally {
            dispatcher.setRenderShadow(true);
            poseStack.popPose();
        }
    }

    @Override
    public boolean shouldRender(EntityDisplayBlockEntity blockEntity, Vec3 cameraPos) {
        return DisplayBlockRenderSupport.isWithinViewDistance(blockEntity.getBlockPos(), cameraPos,
                blockEntity.getRawDisplayData());
    }

    @Override
    public int getViewDistance() {
        return DisplayBlockEntityBase.MAX_RENDER_DISTANCE;
    }

    private static EntityPreviewCache previewCacheOf(EntityDisplayBlockEntity blockEntity) {
        Object existing = blockEntity.clientContentState();
        if (existing instanceof EntityPreviewCache cache) {
            return cache;
        }
        EntityPreviewCache created = new EntityPreviewCache();
        blockEntity.setClientContentState(created);
        return created;
    }

    /** 客户端侧的假实体缓存：按数据版本重建，失败后不再重试。 */
    private static final class EntityPreviewCache {

        private int revision = Integer.MIN_VALUE;
        private boolean failed;
        @Nullable
        private Entity entity;

        @Nullable
        Entity resolve(EntityDisplayBlockEntity blockEntity, Level level, CompoundTag tag) {
            int currentRevision = blockEntity.getDataRevision();
            if (currentRevision != this.revision) {
                this.revision = currentRevision;
                this.failed = false;
                this.entity = create(blockEntity, level);
            }
            return this.failed ? null : this.entity;
        }

        @Nullable
        private Entity create(EntityDisplayBlockEntity blockEntity, Level level) {
            CompoundTag entityTag = blockEntity.getEntityTag();
            if (entityTag.isEmpty()) {
                this.failed = true;
                return null;
            }
            try {
                Optional<Entity> created = EntityType.create(entityTag, level);
                if (created.isEmpty()) {
                    this.failed = true;
                    return null;
                }
                return created.get();
            } catch (RuntimeException | StackOverflowError | LinkageError failure) {
                this.failed = true;
                return null;
            }
        }

        void markFailed() {
            this.failed = true;
            this.entity = null;
        }
    }
}
