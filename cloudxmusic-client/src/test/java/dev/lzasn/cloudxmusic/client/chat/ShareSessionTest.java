package dev.lzasn.cloudxmusic.client.chat;

import dev.lzasn.cloudxmusic.protocol.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class ShareSessionTest {
    private final AtomicLong clock = new AtomicLong();
    private final List<ShareWire.Message> sent = new ArrayList<>();
    private final List<ShareWire.Broadcast> received = new ArrayList<>();
    private final List<String> notifications = new ArrayList<>();
    private final ShareSession session = new ShareSession(message -> { sent.add(message); return true; },
            received::add, notifications::add, clock::get);
    private final ShareWire.Song song = new ShareWire.Song(new SongRef(MusicSourceType.NETEASE, "1"), "歌曲", "", 0, false);

    @Test void absentPluginNeverFallsBackToPublicChatOrSendsSongData() {
        assertFalse(session.share(song));
        session.joined(); assertFalse(session.share(song));
        assertEquals(List.of(new ShareWire.Hello()), sent);
        assertFalse(notifications.isEmpty());
        clock.set(1_999_999_999L); session.tick(); assertEquals(1, sent.size());
        clock.incrementAndGet(); session.tick(); assertEquals(2, sent.size());
        assertTrue(sent.stream().allMatch(message -> message instanceof ShareWire.Hello));
    }
    @Test void privateShareAndEchoWorkOnlyAfterWelcome() {
        session.joined(); session.receive(new ShareWire.Welcome());
        assertTrue(session.share(song));
        assertEquals(new ShareWire.Request(song), sent.getLast());
        var broadcast = new ShareWire.Broadcast("Alice", song);
        session.receive(broadcast); assertEquals(List.of(broadcast), received);
        int count = sent.size(); clock.set(10_000_000_000L); session.tick(); assertEquals(count, sent.size());
    }
    @Test void reconnectAndDisconnectDiscardReadinessAndLateMessages() {
        session.joined(); session.receive(new ShareWire.Welcome()); session.disconnected();
        session.receive(new ShareWire.Broadcast("Alice", song)); assertTrue(received.isEmpty());
        session.receive(new ShareWire.Welcome()); assertFalse(session.share(song));
        session.joined(); assertFalse(session.share(song));
    }
    @Test void serverRejectionRevokesHandshakeWithoutSendingMoreSongData() {
        session.joined(); session.receive(new ShareWire.Welcome());
        session.receive(new ShareWire.Rejected(ShareWire.Failure.HANDSHAKE_REQUIRED));
        assertFalse(session.share(song)); assertTrue(sent.stream().noneMatch(message -> message instanceof ShareWire.Request));
    }
}
