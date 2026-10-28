package com.ginka.explainstone.client;

import com.ginka.explainstone.BroadcastTransmitterBlock;
import com.ginka.explainstone.network.SpeakerNetwork;
import com.ginka.explainstone.network.SpeakerPacket;
import com.ginka.explainstone.speaker.SpeakerBlockEntity;
import com.ginka.explainstone.speaker.Track;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/** 讲解石 / 颂美铃 / 发信器界面（Forge 版）喵 */
public class SpeakerScreen extends Screen {
    private static final int PANEL_W = 300;
    private static final int ROW_H = 12;
    private static final int BTN_H = 18;

    private final BlockPos pos;
    private int left;
    private int top;
    private int listTop;
    private int listRows = 5;
    private int scroll;
    private int channelY;
    private boolean receiveTab;
    private boolean tabInitialised;

    private Button playButton;
    private Button pauseButton;
    private Button stopButton;
    private Button modeButton;
    private Button fourthButton;
    private Button enableButton;

    public SpeakerScreen(BlockPos pos) {
        super(Component.translatable("screen.explainstone.title.speaker"));
        this.pos = pos.immutable();     // 防止 MutableBlockPos 被改掉导致查表失败 喵
    }

    String posString() {
        return this.pos.getX() + "," + this.pos.getY() + "," + this.pos.getZ();
    }

