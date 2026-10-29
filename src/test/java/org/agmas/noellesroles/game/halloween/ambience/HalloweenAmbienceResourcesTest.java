package org.agmas.noellesroles.game.halloween.ambience;

import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HalloweenAmbienceResourcesTest {
    @Test
    void registeredMusicEntryStreamsAnExistingVorbisResource() throws Exception {
        try (InputStream json = resource("assets/halloween/sounds.json")) {
            var sounds = JsonParser.parseReader(new InputStreamReader(json, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("music.town").getAsJsonArray("sounds");
            assertEquals(1, sounds.size());
            var sound = sounds.get(0).getAsJsonObject();
            assertTrue(sound.get("stream").getAsBoolean());
            assertEquals("halloween:music/town", sound.get("name").getAsString());
            String[] location = sound.get("name").getAsString().split(":", 2);
            try (InputStream ogg = resource("assets/" + location[0] + "/sounds/" + location[1] + ".ogg")) {
                byte[] bytes = ogg.readAllBytes();
                assertTrue(bytes.length > 100);
                assertEquals("OggS", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
                // 识别包必须是 Vorbis，不接受改后缀的 WAV/MP3 或空文件。
                assertTrue(new String(bytes, StandardCharsets.ISO_8859_1).contains("\u0001vorbis"));
            }
        }
    }

    private static InputStream resource(String path) {
        InputStream stream = HalloweenAmbienceResourcesTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, path);
        return stream;
    }
}
