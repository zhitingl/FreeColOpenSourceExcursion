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

package net.sf.freecol.client.gui.action;

import java.awt.event.ActionEvent;
import java.util.Iterator;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsContainer;
import net.sf.freecol.common.model.Unit;


/**
 * An action for filling the holds of the currently selected unit.
 */
public class LoadAction extends MapboardAction {

    public static final String id = "loadAction";


    /**
     * Creates this action.
     *
     * @param freeColClient The <code>FreeColClient</code> for the game.
     */
    public LoadAction(FreeColClient freeColClient) {
        super(freeColClient, id);
    }


    /**
     * Checks if this action should be enabled.
     * 
     * @return True if there is an active carrier with space to load to.
     */
    @Override
    protected boolean shouldBeEnabled() {
        final Unit carrier = getGUI().getActiveUnit();
        return super.shouldBeEnabled()
            && carrier != null
            && carrier.isCarrier()
            && carrier.hasSpaceLeft();
    }    


    // Interface ActionListener

    /**
     * {@inheritDoc}
     */
    public void actionPerformed(ActionEvent e) {
        Unit unit = getGUI().getActiveUnit();
        if (unit != null) {
            Colony colony = unit.getColony();
            if (colony != null) {
                Iterator<Goods> goodsIterator = unit.getGoodsIterator();
                while (goodsIterator.hasNext()) {
                    Goods goods = goodsIterator.next();
                    if (goods.getAmount() < GoodsContainer.CARGO_SIZE
                        && colony.getGoodsCount(goods.getType()) > 0) {
                        int amount = Math.min(GoodsContainer.CARGO_SIZE - goods.getAmount(),
                            colony.getGoodsCount(goods.getType()));
                        Goods newGoods = new Goods(goods.getGame(), colony, goods.getType(), amount);
                        getInGameController().loadCargo(newGoods, unit);
                    }
                }
            }
        }
    }
}
