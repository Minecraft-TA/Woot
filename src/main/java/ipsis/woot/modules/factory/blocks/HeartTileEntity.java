package ipsis.woot.modules.factory.blocks;

import ipsis.woot.Woot;
import ipsis.woot.fluilds.FluidSetup;
import ipsis.woot.mod.ModNBT;
import ipsis.woot.modules.factory.*;
import ipsis.woot.modules.factory.calculators.CalculatorVersion2;
import ipsis.woot.modules.factory.client.ClientFactorySetup;
import ipsis.woot.modules.factory.generators.LootGeneration;
import ipsis.woot.modules.factory.layout.Layout;
import ipsis.woot.modules.factory.multiblock.MultiBlockMaster;
import ipsis.woot.modules.factory.network.HeartStaticDataReply;
import ipsis.woot.simulator.MobSimulator;
import ipsis.woot.util.FakeMob;
import ipsis.woot.util.WootDebug;
import ipsis.woot.util.helper.StorageHelper;
import ipsis.woot.util.helper.StringHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * The factory is formed manually by the user via the intern -> interrupt
 * When an attached block is removed or unloaded it should inform the heart -> interrupt
 */
public class HeartTileEntity extends TileEntity implements ITickableTileEntity, MultiBlockMaster, WootDebug, INamedContainerProvider {

    static final Logger LOGGER = LogManager.getLogger();

    /**
     * Layout will not exist until after the first update call
     * Setup will only exist if the layout is formed
     */
    Layout layout;
    FormedSetup formedSetup;
    Recipe recipe;
    TickTracker tickTracker = new TickTracker();
    boolean loadedFromNBT = false;

    public HeartTileEntity() {
        super(FactorySetup.HEART_BLOCK_TILE.get());
        tickTracker.setStructureTickCount(20);
    }

    @Override
    public void remove() {
        super.remove();
        if (layout != null)
            layout.fullDisconnect();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (layout != null)
            layout.fullDisconnect();
    }

    public boolean isFormed() { return layout != null && layout.isFormed(); }

    @Override
    public void tick() {
        if (world == null)
            return;

       if (world.isRemote)
           return;

       if (layout == null) {
           layout = new Layout();
           layout.setLocation(world,pos, world.getBlockState(pos).get(BlockStateProperties.HORIZONTAL_FACING));
           layout.setDirty();
       }

       // Check for tick acceleration
       if (!tickTracker.tick(world))
           return;

       layout.tick(tickTracker, this);
       if (layout.isFormed()) {
           if (layout.hasChanged()) {
               formedSetup = FormedSetup.createFromValidLayout(world, layout);
               LOGGER.debug("formedSetup: {}", formedSetup);

               formedSetup.getAllMobs().forEach(m -> MobSimulator.getInstance().learn(m));
               recipe = CalculatorVersion2.calculate(formedSetup);

               // Restore the progress on load
               if (loadedFromNBT) {
                   loadedFromNBT = false;
               } else {
                   consumedUnits = 0;
                   markDirty();
               }

               layout.clearChanged();
           }

           if (world.isBlockPowered(pos))
               return;

           tickRecipe();
           if (consumedUnits >= recipe.getNumTicks()) {
               // get and process the ingredients
               consumedUnits = 0;
               markDirty();

               List<ItemStack> items = createItemIngredients(recipe, formedSetup);
               List<FluidStack> fluids = createFluidIngredients(recipe, formedSetup);

               if (hasItemIngredients(items, formedSetup) && hasFluidIngredients(fluids, formedSetup)) {
                   LazyOptional<IFluidHandler> hdlr = formedSetup.getCellFluidHandler();
                   if (hdlr.isPresent()) {
                       IFluidHandler iFluidHandler = hdlr.orElseThrow(NullPointerException::new);
                       FluidStack fluidStack = iFluidHandler.drain(recipe.getNumUnits(), IFluidHandler.FluidAction.SIMULATE);
                       if (fluidStack.getAmount() == recipe.getNumUnits()) {
                           LOGGER.debug("Generate loot");
                           consumeItemIngredients(items, formedSetup);
                           consumeFluidIngredients(fluids, formedSetup);
                           iFluidHandler.drain(recipe.getNumUnits(), IFluidHandler.FluidAction.EXECUTE);
                           LootGeneration.get().generate(this, formedSetup);
                       }
                   }
               }
           }
       }
    }

    private List<ItemStack> createItemIngredients(Recipe recipe, FormedSetup formedSetup) {
        List<ItemStack> items = new ArrayList<>();
        for (FakeMob fakeMob : formedSetup.getAllMobs()) {
            if (recipe.items.containsKey(fakeMob)) {
                // items are already calculated based on mob count
                for (ItemStack itemStack : recipe.items.get(fakeMob))
                    items.add(itemStack.copy());
            }
        }
        return StorageHelper.flattenItemStackList(items);
    }

