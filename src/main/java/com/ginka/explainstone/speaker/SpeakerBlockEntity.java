package com.ginka.explainstone.speaker;

import com.ginka.explainstone.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** 讲解石 / 颂美铃 / 广播发信器的方块实体（阶段 1：只存数据）喵 */
public class SpeakerBlockEntity extends BlockEntity {
    private final List<Track> tracks = new ArrayList<>();
    private boolean playing;
    private boolean paused;
    private int selected;
    private PlayMode mode = PlayMode.SEQUENCE;
    private boolean respectRedstone = true;
    private boolean lastPowered;
    private int channel = 234;
    private boolean receiveMode;
    private boolean speakerEnabled = true;
    private int volume = 100;
    private int audibleRange = 40;
    private int transmitRange = 512;
    private int timerSeconds;
    private int ticksLeft;
    /** 本次播放开始的服务端 tick 喵 */
    private long playStartedAt;
    /** 暂停时的进度（秒）喵 */
    private float pausedAt;
    /** 当前曲目时长（秒，-1 表示未知）喵 */
    private int durationSeconds = -1;

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPEAKER.get(), pos, state);
    }

    public List<Track> getTracks() {
        return this.tracks;
    }

    public void addTrack(Track track) {
        this.tracks.add(track);
        this.durationSeconds = -1;
        this.sync();
    }

    public void removeTrack(String id) {
        this.tracks.removeIf(track -> track.id().equals(id));
        this.durationSeconds = -1;
        this.sync();
    }

    public boolean isPlaying() {
        return this.playing;
    }

    public boolean isPaused() {
        return this.paused;
    }

    public void setPaused(boolean paused) {
        if (paused && this.level != null) {
            this.pausedAt = (this.level.getGameTime() - this.playStartedAt) / 20.0f;
        } else if (!paused && this.level != null) {
            this.playStartedAt = this.level.getGameTime() - (long) (this.pausedAt * 20);
        }
        this.paused = paused;
        this.sync();
    }

    /** 播放进度（秒），客户端界面画进度条用 喵 */
    public float progressSeconds() {
        if (!this.playing || this.level == null) {
            return 0.0f;
        }
        if (this.paused) {
            return this.pausedAt;
        }
        return (this.level.getGameTime() - this.playStartedAt) / 20.0f;
    }

    public int getKnownDuration() {
        return this.durationSeconds;
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
        if (playing) {
            this.playStartedAt = this.level == null ? 0 : this.level.getGameTime();
            this.durationSeconds = -1;  // 重新解析当前曲目时长 喵
        }
        if (this.level != null && !this.level.isClientSide) {
            var state = this.getBlockState();
            if (state.hasProperty(com.ginka.explainstone.SpeakerBlock.PLAYING)) {
                this.level.setBlock(this.worldPosition,
                        state.setValue(com.ginka.explainstone.SpeakerBlock.PLAYING, playing), 3);
            } else if (state.hasProperty(com.ginka.explainstone.BroadcastTransmitterBlock.BROADCASTING)) {
                this.level.setBlock(this.worldPosition,
                        state.setValue(com.ginka.explainstone.BroadcastTransmitterBlock.BROADCASTING, playing), 3);
            }
        }
        this.sync();
    }

    public int getSelected() {
        return this.tracks.isEmpty() ? 0 : Math.floorMod(this.selected, this.tracks.size());
    }

    public void setSelected(int index) {
        this.selected = index;
        this.durationSeconds = -1;      // 换曲目必须重新算时长 喵
        this.sync();
    }

    public PlayMode getMode() {
        return this.mode;
    }

    public void setMode(PlayMode mode) {
        this.mode = mode;
        this.sync();
    }

    public boolean isRespectRedstone() {
        return this.respectRedstone;
    }

    public void setRespectRedstone(boolean value) {
        this.respectRedstone = value;
        this.sync();
    }

    public boolean isLastPowered() {
        return this.lastPowered;
    }

    public void setLastPowered(boolean value) {
        this.lastPowered = value;
    }

    public int getChannel() {
        return this.channel;
    }

    public void setChannel(int channel) {
        this.channel = Math.floorMod(channel, 1000);
        this.sync();
    }

    public boolean isReceiveMode() {
        return this.receiveMode;
    }

    public void setReceiveMode(boolean value) {
        this.receiveMode = value;
        this.sync();
    }

    public boolean isSpeakerEnabled() {
        return this.speakerEnabled;
    }

    public void setSpeakerEnabled(boolean value) {
        this.speakerEnabled = value;
        this.sync();
    }

    public int getVolume() {
        return this.volume;
    }

    public void setVolume(int volume) {
        this.volume = Math.max(0, Math.min(100, volume));
        this.sync();
    }

    public int getAudibleRange() {
        return this.audibleRange;
    }

    public void setAudibleRange(int range) {
        this.audibleRange = Math.max(8, Math.min(48, range));
        this.sync();
    }

    public int getTransmitRange() {
        return this.transmitRange;
    }

    public void setTransmitRange(int range) {
        this.transmitRange = Math.max(32, Math.min(512, range));
        this.sync();
    }

    public int getTimerSeconds() {
        return this.timerSeconds;
    }

    public void setTimerSeconds(int seconds) {
        this.timerSeconds = Math.max(0, seconds);
        this.ticksLeft = this.timerSeconds * 20;
        this.sync();
    }

    public int getTicksLeft() {
        return this.ticksLeft;
    }

    public void setTicksLeft(int ticks) {
        this.ticksLeft = Math.max(0, ticks);
    }

    /** 服务端每 tick：定时倒计时 + 顺序/单曲/随机切歌 喵 */
    public void tickServer() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        if (this.ticksLeft > 0) {
            if (--this.ticksLeft == 0 && this.playing) {
                this.setPlaying(false);
            }
        }
        if (!this.playing || this.receiveMode || this.paused) {
            return;
        }
        if (this.durationSeconds <= 0) {
            var tracksNow = this.tracks;
            if (!tracksNow.isEmpty()) {
                int cached = tracksNow.get(this.getSelected()).duration();
                if (cached > 0) {
                    this.durationSeconds = cached;
                }
            }
            if (this.durationSeconds <= 0) {
                String id = this.currentTrackId();
                if (id.startsWith("file:") && this.level.getServer() != null) {
                    this.durationSeconds = SpeakerStorage.durationSeconds(this.level.getServer(), id.substring(5));
                }
            }
        }
        if (this.durationSeconds <= 0) {
            return;
        }
        if (this.durationSeconds < 10) {
            return;      // 时长解析异常（<10 秒）时不自动切歌，避免开头就停 喵
        }
        long elapsed = this.level.getGameTime() - this.playStartedAt;
        if (elapsed < this.durationSeconds * 20L) {
            return;
        }
        org.slf4j.LoggerFactory.getLogger("explainstone-be").info(
                "曲目结束 track={} 时长={}s 实际={}s 模式={} 列表={}",
                this.currentTrackId(), this.durationSeconds, elapsed / 20, this.mode, this.tracks.size());
        this.playStartedAt = this.level.getGameTime();
        switch (this.mode) {
            case REPEAT_ONE -> {
            }
            case SHUFFLE -> {
                if (this.tracks.size() <= 1) {
                    this.setPlaying(false);       // 只有一首，随机也没得换 → 播完停 喵
                    return;
                }
                this.selected = java.util.concurrent.ThreadLocalRandom.current().nextInt(this.tracks.size());
            }
            default -> {
                int next = this.getSelected() + 1;
                if (next >= this.tracks.size()) {
                    this.setPlaying(false);       // 顺序播放到最后一首 → 播完停 喵
                    return;
                }
                this.selected = next;
            }
        }
        this.durationSeconds = -1;
        this.sync();
    }

    public String currentTrackId() {
        return this.tracks.isEmpty() ? "" : this.tracks.get(this.getSelected()).id();
    }

    public int getDurationSeconds() {
        return this.durationSeconds;
    }

    public void sync() {
        this.setChanged();
        if (this.level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            // 方块状态没变时 sendBlockUpdated 不会带方块实体数据，这里手动发给附近玩家 喵
            var packet = net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
            for (net.minecraft.server.level.ServerPlayer player : serverLevel.players()) {
                if (player.distanceToSqr(this.worldPosition.getX() + 0.5,
                        this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5) <= 64.0 * 64.0) {
                    player.connection.send(packet);
                }
            }
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (Track track : this.tracks) {
            list.add(track.toNbt());
        }
        tag.put("tracks", list);
        tag.putInt("selected", this.selected);
        tag.putString("mode", this.mode.asString());
        tag.putBoolean("respectRedstone", this.respectRedstone);
        tag.putBoolean("lastPowered", this.lastPowered);
        tag.putInt("channel", this.channel);
        tag.putBoolean("receiveMode", this.receiveMode);
        tag.putBoolean("speakerEnabled", this.speakerEnabled);
        tag.putInt("volume", this.volume);
        tag.putInt("audibleRange", this.audibleRange);
        tag.putInt("transmitRange", this.transmitRange);
        tag.putInt("timerSeconds", this.timerSeconds);
        tag.putBoolean("playing", this.playing);
        tag.putBoolean("paused", this.paused);
        tag.putLong("playStartedAt", this.playStartedAt);
        tag.putInt("durationSeconds", this.durationSeconds);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.tracks.clear();
        ListTag list = tag.getList("tracks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            this.tracks.add(Track.fromNbt(list.getCompound(i)));
        }
        this.selected = tag.getInt("selected");
        this.mode = PlayMode.byName(tag.getString("mode"));
        if (tag.contains("respectRedstone")) {
            this.respectRedstone = tag.getBoolean("respectRedstone");
        }
        if (tag.contains("lastPowered")) {
            this.lastPowered = tag.getBoolean("lastPowered");
        }
        if (tag.contains("channel")) {
            this.channel = Math.floorMod(tag.getInt("channel"), 1000);
        }
        if (tag.contains("receiveMode")) {
            this.receiveMode = tag.getBoolean("receiveMode");
        }
        if (tag.contains("speakerEnabled")) {
            this.speakerEnabled = tag.getBoolean("speakerEnabled");
        }
        if (tag.contains("volume")) {
            this.volume = Math.max(0, Math.min(100, tag.getInt("volume")));
        }
        if (tag.contains("audibleRange")) {
            this.audibleRange = Math.max(8, Math.min(48, tag.getInt("audibleRange")));
        }
        if (tag.contains("transmitRange")) {
            this.transmitRange = Math.max(32, Math.min(512, tag.getInt("transmitRange")));
        }
        if (tag.contains("timerSeconds")) {
            this.timerSeconds = Math.max(0, tag.getInt("timerSeconds"));
        }
        this.playing = tag.getBoolean("playing");
        this.paused = tag.getBoolean("paused");
        this.playStartedAt = tag.getLong("playStartedAt");
        this.durationSeconds = tag.contains("durationSeconds") ? tag.getInt("durationSeconds") : -1;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
