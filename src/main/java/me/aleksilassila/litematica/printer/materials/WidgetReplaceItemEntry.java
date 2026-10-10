package me.aleksilassila.litematica.printer.materials;

import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class WidgetReplaceItemEntry extends WidgetListEntryBase<Item> {
    private final boolean isOdd;
    private final ItemStack stack;
    private final String id;

    public WidgetReplaceItemEntry(int x, int y, int width, int height, boolean isOdd, Item item, int listIndex) {
        super(x, y, width, height, item, listIndex);

        this.isOdd = isOdd;
        this.stack = new ItemStack(item);
        this.id = BuiltInRegistries.ITEM.getKey(item).toString();
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        // Same row style as Litematica's material list
        if (selected || this.isMouseOver(mouseX, mouseY)) {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0xA0707070);
        } else if (this.isOdd) {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0xA0101010);
        } else {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0xA0303030);
        }

        int y = this.y + 3;
        RenderUtils.drawRect(ctx, this.x + 4, y, 16, 16, 0x20FFFFFF);
        ctx.renderItem(this.stack, this.x + 4, y);

        int textY = this.y + (this.height - this.fontHeight) / 2 + 1;
        this.drawStringWithShadow(ctx, this.x + 26, textY, 0xFFFFFFFF, this.stack.getHoverName().getString());
        this.drawStringWithShadow(ctx, this.x + this.width - this.getStringWidth(this.id) - 6, textY, 0xFF909090, this.id);

        super.render(ctx, mouseX, mouseY, selected);
    }
}
