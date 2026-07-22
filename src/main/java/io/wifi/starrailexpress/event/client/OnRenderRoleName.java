package io.wifi.starrailexpress.event.client;

import io.wifi.starrailexpress.content.entity.NoteEntity;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import io.wifi.starrailexpress.util.TrueFalseResult;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;

import java.util.Optional;

/**
 * 客户端渲染玩家角色名称及相关信息的事件容器。
 * <p>
 * 所有事件均在 {@link io.wifi.starrailexpress.client.gui.RoleNameRenderer#renderHud}
 * 中被触发，
 * 用于控制目标玩家名称、角色、修饰符、傀儡（PuppeteerBodyEntity）、笔记（NoteEntity）
 * 以及额外信息的渲染行为。监听器可通过返回不同的结果值来修改默认渲染逻辑。
 */
public class OnRenderRoleName {

    /**
     * 用于控制玩家修饰符（modifier）文本渲染的事件。
     * <p>
     * 该事件在玩家名称之后被调用，允许监听器决定是否显示当前目标玩家的修饰符（例如来自
     * {@link org.agmas.harpymodloader.modifiers.SREModifier} 的附加文本），
     * 或提供自定义的修饰符内容。
     *
     * <p>
     * <b>返回值含义：</b>
     * <ul>
     * <li>{@link TrueFalseAndCustomResult#disallow()}：不渲染任何修饰符文本。</li>
     * <li>{@link TrueFalseAndCustomResult#custom()}：使用自定义文本</li>
     * <li>{@link TrueFalseAndCustomResult#pass()}：使用默认逻辑（根据玩家是否处于旁观/创造模式以及实际拥有的修饰符列表生成）。</li>
     * </ul>
     *
     * @see RenderPlayerNameInterface
     */
    public static final Event<RenderPlayerNameInterface> RENDER_PLAYER_MODIFIER = EventFactory.createArrayBacked(
            RenderPlayerNameInterface.class,
            listeners -> (p, p1, c, t, r) -> {
                for (RenderPlayerNameInterface listener : listeners) {
                    var result = listener.allowRender(p, p1, c, t, r);
                    if (result != null && !result.isPass()) {
                        return result;
                    }
                }
                return TrueFalseAndCustomResult.pass();
            });

    /**
     * 用于控制玩家名称（nametag）渲染的事件。
     * <p>
     * 当玩家瞄准另一个玩家时，此事件决定该目标玩家的名称如何显示。
     *
     * <p>
     * <b>返回值含义：</b>
     * <ul>
     * <li>{@link TrueFalseAndCustomResult#disallow()}：完全不显示名称。</li>
     * <li>{@link TrueFalseAndCustomResult#custom()}：使用自定义文本</li>
     * <li>{@link TrueFalseAndCustomResult#pass()}：使用默认名称（即
     * {@link Player#getDisplayName()}）。</li>
     * </ul>
     *
     * @see RenderPlayerNameInterface
     */
    public static final Event<RenderPlayerNameInterface> RENDER_PLAYER_NAME = EventFactory.createArrayBacked(
            RenderPlayerNameInterface.class,
            listeners -> (p, p1, c, t, r) -> {
                for (RenderPlayerNameInterface listener : listeners) {
                    var result = listener.allowRender(p, p1, c, t, r);
                    if (result != null && !result.isPass()) {
                        return result;
                    }
                }
                return TrueFalseAndCustomResult.pass();
            });