    private List<FluidStack> createFluidIngredients(Recipe recipe, FormedSetup formedSetup) {
        List<FluidStack> fluids = new ArrayList<>();
        for (FakeMob fakeMob : formedSetup.getAllMobs()) {
            if (recipe.fluids.containsKey(fakeMob)) {
                // fluids are already calculated based on mob count
                for (FluidStack fluidStack : recipe.fluids.get(fakeMob))
                    fluids.add(fluidStack.copy());
            }
        }
        return fluids;
    }

    private boolean hasItemIngredients(List<ItemStack> items, FormedSetup formedSetup) {
        if (items.isEmpty())
            return true;

        for (ItemStack itemStack : items) {
            int count = StorageHelper.getCount(itemStack, formedSetup.getImportHandlers());
            if (count == 0 || count < itemStack.getCount())
                return false;
        }

        return true;
    }

    private boolean hasFluidIngredients(List<FluidStack> fluids, FormedSetup formedSetup) {
        if (fluids.isEmpty())
            return true;

        for (FluidStack fluidStack : fluids) {
            int amount = StorageHelper.getAmount(fluidStack, formedSetup.getImportFluidHandlers());
            if (amount == 0 || amount < fluidStack.getAmount())
                return false;
        }

        return true;
    }

    private void consumeItemIngredients(List<ItemStack> items, FormedSetup formedSetup) {
        if (items.isEmpty())
            return;

        for (LazyOptional<IItemHandler> hdlr : formedSetup.getImportHandlers()) {
            if (items.isEmpty())
                break;

            hdlr.ifPresent(h -> {
                for (ItemStack itemStack : items) {
                    if (itemStack.isEmpty())
                        continue;

                    Woot.setup.getLogger().debug("consumeItemIngredients: to consume {}", itemStack);

                    for (int slot = 0; slot < h.getSlots(); slot++) {
                        ItemStack slotStack = h.getStackInSlot(slot);
                        if (!slotStack.isEmpty() && ItemStack.areItemsEqual(itemStack, slotStack)) {

                            int consumed = 0;
                            if (itemStack.getCount() > slotStack.getCount())
                                consumed = slotStack.getCount();
                            else
                                consumed = itemStack.getCount();

                            Woot.setup.getLogger().debug("consumeItemIngredients: consumed {}", consumed);
                            slotStack.shrink(consumed);
                            itemStack.shrink(consumed);
                        }
                    }
                }
            });
        }
    }

    private void consumeFluidIngredients(List<FluidStack> fluids, FormedSetup formedSetup) {
        if (fluids.isEmpty())
            return;

        for (LazyOptional<IFluidHandler> hdlr : formedSetup.getImportFluidHandlers()) {
            if (fluids.isEmpty())
                break;

            hdlr.ifPresent(h -> {
                for (FluidStack fluidStack : fluids) {
                    if (fluidStack.isEmpty())
                        continue;

                    Woot.setup.getLogger().debug("consumeFluidIngredients: to consume {}", fluidStack);

                    FluidStack drainedStack = h.drain(fluidStack, IFluidHandler.FluidAction.EXECUTE);
                    int consumed = drainedStack.getAmount();
                    fluidStack.setAmount(fluidStack.getAmount() - consumed);
                    if (fluidStack.getAmount() < 0)
                        fluidStack.setAmount(0);
                    Woot.setup.getLogger().debug("consumeFluidIngredients: consumed {}", consumed);
                }
            });
        }
    }

    /**
     * NBT
     */
    @Override
    public void read(CompoundNBT compound) {
        super.read(compound);
        if (compound.contains(ModNBT.Heart.PROGRESS_TAG)) {
            loadedFromNBT = true;
            consumedUnits = compound.getInt(ModNBT.Heart.PROGRESS_TAG);
            LOGGER.debug("read: loading progress " + consumedUnits);
        }
    }

    @Override
    public CompoundNBT write(CompoundNBT compound) {
        super.write(compound);
        if (isFormed() && recipe != null) {
            compound.putInt(ModNBT.Heart.PROGRESS_TAG, consumedUnits);
            LOGGER.debug("write: saving progress " + consumedUnits);
        }
        return compound;
    }

    /**
     * MultiBlockMaster
     */
    @Override
    public void interrupt() {
        //LOGGER.debug("interrupt layout:" + layout);
        if (layout != null)
            layout.setDirty();
    }

