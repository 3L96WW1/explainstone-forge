package com.ginka.explainstone;

import com.ginka.explainstone.speaker.SpeakerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Explainstone.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Explainstone.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Explainstone.MOD_ID);

    private static BlockBehaviour.Properties speakerProps() {
        return BlockBehaviour.Properties.of()
                .strength(1.05f)
                .sound(SoundType.STONE)
                .noOcclusion();
    }

    public static final RegistryObject<Block> SPEAKER_BLOCK = register("speaker_block",
            () -> new SpeakerBlock(speakerProps()));
    public static final RegistryObject<Block> STONE_SPEAKER_BLOCK = register("stone_speaker_block",
            () -> new SpeakerBlock(speakerProps()));
    public static final RegistryObject<Block> COBBLESTONE_SPEAKER_BLOCK = register("cobblestone_speaker_block",
            () -> new SpeakerBlock(speakerProps()));
    public static final RegistryObject<Block> GRANITE_SPEAKER_BLOCK = register("granite_speaker_block",
            () -> new SpeakerBlock(speakerProps()));
    public static final RegistryObject<Block> DIORITE_SPEAKER_BLOCK = register("diorite_speaker_block",
            () -> new SpeakerBlock(speakerProps()));
    public static final RegistryObject<Block> ANDESITE_SPEAKER_BLOCK = register("andesite_speaker_block",
            () -> new SpeakerBlock(speakerProps()));
    public static final RegistryObject<Block> SONGMELYN_BLOCK = register("songmelyn_block",
            () -> new SongmelynBlock(speakerProps()));
    public static final RegistryObject<Block> BROADCAST_TRANSMITTER = register("broadcast_transmitter",
            () -> new BroadcastTransmitterBlock(speakerProps()));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("explainstone",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.explainstone"))
                    .icon(() -> SPEAKER_BLOCK.get().asItem().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(SPEAKER_BLOCK.get());
                        output.accept(STONE_SPEAKER_BLOCK.get());
                        output.accept(COBBLESTONE_SPEAKER_BLOCK.get());
                        output.accept(GRANITE_SPEAKER_BLOCK.get());
                        output.accept(DIORITE_SPEAKER_BLOCK.get());
                        output.accept(ANDESITE_SPEAKER_BLOCK.get());
                        output.accept(SONGMELYN_BLOCK.get());
                        output.accept(BROADCAST_TRANSMITTER.get());
                    })
                    .build());

    private static RegistryObject<Block> register(String name, Supplier<Block> block) {
        RegistryObject<Block> obj = BLOCKS.register(name, block);
        ITEMS.register(name, () -> new BlockItem(obj.get(), new Item.Properties()));
        return obj;
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TABS.register(bus);
        ModBlockEntities.BLOCK_ENTITIES.register(bus);
    }

    private ModBlocks() {
    }
}
