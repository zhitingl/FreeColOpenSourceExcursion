/**
 *  Copyright (C) 2002-2014   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.client;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.io.FreeColModFile;
import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.io.Mods;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.FreeColObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.option.BooleanOption;
import net.sf.freecol.common.option.IntegerOption;
import net.sf.freecol.common.option.ListOption;
import net.sf.freecol.common.option.ModListOption;
import net.sf.freecol.common.option.OptionGroup;
import net.sf.freecol.common.option.SelectOption;
import net.sf.freecol.common.option.TextOption;
import net.sf.freecol.common.util.Utils;

import javax.xml.stream.events.XMLEvent;


/**
 * Defines how available client options are displayed on the Setting
 * dialog from File/Preferences Also contains several Comparators used
 * for display purposes.
 *
 * Most available client options and their default values are defined
 * in the file <code>base/client-options.xml</code> in the FreeCol
 * data directory. They are overridden by the player's personal
 * settings in the file <code>options.xml</code> in the user
 * directory. Options that can not be defined in this way because they
 * are generated dynamically (such as a list of available mods) must
 * be added to {@link #addDefaultOptions()}.
 *
 * Each option should be given an unique identifier (defined as a
 * constant in this class). In general, the options are called
 * something like "model.option.UNIQUENAME". Since the options must
 * also be represented by the GUI, they following two keys must be
 * added to the file <code>FreeColMessages.properties</code>:
 *
 * <ul><li>model.option.UNIQUENAME.name</li>
 * <li>model.option.UNIQUENAME.shortDescription</li></ul>
 */
public class ClientOptions extends OptionGroup {

    private static final Logger logger = Logger.getLogger(ClientOptions.class.getName());

    //
    // Constants for each client option.
    // Keep these in sync with data/base/client-options.xml.
    //

    // clientOptions.personal

    /**
     * Option for the player's preferred name
     */
    public static final String NAME
        = "model.option.playerName";


    // clientOptions.gui

    /**
     * Option for setting the language.
     */
    public static final String LANGUAGE
        = "model.option.languageOption";
    // Value for automatic language selection.
    public static final String AUTOMATIC = "automatic";

    /**
     * Used by GUI, the number will be displayed when a group of goods are
     * higher than this number.
     *
     * @see net.sf.freecol.client.gui.MapViewer
     */
    public static final String MIN_NUMBER_FOR_DISPLAYING_GOODS_COUNT
        = "model.option.guiMinNumberToDisplayGoodsCount";

    /**
     * Used by GUI, this is the most repetitions drawn of a goods image for a
     * single goods grouping.
     *
     * @see net.sf.freecol.client.gui.MapViewer
     */
    public static final String MAX_NUMBER_OF_GOODS_IMAGES
        = "model.option.guiMaxNumberOfGoodsImages";

    /**
     * Used by GUI, this is the minimum number of goods a colony must
     * possess for the goods to show up at the bottom of the colony
     * panel.
     *
     * @see net.sf.freecol.client.gui.GUI
     */
    public static final String MIN_NUMBER_FOR_DISPLAYING_GOODS
        = "model.option.guiMinNumberToDisplayGoods";

    /**
     * Selected tiles always gets centered if this option is enabled (even if
     * the tile is {@link net.sf.freecol.client.gui.GUI#onScreen(Tile)}).
     *
     * @see net.sf.freecol.client.gui.GUI
     */
    public static final String ALWAYS_CENTER
        = "model.option.alwaysCenter";

    /**
     * If this option is enabled, the display will recenter in order
     * to display the active unit if it is not
     * {@link net.sf.freecol.client.gui.GUI#onScreen(Tile)}).
     *
     * @see net.sf.freecol.client.gui.GUI
     */
    public static final String JUMP_TO_ACTIVE_UNIT
        = "model.option.jumpToActiveUnit";

