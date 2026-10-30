package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QQMqttCodecTest {
    @Test void splitAndCombinedPacketsAreReassembled() throws Exception {
        QQMqttCodec.Decoder decoder = new QQMqttCodec.Decoder();
        assertTrue(decoder.feed(ByteBuffer.wrap(new byte[]{0x20})).isEmpty());
        var packets = decoder.feed(ByteBuffer.wrap(new byte[]{3, 0, 0, 0, (byte) 0xd0, 0}));
        assertEquals(2, packets.size()); assertEquals(0x20, packets.getFirst().header());
        assertArrayEquals(new byte[]{0, 0, 0}, packets.getFirst().body());
    }
    @Test void everyByteBoundaryIsSupported() throws Exception {
        byte[] bytes = QQMqttCodec.connect("test-id"); QQMqttCodec.Decoder decoder = new QQMqttCodec.Decoder();
        List<QQMqttCodec.Packet> packets = new ArrayList<>();
        for (byte b : bytes) packets.addAll(decoder.feed(ByteBuffer.wrap(new byte[]{b})));
        assertEquals(1, packets.size());
        QQMqttCodec.Cursor c = new QQMqttCodec.Cursor(packets.getFirst().body());
        assertEquals("MQTT", c.string()); assertEquals(5, c.byteValue()); assertEquals(2, c.byteValue()); assertEquals(45, c.u16());
        var props = c.properties(); assertEquals("qqmusic", props.get("tmeAppID")); assertEquals("test-id", props.get("hashTag"));
        assertEquals("test-id", props.get("userID")); assertEquals("management.user", props.get("clientTag")); assertFalse(c.string().isEmpty()); c.end();
    }
    @Test void subscribeMatchesAppChannelContract() throws Exception {
        QQMqttCodec.Packet p = new QQMqttCodec.Decoder().feed(ByteBuffer.wrap(QQMqttCodec.subscribe("management.qrcode_login/id"))).getFirst();
        assertEquals(0x82, p.header()); QQMqttCodec.Cursor c = new QQMqttCodec.Cursor(p.body());
        assertEquals(1, c.u16()); assertEquals(Map.of("authorization", "tmelogin", "pubsub", "unicast"), c.properties());
        assertEquals("management.qrcode_login/id", c.string()); assertEquals(0, c.byteValue()); c.end();
    }
    @Test void invalidLengthsAndOversizedInputFail() {
        assertThrows(IOException.class, () -> new QQMqttCodec.Decoder().feed(ByteBuffer.wrap(new byte[]{0x20,(byte)128,(byte)128,(byte)128,(byte)128})));
        assertThrows(IOException.class, () -> new QQMqttCodec.Decoder().feed(ByteBuffer.wrap(new byte[QQMqttCodec.MAX_BYTES + 6])));
    }
    @Test void truncatedAndUnknownPropertiesFail() {
        for (byte[] bytes : new byte[][]{{2,0x26,0}, {1,0x7f}, {3,0x13,0}, {1,0x13,0,0}})
            assertThrows(IOException.class, () -> new QQMqttCodec.Cursor(bytes).properties());
    }
    @Test void malformedUtf8AndStringsFail() {
        assertThrows(IOException.class, () -> new QQMqttCodec.Cursor(new byte[]{0,1,(byte)0xff}).string());
        assertThrows(IOException.class, () -> new QQMqttCodec.Cursor(new byte[]{0,1,0}).string());
        assertThrows(IOException.class, () -> new QQMqttCodec.Cursor(new byte[]{0,3,1}).string());
    }
    @Test void excessivePacketsAreBounded() {
        byte[] packets = new byte[66]; for (int i = 0; i < packets.length; i += 2) packets[i] = (byte)0xd0;
        assertThrows(IOException.class, () -> new QQMqttCodec.Decoder().feed(ByteBuffer.wrap(packets)));
    }
    @Test void routeCannotChangeHostOrEscapePath() throws Exception {
        assertEquals("/ws/handshake/server:1", QQLoginChannel.routePath("server:1"));
        for (String ref : List.of("https://evil.example/", "../x", "x?y", "x#y", "a/b", ""))
            assertThrows(IOException.class, () -> QQLoginChannel.routePath(ref));
    }
}
