package net.eclipce.transpondersnails.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.eclipce.transpondersnails.entity.ModEntities;
import net.eclipce.transpondersnails.entity.custom.DenDenMushiEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;

public class SpawnTestCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("testdendenspawn")
                .requires(source -> source.hasPermission(2))
                .executes(SpawnTestCommand::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPos.containing(context.getSource().getPosition());

        context.getSource().sendSuccess(() ->
                Component.literal("Attempting to spawn Den Den Mushi at " + pos), false);

        // Try to spawn
        DenDenMushiEntity entity = ModEntities.DEN_DEN_MUSHI.get().create(level);
        if (entity != null) {
            entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            entity.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                    MobSpawnType.COMMAND, null, null);
            level.addFreshEntity(entity);

            context.getSource().sendSuccess(() ->
                    Component.literal("✓ Den Den Mushi spawned successfully!"), false);
        } else {
            context.getSource().sendFailure(
                    Component.literal("✗ Failed to create entity"));
        }

        return 1;
    }
}