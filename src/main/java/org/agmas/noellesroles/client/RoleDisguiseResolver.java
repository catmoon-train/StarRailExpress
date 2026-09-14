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

package org.agmas.noellesroles.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.player.AbstractClientPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「这名玩家现在被伪装成什么」的客户端判定缓存。
 * <p>
 * 判定本身要查职业数据 / 修饰符（几次组件查找），不该每帧做；但形态是会被临时切换的
 * （猪能变回人、幻灵能切形态、修饰符能加能删），所以也不能只在职业变更事件里算一次。
 * 折中是<b>按 tick 打戳</b>：同一 tick 内重复询问只是查表，跨 tick 才重新探测，
 * 于是形态变化最迟下一个 tick（50ms）生效。
 * <p>
 * 这里刻意不用 {@code System.currentTimeMillis()} 那套时间节流：读墙钟既比读一个 tick 计数贵，
 * 又会让形态变化最多滞后一整个节流窗口（原来 200ms = 4 个 tick），而且同一份缓存原本在两个 mixin
 * 里各写了一遍。
 * <p>
 * 判定顺序与原实现一致：熊猫 &gt; 猪 &gt; 兔 &gt; 番茄 &gt; 悦灵（原实现把五个布尔全算出来再按这个顺序用，
 * 这里只是把「算」提前到每 tick 一次）；同一名玩家不可能同时持有两种职业形态，唯一可能叠加的是
 * 职业形态 + {@code RABBIT_SHAPE} 修饰符，那种情况下两者都取更高优先级的那个。
 */
@Environment(EnvType.CLIENT)
public final class RoleDisguiseResolver {

    /** 一名玩家在当前 tick 的伪装判定。字段公开，供渲染 mixin 直接读。 */
    public static final class Flags {
        /** 这份判定是在哪个 tick 算出来的；{@code -1} 表示还没算过。 */
        public int tick = -1;
        public boolean pig;
        public boolean rabbit;
        public boolean tomato;
        public boolean allay;
        public boolean panda;

        /**
         * 第一人称下是否也渲染自己（决定原版「跳过相机所在实体」要不要被谎报掉）。
         * <p>
         * 与原来的自见 mixin 保持一致：熊猫不在其列，所以这里也不含 panda。
         */
        public boolean anyForFirstPersonSelfView() {
            return pig || rabbit || allay || tomato;
        }
    }

    private static final Map<UUID, Flags> CACHE = new ConcurrentHashMap<>();

    static {
        // 打戳本身已经能让旧条目在下次询问时自愈，这里只是别让 UUID 表随会话无限增长。
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    private RoleDisguiseResolver() {
    }

    /** 取当前 tick 的判定结果（每 tick 至多探测一次）。 */
    public static Flags resolve(AbstractClientPlayer player) {
        Flags flags = CACHE.computeIfAbsent(player.getUUID(), id -> new Flags());
        int tick = player.tickCount;
        if (flags.tick == tick) {
            return flags;
        }
        flags.tick = tick;
        flags.panda = PandaDisguiseRenderer.shouldDisguise(player);
        flags.pig = LeatherPigDisguiseRenderer.shouldDisguise(player);
        flags.rabbit = RabbitDisguiseRenderer.shouldDisguise(player);
        flags.tomato = TomatoHeadDisguiseRenderer.shouldDisguise(player);
        flags.allay = AllayDisguiseRenderer.shouldDisguise(player);
        return flags;
    }

    public static void clear() {
        CACHE.clear();
    }
}