    /**
     * 用于控制傀儡实体（{@link PuppeteerBodyEntity}）名称及提示信息渲染的事件。
     * <p>
     * 当玩家瞄准一个傀儡实体时，此事件决定是否渲染其相关信息（所有者名称和实体类型提示）。
     *
     * <p>
     * <b>返回值含义：</b>
     * <ul>
     * <li>{@link TrueFalseResult#FALSE}：不渲染任何信息。</li>
     * <li>{@link TrueFalseResult#PASS}：使用默认逻辑（仅当玩家处于旁观或创造模式时渲染）。</li>
     * <li>{@link TrueFalseResult#TRUE}：强制渲染（但当前实现中未使用该值，逻辑上与 PASS 相同）。</li>
     * </ul>
     *
     * @see RenderWithPuppeteerTargetInterface
     */
    public static final Event<RenderWithPuppeteerTargetInterface> RENDER_PUPPETEER = EventFactory.createArrayBacked(
            RenderWithPuppeteerTargetInterface.class,
            listeners -> (p, p1, c, t, r) -> {
                for (RenderWithPuppeteerTargetInterface listener : listeners) {
                    var result = listener.allowRender(p, p1, c, t, r);
                    if (result != null && !result.isPass()) {
                        return result;
                    }
                }
                return TrueFalseResult.PASS;
            });

    /**
     * 用于控制玩家“同伙”（cohort，即杀手队友）文本渲染的事件。
     * <p>
     * 当目标玩家是杀手且当前玩家也是杀手且能看到队友时，默认会显示“同伙”提示。
     * 此事件允许自定义该文本或禁止显示。
     *
     * <p>
     * <b>返回值含义：</b>
     * <ul>
     * <li>{@link TrueFalseAndCustomResult#disallow()}：不显示同伙文本。</li>
     * <li>{@link TrueFalseAndCustomResult#custom()}：使用使用自定义文本</li>
     * <li>{@link TrueFalseAndCustomResult#pass()}：使用默认同伙文本（"game.tip.cohort"）。</li>
     * </ul>
     *
     * @see RenderPlayerNameInterface
     */
    public static final Event<RenderPlayerNameInterface> RENDER_PLAYER_COHORT = EventFactory.createArrayBacked(
            RenderPlayerNameInterface.class,
            listeners -> (p, p1, c, t, r) -> {
                for (RenderPlayerNameInterface listener : listeners) {
                    var result = listener.allowRender(p, p1, c, t, r);
                    if (result != null && !result.isPass()) {
                        return result;
                    }
                }
                return TrueFalseAndCustomResult.pass();
            });

    /**
     * 用于控制玩家角色（role，如杀手）文本渲染的事件。
     * <p>
     * 当目标玩家具有角色且满足显示条件时，默认会显示角色名称。
     * 此事件允许自定义角色文本或禁止显示。
     *
     * <p>
     * <b>返回值含义：</b>
     * <ul>
     * <li>{@link TrueFalseAndCustomResult#disallow()}：不显示角色文本。</li>
     * <li>{@link TrueFalseAndCustomResult#custom()}：使用使用自定义文本</li>
     * <li>{@link TrueFalseAndCustomResult#pass()}：使用默认逻辑（当双方均为杀手且能看到队友时，显示杀手角色名称）。</li>
     * </ul>
     *
     * @see RenderPlayerNameInterface
     */
    public static final Event<RenderPlayerNameInterface> RENDER_PLAYER_ROLE = EventFactory.createArrayBacked(
            RenderPlayerNameInterface.class,
            listeners -> (p, p1, c, t, r) -> {
                for (RenderPlayerNameInterface listener : listeners) {
                    var result = listener.allowRender(p, p1, c, t, r);
                    if (result != null && !result.isPass()) {
                        return result;
                    }
                }
                return TrueFalseAndCustomResult.pass();
            });

    /**
     * 在渲染玩家信息之前触发的事件。
     * <p>
     * 此事件在所有玩家相关渲染开始前被调用，可用于绘制自定义背景或额外元素。
     * 监听器无需返回值，所有监听器均会被执行。
     *
     * @see RenderExtraInterface
     */
    public static final Event<RenderExtraInterface> RENDER_START = EventFactory.createArrayBacked(
            RenderExtraInterface.class,
            listeners -> (p, ra, c, t, r) -> {
                for (RenderExtraInterface listener : listeners) {
                    listener.render(p, ra, c, t, r);
                }
            });

