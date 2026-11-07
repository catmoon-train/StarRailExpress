package io.wifi.starrailexpress.event.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.util.TrueFalseResult;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;

/** 纯客户端扩展时点；这里只定义调度规则，不包含职业、效果或活动判断。 */
@Environment(EnvType.CLIENT)
public final class ClientGameplayEvents {
    private ClientGameplayEvents() {
    }

    /** 本能 HEAD 查询，首个非 PASS 覆盖原逻辑；player 可能为空。 */
    public static final Event<InstinctOverride> INSTINCT_OVERRIDE = EventFactory.createArrayBacked(
            InstinctOverride.class, listeners -> player -> {
                for (InstinctOverride listener : listeners) {
                    TrueFalseResult result = listener.query(player);
                    if (!result.isPass()) return result;
                }
                return TrueFalseResult.PASS;
            });

    /** 时间原权限计算之后的修饰，按顺序传递结果，不受游戏运行状态额外限制。 */
    public static final Event<TimeVisibility> TIME_VISIBILITY = EventFactory.createArrayBacked(
            TimeVisibility.class, listeners -> (player, visible) -> {
                for (TimeVisibility listener : listeners) visible = listener.modify(player, visible);
                return visible;
            });

    /** 状态条方法入口；包括本体会提前返回的情况。 */
    public static final Event<StatusBarRender> STATUS_BAR_RENDER_HEAD = statusBarEvent();
    /** 状态条方法正常返回时触发；本体抛异常时不触发，与原 RETURN 注入一致。 */
    public static final Event<StatusBarRender> STATUS_BAR_RENDER_RETURN = statusBarEvent();

    private static Event<StatusBarRender> statusBarEvent() {
        return EventFactory.createArrayBacked(StatusBarRender.class, listeners -> (graphics, delta) -> {
            for (StatusBarRender listener : listeners) listener.render(graphics, delta);
        });
    }

    /** 心情渲染 HEAD 替换；首个 true 表示已绘制，跳过原渲染和后续监听器。 */
    public static final Event<MoodRender> KILLER_MOOD_RENDER = EventFactory.createArrayBacked(MoodRender.class,
            listeners -> (font, graphics, color, role) -> {
                for (MoodRender listener : listeners) {
                    if (listener.render(font, graphics, color, role)) return true;
                }
                return false;
            });

    /** 尸体旋转 HEAD 替换；首个 true 跳过原旋转。 */
    public static final Event<BodyRotations> BODY_ROTATIONS = EventFactory.createArrayBacked(BodyRotations.class,
            listeners -> (body, pose, animation, yaw, delta, scale) -> {
                for (BodyRotations listener : listeners) {
                    if (listener.apply(body, pose, animation, yaw, delta, scale)) return true;
                }
                return false;
            });

    /** 默认尸体缩放完成后通知，不改变调用处的矩阵栈层级。 */
    public static final Event<BodyScale> BODY_SCALE = EventFactory.createArrayBacked(BodyScale.class,
            listeners -> (body, pose, amount) -> {
                for (BodyScale listener : listeners) listener.apply(body, pose, amount);
            });

    /** 尸体身体绘制之后的 TAIL 钩子；true 只取消后续尾部回调，不能撤销绘制。 */
    public static final Event<BodyRenderTail> BODY_RENDER_TAIL = EventFactory.createArrayBacked(BodyRenderTail.class,
            listeners -> (body, yaw, delta, pose, buffers, light, alpha) -> {
                for (BodyRenderTail listener : listeners) {
                    if (listener.cancel(body, yaw, delta, pose, buffers, light, alpha)) return true;
                }
                return false;
            });

    /** 地图简介 start 入口，首个 false 取消；早于参数检查及已有卡片状态修改。 */
    public static final Event<MapIntro> ALLOW_MAP_INTRO = EventFactory.createArrayBacked(MapIntro.class,
            listeners -> id -> {
                for (MapIntro listener : listeners) {
                    if (!listener.allow(id)) return false;
                }
                return true;
            });

    /** 实体聚焦 start 入口，首个 false 取消；早于摄像机状态修改。 */
    public static final Event<EntityFocus> ALLOW_ENTITY_FOCUS = EventFactory.createArrayBacked(EntityFocus.class,
            listeners -> (entityId, duration, blackBars) -> {
                for (EntityFocus listener : listeners) {
                    if (!listener.allow(entityId, duration, blackBars)) return false;
                }
                return true;
            });

    /**
     * 屏幕 render 方法包装。先注册的监听器在外层；异常原样传播，监听器可用 finally 恢复状态。
     * 包装链只在注册时重建，不动态注册每帧监听器。
     */
    public static final Event<ScreenRender> SCREEN_RENDER = EventFactory.createArrayBacked(ScreenRender.class,
            listeners -> {
                ScreenRender chain = (screen, render) -> render.run();
                for (int i = listeners.length - 1; i >= 0; i--) {
                    ScreenRender listener = listeners[i];
                    ScreenRender next = chain;
                    chain = (screen, render) -> listener.render(screen, () -> next.render(screen, render));
                }
                return chain;
            });

    @FunctionalInterface
    public interface InstinctOverride {
        TrueFalseResult query(Player player);
    }

    @FunctionalInterface
    public interface TimeVisibility {
        boolean modify(Player player, boolean visible);
    }

    @FunctionalInterface
    public interface StatusBarRender {
        void render(GuiGraphics graphics, float delta);
    }

    @FunctionalInterface
    public interface MoodRender {
        boolean render(Font font, FakeGuiGraphics graphics, int color, SRERole role);
    }

    @FunctionalInterface
    public interface BodyRotations {
        boolean apply(PlayerBodyEntity body, PoseStack pose, float animation, float yaw, float delta, float scale);
    }

    @FunctionalInterface
    public interface BodyScale {
        void apply(PlayerBodyEntity body, PoseStack pose, float amount);
    }

    @FunctionalInterface
    public interface BodyRenderTail {
        boolean cancel(PlayerBodyEntity body, float yaw, float delta, PoseStack pose,
                MultiBufferSource buffers, int light, float alpha);
    }

    @FunctionalInterface
    public interface MapIntro {
        boolean allow(String id);
    }

    @FunctionalInterface
    public interface EntityFocus {
        boolean allow(int entityId, int duration, boolean blackBars);
    }

    @FunctionalInterface
    public interface ScreenRender {
        void render(Screen screen, Runnable original);
    }
}
