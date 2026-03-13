package io.wifi.starrailexpress.command.argument;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import io.wifi.starrailexpress.cca.StarTrainWorldComponent;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.StringRepresentableArgument;
import net.minecraft.util.StringRepresentable;

public class TimeOfDayArgumentType extends StringRepresentableArgument<StarTrainWorldComponent.TimeOfDay> {
    private static final Codec<StarTrainWorldComponent.TimeOfDay> CODEC = StringRepresentable.fromEnumWithMapping(
            TimeOfDayArgumentType::getValues, name -> name.toLowerCase(Locale.ROOT)
    );

    private static StarTrainWorldComponent.TimeOfDay[] getValues() {
        return Arrays.stream(StarTrainWorldComponent.TimeOfDay.values()).toArray(StarTrainWorldComponent.TimeOfDay[]::new);
    }

    private TimeOfDayArgumentType() {
        super(CODEC, TimeOfDayArgumentType::getValues);
    }

    public static TimeOfDayArgumentType timeofday() {
        return new TimeOfDayArgumentType();
    }

    public static StarTrainWorldComponent.TimeOfDay getTimeofday(CommandContext<CommandSourceStack> context, String id) {
        return context.getArgument(id, StarTrainWorldComponent.TimeOfDay.class);
    }

    @Override
    protected String convertId(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
