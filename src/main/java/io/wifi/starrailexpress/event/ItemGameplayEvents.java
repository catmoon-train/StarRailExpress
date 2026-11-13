package io.wifi.starrailexpress.event;

import io.wifi.starrailexpress.content.block_entity.PlateTrayBlockEntity;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

/** 物品与托盘的精确扩展时点；不依赖客户端类型，监听器在对应环境初始化时注册。 */
public final class ItemGameplayEvents {
    private ItemGameplayEvents() {
    }

    /** 职业丢弃判定之前查询，首个非 PASS 短路；双端调用，不修改物品栈。 */
    public static final Event<DropItem> BEFORE_ROLE_DROP = dropGate();

    static Event<DropItem> dropGate() {
        return EventFactory.createArrayBacked(DropItem.class, listeners -> (player, stack) -> {
            for (DropItem listener : listeners) {
                InteractionResult result = listener.beforeDrop(player, stack);
                if (result != InteractionResult.PASS) return result;
            }
            return InteractionResult.PASS;
        });
    }

    @FunctionalInterface
    public interface DropItem {
        InteractionResult beforeDrop(Player player, ItemStack stack);
    }

    /** 枪械开火前否决，在耗弹、音效和冷却之前调用，包含普通枪和狙击。 */
    public static final Event<WeaponAction> ALLOW_GUN_FIRE = weaponGate();
    /** 普通刀击前否决，在命中、副作用和冷却之前调用。 */
    public static final Event<WeaponAction> ALLOW_KNIFE_ATTACK = weaponGate();

    static Event<WeaponAction> weaponGate() {
        return EventFactory.createArrayBacked(WeaponAction.class, listeners -> (player, target) -> {
            for (WeaponAction listener : listeners) if (!listener.allow(player, target)) return false;
            return true;
        });
    }

    @FunctionalInterface
    public interface WeaponAction {
        boolean allow(ServerPlayer player, int targetId);
    }

    /** 物品 use 入口通知，不修改原返回值，也不切换调用线程或逻辑端。 */
    public static final Event<BeforeUse> BEFORE_USE = EventFactory.createArrayBacked(BeforeUse.class,
            listeners -> (item, level, player, hand) -> {
                for (BeforeUse listener : listeners) listener.onUse(item, level, player, hand);
            });

    /** 刀目标的 HEAD 替换；首个非 null 结果生效，null 继续原检测。 */
    public static final Event<TargetOverride> KNIFE_TARGET = EventFactory.createArrayBacked(TargetOverride.class,
            ItemGameplayEvents::targetInvoker);

    /** 左轮目标的 HEAD 替换；首个非 null 结果生效，不提前执行原检测。 */
    public static final Event<TargetOverride> REVOLVER_TARGET = EventFactory.createArrayBacked(TargetOverride.class,
            ItemGameplayEvents::targetInvoker);

    private static TargetOverride targetInvoker(TargetOverride[] listeners) {
        return player -> {
            for (TargetOverride listener : listeners) {
                HitResult result = listener.query(player);
                if (result != null) return result;
            }
            return null;
        };
    }

    /** 标准手枪目标的 RETURN 修饰；按注册顺序传递前一个监听器的结果。 */
    public static final Event<TargetModifier> STANDARD_REVOLVER_TARGET = EventFactory.createArrayBacked(
            TargetModifier.class, listeners -> (player, result) -> {
                for (TargetModifier listener : listeners) result = listener.modify(player, result);
                return result;
            });

    /** 托盘客户端 tick；首个 true 终止后续监听器，支持用 Fabric phase 保留原注入顺序。 */
    public static final Event<PlateTrayTick> PLATE_TRAY_CLIENT_TICK = EventFactory.createArrayBacked(
            PlateTrayTick.class, listeners -> (level, pos, state, tray) -> {
                for (PlateTrayTick listener : listeners) {
                    if (listener.tick(level, pos, state, tray)) return true;
                }
                return false;
            });

    /** 服务端刀击即将写入冷却，原参数已求值；未进入冷却分支时不触发。 */
    public static final Event<KnifeCooldown> BEFORE_KNIFE_COOLDOWN = EventFactory.createArrayBacked(
            KnifeCooldown.class, listeners -> (player, targetId) -> {
                for (KnifeCooldown listener : listeners) listener.beforeCooldown(player, targetId);
            });

    @FunctionalInterface
    public interface KnifeCooldown {
        void beforeCooldown(ServerPlayer player, int targetId);
    }
    @FunctionalInterface
    public interface BeforeUse {
        void onUse(Item item, Level level, Player player, InteractionHand hand);
    }

    @FunctionalInterface
    public interface TargetOverride {
        @Nullable HitResult query(Player player);
    }

    @FunctionalInterface
    public interface TargetModifier {
        HitResult modify(Player player, HitResult result);
    }

    @FunctionalInterface
    public interface PlateTrayTick {
        boolean tick(Level level, BlockPos pos, BlockState state, PlateTrayBlockEntity tray);
    }
}
