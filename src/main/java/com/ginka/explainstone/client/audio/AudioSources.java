package com.ginka.explainstone.client.audio;

import com.mojang.blaze3d.audio.OggAudioStream;
import net.minecraft.client.Minecraft;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** ogg 解码缓存（服务端下发的字节 → PCM）喵 */
public final class AudioSources {
    public record Pcm(java.nio.ByteBuffer data, int channels, float rate) {
    }

    private static final Map<String, Pcm> CACHE = new ConcurrentHashMap<>();

    public static Pcm get(String sha1, byte[] ogg) {
        if (sha1 == null || ogg == null) {
            return null;
        }
        return CACHE.computeIfAbsent(sha1, key -> {
            try (OggAudioStream stream = new OggAudioStream(new ByteArrayInputStream(ogg))) {
                var format = stream.getFormat();
                var buffer = stream.readAll();
                return new Pcm(buffer, Math.max(1, format.getChannels()), format.getSampleRate());
            } catch (Exception e) {
                return null;
            }
        });
    }

    /** 解码后算总时长（秒），未知返回 -1 喵 */
    public static double seconds(String sha1, byte[] ogg) {
        Pcm pcm = get(sha1, ogg);
        if (pcm == null || pcm.rate() <= 0) {
            return -1;
        }
        return pcm.data().remaining() / (double) (pcm.channels() * 2) / pcm.rate();
    }

    public static void clear() {
        CACHE.clear();
    }

    private AudioSources() {
    }
}
