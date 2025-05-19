package net.eclipce.transpondersnails.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.data.SnailNumberRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.OptionalInt;

/**
 * /snailnumber set <player> <number>   — admin override a player's snail number
 * /snailnumber reset <player>          — admin clear a player's snail number
 * /snailnumber view <targets>          — list snail numbers for one or more players
 */
@Mod.EventBusSubscriber(modid = TransponderSnails.MOD_ID)
public class SnailNumberCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("snailnumber")
                        .requires(src -> src.hasPermission(2))

                        // set <player> <number>
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("number",
                                                                IntegerArgumentType.integer(101, 9999)
                                                        )
                                                        .executes(ctx -> {
                                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                            int number = IntegerArgumentType.getInteger(ctx, "number");

                                                            ServerLevel level = (ServerLevel) ctx.getSource().getLevel();
                                                            SnailNumberRegistry registry = SnailNumberRegistry.get(level);
                                                            registry.forceSetNumber(target.getUUID(), number);

                                                            ctx.getSource().sendSuccess(
                                                                    () -> Component.literal(
                                                                            "Snail Number for "
                                                                                    + target.getName().getString()
                                                                                    + " set to "
                                                                                    + String.format("%04d", number)
                                                                    ), true
                                                            );
                                                            return 1;
                                                        })
                                        )
                                )
                        )

                        // reset <player>
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            ServerLevel level = (ServerLevel) ctx.getSource().getLevel();
                                            SnailNumberRegistry registry = SnailNumberRegistry.get(level);

                                            boolean had = registry.removeNumber(target.getUUID());
                                            if (had) {
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal(
                                                                "Cleared Snail Number for "
                                                                        + target.getName().getString()
                                                        ), true
                                                );
                                                return 1;
                                            } else {
                                                ctx.getSource().sendFailure(
                                                        Component.literal(
                                                                target.getName().getString()
                                                                        + " did not have a Snail Number set."
                                                        )
                                                );
                                                return 0;
                                            }
                                        })
                                )
                        )

                        // view <targets>
                        .then(Commands.literal("view")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> {
                                            Collection<ServerPlayer> players =
                                                    EntityArgument.getPlayers(ctx, "targets");
                                            ServerLevel level = (ServerLevel) ctx.getSource().getLevel();
                                            SnailNumberRegistry registry = SnailNumberRegistry.get(level);

                                            int count = 0;
                                            for (ServerPlayer sp : players) {
                                                OptionalInt opt = registry.getNumber(sp);
                                                if (opt.isPresent()) {
                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal(
                                                                    sp.getName().getString()
                                                                            + ": "
                                                                            + String.format("%04d", opt.getAsInt())
                                                            ), false
                                                    );
                                                } else {
                                                    ctx.getSource().sendFailure(
                                                            Component.literal(
                                                                    sp.getName().getString()
                                                                            + " has no Snail Number set."
                                                            )
                                                    );
                                                }
                                                count++;
                                            }
                                            return count;
                                        })
                                )
                        )
        );
    }
}
