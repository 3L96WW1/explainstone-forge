package com.ginka.explainstone.speaker;

import com.ginka.explainstone.BroadcastTransmitterBlock;
import com.ginka.explainstone.network.SpeakerNetwork;
import com.ginka.explainstone.network.SpeakerPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/** 服务端广播表：发信器在播什么、哪个频道、范围多大 喵 */
public final class Broadcasts {
    public record Entry(int channel, String trackId, int mode, int index, double range, long startTick, int duration) {
    }

    private static final Map<BlockPos, Entry> ENTRIES = new HashMap<>();
    private static int ticker;

    public static void start(ServerLevel level, BlockPos pos, SpeakerBlockEntity speaker) {
        var tracks = speaker.getTracks();
        if (tracks.isEmpty() || !(level.getBlockState(pos).getBlock() instanceof BroadcastTransmitterBlock)) {
            return;
        }
        Entry old = ENTRIES.get(pos.immutable());
        String trackId = tracks.get(speaker.getSelected()).id();
        if (old != null && (old.channel() != speaker.getChannel() || !old.trackId().equals(trackId))) {
            for (ServerPlayer player : level.players()) {
                SpeakerNetwork.sendToPlayer(player, SpeakerPacket.broadcast(old.channel(), old.mode(),
                        old.index(), false, ""));
            }
        }
        int duration = trackId.startsWith("file:")
                ? SpeakerStorage.durationSeconds(level.getServer(), trackId.substring(5)) : 0;
        ENTRIES.put(pos.immutable(), new Entry(speaker.getChannel(), trackId,
                speaker.getMode().ordinal(), speaker.getSelected(), speaker.getTransmitRange(),
                level.getGameTime(), duration));
        push(level, pos.immutable());
    }

    public static void stop(BlockPos pos) {
        ENTRIES.remove(pos);
    }

    /** 无条件给全服推一条"该频道已停止"，保证一定静音 喵 */
    public static void notifyStop(ServerLevel level, int channel) {
        for (ServerPlayer player : level.players()) {
            SpeakerNetwork.sendToPlayer(player, SpeakerPacket.broadcast(channel, 0, 0, false, ""));
        }
    }

    /** 停止广播并立刻通知范围内玩家（否则客户端要等 10 秒过期）喵 */
    public static void stopAndNotify(ServerLevel level, BlockPos pos) {
        Entry entry = ENTRIES.remove(pos);
        if (entry == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            SpeakerNetwork.sendToPlayer(player, SpeakerPacket.broadcast(entry.channel(), entry.mode(),
                    entry.index(), false, ""));
        }
    }

    public static void tick(ServerLevel level) {
        if (++ticker % 20 != 0) {
            return;
        }
        for (BlockPos pos : ENTRIES.keySet()) {
            push(level, pos);
        }
    }

    private static void push(ServerLevel level, BlockPos pos) {
        Entry entry = ENTRIES.get(pos);
        if (entry == null) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SpeakerBlockEntity speaker) || !speaker.isPlaying()) {
            stopAndNotify(level, pos);      // 方块被挖/停播 → 立刻通知，别等过期 喵
            return;
        }
        Vec3 center = Vec3.atCenterOf(pos);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(center) <= entry.range() * entry.range()) {
                int elapsed = (int) Math.max(0, (level.getGameTime() - entry.startTick()) / 20);
                SpeakerNetwork.sendToPlayer(player, SpeakerPacket.broadcast(entry.channel(), entry.mode(),
                        entry.index(), true, entry.trackId(), entry.duration(), elapsed));
            }
        }
    }

    public static void forget(BlockPos pos) {
        ENTRIES.remove(pos);
    }

    private Broadcasts() {
    }
}
