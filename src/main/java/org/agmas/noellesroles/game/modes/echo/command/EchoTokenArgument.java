package org.agmas.noellesroles.game.modes.echo.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.List;

/** 完整空白 token，保留资源 ID 中的斜杠、冒号；具体白名单由业务层校验。 */
public final class EchoTokenArgument implements ArgumentType<String> {
    public static EchoTokenArgument token() { return new EchoTokenArgument(); }
    @Override public String parse(StringReader reader) throws CommandSyntaxException {
        int start=reader.getCursor();
        String value;
        if(reader.canRead() && StringReader.isQuotedStringStart(reader.peek())) value=reader.readQuotedString();
        else {
            while(reader.canRead() && !Character.isWhitespace(reader.peek())) reader.skip();
            value=reader.getString().substring(start,reader.getCursor());
        }
        if(value.isEmpty() || value.length()>256) {
            reader.setCursor(start);
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedStartOfQuote().createWithContext(reader);
        }
        return value;
    }
    @Override public Collection<String> getExamples() { return List.of("season1/echo_station","noellesroles:echo/sound_key"); }
}
