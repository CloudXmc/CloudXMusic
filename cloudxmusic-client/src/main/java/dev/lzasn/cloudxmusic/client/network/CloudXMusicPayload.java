package dev.lzasn.cloudxmusic.client.network;

import dev.lzasn.cloudxmusic.protocol.Protocol;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.Arrays;

/** Fabric Custom Payload 只做透明字节承载，协议体由 music-protocol 编解码。 */
public record CloudXMusicPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<CloudXMusicPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("cloudxmusic", "main"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CloudXMusicPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBytes(payload.data),
            buffer -> {
                int length = buffer.readableBytes();
                if (length < 1 || length > Protocol.MAX_PACKET_BYTES) throw new IllegalArgumentException("Invalid payload size");
                byte[] data = new byte[length];
                buffer.readBytes(data);
                return new CloudXMusicPayload(data);
            });

    public CloudXMusicPayload {
        data = data.clone();
    }

    @Override public byte[] data() { return data.clone(); }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(TYPE, CODEC);
        PayloadTypeRegistry.playS2C().register(TYPE, CODEC);
    }
}
