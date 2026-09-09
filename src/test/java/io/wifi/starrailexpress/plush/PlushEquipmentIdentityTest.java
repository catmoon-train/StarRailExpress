package io.wifi.starrailexpress.plush;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlushEquipmentIdentityTest {
    @Test
    void emptyEncodingMeansNoPlush() {
        assertEquals("", PlushEquipmentIdentity.NONE);
        assertNull(PlushEquipmentIdentity.decode(null));
        assertNull(PlushEquipmentIdentity.decode(""));
        assertNull(PlushEquipmentIdentity.decode("   "));
    }

    @Test
    void namedPlushRoundTrip() {
        PlushEquipmentIdentity identity = PlushEquipmentIdentity.decode("noellesroles:canyuesama_plush");
        assertEquals("noellesroles:canyuesama_plush", identity.encode());
        assertTrue(identity.isNamedSponsorPath());
        assertEquals("canyuesama", identity.sponsorSkinName());
    }

    @Test
    void profileAndTextureSuffixesSurviveEncode() {
        PlushEquipmentIdentity withProfile = PlushEquipmentIdentity
                .decode("noellesroles:custom_player_plush|p:Steve");
        assertEquals("noellesroles:custom_player_plush|p:Steve", withProfile.encode());
        assertEquals("", withProfile.sponsorSkinName());

        PlushEquipmentIdentity withTexture = PlushEquipmentIdentity
                .decode("noellesroles:custom_player_plush|t:starrailexpress:textures/entity/disguise/disguise_skin_1.png");
        assertEquals(
                "noellesroles:custom_player_plush|t:starrailexpress:textures/entity/disguise/disguise_skin_1.png",
                withTexture.encode());
    }

    @Test
    void invalidItemIdIsRejected() {
        assertNull(PlushEquipmentIdentity.decode("not a resource"));
    }
}
