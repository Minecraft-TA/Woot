package ipsis.woot.modules.infuser.client;

import com.mojang.blaze3d.platform.GlStateManager;
import ipsis.woot.Woot;
import ipsis.woot.modules.infuser.InfuserConfiguration;
import ipsis.woot.modules.infuser.blocks.InfuserContainer;
import ipsis.woot.util.WootContainerScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

public class InfuserScreen extends WootContainerScreen<InfuserContainer> {

    private ResourceLocation GUI = new ResourceLocation(Woot.MODID, "textures/gui/infuser.png");

    private static final int GUI_XSIZE = 180;
    private static final int GUI_YSIZE = 177;

    private static final int ENERGY_LX = 10;
    private static final int ENERGY_LY = 18;
    private static final int ENERGY_RX = 25;
    private static final int ENERGY_RY = 77;
    private static final int ENERGY_WIDTH = ENERGY_RX - ENERGY_LX + 1;
    private static final int ENERGY_HEIGHT = ENERGY_RY - ENERGY_LY + 1;

    private static final int TANK_LX = 154;
    private static final int TANK_LY = 18;
    private static final int TANK_RX = 169;
    private static final int TANK_RY = 77;
    private static final int TANK_WIDTH = TANK_RX - TANK_LX + 1;
    private static final int TANK_HEIGHT = TANK_RY - TANK_LY + 1;

    public InfuserScreen(InfuserContainer container, PlayerInventory playerInventory, ITextComponent name) {
        super(container, playerInventory, name);
        xSize = GUI_XSIZE;
        ySize = GUI_YSIZE;
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        this.renderBackground();
        super.render(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);

        if (isPointInRegion(TANK_LX, TANK_LY, TANK_WIDTH, TANK_HEIGHT, mouseX, mouseY))
            renderFluidTankTooltip(mouseX, mouseY, container.getInputFluid(),
                    InfuserConfiguration.INFUSER_TANK_CAPACITY.get());
        if (isPointInRegion(ENERGY_LX, ENERGY_LY, ENERGY_WIDTH, ENERGY_HEIGHT, mouseX, mouseY))
            renderEnergyTooltip(mouseX, mouseY, container.getEnergy(),
                    InfuserConfiguration.INFUSER_MAX_ENERGY.get(), InfuserConfiguration.INFUSER_ENERGY_PER_TICK.get());
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        minecraft.getTextureManager().bindTexture(GUI);
        int relX = (this.width - this.xSize) / 2;
        int relY = (this.height - this.ySize) / 2;
        blit(relX, relY, 0, 0, this.xSize, this.ySize);

        // Progress
        int progress = container.getProgress();
        blit(this.guiLeft + 90, this.guiTop + 39, 180, 0,(int)(18 * (progress / 100.0F)) , 17);

        renderEnergyBar(
                ENERGY_LX,
                ENERGY_RY,
                ENERGY_HEIGHT,
                ENERGY_WIDTH,
                container.getEnergy(), InfuserConfiguration.INFUSER_MAX_ENERGY.get());

        renderFluidTank(
                TANK_LX,
                TANK_RY,
                TANK_HEIGHT,
                TANK_WIDTH,
                InfuserConfiguration.INFUSER_TANK_CAPACITY.get(),
                container.getInputFluid());

    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String text = title.getFormattedText();
        this.font.drawString(text, (float)(this.xSize / 2 - this.font.getStringWidth(text) / 2), 6.0F, 4210752);
    }
}



