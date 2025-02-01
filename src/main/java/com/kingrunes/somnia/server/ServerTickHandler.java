package com.kingrunes.somnia.server;

import com.kingrunes.somnia.Somnia;
import com.kingrunes.somnia.common.PacketHandler;
import com.kingrunes.somnia.common.SomniaConfig;
import com.kingrunes.somnia.common.util.SomniaState;
import com.kingrunes.somnia.common.util.SomniaUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketTimeUpdate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;

import javax.annotation.Nullable;
import java.util.Iterator;

import static com.kingrunes.somnia.common.util.SomniaState.*;

public class ServerTickHandler
{
	public static final String TRANSLATION_FORMAT = "somnia.status.%s";
			
	private static int activeTickHandlers = 0;
	
	public WorldServer worldServer;
	public SomniaState currentState;
	
	public long 	lastSleepStart,
					currentSleepPeriod; 			// Incremented while mbCheck is true, reset when state is changed
	public long		checkTimer 			= 0, 		// Used to schedule GUI update packets and sleep state checks
					lastTpsMillis		= 0,
					liTps 				= 0, 		// Counts ticks
					tps					= 0;		// Set per second to liTPS, used to work out actual multiplier to send to clients
	
	private double 	multiplier 			= SomniaConfig.LOGIC.baseMultiplier;
	private double 	worldTimeMultiplier = SomniaConfig.PERFORMANCE.fasterWorldTimeMultiplier;
	
	public ServerTickHandler(WorldServer worldServer)
	{
		this.worldServer = worldServer;
	}	
	
	public void tickStart()
	{
		if (++checkTimer == 10)
		{
			checkTimer = 0;
			
			SomniaState prevState = currentState;
			currentState = SomniaState.getState(this);
			
			if (prevState != currentState)
			{
				currentSleepPeriod = 0;
				if (currentState == ACTIVE) // acceleration started
				{
					lastSleepStart = worldServer.getWorldTime();
					activeTickHandlers++;
				}
				else if (prevState == ACTIVE) // acceleration stopped
				{
					activeTickHandlers--;
					
					if (currentState == EXPIRED || currentState == NOT_NOW)
						closeGuiWithMessage(currentState.toString());
				}
			}
			
			if (currentState == ACTIVE || currentState == WAITING_PLAYERS || currentState == COOLDOWN)
			{
				FMLProxyPacket packet = PacketHandler.buildPropUpdatePacket(
					0x00,
					0x00, currentState == ACTIVE ? (double)tps/20d : .0d,
					0x01, currentState == ACTIVE ? SomniaUtil.timeStringForWorldTime(worldServer.getWorldTime()) : "f:"+currentState.toString()
				);
				
				Somnia.eventChannel.sendToDimension(packet, worldServer.provider.getDimension());
			}
		}
		
		if (currentState == ACTIVE)
			doMultipliedTicking();
	}
	
	private void closeGuiWithMessage(@Nullable String key)
	{
		FMLProxyPacket packet = PacketHandler.buildWakePacket();

		Iterator<EntityPlayer> iter = worldServer.playerEntities.iterator();
		EntityPlayer ep;
		while (iter.hasNext())
		{
			ep = iter.next();
			if (ep.isPlayerSleeping())
			{
				Somnia.eventChannel.sendTo(packet, (EntityPlayerMP) ep);
				if (ep.isPlayerSleeping()) // this if might stop random teleporting when players have already woken
				{
					ep.wakeUpPlayer(false, true, true); // Stop clients ignoring GUI close packets (major hax)
				}
				if (key != null)
					ep.sendMessage(new TextComponentTranslation(String.format(TRANSLATION_FORMAT, key.toLowerCase())));
			}
		}
	}
	
	private void incrementCounters()
	{
		liTps++;
		currentSleepPeriod++;
	}
	
	private double overflow = .0d;
	private double worldTimeOverflow = .0d;
	private void doMultipliedTicking()
	{
		/*
		 * We can't run 0.5 of a tick,
		 * so we floor the multiplier and store the difference as overflow to be ran on the next tick
		 */
		double target = multiplier + overflow;
		int liTarget = (int) Math.floor(target);
		overflow = target - liTarget;
		
		long delta = System.currentTimeMillis();
		for (int i=0; i<liTarget; i++)
			doMultipliedServerTicking();
		delta = System.currentTimeMillis() - delta;

		MinecraftServer server = worldServer.getMinecraftServer();
		if (server == null) return;
		server.getPlayerList().sendPacketToAllPlayersInDimension(new SPacketTimeUpdate(worldServer.getTotalWorldTime(), worldServer.getWorldTime(), worldServer.getGameRules().getBoolean("doDaylightCycle")), worldServer.provider.getDimension());
		
		if (delta > (SomniaConfig.LOGIC.delta/activeTickHandlers))
			multiplier -= .1d;
		else
			multiplier += .1d;
		
		if (multiplier > SomniaConfig.LOGIC.multiplierCap)
			multiplier = SomniaConfig.LOGIC.multiplierCap;
		
		if (multiplier < SomniaConfig.LOGIC.baseMultiplier)
			multiplier = SomniaConfig.LOGIC.baseMultiplier;
		
		long currentTimeMillis = System.currentTimeMillis();
		if (currentTimeMillis-lastTpsMillis > 1000)
		{
			tps = liTps;
			liTps = 0;
			lastTpsMillis = currentTimeMillis;
		}
	}
	
	private void doMultipliedServerTicking()
	{
		FMLCommonHandler.instance().onPreWorldTick(worldServer);
		worldServer.tick();
		worldServer.updateEntities();
		worldServer.getEntityTracker().tick();
		FMLCommonHandler.instance().onPostWorldTick(worldServer);
		
		/*
		 * Work around for making sure fatigue is updated with every tick (including Somnia ticks)
		 */
		for (Object obj : worldServer.playerEntities)
			Somnia.forgeEventHandler.onPlayerTick(new TickEvent.PlayerTickEvent(Phase.START, (EntityPlayer) obj));
		
		if (SomniaConfig.PERFORMANCE.fasterWorldTime)
		{
			double target = worldTimeMultiplier + worldTimeOverflow;
			int liTarget = (int) Math.floor(target);
			worldTimeOverflow = target - liTarget;
			
			worldServer.setWorldTime(worldServer.getWorldTime() + liTarget);
		}
		
		incrementCounters();
	}
}