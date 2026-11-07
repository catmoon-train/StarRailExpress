package io.wifi.srepackcompat.skin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkinTexturePolicyTest {
    @Test void onlyExactTexturePropertyIsUnlocked() {
        assertTrue(SkinTexturePolicy.isTextureProperty("textures"));
        for (String name : new String[]{null, "", "Textures", "texture", "textures ", "profileId", "profileName", "signature", "publicKey", "chat"}) {
            assertFalse(SkinTexturePolicy.isTextureProperty(name), String.valueOf(name));
        }
    }
}
