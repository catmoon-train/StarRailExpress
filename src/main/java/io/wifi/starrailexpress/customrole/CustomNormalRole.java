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

package io.wifi.starrailexpress.customrole;

import io.wifi.starrailexpress.api.CustomWinnerRoleInterface;
import io.wifi.starrailexpress.api.ExtraEffectRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.network.CustomRoleClientNetwork;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * 支持自定义商店和初始物品的自定义职业基类
 */
public class CustomNormalRole extends ExtraEffectRole implements CustomWinnerRoleInterface {
    private List<ShopEntry> customShop;
    private List<ItemStack> defaultItems = List.of();

    public CustomNormalRole(ResourceLocation id, int color, boolean isInnocent, boolean canUseKiller,
            SRERole.MoodType mood, int maxSprintTime, boolean canSeeTime,
            ArrayList<MobEffectInstance> effects, List<ShopEntry> shop) {
        super(id, color, isInnocent, canUseKiller, mood, maxSprintTime, canSeeTime, effects);
        this.customShop = shop;
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        if (customShop != null) {
            if (customShop.isEmpty()) {
                if (canUseKiller()) {
                    return ShopContent.getDefaultKnifeEntries();
                }
            }
            return customShop;
        }
        return super.getShopEntries();
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        if (defaultItems != null && !defaultItems.isEmpty())
            return defaultItems;
        return super.getDefaultItems();
    }

    public void setCustomShop(List<ShopEntry> shop) {
        this.customShop = shop;
    }

    public void setDefaultItems(List<ItemStack> items) {
        this.defaultItems = items;
    }

    private static String fixNewlines(String text) {
        return text.replace("\\n", "\n");
    }

    @Override
    public Component getName() {
        var id = this.identifier();
        var customData = CustomRoleLoader
                .getCustomRoleData(id.getPath());
        if (customData != null && !customData.displayName.isEmpty()) {
            return Component.literal(customData.displayName);
        }
        if (FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
            // 服务端不可用时，回退到客户端网络同步的数据
            customData = CustomRoleClientNetwork
                    .getSyncedRole(id.getPath());
            if (customData != null && !customData.displayName.isEmpty()) {
                return Component.literal(customData.displayName);
            }
        }
        return Component.literal(id.toString());
    }

    @Override
    public Component getDescription() {
        var id = this.identifier();
        var cd = CustomRoleLoader.getCustomRoleData(id.getPath());
        if (cd != null && !cd.description.isEmpty()) {
            return Component.literal(fixNewlines(cd.description));
        }
        if (FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
            cd = CustomRoleClientNetwork.getSyncedRole(id.getPath());
            if (cd != null && !cd.description.isEmpty()) {
                return Component.literal(fixNewlines(cd.description));
            }
        }
        return Component.literal(id.toString());

    }

    @Override
    public Component getSimpleDescription() {
        return getDescription();
    }

    @Override
    public boolean hasSimpleDescription() {
        return true;
    }

    @Override
    public Component getGoal() {
        var cd = CustomRoleLoader.getCustomRoleData(this.identifier().getPath());
        if (cd != null && !cd.goals.isEmpty())
            return Component.literal(cd.goals);
        return super.getGoal();
    }

    // ════════════════════════════════════════════════════════════════
    // 跟随获胜（参考黑白熊 / 雇佣兵 / 骷髅跟随召唤者）
    // 结算时由 SREMurderGameMode.isPlayerTheWinner 自动调用 didPlayerWin。
    // ════════════════════════════════════════════════════════════════

