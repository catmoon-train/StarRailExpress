package org.agmas.noellesroles.game.modes.echo.cards;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EchoCardAssetsTest {
    @Test void everyRegisteredCardHasDistinctReadableSquareIcon() throws Exception {
        var hashes=new HashSet<String>();
        for(var card:EchoCardRegistry.all()) {
            String[] icon=card.icon().split(":",2);
            Path path=Path.of("src/main/resources/assets",icon[0],icon[1]);
            assertTrue(Files.isRegularFile(path),card.id());
            var image=ImageIO.read(path.toFile()); assertNotNull(image,card.id());
            assertEquals(64,image.getWidth(),card.id()); assertEquals(64,image.getHeight(),card.id());
            assertTrue(image.getColorModel().hasAlpha(),card.id());
            assertTrue(hashes.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)))),card.id());
        }
        assertEquals(37,hashes.size());
    }
    @Test void toolsHaveTheirOwnReadableTexturesAndModelsReferenceThem() throws Exception {
        for(String tool:List.of("echo_cutter","echo_card_proxy")) {
            var path=Path.of("src/main/resources/assets/noellesroles/textures/item/"+tool+".png");
            var image=ImageIO.read(path.toFile()); assertNotNull(image); assertEquals(32,image.getWidth()); assertEquals(32,image.getHeight()); assertTrue(image.getColorModel().hasAlpha());
            var model=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/noellesroles/models/item/"+tool+".json"))).getAsJsonObject();
            assertEquals("noellesroles:item/"+tool,model.getAsJsonObject("textures").get("layer0").getAsString());
        }
    }
}
