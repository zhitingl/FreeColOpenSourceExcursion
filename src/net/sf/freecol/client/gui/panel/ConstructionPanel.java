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

package net.sf.freecol.client.gui.panel;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.Image;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.client.gui.i18n.Messages;
import net.sf.freecol.client.gui.panel.MigPanel;
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.BuildableType;
import net.sf.freecol.common.model.BuildingType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.resources.ResourceManager;


/**
 * This panel shows the progress of constructing a building or
 * unit in a colony.
 */
public class ConstructionPanel extends MigPanel
    implements PropertyChangeListener {

    public static final String EVENT
        = Colony.ColonyChangeEvent.BUILD_QUEUE_CHANGE.toString();

    /** The enclosing client. */
    private final FreeColClient freeColClient;

    /** Should a mouse click open the build queue? */
    private final boolean openBuildQueue;

    /** The colony performing the construction. */
    private Colony colony;

    /** The text to display if buildable == null. */
    private StringTemplate defaultLabel
        = StringTemplate.key("colonyPanel.clickToBuild");


    /**
     * Creates a ConstructionPanel.
     *
     * @param freeColClient The <code>FreeColClient</code> for the game.
     * @param colony The <code>Colony</code> whose construction is to be
     *     modified.
     * @param openBuildQueue True if the build queue should be immediately
     *     shown.
     */
    public ConstructionPanel(FreeColClient freeColClient,
                             Colony colony, boolean openBuildQueue) {
        super("ConstructionPanelUI");

        this.freeColClient = freeColClient;
        this.colony = colony;
        this.openBuildQueue = openBuildQueue;

        setLayout(new MigLayout("fill, gapy 2:5, wrap 2",
                "push[]10[center]push"));
        setOpaque(openBuildQueue);
    }


    public void setColony(Colony newColony) {
        if (newColony != colony) {
            cleanup();
            this.colony = newColony;
            initialize();
        }
    }

    public void initialize() {
        if (colony != null) {
            // we are interested in changes to the build queue, as well as
            // changes to the warehouse and the colony's production bonus
            colony.addPropertyChangeListener(EVENT, this);
                
            if (openBuildQueue) {
                addMouseListener(new MouseAdapter() {
                        public void mousePressed(MouseEvent e) {
                            freeColClient.getGUI().showBuildQueuePanel(colony);
                        }
                    });
            }
        }
        update();
    }

    public void cleanup() {
        if (colony != null) {
            colony.removePropertyChangeListener(EVENT, this);
        }
        for (MouseListener listener : getMouseListeners()) {
            removeMouseListener(listener);
        }
    }

    public void update() {
        BuildableType buildable = (colony == null) ? null
            : colony.getCurrentlyBuilding();
        GUI gui = freeColClient.getGUI();

        removeAll();

        if (buildable == null) {
            String clickToBuild = Messages.message(getDefaultLabel());
            int breakingPoint = Messages.getBreakingPoint(clickToBuild);
            if (breakingPoint > 0) {
                add(new JLabel(clickToBuild.substring(0, breakingPoint)),
                    "span, align center");
                add(new JLabel(clickToBuild.substring(breakingPoint + 1)),
                    "span, align center");
            } else {
                add(new JLabel(clickToBuild), "span, align center");
            }
        } else {
            int turnsToComplete = colony.getTurnsToComplete(buildable);
            String turnsStr = Messages.getTurnsText(turnsToComplete);
            // TODO: distinguish national unit types
            Image image = (buildable instanceof BuildingType)
                ? gui.getImageLibrary()
                    .getBuildingImage((BuildingType) buildable, colony.getOwner(), 0.75)
                : gui.getImageLibrary().getImage(buildable, 0.75);
            add(new JLabel(new ImageIcon(image)), "spany");
            add(new JLabel(Messages.message(StringTemplate.template("colonyPanel.currentlyBuilding")
                                            .addName("%buildable%", buildable))));

            add(new JLabel(Messages.message(StringTemplate.template("turnsToComplete.long")
                                            .addName("%number%", turnsStr))));

            for (AbstractGoods ag : buildable.getRequiredGoods()) {
                int amountNeeded = ag.getAmount();
                int amountAvailable = colony.getGoodsCount(ag.getType());
                int amountProduced = colony.getAdjustedNetProductionOf(ag.getType());
                add(new FreeColProgressBar(gui, ag.getType(), 0,
                                           amountNeeded, amountAvailable, amountProduced),
                    "height 20:");
            }
        }

        revalidate();
        repaint();
    }


    /**
     * Get the <code>DefaultLabel</code> value.
     *
     * @return a <code>StringTemplate</code> value
     */
    public final StringTemplate getDefaultLabel() {
        return defaultLabel;
    }

    /**
     * Set the <code>DefaultLabel</code> value.
     *
     * @param newDefaultLabel The new DefaultLabel value.
     */
    public final void setDefaultLabel(final StringTemplate newDefaultLabel) {
        this.defaultLabel = newDefaultLabel;
    }


    // Interface PropertyChangeListener

    /**
     * {@inheritDoc}
     */
    public void propertyChange(PropertyChangeEvent event) {
        update();
    }
}
