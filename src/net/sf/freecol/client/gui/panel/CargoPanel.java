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

import java.awt.Component;
import java.awt.Container;
import java.awt.LayoutManager;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Iterator;
import java.util.logging.Logger;

import javax.swing.border.TitledBorder;

import net.miginfocom.swing.MigLayout;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.client.gui.i18n.Messages;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Unit;


/**
 * A panel that holds units and goods that represent Units and cargo
 * that are on board the currently selected ship.
 */
public class CargoPanel extends FreeColPanel
    implements DropTarget, PropertyChangeListener {

    private static Logger logger = Logger.getLogger(CargoPanel.class.getName());

    /** The carrier that contains cargo. */
    private Unit carrier;

    private DefaultTransferHandler defaultTransferHandler = null;

    private MouseListener pressListener = null;

    private final TitledBorder border;


    /**
     * Creates this CargoPanel.
     *
     * @param freeColClient The <code>FreeColClient</code> for the game.
     * @param withTitle Should the panel have a title?
     */
    public CargoPanel(FreeColClient freeColClient, boolean withTitle) {
        super(freeColClient, new MigLayout("wrap 6, fill, insets 0"));

        this.border = (withTitle) ? GUI.setTitledBorder(this, "cargoOnCarrier")
            : null;

        this.carrier = null;

        this.defaultTransferHandler
            = new DefaultTransferHandler(getFreeColClient(), this);

        this.pressListener = new DragListener(getFreeColClient(), this);
    }


    /**
     * Initialize this CargoPanel.
     */
    public void initialize() {
        addPropertyChangeListeners();
        update();
    }

    /**
     * Clean up this CargoPanel.
     */
    public void cleanup() {
        removePropertyChangeListeners();
    }

    protected void addPropertyChangeListeners() {
        if (carrier != null) {
            carrier.addPropertyChangeListener(Unit.CARGO_CHANGE, this);
            carrier.getGoodsContainer().addPropertyChangeListener(this);
        }
    }

    protected void removePropertyChangeListeners() {
        if (carrier != null) {
            carrier.removePropertyChangeListener(Unit.CARGO_CHANGE, this);
            carrier.getGoodsContainer().removePropertyChangeListener(this);
        }
    }

    /**
     * Update this CargoPanel.
     */
    public void update() {
        removeAll();

        if (carrier != null) {

            Iterator<Unit> unitIterator = carrier.getUnitIterator();
            while (unitIterator.hasNext()) {
                Unit unit = unitIterator.next();

                UnitLabel label = new UnitLabel(getFreeColClient(), unit);
                if (isEditable()) {
                    label.setTransferHandler(defaultTransferHandler);
                    label.addMouseListener(pressListener);
                }
                add(label);
            }

            Iterator<Goods> goodsIterator = carrier.getGoodsIterator();
            while (goodsIterator.hasNext()) {
                Goods g = goodsIterator.next();

                GoodsLabel label = new GoodsLabel(g, getGUI());
                if (isEditable()) {
                    label.setTransferHandler(defaultTransferHandler);
                    label.addMouseListener(pressListener);
                }
                add(label);
            }
        }
        updateTitle();
        revalidate();
        repaint();
    }


    /**
     * Whether this panel is active.
     *
     * @return boolean <b>true</b> == active
     */
    public boolean isActive() {
        return carrier != null;
    }

    /**
     * Get the carrier unit.
     *
     * @return The carrier <code>Unit</code>.
     */
    public Unit getCarrier() {
        return carrier;
    }

    /**
     * Set the carrier unit.
     *
     * @param newCarrier The new carrier <code>Unit</code>.
     */
    public void setCarrier(final Unit newCarrier) {
        if (newCarrier != carrier) {
            cleanup();
            this.carrier = newCarrier;
            initialize();
        }
    }

    /**
     * Update the title of this CargoPanel.
     */
    private void updateTitle() {
        if (border == null) return;

        if (carrier == null) {
            border.setTitle(Messages.message("cargoOnCarrier"));
        } else {
            StringTemplate t = StringTemplate.template("cargoOnCarrierLong")
                .addStringTemplate("%name%", carrier.getFullLabel(false))
                .addAmount("%space%", carrier.getSpaceLeft());
            border.setTitle(Messages.message(t));
        }
    }


    // Interface DropTarget

    /**
     * {@inheritDoc}
     */
    public boolean accepts(Unit unit) {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public boolean accepts(Goods goods) {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public Component add(Component comp, boolean editState) {
        if (carrier == null) return null;

        if (editState) {
            if (comp instanceof UnitLabel) {
                Unit unit = ((UnitLabel) comp).getUnit();
                if (carrier.canAdd(unit)) {
                    Container oldParent = comp.getParent();
                    if (getController().boardShip(unit, carrier)) {
                        ((UnitLabel) comp).setSmall(false);
                        if (oldParent != null) oldParent.remove(comp);
                        update();
                        return comp;
                    }
                }

            } else if (comp instanceof GoodsLabel) {
                Goods goods = ((GoodsLabel) comp).getGoods();
                int loadableAmount = carrier.getLoadableAmount(goods.getType());
                if (loadableAmount == 0) {
                    return null;
                } else if (loadableAmount > goods.getAmount()) {
                    loadableAmount = goods.getAmount();
                }
                Goods goodsToAdd = new Goods(goods.getGame(), goods.getLocation(),
                                             goods.getType(), loadableAmount);
                goods.setAmount(goods.getAmount() - loadableAmount);
                getController().loadCargo(goodsToAdd, carrier);
                update();
                return comp;

            } else if (comp instanceof MarketLabel) {
                MarketLabel label = (MarketLabel) comp;
                Player player = carrier.getOwner();
                if (player.canTrade(label.getType())) {
                    getController().buyGoods(label.getType(), 
                                             label.getAmount(), carrier);
                    getController().nextModelMessage();
                    update();
                    return comp;
                } else {
                    getController().payArrears(label.getType());
                    return null;
                }
            } else {
                return null;
            }
        } else {
            super.add(comp);
        }
        return null;
    }


    // Interface PropertyChangeListener

    public void propertyChange(PropertyChangeEvent event) {
        logger.finest("CargoPanel change " + event.getPropertyName()
                      + ": " + event.getOldValue()
                      + " -> " + event.getNewValue());
        update();
    }


    // Override JLabel

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUIClassID() {
        return "CargoPanelUI";
    }


    // Override Container

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(Component comp) {
        if (comp instanceof UnitLabel) {
            Unit unit = ((UnitLabel)comp).getUnit();
            getController().leaveShip(unit);
            update();
        } else if (comp instanceof GoodsLabel) {
            Goods g = ((GoodsLabel)comp).getGoods();
            getController().unloadCargo(g, false);
            update();
        }
    }


    // Override Component

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeNotify() {
        super.removeNotify();

        removeAll();
        removePropertyChangeListeners();

        defaultTransferHandler = null;
        pressListener = null;
    }
}
