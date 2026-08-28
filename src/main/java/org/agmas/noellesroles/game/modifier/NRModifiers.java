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

package org.agmas.noellesroles.game.modifier;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.agmas.harpymodloader.events.ResetPlayerEvent;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.modifier.expedition.ExpeditionComponent;
import org.agmas.noellesroles.game.modifier.hoarse.HoarseModifier;
import org.agmas.noellesroles.game.modifier.introverted.IntrovertedModifier;
import org.agmas.noellesroles.game.modifier.taxed.TaxedModifier;
import org.agmas.noellesroles.role.ModRoles;

import java.awt.*;
import java.util.HashSet;
import java.util.List;

/**
 * NoellesRoles 修饰符注册类
 */
public class NRModifiers {

    /** Runtime-only marker for a player body controlled by Fake Steve. */
    public static final SREModifier FAKE_STEVE_REPLACED = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("fake_steve_replaced"),
            0x303030,
            null,
            null,
            false,
            false))
            .setCanSetSpawnInfoInConfig(false)
            .setDefaultEnableChance(0)
            .setDefaultMax(0)
            .setAddedVersion("4.4");

    /** 远征队修饰符 */
    public static SREModifier EXPEDITION = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("expedition"),
            new Color(210, 180, 140).getRGB(), // 棕色 - 代表远征
            null,
            null,
            false,
            false))
            .setAddedVersion("3.2")
            .setDefaultEnableChance(5000);

    /** 内向修饰符 */
    public static SREModifier INTROVERTED = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("introverted"),
            0x9B7FD4, // 紫色
            null,
            null,
            false,
            false))
            .setServerGameTickEvent((p) -> IntrovertedModifier.serverTick(p))
            .setDefaultMax(2)
            .setDefaultEnableChance(5000);

    /** 纳税修饰符 */
    public static SREModifier TAXED = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("taxed"),
            0xFC8E26, // 橙色
            null,
            null,
            false,
            false))
            .setDefaultMax(1)
            .setDefaultEnableChance(2000);

    /** 饥渴修饰符：可从食物盘和饮料盘各拿取至多2份食物和2份饮料 */
    public static SREModifier HUNGRY = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("hungry"),
            0xE05A47, // 红橙色 - 代表食欲
            null,
            null,
            false,
            false))
            .setDefaultMax(2)
            .setDefaultEnableChance(5000);

    /** 沙哑修饰符：嗓音十分低沉 */
    public static SREModifier HOARSE = HMLModifiers.registerModifier(new SREModifier(
            Noellesroles.id("hoarse"),
            0x8B7355, // 棕灰色 - 代表沙哑
            null,
            null,
            false,
            false))
            .setServerGameTickEvent((p) -> HoarseModifier.serverTick(p))
            .setDefaultMax(2)
            .setDefaultEnableChance(5000);

    /**
     * 初始化修饰符系统
     */
    public static void init() {
        FAKE_STEVE_REPLACED.addBothRelatedRole(ModRoles.FAKE_STEVE);
        EXPEDITION.civilianOnly = true;
        EXPEDITION.cannotBeAppliedTo = new HashSet<>(List.of(ModRoles.GHOST));
        INTROVERTED.civilianOnly = true;
        excludeLeonFromAllModifiers();
        assignModifierComponents();
        TaxedModifier.init();
    }

    /**
     * 里昂不与远征队等任何修饰符共存于一人身上。
     *
     * <p>由于本模组（Noellesroles）入口先于其它模组（如 stupid_express）的修饰符注册执行，
     * 此处在服务器启动时（所有模组修饰符均已注册到 {@link HMLModifiers#MODIFIERS}）统一把里昂
     * 加入每个修饰符的 {@code cannotBeAppliedTo} 排除名单。
     */
    private static void excludeLeonFromAllModifiers() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            for (SREModifier modifier : HMLModifiers.MODIFIERS) {
                if (modifier.cannotBeAppliedTo == null) {
                    modifier.cannotBeAppliedTo = new HashSet<>();
                }
                modifier.cannotBeAppliedTo.add(ModRoles.LEON);
            }
        });
    }

    /**
     * 分配修饰符组件
     */
    public static void assignModifierComponents() {
        // 远征队修饰符分配事件
        ModifierAssigned.EVENT.register((player, modifier) -> {
            if (!modifier.equals(EXPEDITION)) {
                return;
            }
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }

            var level = serverPlayer.serverLevel();
            var gameWorld = SREGameWorldComponent.KEY.get(level);

            // 检查玩家是否是好人阵营（包括平民和警长阵营）
            // 并且不能是杀手阵营或中立阵营
            SRERole role = gameWorld.getRole(player);
            if (role != null && role.isInnocent() && !role.canUseKiller() && !role.isNeutrals()) {
                // 只排除小透明
                if (!gameWorld.isRole(player, ModRoles.GHOST)) {
                    // 给玩家分配远征队组件
                    var expeditionComponent = ExpeditionComponent.KEY.get(player);
                    expeditionComponent.sync();

                    Noellesroles.LOGGER.info("Expedition modifier assigned to player: " + player.getName().getString());
                }
            } else {
                Noellesroles.LOGGER
                        .info("Expedition modifier not assigned to killer/neutral: " + player.getName().getString());
            }
        });

        // 远征队修饰符移除事件
        ModifierRemoved.EVENT.register((player, modifier) -> {
            if (modifier.equals(EXPEDITION)) {
                var expeditionComponent = ExpeditionComponent.KEY.get(player);
                if (expeditionComponent != null) {
                    expeditionComponent.clear();
                    expeditionComponent.sync();
                }
            }
        });

        // 玩家重置事件 - 清理远征队修饰符组件
        ResetPlayerEvent.EVENT.register(player -> {
            try {
                var expeditionComponent = ExpeditionComponent.KEY.get(player);
                if (expeditionComponent != null) {
                    expeditionComponent.clear();
                    expeditionComponent.sync();
                }
            } catch (Exception e) {
                // 玩家可能没有 expedition 组件，忽略错误
            }
        });
    }
    static {
        INTROVERTED.setAddedVersion("4.0");
        TAXED.setAddedVersion("4.0");
        HUNGRY.setAddedVersion("4.3");
        HOARSE.setAddedVersion("4.3");
    }
}
