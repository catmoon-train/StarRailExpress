package net.exmo.sre.sixtyseconds.content.block;

import net.exmo.sre.sixtyseconds.SixtySecondsPhase;
import net.exmo.sre.sixtyseconds.arena.SixtySecondsSearchZones;
import net.exmo.sre.sixtyseconds.component.FamilyPosition;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.network.OpenSixtySecondsDoorS2CPacket;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.world.item.Items;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
 * 避难所的门（交互中枢）。用 blockstate 属性 {@link #PURPOSE} 区分三类用途：
 * <ul>
 *   <li>SEARCH：进/出本队搜索区（{@link SixtySecondsSearchZones}）。</li>
 *   <li>EVENT：打开事件 GUI 壳（商人/污雨/浓烟…，业务留 TODO）。</li>
 *   <li>VISIT：打开拜访 GUI 壳（选别队玩家→交易/？？？，业务留 TODO）。</li>
 * </ul>
 * 管理员在创造模式下 <b>潜行+右键</b> 循环切换门的用途；玩家右键触发对应行为。
 * 参照 {@code RepairExitGateBlock} / {@code RepairStationBlock}。
 */
public class ShelterDoorBlock extends Block {
    public static final EnumProperty<DoorPurpose> PURPOSE = EnumProperty.create("purpose", DoorPurpose.class);

    public ShelterDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PURPOSE, DoorPurpose.SEARCH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PURPOSE);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        useDoor(state, level, pos, player);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        useDoor(state, level, pos, player);
        return InteractionResult.SUCCESS;
    }

    private void useDoor(BlockState state, Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        DoorPurpose purpose = state.getValue(PURPOSE);
        // 创造模式潜行右键：循环切换门用途（管理员配置）
        if (serverPlayer.isCreative() && serverPlayer.isShiftKeyDown()) {
            DoorPurpose next = purpose.next();
            level.setBlock(pos, state.setValue(PURPOSE, next), Block.UPDATE_ALL);
            serverPlayer.displayClientMessage(
                    Component.literal("[60s] door -> " + next.getSerializedName()), true);
            return;
        }
        // 准备阶段：右键地下室门把携带的物资记录进本队库存
        SixtySecondsState.Data data = SixtySecondsState.get((ServerLevel) level);
        if (data.phase == SixtySecondsPhase.PREPARATION) {
            depositSupplies((ServerLevel) level, serverPlayer, data);
            return;
        }
        switch (purpose) {
            case SEARCH -> {
                if (SixtySecondsSearchZones.isInSearchZone(serverPlayer)) {
                    SixtySecondsSearchZones.returnPlayer(serverPlayer);
                } else {
                    SixtySecondsSearchZones.enter(serverPlayer);
                }
            }
            case EVENT -> ServerPlayNetworking.send(serverPlayer,
                    new OpenSixtySecondsDoorS2CPacket(DoorPurpose.EVENT.ordinal(), pos));
            case VISIT -> net.exmo.sre.sixtyseconds.logic.SixtySecondsVisitSystem.openRequestScreen(serverPlayer);
        }
    }

    /** 准备阶段：把玩家携带槽（0..携带上限-1）里的物资转入本队库存，清空这些槽以便继续搜集。 */
    private void depositSupplies(ServerLevel level, ServerPlayer player, SixtySecondsState.Data data) {
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        SixtySecondsState.TeamData team = data.teams.get(stats.teamId);
        if (team == null) {
            return;
        }
        int carry = stats.familyPosition == null ? FamilyPosition.MOTHER.carryLimit : stats.familyPosition.carryLimit;
        int deposited = 0;
        for (int slot = 0; slot < carry && slot <= 35; slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            if (!current.isEmpty() && !current.is(Items.BARRIER)) {
                team.storedSupplies.add(current.copy());
                deposited += current.getCount();
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
        player.displayClientMessage(
                Component.translatable("message.noellesroles.sixty_seconds.supplies_recorded", deposited), true);
    }
}
