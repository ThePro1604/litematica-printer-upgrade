package me.aleksilassila.litematica.printer.materials;

import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfirmAction;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.interfaces.IConfirmationListener;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;

/**
 * Lets the player pick the block item to replace a material list entry with.
 */
public class GuiMaterialReplace extends GuiListBase<Item, WidgetReplaceItemEntry, WidgetListReplaceItems>
        implements ISelectionListener<Item> {
    private final SchematicPlacement placement;
    private final MaterialListBase materialList;
    private final MaterialListEntry entry;
    private final List<Item> items;

    public GuiMaterialReplace(SchematicPlacement placement, MaterialListBase materialList, MaterialListEntry entry, Screen parent) {
        super(10, 40);

        this.placement = placement;
        this.materialList = materialList;
        this.entry = entry;
        this.title = StringUtils.translate("litematica-printer.gui.title.material_replace", entry.getStack().getHoverName().getString());
        this.setParent(parent);

        Item original = entry.getStack().getItem();
        this.items = BuiltInRegistries.ITEM.stream()
                .filter(item -> item instanceof BlockItem && item != original)
                .sorted(Comparator.comparing(item -> new ItemStack(item).getHoverName().getString()))
                .toList();
    }

    public List<Item> getItems() {
        return this.items;
    }

    @Override
    protected int getBrowserWidth() {
        return this.width - 20;
    }

    @Override
    protected int getBrowserHeight() {
        return this.height - 80;
    }

    @Override
    protected WidgetListReplaceItems createListWidget(int listX, int listY) {
        return new WidgetListReplaceItems(listX, listY, this.getBrowserWidth(), this.getBrowserHeight(), this);
    }

    @Override
    public void initGui() {
        super.initGui();

        String label = StringUtils.translate("malilib.gui.button.cancel");
        int width = this.getStringWidth(label) + 10;
        this.addButton(new ButtonGeneric(12, this.height - 32, width, 20, label), (button, mouseButton) -> GuiBase.openGui(this.getParent()));
    }

    @Override
    public void onSelectionChange(@Nullable Item item) {
        if (item == null) {
            return;
        }

        String from = this.entry.getStack().getHoverName().getString();
        String to = new ItemStack(item).getHoverName().getString();
        GuiConfirmAction confirm = new GuiConfirmAction(320, "litematica-printer.gui.title.material_replace_confirm",
                new ConfirmListener(this.placement, this.materialList, this.entry, item), this.getParent(),
                "litematica-printer.gui.message.material_replace_confirm",
                from, to, this.entry.getCountTotal(), this.placement.getName());
        GuiBase.openGui(confirm);
    }

    private record ConfirmListener(SchematicPlacement placement, MaterialListBase materialList,
                                   MaterialListEntry entry, Item replacement) implements IConfirmationListener {
        @Override
        public boolean onActionConfirmed() {
            int replaced = MaterialReplacer.replace(this.placement, this.materialList, this.entry.getStack(), this.replacement);
            String to = new ItemStack(this.replacement).getHoverName().getString();

            if (replaced > 0) {
                InfoUtils.showGuiOrInGameMessage(MessageType.SUCCESS, "litematica-printer.message.material_replaced",
                        this.entry.getStack().getHoverName().getString(), to);
            } else {
                InfoUtils.showGuiOrInGameMessage(MessageType.ERROR, "litematica-printer.message.material_replace_failed",
                        this.entry.getStack().getHoverName().getString(), to);
            }

            return true;
        }

        @Override
        public boolean onActionCancelled() {
            return true;
        }
    }
}
