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

package org.agmas.noellesroles.role_data.neutral;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 领袖（Leader）职业数据（新 CCA 存储方式）。
 *
 * <ul>
 * <li>{@link #skillUsed}：技能是否已释放（全局仅一次）</li>
 * <li>{@link #followers}：追随者 UUID 列表（对初学者释放时，场上其它初学者也会加入）</li>
 * <li>{@link #followerRoleIds}：追随者被招募时的职业 path，用于 HUD 显示职业名</li>
 * <li>{@link #followerNames}：追随者玩家名，用于 HUD 显示</li>
 * <li>{@link #hesitated}：是否已因「犹豫」死亡（避免重复触发）</li>
 * </ul>
 *
 * <p>
 * 仅在状态变化（释放技能、加入追随者、犹豫死亡）时 {@link #sync()}，绝不每 tick 同步。
 * </p>
 */
public class LeaderRoleData extends SimpleRoleData {

    /** 技能是否已释放（全局仅一次） */
    public boolean skillUsed = false;

    /** 追随者 UUID 列表 */
    public List<UUID> followers = new ArrayList<>();

    /** 追随者被招募时的职业 path（用于 HUD 显示职业名，追随者转型后仍显示招募时职业） */
    public List<String> followerRoleIds = new ArrayList<>();

    /** 追随者玩家名 */
    public List<String> followerNames = new ArrayList<>();

    /** 是否已因「犹豫」死亡（避免重复触发） */
    public boolean hesitated = false;

    /** 开局安全时间（tick）：安全时间内犹豫倒计时不下降 */
    public long safeTimeTicks = 0;

    public LeaderRoleData(RoleDataContext context) {
        super(context);
    }

    public boolean isFollower(UUID uuid) {
        return followers.contains(uuid);
    }

    /**
     * 添加追随者（含初学者联动：其它初学者也会加入）。
     *
     * @param follower   追随者
     * @param roleIdPath 追随者被招募时的职业 path
     */
    public void addFollower(ServerPlayer follower, String roleIdPath) {
        if (followers.contains(follower.getUUID())) {
            return;
        }
        followers.add(follower.getUUID());
        followerRoleIds.add(roleIdPath);
        followerNames.add(follower.getScoreboardName());
        sync();
    }

    /**
     * 标记技能已释放。
     */
    public void markSkillUsed() {
        if (!this.skillUsed) {
            this.skillUsed = true;
            this.sync();
        }
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
            return;
        }
        // 「犹豫」死亡：200 秒（4000 tick）内未释放技能；安全时间内倒计时不下降
        if (!skillUsed && !hesitated) {
            // 记录开局安全时间（SAFE_TIME 效果时长），仅记录一次
            if (safeTimeTicks <= 0 && serverPlayer.hasEffect(ModEffects.SAFE_TIME)) {
                safeTimeTicks = serverPlayer.getEffect(ModEffects.SAFE_TIME).getDuration();
                sync();
            }
            long elapsed = player.level().getGameTime()
                    - SREGameTimeComponent.KEY.get(player.level()).startWorldTick;
            long effectiveElapsed = Math.max(0, elapsed - safeTimeTicks);
            if (effectiveElapsed >= 200 * 20L) {
                hesitated = true;
                sync();
                GameUtils.killPlayer(serverPlayer, true, null, GameConstants.DeathReasons.HESITATION);
            }
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("skillUsed", skillUsed);
        tag.putBoolean("hesitated", hesitated);
        tag.putLong("safeTimeTicks", safeTimeTicks);
        ListTag followerList = new ListTag();
        for (UUID uid : followers) {
            followerList.add(StringTag.valueOf(uid.toString()));
        }
        tag.put("followers", followerList);
        ListTag roleIdList = new ListTag();
        for (String s : followerRoleIds) {
            roleIdList.add(StringTag.valueOf(s));
        }
        tag.put("followerRoleIds", roleIdList);
        ListTag nameList = new ListTag();
        for (String s : followerNames) {
            nameList.add(StringTag.valueOf(s));
        }
        tag.put("followerNames", nameList);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        skillUsed = tag.getBoolean("skillUsed");
        hesitated = tag.getBoolean("hesitated");
        safeTimeTicks = tag.contains("safeTimeTicks") ? tag.getLong("safeTimeTicks") : 0;
        followers.clear();
        ListTag followerList = tag.getList("followers", Tag.TAG_STRING);
        for (int i = 0; i < followerList.size(); i++) {
            try {
                followers.add(UUID.fromString(followerList.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        followerRoleIds.clear();
        ListTag roleIdList = tag.getList("followerRoleIds", Tag.TAG_STRING);
        for (int i = 0; i < roleIdList.size(); i++) {
            followerRoleIds.add(roleIdList.getString(i));
        }
        followerNames.clear();
        ListTag nameList = tag.getList("followerNames", Tag.TAG_STRING);
        for (int i = 0; i < nameList.size(); i++) {
            followerNames.add(nameList.getString(i));
        }
    }
}
