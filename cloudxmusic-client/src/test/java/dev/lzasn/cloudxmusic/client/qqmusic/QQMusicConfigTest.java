package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class QQMusicConfigTest {
    @TempDir Path directory;
    private Path file() { return directory.resolve("qqmusic/config.json"); }
    @Test void createsEmptyReservedConfiguration() throws Exception {
        assertTrue(new QQMusicConfig(file()).load().isEmpty());
        assertEquals("{}\n", Files.readString(file()));
    }
    @Test void preservesUnknownFieldsAndLegacySettingsWithoutUsingThem() throws Exception {
        String text = "{\"apiBaseUrl\":\"obsolete\",\"custom\":\"保留\"}";
        write(text);
        assertEquals("保留", new QQMusicConfig(file()).load().get("custom").getAsString());
        assertEquals(text, Files.readString(file()));
    }
    @Test void rereadsChangesAndDoesNotMutateLoadedSnapshot() throws Exception {
        QQMusicConfig config = new QQMusicConfig(file());
        write("{\"custom\":1}");
        var first = config.load();
        write("{\"custom\":2}");
        assertEquals(2, config.load().get("custom").getAsInt());
        assertEquals(1, first.get("custom").getAsInt());
    }
    @Test void rejectsMalformedOrWrongShapedConfiguration() throws Exception {
        for (String text : new String[]{"{", "[]", "null", "{} trailing"}) {
            write(text);
            assertThrows(IOException.class, new QQMusicConfig(file())::load);
            assertEquals(text, Files.readString(file()));
        }
    }
    @Test void rejectsOversizedConfiguration() throws Exception {
        write(" ".repeat(64 * 1024 + 1));
        assertThrows(IOException.class, new QQMusicConfig(file())::load);
    }
    @Test void rejectsInvalidUtf8() throws Exception {
        Files.createDirectories(file().getParent());
        Files.write(file(), new byte[]{(byte)0xc3, 0x28});
        assertThrows(IOException.class, new QQMusicConfig(file())::load);
    }
    @Test void reportsFilesystemFailure() throws Exception {
        Files.createDirectories(file());
        assertThrows(IOException.class, new QQMusicConfig(file())::load);
    }
    private void write(String text) throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), text);
    }
}
