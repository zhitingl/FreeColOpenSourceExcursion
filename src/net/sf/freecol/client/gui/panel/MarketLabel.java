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
import java.util.logging.Logger;

import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.client.gui.i18n.Messages;
import net.sf.freecol.common.debug.FreeColDebugger;
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.GoodsContainer;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.Player;


/**
 * This label represents a cargo type on the European market.
 */
public final class MarketLabel extends AbstractGoodsLabel
    implements Draggable {

    private static Logger logger = Logger.getLogger(MarketLabel.class.getName());

    /** The enclosing market. */
    private final Market market;


    /**
     * Initializes this JLabel with the given goods type.
     *
     * @param type The <code>GoodsType</code> to represent.
     * @param market The <code>Market</code> in which to trade the goods.
     * @param gui The <code>GUI</code> to display on.
     */
    public MarketLabel(GoodsType type, Market market, GUI gui) {
        super(new AbstractGoods(type, GoodsContainer.CARGO_SIZE), gui);

        if (market == null) throw new IllegalArgumentException("Null market");
        this.market = market;
    }


    /**
     * Get this MarketLabel's market.
     *
     * @return The enclosing <code>Market</code>.
     */
    public Market getMarket() {
        return market;
    }

    /**
     * Sets this MarketLabel's goods amount.
     *
     * @param amount The amount of goods.
     */
    public void setAmount(int amount) {
        getGoods().setAmount(amount);
    }

    /**
     * Sets the amount of the goods wrapped by this Label to
     * GoodsContainer.CARGO_SIZE.
     */
    public void setDefaultAmount() {
        setAmount(GoodsContainer.CARGO_SIZE);
    }

    /**
     * Is this label on a carrier?  No, it is in a market!
     *
     * @return False.
     */
    public boolean isOnCarrier() {
        return false;
    }

    /**
     * Paint this MarketLabel.
     *
     * @param g The graphics context in which to do the painting.
     */
    @Override
    public void paintComponent(Graphics g) {
        Player player = market.getOwner();
        GoodsType type = getType();
        String toolTipText = Messages.message(type.getNameKey());
        if (player == null || player.canTrade(type)) {
            setEnabled(true);
        } else {
            toolTipText = Messages.message(type.getLabel());
            setEnabled(false);
        }
        if (FreeColDebugger.isInDebugMode(FreeColDebugger.DebugMode.MENUS)) {
            toolTipText += " " + market.getAmountInMarket(type);
        }
        setToolTipText(toolTipText);

        super.setText(market.getPaidForSale(type)
                      + "/" + market.getCostToBuy(type));
        super.paintComponent(g);
    }
}
