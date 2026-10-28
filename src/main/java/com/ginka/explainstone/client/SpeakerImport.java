package com.ginka.explainstone.client;

import com.ginka.explainstone.network.SpeakerNetwork;
import com.ginka.explainstone.network.SpeakerPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.FileDialog;
import java.awt.Frame;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** 导入 ogg：优先 inbox 目录，其次 AWT 文件框；分块上传喵 */
public final class SpeakerImport {
    private static final int CHUNK = 8000;
    private static final int MAX_BYTES = 6 * 1024 * 1024;

    public static void choose(Screen screen) {
        Path inbox = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("explainstone").resolve("inbox");
        try {
            Files.createDirectories(inbox);
            try (var stream = Files.list(inbox)) {
                var files = stream.filter(f -> f.getFileName().toString().toLowerCase().endsWith(".ogg")).toList();
                if (!files.isEmpty()) {
                    for (Path file : files) {          // inbox 里的 ogg 全部导入 喵
                        byte[] data = Files.readAllBytes(file);
                        upload(screen, file.getFileName().toString(), data);
                    }
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        Thread thread = new Thread(() -> {
            try {
                FileDialog dialog = new FileDialog((Frame) null, "选择 .ogg 文件", FileDialog.LOAD);
                dialog.setFile("*.ogg");
                dialog.setVisible(true);
                String dir = dialog.getDirectory();
                String file = dialog.getFile();
                if (dir == null || file == null) {
                    message(Component.translatable("message.explainstone.import.none", inbox.toString()));
                    return;
                }
                byte[] data = Files.readAllBytes(Path.of(dir, file));
                upload(screen, file, data);
            } catch (Throwable error) {
                message(Component.translatable("message.explainstone.import.dialog_failed", inbox.toString()));
            }
        }, "explainstone-import");
        thread.setDaemon(true);
        thread.start();
    }

    private static void upload(Screen screen, String name, byte[] data) {
        if (data.length < 4 || data[0] != 'O' || data[1] != 'g' || data[2] != 'g' || data[3] != 'S') {
            message(Component.translatable("message.explainstone.import.not_ogg"));
            return;
        }
        if (data.length > MAX_BYTES) {
            message(Component.translatable("message.explainstone.import.too_large"));
            return;
        }
        int total = (data.length + CHUNK - 1) / CHUNK;
        String prefix = (screen instanceof SpeakerScreen s ? s.posString() : "0,0,0") + "|" + name;
        for (int i = 0; i < total; i++) {
            byte[] part = Arrays.copyOfRange(data, i * CHUNK, Math.min(data.length, (i + 1) * CHUNK));
            SpeakerNetwork.sendToServer(SpeakerPacket.importChunk(prefix, i, total, part));
        }
        message(Component.translatable("message.explainstone.import.ok", name));
    }

    private static void message(Component text) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.displayClientMessage(text, false);
            }
        });
    }

    private SpeakerImport() {
    }
}
