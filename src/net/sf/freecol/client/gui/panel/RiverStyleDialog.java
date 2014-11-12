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

import javax.swing.ImageIcon;
import javax.swing.JPanel;

import java.util.List;
import java.util.logging.Logger;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.client.gui.i18n.Messages;
import net.sf.freecol.common.resources.ResourceManager;


/**
 * A panel for adjusting the river style.
 *
 * This panel is only used when running in
 * {@link net.sf.freecol.client.FreeColClient#isMapEditor()} map editor mode.
 */
public final class RiverStyleDialog extends FreeColChoiceDialog<String> {

    @SuppressWarnings("unused")
    private static final Logger logger = Logger.getLogger(RiverStyleDialog.class.getName());

    public static final String DELETE = "DELETE";

    private static final String PREFIX = "model.tile.river";


    /**
     * Creates a dialog to choose a river style.
     *
     * @param freeColClient The <code>FreeColClient</code> for the game.
     */
    public RiverStyleDialog(FreeColClient freeColClient) {
        super(freeColClient);

        JPanel panel = new JPanel();
        String str = Messages.message("riverStyleDialog.text");
        panel.add(GUI.getDefaultHeader(str), "align center");

        List<ChoiceItem<String>> c = choices();
        c.add(new ChoiceItem<String>(DELETE, DELETE)
            .setIcon(new ImageIcon(getGUI().getImageLibrary()
                    .getMiscImage(ImageLibrary.DELETE, 0.5))));
        for (String key : ResourceManager.getKeys(PREFIX)) {
            c.add(new ChoiceItem<String>(key, key)
                .setIcon(new ImageIcon(ResourceManager.getImage(key, 0.5))));
        }
       
        initializeChoiceDialog(true, panel, null, "cancel", c);
    }
}
