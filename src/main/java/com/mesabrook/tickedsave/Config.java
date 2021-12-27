package com.mesabrook.tickedsave;

import org.apache.logging.log4j.Level;

import net.minecraftforge.common.config.Configuration;

public class Config {
	private static final String CATEGORY_GENERAL = "general";
	
	public static int maximumTickTime = 10;
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
		maximumTickTime = cfg.getInt("maximumTickTime", CATEGORY_GENERAL, maximumTickTime, 1, Integer.MAX_VALUE, "What is the maximum amount of time any operation should take?");
		broadcast = cfg.getString("broadcast", CATEGORY_GENERAL, broadcast, "Should we broadcast save messages?");
	}
}
