package net.exmo.sre.sixtyseconds.content.block;

import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.network.OpenShelterPanelS2CPacket;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
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
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 避难所控制面板方块：右键打开本队仪表盘 GUI（家门耐久/等级、电力剩余、已解锁科技、成员健康与状态）。
 * 数据服务端现场汇总为快照发给点击者（{@link OpenShelterPanelS2CPacket}），不做持续同步。
 */
public class SixtySecondsShelterPanelBlock extends Block {

    public SixtySecondsShelterPanelBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        open(level, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        open(level, player);
        return ItemInteractionResult.SUCCESS;
    }

    private static void open(Level level, Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)
                || !SixtySecondsMod.isActive(level)) {
            return;
        }
        SixtySecondsState.Data data = SixtySecondsState.get(serverLevel);
        SixtySecondsState.TeamData team =
                data.teams.get(SixtySecondsStatsComponent.KEY.get(serverPlayer).teamId);
        if (team == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.no_team"), true);
            return;
        }
        long powerRemaining = Math.max(0, team.powerEndTick - serverLevel.getGameTime());
        List<String> names = new ArrayList<>();
        List<Integer> health = new ArrayList<>();
        List<Integer> flags = new ArrayList<>();
        for (UUID uuid : team.members) {
            if (serverLevel.getPlayerByUUID(uuid) instanceof ServerPlayer member) {
                SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(member);
                names.add(member.getGameProfile().getName());
                health.add(stats.health);
                flags.add((stats.sick ? 1 : 0) | (stats.downed ? 2 : 0) | (stats.monster ? 4 : 0));
            }
        }
        ServerPlayNetworking.send(serverPlayer, new OpenShelterPanelS2CPacket(
                team.doorHp, team.doorMaxHp, team.doorLevel, team.doorBroken,
                powerRemaining, team.storedSupplies.size(),
                new ArrayList<>(team.unlockedTech), names, health, flags));
    }
}
