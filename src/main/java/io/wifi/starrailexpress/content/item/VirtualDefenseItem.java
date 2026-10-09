/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.roles.killer.dream.VirtualShieldComponent;

import java.util.ArrayList;

/**
 * 虚拟护盾试剂。
 *
 * <ul>
 * <li>可放入<b>食物盘 / 饮料盘</b>：给下一个拿取食物的玩家提供
 * {@link VirtualShieldComponent#DEFAULT_SHIELD} 点虚拟护盾（见
 * {@code PlatterBlock} 与 {@code PlayerEntityMixin} 的进食结算）。</li>
 * <li>可被 {@link #canUseByRightClickRolePaths} 里的职业（亡命徒 / 超级亡命徒 / 污秽）
 * 直接右键饮用，效果与放入盘子相同。</li>
 * <li>虚拟护盾<b>只抵挡虚拟血量伤害</b>，无法抵挡正常死亡（原版血量归零、摔死、环境伤害等照旧）。</li>
 * </ul>
 *
 * <p>护盾试剂的护盾是「层数」，虚拟护盾是「点数条」，所以这里只在护盾为 0 时补满
 * {@link VirtualShieldComponent#DEFAULT_SHIELD} 点（重复饮用不会无限叠加）。</p>
 */
public class VirtualDefenseItem extends Item {
    public SREGameWorldComponent gameWorldComponent = null;
    /** 可以直接右键饮用虚拟护盾试剂的职业路径。 */
    public static ArrayList<String> canUseByRightClickRolePaths = new ArrayList<>();

    public VirtualDefenseItem(Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack itemStack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack itemStack, LivingEntity livingEntity) {
        return 20;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {
        ItemStack itemStack = player.getItemInHand(interactionHand);
        if (gameWorldComponent == null) {
            gameWorldComponent = SREGameWorldComponent.KEY.get(level);
        }
        if (gameWorldComponent != null) {
            var role = gameWorldComponent.getRole(player);
            if (role != null && canUseByRightClickRolePaths.contains(role.identifier().getPath())) {
                player.startUsingItem(interactionHand);
                return InteractionResultHolder.consume(itemStack);
            }
        }
        return InteractionResultHolder.pass(player.getItemInHand(interactionHand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity livingEntity) {
        if (gameWorldComponent == null) {
            gameWorldComponent = SREGameWorldComponent.KEY.get(level);
        }
        if (gameWorldComponent != null) {
            var role = gameWorldComponent.getRole(livingEntity.getUUID());
            if (role != null && canUseByRightClickRolePaths.contains(role.identifier().getPath())
                    && livingEntity instanceof Player player) {
                VirtualShieldComponent shield = VirtualShieldComponent.KEY.get(player);
                // 已有护盾时不叠加（护盾是点数条，不是层数），只在空条时补满
                if (shield.currentShield() <= 0) {
                    shield.setShield(VirtualShieldComponent.DEFAULT_SHIELD);
                    itemStack.consume(1, livingEntity);
                }
            }
        }
        return itemStack;
    }
}