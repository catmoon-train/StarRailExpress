package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.network.OpenTechTreeS2CPacket;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 科技树：用<b>废料</b>（{@code sixty_seconds_scrap}）解锁科技，按队共享
 * （{@link SixtySecondsState.TeamData#unlockedTech}）。科技门控合成台配方
 * （{@link SixtySecondsRecipes}）与功能方块；{@code defense} 解锁同时给家门 +1 级。
 * 入口：{@code /sre:60s tech} 打开科技树界面。
 */
public final class SixtySecondsTechTree {

    /** 科技节点：父节点未解锁不能解锁本节点。名称/描述用 {@code tech.noellesroles.sixty_seconds.<id>} 键。 */
    public record TechNode(String id, int scrapCost, String parentId) {
    }

    /** 静态科技表（两端共享，只发解锁状态）。 */
    public static final List<TechNode> NODES = List.of(
            new TechNode("basic_tools", 5, null),      // -30%: 8→5  基础工具：简易工作台（书桌）配方
            new TechNode("survival", 8, "basic_tools"), // -30%: 12→8  野外生存：罗盘/收音机/警报器/诱饵/工具箱
            new TechNode("kitchen", 8, "basic_tools"), // -30%: 12→8  厨房：灶台配方（消毒绷带等）
            new TechNode("medicine", 11, "kitchen"),    // -30%: 16→11  医疗：抗生素/医疗包/肾上腺素
            new TechNode("brewing", 8, "basic_tools"), // -30%: 12→8  净化：浴缸配方（净化污染水）
            new TechNode("chemistry", 14, "brewing"),  // -30%: 20→14  化学：燃烧瓶/炸弹/闪光弹/净化药片
            new TechNode("power", 14, "basic_tools"),  // -30%: 20→14  电力：发电机 + 电灯
            new TechNode("engineering", 16, "power"),  // -30%: 24→16  工程学：电锯/电击棍/太阳能板/夜视镜
            new TechNode("defense", 11, "basic_tools"), // -30%: 16→11  防御工事：路障/陷阱 + 家门升 1 级
            new TechNode("weapons", 16, "defense"),    // -30%: 24→16  武器制造
            new TechNode("armor", 14, "defense"),      // -30%: 20→14  护甲制造
            new TechNode("locksmith", 11, "basic_tools"), // -30%: 16→11  开锁技术：高级撬棍/开锁器
            new TechNode("metallurgy", 16, "weapons"), // -30%: 24→16  冶金：钢锭 + 钢制武器/护甲
            new TechNode("agriculture", 11, "kitchen"), // -30%: 16→11  农业：肥料/蔬菜/加工食品
            new TechNode("fortification", 14, "defense"), // -30%: 20→14  工事强化：强化路障/探照灯/钉子
            // ══ 扩充批次二 ══════════════════════════════════════════════
            new TechNode("comfort", 8, "basic_tools"),   // 心理慰藉：娱乐物品（扑克/象棋/口琴/吉他/泰迪熊）
            new TechNode("gourmet", 10, "kitchen"),      // 炊事进阶：保温壶/末日乱炖/军用口粮
            new TechNode("filtration", 14, "brewing"),   // 高级净化：大瓶水/批量净化（需供电）
            new TechNode("gunsmith", 20, "weapons"),     // 军械工坊：自产子弹/手枪/猎枪/破片手雷
            new TechNode("rainwater", 8, "brewing"));    // 雨水收集：三档集水器（被动产污染水）

    private SixtySecondsTechTree() {
    }

    public static TechNode byId(String id) {
        for (TechNode node : NODES) {
            if (node.id().equals(id)) {
                return node;
            }
        }
        return null;
    }

    public static boolean isUnlocked(SixtySecondsState.TeamData team, String techId) {
        return team != null && team.unlockedTech.contains(techId);
    }

    /** 打开科技树界面（命令入口）。 */
    public static void open(ServerPlayer player) {
        if (!SixtySecondsMod.isActive(player.level())) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.cmd_not_running"), false);
            return;
        }
        sendState(player);
    }

    private static void sendState(ServerPlayer player) {
        SixtySecondsState.Data data = SixtySecondsState.get(player.serverLevel());
        SixtySecondsState.TeamData team = data.teams.get(SixtySecondsStatsComponent.KEY.get(player).teamId);
        List<String> unlocked = team == null ? List.of() : new ArrayList<>(team.unlockedTech);
        ServerPlayNetworking.send(player, new OpenTechTreeS2CPacket(unlocked.toArray(new String[0])));
    }

    /** C2S 解锁请求：校验前置科技 + 背包废料，消耗后解锁并通知全队。 */
    public static void handleUnlock(ServerPlayer player, String techId) {
        if (!SixtySecondsMod.isActive(player.level()) || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        SixtySecondsState.TeamData team = data.teams.get(SixtySecondsStatsComponent.KEY.get(player).teamId);
        TechNode node = byId(techId);
        if (team == null || node == null || team.unlockedTech.contains(techId)) {
            return;
        }
        if (node.parentId() != null && !team.unlockedTech.contains(node.parentId())) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.tech_parent_locked"), true);
            return;
        }
        Item scrap = org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_SCRAP;
        if (!consume(player, scrap, node.scrapCost())) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.tech_no_scrap", node.scrapCost()), true);
            return;
        }
        team.unlockedTech.add(techId);
        if ("defense".equals(techId)) {
            team.doorLevel = Math.min(3, team.doorLevel + 1);
        }
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
        Component name = Component.translatable("tech.noellesroles.sixty_seconds." + techId);
        for (UUID uuid : team.members) {
            if (level.getPlayerByUUID(uuid) instanceof ServerPlayer member) {
                member.displayClientMessage(Component.translatable(
                        "message.noellesroles.sixty_seconds.tech_unlocked",
                        player.getGameProfile().getName(), name).withStyle(ChatFormatting.GREEN), false);
            }
        }
        sendState(player); // 刷新界面
    }

    /** 统计并消耗背包中的指定物品；不足则不动返回 false。 */
    public static boolean consume(ServerPlayer player, Item item, int count) {
        int have = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                have += stack.getCount();
            }
        }
        if (have < count) {
            return false;
        }
        int left = count;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && left > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        return true;
    }
}
