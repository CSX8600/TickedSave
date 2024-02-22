package com.mesabrook.tickedsave;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.LDC;
import static org.objectweb.asm.Opcodes.NEW;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;

public class TickedSaveClassTransformer implements IClassTransformer {

	private static final String[] classesToTransform = 
		{
			"net.minecraft.server.MinecraftServer",
			"net.minecraft.world.World"
		};
	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		boolean isObfuscated = !((boolean)Launch.blackboard.get("fml.deobfuscatedEnvironment"));
		int index = Arrays.asList(classesToTransform).indexOf(transformedName);
		return index != -1 ? transform(index, basicClass, isObfuscated) : basicClass;
	}
	
	private byte[] transform(int index, byte[] classBytes, boolean isObfuscated)
	{
		System.out.println("Transforming: " + classesToTransform[index]);
		System.out.println("Is Obfuscated Environment: " + isObfuscated);
		
		try
		{
			ClassNode classNode = new ClassNode();
			ClassReader classReader = new ClassReader(classBytes);
			classReader.accept(classNode, 0);
			
			switch(index)
			{
				case 0:
					transformMinecraftServer(classNode, isObfuscated);
					break;
				case 1:
					transformWorld(classNode, isObfuscated);
					break;
				default:
					return classBytes;
			}
			
			System.out.println("Transformation complete - re-writing class");
			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
			classNode.accept(writer);
			byte[] newBytes = writer.toByteArray();
			
			System.out.println("Class re-written!");
			return newBytes;
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
		return classBytes;
	}
	
	private void transformMinecraftServer(ClassNode minecraftServerClass, boolean isObfuscated)
	{
		final String TICK = isObfuscated ? "C" : "tick";
		final String TICK_DESC = "()V";
		
		String worldsFieldName = "worlds"; // We literally have to go searching for this because MCP Mapping doesn't have obfuscated names for MinecraftServer
		if (isObfuscated)
		{
			for(FieldNode field : minecraftServerClass.fields)
			{
				if (field.desc.equals("[Loo;"))
				{
					worldsFieldName = field.name;
				}
			}
		}
		
		boolean didFindTickMethod = false;
		for (MethodNode method : minecraftServerClass.methods)
		{
			// Find method
			if (!method.name.equals(TICK) || !method.desc.equals(TICK_DESC))
			{
				continue;
			}
			
			didFindTickMethod = true;
			
			// Find the profiler save method as it's right above the vanilla lines we want to remove 
			AbstractInsnNode profilerSaveLDC = null;
			for(AbstractInsnNode instruction : method.instructions.toArray())
			{
				if (instruction.getOpcode() == LDC)
				{
					LdcInsnNode ldcInsn = (LdcInsnNode)instruction;
					if (ldcInsn.cst.equals("save"))
					{
						profilerSaveLDC = ldcInsn;
						break;
					}
				}
			}
			
			if (profilerSaveLDC == null)
			{
				throw new NoSuchMethodError("Could not find profiler save method in MinecraftServer");
			}
			
			AbstractInsnNode targetNode = profilerSaveLDC.getNext(); // Method for profiler
			targetNode = targetNode.getNext(); // Label 24 node
			targetNode = targetNode.getNext(); // Line number 690 node
			targetNode = targetNode.getNext(); // Load self for use in saving player list
			
			// Delete the two save calls
			for(int i = 0; i < 8; i++)
			{
				targetNode = targetNode.getNext();
				method.instructions.remove(targetNode.getPrevious());
			}
			
			// Insert our own save calls
			// *todo*
			InsnList instructionList = new InsnList();
			instructionList.add(new VarInsnNode(ALOAD, 0));
			instructionList.add(new FieldInsnNode(GETFIELD, "net/minecraft/server/MinecraftServer", worldsFieldName, isObfuscated ? "[Loo;" : "[Lnet/minecraft/world/WorldServer;"));
			instructionList.add(new MethodInsnNode(INVOKESTATIC, Type.getInternalName(SaveManager.class), "saveWorlds", isObfuscated ? "([Loo;)V" : "([Lnet/minecraft/world/WorldServer;)V", false));
			method.instructions.insertBefore(targetNode, instructionList);
			
			break;
		}
		
		if (!didFindTickMethod)
		{
			throw new NoSuchMethodError("Could not find " + TICK + TICK_DESC + " in MinecraftServer");
		}
	}
	
	private void transformWorld(ClassNode worldClass, boolean isObfuscated)
	{
		final String UPDATE_ENTITIES = isObfuscated ? "k" : "updateEntities";
		final String UPDATE_ENTITIES_DESC = "()V";
		final String WORLD_CLASS = isObfuscated ? "amu" : "net/minecraft/world/World";
		final String ADDED_TILE_ENTITY_LIST = isObfuscated ? "b" : "addedTileEntityList";
		final String TILE_ENTITY_CLASS = isObfuscated ? "avh" : "TileEntity";
		final String LOADED_TILE_ENTITY_LIST = isObfuscated ? "g" : "loadedTileEntityList";
		
		for(MethodNode method : worldClass.methods)
		{
			if (!method.name.equals(UPDATE_ENTITIES) || !method.desc.equals(UPDATE_ENTITIES_DESC))
			{
				continue;
			}
			
			AbstractInsnNode targetLabel = null;
			AbstractInsnNode finalLabel = null;
			AbstractInsnNode getArrayListInsn = null;
			for(AbstractInsnNode node : method.instructions.toArray())
			{
				if (targetLabel == null)
				{
					if (node.getOpcode() == GETFIELD && node.getNext().getOpcode() == 185)
					{
						FieldInsnNode getAddedTENode = (FieldInsnNode)node;
						MethodInsnNode isEmptyNode = (MethodInsnNode)node.getNext();						
						
						if (!getAddedTENode.owner.equals(WORLD_CLASS) || !getAddedTENode.name.equals(ADDED_TILE_ENTITY_LIST) || !getAddedTENode.desc.equals("Ljava/util/List;") ||
								!isEmptyNode.owner.equals("java/util/List") || !isEmptyNode.name.equals("isEmpty") || !isEmptyNode.desc.equals("()Z") || !isEmptyNode.itf)
						{
							continue;
						}
						
						targetLabel = isEmptyNode;
						for(int i = 0; i < 2; i++)
						{
							targetLabel = targetLabel.getNext();
						}
					}
				}
				else if (getArrayListInsn == null)
				{
					if (node.getOpcode() == ALOAD && node.getNext().getOpcode() == GETFIELD && node.getNext().getNext().getOpcode() == ALOAD && node.getNext().getNext().getNext().getOpcode() == INVOKEINTERFACE)
					{
						VarInsnNode loadThis = (VarInsnNode)node;
						FieldInsnNode getArrayList = (FieldInsnNode)node.getNext();
						VarInsnNode loadTileEntityVariable = (VarInsnNode)getArrayList.getNext();
						MethodInsnNode containsMethodCall = (MethodInsnNode)loadTileEntityVariable.getNext();
						
						if (loadThis.var != 0 ||
								!getArrayList.owner.equals(WORLD_CLASS) || !getArrayList.name.equals(LOADED_TILE_ENTITY_LIST) || !getArrayList.desc.equals("Ljava/util/List;") ||
								loadTileEntityVariable.var != 3 ||
								!containsMethodCall.owner.equals("java/util/List") || !containsMethodCall.name.equals("contains") || !containsMethodCall.desc.equals("(Ljava/lang/Object;)Z") || !containsMethodCall.itf)
						{
							continue;
						}
						
						getArrayListInsn = getArrayList;
					}
				}
				else if (finalLabel == null)
				{
					if (node.getOpcode() == GETFIELD && node.getNext().getOpcode() == INVOKEINTERFACE)
					{
						FieldInsnNode getAddedTENode = (FieldInsnNode)node;
						MethodInsnNode clearNode = (MethodInsnNode)node.getNext();
						
						if (!getAddedTENode.owner.equals(WORLD_CLASS) || !getAddedTENode.name.equals(ADDED_TILE_ENTITY_LIST) || !getAddedTENode.desc.equals("Ljava/util/List;") ||
								!clearNode.owner.equals("java/util/List") || !clearNode.name.equals("clear") || !clearNode.desc.equals("()V") || !clearNode.itf)
						{
							continue;
						}
						
						finalLabel = clearNode.getNext();
					}
				}
			}
			
			if (targetLabel == null)
			{
				throw new NoSuchMethodError("Could not find !this.addedTileEntityList.isEmpty() in World");
			}
			
			if (getArrayListInsn == null)
			{
				throw new NoSuchMethodError("Could not find !this.loadedTileEntityList.contains() in World after targetLabel");
			}
			
			if (finalLabel == null)
			{
				throw new NoSuchMethodError("Could not find !this.addedTileEntityList.clear() in World after getArrayListInsn");
			}
			
			method.visitLocalVariable("loaded", "Ljava/util/Set;", "Ljava/util/Set<Lnet/minecraft/tileentity/" + TILE_ENTITY_CLASS + ";>;", ((LabelNode)targetLabel).getLabel(), ((LabelNode)finalLabel).getLabel(), 6);
			
			InsnList instructionList = new InsnList();
			
			// Create the Set
			instructionList.add(new TypeInsnNode(NEW, "java/util/IdentityHashMap"));
			instructionList.add(new InsnNode(DUP));
			instructionList.add(new MethodInsnNode(INVOKESPECIAL, "java/util/IdentityHashMap", "<init>", "()V", false));
			instructionList.add(new MethodInsnNode(INVOKESTATIC, "java/util/Collections", "newSetFromMap", "(Ljava/util/Map;)Ljava/util/Set;", false));
			instructionList.add(new VarInsnNode(ASTORE, 6));
			
			// Add all loaded TEs to the set
			instructionList.add(new VarInsnNode(ALOAD, 6));
			instructionList.add(new VarInsnNode(ALOAD, 0));
			instructionList.add(new FieldInsnNode(GETFIELD, WORLD_CLASS, LOADED_TILE_ENTITY_LIST, "Ljava/util/List;"));
			instructionList.add(new MethodInsnNode(INVOKEINTERFACE, "java/util/Set", "addAll", "(Ljava/util/Collection;)Z", true));
			instructionList.add(new InsnNode(Opcodes.POP));
			method.instructions.insert(targetLabel.getNext(), instructionList);
			
			// Add new part of if statement to use the set instead of arraylist
			method.instructions.insert(getArrayListInsn, new VarInsnNode(ALOAD, 6));
			method.instructions.insert(getArrayListInsn
										.getNext() // ALOAD 6 (above)
										.getNext(), // ALOAD 3 (tile entity being checked)
										new MethodInsnNode(INVOKEINTERFACE, "java/util/Set", "contains", "(Ljava/lang/Object;)Z", true));
			
			// Remove old part of if statement
			method.instructions.remove(getArrayListInsn
										.getNext() // ALOAD 6 (above)
										.getNext() // ALOAD 3 (tile entity being checked)
										.getNext() // Set's contains call (above)
										.getNext()); // array list contains call
			method.instructions.remove(getArrayListInsn.getPrevious()); // ALOAD 0 (loads this)
			method.instructions.remove(getArrayListInsn); // GETFIELD for arraylist
		}
	}
}
