package us.hebi.graalvm.jfx.bugfixes;

import javafx.scene.text.FontWeight;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static us.hebi.graalvm.jfx.bugfixes.IosFontWeights.*;

/**
 * @author Florian Enner
 * @since 14 Oct 2026
 */
class IosFontWeightsTest {

    @Test
    void systemMemberForEveryWeight() {
        Map<FontWeight, String> expected = Map.of(
                FontWeight.MEDIUM, MEDIUM_MEMBER,
                FontWeight.EXTRA_BOLD, TRUE_BOLD_MEMBER,
                FontWeight.BLACK, TRUE_BOLD_MEMBER);
        assertEquals(9, FontWeight.values().length);
        for (FontWeight weight : FontWeight.values()) {
            assertEquals(expected.get(weight), memberName(weight), weight.name());
        }
        assertNull(memberName(null));
    }

    @Test
    void boldFlagForEveryWeight() {
        for (FontWeight weight : FontWeight.values()) {
            boolean semiboldOrHeavier = weight.getWeight() >= 600;
            boolean boldOrHeavier = weight.getWeight() >= 700;
            assertEquals(semiboldOrHeavier, isBold(weight, true), "System " + weight.name());
            assertEquals(boldOrHeavier, isBold(weight, false), "other " + weight.name());
        }
        assertFalse(isBold(null, true));
        assertFalse(isBold(null, false));
    }

    @Test
    void systemFamilies() {
        assertTrue(isSystemFamily("System"));
        assertTrue(isSystemFamily("system"));
        assertTrue(isSystemFamily("System Font"));
        assertTrue(isSystemFamily("SYSTEM FONT"));
        assertFalse(isSystemFamily("SansSerif"));
        assertFalse(isSystemFamily("System Font Bold"));
        assertFalse(isSystemFamily("Roboto"));
        assertFalse(isSystemFamily(null));
    }

    @Test
    void weightTraitByName() {
        assertEquals(0.23, weightTrait(MEDIUM_MEMBER));
        assertEquals(0.4, weightTrait(TRUE_BOLD_MEMBER));
        assertTrue(Double.isNaN(weightTrait("System Font")));
        assertTrue(Double.isNaN(weightTrait("System Font Bold")), "emphasized font stays the bold trait");
        assertTrue(Double.isNaN(weightTrait("System Font Semibold")));
        assertTrue(Double.isNaN(weightTrait("System Font medium")), "full names are matched exactly");
        assertTrue(Double.isNaN(weightTrait("Helvetica Bold")));
    }

    @Test
    void everyMemberHasAWeight() {
        for (FontWeight weight : FontWeight.values()) {
            String member = memberName(weight);
            if (member != null) {
                assertFalse(Double.isNaN(weightTrait(member)), weight.name());
            }
        }
    }

    @Test
    void membersAreRegisteredAgainstTheSystemFontFile() {
        Map<String, String> files = new HashMap<>();
        Map<String, String> families = new HashMap<>();
        files.put("system font regular", "/System/Library/Fonts/Core/SFUI.ttf");
        families.put("system font regular", "System Font");
        files.put("system font semibold", "/System/Library/Fonts/Core/SFUI.ttf");
        families.put("system font semibold", "System Font");
        files.put("helvetica", "/System/Library/Fonts/Core/Helvetica.ttc");
        families.put("helvetica", "Helvetica");

        addMembers(files, families);
        assertEquals(5, files.size());
        assertEquals("/System/Library/Fonts/Core/SFUI.ttf", files.get("system font medium"));
        assertEquals("/System/Library/Fonts/Core/SFUI.ttf", files.get("system font bold (true)"));
        assertEquals("System Font", families.get("system font medium"));
        assertEquals("System Font", families.get("system font bold (true)"));
    }

    @Test
    void noMembersWithoutASystemFont() {
        Map<String, String> files = new HashMap<>();
        Map<String, String> families = new HashMap<>();
        files.put("helvetica", "/System/Library/Fonts/Core/Helvetica.ttc");
        families.put("helvetica", "Helvetica");
        addMembers(files, families);
        assertEquals(Map.of("helvetica", "/System/Library/Fonts/Core/Helvetica.ttc"), files);
        assertEquals(Map.of("helvetica", "Helvetica"), families);
    }

}
