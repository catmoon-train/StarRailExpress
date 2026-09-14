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

package io.wifi.starrailexpress.client.disguise;

import com.mojang.blaze3d.vertex.PoseStack;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.disguise.EntityDisguiseState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 把伪装中的玩家画成目标实体（通用版「皮革噶的的猪」）。
 * <p>
 * 每位伪装玩家持有一只**不入世界的客户端实体**，逐帧复制玩家的位置、朝向与动画状态后，
 * 交给该实体自己的渲染器绘制——因此不枚举实体类型：牛、羊、僵尸、物品实体、
 * 甚至其他模组的实体都走同一条路径。
 * <p>
 * <b>客户端开销</b>（三个分支，按常见程度排序）：
 * <ol>
 * <li>没人伪装（绝大多数帧）：一次 volatile 读就返回，无查表、无分配。</li>
 * <li>这名玩家没被伪装：一次哈希查找后返回。</li>
 * <li>正在画某个伪装玩家：一次哈希查找拿到临时实体 + 状态 + 已解析好的渲染器，
 * 然后逐帧拷贝玩家状态。**不做深比较**——状态按引用判断是否需要重建，所以每一帧都不会对
 * NBT 做递归等价判断；渲染器也只在重建时解析一次。</li>
 * </ol>
 * 临时实体只在「状态换了 / 换维度」时重建（每次状态变更最多一次），之后逐帧只做字段拷贝。
 * 读写全部发生在客户端主线程，所以用普通 {@link HashMap}，不上锁。
 * <p>
 * 注意：不调用 {@code tick()}——客户端跑实体 AI 没有意义，还会与逐帧的位置拷贝抢位置产生抖动；
 * 只推进 {@code tickCount} 与 {@code walkAnimation}。代价是少数依赖「客户端 tick 里算出来的辅助状态」
 * 的模型动画（如狼甩尾）会静止，纯外观问题。
 */
@Environment(EnvType.CLIENT)
public final class EntityDisguiseRenderer {

    /**
     * 一名伪装玩家的渲染件：临时实体 + 它是按哪份状态建的 + 解析好的渲染器。
     * 三者绑成一条记录，是为了让每帧只查一次表。
     *
     * @param state 同步包给出的状态对象本身，按**引用**比较（见 {@link #renderDisguised}）
     */
    private record Dummy(Entity entity, EntityDisguiseState state, EntityRenderer<? super Entity> renderer) {
    }

    private static final Map<UUID, Dummy> DUMMIES = new HashMap<>();

    private EntityDisguiseRenderer() {
    }

    /**
     * 渲染伪装模型。返回 {@code true} 表示已接管本帧渲染，调用方应取消玩家本体的渲染。
     */
    public static boolean render(AbstractClientPlayer player, float yaw, float tickDelta, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight) {
        if (ClientEntityDisguiseCache.isEmpty()) {
            // 没人伪装时走这里：一次 volatile 读。若还留着临时实体（服务端已清空、客户端还没收到），
            // 顺手收掉——收完这一次之后就不会再进这个分支了。
            if (!DUMMIES.isEmpty()) {
                prune();
            }
            return false;
        }
        return renderDisguised(player, yaw, tickDelta, poseStack, bufferSource, packedLight);
    }

    private static boolean renderDisguised(AbstractClientPlayer player, float yaw, float tickDelta,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        UUID uuid = player.getUUID();
        EntityDisguiseState state = ClientEntityDisguiseCache.get(uuid);
        if (state.isNone()) {
            DUMMIES.remove(uuid);
            return false;
        }
        Level level = player.level();
        Dummy dummy = DUMMIES.get(uuid);
        // 引用比较即可：状态对象由同步包直接给出，只有真的收到新状态、或玩家换了维度才需要重建。
        // 换成 equals 会每帧对 NBT 做一次递归比较，那是这条路径上唯一真正贵的操作。
        if (dummy == null || dummy.state() != state || dummy.entity().level() != level) {
            dummy = createDummy(uuid, level, state);
            if (dummy == null) {
                return false;
            }
        }
        Entity entity = dummy.entity();
        copyPlayerState(player, entity);
        poseStack.pushPose();
        dummy.renderer().render(entity, yaw, tickDelta, poseStack, bufferSource, packedLight);
        poseStack.popPose();
        return true;
    }

