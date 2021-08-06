package com.mesabrook.tickedsave;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.LDC;

import java.util.Arrays;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;

public class TickedSaveClassTransformer implements IClassTransformer {

	private static final String[] classesToTransform = 
		{
			"net.minecraft.server.MinecraftServer"	
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
			}
			
			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
			classNode.accept(writer);
			byte[] newBytes = writer.toByteArray();
			
			System.out.println("Transformation complete!");
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
}
