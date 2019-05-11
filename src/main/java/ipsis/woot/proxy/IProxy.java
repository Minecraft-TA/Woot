package ipsis.woot.proxy;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

public interface IProxy {
    void setup(FMLCommonSetupEvent event);
    EntityPlayer getClientPlayer();
    World getClientWorld();
}