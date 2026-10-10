package me.aleksilassila.litematica.printer.mixin;

import fi.dy.masa.litematica.gui.widgets.WidgetMaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListPlacement;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntrySortable;
import fi.dy.masa.malilib.util.GuiUtils;
import fi.dy.masa.malilib.util.StringUtils;
import me.aleksilassila.litematica.printer.materials.GuiMaterialReplace;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(value = WidgetMaterialListEntry.class, remap = false)
public abstract class WidgetMaterialListEntryMixin extends WidgetListEntrySortable<MaterialListEntry> {
    @Shadow @Final private MaterialListBase materialList;
    @Shadow @Final @Nullable private MaterialListEntry entry;

    protected WidgetMaterialListEntryMixin(int x, int y, int width, int height, @Nullable MaterialListEntry entry, int listIndex) {
        super(x, y, width, height, entry, listIndex);
    }

    // Adds a Replace button to the left of the Ignore button, for placed schematics only
    @Inject(method = "<init>", at = @At("TAIL"))
    private void addReplaceButton(CallbackInfo ci) {
        if (this.entry == null || !(this.materialList instanceof MaterialListPlacement materialListPlacement)) {
            return;
        }

        int xRight = this.x + this.width;
        for (WidgetBase widget : this.subWidgets) {
            if (widget instanceof ButtonBase) {
                xRight = Math.min(xRight, widget.getX());
            }
        }

        MaterialListEntry entry = this.entry;
        MaterialListBase materialList = this.materialList;
        String label = StringUtils.translate("litematica-printer.gui.button.material_list.replace");

        this.addButton(new ButtonGeneric(xRight - 2, this.y + 1, -1, true, label), (button, mouseButton) ->
                GuiBase.openGui(new GuiMaterialReplace(((MaterialListPlacementAccessor) materialListPlacement).getPlacement(),
                        materialList, entry, GuiUtils.getCurrentScreen())));
    }
}