    /**
     * Option for activating autoscroll when dragging units on the mapboard.
     */
    public static final String MAP_SCROLL_ON_DRAG
        = "model.option.mapScrollOnDrag";

    /**
     * Option for activating autoscroll when dragging units on the mapboard.
     */
    public static final String AUTO_SCROLL
        = "model.option.autoScroll";

    /**
     * Whether to display a compass rose or not.
     */
    public static final String DISPLAY_COMPASS_ROSE
        = "model.option.displayCompassRose";

    /**
     * Whether to display the map controls or not.
     */
    public static final String DISPLAY_MAP_CONTROLS
        = "model.option.displayMapControls";

    /**
     * Whether to display the grid by default or not.
     */
    public static final String DISPLAY_GRID
        = "model.option.displayGrid";

    /**
     * Whether to display borders by default or not.
     */
    public static final String DISPLAY_BORDERS
        = "model.option.displayBorders";

    /**
     * Whether to delay on a unit's last move or not.
     */
    public static final String UNIT_LAST_MOVE_DELAY
        = "model.option.unitLastMoveDelay";

    /**
     * Pixmap setting to work around Java 2D graphics bug.
     */
    public static final String USE_PIXMAPS
        = "model.option.usePixmaps";

    /**
     * Whether to remember the positions of various dialogs and panels.
     */
    public static final String REMEMBER_PANEL_POSITIONS
        = "model.option.rememberPanelPositions";

    /**
     * Whether to remember the sizes of various dialogs and panels.
     */
    public static final String REMEMBER_PANEL_SIZES
        = "model.option.rememberPanelSizes";

    /**
     * If selected: Enables smooth rendering of the minimap when zoomed out.
     */
    public static final String SMOOTH_MINIMAP_RENDERING
        = "model.option.smoothRendering";

    /**
     * Whether to display end turn grey background or not.
     */
    public static final String DISABLE_GRAY_LAYER
        = "model.option.disableGrayLayer";

    /**
     * If selected: Draws the fog of war on the minimap.
     */
    public static final String MINIMAP_TOGGLE_FOG_OF_WAR
        = "model.option.miniMapToggleFogOfWar";
    
    /**
     * If selected: Draws the borders on the minimap.
     */
    public static final String MINIMAP_TOGGLE_BORDERS
        = "model.option.miniMapToggleBorders";
    
    /**
     * Style of map controls.
     */
    public static final String MAP_CONTROLS
        = "model.option.mapControls";
    public static final String MAP_CONTROLS_CORNERS = "CornerMapControls";
    public static final String MAP_CONTROLS_CLASSIC = "ClassicMapControls";

    /**
     * The color to fill in around the actual map on the
     * minimap.  Typically only visible when the minimap is at full
     * zoom-out, but at the default 'black' you can't differentiate
     * between the background and the (unexplored) map.  Actually:
     * clientOptions.minimap.color.background
     */
    public static final String MINIMAP_BACKGROUND_COLOR
        = "model.option.color.background";

    /**
     * What text to display in the tiles.
     */
    public static final String DISPLAY_TILE_TEXT
        = "model.option.displayTileText";
    public static final int DISPLAY_TILE_TEXT_EMPTY = 0,
        DISPLAY_TILE_TEXT_NAMES = 1,
        DISPLAY_TILE_TEXT_OWNERS = 2,
        DISPLAY_TILE_TEXT_REGIONS = 3;

    /**
     * Style of colony labels.
     */
    public static final String COLONY_LABELS
        = "model.option.displayColonyLabels";
    public static final int COLONY_LABELS_NONE = 0;
    public static final int COLONY_LABELS_CLASSIC = 1;
    public static final int COLONY_LABELS_MODERN = 2;

    /**
     * Used by GUI to sort colonies.
     */
    public static final String COLONY_COMPARATOR
        = "model.option.colonyComparator";
    public static final int COLONY_COMPARATOR_NAME = 0,
        COLONY_COMPARATOR_AGE = 1,
        COLONY_COMPARATOR_POSITION = 2,
        COLONY_COMPARATOR_SIZE = 3,
        COLONY_COMPARATOR_SOL = 4;

