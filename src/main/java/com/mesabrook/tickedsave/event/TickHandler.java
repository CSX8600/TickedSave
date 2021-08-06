package com.mesabrook.tickedsave.event;

import com.mesabrook.tickedsave.SaveManager;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent;

public class TickHandler {

	@SubscribeEvent
	public void WorldTick(ServerTickEvent e)
	{		
		if (e.phase != Phase.END)
		{
			return;
		}
		
		SaveManager.instance().tick();
	}
}