    /**
     * 第一人称手臂：把玩家自己的手臂换成目标实体的手臂。
     * <p>
     * 原版 {@code ItemInHandRenderer.renderPlayerArm} 会把手臂绘制交给
     * {@code PlayerRenderer#renderRightHand/renderLeftHand}（都是 public），并且**调用前已经把
     * 第一人称手臂该有的位姿推入 poseStack**——所以这里只需要换掉「画谁的手臂 + 用谁的贴图」，
     * 位置由原版保证，不需要自己写任何位姿数学。
     * <p>
     * 只对模型是 {@link HumanoidModel} 的实体生效（僵尸、骷髅、村民、灾厄、盔甲架以及大多模组人形）：
     * 只渲染那一个 {@code ModelPart}，不动模型的 visible 标志，因此不会影响世界里真实实体的渲染。
     * 非人形实体（牛、羊、物品实体……）没有手臂可画，直接返回 {@code true} 让原版手臂消失——
     * 伪装成牛却看到一只人手会更怪。
     *
     * @return {@code true} 表示已接管（调用方应取消原版的渲染），包括「这只实体的手臂不画」的情况
     */
    public static boolean renderFirstPersonHand(AbstractClientPlayer player, boolean rightHand, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight) {
        if (ClientEntityDisguiseCache.isEmpty()) {
            return false;
        }
        EntityDisguiseState state = ClientEntityDisguiseCache.get(player.getUUID());
        if (state.isNone()) {
            return false;
        }
        Level level = player.level();
        Dummy dummy = DUMMIES.get(player.getUUID());
        if (dummy == null || dummy.state() != state || dummy.entity().level() != level) {
            dummy = createDummy(player.getUUID(), level, state);
            if (dummy == null) {
                return false;
            }
        }
        Entity entity = dummy.entity();
        if (!(entity instanceof LivingEntity living)) {
            // 非生物（物品实体等）：不画手臂。
            return true;
        }
        if (!(dummy.renderer() instanceof LivingEntityRenderer<?, ?> livingRenderer)) {
            return true;
        }
        if (!(livingRenderer.getModel() instanceof HumanoidModel<?> humanoid)) {
            return true;
        }
        ModelPart arm = rightHand ? humanoid.rightArm : humanoid.leftArm;
        if (arm == null) {
            return true;
        }
        // 与 PlayerRenderer#renderHand 一致：先把模型摆成无动作状态，再把这条手臂摆平。
        // 模型是渲染器共享的实例，但这里只读它的骨骼、只写这一条手臂的旋转（原版也这么干），
        // 且只渲染这一个 ModelPart，所以不会把状态漏给世界里真实实体的渲染。
        setupArm(humanoid, living, arm);
        ResourceLocation texture = dummy.renderer().getTextureLocation(entity);
        arm.render(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture)), packedLight,
                OverlayTexture.NO_OVERLAY);
        return true;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void setupArm(HumanoidModel<?> humanoid, LivingEntity entity, ModelPart arm) {
        humanoid.attackTime = 0.0F;
        humanoid.crouching = false;
        humanoid.swimAmount = 0.0F;
        ((HumanoidModel) humanoid).setupAnim(entity, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        arm.xRot = 0.0F;
        arm.yRot = 0.0F;
        arm.zRot = 0.0F;
    }

    /** 丢弃已解除伪装者的临时实体。收到同步包 / 发现缓存空了时调一次即可。 */
    public static void prune() {
        if (DUMMIES.isEmpty()) {
            return;
        }
        // 迭代器直接删，不复制 keySet（原先每次同步都会多分配一个 HashSet）。
        for (Iterator<Map.Entry<UUID, Dummy>> iterator = DUMMIES.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<UUID, Dummy> entry = iterator.next();
            if (ClientEntityDisguiseCache.get(entry.getKey()).isNone()) {
                iterator.remove();
            }
        }
    }

    public static void clear() {
        DUMMIES.clear();
    }

    private static @Nullable Dummy createDummy(UUID uuid, Level level, EntityDisguiseState state) {
        Entity entity;
        try {
            entity = state.type() == null ? null : state.type().create(level);
            if (entity == null) {
                return null;
            }
            if (state.nbt() != null) {
                entity.load(state.nbt());
            }
            // 名字要留着：jeb_ 绵羊（彩虹毛）、Toast 兔子、Dinnerbone / Grumm（倒过来）这些原版外观效果
            // 都是读名字触发的；但把可见标记按掉，免得凭空多出玩家名牌。
            entity.setCustomNameVisible(false);
        } catch (Throwable throwable) {
            SRE.LOGGER.warn("EntityDisguise 创建渲染用临时实体失败: {}", state, throwable);
            return null;
        }
        EntityRenderer<? super Entity> renderer = Minecraft.getInstance().getEntityRenderDispatcher()
                .getRenderer(entity);
        if (renderer == null) {
            return null;
        }
        Dummy dummy = new Dummy(entity, state, renderer);
        DUMMIES.put(uuid, dummy);
        return dummy;
    }

    private static void copyPlayerState(AbstractClientPlayer player, Entity dummy) {
        // 位置没变就跳过 setPos：Entity#setPos 每次都会重算一遍 AABB（一次分配 + 边界盒写入），
        // 站着不动时那是纯浪费。xo/yo/zo 仍然逐帧抄，渲染插值不受影响。
        if (dummy.getX() != player.getX() || dummy.getY() != player.getY() || dummy.getZ() != player.getZ()) {
            dummy.setPos(player.getX(), player.getY(), player.getZ());
        }
        dummy.xo = player.xo;
        dummy.yo = player.yo;
        dummy.zo = player.zo;
        dummy.setYRot(player.getYRot());
        dummy.yRotO = player.yRotO;
        dummy.setXRot(player.getXRot());
        dummy.xRotO = player.xRotO;
        dummy.setInvisible(player.isInvisible());
        if (dummy instanceof LivingEntity living) {
            living.hurtTime = player.hurtTime;
            living.yBodyRot = player.yBodyRot;
            living.yBodyRotO = player.yBodyRotO;
            living.yHeadRot = player.yHeadRot;
            living.yHeadRotO = player.yHeadRotO;
            if (living.tickCount != player.tickCount) {
                // 行走动画每 tick 只推进一次，其余状态逐帧复制。
                living.walkAnimation.update(player.walkAnimation.speed(), 1.0F);
                living.tickCount = player.tickCount;
            }
        } else {
            dummy.tickCount = player.tickCount;
        }
    }
}
