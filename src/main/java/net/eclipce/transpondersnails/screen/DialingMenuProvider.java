package net.eclipce.transpondersnails.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import javax.annotation.Nullable;

public class DialingMenuProvider implements MenuProvider {

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.transpondersnails.dialing");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory pInventory, Player pPlayer) {
        // Create your DialingMenu with the required parameters
        return new DialingMenu(containerId, pInventory);
    }
}