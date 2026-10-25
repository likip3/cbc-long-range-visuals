package dev.likip.cbclongrangevisuals.network;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class VisualLightTest {
    @Test void convertsAllBlockAndSkyLevelsToMinecraftLightmapCoordinates() {
        for (int block = 0; block <= 15; block++) for (int sky = 0; sky <= 15; sky++) {
            int encoded = VisualLight.levels(block, sky);
            assertTrue(encoded >= 0 && encoded <= 255);
            assertEquals((block << 4) | (sky << 20), VisualLight.packed(encoded));
        }
    }
    @Test void outdoorSmokeUsesSkyLightWithoutBecomingAnEmissiveTracer() {
        assertEquals(0xF00000, VisualLight.packed(VisualLight.levels(0, 15)));
        assertNotEquals(0xF000F0, VisualLight.packed(VisualLight.levels(0, 15)));
        assertEquals(0xF000F0, VisualLight.packed(VisualLight.levels(15, 15)));
    }
}
