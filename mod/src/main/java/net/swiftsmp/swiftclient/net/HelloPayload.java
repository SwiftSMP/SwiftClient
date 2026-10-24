package net.swiftsmp.swiftclient.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.swiftsmp.swiftclient.SwiftClientMod;

/**
 * Sent once on join so the server can tell a SwiftClient player from everyone else.
 * The body is {@code launcher} when the SwiftClient launcher started the game (it passes
 * {@code -Dswiftclient.capeApi}) and {@code mod} when the jar was dropped into another launcher.
 */
public record HelloPayload(String body) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HelloPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "hello"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HelloPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.body()),
            buf -> new HelloPayload(buf.readUtf()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
