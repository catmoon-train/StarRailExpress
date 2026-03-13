package pro.fazeclan.river.stupid_express.role.arsonist.item;

import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.role.arsonist.cca.DousedPlayerComponent;
import pro.fazeclan.river.stupid_express.utils.StupidRoleUtils;

public class LighterItem extends Item {

    public LighterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {
        StarGameWorldComponent gwc = StarGameWorldComponent.KEY.get(level);

        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.pass(player.getItemInHand(interactionHand));
        }
        if (!gwc.isRole(player, SERoles.ARSONIST)) {
            return InteractionResultHolder.pass(player.getItemInHand(interactionHand));
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(player.getItemInHand(interactionHand));
        }
        if (player.getCooldowns().isOnCooldown(Items.COMMAND_BLOCK_MINECART)) {
            player.displayClientMessage(Component.translatable("item.stupid_express.lighter.unable_cooldown"), true);
            return InteractionResultHolder.pass(player.getItemInHand(interactionHand));
        }
        var server = player.getServer();
        var players = server.getPlayerList().getPlayers();
        var alivePlayers = players.stream().filter(GameFunctions::isPlayerAliveAndSurvival).toList();
        var dousedCountComponent = DousedPlayerComponent.KEY.get(player);
        var dousedCount = dousedCountComponent.dousedCount;
        if (dousedCount >= (int) (alivePlayers.size() * 0.3)) {
            // 杀死所有存活且被泼油的玩家
            for (ServerPlayer target : players) {
                if (DousedPlayerComponent.KEY.get(target).getDoused()
                        && GameFunctions.isPlayerAliveAndSurvival(target)) {
                    GameFunctions.killPlayer(target, true, player, StupidExpress.id("ignited"));
                }
                DousedPlayerComponent.KEY.get(target).reset();
            }
            // 重置计数器
            player.playNotifySound(SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
            player.displayClientMessage(Component.translatable("item.stupid_express.lighter.used"), true);
            player.playNotifySound(SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1f, 1f);
            var playersLeft = players.stream().filter(GameFunctions::isPlayerAliveAndSurvival).count();
            if (playersLeft <= 1) {
                // 纵火犯独立胜利统计：使用 RoleUtils.customWinnerWin
                StupidRoleUtils.customWinnerWin(serverLevel, GameFunctions.WinStatus.CUSTOM,
                        SERoles.ARSONIST.identifier().getPath(),
                        java.util.OptionalInt.of(SERoles.ARSONIST.color()));
            }
        } else {
            player.playNotifySound(SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 1.0f);
            GameFunctions.killPlayer(player, true, player, StupidExpress.id("failed_ignite"));
        }
        player.getCooldowns().addCooldown(Items.COMMAND_BLOCK_MINECART, 20 * 20);
        return InteractionResultHolder.pass(player.getItemInHand(interactionHand));
    }
}
