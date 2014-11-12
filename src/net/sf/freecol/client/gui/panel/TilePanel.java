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

import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.ComponentInputMap;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import net.miginfocom.swing.MigLayout;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.client.gui.i18n.Messages;
import net.sf.freecol.common.debug.DebugUtils;
import net.sf.freecol.common.debug.FreeColDebugger;
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.ProductionType;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.UnitType;


/**
 * This panel is used to show information about a tile.
 */
public final class TilePanel extends FreeColPanel {

    @SuppressWarnings("unused")
    private static final Logger logger = Logger.getLogger(TilePanel.class.getName());

    private static final String COLOPEDIA = "COLOPEDIA";


    /**
     * Creates a panel describing a tile.
     *
     * @param freeColClient The <code>FreeColClient</code> for the game.
     * @param tile The <code>Tile</code> to describe.
     */
    public TilePanel(FreeColClient freeColClient, Tile tile) {
        super(freeColClient, new MigLayout("wrap 2, insets 20 30 10 30",
                                           "[right, sg][left, sg]"));

        TileType tileType = tile.getType();
        JButton colopediaButton = new JButton(Messages.message("menuBar.colopedia"));
        colopediaButton.setActionCommand(tile.getType().getId());
        colopediaButton.addActionListener(this);

        // Use ESCAPE for closing the panel:
        InputMap inputMap = new ComponentInputMap(okButton);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false), "pressed");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, true), "released");
        SwingUtilities.replaceUIInputMap(okButton, JComponent.WHEN_IN_FOCUSED_WINDOW, inputMap);

        StringTemplate template = StringTemplate.template("tilePanel.label")
            .addStringTemplate("%label%", tile.getLabel())
            .addAmount("%x%", tile.getX())
            .addAmount("%y%", tile.getY());
        add(new JLabel(Messages.message(template)), "span, center");

        final ImageLibrary lib = getLibrary();
        final Image terrain = lib.getTerrainImage(tileType, tile.getX(), tile.getY());
        final int width = terrain.getWidth(null);
        final int baseHeight = terrain.getHeight(null);
        int height = lib.getCompoundTerrainImageHeight(tileType);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = (Graphics2D)image.getGraphics();
        g.translate(0, height - baseHeight);
        getGUI().displayColonyTile(g, tile, null);
        add(new JLabel(new ImageIcon(image)), "span, center");

        if (tile.getRegion() != null) {
            add(localizedLabel("tilePanel.region"));
            add(new JLabel(Messages.message(tile.getRegion().getLabel())));
        }
        if (tile.getOwner() != null) {
            StringTemplate ownerName = tile.getOwner().getNationName();
            if (ownerName != null) {
                add(localizedLabel("tilePanel.owner"));
                add(new JLabel(Messages.message(ownerName)));
            }
        }
        if (tile.getOwningSettlement() != null) {
            add(localizedLabel("tilePanel.settlement"));
            add(new JLabel(tile.getOwningSettlement().getName()));
        }

        if (tileType != null) {
            // TODO: make this more generic
            UnitType colonist = getSpecification().getDefaultUnitType();

            JLabel label = null;
            boolean first = true;
            for (ProductionType productionType
                     : tileType.getAvailableProductionTypes(false)) {
                for (AbstractGoods output : productionType.getOutputs()) {
                    GoodsType goodsType = output.getType();
                    int potential = output.getAmount();
                    if (tile.getTileItemContainer() != null) {
                        potential = tile.getTileItemContainer()
                            .getTotalBonusPotential(goodsType, colonist, potential, true);
                    }
                    int expertPotential = potential;
                    UnitType expert = getSpecification().getExpertForProducing(goodsType);
                    if (expert != null) {
                        expertPotential = (int)expert.applyModifiers(potential,
                            getGame().getTurn(), goodsType.getId());
                    }
                    if (potential > 0) {
                        label = new JLabel(String.valueOf(potential),
                                           getLibrary().getGoodsImageIcon(goodsType),
                                           JLabel.CENTER);
                        if (first) {
                            add(label, "span, split, center");
                            first = false;
                        } else {
                            add(label);
                        }
                    }
                    if (expertPotential > potential) {
                        if (label == null) {
                            // this could happen if a resource were exploitable
                            // only by experts, for example
                            label = new JLabel(String.valueOf(expertPotential),
                                               getLibrary().getGoodsImageIcon(goodsType),
                                               JLabel.CENTER);
                            label.setToolTipText(Messages.message(expert.getNameKey()));
                            if (first) {
                                add(label, "span, split");
                                first = false;
                            } else {
                                add(new JLabel("/"));
                                add(label);
                            }
                        } else {
                            label.setText(String.valueOf(potential) + "/" +
                                          String.valueOf(expertPotential));
                            label.setToolTipText(Messages.message(colonist.getNameKey()) + "/" +
                                                 Messages.message(expert.getNameKey()));
                        }
                    }
                }
            }
        }

        Player player = FreeColDebugger.debugDisplayColonyValuePlayer();
        if (player != null) {
            List<Double> values = player.getAllColonyValues(tile);
            int result = player.getColonyValue(tile);
            if (result < 0) {
                add(new JLabel(DebugUtils.getColonyValue(tile)),
                    "newline 5, span, align center");
            } else {
                for (Player.ColonyValueCategory c
                         : Player.ColonyValueCategory.values()) {
                    String cat = c.toString();
                    add(new JLabel(cat + values.get(c.ordinal())),
                        "newline 5, span, align center");
                }
                for (int a = Player.ColonyValueCategory.A_GOODS.ordinal();
                     a < values.size(); a++) {
                    add(new JLabel("... " + values.get(a)),
                        "newline 5, span, align center");
                }
                add(new JLabel("Result " + result), "newline 5, span, align center");
            }
        }

        add(okButton, "newline 30, span, split 2, align center, tag ok");
        add(colopediaButton, "tag help");

        setSize(getPreferredSize());

        g.dispose();
    }


    // Interface ActionListener

    /**
     * {@inheritDoc}
     */
    public void actionPerformed(ActionEvent event) {
        final String command = event.getActionCommand();
        if (OK.equals(command)) {
            getGUI().removeFromCanvas(this);
        } else {
            getGUI().showColopediaPanel(command);
        }
    }


    // Override JPanel

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUIClassID() {
        return "TilePanelUI";
    }
}
