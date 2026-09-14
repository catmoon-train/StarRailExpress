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

package io.wifi.starrailexpress.content.command.data;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.wifi.starrailexpress.disguise.EntityDisguise;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.commands.data.DataAccessor;

import java.util.Locale;

/**
 * 伪装 NBT 的读写口，交给原版 {@code /data} / {@code /execute if data} 使用。
 * <p>
 * 读的是**已保存的外观 NBT**（也就是同步给客户端、决定伪装长相的那份），
 * 写回时只做清洗（去掉坐标 / 生存状态 / AI 记忆等不该跟着伪装走的键）并重算眼高，
 * 然后走和 {@code /sre:disguise} 一样的同步链路（合批广播 + refreshDimensions），
 * 结束条件（时长 / predicate）保持不变。
 * <p>
 * 与玩家本体 NBT 无关：原版 {@code EntityDataAccessor} 出于安全禁止改玩家数据
 * （{@code commands.data.entity.invalid}），而这里的伪装 NBT 是模组自己的存储，改它不会有
 * 「把玩家 NBT 改坏」的风险，所以允许写入——这也是本数据源存在的意义。
 */
public final class DisguiseDataAccessor implements DataAccessor {

    private static final SimpleCommandExceptionType ERROR_NOT_DISGUISED = new SimpleCommandExceptionType(
            Component.translatable("commands.sre.entitydisguise.data.invalid"));

    private final ServerPlayer player;

    public DisguiseDataAccessor(ServerPlayer player) {
        this.player = player;
    }

    @Override
    public void setData(CompoundTag tag) throws CommandSyntaxException {
        if (EntityDisguise.get(this.player).isNone()) {
            // 没有伪装就没有可变的东西；要建伪装得用 /sre:disguise start（NBT 里没有实体类型）。
            throw ERROR_NOT_DISGUISED.create();
        }
        EntityDisguise.setNbt(this.player, tag);
    }

    @Override
    public CompoundTag getData() {
        // 返回副本：/data modify 会拿这份做路径运算，不能让它直接改到内部状态。
        CompoundTag nbt = EntityDisguise.get(this.player).nbt();
        return nbt == null ? new CompoundTag() : nbt.copy();
    }

    @Override
    public Component getModifiedSuccess() {
        return Component.translatable("commands.sre.entitydisguise.data.modified",
                this.player.getDisplayName());
    }

    @Override
    public Component getPrintSuccess(Tag tag) {
        return Component.translatable("commands.sre.entitydisguise.data.query",
                this.player.getDisplayName(), NbtUtils.toPrettyComponent(tag));
    }

    @Override
    public Component getPrintSuccess(NbtPathArgument.NbtPath path, double scale, int result) {
        return Component.translatable("commands.sre.entitydisguise.data.get", path.asString(),
                this.player.getDisplayName(), String.format(Locale.ROOT, "%.2f", scale), result);
    }
}
