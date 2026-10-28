package com.ginka.explainstone.network;

import com.ginka.explainstone.Explainstone;
import com.ginka.explainstone.speaker.PlayMode;
import com.ginka.explainstone.speaker.SpeakerBlockEntity;
import com.ginka.explainstone.speaker.Track;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** 网络层（Forge SimpleChannel）：操作指令 + 音频分块 + 广播状态 喵 */
public final class SpeakerNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Explainstone.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static int nextId = 0;

    public static void register() {
        CHANNEL.messageBuilder(SpeakerPacket.class, nextId++)
                .encoder(SpeakerPacket::encode)
                .decoder(SpeakerPacket::decode)
                .consumerMainThread(SpeakerPacket::handle)
                .add();
    }

    public static void sendToServer(SpeakerPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendToPlayer(ServerPlayer player, SpeakerPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** 导入分块缓存：玩家UUID + 名字 → 分块 喵 */
    private static final java.util.Map<String, java.util.Map<Integer, byte[]>> IMPORT_CHUNKS = new java.util.HashMap<>();

    /** 客户端收到的音频分块缓存 喵 */
    private static final java.util.Map<String, java.util.Map<Integer, byte[]>> AUDIO_CHUNKS = new java.util.HashMap<>();

    /** 服务端处理 喵 */
    public static void handle(SpeakerPacket packet, NetworkEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (packet.kind() == SpeakerPacket.KIND_IMPORT && player != null) {
            handleImport(packet, player);
            return;
        }
        if (packet.kind() == SpeakerPacket.KIND_AUDIO_REQUEST && player != null) {
            byte[] ogg = com.ginka.explainstone.speaker.SpeakerStorage.read(player.server, packet.text());
            if (ogg != null) {
                int total = (ogg.length + 24000 - 1) / 24000;
                for (int i = 0; i < total; i++) {
                    byte[] part = java.util.Arrays.copyOfRange(ogg, i * 24000, Math.min(ogg.length, (i + 1) * 24000));
                    sendToPlayer(player, SpeakerPacket.audio(packet.text(), i, total, part));
                }
            }
            return;
        }
        if (packet.kind() == SpeakerPacket.KIND_BROADCAST && player == null) {
            com.ginka.explainstone.client.audio.SpeakerAudioCache.setBroadcast(packet.value(), packet.text(),
                    packet.total(), packet.action());
            return;
        }
        if (packet.kind() == SpeakerPacket.KIND_AUDIO && player == null) {
            handleAudioChunk(packet);
            return;
        }
        if (player == null || packet.kind() != SpeakerPacket.KIND_ACTION) {
            return;
        }
        BlockPos pos = player.blockPosition();
        // 客户端把方块坐标放在 text 里："x,y,z|payload"
        String text = packet.text();
        int bar = text.indexOf('|');
        if (bar > 0) {
            String[] xyz = text.substring(0, bar).split(",");
            if (xyz.length == 3) {
                pos = new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
            }
            text = text.substring(bar + 1);
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SpeakerBlockEntity speaker)) {
            return;
        }
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 256.0) {
            return;
        }
        boolean wasPlaying = speaker.isPlaying();
        switch (packet.action()) {
            case 0 -> speaker.setPlaying(true);
            case 1 -> speaker.setPlaying(false);
            case 2 -> speaker.setMode(speaker.getMode().next());
            case 3 -> speaker.setRespectRedstone(!speaker.isRespectRedstone());
            case 4 -> speaker.setSelected(packet.value());
            case 5 -> speaker.removeTrack(text);
            case 6 -> speaker.setChannel(packet.value());
            case 7 -> speaker.setReceiveMode(!speaker.isReceiveMode());
            case 8 -> speaker.setSpeakerEnabled(!speaker.isSpeakerEnabled());
            case 9 -> speaker.setVolume(packet.value());
            case 10 -> speaker.setAudibleRange(packet.value());
            case 11 -> speaker.setTransmitRange(packet.value());
            case 12 -> speaker.setTimerSeconds(packet.value());
            case 13 -> {
                speaker.setPaused(!speaker.isPaused());
                if (level.getBlockState(pos).getBlock()
                        instanceof com.ginka.explainstone.BroadcastTransmitterBlock) {
                    if (speaker.isPaused()) {
                        com.ginka.explainstone.speaker.Broadcasts.stopAndNotify(level, pos);
                    } else {
                        com.ginka.explainstone.speaker.Broadcasts.start(level, pos, speaker);
                    }
                }
            }
            default -> {
            }
        }
        speaker.sync();
        org.slf4j.LoggerFactory.getLogger("explainstone-net")
                .info("action={} pos={} playing={} channel={}", packet.action(), pos, speaker.isPlaying(), speaker.getChannel());
        if (level.getBlockState(pos).getBlock() instanceof com.ginka.explainstone.BroadcastTransmitterBlock) {
            if (speaker.isPlaying()) {
                // 播放中改频道/曲目/模式/范围都要立刻刷新广播 喵
                com.ginka.explainstone.speaker.Broadcasts.start(level, pos, speaker);
            } else {
                com.ginka.explainstone.speaker.Broadcasts.stopAndNotify(level, pos);
                com.ginka.explainstone.speaker.Broadcasts.notifyStop(level, speaker.getChannel());
            }
        }
    }

    private static void handleImport(SpeakerPacket packet, ServerPlayer player) {
        String text = packet.text();
        int bar = text.indexOf('|');
        if (bar <= 0) {
            return;
        }
        BlockPos pos = parsePos(text.substring(0, bar));
        String name = text.substring(bar + 1);
        if (pos == null || name.isBlank() || packet.data() == null || packet.data().length == 0) {
            return;
        }
        String key = player.getUUID() + "|" + name;
        var parts = IMPORT_CHUNKS.computeIfAbsent(key, k -> new java.util.TreeMap<>());
        parts.put(packet.index(), packet.data());
        if (parts.size() < packet.total()) {
            return;
        }
        IMPORT_CHUNKS.remove(key);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (byte[] part : parts.values()) {
            out.writeBytes(part);
        }
        byte[] data = out.toByteArray();
        if (data.length > 6 * 1024 * 1024) {
            return;
        }
        String sha1 = com.ginka.explainstone.speaker.SpeakerStorage.sha1(data);
        if (!com.ginka.explainstone.speaker.SpeakerStorage.write(player.server, sha1, data)) {
            return;
        }
        if (player.level().getBlockEntity(pos) instanceof SpeakerBlockEntity speaker) {
            speaker.addTrack(Track.file(name, sha1,
                    com.ginka.explainstone.speaker.SpeakerStorage.durationSeconds(player.server, sha1)));
        }
        player.displayClientMessage(Component.translatable("message.explainstone.import.ok", name), false);
    }

    private static void handleAudioChunk(SpeakerPacket packet) {
        var parts = AUDIO_CHUNKS.computeIfAbsent(packet.text(), k -> new java.util.TreeMap<>());
        parts.put(packet.index(), packet.data());
        if (parts.size() < packet.total()) {
            return;
        }
        AUDIO_CHUNKS.remove(packet.text());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (byte[] part : parts.values()) {
            out.writeBytes(part);
        }
        com.ginka.explainstone.client.audio.SpeakerAudioCache.put(packet.text(), out.toByteArray());
    }

    private static BlockPos parsePos(String text) {
        String[] xyz = text.split(",");
        if (xyz.length != 3) {
            return null;
        }
        try {
            return new BlockPos(Integer.parseInt(xyz[0].trim()), Integer.parseInt(xyz[1].trim()), Integer.parseInt(xyz[2].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean isReceiveMode(SpeakerBlockEntity be) {
        return be.isReceiveMode();
    }

    public static Component modeName(PlayMode mode) {
        return Component.translatable(mode.getTranslationKey());
    }

    private SpeakerNetwork() {
    }
}
