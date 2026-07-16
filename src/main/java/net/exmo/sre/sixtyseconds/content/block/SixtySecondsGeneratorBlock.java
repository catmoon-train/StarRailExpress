package net.exmo.sre.sixtyseconds.content.block;

import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsPowerSystem;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 发电机：手持废料/煤炭右键投喂燃料，每份为全队供电
 * （{@link SixtySecondsPowerSystem}）；供电中 LIT 点亮。放置登记归属队伍，拆除注销。
 *
 * <h3>燃料换算表</h3>
 * <ul>
 *   <li>废料 = 2 份（20 秒）</li>
 *   <li>煤炭/木炭 = 6 份（60 秒）</li>
 *   <li>电池 = 12 份（120 秒）</li>
 *   <li>燃料罐 = 45 份（450 秒）</li>
 *   <li>柴油罐 = 90 份（900 秒）</li>
 *   <li>大型电池 = 36 份（360 秒，= 电池 ×3）</li>
 *   <li>太阳能板 = 108 份（1080 秒，= 大型电池 ×3，仅白天）</li>
 * </ul>
 */
public class SixtySecondsGeneratorBlock extends Block {

    public SixtySecondsGeneratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.LIT);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel && placer instanceof ServerPlayer player) {
            SixtySecondsPowerSystem.registerGenerator(serverLevel, pos,
                    SixtySecondsStatsComponent.KEY.get(player).teamId);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            SixtySecondsPowerSystem.unregister(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)
                || !SixtySecondsMod.isActive(level)) {
            return ItemInteractionResult.SUCCESS;
        }
        SixtySecondsState.Data data = SixtySecondsState.get(serverLevel);
        SixtySecondsState.TeamData team =
                data.teams.get(SixtySecondsStatsComponent.KEY.get(serverPlayer).teamId);
        if (team == null) {
            return ItemInteractionResult.SUCCESS;
        }
        int units = 0;
        if (stack.is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_BATTERY)) {
            units = 12; // 电池 = 120 秒
        } else if (stack.is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_BATTERY_LARGE)) {
            units = 36; // 大型电池 = 电池 ×3 = 360 秒
        } else if (stack.is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_FUEL_CAN)) {
            units = 45; // 燃料罐 = 450 秒
        } else if (stack.is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_DIESEL_CAN)) {
            units = 90; // 柴油罐 = 900 秒
        } else if (stack.is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_SOLAR_PANEL)) {
            // 太阳能板：仅白天（清晨/白天子相位）可用，= 大型电池 ×3 = 1080 秒
            SixtySecondsState.Data solarData = SixtySecondsState.get(serverLevel);
            if (net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.isNight(solarData, serverLevel.getGameTime())) {
                serverPlayer.displayClientMessage(Component.translatable(
                        "message.noellesroles.sixty_seconds.solar_no_sun"), true);
                return ItemInteractionResult.SUCCESS;
            }
            units = 108;
        } else if (stack.is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_SCRAP)) {
            units = 2; // 废料 = 20 秒
        } else if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) {
            units = 6; // 煤炭/木炭 = 60 秒
        }
        if (units > 0) {
            if (!serverPlayer.isCreative()) {
                stack.shrink(1);
            }
            for (int i = 0; i < units; i++) {
                SixtySecondsPowerSystem.addFuel(serverLevel, team);
            }
            serverLevel.playSound(null, pos, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.6F, 0.6F);
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.generator_fueled",
                    Math.max(0, (team.powerEndTick - serverLevel.getGameTime()) / 20)), true);
        } else {
            // 非燃料：打开电力面板 GUI（实时倒计时 + 燃料换算表）
            openPowerPanel(serverLevel, serverPlayer, team);
        }
        return ItemInteractionResult.SUCCESS;
    }

    /** 空手右键：打开电力面板 GUI。 */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
                && SixtySecondsMod.isActive(level)) {
            SixtySecondsState.TeamData team = SixtySecondsState.get(serverLevel).teams
                    .get(SixtySecondsStatsComponent.KEY.get(serverPlayer).teamId);
            if (team != null) {
                openPowerPanel(serverLevel, serverPlayer, team);
            }
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    private static void openPowerPanel(ServerLevel level, ServerPlayer player, SixtySecondsState.TeamData team) {
        long remaining = Math.max(0, team.powerEndTick - level.getGameTime());
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new net.exmo.sre.sixtyseconds.network.OpenPowerPanelS2CPacket(remaining));
    }
}
