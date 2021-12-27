package com.mesabrook.tickedsave;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.common.base.Stopwatch;

import net.minecraft.block.BlockDirt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.server.FMLServerHandler;

public class SaveManager {	
	private static SaveManager instance;
	private HashMap<WorldServer, WorldSaveManager> saveManagersByWorld = new HashMap<>();
	private static Method saveLevelMethod = null;
	private static Method saveChunkExtraDataMethod = null;
	private static Method saveChunkDataMethod = null;
	private static Field playerChunkMapField = null;
	private boolean isObfuscated = false;
	private Queue<Long> chunkIDs = new LinkedList<Long>();
	private Queue<Chunk> loadedChunks = new LinkedList<Chunk>();
	
	public static SaveManager instance()
	{
		if (instance == null)
		{
			instance = new SaveManager();
		}
		
		return instance;
	}
	
	private SaveManager()
	{
		isObfuscated = !Arrays.stream(BlockDirt.class.getMethods()).anyMatch(m -> m.getName() == "getStateFromMeta");
		try
		{
			saveLevelMethod = WorldServer.class.getDeclaredMethod(isObfuscated ? "func_73042_a" : "saveLevel", new Class<?>[0]);
			saveLevelMethod.setAccessible(true);
			saveChunkExtraDataMethod = ChunkProviderServer.class.getDeclaredMethod(isObfuscated ? "func_73243_a" : "saveChunkExtraData", Chunk.class);
			saveChunkExtraDataMethod.setAccessible(true);
			saveChunkDataMethod = ChunkProviderServer.class.getDeclaredMethod(isObfuscated ? "func_73242_b" : "saveChunkData", Chunk.class);
			saveChunkDataMethod.setAccessible(true);
			playerChunkMapField = WorldServer.class.getDeclaredField(isObfuscated ? "field_73063_M" : "playerChunkMap");
			playerChunkMapField.setAccessible(true);
		}
		catch(Exception ex)
		{
			FMLServerHandler.instance().haltGame("Could not get one of the following: WorldServer#saveLevel(), ChunkProviderServer#saveChunkExtraData(Chunk), CHunkProviderServer#saveChunkData(Chunk), WorldServer#playerChunkMap", ex);
		}
	}
	
	public static void saveWorlds(WorldServer[] worlds)
	{
		if (worlds != null && worlds.length != 0)
		{
			instance().addWorlds(worlds);
		}
	}
	
	private void addWorlds(WorldServer[] worlds)
	{
		for(WorldServer worldToAdd : worlds)
		{
			WorldSaveManager manager = saveManagersByWorld.get(worldToAdd);
			if (manager == null || !manager.isSaving())
			{
				saveManagersByWorld.put(worldToAdd, new WorldSaveManager(worldToAdd));
			}
		}
	}
	
	public void tick()
	{
		for(WorldSaveManager manager : saveManagersByWorld.values())
		{
			manager.tick();
		}
	}
	
	private class WorldSaveManager
	{
		private final WorldServer worldServer;
		private boolean isSaving;
		private int currentChunk;
		private SaveManagerPhases phase;
		
 		public WorldSaveManager(WorldServer worldServer)
		{
			this.worldServer = worldServer;
			log("message.verbose.initialize");
			log("message.normal.saving");
			isSaving = true;
			phase = SaveManagerPhases.SaveLevel;
		}
		
		public boolean isSaving()
		{
			return isSaving;
		}
		
		public void tick()
		{
			if (!isSaving())
			{
				return;
			}
			
			ChunkProviderServer provider = worldServer.getChunkProvider();
			if (!provider.canSave())
			{
				log("message.verbose.providernosave");
				return;
			}
			
			switch(phase)
			{
				case SaveLevel:
					if (saveLevel())
					{
						phase = SaveManagerPhases.SaveChuks;
					}
					break;
				case SaveChuks:
					if (saveChunks())
					{
						phase = SaveManagerPhases.PostSaveEvent;
					}
					break;
				case PostSaveEvent:
					if (postSaveEvent())
					{
						phase = SaveManagerPhases.UnloadChunks;
					}
					break;
				case UnloadChunks:
					if (unloadChunks())
					{
						log("message.normal.saved");
						isSaving = false;
					}
					break;
			}
		}
	
		private boolean saveLevel()
		{
			log("message.verbose.savelevel");
			try
			{
				saveLevelMethod.invoke(worldServer, new Object[0]);
			}
			catch(Exception ex)
			{
				FMLServerHandler.instance().haltGame("Could not save levels in World Save Manager.", ex);
				return false;
			}
			
			return true;
		}
		
