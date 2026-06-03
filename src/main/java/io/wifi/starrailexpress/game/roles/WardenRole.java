
package io.wifi.starrailexpress.game.roles;

import io.wifi.starrailexpress.api.CustomWinnerRole;
import org.agmas.noellesroles.game.roles.neutral.warden.WardenPlayerComponent;
import io.wifi.starrailexpress.game.GameUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;

import java.util.ArrayList;
import java.util.List;

/**
 * 典狱长 - 独立胜利中立阵营
 *
 * 被动：
 *   - 开局自带一层护盾
 *   - 持续获得速度I
 *   - 开局自带一把假左轮手枪
 *   - 假心情（显示红色心情条）
 *   - 按直觉键透视周围所有人（灰色，10格内），目标常驻透视（深蓝色，无限距离）
 *
 * 主动技能 [正义戒律]：
 *   - 冷却60s，需持有假左轮
 *   - 对3格内目标施加正义戒律，冷却后可更改目标
 *   - 若目标击杀他人，典狱长受惩罚（10s缓慢V，技能重置冷却，目标清除）
 *
 * 正义审判：
 *   - 若目标被杀手或中立击杀，进入审判阶段
 *   - 假左轮替换为德林加手枪
 *   - 需在击杀上限内击杀凶手则独立胜利
 *   - 若击杀数达到上限仍未击杀凶手则正义反噬死亡
 *   - 非审判阶段击杀任何人同样触发正义反噬
 *
 * 商店：100金币买假左轮，90秒购买冷却
 */
public class WardenRole extends CustomWinnerRole {

    public WardenRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
                      MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    // ==================== 角色初始化 ====================
    // 初始化逻辑已迁移到ModRolesInitialEventRegister中处理

    // ==================== 持续速度I ====================
    // 速度I逻辑已迁移到WardenPlayerComponent.serverTick()中处理

    // ==================== 技能系统 ====================
    // 技能注册已迁移到ModRolesInitialEventRegister中处理（使用context.target()获取鼠标准星目标）

    // ==================== 击杀处理 ====================
    // 击杀处理逻辑已迁移到WardenPlayerComponent.registerEvents()通过事件系统处理

    // ==================== 假左轮/德林加处理 ====================
    // 假左轮手枪(FakeRevolverItem)本身就不会真正射击，无需额外阻止逻辑

    // ==================== 胜利条件 ====================

    @Override
    public GameUtils.WinStatus checkWin(ServerPlayer player, GameUtils.WinStatus winStatus) {
        WardenPlayerComponent wardenComp = WardenPlayerComponent.KEY.get(player);
        if (wardenComp.hasWon()) {
            return GameUtils.WinStatus.CUSTOM;
        }
        return GameUtils.WinStatus.NOT_MODIFY;
    }

    @Override
    public boolean didPlayerWin(ServerPlayer player, boolean original, GameUtils.WinStatus winStatus) {
        WardenPlayerComponent wardenComp = WardenPlayerComponent.KEY.get(player);
        if (wardenComp.hasWon()) {
            return true;
        }
        return original;
    }

    // ==================== 商店 ====================

    @Override
    public List<ItemStack> getDefaultItems() {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(ModItems.FAKE_REVOLVER)); // 假左轮
        return items;
    }

    // ==================== 注册辅助 ====================
    // registerRole和init已不再需要，角色注册通过TMMRoles.registerRole处理
}
