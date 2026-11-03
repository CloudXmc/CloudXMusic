package dev.lzasn.cloudxmusic.client.network;

import dev.lzasn.cloudxmusic.protocol.ShareWire;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/** 分享专用负载，与房间通道分开注册。 */
public record SharePayload(byte[] data) implements CustomPacketPayload {
    public static final Type<SharePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("cloudxmusic", "share"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SharePayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBytes(payload.data), buffer -> {
                int length = buffer.readableBytes();
                if (length < 7 || length > ShareWire.MAX_BYTES) throw new IllegalArgumentException("Invalid share payload size");
                byte[] bytes = new byte[length]; buffer.readBytes(bytes); return new SharePayload(bytes);
            });
    public SharePayload { data = data.clone(); }
    @Override public byte[] data() { return data.clone(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register() {
        PayloadTypeRegistry.playC2S().register(TYPE, CODEC);
        PayloadTypeRegistry.playS2C().register(TYPE, CODEC);
    }
}
