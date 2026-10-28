package com.ginka.explainstone.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 通用数据包：C2S 用 kind=ACTION/IMPORT，S2C 用 kind=AUDIO/BROADCAST/RESYNC 喵
 */
public record SpeakerPacket(int kind, int action, int index, int total, int value, String text, byte[] data) {
    public static final int KIND_ACTION = 0;
    public static final int KIND_IMPORT = 1;
    public static final int KIND_AUDIO = 2;
    public static final int KIND_BROADCAST = 3;
    public static final int KIND_RESYNC = 4;
    public static final int KIND_AUDIO_REQUEST = 5;

    public static SpeakerPacket action(int action, int value, String text) {
        return new SpeakerPacket(KIND_ACTION, action, 0, 0, value, text == null ? "" : text, EMPTY);
    }

    public static SpeakerPacket importChunk(String name, int index, int total, byte[] data) {
        return new SpeakerPacket(KIND_IMPORT, 0, index, total, 0, name, data);
    }

    public static SpeakerPacket audio(String sha1, int index, int total, byte[] data) {
        return new SpeakerPacket(KIND_AUDIO, 0, index, total, 0, sha1, data);
    }

    public static SpeakerPacket broadcast(int channel, int mode, int index, boolean active, String tracks) {
        return broadcast(channel, mode, index, active, tracks, 0);
    }

    public static SpeakerPacket broadcast(int channel, int mode, int index, boolean active, String tracks, int durationSeconds) {
        return broadcast(channel, mode, index, active, tracks, durationSeconds, 0);
    }

    /** action 字段借用为"已播秒数"，让所有客户端对齐进度 喵 */
    public static SpeakerPacket broadcast(int channel, int mode, int index, boolean active, String tracks,
                                          int durationSeconds, int elapsedSeconds) {
        return new SpeakerPacket(KIND_BROADCAST, elapsedSeconds, index, durationSeconds, channel, tracks,
                new byte[]{(byte) mode, (byte) (active ? 1 : 0)});
    }

    public static SpeakerPacket audioRequest(String sha1) {
        return new SpeakerPacket(KIND_AUDIO_REQUEST, 0, 0, 0, 0, sha1, EMPTY);
    }

    public static SpeakerPacket resync() {
        return new SpeakerPacket(KIND_RESYNC, 0, 0, 0, 0, "", EMPTY);
    }

    private static final byte[] EMPTY = new byte[0];

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.kind);
        buf.writeVarInt(this.action);
        buf.writeVarInt(this.index);
        buf.writeVarInt(this.total);
        buf.writeVarInt(this.value);
        buf.writeUtf(this.text, 32767);
        buf.writeByteArray(this.data == null ? EMPTY : this.data);
    }

    public static SpeakerPacket decode(FriendlyByteBuf buf) {
        int kind = buf.readVarInt();
        int action = buf.readVarInt();
        int index = buf.readVarInt();
        int total = buf.readVarInt();
        int value = buf.readVarInt();
        String text = buf.readUtf(32767);
        byte[] data = buf.readByteArray();
        return new SpeakerPacket(kind, action, index, total, value, text, data);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> SpeakerNetwork.handle(this, ctx));
        ctx.setPacketHandled(true);
    }
}