    /**
     * Recipe handling
     */
    int consumedUnits = 0;
    void tickRecipe() {
        // Purely the passage of time
        consumedUnits++;
        markDirty();
    }

    /**
     * Tick Tracker
     */
    public class TickTracker {
        long lastGameTime = -1;
        int structureTicksTimeout = 0;
        int currStructureTicks = 0;

        public boolean tick(World world) {

            boolean realTick = false;
            long currGameTime = world.getGameTime();
            if (FactoryConfiguration.TICK_ACCEL.get() || lastGameTime != currGameTime) {
                // actual time has passed - no acceleration
                lastGameTime = currGameTime;
                realTick = true;

                if (structureTicksTimeout > 0)
                    currStructureTicks++;
            }

            return realTick;
        }

        public boolean hasStructureTickExpired() {
            return structureTicksTimeout > 0 && currStructureTicks >= structureTicksTimeout;
        }

        public void setStructureTickCount(int ticks) {
            structureTicksTimeout = ticks;
        }

        public void resetStructureTickCount() {
            currStructureTicks = 0;
        }
    }

    /**
     * Currently running recipe
     */
    public static class Recipe {

        int numTicks;
        int numUnits;
        public HashMap<FakeMob, List<ItemStack>> items = new HashMap<>();
        public HashMap<FakeMob, List<FluidStack>> fluids = new HashMap<>();

        public int getNumTicks() {
            return numTicks;
        }

        public int getNumUnits() {
            return numUnits;
        }

        public Recipe() {
            numTicks = 1;
            numUnits = 1;
        }

        public Recipe(int numTicks, int numUnits) {
            this.numTicks = MathHelper.clamp(numTicks, 1, Integer.MAX_VALUE);
            this.numUnits = MathHelper.clamp(numUnits, 1, Integer.MAX_VALUE);
        }

        public void addItem(FakeMob fakeMob, ItemStack itemStack) {
            if (!items.containsKey(fakeMob))
                items.put(fakeMob, new ArrayList<>());

            if (!itemStack.isEmpty())
                items.get(fakeMob).add(itemStack.copy());
        }

        public void addFluid(FakeMob fakeMob, FluidStack fluidStack) {
            if (!fluids.containsKey(fakeMob))
                fluids.put(fakeMob, new ArrayList<>());

            if (!fluidStack.isEmpty())
                fluids.get(fakeMob).add(fluidStack.copy());
        }

        @Override
        public String toString() {
            return "Recipe{" +
                    "numTicks=" + numTicks +
                    ", numUnits=" + numUnits +
                    ", items=" + items.size() +
                    ", fluids=" + fluids.size() +
                    '}';
        }
    }



    /**
     * WootDebug
     */
    @Override
    public List<String> getDebugText(List<String> debug, ItemUseContext itemUseContext) {
        debug.add("====> HeartTileEntity");
        debug.add("      layout: " + layout);
        debug.add("      setup: " + formedSetup);
        debug.add("      recipe: " + recipe);
        debug.add("      consumed: " + consumedUnits);
        return debug;
    }

    /**
     * INamedContainerProvider
     */
    @Override
    public ITextComponent getDisplayName() {
        if (isFormed())
            return StringHelper.translate(formedSetup.getTier().getTranslationKey());

        return new StringTextComponent(getType().getRegistryName().getPath());
    }

    @Nullable
    @Override
    public Container createMenu(int windowId, PlayerInventory playerInventory, PlayerEntity playerEntity) {
        return new HeartContainer(windowId, world, pos, playerInventory, playerEntity);
    }

    public int getFluidCapacity() {
        return formedSetup != null ? formedSetup.getCellCapacity() : 0;
    }
    public FluidStack getTankFluid() {
        return formedSetup != null ?
                new FluidStack(FluidSetup.CONATUS_FLUID.get(), formedSetup.getCellFluidAmount()) :
                FluidStack.EMPTY;
    }

    public int getProgress() {
        if (formedSetup == null)
            return 0;

        return (int)((100.0F / recipe.getNumTicks() * consumedUnits));
    }

    public List<FakeMob> getFormedMobs() {
        List<FakeMob> mobs = new ArrayList<>();
        if (isFormed())
            mobs.addAll(formedSetup.getAllMobs());
        return mobs;
    }

    @OnlyIn(Dist.CLIENT)
    public ClientFactorySetup clientFactorySetup;

    @OnlyIn(Dist.CLIENT)
    public void setClientFactorySetup(ClientFactorySetup clientFactorySetup) {
        this.clientFactorySetup = clientFactorySetup;
    }

    public HeartStaticDataReply createStaticDataReply2() {
        return new HeartStaticDataReply(formedSetup, recipe);
    }
}
