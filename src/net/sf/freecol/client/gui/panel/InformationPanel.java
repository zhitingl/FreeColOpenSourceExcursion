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

import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import net.miginfocom.swing.MigLayout;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.client.gui.i18n.Messages;
import net.sf.freecol.client.gui.panel.MigPanel;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.FreeColObject;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.WorkLocation;
import net.sf.freecol.common.resources.ResourceManager;


/**
 * A general panel for information display.
 */
public class InformationPanel extends FreeColPanel {

    /**
     * Creates an information panel that shows the given texts and
     * images, and an "OK" button.
     *
     * @param freeColClient The <code>FreeColClient</code> for the game.
     * @param text The text to be displayed in the panel.
     * @param image The image to be displayed in the panel.
     */
    public InformationPanel(FreeColClient freeColClient,
                            String text, FreeColObject fco, ImageIcon image) {
        this(freeColClient, new String[] { text }, new FreeColObject[] { fco },
             new ImageIcon[] { image });
    }

    /**
     * Creates an information panel that shows the given
     * texts and images, and an "OK" button.
     *
     * @param freeColClient The <code>FreeColClient</code> for the game.
     * @param texts The texts to be displayed in the panel.
     * @param fcos The source <code>FreeColObject</code> for the text.
     * @param images The images to be displayed in the panel.
     */
    public InformationPanel(FreeColClient freeColClient, String[] texts,
                            FreeColObject[] fcos, ImageIcon[] images) {
        super(freeColClient, new MigLayout("wrap 1, insets 200 10 10 10",
                "[510]", "[242]20[20]"));

        final GUI gui = getGUI();
        JPanel textPanel = new MigPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new MigLayout("wrap 2", "", ""));
        for (int i = 0; i < texts.length; i++) {
            if (images != null && images[i] != null) {
                textPanel.add(new JLabel(images[i]));
                textPanel.add(GUI.getDefaultTextArea(texts[i], 30));
            } else {
                textPanel.add(GUI.getDefaultTextArea(texts[i], 30), "skip");
            }
            String disp = (fcos == null) ? null : displayLabel(fcos[i]);
            if (disp != null) {
                JButton button = new JButton(Messages.message("display")
                    + " " + disp);
                final FreeColObject fco = fcos[i];
                button.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            displayFCO(gui, fco);
                        }
                    });
                textPanel.add(button, "skip");
            }
        }

        JScrollPane scrollPane = new JScrollPane(textPanel,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        // correct way to make scroll pane opaque
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        setBorder(null);

        add(scrollPane);
        add(okButton, "tag ok");
    }

    /**
     * A label for an FCO that can meaningfully be displayed.
     * Keep this routine synchronized with {@link #displayFCO}.
     *
     * @param fco The <code>FreeColObject</code> to check.
     * @return An i18n-compliant label.
     */
    private String displayLabel(FreeColObject fco) {
        return (fco instanceof Colony)
            ? ((getMyPlayer().owns((Colony)fco))
                ? Messages.message(((Colony)fco).getLocationName())
                : null)

            : (fco instanceof Europe)
            ? Messages.message(((Europe)fco).getLocationName())

            : (fco instanceof IndianSettlement)
            ? Messages.message(((IndianSettlement)fco).getLocationName())

            : (fco instanceof Tile)
            ? Messages.message(StringTemplate.template("tile")
                .addAmount("%x%", ((Tile)fco).getX())
                .addAmount("%y%", ((Tile)fco).getY()))

            : (fco instanceof Unit)
            ? displayLabel((FreeColObject)((Unit)fco).getLocation())

            : (fco instanceof WorkLocation)
            ? Messages.message(((WorkLocation)fco).getColony()
                .getLocationName())
            : null;
    }

    /**
     * Handler for the display buttons on this panel.
     *
     * @param gui The <code>GUI</code> to display on.
     * @param fco The <code>FreeColObject</code> to display.
     */
    private void displayFCO(GUI gui, FreeColObject fco) {
        if (fco instanceof Colony) {
            gui.showColonyPanel((Colony)fco, null);
        } else if (fco instanceof Europe) {
            gui.showEuropePanel();
        } else if (fco instanceof IndianSettlement) {
            gui.showIndianSettlementPanel((IndianSettlement)fco);
        } else if (fco instanceof Tile) {
            gui.setFocus((Tile)fco);
        } else if (fco instanceof Unit) {
            displayFCO(gui, (FreeColObject)(((Unit)fco).getLocation()));
        } else if (fco instanceof WorkLocation) {
            gui.showColonyPanel(((WorkLocation)fco).getColony(), null);
        }
    }


    // Override JComponent

    @Override
    public void paintComponent(Graphics g) {
        g.drawImage(ResourceManager.getImage("InformationPanel.backgroundImage"),
            0, 0, this);
    }
}
