package org.agmas.noellesroles.game.roles.innocent.meatball;

import io.wifi.starrailexpress.api.ExtraEffectRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.ChatFormatting;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 肉汁角色类
 * 继承 ExtraEffectRole 以支持药水效果
 * 重写 onFinishQuest 方法以实现赏金增加
 */
public class MeatballRole extends ExtraEffectRole {

    public MeatballRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
                        SRERole.MoodType moodType, int maxSprintTime, boolean hideOnScoreboard) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, hideOnScoreboard);
    }

    @Override
    public void onFinishQuest(Player player, String quest) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        
        // 检查是否是肉汁角色
        if (!gameWorld.isRole(player, ModRoles.MEATBALL)) {
            return;
        }
        
        // 增加赏金
        MeatballPlayerComponent component = ModComponents.MEATBALL.get(player);
        if (component != null) {
            component.addBounty();
            
            // 发送消息提示
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                    Component.translatable("message.noellesroles.meatball.bounty_increased", component.getBounty())
                        .withStyle(ChatFormatting.GOLD),
                    true
                );
            }
        }
    }

    @Override
    public void onKill(Player victim, boolean spawnBody, Player killer, ResourceLocation deathReason) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(victim.level());
        
        // 检查是否是肉汁被击杀
        if (!gameWorld.isRole(victim, ModRoles.MEATBALL)) {
            return;
        }
        
        // 收集赏金（无论谁击杀都要清空）
        MeatballPlayerComponent meatballComponent = ModComponents.MEATBALL.get(victim);
        if (meatballComponent == null) {
            return;
        }
        
        int bounty = meatballComponent.collectBounty();
        
        // 只有非乘客阵营的击杀者才能获得赏金
        if (killer != null && !gameWorld.isInnocent(killer) && bounty > 0) {
            SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(killer);
            if (shopComponent != null) {
                shopComponent.addToBalance(bounty);
                
                // 发送消息提示
                if (killer instanceof ServerPlayer sp) {
                    sp.displayClientMessage(
                        Component.translatable("message.noellesroles.meatball.killer_reward", bounty)
                            .withStyle(ChatFormatting.GOLD),
                        true
                    );
                }
            }
        }
    }
}