    /**
     * Default zoom level of the minimap.
     */
    public static final String DEFAULT_MINIMAP_ZOOM
        = "model.option.defaultZoomLevel";

    /**
     * Animation speed for friendly units.
     */
    public static final String MOVE_ANIMATION_SPEED
        = "model.option.moveAnimationSpeed";

    /**
     * Animation speed for enemy units.
     */
    public static final String ENEMY_MOVE_ANIMATION_SPEED
        = "model.option.enemyMoveAnimationSpeed";


    // clientOptions.messages

   /**
     * Used by GUI, this defines the grouping of ModelMessages.
     * Possible values include nothing, type and source.
     *
     * @see net.sf.freecol.client.gui.MapViewer
     * @see net.sf.freecol.common.model.ModelMessage
     */
    public static final String MESSAGES_GROUP_BY
        = "model.option.guiMessagesGroupBy";
    public static final int MESSAGES_GROUP_BY_NOTHING = 0,
        MESSAGES_GROUP_BY_TYPE = 1,
        MESSAGES_GROUP_BY_SOURCE = 2;

    /**
     * Show goods movement messages.
     */
    public static final String SHOW_GOODS_MOVEMENT
        = "model.option.guiShowGoodsMovement";

    /**
     * Show warnings about colony sites.
     */
    public static final String SHOW_COLONY_WARNINGS
        = "model.option.guiShowColonyWarnings";

    /**
     * Show the pre-combat dialog?
     */
    public static final String SHOW_PRECOMBAT
        = "model.option.guiShowPreCombat";

    /**
     * Show warnings about suboptimal colony tile choice.
     */
    public static final String SHOW_NOT_BEST_TILE
        = "model.option.guiShowNotBestTile";

    /**
     * Option for selecting the compact colony report.
     */
    public static final String COLONY_REPORT
        = "model.option.colonyReport";
    public static final int COLONY_REPORT_CLASSIC = 0;
    public static final int COLONY_REPORT_COMPACT = 1;

    /**
     * The type of labour report to display.
     */
    public static final String LABOUR_REPORT
        = "model.option.labourReport";
    public static final int LABOUR_REPORT_CLASSIC = 0;
    public static final int LABOUR_REPORT_COMPACT = 1;


    // clientOptions.savegames

    /**
     * Use default values for savegames instead of displaying a dialog. <br>
     * <br>
     * Possible values for this option are:
     * <ol>
     * <li>{@link #SHOW_SAVEGAME_SETTINGS_NEVER}</li>
     * <li>{@link #SHOW_SAVEGAME_SETTINGS_MULTIPLAYER}</li>
     * <li>{@link #SHOW_SAVEGAME_SETTINGS_ALWAYS}</li>
     * </ol>
     */
    public static final String SHOW_SAVEGAME_SETTINGS
        = "model.option.showSavegameSettings";
    public static final int SHOW_SAVEGAME_SETTINGS_NEVER = 0,
        SHOW_SAVEGAME_SETTINGS_MULTIPLAYER = 1,
        SHOW_SAVEGAME_SETTINGS_ALWAYS = 2;

    /**
     * Option for setting the period of autosaves. The value 0 signals that
     * autosaving is disabled.
     */
    public static final String AUTOSAVE_PERIOD
        = "model.option.autosavePeriod";

    /**
     * Option for setting the number of days autosaves are keep (valid
     * time).  If set to 0, valid time is not checked.
     */
    public static final String AUTOSAVE_VALIDITY
        = "model.option.autosaveValidity";

    /**
     * Option for deleting autosaves when a new game is started.  If set to
     * true, old autosaves will be deleted if a new game is started.
     */
    public static final String AUTOSAVE_DELETE
        = "model.option.autosaveDelete";

