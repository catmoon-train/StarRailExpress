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

package io.wifi.starrailexpress.content.command.argument;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import io.wifi.starrailexpress.cca.SRETrainWorldComponent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.StringRepresentableArgument;
import net.minecraft.util.StringRepresentable;

import java.util.Arrays;
import java.util.Locale;

public class TimeOfDayArgumentType extends StringRepresentableArgument<SRETrainWorldComponent.TimeOfDay> {
    private static final Codec<SRETrainWorldComponent.TimeOfDay> CODEC = StringRepresentable.fromEnumWithMapping(
            TimeOfDayArgumentType::getValues, name -> name.toLowerCase(Locale.ROOT)
    );

    private static SRETrainWorldComponent.TimeOfDay[] getValues() {
        return Arrays.stream(SRETrainWorldComponent.TimeOfDay.values()).toArray(SRETrainWorldComponent.TimeOfDay[]::new);
    }

    private TimeOfDayArgumentType() {
        super(CODEC, TimeOfDayArgumentType::getValues);
    }

    public static TimeOfDayArgumentType timeofday() {
        return new TimeOfDayArgumentType();
    }

    public static SRETrainWorldComponent.TimeOfDay getTimeofday(CommandContext<CommandSourceStack> context, String id) {
        return context.getArgument(id, SRETrainWorldComponent.TimeOfDay.class);
    }

    @Override
    protected String convertId(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
