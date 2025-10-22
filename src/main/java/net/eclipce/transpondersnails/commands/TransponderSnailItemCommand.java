package net.eclipce.transpondersnails.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.eclipce.transpondersnails.data.SnailNBTHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class TransponderSnailItemCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("snailitem")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("setstate")
                        .then(Commands.argument("state", IntegerArgumentType.integer(0, 3))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    int stateValue = IntegerArgumentType.getInteger(context, "state");

                                    ItemStack held = player.getMainHandItem();
                                    int snailNumber = SnailNBTHandler.getSnailNumber(held);

                                    if (snailNumber == -1) {
                                        player.sendSystemMessage(Component.literal("Hold a Transponder Snail!"));
                                        return 0;
                                    }

                                    CompoundTag nbt = held.getOrCreateTag();
                                    String[] states = {"idle", "ringing", "connected", "connected"};
                                    nbt.putString("call_state", states[stateValue]);

                                    if (stateValue == 3) {
                                        nbt.putBoolean("has_active_audio", true);
                                    } else {
                                        nbt.remove("has_active_audio");
                                    }

                                    player.sendSystemMessage(Component.literal("Set state to: " + states[stateValue]));
                                    return 1;
                                })))
                .then(Commands.literal("debug")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ItemStack held = player.getMainHandItem();

                            CompoundTag nbt = held.getTag();
                            if (nbt == null) {
                                player.sendSystemMessage(Component.literal("No NBT data"));
                                return 0;
                            }

                            player.sendSystemMessage(Component.literal("=== Snail Item Debug ==="));
                            player.sendSystemMessage(Component.literal("Call State: " + nbt.getString("call_state")));
                            player.sendSystemMessage(Component.literal("Shell Color: " + nbt.getInt("shell_color")));
                            player.sendSystemMessage(Component.literal("Body Color: " + Integer.toHexString(nbt.getInt("body_color"))));
                            player.sendSystemMessage(Component.literal("Has Audio: " + nbt.getBoolean("has_active_audio")));

                            return 1;
                        }))
        );
    }
}