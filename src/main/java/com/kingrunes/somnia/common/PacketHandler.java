package com.kingrunes.somnia.common;

import com.kingrunes.somnia.Somnia;
import com.kingrunes.somnia.api.capability.CapabilityFatigue;
import com.kingrunes.somnia.api.capability.IFatigue;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientCustomPacketEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ServerCustomPacketEvent;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;

import javax.annotation.Nullable;
import java.io.DataInputStream;
import java.io.IOException;

public class PacketHandler
{
	/*
	 * Handling
	 */
	@SubscribeEvent
	public void onServerPacket(ServerCustomPacketEvent event)
	{
		if (event.getPacket().channel().equals(Somnia.MOD_ID))
			onPacket(event.getPacket(), ((NetHandlerPlayServer)event.getHandler()).player);
	}
	
	@SubscribeEvent
	public void onClientPacket(ClientCustomPacketEvent event)
	{
		if (event.getPacket().channel().equals(Somnia.MOD_ID))
			onPacket(event.getPacket(), null);
	}
	
	public void onPacket(FMLProxyPacket packet, EntityPlayerMP player)
	{
		DataInputStream in = new DataInputStream(new ByteBufInputStream(packet.payload()));
		try
		{
			byte id = in.readByte();
			
			switch (id)
			{
			case 0x00:
				handleGUIOpenPacket();
				break;
			case 0x01:
				handleWakePacket(player, in);
				break;
			case 0x02:
				handlePropUpdatePacket(in, player);
				break;
			case 0x03:
				handleRightClickBlockPacket(player, in);
				break;
			case 0x04:
				handleRideEntityPacket(player, in);
				break;
			}
		}
		catch (IOException e)
		{
			Somnia.logger.error("Packet handling error", e);
		}
	}

	// CLIENT
	private void handleGUIOpenPacket() {
		Minecraft.getMinecraft().addScheduledTask(() -> 
			Somnia.proxy.handleGUIOpenPacket()
		);
	}
	
	private void handlePropUpdatePacket(DataInputStream in, @Nullable EntityPlayerMP player) throws IOException
	{
		if (player == null || player.world.isRemote) {
			Minecraft.getMinecraft().addScheduledTask(() -> {
				try {
					Somnia.proxy.handlePropUpdatePacket(in);
				} catch (IOException e) {
					Somnia.logger.error("Prop update error", e);
				}
			});
			return;
		}

		byte target = in.readByte();
		IFatigue props = player.getCapability(CapabilityFatigue.FATIGUE_CAPABILITY, null);

		if (target == 0x01 && props != null) {
			int b = in.readInt();
			for (int a=0; a<b; a++)
			{
				int val = in.readByte();
				if (val == 0x00) {
					props.setFatigue(in.readDouble());
				}
				else if (val == 0x01) {
					props.setResetSpawn(in.readBoolean());
				}
				else if (val == 0x02) {
					props.setSleepNormally(in.readBoolean());
				}
			}
		}
	}
	
	private void handleWakePacket(EntityPlayerMP player, DataInputStream in)
	{
		Minecraft.getMinecraft().addScheduledTask(() -> 
			Somnia.proxy.handleWakePacket(player)
		);
	}

	private void handleRightClickBlockPacket(EntityPlayerMP player, DataInputStream in) throws IOException {
		BlockPos pos = new BlockPos(in.readInt(), in.readInt(), in.readInt());
		EnumFacing side = EnumFacing.values()[in.readByte()];
		float hitX = in.readFloat();
		float hitY = in.readFloat();
		float hitZ = in.readFloat();
		player.world.getBlockState(pos).getBlock().onBlockActivated(
				player.world, pos, player.world.getBlockState(pos), player, EnumHand.MAIN_HAND, 
				side, hitX, hitY, hitZ
		);
	}

	private void handleRideEntityPacket(EntityPlayerMP player, DataInputStream in) throws IOException {
		int entityId = in.readInt();
		Entity entity = player.world.getEntityByID(entityId);
		if (entity != null) {
			player.startRiding(entity);
		}
	}

	/*
	 * Building
	 */
	private static PacketBuffer unpooled() {
		return new PacketBuffer(Unpooled.buffer());
	}

	public static FMLProxyPacket buildGUIOpenPacket() {
		PacketBuffer buffer = unpooled();
		buffer.writeByte(0x00);
		return new FMLProxyPacket(buffer, Somnia.MOD_ID);
	}

	public static FMLProxyPacket buildWakePacket() {
		PacketBuffer buffer = unpooled();
		buffer.writeByte(0x01);
		return new FMLProxyPacket(buffer, Somnia.MOD_ID);
	}

	public static FMLProxyPacket buildPropUpdatePacket(int target, int prop, boolean value) {
		PacketBuffer buffer = unpooled();
		buffer.writeByte(0x02);
		buffer.writeByte(target);
		buffer.writeInt(1); // Number of properties
		buffer.writeByte(prop);
		buffer.writeBoolean(value);
		return new FMLProxyPacket(buffer, Somnia.MOD_ID);
	}

	public static FMLProxyPacket buildPropUpdatePacket(int target, int prop, double value) {
		PacketBuffer buffer = unpooled();
		buffer.writeByte(0x02);
		buffer.writeByte(target);
		buffer.writeInt(1);
		buffer.writeByte(prop);
		buffer.writeDouble(value);
		return new FMLProxyPacket(buffer, Somnia.MOD_ID);
	}

	public static FMLProxyPacket buildPropUpdatePacket(int target, int prop, double value, int prop2, String value2) {
		PacketBuffer buffer = unpooled();
		buffer.writeByte(0x02);
		buffer.writeByte(target);
		buffer.writeInt(2); // Number of properties
		buffer.writeByte(prop);
		buffer.writeDouble(value);
		buffer.writeByte(prop2);
		buffer.writeString(value2);
		return new FMLProxyPacket(buffer, Somnia.MOD_ID);
	}

	public static FMLProxyPacket buildRightClickBlockPacket(BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ) {
		PacketBuffer buffer = unpooled();
		buffer.writeByte(0x03);
		buffer.writeInt(pos.getX());
		buffer.writeInt(pos.getY());
		buffer.writeInt(pos.getZ());
		buffer.writeByte(side.ordinal());
		buffer.writeFloat(hitX);
		buffer.writeFloat(hitY);
		buffer.writeFloat(hitZ);
		return new FMLProxyPacket(buffer, Somnia.MOD_ID);
	}

	public static FMLProxyPacket buildRideEntityPacket(Entity entity) {
		PacketBuffer buffer = unpooled();
		buffer.writeByte(0x04);
		buffer.writeInt(entity.getEntityId());
		return new FMLProxyPacket(buffer, Somnia.MOD_ID);
	}
}