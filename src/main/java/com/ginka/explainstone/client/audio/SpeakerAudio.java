package com.ginka.explainstone.client.audio;

import com.ginka.explainstone.speaker.SpeakerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;

import java.util.HashMap;
import java.util.Map;

/** 自建 OpenAL 播放引擎（Forge 1.20.1 版，阶段 3：单曲播放 + 距离衰减）喵 */
public final class SpeakerAudio {
    private static long device;
    private static long context;
    private static boolean available;
    private static boolean initialised;
    private static boolean warned;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("explainstone-audio");

    private static final Map<BlockPos, Voice> VOICES = new HashMap<>();
    /** 曲目 id → OpenAL buffer（多台音响共享，省内存也避免 buffer 泄漏）喵 */
    private static final Map<String, Integer> BUFFERS = new HashMap<>();
    /** 本地暂停（点暂停立刻生效，不等服务端回包）喵 */
    private static final java.util.Set<BlockPos> LOCAL_PAUSED = new java.util.HashSet<>();
    private static int debugTicker;
    /** 引擎时钟（tick），用于多台音响对齐进度 喵 */
    private static long clock;
    /** 同一首歌的公共起播时刻 喵 */
    private static final Map<String, Long> TRACK_START = new HashMap<>();
    /** 每 2 tick 扫一次，按键后更快出声 喵 */
    private static int scanCooldown;
    /** 粒子节拍 喵 */
    private static int particleTick;
    private static final java.util.Random RANDOM = new java.util.Random();

    private static final class Voice {
        int source;
        int buffer;
        String track = "";
        long lastSeen;
        int misses;
        double seconds;
    }

    public static boolean available() {
        return available;
    }

