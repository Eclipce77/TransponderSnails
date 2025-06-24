package net.eclipce.transpondersnails.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.data.SnailNumberRegistry;
import net.eclipce.transpondersnails.voice.CallManager;
import net.eclipce.transpondersnails.voice.TransponderSnailAudioPlugin;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static com.mojang.text2speech.Narrator.LOGGER;

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

                                                            boolean success = registry.forceSetNumber(target.getUUID(), number);
                                                            if (success) {
                                                                ctx.getSource().sendSuccess(
                                                                        () -> Component.literal(
                                                                                "Snail Number for "
                                                                                        + target.getName().getString()
                                                                                        + " set to "
                                                                                        + String.format("%04d", number)
                                                                        ), true
                                                                );
                                                                return 1;
                                                            } else {
                                                                ctx.getSource().sendFailure(
                                                                        Component.literal(
                                                                                "Cannot set number: that number is already in use or invalid."
                                                                        )
                                                                );
                                                                return 0;
                                                            }
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

                        .then(Commands.literal("dial")
                                .then(Commands.argument("number", IntegerArgumentType.integer(101, 9999))
                                        .executes(ctx -> {
                                            ServerPlayer caller = ctx.getSource().getPlayerOrException();
                                            int targetNumber = IntegerArgumentType.getInteger(ctx, "number");

                                            ServerLevel level = (ServerLevel) caller.level();
                                            SnailNumberRegistry registry = SnailNumberRegistry.get(level);

                                            // 1) Caller must have set their own snail number
                                            OptionalInt callerOpt = registry.getNumber(caller);
                                            if (callerOpt.isEmpty()) {
                                                ctx.getSource().sendFailure(
                                                        Component.literal("You must set your snail number first."));
                                                return 0;
                                            }
                                            int callerNumber = callerOpt.getAsInt();

                                            // 2) Prevent dialing yourself
                                           // if (callerNumber == targetNumber) {
                                                //ctx.getSource().sendFailure(
                                                        //Component.literal("You cannot dial your own snail number."));
                                                //return 0;
                                            //}

                                            // 3) Find target UUID
                                            Optional<UUID> targetUuid = registry.getPlayerByNumber(targetNumber);
                                            if (targetUuid.isEmpty()) {
                                                ctx.getSource().sendFailure(
                                                        Component.literal("No player has snail number " + String.format("%04d", targetNumber)));
                                                return 0;
                                            }

                                            ServerPlayer receiver = level.getServer()
                                                    .getPlayerList()
                                                    .getPlayer(targetUuid.get());
                                            if (receiver == null) {
                                                ctx.getSource().sendFailure(
                                                        Component.literal("Player with snail number "
                                                                + String.format("%04d", targetNumber)
                                                                + " is not online."));
                                                return 0;
                                            }

                                            // 4) Initiate the “call” by opening the audio channels
                                            UUID callId = UUID.randomUUID();
                                            TransponderSnailAudioPlugin.openCallChannel(caller, receiver, callId);
                                            CallManager.startRinging(caller, receiver, callId, targetNumber);


                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Dialing Snail #"
                                                            + String.format("%04d", targetNumber)
                                                            + " (call id: " + callId + ")…"),
                                                    false
                                            );
                                            return 1;
                                        })
                                )
                        )

                        // accept <callId>
                        .then(Commands.literal("accept")
                                .then(Commands.argument("callId", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String idStr = StringArgumentType.getString(ctx, "callId");
                                            UUID callId;
                                            try {
                                                callId = UUID.fromString(idStr);
                                            } catch (IllegalArgumentException e) {
                                                ctx.getSource().sendFailure(Component.literal("Invalid call ID."));
                                                return 0;
                                            }
                                            if (CallManager.acceptCall(callId)) {
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal("Call accepted."),
                                                        false
                                                );
                                                return 1;
                                            } else {
                                                ctx.getSource().sendFailure(Component.literal("No active call “" + callId + "”."));
                                                return 0;
                                            }
                                        })
                                )
                        )

                        // decline <callId>
                        .then(Commands.literal("decline")
                                .then(Commands.argument("callId", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String idStr = StringArgumentType.getString(ctx, "callId");
                                            UUID callId;
                                            try {
                                                callId = UUID.fromString(idStr);
                                            } catch (IllegalArgumentException e) {
                                                ctx.getSource().sendFailure(Component.literal("Invalid call ID."));
                                                return 0;
                                            }
                                            if (CallManager.declineCall(callId)) {
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal("Call declined."),
                                                        false
                                                );
                                                return 1;
                                            } else {
                                                ctx.getSource().sendFailure(Component.literal("No active call “" + callId + "”."));
                                                return 0;
                                            }
                                        })
                                )
                        )
        );
    }
}