    /**
     * Whether to display confirmation for the overwrite of existing
     * save files.
     */
    public static final String CONFIRM_SAVE_OVERWRITE
        = "model.option.confirmSaveOverwrite";

    /**
     * Prefix for the auto-save file.
     */
    public static final String AUTO_SAVE_PREFIX
        = "model.option.autoSavePrefix";

    /**
     * Stem of the last-turn save file name.
     */
    public static final String LAST_TURN_NAME
        = "model.option.lastTurnName";

    /**
     * Stem of the before-last-turn save file name.
     */
    public static final String BEFORE_LAST_TURN_NAME
        = "model.option.beforeLastTurnName";


    // clientOptions.warehouse

    /**
     * The Stock the custom house should keep when selling goods.
     */
    public static final String CUSTOM_STOCK
        = "model.option.customStock";

    /**
     * Generate warning of stock drops below this percentage of capacity.
     */
    public static final String LOW_LEVEL
        = "model.option.lowLevel";

    /**
     * Generate warning of stock exceeds this percentage of capacity.
     */
    public static final String HIGH_LEVEL
        = "model.option.highLevel";


    // clientOptions.audio

    /**
     * Choose the default mixer.
     */
    public static final String AUDIO_MIXER
        = "model.option.audioMixer";

    /**
     * The volume level to set.
     */
    public static final String AUDIO_VOLUME
        = "model.option.audioVolume";

    /**
     * Play an alert sound on message arrival.
     */
    public static final String AUDIO_ALERTS
        = "model.option.audioAlerts";


    // clientOptions.other

    /**
     * Option for autoload emigrants on saling to america.
     */
    public static final String AUTOLOAD_EMIGRANTS
        = "model.option.autoloadEmigrants";

    /**
     * If enabled: Automatically ends the turn when no units can be
     * made active.
     */
    public static final String AUTO_END_TURN
        = "model.option.autoEndTurn";

    /**
     * Show EndTurnDialog.
     */
    public static final String SHOW_END_TURN_DIALOG
        = "model.option.showEndTurnDialog";

    /**
     * The Indian demand action.
     */
    public static final String INDIAN_DEMAND_RESPONSE
        = "model.option.indianDemandResponse";
    public static final int INDIAN_DEMAND_RESPONSE_ASK = 0;
    public static final int INDIAN_DEMAND_RESPONSE_ACCEPT = 1;
    public static final int INDIAN_DEMAND_RESPONSE_REJECT = 2;

    /**
     * The warehouse overflow on unload action.
     */
    public static final String UNLOAD_OVERFLOW_RESPONSE
        = "model.option.unloadOverflowResponse";
    public static final int UNLOAD_OVERFLOW_RESPONSE_ASK = 0;
    public static final int UNLOAD_OVERFLOW_RESPONSE_NEVER = 1;
    public static final int UNLOAD_OVERFLOW_RESPONSE_ALWAYS = 2;


    // clientOptions.mods

    /**
     * The mods.
     */
    public static final String USER_MODS
        = "userMods";


    /**
     * Comparators for sorting colonies.
     */
    private static Comparator<Colony> colonyAgeComparator
        = new Comparator<Colony>() {
            public int compare(Colony s1, Colony s2) {
                return s1.getEstablished().getNumber()
                    - s2.getEstablished().getNumber();
            }
        };

    private static Comparator<Colony> colonyNameComparator
        = new Comparator<Colony>() {
            public int compare(Colony s1, Colony s2) {
                return s1.getName().compareTo(s2.getName());
            }
        };

    private static Comparator<Colony> colonySizeComparator
        = new Comparator<Colony>() {
            public int compare(Colony s1, Colony s2) {
                // sort size descending, then SoL descending
                int dsize = s2.getUnitCount() - s1.getUnitCount();
                if (dsize == 0) {
                    return s2.getSoL() - s1.getSoL();
                } else {
                    return dsize;
                }
            }
        };

