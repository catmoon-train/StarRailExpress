package io.wifi.starrailexpress.compat.chameleon;

import com.mecchachameleon.game.Room;
import com.mecchachameleon.game.Rooms;
import com.mecchachameleon.item.ArmorPaintHandler;
import com.mecchachameleon.item.MecchaItems;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.mixin.compat.mecchachameleon.RoomAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 把 SRE 的一局游戏“伪装成”一个变色龙房间。
 *
 * <p>
 * 变色龙 mod 的所有机制（涂装、方块伪装、匍匐、霰弹枪、隐藏名牌）都只认一件事：
 * {@code Rooms.MEMBER_ROOM} 里有没有这个玩家。所以这里只造一个 {@link Room}，
 * 塞进 {@code MEMBER_ROOM}，就能让机制生效。
 * </p>
 *
 * <p>
 * 关键取舍：
 * </p>
 * <ul>
 * <li>房间<b>不</b>注册进 {@code Rooms.ROOMS}，因此变色龙自己的 {@code Room.tick}
 * （阶段计时、竞技场边界、淘汰、记分板）永远不会跑到它头上，回合完全由 SRE 主导。</li>
 * <li>{@code phase} 直接设为 {@code SEEKING}，霰弹枪的弹药与冷却（{@code Room.canShoot} /
 * {@code Room.onShot}）才会生效。</li>
 * <li>{@code hiders} 故意<b>留空</b>。变色龙的 {@code AttackEntityCallback} 一旦发现
 * “seeker 近战打到 hider”，就会调用 {@code Rooms.found} 并取消这次攻击——那会顶掉 SRE 自己的刀 /
 * 枪。留空后近战走 SRE 原本的流程；霰弹枪则由 {@code ShotgunItemMixin} 把它那次
 * {@code isHider} 调用重定向到 SRE 的职业判定。</li>
 * </ul>
 */
final class ChameleonRoomBridge {
    private static final String ROOM_NAME = "sre_chameleon";

    /** {@code Room} 的第三个构造参数是变色龙内部的自增序号，只用于生成计分板队伍名。 */
    private static final int ROOM_SEQ = 30000;

    private static Room room;
    private static final Set<UUID> members = new HashSet<>();

    private ChameleonRoomBridge() {
    }

    static boolean isActive() {
        return room != null;
    }

    /**
     * {@code Rooms.MEMBER_ROOM} 是私有静态字段。这里只取一次引用后原地增删，不重新赋值。
     * 取不到就返回 null，调用方据此放弃接管（伪装机制失效，但不会影响正常游戏）。
     */
    private static Map<UUID, Room> memberRoom() {
        try {
            Field field = Rooms.class.getDeclaredField("MEMBER_ROOM");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, Room> map = (Map<UUID, Room>) field.get(null);
            return map;
        } catch (ReflectiveOperationException e) {
            SRE.LOGGER.error("无法访问变色龙的 Rooms.MEMBER_ROOM，变色龙模式的伪装机制将不可用", e);
            return null;
        }
    }

    static void open(List<ServerPlayer> players, Collection<UUID> hunterIds, int ammoLimit, int shotCooldown) {
        MinecraftServer server = players.isEmpty() ? null : players.get(0).getServer();
        close(server);
        if (players.isEmpty()) {
            return;
        }

        Map<UUID, Room> memberRoom = memberRoom();
        if (memberRoom == null) {
            return;
        }

        Room created = newRoom();
        if (created == null) {
            return;
        }

        RoomAccessor access = (RoomAccessor) (Object) created;
        access.setPhase(Room.Phase.SEEKING);

        Room.Config config = created.config();
        config.blockDisguise = 1;
        config.ammoLimit = ammoLimit;
        config.shotCooldown = shotCooldown;
        config.manualRoles = 1;
        config.maxRoom = Math.max(2, players.size());

        for (ServerPlayer player : players) {
            UUID id = player.getUUID();
            access.getRoster().add(id);
            if (hunterIds.contains(id)) {
                access.getSeekers().add(id);
            }
            memberRoom.put(id, created);
            members.add(id);
        }

        room = created;

        // 衣服是分配职业时穿上的，那时房间还不存在，updateShrink 会把所有人都当成躲藏者缩小。
        // 房间就位后重算一次，猎人才能恢复正常体型。
        for (ServerPlayer player : players) {
            ArmorPaintHandler.updateShrink(player);
        }
    }

    static void close(MinecraftServer server) {
        if (room == null) {
            members.clear();
            return;
        }

        Map<UUID, Room> memberRoom = memberRoom();
        for (UUID id : members) {
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(id);
            if (player != null) {
                // 先退出方块形态，才能把开局前被暂存的头盔还回去。
                Rooms.leaveBlockPose(player);
                MecchaItems.stripRoomGear(player);
                // 衣服脱了，把缩小属性一并还原。
                ArmorPaintHandler.updateShrink(player);
            }
            Rooms.clearBlockMemory(id);
            if (memberRoom != null) {
                memberRoom.remove(id);
            }
        }

        members.clear();
        room = null;
    }

    static boolean owns(Object candidate) {
        return room != null && room == candidate;
    }

    static void exitDisguise(ServerPlayer player) {
        Rooms.leaveBlockPose(player);
    }

    static void equipGear(ServerPlayer player) {
        player.setItemSlot(EquipmentSlot.HEAD, MecchaItems.roomGear(MecchaItems.CHAMELEON_HELMET.get()));
        player.setItemSlot(EquipmentSlot.CHEST, MecchaItems.roomGear(MecchaItems.CHAMELEON_CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, MecchaItems.roomGear(MecchaItems.CHAMELEON_LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, MecchaItems.roomGear(MecchaItems.CHAMELEON_BOOTS.get()));
    }

    static List<ItemStack> hunterItems() {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(MecchaItems.SHOTGUN.get()));
        return items;
    }

    /** {@code Room} 的构造器是包私有的，变色龙也没有公开的工厂，只能反射。 */
    private static Room newRoom() {
        try {
            Constructor<Room> constructor = Room.class.getDeclaredConstructor(String.class, String.class, int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(ROOM_NAME, null, ROOM_SEQ);
        } catch (ReflectiveOperationException e) {
            SRE.LOGGER.error("无法创建变色龙房间，变色龙模式的伪装机制将不可用", e);
            return null;
        }
    }
}
