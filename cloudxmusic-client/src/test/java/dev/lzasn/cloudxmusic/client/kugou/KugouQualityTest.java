package dev.lzasn.cloudxmusic.client.kugou;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KugouQualityTest {
    @Test void officialNamesParametersAndStrictlyDownwardFallback() {
        assertEquals(List.of("Hi-Res音质","无损音质","高品音质","标准音质"),
                Arrays.stream(KugouQuality.values()).map(KugouQuality::menuLabel).toList());
        assertEquals(List.of("high","flac","320","128"),
                Arrays.stream(KugouQuality.values()).map(KugouQuality::apiValue).toList());
        assertEquals(List.of(KugouQuality.FLAC,KugouQuality.HQ,KugouQuality.STANDARD),KugouQuality.FLAC.fallbacks());
        assertEquals(List.of(KugouQuality.STANDARD),KugouQuality.STANDARD.fallbacks());
    }
}
