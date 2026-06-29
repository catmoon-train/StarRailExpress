package pro.fazeclan.river.stupid_express.modifier.split_personality;

import io.wifi.starrailexpress.event.AllowPlayerDeath;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SkinSplitPersonalityComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent.ChoiceType;
import pro.fazeclan.river.stupid_express.network.SplitBackCamera;

import java.util.UUID;

public class SplitPersonalityHandler {

    // 监听双重人格的替换者
    // private static final Set<UUID> switchingWatchers = new HashSet<>();

    public static void init() {
        // 注册死亡事件 - 处理双重人格死亡时的倒计时选择
        AllowPlayerDeath.EVENT.register((victim, deathReason) -> {
            if (!(victim instanceof ServerPlayer serverVictim))
                return true;
            var worldModifierComponent = WorldModifierComponent.KEY.get(victim.level());
            if (!worldModifierComponent.isModifier(serverVictim, SEModifiers.SPLIT_PERSONALITY)) {
                return true;
            }

            var component = SplitPersonalityComponent.KEY.get(serverVictim);
            if (deathReason.getPath().equals("disconnected")) {
                if (component.getMainPersonality().equals(victim.getUUID())) {
                    component.setMainPersonalityChoice(ChoiceType.SACRIFICE);
                } else {
                    component.setSecondPersonalityChoice(ChoiceType.SACRIFICE);
                }
            }
            // 检查是否是双重人格
            if (component.getMainPersonality() == null || component.getSecondPersonality() == null) {
                resetSplitComponent(serverVictim);
                return true;
            }
            if (component.getTemporaryRevivalStartTick() > 0) {
                ServerPlayNetworking.send(serverVictim, new SplitBackCamera());
                resetSplitComponent(serverVictim);
                return true;
            }
            component.setDeath(true);
            return handleDeathChoicesPublic(serverVictim, component);
        });
    }

    /**
     * 处理死亡选择的结果
     */
    public static boolean handleDeathChoicesPublic(ServerPlayer player, SplitPersonalityComponent component) {
        UUID p_au = component.getMainPersonality();
        UUID p_bu = component.getSecondPersonality();
        Player p_a = player.level().getPlayerByUUID(p_au);
        Player p_b = player.level().getPlayerByUUID(p_bu);
        if (!(p_a instanceof ServerPlayer p_sa))
            return true;
        if (!(p_b instanceof ServerPlayer p_sb))
            return true;
        int playerType = 0;
        if (p_au.equals(player.getUUID())) {
            playerType = 1; // 主人格
        } else if (p_bu.equals(player.getUUID())) {
            playerType = 2; // 副人格
        }
        if (playerType == 1) {
            var nComp = SplitPersonalityComponent.KEY.get(p_sb);
            boolean needDeath = handleDeathChoices(p_sb, nComp);
            if (needDeath) {
                p_sb.setGameMode(GameType.ADVENTURE);
                nComp.init();
                resetSplitComponent(p_sb);
                GameUtils.killPlayer(p_sb, false, null, StupidExpress.id("split_personality"));
            } else {
                p_sb.teleportTo(player.getX(), player.getY(), player.getZ());
                p_sb.setGameMode(GameType.ADVENTURE);
                nComp.setDeath(false);
                // revivePlayer(p_sb, nComp);
                // 复活
            }
        } else {
            var nComp = SplitPersonalityComponent.KEY.get(p_sa);
            boolean needDeath = handleDeathChoices(p_sa, nComp);
            if (needDeath) {
                p_sa.setGameMode(GameType.ADVENTURE);
                nComp.init();
                resetSplitComponent(p_sa);

                GameUtils.killPlayer(p_sa, false, null, StupidExpress.id("split_personality"));
            } else {
                p_sa.teleportTo(player.getX(), player.getY(), player.getZ());
                p_sa.setGameMode(GameType.ADVENTURE);
                nComp.setDeath(false);
                // revivePlayer(p_sa, nComp);
                // 复活
            }
        }

        return handleDeathChoices(player, component);
    }

    private static void resetSplitComponent(ServerPlayer player) {
        WorldModifierComponent.KEY.get(player.level()).removeModifier(player.getUUID(),
                SEModifiers.SPLIT_PERSONALITY);
        SplitPersonalityComponent.KEY.get(player).init();
        SkinSplitPersonalityComponent.KEY.get(player).clear();
    }

