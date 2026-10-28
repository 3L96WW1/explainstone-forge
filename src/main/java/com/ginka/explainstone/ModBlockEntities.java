package com.ginka.explainstone;

import com.ginka.explainstone.speaker.SpeakerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Explainstone.MOD_ID);

    public static final RegistryObject<BlockEntityType<SpeakerBlockEntity>> SPEAKER = BLOCK_ENTITIES.register(
            "speaker_block_entity",
            () -> BlockEntityType.Builder.of(SpeakerBlockEntity::new,
                    ModBlocks.SPEAKER_BLOCK.get(),
                    ModBlocks.STONE_SPEAKER_BLOCK.get(),
                    ModBlocks.COBBLESTONE_SPEAKER_BLOCK.get(),
                    ModBlocks.GRANITE_SPEAKER_BLOCK.get(),
                    ModBlocks.DIORITE_SPEAKER_BLOCK.get(),
                    ModBlocks.ANDESITE_SPEAKER_BLOCK.get(),
                    ModBlocks.SONGMELYN_BLOCK.get(),
                    ModBlocks.BROADCAST_TRANSMITTER.get()
            ).build(null));

    private ModBlockEntities() {
    }
}
