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
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public abstract class AnimatableBlockEntityRenderer<T extends BlockEntity> extends HierarchicalModel<Entity> implements BlockEntityRenderer<T> {

    /** Cached part list, avoids rebuilding {@code getAllParts()} every frame. */
    private List<ModelPart> cachedParts;

    /** Cached bone part lookup, avoids re-traversing the model tree every frame. */
    private final Map<String, ModelPart> boneParts = new HashMap<>();

    public AnimatableBlockEntityRenderer() {
        super();
    }

    public AnimatableBlockEntityRenderer(Function<ResourceLocation, RenderType> layerFactory) {
        super(layerFactory);
    }

    @Override
    public void render(T entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        this.setAngles(entity, this.getAge(entity) + tickDelta);
        this.renderPart(entity, tickDelta, matrices, vertexConsumers, light, overlay);
    }

    public void renderPart(T entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        this.root().render(matrices, vertexConsumers.getBuffer(this.renderType.apply(this.getTexture(entity, tickDelta))), light, overlay);
    }

    public int getAge(T entity) {
        return entity.getLevel() == null ? 0 : (int) entity.getLevel().getGameTime();
    }

    public abstract void setAngles(T entity, float animationProgress);

    public abstract ResourceLocation getTexture(T entity, float tickDelta);

    // ── Animation fast paths ───────────────────────────────────────────────────

    /**
     * All parts of the model, cached after first use. The model structure never
     * changes at runtime, so the list is rebuilt once instead of every frame.
     */
    protected final List<ModelPart> parts() {
        if (this.cachedParts == null) {
            List<ModelPart> list = new ArrayList<>();
            this.root().getAllParts().forEach(list::add);
            this.cachedParts = list;
        }
        return this.cachedParts;
    }

    /**
     * Advances the animation clock and reports whether the animation is still
     * producing motion. A door whose animation finished (or never started) is at
     * rest and its pose is the constant end pose — no keyframe interpolation is
     * needed.
     */
    protected final boolean isAnimationActive(AnimationState state, AnimationDefinition definition, float animationProgress) {
        state.updateTime(animationProgress, 1.0F);
        return state.isStarted()
                && state.getAccumulatedTime() < (long) (definition.lengthInSeconds() * 1000.0F);
    }

    /**
     * Applies the constant end pose of an animation definition directly, without
     * per-frame keyframe interpolation. Bone parts are cached so the model tree
     * is only traversed once.
     */
    protected final void applyFinalPose(AnimationDefinition definition) {
        for (Map.Entry<String, List<AnimationChannel>> entry : definition.boneAnimations().entrySet()) {
            ModelPart part = this.boneParts.computeIfAbsent(entry.getKey(),
                    name -> this.getAnyDescendantWithName(name).orElse(null));
            if (part == null) {
                continue;
            }
            for (AnimationChannel channel : entry.getValue()) {
                Keyframe[] keyframes = channel.keyframes();
                if (keyframes.length == 0) {
                    continue;
                }
                channel.target().apply(part, keyframes[keyframes.length - 1].target());
            }
        }
    }

    @Override
    public final void setupAnim(Entity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        throw new AssertionError();
    }

    @Override
    public final void prepareMobModel(Entity entity, float limbAngle, float limbDistance, float tickDelta) {
        throw new AssertionError();
    }
}