    @Override
    public boolean didPlayerWin(ServerPlayer player, boolean original, WinStatus winStatus) {
        if (winStatus == WinStatus.NONE || winStatus == WinStatus.NOT_MODIFY) {
            return original;
        }
        CustomRoleData cd = CustomRoleLoader.getCustomRoleData(this.identifier().getPath());
        if (cd == null || !cd.enableFollowWin) {
            return original;
        }
        String condition = normalizeOption(cd.followWinCondition, "UNCONDITIONAL");
        String faction = normalizeOption(cd.followWinFaction, "NONE");

        // 跟随条件：存活到最后 —— 结算时必须存活
        if ("SURVIVE_TO_END".equals(condition) && !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        // 含中立的最终结算：任何职业胜利，只要他还活着就算胜利
        if ("FINAL_INCL_NEUTRAL".equals(faction) && !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        SREGameRoundEndComponent roundEnd = SREGameRoundEndComponent.KEY.get(level);

        // 1) 跟随特定职业：填写的职业获胜他就跟随获胜
        if (cd.followWinRoleId != null && !cd.followWinRoleId.isBlank()
                && followedRoleWon(level, game, roundEnd, winStatus, cd.followWinRoleId)) {
            return true;
        }
        // 2) 跟随带特定修饰符的玩家获胜
        if (cd.followWinModifierId != null && !cd.followWinModifierId.isBlank()
                && modifierHolderWon(level, game, roundEnd, winStatus, cd.followWinModifierId)) {
            return true;
        }

        // 3) 跟随阵营（"在该阵营玩家周围"条件：周围 6 格内需有该阵营的存活玩家）
        boolean nearRequired = "NEAR_FACTION".equals(condition);
        switch (faction) {
            case "KILLER_ONLY" -> {
                if (!winStatus.isKillerWin()) {
                    return false;
                }
                return !nearRequired || hasNearbyFactionPlayer(level, game, player, SRERole::isKillerTeam);
            }
            case "INNOCENT_ONLY" -> {
                if (!winStatus.isInnocentWin()) {
                    return false;
                }
                return !nearRequired || hasNearbyFactionPlayer(level, game, player, SRERole::winWithInnocent);
            }
            case "FINAL_EXCL_NEUTRAL" -> {
                if (!(winStatus.isInnocentWin() || winStatus.isKillerWin())) {
                    return false;
                }
                return !nearRequired || hasNearbyFactionPlayer(level, game, player,
                        role -> (winStatus.isInnocentWin() && role.winWithInnocent())
                                || (winStatus.isKillerWin() && role.winWithKiller()));
            }
            case "FINAL_INCL_NEUTRAL" -> {
                // 任何职业胜利即可（存活要求见上方检查）
                return true;
            }
            default -> {
                // 未设置阵营且条件为"在该阵营玩家周围"时，退化为黑白熊行为：跟随最近存活玩家的阵营
                if (nearRequired) {
                    return nearestPlayerWon(level, game, player, winStatus);
                }
                return original;
            }
        }
    }

    /** 跟随特定职业是否获胜。 */
    private static boolean followedRoleWon(ServerLevel level, SREGameWorldComponent game,
            SREGameRoundEndComponent roundEnd, WinStatus winStatus, String roleIdInput) {
        // 内置特殊胜负（赌徒/年兽/记录者/漏网之鱼）：胜负状态本身对应特定职业
        String path = stripNamespace(roleIdInput).toLowerCase(Locale.ROOT);
        if ((winStatus == WinStatus.GAMBLER && path.equals("gambler"))
                || (winStatus == WinStatus.NIAN_SHOU && path.equals("nianshou"))
                || (winStatus == WinStatus.RECORDER && path.equals("recorder"))
                || (winStatus == WinStatus.LOOSE_END && path.equals("loose_end"))) {
            return true;
        }
        // 自定义独立胜利：结算方职业路径匹配（对齐 isPlayerTheWinner 的 CUSTOM 分支）
        if (winStatus == WinStatus.CUSTOM || winStatus == WinStatus.CUSTOM_COMPONENT) {
            if (roundEnd == null) {
                return false;
            }
            if (roundEnd.CustomWinnerID != null && roundEnd.CustomWinnerID.equals(path)) {
                return true;
            }
            return roundEnd.CustomWinnerExtraRoleIds != null
                    && roundEnd.CustomWinnerExtraRoleIds.contains(path);
        }
        // 常规阵营胜利：指定职业随该结果获胜（winWithKiller / winWithInnocent）
        for (ServerPlayer p : level.players()) {
            SRERole role = game.getRole(p);
            if (role == null || !roleIdMatches(role, roleIdInput)) {
                continue;
            }
            if (winStatus.isKillerWin() && role.winWithKiller()) {
                return true;
            }
            if (winStatus.isInnocentWin() && role.winWithInnocent()) {
                return true;
            }
        }
        return false;
    }

    /** 带特定修饰符的玩家是否获胜。 */
    private static boolean modifierHolderWon(ServerLevel level, SREGameWorldComponent game,
            SREGameRoundEndComponent roundEnd, WinStatus winStatus, String modifierIdInput) {
        SREModifier modifier = HMLModifiers.getModifier(resolveId(modifierIdInput));
        if (modifier == null) {
            return false;
        }
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(level);
        if (modifiers == null) {
            return false;
        }
        for (ServerPlayer p : level.players()) {
            if (!modifiers.isModifier(p, modifier)) {
                continue;
            }
            // 自定义独立胜利 / 恋人等：获胜玩家在 CustomWinnerPlayers 名单中
            if ((winStatus == WinStatus.CUSTOM || winStatus == WinStatus.CUSTOM_COMPONENT
                    || winStatus == WinStatus.LOVERS)
                    && roundEnd != null && roundEnd.CustomWinnerPlayers != null
                    && roundEnd.CustomWinnerPlayers.contains(p.getUUID())) {
                return true;
            }
            SRERole role = game.getRole(p);
            if (role == null) {
                continue;
            }
            if (winStatus.isKillerWin() && role.winWithKiller()) {
                return true;
            }
            if (winStatus.isInnocentWin() && role.winWithInnocent()) {
                return true;
            }
        }
        return false;
    }

    /** 周围 radius 格内是否存在满足条件的存活玩家（"在该阵营玩家周围"，黑白同款 6 格）。 */
    private static boolean hasNearbyFactionPlayer(ServerLevel level, SREGameWorldComponent game,
            ServerPlayer player, Predicate<SRERole> match) {
        double radiusSq = 6 * 6;
        for (ServerPlayer p : level.players()) {
            if (p.getUUID().equals(player.getUUID()) || !GameUtils.isPlayerAliveAndSurvival(p)) {
                continue;
            }
            if (p.distanceToSqr(player) > radiusSq) {
                continue;
            }
            SRERole role = game.getRole(p);
            if (role != null && match.test(role)) {
                return true;
            }
        }
        return false;
    }

    /** 黑白熊同款：跟随最近存活玩家的阵营获胜。 */
    private static boolean nearestPlayerWon(ServerLevel level, SREGameWorldComponent game,
            ServerPlayer player, WinStatus winStatus) {
        ServerPlayer nearest = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            if (p.getUUID().equals(player.getUUID()) || !GameUtils.isPlayerAliveAndSurvival(p)) {
                continue;
            }
            double d = p.distanceToSqr(player);
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        if (nearest == null) {
            return false;
        }
        SRERole role = game.getRole(nearest);
        if (role == null) {
            return false;
        }
        if (role.isInnocent() && winStatus.isInnocentWin()) {
            return true;
        }
        return role.isKiller() && winStatus.isKillerWin();
    }

    private static boolean roleIdMatches(SRERole role, String configuredId) {
        if (configuredId == null) {
            return false;
        }
        String id = configuredId.trim();
        if (id.isEmpty()) {
            return false;
        }
        ResourceLocation actual = role.identifier();
        return id.indexOf(':') >= 0
                ? actual.toString().equalsIgnoreCase(id)
                : actual.getPath().equalsIgnoreCase(id);
    }

    private static String stripNamespace(String id) {
        if (id == null) {
            return "";
        }
        String trimmed = id.trim();
        int idx = trimmed.indexOf(':');
        return idx >= 0 ? trimmed.substring(idx + 1) : trimmed;
    }

    /** 解析 id：优先按 命名空间:路径 解析，无命名空间时回退到 noellesroles 命名空间。 */
    private static ResourceLocation resolveId(String input) {
        String id = input == null ? "" : input.trim();
        if (id.isEmpty()) {
            return null;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl != null) {
            return rl;
        }
        return ResourceLocation.tryParse("noellesroles:" + id);
    }

    private static String normalizeOption(String value, String def) {
        return value == null || value.isBlank() ? def : value.trim().toUpperCase(Locale.ROOT);
    }
}
