package com.mesabrook.tickedsave;

import java.util.Map;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.TransformerExclusions({"com.mesabrook.tickedsave"})
public class TickedSavePlugin implements IFMLLoadingPlugin {

	@Override
	public String[] getASMTransformerClass() {
		return new String[] { "com.mesabrook.tickedsave.TickedSaveClassTransformer" };
	}

	@Override
	public String getModContainerClass() {
		return "com.mesabrook.tickedsave.TickedSaveContainer";
	}

	@Override
	public String getSetupClass() {
		return null;
	}

	@Override
	public void injectData(Map<String, Object> data) {
		
	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}

}
