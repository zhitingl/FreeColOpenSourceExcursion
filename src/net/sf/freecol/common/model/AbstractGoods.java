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

package net.sf.freecol.common.model;

import java.util.Collection;
import java.util.Comparator;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.io.FreeColXMLWriter;
import net.sf.freecol.common.util.Utils;


/**
 * Represents a certain amount of a GoodsType.  This does not
 * correspond to actual cargo present in a Location, but is intended
 * to represent things such as the amount of Lumber necessary to build
 * something, or the amount of cargo to load at a certain Location.
 */
public class AbstractGoods extends FreeColObject implements Named {

    /** A comparator to sort by descending goods amount. */
    public static final Comparator<AbstractGoods> goodsAmountComparator
        = new Comparator<AbstractGoods>() {
            public int compare(AbstractGoods o, AbstractGoods p) {
                return p.getAmount() - o.getAmount();
            }
        };

    /** The type of goods. */
    private GoodsType type;

    /** The amount of goods. */
    private int amount;


    /**
     * Empty constructor.
     */
    public AbstractGoods() {}

    /**
     * Creates a new <code>AbstractGoods</code> instance.
     *
     * @param type The <code>GoodsType</code> to create.
     * @param amount The amount of goods to create.
     */
    public AbstractGoods(GoodsType type, int amount) {
        setId(type.getId());
        this.type = type;
        this.amount = amount;
    }

    /**
     * Creates a new <code>AbstractGoods</code> instance.
     *
     * @param other Another <code>AbstractGoods</code> to copy.
     */
    public AbstractGoods(AbstractGoods other) {
        setId(other.type.getId());
        this.type = other.type;
        this.amount = other.amount;
    }


    /**
     * Get the goods type.
     *
     * @return The <code>GoodsType</code>.
     */
    public final GoodsType getType() {
        return type;
    }

    /**
     * Set the goods type.
     *
     * @param newType The new <code>GoodsType</code>.
     */
    public final void setType(final GoodsType newType) {
        this.type = newType;
    }

    /**
     * Get the goods amount.
     *
     * @return The goods amount.
     */
    public final int getAmount() {
        return amount;
    }

    /**
     * Set the goods amount.
     *
     * @param newAmount The new goods amount.
     */
    public final void setAmount(final int newAmount) {
        this.amount = newAmount;
    }


    /**
     * Convenience lookup of the member of a collection of abstract goods that
     * matches a given goods type.
     *
     * @param type The <code>GoodsType</code> to look for.
     * @param goods The collection of <code>AbstractGoods</code> to look in.
     * @return The <code>AbstractGoods</code> found, or null if not.
     */
    public static AbstractGoods findByType(GoodsType type,
                                           Collection<AbstractGoods> goods) {
        for (AbstractGoods ag : goods) {
            if (ag.getType() == type) return ag;
        }
        return null;
    }

    /**
     * Convenience lookup of the goods count in a collection of
     * abstract goods given a goods type.
     * 
     * @param type The <code>GoodsType</code> to look for.
     * @param goods The collection of <code>AbstractGoods</code> to look in.
     * @return The goods count found, or zero if not found.
     */
    public static int getCount(GoodsType type,
                               Collection<AbstractGoods> goods) {
        AbstractGoods ag = findByType(type, goods);
        return (ag == null) ? 0 : ag.getAmount();
    }


    // Interface Named

    /**
     * {@inheritDoc}
     */
    public String getNameKey() {
        return getType().getNameKey();
    }


    // Override Object

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof AbstractGoods) {
            AbstractGoods ag = (AbstractGoods)o;
            return type == ag.type && amount == ag.amount;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return 31 * Utils.hashCode(type) + amount;
    }


    // Serialization

    private static final String AMOUNT_TAG = "amount";
    private static final String TYPE_TAG = "type";


    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeAttributes(FreeColXMLWriter xw) throws XMLStreamException {
        super.writeAttributes(xw);

        xw.writeAttribute(TYPE_TAG, type);

        xw.writeAttribute(AMOUNT_TAG, amount);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readAttributes(FreeColXMLReader xr) throws XMLStreamException {
        final Specification spec = getSpecification();

        super.readAttributes(xr);

        type = xr.getType(spec, TYPE_TAG, GoodsType.class, (GoodsType)null);
        if (type == null) {
            throw new XMLStreamException("Null goods type.");
        } else {
            setId(type.getId());
        }

        amount = xr.getAttribute(AMOUNT_TAG, 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return AbstractGoods.toString(this);
    }

    /**
     * Simple string version of some goods.
     *
     * @param ag The <code>AbstractGoods</code> to make a string from.
     * @return A string version of the goods.
     */     
    public static String toString(AbstractGoods ag) {
        return toString(ag.getType(), ag.getAmount());
    }

    /**
     * Simple string version of the component parts of some goods.
     *
     * @param goodsType The <code>GoodsType</code> to use.
     * @param amount The amount of goods.
     * @return A string version of the goods.
     */     
    public static String toString(GoodsType goodsType, int amount) {
        return amount + " " + goodsType.getId();
    }

    /**
     * {@inheritDoc}
     */
    public String getXMLTagName() { return getXMLElementTagName(); }

    /**
     * Gets the tag name of the root element representing this object.
     *
     * @return "abstractGoods".
     */
    public static String getXMLElementTagName() {
        return "abstractGoods";
    }
}
