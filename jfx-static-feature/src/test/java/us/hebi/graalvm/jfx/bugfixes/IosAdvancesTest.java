package us.hebi.graalvm.jfx.bugfixes;

import org.junit.jupiter.api.Test;
import us.hebi.graalvm.jfx.bugfixes.IosAdvances.AdvanceCache;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Florian Enner
 * @since 14 Oct 2026
 */
class IosAdvancesTest {

    @Test
    void cacheStartsEmpty() {
        AdvanceCache cache = new AdvanceCache();
        assertTrue(Float.isNaN(cache.get(0, 17)));
        assertTrue(Float.isNaN(cache.get(5000, 17)));
    }

    @Test
    void cacheKeepsAdvancesPerSize() {
        AdvanceCache cache = new AdvanceCache();
        cache.put(3, 17, 9.5f);
        cache.put(3, 13, 7.25f);
        cache.put(4, 17, 8f);
        assertEquals(9.5f, cache.get(3, 17));
        assertEquals(7.25f, cache.get(3, 13));
        assertEquals(8f, cache.get(4, 17));
        assertTrue(Float.isNaN(cache.get(4, 13)));
        assertTrue(Float.isNaN(cache.get(3, 34)));
    }

    @Test
    void cacheGrowsWithoutLosingAdvances() {
        AdvanceCache cache = new AdvanceCache();
        cache.put(3, 17, 9.5f);
        cache.put(3, 13, 7.25f);
        cache.put(1000, 17, 11f);
        cache.put(70000, 17, 12f);
        assertEquals(9.5f, cache.get(3, 17));
        assertEquals(11f, cache.get(1000, 17));
        assertEquals(12f, cache.get(70000, 17));
        assertTrue(Float.isNaN(cache.get(999, 17)));
        assertEquals(7.25f, cache.get(3, 13));
        assertEquals(9.5f, cache.get(3, 17), "grown array replaced the old one for its size");
    }

    @Test
    void cacheIsClearedWhenFull() {
        AdvanceCache cache = new AdvanceCache();
        for (int i = 0; i < AdvanceCache.maxSizes; i++) {
            cache.put(0, 10 + i, i);
        }
        for (int i = 0; i < AdvanceCache.maxSizes; i++) {
            assertEquals(i, cache.get(0, 10 + i), "size " + (10 + i));
        }
        cache.put(0, 99, 99);
        assertEquals(99, cache.get(0, 99));
        for (int i = 0; i < AdvanceCache.maxSizes; i++) {
            assertTrue(Float.isNaN(cache.get(0, 10 + i)), "size " + (10 + i));
        }
        cache.put(1, 10, 1);
        assertEquals(1, cache.get(1, 10));
        assertEquals(99, cache.get(0, 99));
    }

}
