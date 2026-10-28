package com.ginka.explainstone.speaker;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** 服务端音频存储：<存档>/explainstone/audio/<sha1>.ogg 喵 */
public final class SpeakerStorage {
    public static Path dir(MinecraftServer server) {
        Path path = server.getWorldPath(LevelResource.ROOT).resolve("explainstone").resolve("audio");
        try {
            Files.createDirectories(path);
        } catch (IOException ignored) {
        }
        return path;
    }

    public static String sha1(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (Exception e) {
            return Integer.toHexString(java.util.Arrays.hashCode(data));
        }
    }

    public static boolean write(MinecraftServer server, String sha1, byte[] data) {
        try {
            Files.write(dir(server).resolve(sha1 + ".ogg"), data);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static byte[] read(MinecraftServer server, String sha1) {
        if (!sha1.matches("[0-9a-f]{8,64}")) {
            return null;
        }
        try {
            Path file = dir(server).resolve(sha1 + ".ogg");
            return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static final java.util.Map<String, Integer> DURATIONS = new java.util.HashMap<>();

    /** 读 ogg 头部算时长（秒），结果缓存 喵 */
    public static int durationSeconds(MinecraftServer server, String sha1) {
        if (DURATIONS.containsKey(sha1)) {
            return DURATIONS.get(sha1);
        }
        byte[] data = read(server, sha1);
        int seconds = data == null ? -1 : parseSeconds(data);
        DURATIONS.put(sha1, seconds);
        return seconds;
    }

    private static int parseSeconds(byte[] data) {
        int rate = -1;
        for (int i = 0; i + 4 <= data.length; i++) {
            if (data[i] == 'O' && data[i + 1] == 'g' && data[i + 2] == 'g' && data[i + 3] == 'S') {
                int segments = data[i + 26] & 0xFF;
                int pkt = i + 27 + segments;
                if (pkt + 16 <= data.length && data[pkt] == 1) {
                    rate = (data[pkt + 12] & 0xFF) | ((data[pkt + 13] & 0xFF) << 8)
                            | ((data[pkt + 14] & 0xFF) << 16) | ((data[pkt + 15] & 0xFF) << 24);
                }
                break;
            }
        }
        if (rate <= 0) {
            return -1;
        }
        for (int i = data.length - 4; i >= 0; i--) {
            if (data[i] == 'O' && data[i + 1] == 'g' && data[i + 2] == 'g' && data[i + 3] == 'S' && i + 14 <= data.length) {
                long granule = 0L;
                for (int b = 7; b >= 0; b--) {
                    granule = (granule << 8) | (data[i + 6 + b] & 0xFF);
                }
                return granule > 0 ? (int) (granule / rate) : -1;
            }
        }
        return -1;
    }

    private SpeakerStorage() {
    }
}
