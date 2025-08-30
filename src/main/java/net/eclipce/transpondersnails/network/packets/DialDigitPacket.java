package net.eclipce.transpondersnails.network.packets;

import net.eclipce.transpondersnails.network.ModPackets;
import net.eclipce.transpondersnails.screen.DialingMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server: Player dialed a digit in the GUI
 * Syncs dial input from client to server
 */
public class DialDigitPacket {
    private final int digit;
    private final String action; // "dial", "clear", "backspace"

    public DialDigitPacket(int digit, String action) {
        this.digit = digit;
        this.action = action;
    }

    public DialDigitPacket(FriendlyByteBuf buf) {
        this.digit = buf.readInt();
        this.action = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(digit);
        buf.writeUtf(action);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }

            try {
                // Check if player has a dialing menu open
                if (player.containerMenu instanceof DialingMenu dialingMenu) {
                    switch (action.toLowerCase()) {
                        case "dial":
                            if (digit >= 0 && digit <= 9) {
                                dialingMenu.dialDigit(digit);

                                // Send back the updated dialed number to client
                                String currentNumber = dialingMenu.getDialedNumber();
                                ModPackets.sendToPlayer(new DialedNumberSyncPacket(currentNumber), player);

                                System.out.println("DialDigitPacket: Player " + player.getName().getString() +
                                        " dialed digit " + digit + ", current number: " + currentNumber);
                            }
                            break;

                        case "clear":
                            dialingMenu.clearDialedNumber();
                            ModPackets.sendToPlayer(new DialedNumberSyncPacket(""), player);
                            System.out.println("DialDigitPacket: Player " + player.getName().getString() + " cleared dialed number");
                            break;

                        case "backspace":
                            // Remove last digit
                            String currentNumber = dialingMenu.getDialedNumber();
                            if (!currentNumber.isEmpty()) {
                                String newNumber = currentNumber.substring(0, currentNumber.length() - 1);
                                dialingMenu.clearDialedNumber();
                                for (char c : newNumber.toCharArray()) {
                                    if (Character.isDigit(c)) {
                                        dialingMenu.dialDigit(Character.getNumericValue(c));
                                    }
                                }
                                ModPackets.sendToPlayer(new DialedNumberSyncPacket(newNumber), player);
                                System.out.println("DialDigitPacket: Player " + player.getName().getString() +
                                        " backspaced, new number: " + newNumber);
                            }
                            break;

                        default:
                            System.err.println("DialDigitPacket: Unknown action: " + action);
                            break;
                    }
                } else {
                    System.err.println("DialDigitPacket: Player " + player.getName().getString() +
                            " sent dial packet but has no dialing menu open");
                }

            } catch (Exception e) {
                System.err.println("DialDigitPacket: Error handling dial input: " + e.getMessage());
                e.printStackTrace();
            }
        });
        ctx.get().setPacketHandled(true);
    }

    // Getters
    public int getDigit() {
        return digit;
    }

    public String getAction() {
        return action;
    }
}