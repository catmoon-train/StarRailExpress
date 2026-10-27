package org.agmas.noellesroles.game.modes.echo.content;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.content.item.KnifeItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.modes.echo.combat.EchoCutterStrike;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoSessions;
import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import org.agmas.noellesroles.game.modes.echo.state.EchoRoleData;

/** 按住右键蓄力 0.8 秒，松手立即出击；绝不发默认 KnifeStab 包。 */
public final class EchoCutterItem extends KnifeItem {
    public EchoCutterItem(Properties properties) { super(properties); }
    /** 读取服务端原版冷却包的投影；不是客户端推算的第二份攻击截止。 */
    public static int cutterRemaining(Player player) {
        var cooldowns=player.getCooldowns();
        var entry=cooldowns.cooldowns.get(EchoContent.CUTTER);
        return entry==null?0:Math.max(0,entry.endTime-cooldowns.tickCount);
    }
    @Override public int getMinKnifeChargeTicks(ItemStack stack,LivingEntity user) { return EchoCutterStrike.CHARGE_TICKS; }
    @Override public int getUseDuration(ItemStack stack,LivingEntity user) { return 72000; }
    @Override public boolean canStartKnifeCharge(Level level,Player player,InteractionHand hand,ItemStack stack) {
        if(hand!=InteractionHand.MAIN_HAND || player.getInventory().selected!=0) return false;
        if(player instanceof ServerPlayer server) {
            var match=EchoSessions.get(server.serverLevel());
            return match!=null && match.combat().beginCutter(server);
        }
        var data=RoleData.getNullable(EchoRoleData.class,player);
        return data!=null && data.matchId()!=null && data.faction()==EchoFaction.INTERCEPTOR
                && data.phase().acceptsActions() && data.life().standing() && !data.controlled()
                && cutterRemaining(player)==0;
    }
    @Override public boolean onKnifeChargeReleased(ItemStack stack,Level level,Player player,int usedTicks) {
        if(player instanceof ServerPlayer server) {
            var match=EchoSessions.get(server.serverLevel());
            if(match!=null) {
                // 本地使用时长和比赛时长都必须达标；队列等待不能替短蓄力补齐最后一 tick。
                if(usedTicks<EchoCutterStrike.CHARGE_TICKS || !match.releaseCutter(server))
                    match.combat().cancelCutter(server);
            }
        }
        return true;
    }
}
