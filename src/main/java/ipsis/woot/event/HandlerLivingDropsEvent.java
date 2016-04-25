package ipsis.woot.event;

import ipsis.Woot;
import ipsis.woot.reference.Settings;
import ipsis.woot.util.DamageSourceWoot;
import net.minecraft.entity.EntityLiving;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class HandlerLivingDropsEvent {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDropsEvent(LivingDropsEvent e) {

        /**
         * This is called for players and mobs
         */
        if (e.getEntity() instanceof EntityLiving) {

            DamageSourceWoot damageSourceWoot = DamageSourceWoot.getDamageSource(e.getSource().damageType);
            if (damageSourceWoot != null) {

                /**
                 *  Killed in one of our spawners, but we dont care which one.
                 *  We only store the drop information.
                 */
                String mobID = Woot.mobRegistry.createWootName((EntityLiving) e.getEntity());
                if (!Woot.spawnerManager.isFull(mobID, damageSourceWoot.getEnchantKey()))
                    Woot.spawnerManager.addDrops(mobID, damageSourceWoot.getEnchantKey(), e.getDrops());

                e.setCanceled(true);
            } else if (!Settings.strictFactorySpawns) {

                /**
                 * Convert the non-spawner kill into a damage source if possible
                 */
                String mobID = Woot.mobRegistry.createWootName((EntityLiving) e.getEntity());
                damageSourceWoot = DamageSourceWoot.getDamageSource(e.getLootingLevel());
                if (damageSourceWoot != null && !Woot.spawnerManager.isFull(mobID, damageSourceWoot.getEnchantKey()))
                    Woot.spawnerManager.addDrops(mobID, damageSourceWoot.getEnchantKey(), e.getDrops());
            }
        }
    }
}
