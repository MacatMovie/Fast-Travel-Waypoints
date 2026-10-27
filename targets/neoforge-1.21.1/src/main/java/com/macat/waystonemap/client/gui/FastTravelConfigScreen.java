package com.macat.waystonemap.client.gui;

import com.macat.waystonemap.ConfigUiBridge;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/** Living Wind-style compact configuration navigator: main > common/client > subsections. */
public final class FastTravelConfigScreen extends Screen {
    private static final int TOP = 38, BOTTOM = 40, ROW = 28;
    private final Screen parent;
    private final String page;
    private final List<Entry> entries = new ArrayList<>();
    private double scroll;

    public FastTravelConfigScreen(Screen parent) {this(parent, "main");}
    private FastTravelConfigScreen(Screen parent, String page) {
        super(Component.literal(switch(page) {
            case "main" -> "Fast Travel Waypoints Configuration";
            case "common" -> "Fast Travel Waypoints - Common";
            case "client" -> "Fast Travel Waypoints - Client";
            case "costs" -> "XP Costs";
            case "waystones" -> "Waystone Access";
            case "countdown" -> "Teleport Countdown";
            case "environment" -> "Environment Restrictions";
            default -> "Fast Travel Waypoints";
        }));
        this.parent=parent;this.page=page;
    }
    @Override protected void init() {
        entries.clear();scroll=0;
        switch(page) {
            case "main" -> {
                nav("Common Options", "Costs, access, countdown and environment", "common");
                nav("Client Options", "Local sound selection", "client");
            }
            case "common" -> {
                nav("XP Costs", "XP costs and dimension travel", "costs");
                nav("Waystone Access", "Waystone proximity and range", "waystones");
                nav("Teleport Countdown", "Countdown skip options", "countdown");
                nav("Environment Restrictions", "Open sky and minimum height", "environment");
            }
            case "costs" -> {
                integer("XP Level Cost", "levelCost", "Levels consumed per successful teleport. Zero is free; ignored when Waystones scaling is enabled.");
                bool("Use Waystones XP Scaling", "xpScaling", "Use the Waystones mod's configured distance and dimension XP calculations.");
                bool("Cross-Dimensional Travel", "crossDimension", "Permit travel to Waystones in other dimensions.");
                integer("Extra Dimension XP Cost", "dimensionCost", "Added to level cost on cross-dimensional travel; ignored with Waystones scaling.");
            }
            case "waystones" -> {
                bool("Require Nearby Waystone", "nearby", "Require the player to stand near a Waystone to travel from Xaero's World Map.");
                integer("Nearby Waystone Radius", "nearbyRadius", "Allowed range in blocks (1 to 64). Also used by countdown skip checks.");
            }
            case "countdown" -> {
                bool("Skip Countdown Near Waystone", "skipNearby", "Skip the three-second countdown when standing close to a Waystone.");
                bool("Skip Countdown From Anywhere", "skipAnywhere", "Skip countdown when starting a teleport away from a Waystone.");
            }
            case "environment" -> {
                bool("Require Open Sky At Player", "openSkyPlayer", "Require clear sky at the starting position.");
                bool("Require Open Sky At Destination", "openSkyDestination", "Require clear sky above the destination Waystone.");
                dimensions();
                bool("Enable Minimum Y Check", "minYEnabled", "Prevent starting fast travel below a set world height.");
                integer("Minimum Y Level", "minY", "The minimum required starting height when enabled.");
            }
            case "client" -> {
                Button button = Button.builder(soundLabel(), b -> {
                    ConfigUiBridge.setEnderman(!ConfigUiBridge.useEnderman());
                    b.setMessage(soundLabel());
                    ConfigUiBridge.saveAll();
                }).bounds(0,0,150,20).tooltip(Tooltip.create(Component.literal("Hybrid A (default) or original Enderman sound. Changes what this client hears, including on servers."))).build();
                add("Teleport Arrival Sound", button);
            }
        }
        addRenderableWidget(Button.builder(Component.literal(page.equals("main") ? "Done" : "Back"),b -> goBack())
            .bounds(width/2 - 77,height-29,154,20).build());
        position();
    }
    private static Component soundLabel() {
        return Component.literal(ConfigUiBridge.useEnderman() ? "Enderman (Original)" : "Hybrid A (New)");
    }
    private void add(String label, net.minecraft.client.gui.components.AbstractWidget widget) {
        entries.add(new Entry(label, widget));addRenderableWidget(widget);
    }
    private void nav(String label,String description,String next) {
        Button b=Button.builder(Component.literal("Configure"), widget -> {
            ConfigUiBridge.saveAll();minecraft.setScreen(new FastTravelConfigScreen(this,next));
        }).bounds(0,0,150,20).tooltip(Tooltip.create(Component.literal(description))).build();
        add(label,b);
    }
    private void bool(String label,String id,String description) {
        Button b=Button.builder(Component.translatable(ConfigUiBridge.bool(id)?"options.on":"options.off"), widget -> {
            boolean next=!ConfigUiBridge.bool(id);ConfigUiBridge.setBool(id,next);
            widget.setMessage(Component.translatable(next?"options.on":"options.off"));
            ConfigUiBridge.saveAll();
        }).bounds(0,0,150,20).tooltip(Tooltip.create(Component.literal(description))).build();add(label,b);
    }
    private void integer(String label,String id,String description) {
        EditBox edit=new EditBox(font,0,0,150,20,Component.literal(label));
        edit.setMaxLength(8); edit.setValue(Integer.toString(ConfigUiBridge.integer(id)));
        edit.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d{1,7}"));
        edit.setResponder(s -> {try {int v=Integer.parseInt(s);if(v>=ConfigUiBridge.min(id)&&v<=ConfigUiBridge.max(id)) {
            ConfigUiBridge.setInteger(id,v);ConfigUiBridge.saveAll();
        }}catch(NumberFormatException ignored){}});
        edit.setTooltip(Tooltip.create(Component.literal(description+" ["+ConfigUiBridge.min(id)+" .. "+ConfigUiBridge.max(id)+"]")));
        add(label,edit);
    }
    private void dimensions() {
        EditBox edit=new EditBox(font,0,0,150,20,Component.literal("Open Sky Dimensions"));
        edit.setMaxLength(1024);edit.setValue(ConfigUiBridge.whitelist());
        edit.setResponder(s -> {ConfigUiBridge.setWhitelist(s);ConfigUiBridge.saveAll();});
        edit.setTooltip(Tooltip.create(Component.literal("Comma-separated dimension IDs, e.g. minecraft:overworld, minecraft:the_end. Empty means no restrictions.")));
        add("Open Sky Dimensions",edit);
    }
    private void goBack() {ConfigUiBridge.saveAll();minecraft.setScreen(parent);}
    @Override public void onClose() {goBack();}
    private int maxScroll() {return Math.max(0,entries.size()*ROW-(height-TOP-BOTTOM));}
    private void position() {
        int y=TOP-(int)scroll;int rightX=width/2+8;
        for(Entry e:entries){e.widget.setX(rightX);e.widget.setY(y+3);e.widget.setWidth(Math.min(150,Math.max(75,width/2-17)));
            e.widget.visible=y>=TOP&&y+ROW<=height-BOTTOM;y+=ROW;}
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double amount){
        if(my>=TOP&&my<height-BOTTOM&&maxScroll()>0){scroll=Math.min(maxScroll(),Math.max(0,scroll-amount*ROW));position();return true;}
        return super.mouseScrolled(mx,my,horizontal,amount);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        // Minecraft 1.21.1 draws the vanilla blur and dark tint in Screen.render.
        // Draw widgets first, then our custom labels so the background stays behind
        // all menu elements instead of covering them. This also avoids rendering
        // the blurred background a second time.
        super.render(g,mx,my,partial);
        for(int i=0;i<entries.size();i++){
            int y=TOP+i*ROW-(int)scroll;
            if(y>=TOP&&y+ROW<=height-BOTTOM)g.drawString(font,entries.get(i).label,Math.max(7,width/2-171),y+9,0xFFFFFF,false);
        }
        g.drawCenteredString(font,title,width/2,14,0xFFFFFF);
    }
    private record Entry(String label,net.minecraft.client.gui.components.AbstractWidget widget){}
}