		private int initialMapSize = 0;
		private boolean saveChunks()
		{
			ChunkProviderServer provider = worldServer.getChunkProvider();
			Stopwatch stopwatch = Stopwatch.createStarted();
			if (chunkIDs.size() <= 0)
			{
				chunkIDs = new LinkedList<>(provider.id2ChunkMap.keySet().stream().collect(Collectors.toList()));
				initialMapSize = chunkIDs.size();
			}
			
			while(stopwatch.elapsed(TimeUnit.MILLISECONDS) < Config.maximumTickTime)
			{
				Long chunkID = chunkIDs.poll();
				if (chunkID == null)
				{
					currentChunk = 0;
					return true;
				}
				
				Chunk chunk = provider.id2ChunkMap.get(chunkID.longValue());
				
				if (chunk == null)
				{
					currentChunk++;
					continue;
				}
				
				try 
				{
					saveChunkExtraDataMethod.invoke(provider, chunk);
				} 
				catch (Exception e) 
				{
					FMLServerHandler.instance().haltGame("Could not save extra chunk data in World Save Manager.", e);
					return false;
				}
				
				if (chunk.needsSaving(true))
				{
					try
					{
						saveChunkDataMethod.invoke(provider, chunk);
					}
					catch(Exception e)
					{
						FMLServerHandler.instance().haltGame("Could not save chunk data in World Save Manager.", e);
						return false;
					}
					
					chunk.setModified(false);
				}
				
				currentChunk++;
			}
			stopwatch.stop();
			
			log("message.verbose.savingchunks", Integer.toString(currentChunk), Integer.toString(initialMapSize));
			
			return false;
		}
		
		private boolean postSaveEvent()
		{
			log("message.verbose.saveevents");
			MinecraftForge.EVENT_BUS.post(new WorldEvent.Save(worldServer));
			return true;
		}
		
		private boolean unloadChunks()
		{
			ChunkProviderServer provider = worldServer.getChunkProvider();
			
			Collection<Chunk> chunks = provider.getLoadedChunks();
			PlayerChunkMap playerChunkMap;
			try
			{
				playerChunkMap = (PlayerChunkMap)playerChunkMapField.get(worldServer);
			}
			catch (Exception e)
			{

				FMLServerHandler.instance().haltGame("Could not unload chunk in World Save Manager.", e);
				return false;
			}
			
			Stopwatch stopwatch = Stopwatch.createStarted();
			if (loadedChunks.size() <= 0)
			{
				loadedChunks = new LinkedList<>(provider.getLoadedChunks());
			}
			
			while(stopwatch.elapsed(TimeUnit.MILLISECONDS) <= Config.maximumTickTime)
			{
				Chunk nextChunk = loadedChunks.poll();
				if (nextChunk == null)
				{
					currentChunk = 0;
					return true;
				}
				
				if (!provider.getLoadedChunks().contains(nextChunk))
				{
					continue;
				}
				
				if (!playerChunkMap.contains(nextChunk.x, nextChunk.z))
				{
					provider.queueUnload(nextChunk);
				}
				
				currentChunk++;
			}
			stopwatch.stop();
			log("message.verbose.unload", Integer.toString(currentChunk), Integer.toString(loadedChunks.size()));
			
			return false;
		}

		private void log(String message, Object... extra)
		{
			if ("false".equalsIgnoreCase(Config.broadcast))
			{
				return;
			}
			else if (("true".equals(Config.broadcast) || "meme".equals(Config.broadcast)) && message.startsWith("message.verbose"))
			{
				return;
			}
			else if ("meme".equals(Config.broadcast) && message.startsWith("message.normal"))
			{
				message = message.replace("message.normal", "message.meme");
			}
			
			int extraSize = 1;
			if (extra != null)
			{
				extraSize += extra.length;
			}
			
			Object[] extras = new Object[extraSize];
			extras[0] = worldServer.getWorldInfo().getWorldName();
			
			if (extra != null)
			{
				for(int i = 0; i < extra.length; i++)
				{
					extras[i + 1] = extra[i];
				}
			}
			
			TextComponentString text = new TextComponentString(localLocalization(message, extras));
			for(EntityPlayer player : worldServer.playerEntities)
			{
				player.sendMessage(text);
			}
		}
	
		private String localLocalization(String unlocalized, Object... extra)
		{
			switch (unlocalized)
			{
				case "message.verbose.initialize":
					return String.format("[Ticked Save] World Save Manager initializing for world %s", extra);
				case "message.normal.saving":
					return String.format("[Ticked Save] Starting world save (%s)", extra);
				case "message.meme.saving":
					return String.format("[Leviathan Save Co. A subsidiary of Leviathan LLC] Saving the shit (%s)", extra);
				case "message.verbose.providernosave":
					return String.format("[Ticked Save] Chunk Provider indicated it cannot save (%s)", extra);
				case "message.verbose.savelevel":
					return String.format("[Ticked Save] Starting to save the level (%s)", extra);
				case "message.verbose.savingchunks":
					return String.format("[Ticked Save] Processed %2$s of %3$s chunks for save (%1$s)", extra);
				case "message.verbose.saveevents":
					return String.format("[Ticked Save] Performing world save events (%s)", extra);
				case "message.verbose.unload":
					return String.format("[Ticked Save] Processed %2$s of %3$s chunks for unload consideration (%1$s)", extra);
				case "message.normal.saved":
					return String.format("[Ticked Save] %s Saved!", extra);
				case "message.meme.saved":
					return String.format("[Leviathan Save Co. A subsidiary of Leviathan LLC] lmfao sabe", extra);
				default:
					return "";
			}
		}
	}
}
