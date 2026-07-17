package net.exmo.sre.sixtyseconds.content.block;

import net.exmo.sre.sixtyseconds.SixtySecondsPhase;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsDoorMenu;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 避难所的门（<b>通用交互中枢</b>）：右键打开统一门菜单 {@link SixtySecondsDoorMenu}，
 * 由服务端按玩家上下文给出操作选项（存物资 / 外出探索 / 返回住所 / 门外事件 / 拜访别队）。
 * 同一扇门对所有玩家可用——搜索区里的门、别队门口的门都走同一套菜单，各自拿到各自的选项。
 * <p>快捷路径（不开菜单）：
 * <ul>
 *   <li>手持区域绑定工具：选中此门为「待绑定门」（管理员搭图）。</li>
 *   <li>游戏日手持木板/铁锭/修理包：直接加固家门（{@code SixtySecondsDefenseSystem}）。</li>
 *   <li>准备阶段<b>潜行</b>右键：快速存入物资（60 秒抢时间用，免开菜单）。</li>
 * </ul>
 * 旧的 {@link DoorPurpose} 分工已废弃；{@link #PURPOSE} 属性仅为旧存档兼容保留，逻辑不再读取。
 */
public class ShelterDoorBlock extends Block {
    /** @deprecated 门已通用化，属性仅为旧存档 blockstate 兼容保留。 */
    @Deprecated
    public static final EnumProperty<DoorPurpose> PURPOSE = EnumProperty.create("purpose", DoorPurpose.class);
    /** 门板朝向（模型为复刻列车钢门的平面门板，需随门洞方向旋转；旧存档缺省=north）。 */
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

    public ShelterDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PURPOSE, DoorPurpose.SEARCH)
                .setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PURPOSE, FACING);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        useDoor(level, pos, player);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        useDoor(level, pos, player);
        return InteractionResult.SUCCESS;
    }

    private void useDoor(Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        // 手持区域绑定工具：选中此门为「待绑定门」（管理员搭图用；见 SixtySecondsAreaWandItem）
        if (serverPlayer.getMainHandItem().getItem()
                == org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_AREA_WAND) {
            net.exmo.sre.sixtyseconds.content.item.SixtySecondsAreaWandItem.selectDoor(serverPlayer, pos);
            return;
        }
        // 手持锚点绑定工具：模板内的门=设为避难所锚点门，探索区的门=预演落位（见 SixtySecondsAnchorWandItem）
        if (serverPlayer.getMainHandItem().getItem()
                == org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_ANCHOR_WAND) {
            net.exmo.sre.sixtyseconds.content.item.SixtySecondsAnchorWandItem.selectDoor(serverPlayer, pos);
            return;
        }
        // 玩家NPC（创造 + 未编队）：右键门 = 敲门喊话（不开门菜单，见 SixtySecondsNpcKnock）
        if (net.exmo.sre.sixtyseconds.logic.SixtySecondsNpcKnock.tryKnock(serverLevel, serverPlayer, pos)) {
            return;
        }
        // 手持门锁/门陷阱：直接安装（方块 useItemOn 先于物品 useOn 执行并吞掉交互，
        // 不在这里短路的话这两个物品对着门永远只会打开门菜单——「门锁装不上」根因）
        ItemStack held = serverPlayer.getMainHandItem();
        if (held.getItem() instanceof net.exmo.sre.sixtyseconds.content.item.SixtySecondsDoorLockItem) {
            net.exmo.sre.sixtyseconds.content.item.SixtySecondsDoorLockItem
                    .install(serverPlayer, serverLevel, pos, held);
            return;
        }
        if (held.getItem() instanceof net.exmo.sre.sixtyseconds.content.item.SixtySecondsDoorTrapItem) {
            net.exmo.sre.sixtyseconds.content.item.SixtySecondsDoorTrapItem
                    .install(serverPlayer, serverLevel, pos, held);
            return;
        }
        SixtySecondsState.Data data = SixtySecondsState.get(serverLevel);
        if (data.phase == SixtySecondsPhase.PREPARATION) {
            // 准备阶段潜行右键：快速存入（60 秒抢时间的免菜单通道）
            if (serverPlayer.isShiftKeyDown()) {
                SixtySecondsDoorMenu.depositSupplies(serverPlayer, data);
                return;
            }
        } else if (net.exmo.sre.sixtyseconds.logic.SixtySecondsDefenseSystem.reinforce(
                serverLevel, serverPlayer, pos, serverPlayer.getMainHandItem())) {
            // 手持木板/铁锭/修理包：加固家门（耐久+等级，见 SixtySecondsDefenseSystem）
            return;
        }
        SixtySecondsDoorMenu.open(serverPlayer, pos);
    }
}