    /**
     * 在渲染玩家信息之后触发的事件。
     * <p>
     * 此事件在所有玩家相关渲染完成后被调用，可用于绘制额外覆盖层或装饰。
     * 监听器无需返回值，所有监听器均会被执行。
     *
     * @see RenderExtraInterface
     */
    public static final Event<RenderExtraInterface> RENDER_END = EventFactory.createArrayBacked(
            RenderExtraInterface.class,
            listeners -> (p, ra, c, t, r) -> {
                for (RenderExtraInterface listener : listeners) {
                    listener.render(p, ra, c, t, r);
                }
            });

    /**
     * 用于渲染玩家额外信息（在角色名称下方）的事件。
     * <p>
     * 此事件允许在目标玩家的名称和角色文本下方添加自定义渲染内容。
     * 监听器无需返回值，所有监听器均会被执行。
     * 建议在渲染前开头先偏移：<pre>ctx.pose().translate(0, 12, 0);</pre>
     * 这将会新启一行避免与前面重复。<br/>
     * 建议在渲染结束时Y轴偏移到 {@code 下一行新的文本应当开始的Y轴位置 - 12}
     *
     * @see RenderPlayerExtraInterface
     */
    public static final Event<RenderPlayerExtraInterface> RENDER_PLAYER_EXTRA = EventFactory.createArrayBacked(
            RenderPlayerExtraInterface.class,
            listeners -> (p, p1, c, t, r) -> {
                for (RenderPlayerExtraInterface listener : listeners) {
                    listener.renderExtra(p, p1, c, t, r);
                }
            });

    /**
     * 用于控制笔记实体（{@link NoteEntity}）内容渲染的事件。
     * <p>
     * 当玩家瞄准一个笔记实体时，此事件决定是否显示笔记的四行文本。
     *
     * <p>
     * <b>返回值含义：</b>
     * <ul>
     * <li>{@link TrueFalseResult#FALSE}：不显示笔记内容。</li>
     * <li>{@link TrueFalseResult#PASS} 或 {@link TrueFalseResult#TRUE}：显示笔记内容（当前逻辑中
     * TRUE 与 PASS 等效）。</li>
     * </ul>
     *
     * @see RenderWithNoteTargetInterface
     */
    public static final Event<RenderWithNoteTargetInterface> RENDER_NOTE = EventFactory.createArrayBacked(
            RenderWithNoteTargetInterface.class,
            listeners -> (p, p1, c, t, r) -> {
                for (RenderWithNoteTargetInterface listener : listeners) {
                    var result = listener.allowRender(p, p1, c, t, r);
                    if (result != null && !result.equals(TrueFalseResult.PASS)) {
                        return result;
                    }
                }
                return TrueFalseResult.PASS;
            });

    /**
     * 用于控制玩家名称（nametag）是否渲染的总开关事件。
     * <p>
     * 此事件在决定是否渲染目标玩家名称之前被调用，优先于 {@link #RENDER_PLAYER_NAME}。
     * 如果返回 {@link TrueFalseResult#FALSE}，则直接跳过后续所有与目标玩家相关的渲染（包括名称、角色、修饰符等）。
     *
     * <p>
     * <b>返回值含义：</b>
     * <ul>
     * <li>{@link TrueFalseResult#FALSE}：完全禁止渲染目标玩家的任何信息。</li>
     * <li>{@link TrueFalseResult#PASS}：继续执行后续默认检查（例如
     * {@link io.wifi.starrailexpress.event.AllowNameRender}）。</li>
     * <li>{@link TrueFalseResult#TRUE}：强制渲染（当前逻辑与 PASS 相同，但通常表示允许）。</li>
     * </ul>
     *
     * @see RenderWithPlayerTargetInterface
     */
    public static final Event<RenderWithPlayerTargetInterface> RENDER_PLAYER = EventFactory
            .createArrayBacked(
                    RenderWithPlayerTargetInterface.class,
                    listeners -> (p, p1, c, t, r) -> {
                        for (RenderWithPlayerTargetInterface listener : listeners) {
                            var result = listener.allowRender(p, p1, c, t, r);
                            if (result != null && !result.equals(TrueFalseResult.PASS)) {
                                return result;
                            }
                        }
                        return TrueFalseResult.PASS;
                    });