    private static Comparator<Colony> colonySoLComparator
        = new Comparator<Colony>() {
            public int compare(Colony s1, Colony s2) {
                // sort SoL descending, then size descending
                int dsol = s2.getSoL() - s1.getSoL();
                if (dsol == 0) {
                    return s2.getUnitCount() - s1.getUnitCount();
                } else {
                    return dsol;
                }
            }
        };

    private static Comparator<Colony> colonyPositionComparator
        = new Comparator<Colony>() {
            public int compare(Colony s1, Colony s2) {
                // sort north to south, then west to east
                int dy = s1.getTile().getY() - s2.getTile().getY();
                if (dy == 0) {
                    return s1.getTile().getX() - s2.getTile().getX();
                } else {
                    return dy;
                }
            }
       };


    private class MessageSourceComparator implements Comparator<ModelMessage> {
        private Game game;

        // sort according to message source

        private MessageSourceComparator(Game game) {
            this.game = game;
        }

        public int compare(ModelMessage message1, ModelMessage message2) {
            String sourceId1 = message1.getSourceId();
            String sourceId2 = message2.getSourceId();
            if (Utils.equals(sourceId1, sourceId2)) {
                return messageTypeComparator.compare(message1, message2);
            }
            FreeColGameObject source1 = game.getMessageSource(message1);
            FreeColGameObject source2 = game.getMessageSource(message2);
            int base = getClassIndex(source1) - getClassIndex(source2);
            if (base == 0) {
                if (source1 instanceof Colony) {
                    return getColonyComparator().compare((Colony) source1, (Colony) source2);
                }
            }
            return base;
        }

        private int getClassIndex(Object object) {
            if (object instanceof Player) {
                return 10;
            } else if (object instanceof Colony) {
                return 20;
            } else if (object instanceof Europe) {
                return 30;
            } else if (object instanceof Unit) {
                return 40;
            } else if (object instanceof FreeColGameObject) {
                return 50;
            } else {
                return 1000;
            }
        }
    }


    private Comparator<ModelMessage> messageTypeComparator
        = new Comparator<ModelMessage>() {
            // sort according to message type
            public int compare(ModelMessage message1, ModelMessage message2) {
                return message1.getMessageType().ordinal() - message2.getMessageType().ordinal();
            }
        };


    /**
     * Creates a new <code>ClientOptions</code>.
     * Unlike other OptionGroup classes, ClientOptions can not supply a
     * specification as it is needed before the specification is available.
     */
    public ClientOptions() {
        super(getXMLElementTagName());

        addDefaultOptions();
    }

    /**
     * Adds the options to this <code>GameOptions</code>.
     */
    private void addDefaultOptions() {
        loadOptions(new File(FreeColDirectories.getBaseDirectory(),
                             "client-options.xml"));
    }

    /**
     * Gets a list of active mods in this ClientOptions.
     *
     * @return A list of active mods.
     */
    @SuppressWarnings("unchecked")
    public List<FreeColModFile> getActiveMods() {
        List<FreeColModFile> active = new ArrayList<FreeColModFile>();
        ModListOption option = (ModListOption)getOption(ClientOptions.USER_MODS);
        if (option != null) {
            for (FreeColModFile modInfo : option.getOptionValues()) {
                if (modInfo != null && modInfo.getId() != null) {
                    FreeColModFile f = Mods.getFreeColModFile(modInfo.getId());
                    if (f != null) active.add(f);
                }
            }
        }
        return active;
    }

