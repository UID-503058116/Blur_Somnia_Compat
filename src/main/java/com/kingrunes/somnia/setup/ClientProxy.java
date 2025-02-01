package com.kingrunes.somnia.setup;

import com.kingrunes.somnia.client.ClientTickHandler;
import com.kingrunes.somnia.client.gui.GuiSelectWakeTime;
import com.kingrunes.somnia.common.SomniaConfig;
import com.kingrunes.somnia.common.util.SomniaUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.DataInputStream;
import java.io.IOException;

@SideOnly(Side.CLIENT)
public class ClientProxy implements IProxy {
    public static double playerFatigue = -1;
    public static final ClientTickHandler clientTickHandler = new ClientTickHandler();
    // 统一使用volatile保证可见性，并修正类型为long
    public static volatile long clientAutoWakeTime = -1; 

    @Override
    public void register() {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(clientTickHandler);
    }

    @Override
    public void handleGUIOpenPacket() {
        final Minecraft mc = Minecraft.getMinecraft();
        if (SomniaConfig.OPTIONS.enableWakeTimeSelectMenu) {
            mc.addScheduledTask(() -> mc.displayGuiScreen(new GuiSelectWakeTime(
                mc.player.inventory.getCurrentItem().getItem() == Items.CLOCK
            )));
        }
    }

    @Override
    public void handlePropUpdatePacket(DataInputStream in) throws IOException {
        byte target = in.readByte();
        EntityPlayer player = Minecraft.getMinecraft().player;

        switch (target) {
            case 0x00:
                if (player.isPlayerSleeping()) {
                    int b = in.readInt();
                    for (int a = 0; a < b; a++) {
                        clientTickHandler.readField(in);
                    }
                }
                break;
            case 0x01:
                int b = in.readInt();
                for (int a = 0; a < b; a++) {
                    byte val = in.readByte();
                    if (val == 0x00) {
                        playerFatigue = in.readDouble();
                    }
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void handleWakePacket(EntityPlayerMP player) {
        // 确保在主线程执行
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (player != null) {
                player.wakeUpPlayer(true, true, true);
            }
            Minecraft.getMinecraft().displayGuiScreen(null);
        });
    }

    @Override
    public void updateWakeTime(EntityPlayer player) {
        if (clientAutoWakeTime != -1) return;

        long worldTime = player.world.getWorldTime();
        clientAutoWakeTime = SomniaUtil.calculateWakeTime(
            worldTime, 
            (worldTime % 24000) > 12000 ? 0 : 12000
        );
    }
}