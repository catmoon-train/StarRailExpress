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

package io.wifi.starrailexpress.mixin.command;

import com.google.common.collect.ImmutableList;
import io.wifi.starrailexpress.content.command.data.DisguiseDataProvider;
import net.minecraft.server.commands.data.DataCommands;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Function;

/**
 * 把伪装 NBT 注册成原版 {@code /data} 与 {@code /execute if data} 的数据源。
 * <p>
 * 原版这三张表是 {@code ImmutableList}（静态初始化时用 {@code ImmutableList.of(...)} 建好、
 * 派生表用 {@code toImmutableList()} 再生成），所以只能替换不能追加——这里用
 * {@code @Mutable @Shadow} 在类初始化末尾把它们换成带上本数据源的新表。
 * <p>
 * <b>为什么注入 {@code <clinit>} 而不是 {@code register}</b>：原版 {@code Commands} 构造时先注册
 * {@code ExecuteCommand}（它建树时会读 {@code SOURCE_PROVIDERS}）、后注册 {@code DataCommands}，
 * 所以在 {@code register} 里加就已经晚了——{@code /execute if data sre:disguise} 会缺这一支。
 * 而读那两张表正好触发 {@link DataCommands} 的类初始化，因此注入 {@code <clinit>} 末尾能保证
 * 两个命令树都拿到含本数据源的表。用 {@code TAIL} 而不是 {@code RETURN}：类初始化里三张表的赋值都在
 * 最后，取「方法最后一个指令」才一定在它们之后（`RETURN` 会在每个 return 处都插一次）。
 */
@Mixin(DataCommands.class)
public abstract class DataCommandsMixin {

    @Mutable
    @Shadow
    @Final
    private static List<Function<String, DataCommands.DataProvider>> ALL_PROVIDERS;

    @Mutable
    @Shadow
    @Final
    private static List<DataCommands.DataProvider> TARGET_PROVIDERS;

    @Mutable
    @Shadow
    @Final
    private static List<DataCommands.DataProvider> SOURCE_PROVIDERS;

    /** 类初始化只会跑一次，这个标记是给「反复调用 register」这种意外情况兜底的。 */
    @Unique
    private static boolean sre$disguiseProviderInstalled = false;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void sre$installDisguiseProvider(CallbackInfo ci) {
        if (sre$disguiseProviderInstalled) {
            return;
        }
        sre$disguiseProviderInstalled = true;
        ALL_PROVIDERS = ImmutableList.<Function<String, DataCommands.DataProvider>>builder()
                .addAll(ALL_PROVIDERS)
                .add(DisguiseDataProvider.PROVIDER)
                .build();
        // 与原版的派生方式一致：写入目标用 "target"、NBT 来源用 "source" 作为参数名。
        TARGET_PROVIDERS = ALL_PROVIDERS.stream().map(provider -> provider.apply("target")).toList();
        SOURCE_PROVIDERS = ALL_PROVIDERS.stream().map(provider -> provider.apply("source")).toList();
    }
}
