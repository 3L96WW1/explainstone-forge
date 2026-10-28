package com.ginka.explainstone;

import com.ginka.explainstone.speaker.Broadcasts;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 服务端事件：广播表心跳喵 */
@Mod.EventBusSubscriber(modid = Explainstone.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerEvents {
    @SubscribeEvent
    public static void onServerTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            Broadcasts.tick(serverLevel);
        }
    }

    private ServerEvents() {
    }
}
