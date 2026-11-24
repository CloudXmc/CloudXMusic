package dev.lzasn.cloudxmusic.client.qqmusic;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** QQ App 二维码通道使用的有限 MQTT 5 子集；WebSocket 帧交给 JDK 处理。 */
final class QQMqttCodec {
    static final int MAX_BYTES = 1024 * 1024;
    record Packet(int header, byte[] body) {}
    private QQMqttCodec() {}
    static final class Writer extends ByteArrayOutputStream {
        void u16(int value) { write(value >>> 8); write(value); }
        void variable(int value) {
            if (value < 0 || value > MAX_BYTES) throw new IllegalArgumentException("MQTT 长度超限");
            do { int digit = value % 128; value /= 128; write(digit | (value > 0 ? 128 : 0)); } while (value > 0);
        }
        void string(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 65535) throw new IllegalArgumentException("MQTT 字符串超限");
            u16(bytes.length); writeBytes(bytes);
        }
        void user(String name, String value) { write(0x26); string(name); string(value); }
        void properties(Writer props) { variable(props.size()); writeBytes(props.toByteArray()); }
    }
    static byte[] packet(int header, Writer body) {
        Writer out = new Writer(); out.write(header); out.variable(body.size()); out.writeBytes(body.toByteArray()); return out.toByteArray();
    }
    static byte[] connect(String id) {
        Writer props = new Writer(); props.write(0x15); props.string("pass");
        props.user("tmeAppID", "qqmusic"); props.user("business", "management"); props.user("hashTag", id);
        props.user("clientTag", "management.user"); props.user("userID", id);
        Writer body = new Writer(); body.string("MQTT"); body.write(5); body.write(2); body.u16(45); body.properties(props);
        body.string(System.currentTimeMillis() + "-" + UUID.randomUUID());
        return packet(0x10, body);
    }
    static byte[] subscribe(String topic) {
        Writer props = new Writer(); props.user("authorization", "tmelogin"); props.user("pubsub", "unicast");
        Writer body = new Writer(); body.u16(1); body.properties(props); body.string(topic); body.write(0);
        return packet(0x82, body);
    }
    static final class Decoder {
        private byte[] pending = new byte[0];
        synchronized List<Packet> feed(ByteBuffer buffer) throws IOException {
            if (buffer.remaining() > MAX_BYTES + 5 - pending.length) throw new IOException("QQ 登录通道数据超过 1 MiB");
            byte[] bytes = Arrays.copyOf(pending, pending.length + buffer.remaining()); buffer.get(bytes, pending.length, buffer.remaining());
            List<Packet> packets = new ArrayList<>(); int start = 0;
            while (bytes.length - start >= 2) {
                int pos = start + 1, length = 0, multiplier = 1, count = 0; boolean complete = false;
                while (pos < bytes.length) {
                    int digit = bytes[pos++] & 255; count++;
                    length += (digit & 127) * multiplier;
                    if (length > MAX_BYTES || count > 4 || (count == 4 && (digit & 128) != 0)) throw new IOException("QQ MQTT 长度无效");
                    if ((digit & 128) == 0) { complete = true; break; }
                    multiplier *= 128;
                }
                if (!complete || bytes.length - pos < length) break;
                packets.add(new Packet(bytes[start] & 255, Arrays.copyOfRange(bytes, pos, pos + length)));
                if (packets.size() > 32) throw new IOException("QQ MQTT 包过于密集");
                start = pos + length;
            }
            pending = Arrays.copyOfRange(bytes, start, bytes.length); return packets;
        }
    }
    static final class Cursor {
        private final byte[] bytes; private int pos;
        Cursor(byte[] bytes) { this.bytes = bytes; }
        int byteValue() throws IOException { if (pos >= bytes.length) throw new IOException("QQ MQTT 数据截断"); return bytes[pos++] & 255; }
        int u16() throws IOException { return byteValue() * 256 + byteValue(); }
        int variable() throws IOException {
            int result = 0, multiplier = 1;
            for (int i = 0; i < 4; i++) { int digit = byteValue(); result += (digit & 127) * multiplier; if ((digit & 128) == 0) return result; multiplier *= 128; }
            throw new IOException("QQ MQTT 变长整数无效");
        }
        void skip(int length) throws IOException { if (length < 0 || length > bytes.length - pos) throw new IOException("QQ MQTT 字段越界"); pos += length; }
        String string() throws IOException {
            int length = u16(); if (length > bytes.length - pos) throw new IOException("QQ MQTT 字符串截断");
            String result = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes, pos, length)).toString();
            pos += length; if (result.indexOf(0) >= 0) throw new IOException("QQ MQTT 字符串无效"); return result;
        }
        Map<String, String> properties() throws IOException {
            int length = variable(); if (length > bytes.length - pos) throw new IOException("QQ MQTT 属性截断");
            // 使用子游标，禁止属性读取越过声明的边界进入有效载荷。
            Cursor p = new Cursor(Arrays.copyOfRange(bytes, pos, pos + length)); pos += length;
            Map<String, String> values = new HashMap<>();
            while (p.pos < p.bytes.length) {
                switch (p.byteValue()) {
                    case 0x26 -> values.put(p.string(), p.string());
                    case 0x1c -> values.put("reference", p.string());
                    case 0x13 -> values.put("keepalive", Integer.toString(p.u16()));
                    case 0x01, 0x17, 0x19, 0x24, 0x25, 0x28, 0x29, 0x2a -> p.skip(1);
                    case 0x21, 0x22, 0x23 -> p.skip(2);
                    case 0x02, 0x11, 0x18, 0x27 -> p.skip(4);
                    case 0x0b -> p.variable();
                    case 0x03, 0x08, 0x12, 0x15, 0x1a, 0x1f -> p.string();
                    case 0x09, 0x16 -> p.skip(p.u16());
                    default -> throw new IOException("QQ MQTT 属性类型不支持");
                }
                if (values.size() > 64) throw new IOException("QQ MQTT 属性过多");
            }
            return values;
        }
        byte[] rest() { byte[] value = Arrays.copyOfRange(bytes, pos, bytes.length); pos = bytes.length; return value; }
        void end() throws IOException { if (pos != bytes.length) throw new IOException("QQ MQTT 包存在额外数据"); }
    }
}
