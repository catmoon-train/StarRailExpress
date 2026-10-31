package org.agmas.noellesroles.game.halloween;

import io.wifi.starrailexpress.api.CustomWinnerRoleInterface;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 魂契复活后的玩家亡灵：独立职业、空手近战，胜利阵营在复活时锁定。 */
public final class HalloweenPactRole extends NormalRole implements CustomWinnerRoleInterface {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("noellesroles", "halloween_pact");
    private static SRERole instance;
    private static final ThreadLocal<Set<UUID>> WIN_GUARD = ThreadLocal.withInitial(HashSet::new);

    private HalloweenPactRole() {
        super(ID, 0xA780D9, false, false, MoodType.NONE, 160, true);
        setDefaultMax(0);
        setCanBeRandomedByOtherRoles(false);
        setCanSetSpawnInfoInConfig(false);
        setCanPickUpRevolver(false);
        setCanUseInstinctAndNightVision(false);
        setCanAutoAddMoney(false);
        setRoleData(Data::new);
    }

    /** 双端初始化阶段调用；不写入中央职业清单。 */
    public static void register() {
        if (instance == null) instance = TMMRoles.registerRole(new HalloweenPactRole())
                .setSpecialNeutral(true)
                .setAddedVersion("4.5");
    }

    public static SRERole role() { return instance; }

    public static boolean isPact(Player player) {
        return SREGameWorldComponent.KEY.get(player.level()).getRole(player) instanceof HalloweenPactRole;
    }

    /** 排除依附胜利职业，避免领袖、骷髅和魂契互相追随。 */
    static boolean canFollow(SRERole role) {
        if (role == null || role instanceof HalloweenPactRole) return false;
        String path = role.identifier().getPath();
        return !path.equals("leader") && !path.equals("skeleton");
    }

    @Override public boolean canBeRandomed() { return false; }
    @Override public boolean winWithKiller() { return false; }
    @Override public boolean winWithInnocent() { return false; }
    @Override public List<ShopEntry> getShopEntries() { return List.of(); }
    @Override public List<ItemStack> getDefaultItems() { return List.of(); }
    @Override public TrueFalseResult onPickUpItem(Player player, ItemStack item) { return TrueFalseResult.FALSE; }

    @Override
    public boolean didPlayerWin(ServerPlayer player, boolean original, GameUtils.WinStatus status) {
        Data data = RoleData.getNullable(Data.class, player);
        if (data == null || data.followedRole == null) return false;
        Set<UUID> guard = WIN_GUARD.get();
        if (!guard.add(player.getUUID())) return false;
        try {
            var end = SREGameRoundEndComponent.KEY.get(player.level());
            String path = data.followedRole.getPath();
            boolean base = switch (status) {
                case KILLERS -> data.killerWin;
                case TIME, PASSENGERS -> data.innocentWin;
                case GAMBLER -> path.equals("gambler");
                case LOOSE_END -> path.equals("loose_end");
                case NIAN_SHOU -> path.equals("nianshou");
                case RECORDER -> path.equals("recorder");
                case CUSTOM, CUSTOM_COMPONENT -> path.equals(end.CustomWinnerID)
                        || end.CustomWinnerExtraRoleIds != null && end.CustomWinnerExtraRoleIds.contains(path)
                        || end.CustomWinnerPlayers != null && end.CustomWinnerPlayers.contains(data.followedPlayer);
                case LOVERS -> end.CustomWinnerPlayers != null && end.CustomWinnerPlayers.contains(data.followedPlayer);
                default -> false;
            };
            SRERole allegiance = TMMRoles.getRole(data.followedRole);
            if (!canFollow(allegiance)) return base;
            var game = SREGameWorldComponent.KEY.get(player.level());
            // 同阵营包含同职业的中立 / 自定义胜利者，并非只认「好人或杀手」。
            for (ServerPlayer member : player.serverLevel().players()) {
                if (guard.contains(member.getUUID())) continue;
                SRERole current = game.getRole(member);
                if (current == null || !current.identifier().equals(data.followedRole)) continue;
                if ((status == GameUtils.WinStatus.CUSTOM || status == GameUtils.WinStatus.CUSTOM_COMPONENT)
                        && end.CustomWinnerPlayers != null && end.CustomWinnerPlayers.contains(member.getUUID())) return true;
                if (allegiance instanceof CustomWinnerRoleInterface custom
                        && custom.didPlayerWin(member, base, status)) return true;
            }
            return base;
        } finally {
            guard.remove(player.getUUID());
            if (guard.isEmpty()) WIN_GUARD.remove();
        }
    }

    /** 仅保留新职业所需的数据；旧职业数据由 changeRole 正常释放。 */
    public static final class Data extends SimpleRoleData {
        UUID followedPlayer;
        ResourceLocation followedRole;
        boolean killerWin;
        boolean innocentWin;
        long meleeReadyAt;

        public Data(RoleDataContext context) { super(context); }

        void follow(ServerPlayer nearest, SRERole allegiance) {
            followedPlayer = nearest.getUUID();
            followedRole = allegiance.identifier();
            killerWin = allegiance.winWithKiller();
            innocentWin = allegiance.winWithInnocent();
            sync();
        }

        @Override
        public void serverTick() {
            if (player instanceof ServerPlayer sp && HalloweenRuntime.isEnabled(sp.level())
                    && GameUtils.isPlayerAliveAndSurvival(sp) && sp.tickCount % 20 == 0) {
                // 本人皮肤保留，固定灵魂粒子 + 发光轮廓明确标识为亡灵。
                sp.serverLevel().sendParticles(ParticleTypes.SOUL, sp.getX(), sp.getY() + 1, sp.getZ(),
                        3, 0.25, 0.5, 0.25, 0.01);
            }
        }

        @Override public boolean shouldSyncWith(ServerPlayer other) { return other == player; }

        @Override
        public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
            if (followedPlayer != null) tag.putUUID("FollowedPlayer", followedPlayer);
            if (followedRole != null) tag.putString("FollowedRole", followedRole.toString());
            tag.putBoolean("KillerWin", killerWin);
            tag.putBoolean("InnocentWin", innocentWin);
        }

        @Override
        public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
            followedPlayer = tag.hasUUID("FollowedPlayer") ? tag.getUUID("FollowedPlayer") : null;
            followedRole = ResourceLocation.tryParse(tag.getString("FollowedRole"));
            killerWin = tag.getBoolean("KillerWin");
            innocentWin = tag.getBoolean("InnocentWin");
        }
    }
}
