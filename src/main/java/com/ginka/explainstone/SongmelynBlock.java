package com.ginka.explainstone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 颂美铃：贴墙的薄板音响，碰撞箱随朝向变化 喵 */
public class SongmelynBlock extends SpeakerBlock {
    private static final VoxelShape NORTH = Shapes.box(1.5 / 16.0, 1.0 / 16.0, 11.0 / 16.0,
            14.5 / 16.0, 15.2 / 16.0, 16.0 / 16.0);
    private static final VoxelShape SOUTH = Shapes.box(1.5 / 16.0, 1.0 / 16.0, 0.0,
            14.5 / 16.0, 15.2 / 16.0, 5.0 / 16.0);
    private static final VoxelShape EAST = Shapes.box(0.0, 1.0 / 16.0, 1.5 / 16.0,
            5.0 / 16.0, 15.2 / 16.0, 14.5 / 16.0);
    private static final VoxelShape WEST = Shapes.box(11.0 / 16.0, 1.0 / 16.0, 1.5 / 16.0,
            16.0 / 16.0, 15.2 / 16.0, 14.5 / 16.0);

    public SongmelynBlock(Properties properties) {
        super(properties);
    }

    private static VoxelShape shapeFor(Direction facing) {
        return switch (facing) {
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(FACING));
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
