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

import com.mojang.authlib.GameProfile;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.AnimatedSceneElementBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 思索场景里的玩家模型：可指定皮肤贴图，并绘制本能透视边框。
 *
 * <p>皮肤覆盖通过 {@link PonderSkinAccess} 登记，并在
 * {@code PlayerEntityRendererMixin#getTextureLocation} 的最前面生效，
 * 因此优先于职业皮肤与疯魔皮肤逻辑，不会被它们改写。
 *
 * <p>边框使用思索场景自带的 {@link Outliner} 绘制 AABB 描边，
 * 颜色语义与游戏内本能透视完全一致。
 */
public class PonderPlayerElement extends AnimatedSceneElementBase {

    private final Vec3 location;
    private float bodyYaw = 180.0F;
    private float headYaw = 180.0F;
    private float pitch = 0.0F;

    @Nullable
    private GameProfile profile;
    @Nullable
    private RemotePlayer puppet;

    @Nullable
    private ResourceLocation skin;
    private int outlineColor = PonderPlayerPreset.NO_OUTLINE;
    private float outlineLineWidth = 1.0F;
    private float outlineExpand = 0.12F;

    /** 所属场景的 Outliner，在加入场景时绑定，用于绘制与保活描边。 */
    @Nullable
    private Outliner outliner;

    public PonderPlayerElement(Vec3 location) {
        this.location = location;
    }

    public PonderPlayerElement profile(@Nullable GameProfile profile) {
        this.profile = profile;
        return this;
    }

    /** 指定渲染用皮肤贴图；{@code null} 表示使用身份皮肤。 */
    public PonderPlayerElement skin(@Nullable ResourceLocation skin) {
        this.skin = skin;
        registerSkinOverride();
        return this;
    }

    /** 配置本能透视边框；{@code argb == NO_OUTLINE} 表示不绘制。 */
    public PonderPlayerElement outline(int argb, float lineWidth, float expand) {
        this.outlineColor = argb;
        this.outlineLineWidth = Math.max(0.1F, lineWidth);
        this.outlineExpand = Math.max(0.0F, expand);
        return this;
    }

    public PonderPlayerElement facing(float bodyYaw, float headYaw, float pitch) {
        this.bodyYaw = bodyYaw;
        this.headYaw = headYaw;
        this.pitch = pitch;
        applyFacingToPuppet();
        return this;
    }

    /** 绑定场景 Outliner；由 {@link PonderPlayerPreset} 在加入场景时调用。 */
    public PonderPlayerElement bindOutliner(@Nullable Outliner outliner) {
        this.outliner = outliner;
        return this;
    }

    /** 当前是否绘制边框。 */
    public boolean hasOutline() {
        return outlineColor != PonderPlayerPreset.NO_OUTLINE;
    }

    private void registerSkinOverride() {
        if (puppet != null && skin != null) {
            PonderSkinAccess.override(puppet, skin);
        }
    }

    private void applyFacingToPuppet() {
        if (puppet == null) {
            return;
        }
        puppet.setYRot(bodyYaw);
        puppet.yRotO = bodyYaw;
        puppet.setYBodyRot(bodyYaw);
        puppet.yBodyRotO = bodyYaw;
        puppet.yHeadRot = headYaw;
        puppet.yHeadRotO = headYaw;
        puppet.setXRot(pitch);
        puppet.xRotO = pitch;
    }

    private void ensurePuppet(PonderLevel world) {
        if (puppet != null) {
            return;
        }
        puppet = PonderPlayerFactory.createPlayer(world, profile);
        if (puppet == null) {
            return;
        }
        resetPuppetPose();
        applyFacingToPuppet();
        registerSkinOverride();
    }

    private void resetPuppetPose() {
        puppet.tickCount = 0;
        puppet.setPosRaw(0, 0, 0);
        puppet.xo = 0;
        puppet.yo = 0;
        puppet.zo = 0;
        puppet.xOld = 0;
        puppet.yOld = 0;
        puppet.zOld = 0;
        puppet.walkAnimation.setSpeed(0);
    }

    @Override
    public void reset(PonderScene scene) {
        super.reset(scene);
        if (puppet == null) {
            return;
        }
        resetPuppetPose();
        applyFacingToPuppet();
        registerSkinOverride();
    }

    @Override
    public void tick(PonderScene scene) {
        super.tick(scene);
        if (puppet == null) {
            return;
        }
        puppet.tickCount++;
        puppet.xo = puppet.getX();
        puppet.yo = puppet.getY();
        puppet.zo = puppet.getZ();
        puppet.xOld = puppet.getX();
        puppet.yOld = puppet.getY();
        puppet.zOld = puppet.getZ();
        puppet.yRotO = puppet.getYRot();
        puppet.xRotO = puppet.getXRot();
        puppet.yBodyRotO = puppet.yBodyRot;
        puppet.yHeadRotO = puppet.yHeadRot;
        puppet.oAttackAnim = puppet.attackAnim;
        puppet.walkAnimation.setSpeed(0);
        // Outliner 条目默认 1 tick 后开始淡出，必须每 tick 保活。
        if (outliner != null && hasOutline() && isVisible()) {
            outliner.keep(this);
        }
    }

    @Override
    protected void renderLast(PonderLevel world, MultiBufferSource buffer, GuiGraphics graphics, float fade, float pt) {
        ensurePuppet(world);
        if (puppet == null) {
            return;
        }

        var poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(location.x, location.y, location.z);
        poseStack.translate(Mth.lerp(pt, puppet.xo, puppet.getX()),
                Mth.lerp(pt, puppet.yo, puppet.getY()),
                Mth.lerp(pt, puppet.zo, puppet.getZ()));

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.render(puppet, 0, 0, 0, 0, pt, poseStack, buffer, lightCoordsFromFade(fade));
        poseStack.popPose();

        renderOutline(fade, pt);
    }

    /**
     * 用场景自带的 Outliner 画一圈描边，等价于游戏内本能透视的轮廓。
     * 颜色沿用 {@code InstinctManager} 的取值，因此与游戏内一致。
     */
    private void renderOutline(float fade, float pt) {
        if (!hasOutline() || outliner == null) {
            return;
        }
        double x = location.x + Mth.lerp(pt, puppet.xo, puppet.getX());
        double y = location.y + Mth.lerp(pt, puppet.yo, puppet.getY());
        double z = location.z + Mth.lerp(pt, puppet.zo, puppet.getZ());
        AABB box = new AABB(x, y, z, x, y, z)
                .inflate(outlineExpand)
                .setMaxY(y + puppet.getBbHeight() + outlineExpand);
        outliner.showAABB(this, box)
                .colored(withAlpha(outlineColor, fade))
                .lineWidth(outlineLineWidth);
    }

    /** InstinctManager 的颜色通常不带 alpha，这里补成不透明并按淡出缩放。 */
    private static int withAlpha(int argb, float fade) {
        int alpha = (argb >>> 24) & 0xFF;
        if (alpha == 0) {
            alpha = 255;
        }
        int scaled = Mth.clamp(Math.round(alpha * fade), 0, 255);
        return (scaled << 24) | (argb & 0xFFFFFF);
    }
}
