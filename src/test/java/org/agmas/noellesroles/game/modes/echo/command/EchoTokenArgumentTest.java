package org.agmas.noellesroles.game.modes.echo.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EchoTokenArgumentTest {
    @Test void fullMapAndCardIdsKeepFollowingArgument() throws Exception {
        var dispatcher=new CommandDispatcher<Object>();
        dispatcher.register(LiteralArgumentBuilder.<Object>literal("test")
                .then(RequiredArgumentBuilder.<Object,String>argument("id",EchoTokenArgument.token())
                        .then(RequiredArgumentBuilder.<Object,Integer>argument("n",IntegerArgumentType.integer())
                                .executes(c -> { assertTrue(c.getArgument("id",String.class).contains("/")); return IntegerArgumentType.getInteger(c,"n"); }))));
        assertEquals(7,dispatcher.execute("test season1/echo_station 7",new Object()));
        assertEquals(9,dispatcher.execute("test noellesroles:echo/sound_key 9",new Object()));
        assertThrows(CommandSyntaxException.class,() -> dispatcher.execute("test season1/echo_station7",new Object()));
    }
    @Test void wordTruncatesButTokenConsumesSlashAndColon() throws Exception {
        var word=new StringReader("noellesroles:echo/sound_key rest");
        assertEquals("noellesroles",StringArgumentType.word().parse(word)); assertEquals(':',word.peek());
        var token=new StringReader(word.getString()); assertEquals("noellesroles:echo/sound_key",EchoTokenArgument.token().parse(token)); assertEquals(' ',token.peek());
    }
    @Test void quotesEmptyAndBoundedInput() throws Exception {
        assertEquals("season1/echo_station",EchoTokenArgument.token().parse(new StringReader("\"season1/echo_station\"")));
        for(String value:new String[]{""," ","\"\"","a".repeat(257),"\"unterminated"}) assertThrows(CommandSyntaxException.class,() -> EchoTokenArgument.token().parse(new StringReader(value)));
    }
}
