package com.ginka.explainstone.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** 只在客户端加载的入口（服务端不会碰这个类）喵 */
public final class ClientHooks {
    public static void openSpeakerScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new SpeakerScreen(pos));
    }

    private ClientHooks() {
    }
}
