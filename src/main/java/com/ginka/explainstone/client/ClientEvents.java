package com.ginka.explainstone.client;

import com.ginka.explainstone.Explainstone;
import com.ginka.explainstone.client.audio.SpeakerAudio;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 客户端事件：每 tick 驱动音频引擎喵 */
@Mod.EventBusSubscriber(modid = Explainstone.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents {
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            SpeakerAudio.tick(net.minecraft.client.Minecraft.getInstance());
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        com.ginka.explainstone.client.audio.SpeakerAudioCache.clear();
    }

    private ClientEvents() {
    }
}