    /**
     * 用于控制整个 HUD 渲染是否执行的事件。
     * <p>
     * 此事件在渲染任何玩家或实体信息之前被调用，提供全局开关。
     * 如果返回 {@link TrueFalseResult#FALSE}，则整个
     * {@link io.wifi.starrailexpress.client.gui.RoleNameRenderer#renderHud}
     * 过程将被跳过（除部分环境亮度检查外）。
     *
     * <p>
     * <b>返回值含义：</b>
     * <ul>
     * <li>{@link TrueFalseResult#FALSE}：跳过所有后续渲染。</li>
     * <li>{@link TrueFalseResult#PASS}：继续执行默认逻辑（还取决于光照条件）。</li>
     * <li>{@link TrueFalseResult#TRUE}：强制渲染（当前与 PASS 效果相同，但通常表示允许）。</li>
     * </ul>
     *
     * @see RenderHeadInterface
     */
    public static final Event<RenderHeadInterface> RENDER_ALL = EventFactory.createArrayBacked(
            RenderHeadInterface.class,
            listeners -> (p, c, t, r) -> {
                for (RenderHeadInterface listener : listeners) {
                    var result = listener.allowRender(p, c, t, r);
                    if (result != null && !result.equals(TrueFalseResult.PASS)) {
                        return result;
                    }
                }
                return TrueFalseResult.PASS;
            });

    /**
     * 用于动态修改玩家检测范围（distance）的事件。
     * <p>
     * 此事件允许监听器覆盖默认的视线检测范围（基于玩家手持物品或游戏模式），
     * 返回一个可选的浮点数作为新的渲染范围。
     *
     * <p>
     * <b>返回值含义：</b>
     * <ul>
     * <li>{@link Optional#empty()} 或返回 {@code null}：使用默认范围（由
     * {@link #getPlayerRange} 计算）。</li>
     * <li>非空 {@link Optional}：使用该值作为新的渲染范围。</li>
     * </ul>
     *
     * @see PlayerRangeInterface
     */
    public static final Event<PlayerRangeInterface> RENDER_RANGE = EventFactory.createArrayBacked(
            PlayerRangeInterface.class,
            listeners -> (player, original) -> {
                for (PlayerRangeInterface listener : listeners) {
                    var result = listener.getPlayerRange(player, original);
                    if (result != null && !result.isEmpty()) {
                        return result;
                    }
                }
                return Optional.of(original);
            });

    // --------------------------- 内部接口 ---------------------------

    /**
     * 用于获取玩家渲染范围的接口。
     * <p>
     * 实现此接口的监听器可以返回一个可选的浮点数，用于覆盖默认的检测距离。
     */
    public interface PlayerRangeInterface {
        /**
         * 获取给定玩家的渲染范围。
         *
         * @param player   当前玩家（观察者）
         * @param original 默认的计算范围
         * @return 一个包含新范围的 {@link Optional}，若为空则使用默认值
         */
        Optional<Float> getPlayerRange(Player player, float original);
    }

    /**
     * 用于控制整个 HUD 渲染开关的接口。
     * <p>
     * 实现此接口的监听器可以决定是否允许本次渲染流程。
     */
    public interface RenderHeadInterface {
        /**
         * 决定是否允许进行渲染。
         *
         * @param player      当前玩家
         * @param context     绘图上下文
         * @param tickCounter 增量时间计数器
         * @param renderer    字体渲染器
         * @return {@link TrueFalseResult#FALSE} 表示禁止渲染，其他值表示允许（或由后续逻辑决定）
         */
        TrueFalseResult allowRender(Player player, FakeGuiGraphics context,
                DeltaTracker tickCounter, Font renderer);
    }

    /**
     * 用于控制玩家目标是否渲染的接口（总开关）。
     * <p>
     * 实现此接口的监听器可以决定是否继续渲染目标玩家的信息（名称、角色等）。
     */
    public interface RenderWithPlayerTargetInterface {
        /**
         * 决定是否允许渲染目标玩家的信息。
         *
         * @param player      当前玩家（观察者）
         * @param target      被观察的目标玩家
         * @param context     绘图上下文
         * @param tickCounter 增量时间计数器
         * @param renderer    字体渲染器
         * @return {@link TrueFalseResult#FALSE} 表示禁止，{@link TrueFalseResult#PASS}
         *         表示由后续逻辑决定
         */
        TrueFalseResult allowRender(Player player, Player target, FakeGuiGraphics context,
                DeltaTracker tickCounter, Font renderer);
    }