    private static boolean handleDeathChoices(ServerPlayer player, SplitPersonalityComponent component) {
        var mainChoice = component.getMainPersonalityChoice();
        var secondChoice = component.getSecondPersonalityChoice();
        UUID p_a = component.getMainPersonality();
        UUID p_b = component.getSecondPersonality();
        if (p_a == null || p_b == null)
            return true;
        int playerType = 0;
        if (p_a.equals(player.getUUID())) {
            playerType = 1; // 主人格
        } else if (p_b.equals(player.getUUID())) {
            playerType = 2; // 副人格
        }
        ServerPlayNetworking.send(player, new SplitBackCamera());
        // 预留：都复活
        // 情况3：两个都选择奉献 -> 两个都复活，但时间只有60秒
        if (mainChoice == SplitPersonalityComponent.ChoiceType.SACRIFICE &&
                secondChoice == SplitPersonalityComponent.ChoiceType.SACRIFICE) {

            revivePlayer(player, component);
            component.setDeath(false);

            component.setTemporaryRevivalStartTick(1200);

            player.setGameMode(GameType.ADVENTURE);

            // 添加消息提示
            MutableComponent reviveMessage = Component.translatable("msg.stupid_express.split_personality.reviveboth");
            player.displayClientMessage(reviveMessage, true);
            return false;
        }

        // 删除控件
        resetSplitComponent(player);

        // 情况1：两个都选择欺骗 -> 直接死亡
        if (mainChoice == SplitPersonalityComponent.ChoiceType.BETRAY &&
                secondChoice == SplitPersonalityComponent.ChoiceType.BETRAY) {
            MutableComponent deathMessage = net.minecraft.network.chat.Component
                    .translatable("msg.stupid_express.split_personality.liebothdie").withStyle(ChatFormatting.RED);
            player.displayClientMessage(deathMessage,
                    true);
            return true;
        }

        // 情况2：一个欺骗一个奉献
        if ((mainChoice == SplitPersonalityComponent.ChoiceType.BETRAY
                && secondChoice == SplitPersonalityComponent.ChoiceType.SACRIFICE)) {
            if (playerType == 1) {
                revivePlayer(player, component);
                player.displayClientMessage(net.minecraft.network.chat.Component
                        .translatable("msg.stupid_express.split_personality.revive").withStyle(ChatFormatting.GREEN),
                        true);
                return false;
            } else {
                player.displayClientMessage(net.minecraft.network.chat.Component
                        .translatable("msg.stupid_express.split_personality.donatedied").withStyle(ChatFormatting.RED),
                        true);
                return true;
            }

        }
        if (mainChoice == SplitPersonalityComponent.ChoiceType.SACRIFICE
                && secondChoice == SplitPersonalityComponent.ChoiceType.BETRAY) {
            if (playerType == 2) {
                revivePlayer(player, component);
                player.displayClientMessage(net.minecraft.network.chat.Component
                        .translatable("msg.stupid_express.split_personality.revive").withStyle(ChatFormatting.GREEN),
                        true);
                return false;
            } else {
                player.displayClientMessage(net.minecraft.network.chat.Component
                        .translatable("msg.stupid_express.split_personality.donatedied").withStyle(ChatFormatting.RED),
                        true);
                return true;
            }
        }
        return true;
    }

    /**
     * 复活玩家并恢复库存
     */
    private static void revivePlayer(ServerPlayer player, SplitPersonalityComponent component) {
        // 复活玩家
        player.setHealth(player.getMaxHealth());
        component.setDeath(false);

        // 消除所有负面效果
        player.removeAllEffects();
    }

    /**
     * 获取另一个人格的玩家
     */
    public static ServerPlayer getOtherPersonality(ServerPlayer player) {
        var component = SplitPersonalityComponent.KEY.get(player);
        if (component == null || component.getMainPersonality() == null)
            return null;

        UUID otherPersonalityUUID;
        if (component.isMainPersonality()) {
            otherPersonalityUUID = component.getSecondPersonality();
        } else {
            otherPersonalityUUID = component.getMainPersonality();
        }

        return (ServerPlayer) player.level().getPlayerByUUID(otherPersonalityUUID);
    }

    /**
     * 检查玩家是否是观察者（未活跃的人格）
     */
    public static boolean isObserver(ServerPlayer player) {
        var component = SplitPersonalityComponent.KEY.get(player);
        if (component == null)
            return false;
        return !component.isCurrentlyActive();
    }
}
