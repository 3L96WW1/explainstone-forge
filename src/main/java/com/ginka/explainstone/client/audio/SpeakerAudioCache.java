package com.ginka.explainstone.client.audio;

import java.util.HashMap;
import java.util.Map;

/** 客户端音频缓存（阶段 3：先支持内存缓存，服务端下发后填）喵 */
public final class SpeakerAudioCache {
    private static final Map<String, byte[]> FILES = new HashMap<>();
    private static final Map<String, Integer> PENDING = new HashMap<>();

    public static byte[] ogg(String id) {
        if (id == null || !id.startsWith("file:")) {
            return null;
        }
        return FILES.get(id.substring(5));
    }

    public static void put(String sha1, byte[] data) {
        FILES.put(sha1, data);
        PENDING.remove(sha1);
    }

    public static boolean isPending(String sha1) {
        return PENDING.containsKey(sha1);
    }

    public static void request(String sha1) {
        if (sha1 != null && !FILES.containsKey(sha1) && !PENDING.containsKey(sha1)) {
            PENDING.put(sha1, 0);
        }
    }

    /** 频道广播：频道 → (曲目 id, 最后更新时间) 喵 */
    private record Broadcast(String trackId, long updated, long startedAt, int duration, int elapsed) {
    }

    private static final Map<Integer, Broadcast> BROADCASTS = new java.util.concurrent.ConcurrentHashMap<>();

    public static void setBroadcast(int channel, String trackId) {
        setBroadcast(channel, trackId, 0);
    }

    public static void setBroadcast(int channel, String trackId, int duration) {
        setBroadcast(channel, trackId, duration, 0);
    }

    public static void setBroadcast(int channel, String trackId, int duration, int elapsed) {
        if (trackId == null || trackId.isBlank()) {
            BROADCASTS.remove(channel);
        } else {
            long now = System.currentTimeMillis();
            Broadcast old = BROADCASTS.get(channel);
            // 同一首继续沿用原来的起播时刻，换曲目则重置 喵
            long startedAt = (old != null && old.trackId().equals(trackId)) ? old.startedAt() : now;
            int keepDuration = (old != null && old.trackId().equals(trackId) && duration <= 0) ? old.duration() : duration;
            BROADCASTS.put(channel, new Broadcast(trackId, now, startedAt, keepDuration, elapsed));
        }
    }

    /** 广播曲目时长（秒），未知返回 0 喵 */
    public static int broadcastDuration(int channel) {
        Broadcast broadcast = BROADCASTS.get(channel);
        return broadcast == null ? 0 : broadcast.duration();
    }

    /** 收信进度（秒），没在收信返回 -1 喵 */
    public static double broadcastProgress(int channel) {
        Broadcast broadcast = BROADCASTS.get(channel);
        if (broadcast == null || broadcastTrack(channel) == null) {
            return -1;
        }
        return broadcast.elapsed() + (System.currentTimeMillis() - broadcast.updated()) / 1000.0;
    }

    /** 超过 60 秒没刷新的广播视为已停止（单机暂停时服务端会停推，留足余量） 喵 */
    public static String broadcastTrack(int channel) {
        Broadcast broadcast = BROADCASTS.get(channel);
        if (broadcast == null) {
            return null;
        }
        if (System.currentTimeMillis() - broadcast.updated() > 6_000L) {
            BROADCASTS.remove(channel);
            return null;
        }
        return broadcast.trackId();
    }

    public static void clear() {
        BROADCASTS.clear();
        FILES.clear();
        PENDING.clear();
    }

    private SpeakerAudioCache() {
    }
}