    /**
     * 用于控制傀儡实体（{@link PuppeteerBodyEntity}）渲染的接口。
     */
    public interface RenderWithPuppeteerTargetInterface {
        /**
         * 决定是否允许渲染傀儡实体的信息。
         *
         * @param player      当前玩家（观察者）
         * @param target      被观察的傀儡实体
         * @param context     绘图上下文
         * @param tickCounter 增量时间计数器
         * @param renderer    字体渲染器
         * @return {@link TrueFalseResult#FALSE} 表示禁止，{@link TrueFalseResult#PASS}
         *         表示由默认逻辑决定
         */
        TrueFalseResult allowRender(Player player, PuppeteerBodyEntity target, FakeGuiGraphics context,
                DeltaTracker tickCounter, Font renderer);
    }

    /**
     * 用于控制笔记实体（{@link NoteEntity}）渲染的接口。
     */
    public interface RenderWithNoteTargetInterface {
        /**
         * 决定是否允许渲染笔记的内容。
         *
         * @param player      当前玩家（观察者）
         * @param targetNote  被观察的笔记实体
         * @param context     绘图上下文
         * @param tickCounter 增量时间计数器
         * @param renderer    字体渲染器
         * @return {@link TrueFalseResult#FALSE} 表示禁止，{@link TrueFalseResult#PASS}
         *         表示允许渲染
         */
        TrueFalseResult allowRender(Player player, NoteEntity targetNote, FakeGuiGraphics context,
                DeltaTracker tickCounter, Font renderer);
    }

    /**
     * 用于自定义玩家名称、角色、修饰符等文本的接口。
     * <p>
     * 实现此接口可返回自定义的 {@link Component} 或通过 {@link TrueFalseAndCustomResult} 控制是否显示。
     */
    public interface RenderPlayerNameInterface {
        /**
         * 决定如何渲染目标玩家的某一项文本（名称、角色、修饰符、同伙等）。
         *
         * @param player      当前玩家（观察者）
         * @param target      被观察的目标玩家
         * @param context     绘图上下文
         * @param tickCounter 增量时间计数器
         * @param renderer    字体渲染器
         * @return 一个 {@link TrueFalseAndCustomResult}，可表示禁止、使用默认或自定义文本
         */
        TrueFalseAndCustomResult<Component> allowRender(Player player, Player target, FakeGuiGraphics context,
                DeltaTracker tickCounter, Font renderer);
    }

    /**
     * 用于在渲染开始或结束阶段执行额外绘制操作的接口。
     * <p>
     * 监听器无需返回值，所有监听器均会被依次执行。
     */
    public interface RenderExtraInterface {
        /**
         * 执行额外的渲染操作。
         *
         * @param player      当前玩家（观察者）
         * @param range       当前的渲染范围
         * @param context     绘图上下文
         * @param tickCounter 增量时间计数器
         * @param renderer    字体渲染器
         */
        void render(Player player, float range, FakeGuiGraphics context,
                DeltaTracker tickCounter, Font renderer);
    }

    /**
     * 用于在目标玩家信息下方渲染额外内容的接口。
     * <p>
     * 此接口专门针对特定目标玩家进行额外绘制。
     */
    public interface RenderPlayerExtraInterface {
        /**
         * 在目标玩家的信息区域绘制额外内容。
         *
         * @param player      当前玩家（观察者）
         * @param target      被观察的目标玩家
         * @param context     绘图上下文
         * @param tickCounter 增量时间计数器
         * @param renderer    字体渲染器
         */
        void renderExtra(Player player, Player target, FakeGuiGraphics context,
                DeltaTracker tickCounter, Font renderer);
    }
}