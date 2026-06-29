package pro.fazeclan.river.stupid_express.role.arsonist.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SEEffects;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.role.arsonist.cca.DousedPlayerComponent;
import pro.fazeclan.river.stupid_express.utils.StupidRoleUtils;

public class LighterItem extends Item {

    public LighterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {
        SREGameWorldComponent gwc = SREGameWorldComponent.KEY.get(level);

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
        var players1 = server.getPlayerList().getPlayers();
        var alivePlayers = players1.stream().filter(GameUtils::isPlayerAliveAndSurvival).toList();
        var dousedCountComponent = DousedPlayerComponent.KEY.get(player);
        var dousedCount = dousedCountComponent.dousedCount;
        if (dousedCount >= (int) (alivePlayers.size() * 0.3)) {
            // 点燃所有存活且被泼油的玩家：施加燃烧效果，期间持续着火，效果结束后才死亡
            int burnTicks = Math.max(20,
                    StupidExpress.CONFIG.rolesSection.arsonistSection.burnDurationSeconds * 20);
            for (ServerPlayer target : alivePlayers) {
                DousedPlayerComponent targetDoused = DousedPlayerComponent.KEY.get(target);
                boolean wasDoused = targetDoused.getDoused();
                targetDoused.reset();
                if (wasDoused) {
                    // 记录点燃者，燃烧结束时把击杀归属给纵火犯
                    targetDoused.setBurningKiller(player.getUUID());
                    target.addEffect(new MobEffectInstance(SEEffects.BURNING, burnTicks, 0, false, true, true));
                    // 隐藏的防火效果：燃烧期间只着火不掉血，死亡时机交给燃烧效果掌控
                    target.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, burnTicks + 20, 0, false, false,
                            false));
                    target.setRemainingFireTicks(40);
                }
            }
            // 重置计数器
            player.playNotifySound(SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
            player.displayClientMessage(Component.translatable("item.stupid_express.lighter.used"), true);
            player.playNotifySound(SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1f, 1f);
            var playersLeft = players1.stream().filter(GameUtils::isPlayerAliveAndSurvival).count();
            if (playersLeft <= 1) {
                // 纵火犯独立胜利统计：使用 RoleUtils.customWinnerWin
                StupidRoleUtils.customWinnerWin(serverLevel, GameUtils.WinStatus.CUSTOM,
                        SERoles.ARSONIST.identifier().getPath(),
                        java.util.OptionalInt.of(SERoles.ARSONIST.color()));
            }
        } else {
            player.playNotifySound(SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 1.0f);
            GameUtils.killPlayer(player, true, null, StupidExpress.id("failed_ignite"));
        }
        player.getCooldowns().addCooldown(Items.COMMAND_BLOCK_MINECART, 20 * 20);
        return InteractionResultHolder.pass(player.getItemInHand(interactionHand));
    }
}
