package me.aleksilassila.litematica.printer.materials;

import com.google.common.collect.ImmutableList;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetSearchBar;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;

public class WidgetListReplaceItems extends WidgetListBase<Item, WidgetReplaceItemEntry> {
    private final GuiMaterialReplace gui;

    public WidgetListReplaceItems(int x, int y, int width, int height, GuiMaterialReplace gui) {
        super(x, y, width, height, gui);

        this.gui = gui;
        this.browserEntryHeight = 22;
        this.widgetSearchBar = new WidgetSearchBar(x + 2, y + 4, width - 16, 14, 0, Icons.FILE_ICON_SEARCH, LeftRight.RIGHT);
        this.widgetSearchBar.setSearchOpen(true);
        this.browserEntriesOffsetY = this.widgetSearchBar.getHeight() + 3;
    }

    @Override
    protected Collection<Item> getAllEntries() {
        return this.gui.getItems();
    }

    @Override
    protected List<String> getEntryStringsForFilter(Item item) {
        return ImmutableList.of(new ItemStack(item).getHoverName().getString().toLowerCase(),
                BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase());
    }

    @Override
    protected WidgetReplaceItemEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd, Item item) {
        return new WidgetReplaceItemEntry(x, y, this.browserEntryWidth, this.getBrowserEntryHeightFor(item), isOdd, item, listIndex);
    }
}
