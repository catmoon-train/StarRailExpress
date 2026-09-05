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

package org.agmas.noellesroles.game.roles.innocence.great_detective;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.content.item.KnifeItem;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.HeldLikeBat;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.HeldLikeRevolver;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.forensic.ForensicCategory;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.game.roles.innocence.great_detective.DetectiveClue.ClueType;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role_data.innocence.GreatDetectiveRoleData;
import org.agmas.noellesroles.role_data.innocence.GreatDetectiveRoleData.DeathNote;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 大侦探（平民阵营）。
 *
 * <p>对着尸体右键开始短时勘察（需静止）。完成后记下死亡时间，并随机写入一条凶手碎片线索。
 * 推理之书按凶手分页；线索 ≥ 3 条时可选择查明方位或生死（每名凶手一次）。
 * 对亡语杀手伪装尸体在勘察开始时揭穿。
 */
public class GreatDetectiveRole extends NormalRole {

    public GreatDetectiveRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean hideScoreboard) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, hideScoreboard);
    }

    @Override
    public InteractionResult rightClickEntity(Player player, Entity victim) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        GreatDetectiveRoleData comp = RoleData.getNullable(GreatDetectiveRoleData.class, serverPlayer);
        if (comp == null) {
            return InteractionResult.PASS;
        }
        return comp.tryStartChannel(serverPlayer, victim);
    }

    /** 施法完成后结算勘察结果。 */
    public static void finishInvestigation(ServerPlayer serverPlayer, GreatDetectiveRoleData comp,
            PlayerBodyEntity body) {
        Level level = serverPlayer.level();
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
        if (gameWorld == null || !gameWorld.isRunning()) {
            return;
        }

        UUID corpseUuid = body.getUUID();
        comp.markCorpseUsed(corpseUuid);
        comp.enterCooldown();

        Component deadName = resolveDeadName(serverPlayer, body);
        int deathSeconds = Math.max(0, body.tickCount / 20);
        Component formattedTime = DetectiveFlavor.formatTime(deathSeconds);
        UUID salt = corpseUuid;

        UUID killerUuid = body.getKillerUuid();
        if (killerUuid == null) {
            comp.sync();
            broadcast(serverPlayer, DetectiveFlavor.noKiller(salt, deadName, formattedTime).withStyle(ChatFormatting.GRAY));
            return;
        }

        int beforeCount = comp.clueCount(killerUuid);
        comp.addDeathNote(killerUuid, new DeathNote(deadName.getString(), deathSeconds));

        List<DetectiveClue> candidates = buildCandidates(serverPlayer, gameWorld, killerUuid, body);
        candidates.removeIf(c -> comp.hasClue(killerUuid, c));
        if (candidates.isEmpty()) {
            ensureBook(serverPlayer);
            comp.sync();
            broadcast(serverPlayer, DetectiveFlavor.noNewClue(salt, deadName, formattedTime).withStyle(ChatFormatting.GRAY));
            return;
        }

        DetectiveClue chosen = candidates.get(level.getRandom().nextInt(candidates.size()));
        comp.addClue(killerUuid, chosen);
        ensureBook(serverPlayer);
        comp.playCompleteFx(serverPlayer);
        comp.sync();

        MutableComponent result = DetectiveFlavor.deathTime(salt, deadName, formattedTime)
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("\n"))
                .append(DetectiveFlavor.clueLine(chosen).withStyle(ChatFormatting.GOLD));
        int afterCount = comp.clueCount(killerUuid);
        if (beforeCount < 3 && afterCount >= 3 && !comp.hasTargetReveal(killerUuid)) {
            result.append(Component.literal("\n"))
                    .append(DetectiveFlavor.enoughClues(killerUuid).withStyle(ChatFormatting.AQUA));
        }
        broadcast(serverPlayer, result);
    }

    private static void broadcast(ServerPlayer player, Component message) {
        SRENetworkMessageUtils.sendBroadcast(player, message);
    }

    /** 根据尸体与凶手当前状态，构建本次可获得的候选线索。 */
    private static List<DetectiveClue> buildCandidates(ServerPlayer detective, SREGameWorldComponent gameWorld,
            UUID killerUuid, PlayerBodyEntity body) {
        List<DetectiveClue> list = new ArrayList<>();
        Level level = detective.level();

        ForensicCategory cat = ForensicCategory.fromDeathReason(ResourceLocation.tryParse(body.getDeathReason()));
        if (cat != ForensicCategory.UNKNOWN) {
            list.add(new DetectiveClue(ClueType.WEAPON, cat.name()));
        }

        SRERole role = gameWorld.getRole(killerUuid);
        if (role != null) {
            list.add(new DetectiveClue(ClueType.ROLE, role.identifier().toString()));
            list.add(new DetectiveClue(ClueType.FACTION, factionOf(role)));
        }

        WorldModifierComponent wmc = WorldModifierComponent.KEY.get(level);
        if (wmc != null) {
            for (SREModifier mod : wmc.getModifiers(killerUuid)) {
                if (mod != null) {
                    list.add(new DetectiveClue(ClueType.MODIFIER, mod.identifier().toString()));
                }
            }
        }

        if (level instanceof ServerLevel sl) {
            int kills = 0;
            for (PlayerBodyEntity other : sl.getEntities(TMMEntities.PLAYER_BODY, e -> true)) {
                if (killerUuid.equals(other.getKillerUuid())) {
                    kills++;
                }
            }
            if (kills > 0) {
                list.add(new DetectiveClue(ClueType.KILLS, String.valueOf(kills)));
            }
        }

        Player killer = level.getPlayerByUUID(killerUuid);
        if (killer != null) {
            String fragment = pickNameFragment(killer.getName().getString(), level.getRandom());
            if (fragment != null && !fragment.isEmpty()) {
                list.add(new DetectiveClue(ClueType.NAME, fragment));
            }
            int room = computeRoom(killer);
            if (room > 0) {
                list.add(new DetectiveClue(ClueType.ROOM, String.valueOf(room)));
            }
            list.add(new DetectiveClue(ClueType.HELD, heldCategory(killer.getMainHandItem())));
            list.add(new DetectiveClue(ClueType.GOLD, goldBand(killer)));
            list.add(new DetectiveClue(ClueType.NEARBY, String.valueOf(nearbyCount(killer))));
            String floor = floorOf(killer);
            if (floor != null) {
                list.add(new DetectiveClue(ClueType.FLOOR, floor));
            }
            list.add(new DetectiveClue(ClueType.GUN, hasGun(killer) ? "yes" : "no"));
            list.add(new DetectiveClue(ClueType.RANGE, rangeBand(detective, killer)));
        }

        return list;
    }

    private static String factionOf(SRERole role) {
        if (role.isInnocent()) {
            return "civilian";
        }
        if (role.isNeutrals()) {
            return "neutral";
        }
        return "killer";
    }

    private static String heldCategory(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        if (stack.getItem() instanceof KnifeItem) {
            return "blade";
        }
        if (stack.getItem() instanceof HeldLikeRevolver) {
            return "firearm";
        }
        if (stack.getItem() instanceof HeldLikeBat) {
            return "blunt";
        }
        return "other";
    }

    private static String goldBand(Player killer) {
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(killer);
        int coins = shop == null ? 0 : shop.balance;
        if (coins < 50) {
            return "empty";
        }
        if (coins < 150) {
            return "low";
        }
        if (coins < 300) {
            return "mid";
        }
        return "high";
    }

    private static int nearbyCount(Player killer) {
        AABB box = killer.getBoundingBox().inflate(8.0);
        return killer.level().getEntitiesOfClass(Player.class, box,
                p -> p != killer && GameUtils.isPlayerAliveAndSurvival(p)).size();
    }

    private static String floorOf(Player killer) {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(killer.level());
        if (areas == null) {
            return null;
        }
        AABB play = areas.getPlayArea();
        if (play == null) {
            return null;
        }
        double height = play.maxY - play.minY;
        if (height < 4.0) {
            return null;
        }
        double mid = (play.minY + play.maxY) / 2.0;
        return killer.getY() >= mid ? "upper" : "lower";
    }

    private static boolean hasGun(Player killer) {
        for (var compartment : killer.getInventory().compartments) {
            for (ItemStack stack : compartment) {
                if (!stack.isEmpty() && stack.getItem() instanceof HeldLikeRevolver) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String rangeBand(Player detective, Player killer) {
        double d = detective.distanceTo(killer);
        if (d < 16.0) {
            return "close";
        }
        if (d < 48.0) {
            return "mid";
        }
        return "far";
    }

    /** 从名字中随机取一个 1 个字的片段。 */
    private static String pickNameFragment(String name, RandomSource random) {
        if (name == null) {
            return null;
        }
        name = name.trim();
        if (name.isEmpty()) {
            return null;
        }
        if (name.length() <= 1) {
            return name;
        }
        int len = 1;
        int start = random.nextInt(name.length() - len + 1);
        return name.substring(start, start + len);
    }

    /** 依据游玩区域沿长轴等分 roomCount 段，计算凶手所在的房间/车厢号（1 起）。 */
    private static int computeRoom(Player killer) {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(killer.level());
        if (areas == null) {
            return -1;
        }
        AABB play = areas.getPlayArea();
        if (play == null) {
            return -1;
        }
        int roomCount = Math.max(1, areas.getRoomCount());
        double lenX = play.maxX - play.minX;
        double lenZ = play.maxZ - play.minZ;
        double pos;
        double min;
        double len;
        if (lenX >= lenZ) {
            pos = killer.getX();
            min = play.minX;
            len = lenX;
        } else {
            pos = killer.getZ();
            min = play.minZ;
            len = lenZ;
        }
        if (len <= 0) {
            return 1;
        }
        double t = (pos - min) / len;
        t = Math.max(0.0, Math.min(0.999999, t));
        return (int) (t * roomCount) + 1;
    }

    /** 若身上没有推理之书则补发一本。 */
    public static void ensureBook(ServerPlayer player) {
        for (var compartment : player.getInventory().compartments) {
            for (ItemStack stack : compartment) {
                if (stack.is(ModItems.DEDUCTION_BOOK)) {
                    return;
                }
            }
        }
        ItemStack book = new ItemStack(ModItems.DEDUCTION_BOOK);
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
    }

    private static Component resolveDeadName(ServerPlayer sp, PlayerBodyEntity body) {
        UUID id = body.getPlayerUuid();
        if (id != null && sp.getServer() != null) {
            ServerPlayer dead = sp.getServer().getPlayerList().getPlayer(id);
            if (dead != null) {
                return dead.getDisplayName();
            }
        }
        if (body.getCustomName() != null) {
            return body.getCustomName();
        }
        return Component.translatable("message.noellesroles.great_detective.unknown");
    }
}