    private SpeakerBlockEntity speaker() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        BlockEntity be = this.minecraft.level.getBlockEntity(this.pos);
        return be instanceof SpeakerBlockEntity s ? s : null;
    }

    private boolean transmitter() {
        return this.minecraft != null && this.minecraft.level != null
                && this.minecraft.level.getBlockState(this.pos).getBlock() instanceof BroadcastTransmitterBlock;
    }

    private void send(int action, int value, String text) {
        SpeakerNetwork.sendToServer(SpeakerPacket.action(action, value,
                this.pos.getX() + "," + this.pos.getY() + "," + this.pos.getZ() + "|" + text));
        this.refresh();
    }

    @Override
    public void tick() {
        super.tick();
        this.refresh();
    }

    @Override
    protected void init() {
        SpeakerBlockEntity be = this.speaker();
        boolean tx = this.transmitter();
        if (be != null && !tx && !this.tabInitialised) {
            this.receiveTab = be.isReceiveMode();
            this.tabInitialised = true;
        }
        this.left = (this.width - PANEL_W) / 2;
        int gap = 4;
        int half = (PANEL_W - 8) / 2;
        int height = 250;
        this.top = Math.max(6, (this.height - height) / 2);
        int y = this.top + 20;

        if (!tx) {
            Button tabSelf = this.addRenderableWidget(Button.builder(
                    Component.translatable(this.receiveTab ? "screen.explainstone.tab.self" : "screen.explainstone.tab.self.active"),
                    b -> {
                        if (this.receiveTab) {
                            this.send(7, 0, "");
                            this.receiveTab = false;
                            this.rebuildWidgets();
                        }
                    }).bounds(this.left, y, half, BTN_H).build());
            Button tabReceive = this.addRenderableWidget(Button.builder(
                    Component.translatable(this.receiveTab ? "screen.explainstone.tab.receive.active" : "screen.explainstone.tab.receive"),
                    b -> {
                        if (!this.receiveTab) {
                            this.send(7, 0, "");
                            this.receiveTab = true;
                            this.rebuildWidgets();
                        }
                    }).bounds(this.left + half + 8, y, half, BTN_H).build());
            tabSelf.active = this.receiveTab;
            tabReceive.active = !this.receiveTab;
            y += BTN_H + gap;
        }

        if (!this.receiveTab) {
            int third = (PANEL_W - 8) / 3;
            this.playButton = this.addRenderableWidget(Button.builder(
                    Component.translatable("screen.explainstone.button.play"),
                    b -> this.send(0, 0, "")).bounds(this.left, y, third, BTN_H).build());
            this.pauseButton = this.addRenderableWidget(Button.builder(
                    Component.translatable("screen.explainstone.button.pause"),
                    b -> {
                        // 本机立即暂停（保证听感），同时通知服务端暂停计时，避免自动切歌 喵
                        com.ginka.explainstone.client.audio.SpeakerAudio.toggleLocalPause(this.pos);
                        this.send(13, 0, "");
                        this.refresh();
                    }).bounds(this.left + third + 4, y, third, BTN_H).build());
            if (tx) {
                this.pauseButton.active = false;   // 发信器没有本地音源，暂停无意义 喵
            }
            this.stopButton = this.addRenderableWidget(Button.builder(
                    Component.translatable("screen.explainstone.button.stop"),
                    b -> this.send(1, 0, "")).bounds(this.left + 2 * (third + 4), y, third, BTN_H).build());
            y += BTN_H + gap;
        } else {
            this.playButton = null;
            this.pauseButton = null;
            this.stopButton = null;
        }

        this.listTop = y;
        this.listRows = Math.max(3, (this.top + height - 150 - this.listTop) / ROW_H);
        y = this.listTop + this.listRows * ROW_H + gap;

        if (!this.receiveTab) {
            this.modeButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
                this.send(2, 0, "");
                this.refresh();
            }).bounds(this.left, y, half, BTN_H).build());
            this.fourthButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
                if (tx) {
                    int current = this.speaker() == null ? 0 : this.speaker().getTimerSeconds();
                    int next = current == 0 ? 60 : current == 60 ? 180 : current == 180 ? 600 : 0;
                    this.send(12, next, "");
                } else {
                    this.send(3, 0, "");
                }
                this.refresh();
            }).bounds(this.left + half + 8, y, half, BTN_H).build());
            y += BTN_H + gap;

            if (tx) {
                this.channelY = y;
                this.addRenderableWidget(Button.builder(Component.literal("−"), b -> {
                    SpeakerBlockEntity s = this.speaker();
                    if (s != null) {
                        this.send(6, Math.floorMod(s.getChannel() - 1, 1000), "");
                        this.refresh();
                    }
                }).bounds(this.left + half + 8, y, 20, BTN_H).build());
                this.addRenderableWidget(Button.builder(Component.literal("+"), b -> {
                    SpeakerBlockEntity s = this.speaker();
                    if (s != null) {
                        this.send(6, Math.floorMod(s.getChannel() + 1, 1000), "");
                        this.refresh();
                    }
                }).bounds(this.left + PANEL_W - 20, y, 20, BTN_H).build());
                y += BTN_H + gap;
            }

            int volume = be == null ? 100 : be.getVolume();
            this.addRenderableWidget(new IntSlider(this.left, y, PANEL_W, BTN_H,
                    Component.translatable("screen.explainstone.slider.volume").getString(), 0, 100, volume,
                    v -> this.send(9, v, "")));
            y += BTN_H + 2;

            if (tx) {
                int range = be == null ? 512 : be.getTransmitRange();
                this.addRenderableWidget(new IntSlider(this.left, y, PANEL_W, BTN_H,
                        Component.translatable("screen.explainstone.slider.range").getString(), 32, 512, range,
                        v -> this.send(11, v, "")));
            } else {
                int audible = be == null ? 40 : be.getAudibleRange();
                this.addRenderableWidget(new IntSlider(this.left, y, PANEL_W, BTN_H,
                        Component.translatable("screen.explainstone.slider.distance").getString(), 8, 48, audible,
                        v -> this.send(10, v, "")));
            }
            y += BTN_H + gap;

            this.addRenderableWidget(Button.builder(Component.translatable("screen.explainstone.button.import"),
                    b -> SpeakerImport.choose(this)).bounds(this.left, y, half, BTN_H).build());
            this.addRenderableWidget(Button.builder(Component.translatable("screen.explainstone.button.remove"), b -> {
                SpeakerBlockEntity s = this.speaker();
                if (s != null && !s.getTracks().isEmpty()) {
                    this.send(5, 0, s.getTracks().get(s.getSelected()).id());
                    this.refresh();
                }
            }).bounds(this.left + half + 8, y, half, BTN_H).build());
        } else {
            y += 22;                       // 给「收信模式 · 只收听频道广播」两行说明留位置 喵
            this.channelY = y;
            this.addRenderableWidget(Button.builder(Component.literal("−"), b -> {
                SpeakerBlockEntity s = this.speaker();
                if (s != null) {
                    this.send(6, Math.floorMod(s.getChannel() - 1, 1000), "");
                    this.refresh();
                }
            }).bounds(this.left + 60, y, 20, BTN_H).build());
            this.addRenderableWidget(Button.builder(Component.literal("+"), b -> {
                SpeakerBlockEntity s = this.speaker();
                if (s != null) {
                    this.send(6, Math.floorMod(s.getChannel() + 1, 1000), "");
                    this.refresh();
                }
            }).bounds(this.left + PANEL_W - 80, y, 20, BTN_H).build());
            y += BTN_H + gap + 22;      // 给进度条留出 22px 喵
            this.enableButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
                this.send(8, 0, "");
                this.refresh();
            }).bounds(this.left, y, PANEL_W, BTN_H).build());
            y += BTN_H + 2;
            int volume = be == null ? 100 : be.getVolume();
            this.addRenderableWidget(new IntSlider(this.left, y, PANEL_W, BTN_H,
                    Component.translatable("screen.explainstone.slider.volume").getString(), 0, 100, volume,
                    v -> this.send(9, v, "")));
            y += BTN_H + 2;
            int audible = be == null ? 40 : be.getAudibleRange();
            this.addRenderableWidget(new IntSlider(this.left, y, PANEL_W, BTN_H,
                    Component.translatable("screen.explainstone.slider.distance").getString(), 8, 48, audible,
                    v -> this.send(10, v, "")));
        }
        this.refresh();
    }

    private void refresh() {
        SpeakerBlockEntity be = this.speaker();
        if (be == null) {
            return;
        }
        boolean tx = this.transmitter();
        if (this.playButton != null) {
            boolean playing = be.isPlaying();
            this.playButton.setMessage(playing
                    ? Component.literal("▶ ").withStyle(net.minecraft.ChatFormatting.GREEN)
                        .append(Component.translatable("screen.explainstone.button.play.playing"))
                    : Component.translatable("screen.explainstone.button.play"));
            this.stopButton.setMessage(Component.translatable("screen.explainstone.button.stop"));
            if (this.pauseButton != null) {
                boolean paused = com.ginka.explainstone.client.audio.SpeakerAudio.isLocallyPaused(this.pos);
                this.pauseButton.setMessage(paused
                        ? Component.translatable("screen.explainstone.button.resume")
                        : Component.translatable("screen.explainstone.button.pause"));
                this.pauseButton.active = !this.transmitter();   // 讲解石/颂美铃可暂停，发信器没有本地音源 喵
            }
        }
        if (this.modeButton != null) {
            this.modeButton.setMessage(Component.translatable("screen.explainstone.mode.label",
                    Component.translatable(be.getMode().getTranslationKey())));
        }
        if (this.fourthButton != null) {
            if (tx) {
                int timer = be.getTimerSeconds();
                this.fourthButton.setMessage(timer <= 0
                        ? Component.translatable("screen.explainstone.timer.off")
                        : Component.translatable("screen.explainstone.timer.minutes", timer / 60));
            } else {
                this.fourthButton.setMessage(be.isRespectRedstone()
                        ? Component.translatable("screen.explainstone.redstone.detect")
                        : Component.translatable("screen.explainstone.redstone.ignore"));
            }
        }
        if (this.enableButton != null) {
            this.enableButton.setMessage(be.isSpeakerEnabled()
                    ? Component.translatable("screen.explainstone.enable.on")
                    : Component.translatable("screen.explainstone.enable.off"));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        SpeakerBlockEntity be = this.speaker();
        Component title = Component.translatable(this.transmitter()
                ? "screen.explainstone.title.transmitter"
                : (this.minecraft != null && this.minecraft.level != null
                        && this.minecraft.level.getBlockState(this.pos).getBlock() instanceof com.ginka.explainstone.SongmelynBlock
                        ? "screen.explainstone.title.songmelyn" : "screen.explainstone.title.speaker"));
        graphics.fill(this.left - 8, this.top - 8, this.left + PANEL_W + 8, this.top + 250, 0x90000000);
        graphics.drawCenteredString(this.font, title, this.width / 2, this.top - 2, 0xFFFFFF);
        if (be == null) {
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        if (this.receiveTab) {
            graphics.drawCenteredString(this.font, Component.translatable("screen.explainstone.receive.title"),
                    this.width / 2, this.listTop + 2, 0x9BE8C8);
            graphics.drawCenteredString(this.font, Component.translatable("screen.explainstone.receive.hint"),
                    this.width / 2, this.listTop + 12, 0x909090);
            String channel = "350." + String.format("%03d", be.getChannel());
            graphics.drawCenteredString(this.font, Component.literal(channel), this.width / 2, this.channelY + 4, 0xFFFFFF);

            // 收信进度条（只展示，没有任何控制按钮）喵
            double progressRaw = com.ginka.explainstone.client.audio.SpeakerAudio.progress(this.pos);
            double progress = progressRaw;
            if (progress >= 0) {
                int barY = this.channelY + 26;
                graphics.fill(this.left, barY, this.left + PANEL_W, barY + 4, 0xFF303030);
                double total = 0;
                String trackId = com.ginka.explainstone.client.audio.SpeakerAudioCache
                        .broadcastTrack(be.getChannel());
                byte[] ogg = trackId == null ? null
                        : com.ginka.explainstone.client.audio.SpeakerAudioCache.ogg(trackId);
                if (ogg != null && trackId != null && trackId.startsWith("file:")) {
                    total = com.ginka.explainstone.client.audio.AudioSources.seconds(trackId.substring(5), ogg);
                }
                if (total <= 0) {
                    total = com.ginka.explainstone.client.audio.SpeakerAudio.duration(this.pos);
                }
                if (total <= 0) {
                    total = com.ginka.explainstone.client.audio.SpeakerAudioCache.broadcastDuration(be.getChannel());
                }
                if (total > 0) {
                    progress = progressRaw % total;      // 循环播放时进度回到 0 喵
                    int width = (int) (Math.min(1.0, progress / total) * PANEL_W);
                    graphics.fill(this.left, barY, this.left + width, barY + 4, 0xFF6FD8A8);
                    String time = String.format("%d:%02d / %d:%02d", (int) progress / 60, (int) progress % 60,
                            (int) total / 60, (int) total % 60);
                    graphics.drawCenteredString(this.font, Component.literal(time),
                            this.width / 2, barY + 8, 0xA0A0A0);
                }
            }
        } else {
            List<Track> tracks = be.getTracks();
            if (tracks.isEmpty()) {
                graphics.drawString(this.font, Component.translatable("screen.explainstone.list.empty"),
                        this.left + 4, this.listTop + 2, 0xA0A0A0, false);
            }
            for (int i = 0; i < Math.min(this.listRows, Math.max(0, tracks.size() - this.scroll)); i++) {
                int index = this.scroll + i;
                boolean selected = index == be.getSelected();
                String label = tracks.get(index).label();
                String duration = durationLabel(tracks.get(index));
                graphics.drawString(this.font, (selected ? "▶ " : "   ") + label,
                        this.left + 4, this.listTop + 2 + i * ROW_H, selected ? 0x9BE8C8 : 0xFFFFFF, false);
                if (!duration.isEmpty()) {
                    graphics.drawString(this.font, duration, this.left + PANEL_W - 40, this.listTop + 2 + i * ROW_H, 0xA0A0A0, false);
                }
            }
            if (be.isPlaying()) {
                float audioProgress = com.ginka.explainstone.client.audio.SpeakerAudio.progress(this.pos);
                float progress = audioProgress >= 0 ? audioProgress : Math.max(0.0f, be.progressSeconds());
                if (progress >= 0) {
                    int barY = this.listTop + this.listRows * ROW_H + 1;
                    graphics.fill(this.left, barY, this.left + PANEL_W, barY + 3, 0xFF303030);
                    double total = be.getKnownDuration();
                    if (total <= 0) {
                        var tracks2 = be.getTracks();
                        if (!tracks2.isEmpty()) {
                            String id = tracks2.get(be.getSelected()).id();
                            byte[] ogg = com.ginka.explainstone.client.audio.SpeakerAudioCache.ogg(id);
                            if (ogg != null && id.startsWith("file:")) {
                                total = com.ginka.explainstone.client.audio.AudioSources.seconds(id.substring(5), ogg);
                            }
                        }
                    }
                    if (total > 0) {
                        int width = (int) (Math.min(1.0, progress / total) * PANEL_W);
                        graphics.fill(this.left, barY, this.left + width, barY + 3, 0xFF6FD8A8);
                    }
                }
            }
            if (this.transmitter()) {
                String channel = "350." + String.format("%03d", be.getChannel());
                graphics.drawCenteredString(this.font, Component.literal(channel), this.left + 227, this.channelY + 5, 0xFFFFFF);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private String durationLabel(Track track) {
        String id = track.id();
        if (!id.startsWith("file:")) {
            return "";
        }
        byte[] ogg = com.ginka.explainstone.client.audio.SpeakerAudioCache.ogg(id);
        if (ogg == null) {
            return "";
        }
        double seconds = com.ginka.explainstone.client.audio.AudioSources.seconds(id.substring(5), ogg);
        if (seconds <= 0) {
            return "";
        }
        return String.format("%d:%02d", (int) seconds / 60, (int) seconds % 60);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        SpeakerBlockEntity be = this.speaker();
        if (be == null || this.receiveTab) {
            return false;
        }
        int max = Math.max(0, be.getTracks().size() - this.listRows);
        this.scroll = Math.max(0, Math.min(max, this.scroll + (delta < 0 ? 1 : -1)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        SpeakerBlockEntity be = this.speaker();
        if (be != null && !this.receiveTab && mouseY >= this.listTop && mouseY < this.listTop + this.listRows * ROW_H
                && mouseX >= this.left && mouseX <= this.left + PANEL_W) {
            int index = this.scroll + (int) ((mouseY - this.listTop) / ROW_H);
            if (index >= 0 && index < be.getTracks().size()) {
                this.send(4, index, "");
                this.refresh();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
