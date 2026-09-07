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

package io.wifi.starrailexpress.sponsor;

import com.mojang.authlib.GameProfile;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.PlushApi;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.syncrequests.SyncRequests;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import org.agmas.noellesroles.init.SREFumoBlocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 赞助者名单管理（服务端）。
 * <p>
 * 从 {@link SREConfig#sponsorListUrl 云端 URL} 拉取一份纯文本名单（每行一个赞助者名，{@code #} 开头与空行忽略），
 * 按命名约定把名字映射到 plush（名字 {@code X} → {@code noellesroles:X_plush}，见 {@link PlushApi}）。
 * 有已注册 plush 时优先使用；没有时使用绑定赞助者玩家名的自定义玩家 plush。
 * <p>
 * 若存在赞助者，则玩家开局拿到的信封（intro 物品）会被
 * {@link #decorateIntroStack 替换为某个赞助者的 plush}（玩家本人优先，否则随机），
 * 该 plush 带 {@link SREDataComponentTypes#SPONSOR_INTRO} 标记，右键时打开游戏介绍 GUI 而非放置方块。
 * <p>
 * 名单会通过 {@link SponsorListPayload} 同步到客户端，供游戏介绍 GUI 列出赞助者 plush。
 */
public final class SponsorManager {

    /** 末尾可能附带的「老鼠赞助者」图标字符，匹配玩家名时需去掉。 */
    private static final char SUPPORTER_ICON = (char) 0xE780;

    /** 当前赞助者名（去重，保留名单中的大小写以供自定义玩家皮肤查询）。 */
    private static volatile List<String> sponsorPlushNames = List.of();

    private SponsorManager() {
    }

    /** 当前是否存在可用的赞助者。 */
    public static boolean hasSponsors() {
        return !sponsorPlushNames.isEmpty();
    }

    /** 返回当前赞助者 plush 名的快照副本。 */
    public static List<String> getSponsorPlushNames() {
        return new ArrayList<>(sponsorPlushNames);
    }

    /**
     * 异步从云端拉取赞助者名单，解析并同步到所有在线玩家。URL 为空时清空名单。
     */
    public static void fetchAsync(MinecraftServer server) {
        String url = SREConfig.instance().sponsorListUrl;
        if (url == null || url.isBlank()) {
            apply(server, List.of());
            return;
        }
        String trimmedUrl = url.trim();
        CompletableFuture.supplyAsync(() -> {
            try {
                return SyncRequests.sendGet(trimmedUrl);
            } catch (Exception e) {
                SRE.LOGGER.warn("[Sponsor] 拉取赞助者名单失败：{}", trimmedUrl, e);
                return null;
            }
        }).thenAccept(body -> {
            if (body == null) {
                return;
            }
            List<String> parsed = parse(body);
            // 回到服务器主线程再应用与同步
            server.execute(() -> apply(server, parsed));
        });
    }

    /** 把原始名单解析为去重后的名字列表，更新缓存并同步。 */
    private static void apply(MinecraftServer server, List<String> rawNames) {
        List<String> sponsors = new ArrayList<>();
        List<String> normalizedNames = new ArrayList<>();
        for (String raw : rawNames) {
            String displayName = raw == null ? "" : raw.trim();
            String normalizedName = normalize(displayName);
            if (normalizedName.isEmpty() || normalizedNames.contains(normalizedName)) {
                continue;
            }
            normalizedNames.add(normalizedName);
            sponsors.add(displayName);
        }
        sponsorPlushNames = List.copyOf(sponsors);
        if(rawNames.isEmpty()){
            return;
        }
        SRE.LOGGER.info("[Sponsor] 赞助者名单已更新：{} 个原始条目，{} 个有效赞助者。",
                rawNames.size(), sponsors.size());
        if (server != null) {
            syncToAll(server);
        }
    }

    /** 解析文本：按行拆分，去掉空行与 {@code #} 注释行。 */
    private static List<String> parse(String body) {
        List<String> result = new ArrayList<>();
        for (String line : body.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            result.add(trimmed);
        }
        return result;
    }

    private static String normalize(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }

    /** 去掉玩家名末尾可能的赞助者图标并规范化，用于和名单做「本人匹配」。 */
    private static String cleanPlayerName(String name) {
        if (name == null) {
            return "";
        }
        if (!name.isEmpty() && name.charAt(name.length() - 1) == SUPPORTER_ICON) {
            name = name.substring(0, name.length() - 1);
        }
        return normalize(name);
    }

    /**
     * 为该玩家挑选一个赞助者 plush 名：玩家本人在名单中则用本人，否则随机一个。
     *
     * @return 赞助者 plush 名；当前没有任何可用赞助者时返回 {@code null}
     */
    private static String pickPlushNameForPlayer(ServerPlayer player) {
        List<String> names = sponsorPlushNames;
        if (names.isEmpty()) {
            return null;
        }
        String self = cleanPlayerName(player.getName().getString());
        for (String s : names) {
            if (normalize(s).equals(self)) {
                return s;
            }
        }
        return names.get(player.getRandom().nextInt(names.size()));
    }

    /**
     * 若存在赞助者，则把开局信封替换为某个赞助者的 plush（保留信封的名称与描述，
     * 并打上 {@link SREDataComponentTypes#SPONSOR_INTRO} 标记）。否则原样返回信封。
     */
    public static ItemStack decorateIntroStack(ItemStack letter, ServerPlayer player) {
        String name = pickPlushNameForPlayer(player);
        if (name == null) {
            return letter;
        }
        ItemStack stack;
        Optional<Item> registeredPlush = PlushApi.getPlushForSkin(name);
        if (registeredPlush.isPresent()) {
            stack = new ItemStack(registeredPlush.get());
        } else {
            stack = new ItemStack(SREFumoBlocks.CUSTOM_PLAYER_PLUSH.asItem());
            // 只提供玩家名，让客户端的 ResolvableProfile 按名字查询该赞助者的皮肤。
            stack.set(DataComponents.PROFILE,
                    new ResolvableProfile(new GameProfile((UUID) null, name)));
        }
        // 沿用信封的名称与描述（信封由 LETTER_UpdateItemFunc 设置 ITEM_NAME 与 LORE）
        Component itemName = letter.get(DataComponents.ITEM_NAME);
        if (itemName != null) {
            stack.set(DataComponents.ITEM_NAME, itemName);
        }
        ItemLore lore = letter.get(DataComponents.LORE);
        if (lore != null) {
            stack.set(DataComponents.LORE, lore);
        }
        stack.set(SREDataComponentTypes.SPONSOR_INTRO, true);
        return stack;
    }

    /** 把当前名单同步给单个玩家。 */
    public static void syncTo(ServerPlayer player) {
        ServerPlayNetworking.send(player, new SponsorListPayload(getSponsorPlushNames()));
    }

    /** 把当前名单同步给所有在线玩家。 */
    public static void syncToAll(MinecraftServer server) {
        SponsorListPayload payload = new SponsorListPayload(getSponsorPlushNames());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }
    }
}