    /**
     * Loads the options from the given file.
     *
     * @param optionsFile The <code>File</code> to read the options from.
     * @return True if the options were loaded without error.
     */
    public boolean loadOptions(File optionsFile) {
        boolean ret = false;
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(optionsFile);
            try {
                ret = loadOptions(new BufferedInputStream(fis));
            } catch (IOException ioe) {
                logger.log(Level.WARNING, "Error reading client options file: "
                    + optionsFile.getPath(), ioe);
            } catch (XMLStreamException xse) {
                logger.log(Level.WARNING, "Error reading client options file: "
                    + optionsFile.getPath(), xse);
            }
        } catch (FileNotFoundException fnfe) {
            logger.log(Level.WARNING, "Client options file not found: "
                + optionsFile.getPath(), fnfe);
        } finally {
            try { if (fis != null) fis.close(); } catch (IOException ioe) {
                logger.log(Level.WARNING, "Failed to close stream.", ioe);
            }
        }
        return ret;
    }

    /**
     * Loads the options from the given stream.
     *
     * @param in The <code>InputStream</code> to read the options from.
     * @return True if the options were loaded without error.
     * @exception IOException if there is a problem opening a
     *     <code>FreeColXMLStreamReader</code>.
     * @exception XMLStreamException if there is problem reading the stream.
     */
    public boolean loadOptions(InputStream in)
        throws IOException, XMLStreamException {
        if (in != null) {
            FreeColXMLReader xr = null;
            try {
                xr = new FreeColXMLReader(in);
                xr.nextTag();
                readFromXML(xr);
                return true;
            } finally {
                if (xr != null) xr.close();
            }
        }
        return false;
    }

    /**
     * Updates the options from the given file.
     *
     * @param optionsFile The <code>File</code> to read the options from.
     */
    public void updateOptions(File optionsFile) {
        try {
            updateOptions(new BufferedInputStream(new FileInputStream(optionsFile)));
        } catch (FileNotFoundException e) {
            logger.warning("Could not find the client options file: "
                + optionsFile.getPath());
        }
    }

    /**
     * Updates the options from the given stream.
     *
     * @param in The <code>InputStream</code> to read the options from.
     */
    public void updateOptions(InputStream in) {
        FreeColXMLReader xr = null;
        try {
            xr = new FreeColXMLReader(in);
            xr.nextTag();
            readFromXML(xr);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Exception when loading options.", e);
        } finally {
            if (xr != null) xr.close();
        }
    }

    /**
     * Extracts the value of the LANGUAGE option from the client options file.
     *
     * @return The language option value, or null if none or IO error.
     */
    public static String getLanguageOption() {
        File options = FreeColDirectories.getClientOptionsFile();
        if (options.canRead()) {
            FreeColXMLReader xr = null;
            try {
                xr = new FreeColXMLReader(new FileInputStream(options));
                xr.nextTag();
                for (int type = xr.getEventType();
                     type != XMLEvent.END_DOCUMENT; type = xr.getEventType()) {
                    if (type == XMLEvent.START_ELEMENT
                        && LANGUAGE.equals(xr.readId())) {
                        return xr.getAttribute("value", (String)null);
                    }
                    xr.nextTag();
                }
                // We don't have a language option in our file, it is
                // either not there or the file is corrupt

            } catch (Exception e) {
                logger.log(Level.WARNING, "Failure getting language.", e);
            } finally {
                if (xr != null) xr.close();
            }
        }
        return null;
    }

    /**
     * Get the client's preferred tile text type.
     *
     * @return A <code>DISPLAY_TILE_TEXT_</code> value
     */
    public int getDisplayTileText() {
        return getInteger(DISPLAY_TILE_TEXT);
    }

    /**
     * Gets a fresh list of sorted colonies for a player.
     * Helper function for a common idiom.
     *
     * @param p The <code>Player</code> to get the colony list for.
     * @return A fresh list of colonies sorted according to the
     *    current comparator.
     */
    public List<Colony> getSortedColonies(Player p) {
        return p.getSortedColonies(getColonyComparator());
    }

    /**
     * Return the client's preferred comparator for colonies.
     *
     * @return a <code>Comparator</code> value
     */
    public Comparator<Colony> getColonyComparator() {
        return getColonyComparator(getInteger(COLONY_COMPARATOR));
    }

    /**
     * Return the colony comparator identified by type.
     *
     * @param type an <code>int</code> value
     * @return a <code>Comparator</code> value
     */
    public static Comparator<Colony> getColonyComparator(int type) {
        switch (type) {
        case COLONY_COMPARATOR_AGE:
            return colonyAgeComparator;
        case COLONY_COMPARATOR_POSITION:
            return colonyPositionComparator;
        case COLONY_COMPARATOR_SIZE:
            return colonySizeComparator;
        case COLONY_COMPARATOR_SOL:
            return colonySoLComparator;
        case COLONY_COMPARATOR_NAME:
            return colonyNameComparator;
        default:
            throw new IllegalStateException("Unknown comparator");
        }
    }

    /**
     * Return the client's preferred comparator for ModelMessages.
     *
     * @return a <code>Comparator</code> value
     */
    public Comparator<ModelMessage> getModelMessageComparator(Game game) {
        switch (getInteger(MESSAGES_GROUP_BY)) {
        case MESSAGES_GROUP_BY_SOURCE:
            return new MessageSourceComparator(game);
        case MESSAGES_GROUP_BY_TYPE:
            return messageTypeComparator;
        default:
            return null;
        }
    }

    /**
     * Returns the boolean option associated with a ModelMessage.
     *
     * @param message a <code>ModelMessage</code> value
     * @return a <code>BooleanOption</code> value
     */
    public BooleanOption getBooleanOption(ModelMessage message) {
        return (BooleanOption) getOption(message.getMessageType()
            .getOptionName());
    }

    /**
     * Perform backward compatibility fixups on new client options as
     * they are introduced.  Annotate with introduction version so we
     * can clean these up as they become standard.
     */
    public void fixClientOptions() {
        // @compat 0.10.1
        addIntegerOption(COLONY_REPORT,
            "clientOptions.messages", 0);
        addBooleanOption(USE_PIXMAPS,
            "clientOptions.gui", true);
        // end @compat 0.10.1
        // @compat 0.10.7
        addBooleanOption(CONFIRM_SAVE_OVERWRITE,
            "clientOptions.savegames", false);
        addBooleanOption(DISABLE_GRAY_LAYER,
            "clientOptions.gui", false);
        addBooleanOption(REMEMBER_PANEL_SIZES,
            "clientOptions.gui", true);
        // end @compat 0.10.7
        // @compact 0.11.0
        addBooleanOption(MINIMAP_TOGGLE_BORDERS,
            "clientOptions.gui", true);    
        addBooleanOption(MINIMAP_TOGGLE_FOG_OF_WAR,
            "clientOptions.gui", true);
        addTextOption(AUTO_SAVE_PREFIX,
            "clientOptions.savegames", "Autosave");
        addTextOption(LAST_TURN_NAME,
            "clientOptions.savegames", "last-turn");
        addTextOption(BEFORE_LAST_TURN_NAME,
            "clientOptions.savegames", "before-last-turn");
        // end @compact 0.11.0
    }

    private void addBooleanOption(String id, String gr, boolean val) {
        if (getOption(id) == null) {
            BooleanOption op = new BooleanOption(id, null);
            op.setGroup(gr);
            op.setValue(val);
            add(op);
        }
    }

    private void addIntegerOption(String id, String gr, int val) {
        if (getOption(id) == null) {
            IntegerOption op = new IntegerOption(id, null);
            op.setGroup(gr);
            op.setValue(val);
            add(op);
        }
    }

    private void addTextOption(String id, String gr, String val) {
        if (getOption(id) == null) {
            TextOption op = new TextOption(id, null);
            op.setGroup(gr);
            op.setValue(val);
            add(op);
        }
    }

    /**
     * Gets the tag name of the root element representing this object.
     *
     * @return "clientOptions".
     */
    public static String getXMLElementTagName() {
        return "clientOptions";
    }
}