    private static void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        try {
            device = ALC10.alcOpenDevice((CharSequence) null);
            if (device == 0L) {
                return;
            }
            context = ALC10.alcCreateContext(device, (int[]) null);
            if (context == 0L || !ALC10.alcMakeContextCurrent(context)) {
                return;
            }
            available = true;
            LOGGER.info("OpenAL 初始化成功（讲解石音频引擎）");
        } catch (Throwable error) {
            LOGGER.warn("OpenAL 初始化失败: {}", error.toString());
            available = false;
        }
    }

    public static void tick(Minecraft client) {
        init();
        if (!available || client.level == null || client.player == null) {
            if (!available && !warned && client.player != null) {
                warned = true;
                client.player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.explainstone.audio.unavailable"), false);
            }
            return;
        }
        clock++;
        if (--scanCooldown > 0) {
            return;
        }
        scanCooldown = 2;
        float master = client.options.getSoundSourceVolume(SoundSource.RECORDS);
        Vec3 ear = client.gameRenderer.getMainCamera().getPosition();
        Map<BlockPos, Boolean> seen = new HashMap<>();
        var chunkSource = client.level.getChunkSource();
        var chunkPos = client.player.chunkPosition();

        // 先扫一遍：频道 → 正在播放的发信器（客户端本地判断，发信器没了就立刻没有）喵
        Map<Integer, SpeakerBlockEntity> activeTransmitters = new HashMap<>();
        for (int cx = chunkPos.x - 5; cx <= chunkPos.x + 5; cx++) {
            for (int cz = chunkPos.z - 5; cz <= chunkPos.z + 5; cz++) {
                var chunk = chunkSource.getChunk(cx, cz, false);
                if (chunk == null) {
                    continue;
                }
                for (var blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof SpeakerBlockEntity tx
                            && tx.getBlockState().getBlock() instanceof com.ginka.explainstone.BroadcastTransmitterBlock
                            && tx.isPlaying() && !tx.getTracks().isEmpty()) {
                        activeTransmitters.put(tx.getChannel(), tx);
                    }
                }
            }
        }

        for (int cx = chunkPos.x - 5; cx <= chunkPos.x + 5; cx++) {
            for (int cz = chunkPos.z - 5; cz <= chunkPos.z + 5; cz++) {
                var chunk = chunkSource.getChunk(cx, cz, false);
                if (chunk == null) {
                    continue;
                }
                for (var blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof SpeakerBlockEntity speaker)) {
                        continue;
                    }
                    BlockPos pos = speaker.getBlockPos().immutable();
                    if (!speaker.isSpeakerEnabled()) {
                        missAt(pos);
                        continue;
                    }
                    // 发信器只发信，自己不出声 喵
                    if (speaker.getBlockState().getBlock()
                            instanceof com.ginka.explainstone.BroadcastTransmitterBlock) {
                        missAt(pos);
                        continue;
                    }
                    prefetch(speaker);
                    String id;
                    if (speaker.isReceiveMode()) {
                        SpeakerBlockEntity tx = activeTransmitters.get(speaker.getChannel());
                        if (tx != null) {
                            id = tx.getTracks().get(tx.getSelected()).id();
                        } else {
                            // 发信器离得太远（超出扫描范围）→ 用服务端广播包 喵
                            id = SpeakerAudioCache.broadcastTrack(speaker.getChannel());
                            if (id == null) {
                                missAt(pos);
                                continue;
                            }
                        }
                    } else {
                        if (!speaker.isPlaying()) {
                            missAt(pos);
                            continue;
                        }
                        var tracks = speaker.getTracks();
                        if (tracks.isEmpty()) {
                            continue;
                        }
                        id = tracks.get(speaker.getSelected()).id();
                    }
                    Voice voice = VOICES.computeIfAbsent(pos, p -> new Voice());
                    voice.misses = 0;
                    seen.put(pos, true);
                    spawnNotes(client, pos);

                    double dist = Math.sqrt(ear.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
                    float range = Math.max(1, speaker.getAudibleRange());
                    float falloff = (float) Math.max(0.0, 1.0 - dist / range);
                    if (falloff > 0.0f && dist < range) {
                        falloff = Math.max(falloff, 0.12f);   // 范围内至少留一点音量 喵
                    }
                    float gain = falloff * master * (speaker.getVolume() / 100.0f);

                    boolean othersPlaying = VOICES.values().stream()
                            .anyMatch(v -> id.equals(v.track) && v.source != 0
                                    && AL10.alGetSourcei(v.source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING);
                    if (!id.equals(voice.track)) {
                        stopVoice(voice);
                        byte[] ogg = SpeakerAudioCache.ogg(id);
                        if (ogg == null) {
                            if (id.startsWith("file:")) {
                                String sha1 = id.substring(5);
                                if (!SpeakerAudioCache.isPending(sha1)) {
                                    SpeakerAudioCache.request(sha1);
                                    com.ginka.explainstone.network.SpeakerNetwork.sendToServer(
                                            com.ginka.explainstone.network.SpeakerPacket.audioRequest(sha1));
                                }
                            }
                            continue;
                        }
                        var pcm = AudioSources.get(id, ogg);
                        if (pcm == null) {
                            LOGGER.warn("解码失败: {} ({} 字节)", id, ogg.length);
                            continue;
                        }
                        LOGGER.info("开始播放 {}，PCM {} KB", id, pcm.data().remaining() / 1024);
                        load(voice, pcm, id);
                        double seconds = pcm.data().remaining() / (double) (pcm.channels() * 2) / pcm.rate();
                        voice.seconds = seconds;
                        double offset;
                        if (speaker.isReceiveMode()) {
                            // 收信：近距离用发信器本地进度，远距离用服务端广播包进度 喵
                            SpeakerBlockEntity tx = activeTransmitters.get(speaker.getChannel());
                            offset = tx != null
                                    ? Math.max(0.0, tx.progressSeconds())
                                    : Math.max(0.0, SpeakerAudioCache.broadcastProgress(speaker.getChannel()));
                            if (offset > seconds * 0.98) {
                                offset = 0.0;
                            }
                        } else if (!othersPlaying) {
                            // 第一次为这台音响起播：从头放，并把公共起播时刻对齐到现在 喵
                            offset = 0.0;
                            TRACK_START.put(id, clock);
                        } else {
                            long start = TRACK_START.computeIfAbsent(id, key -> clock);
                            offset = ((clock - start) / 20.0) % Math.max(0.5, seconds);
                            if (offset > seconds * 0.95) {
                                offset = 0.0;
                                TRACK_START.put(id, clock);
                            }
                        }
                        AL11.alSourcef(voice.source, AL11.AL_SEC_OFFSET, (float) offset);
                        voice.track = id;
                        AL10.alSourcePlay(voice.source);
                    }
                    if (voice.source != 0) {
                        if (++debugTicker % 100 == 0) {
                            LOGGER.info("播放中 pos={} ch={} recv={} gain={} voices={}",
                                    pos, speaker.getChannel(), speaker.isReceiveMode(),
                                    String.format("%.2f", gain), VOICES.size());
                        }
                        AL10.alSourcef(voice.source, AL10.AL_GAIN, Math.max(0.0f, Math.min(1.0f, gain)));
                        int state = AL10.alGetSourcei(voice.source, AL10.AL_SOURCE_STATE);
                        if (speaker.isPaused() || LOCAL_PAUSED.contains(pos)) {
                            AL10.alSourcePause(voice.source);
                        } else if (state == AL10.AL_STOPPED) {
                            // 播完一轮：从头重播，并把公共起播时刻重置 喵
                            TRACK_START.put(id, clock);
                            AL11.alSourcef(voice.source, AL11.AL_SEC_OFFSET, 0.0f);
                            AL10.alSourcePlay(voice.source);
                        } else if (state != AL10.AL_PLAYING) {
                            AL10.alSourcePlay(voice.source);
                        }
                    }
                }
            }
        }
        var iterator = VOICES.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!seen.containsKey(entry.getKey())) {
                stopVoice(entry.getValue());
                iterator.remove();
            }
        }
    }

    /** 提前把曲目音频下载下来，按播放时就不用等 喵 */
    private static void prefetch(SpeakerBlockEntity speaker) {
        var tracks = speaker.getTracks();
        if (tracks.isEmpty()) {
            return;
        }
        String id = tracks.get(Math.min(speaker.getSelected(), tracks.size() - 1)).id();
        if (id.startsWith("file:") && SpeakerAudioCache.ogg(id) == null) {
            String sha1 = id.substring(5);
            if (!SpeakerAudioCache.isPending(sha1)) {
                SpeakerAudioCache.request(sha1);
                com.ginka.explainstone.network.SpeakerNetwork.sendToServer(
                        com.ginka.explainstone.network.SpeakerPacket.audioRequest(sha1));
            }
        }
    }

    /** 播放时在方块上方冒音符粒子 喵 */
    private static void spawnNotes(Minecraft client, BlockPos pos) {
        if (++particleTick % 3 != 0) {
            return;
        }
        // 正上方一格 喵
        double x = pos.getX() + 0.5 + (RANDOM.nextDouble() - 0.5) * 0.4;
        double y = pos.getY() + 1.05 + RANDOM.nextDouble() * 0.2;
        double z = pos.getZ() + 0.5 + (RANDOM.nextDouble() - 0.5) * 0.4;
        float color = RANDOM.nextFloat();
        client.level.addParticle(net.minecraft.core.particles.ParticleTypes.NOTE, x, y, z, color, 0.0, 0.0);
    }

    private static void load(Voice voice, AudioSources.Pcm pcm, String id) {
        voice.buffer = BUFFERS.computeIfAbsent(id, key -> {
            int buffer = AL10.alGenBuffers();
            int fmt = pcm.channels() > 1 ? AL10.AL_FORMAT_STEREO16 : AL10.AL_FORMAT_MONO16;
            AL10.alBufferData(buffer, fmt, pcm.data(), (int) pcm.rate());
            return buffer;
        });
        voice.source = AL10.alGenSources();
        AL10.alSourcei(voice.source, AL10.AL_BUFFER, voice.buffer);
        AL10.alSourcei(voice.source, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
        AL10.alSourcef(voice.source, AL10.AL_ROLLOFF_FACTOR, 0.0f);
        AL10.alSource3f(voice.source, AL10.AL_POSITION, 0.0f, 0.0f, 0.0f);
        AL10.alSourcef(voice.source, AL11.AL_SEC_OFFSET, 0.0f);
    }

    /** 本地暂停切换（返回切换后的状态）喵 */
    public static boolean toggleLocalPause(BlockPos pos) {
        pos = pos.immutable();
        boolean paused = !LOCAL_PAUSED.remove(pos);
        setLocalPause(pos, paused);
        return paused;
    }

    /** 立即设置本地暂停，不等下一帧扫描 喵 */
    public static void setLocalPause(BlockPos pos, boolean paused) {
        pos = pos.immutable();
        if (paused) {
            LOCAL_PAUSED.add(pos);
        } else {
            LOCAL_PAUSED.remove(pos);
        }
        Voice voice = VOICES.get(pos);
        if (voice != null && voice.source != 0) {
            if (paused) {
                AL10.alSourcePause(voice.source);
            } else {
                AL10.alSourcePlay(voice.source);
            }
        }
        LOGGER.info("本地暂停 pos={} paused={} 有声源={}", pos, paused, voice != null && voice.source != 0);
    }

    public static boolean isLocallyPaused(BlockPos pos) {
        return LOCAL_PAUSED.contains(pos);
    }

    /** 方块被移除/停止时清掉本地暂停 喵 */
    public static void clearLocalPause(BlockPos pos) {
        LOCAL_PAUSED.remove(pos);
    }

    /** 该方块正在播的曲目总时长（没在播返回 -1）喵 */
    public static double duration(BlockPos pos) {
        Voice voice = VOICES.get(pos);
        return voice == null ? -1 : voice.seconds;
    }

    /** 当前播放到第几秒（没在播返回 -1）喵 */
    public static float progress(BlockPos pos) {
        Voice voice = VOICES.get(pos);
        if (voice == null || voice.source == 0) {
            return -1.0f;
        }
        return AL11.alGetSourcef(voice.source, AL11.AL_SEC_OFFSET);
    }

    /** 连续几次没条件才真正停，避免同步抖动导致断音 喵 */
    private static void missAt(BlockPos pos) {
        Voice voice = VOICES.get(pos);
        if (voice == null) {
            return;
        }
        if (++voice.misses >= 3) {
            stopAt(pos);
        }
    }

    private static void stopAt(BlockPos pos) {
        Voice voice = VOICES.remove(pos);
        if (voice != null) {
            String id = voice.track;
            stopVoice(voice);
            // 没有其它音响在放同一首了，就清掉公共起播时刻，下次从头开始 喵
            if (id != null && !id.isEmpty()
                    && VOICES.values().stream().noneMatch(v -> id.equals(v.track))) {
                TRACK_START.remove(id);
            }
        }
    }

    private static void stopVoice(Voice voice) {
        if (voice.source != 0) {
            AL10.alSourceStop(voice.source);
            AL10.alDeleteSources(voice.source);
            voice.source = 0;
        }
        // buffer 是共享的，由 BUFFERS 统一管理，这里不删 喵
        voice.buffer = 0;
    }

    private SpeakerAudio() {
    }
}
