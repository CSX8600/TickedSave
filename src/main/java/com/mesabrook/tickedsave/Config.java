package com.mesabrook.tickedsave;

import org.apache.logging.log4j.Level;

import net.minecraftforge.common.config.Configuration;

public class Config {
	private static final String CATEGORY_GENERAL = "general";
	
	public static int chunksPerTick = 100;
	public static String broadcast = "false";
	
	public static void readConfig()
	{
		Configuration cfg = TickedSaveContainer.config;
		
		try
		{
			cfg.load();
			initGeneralConfig(cfg);
		}
		catch(Exception e)
		{
			TickedSaveContainer.logger.log(Level.WARN, "Problem loading config file!", e);
		}
		finally
		{
			if (cfg.hasChanged())
			{
				cfg.save();
			}
		}
	}
	
	private static void initGeneralConfig(Configuration cfg)
	{
		chunksPerTick = cfg.getInt("chunksPerTick", CATEGORY_GENERAL, chunksPerTick, 1, Integer.MAX_VALUE, "How many chunks per tick (per world) should we save?");
		broadcast = cfg.getString("broadcast", CATEGORY_GENERAL, broadcast, "Should we broadcast save messages?");
	}
}
