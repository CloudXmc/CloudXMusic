package dev.lzasn.cloudxmusic.client.gui;

import dev.lzasn.cloudxmusic.client.gui.terms.UserAgreement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class FirstRunNoticeTest {
    @TempDir Path root;
    @Test void oldConsentRequiresNewReadingAndCurrentConsentSurvivesRestart() throws Exception {
        Path file=root.resolve("disclaimer.txt");Files.writeString(file,"accepted=true\n");
        FirstRunNotice.load(file);assertFalse(FirstRunNotice.accepted());assertEquals("accepted=true\n",Files.readString(file));
        FirstRunNotice.accept(file);assertTrue(FirstRunNotice.accepted());
        assertTrue(Files.readString(file).contains("termsVersion="+UserAgreement.VERSION));
        FirstRunNotice.load(root.resolve("absent"));assertFalse(FirstRunNotice.accepted());
        FirstRunNotice.load(file);assertTrue(FirstRunNotice.accepted());
        FirstRunNotice.load(root.resolve("absent"));
    }
    @Test void corruptFutureOrOutdatedConsentCannotCountAsAcceptance() throws Exception {
        Path file=root.resolve("disclaimer.txt");
        for(String text:new String[]{"accepted=true\ntermsVersion=1\n","accepted=true\ntermsVersion=999\n","accepted=false\ntermsVersion=2\n","bad","accepted=true\ntermsVersion=oops\n"}){
            Files.writeString(file,text);FirstRunNotice.load(file);assertFalse(FirstRunNotice.accepted());assertEquals(text,Files.readString(file));
        }
    }
    @Test void failedSaveDoesNotAcceptOrDeleteExistingContent() throws Exception {
        Path directory=root.resolve("disclaimer.txt");Files.createDirectory(directory);Files.writeString(directory.resolve("keep"),"保留");
        FirstRunNotice.load(directory);assertFalse(FirstRunNotice.accepted());
        assertThrows(IllegalStateException.class,()->FirstRunNotice.accept(directory));assertFalse(FirstRunNotice.accepted());
        assertEquals("保留",Files.readString(directory.resolve("keep")));
        try(var paths=Files.list(root)){assertEquals(1,paths.count());}
    }
}
