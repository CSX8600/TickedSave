package com.mesabrook.tickedsave;

import java.io.File;

import org.apache.logging.log4j.Logger;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.mesabrook.tickedsave.event.TickHandler;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class TickedSaveContainer extends DummyModContainer {

	public static Configuration config;
	public static Logger logger;
	public TickedSaveContainer()
	{
		super(new ModMetadata());
		ModMetadata meta = getMetadata();
		meta.modId = "tickedsave";
		meta.version = "0.0.5";
		meta.name = "Ticked Save";
	}
	
	@Override
	public boolean registerBus(EventBus bus, LoadController controller) {
		bus.register(this);
		return true;
	}
	
	@Subscribe
	public void preInit(FMLPreInitializationEvent e)
	{
		File directory = e.getModConfigurationDirectory();
		config = new Configuration(new File(directory.getPath(), "tickedsave.cfg"));
		Config.readConfig();
		
		logger = e.getModLog();
		
		logger.info("Ticked save loaded config!  Broadcast setting: " + Config.broadcast);
	}
	
	@Subscribe 
	public void init(FMLInitializationEvent e)
	{
		MinecraftForge.EVENT_BUS.register(new TickHandler());
	}
}
