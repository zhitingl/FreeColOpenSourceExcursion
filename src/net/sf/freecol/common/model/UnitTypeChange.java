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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.io.FreeColXMLWriter;


/**
 * The possible changes of a unit type.
 */
public class UnitTypeChange extends FreeColObject {

    public static enum ChangeType {
        EDUCATION,
        NATIVES,
        EXPERIENCE,
        LOST_CITY,
        PROMOTION,
        CREATION,
        ENTER_COLONY,
        INDEPENDENCE,
        CLEAR_SKILL,
        DEMOTION,
        CAPTURE,
        CONVERSION,
        UNDEAD
    }

    public static final Map<ChangeType, String> tags
        = new EnumMap<ChangeType, String>(ChangeType.class);
    static {
        tags.put(ChangeType.EDUCATION, "learnInSchool");
        tags.put(ChangeType.NATIVES, "learnFromNatives");
        tags.put(ChangeType.EXPERIENCE, "learnFromExperience");
        tags.put(ChangeType.LOST_CITY, "learnInLostCity");
        tags.put(ChangeType.PROMOTION, "promotion");
        tags.put(ChangeType.CLEAR_SKILL, "clearSkill");
        tags.put(ChangeType.DEMOTION, "demotion");
        tags.put(ChangeType.CAPTURE, "capture");
        tags.put(ChangeType.CREATION, "creation");
        tags.put(ChangeType.ENTER_COLONY, "enterColony");
        tags.put(ChangeType.INDEPENDENCE, "independence");
        tags.put(ChangeType.CONVERSION, "conversion");
        tags.put(ChangeType.UNDEAD, "undead");
    }

    /** The new unit type to change to. */
    private UnitType newUnitType = null;

    /** The number of turns the changes takes, if applicable. */
    protected int turnsToLearn = 0;

    /** A map of change type to probability. */
    protected Map<ChangeType, Integer> changeTypes
        = new EnumMap<ChangeType, Integer>(ChangeType.class);

    /** A list of Scopes limiting the applicability of this Feature. */
    private List<Scope> scopes = null;


    /**
     * Deliberately empty constructor.
     */
    public UnitTypeChange() {}

    /**
     * Creates a new <code>UnitTypeChange</code> instance.
     *
     * @param xr An <code>FreeColXMLReader</code> to read from.
     * @param specification The <code>Specification</code> to refer to.
     * @exception XMLStreamException if an error occurs
     */
    public UnitTypeChange(FreeColXMLReader xr,
                          Specification specification) throws XMLStreamException {
        setSpecification(specification);
        readFromXML(xr);
    }


    /**
     * Gets the unit type to change to.
     *
     * @return The new <code>UnitType</code>.
     */
    public final UnitType getNewUnitType() {
        return newUnitType;
    }

    /**
     * Sets the new unit type to change to.
     * Public for the test suite.
     *
     * @param newUnitType The new <code>UnitType</code>.
     */
    public final void setNewUnitType(final UnitType newUnitType) {
        this.newUnitType = newUnitType;
    }

    /**
     * Gets the turns to learn the skill.
     *
     * @return The turns to learn.
     */
    public final int getTurnsToLearn() {
        return turnsToLearn;
    }

    /**
     * Sets the turns to learn.
     *
     * @param newTurnsToLearn The new turns to learn.
     */
    public final void setTurnsToLearn(final int newTurnsToLearn) {
        this.turnsToLearn = newTurnsToLearn;
    }

    /**
     * Gets the change type probability map.
     *
     * @return The change type map.
     */
    public Map<ChangeType, Integer> getChangeTypes() {
        return changeTypes;
    }

    /**
     * Gets the probability of a change taking place.
     * At the moment, this probability only applies to the
     * ChangeTypes EXPERIENCE and PROMOTION.
     *
     * @param type The <code>ChangeType</code> to check.
     * @return The probability, defaulting to zero.
     */
    public final int getProbability(ChangeType type) {
        Integer result = changeTypes.get(type);
        return (result == null) ? 0 : result;
    }

    /**
     * Is this unit change type possible as a specific change type.
     *
     * @param type The <code>ChangeType</code> to check.
     * @return True if the change type can occur.
     */
    public boolean asResultOf(ChangeType type) {
        return changeTypes.containsKey(type)
            && changeTypes.get(type) > 0;
    }

    /**
     * Can this unit type change occur as a result of education?
     *
     * @return True if this is a valid educational change.
     */
    public boolean canBeTaught() {
        return asResultOf(ChangeType.EDUCATION) && turnsToLearn > 0;
    }

    /**
     * Gets the scopes associated with this type change.
     *
     * @return The list of scopes.
     */
    public List<Scope> getScopes() {
        return (scopes == null) ? Collections.<Scope>emptyList()
            : scopes;
    }

    /**
     * Sets the scopes associated with this type change.
     * Public for the test suite.
     *
     * @param scopes The new list of <code>Scope</code>s.
     */
    public void setScopes(List<Scope> scopes) {
        this.scopes = scopes;
    }

    /**
     * Add a scope.
     *
     * @param scope The <code>Scope</code> to add.
     */
    private void addScope(Scope scope) {
        if (scopes == null) {
            scopes = new ArrayList<Scope>();
        }
        scopes.add(scope);
    }

    /**
     * Does this change type apply to a given player?
     *
     * @param player The <code>Player</code> to test.
     * @return True if this change is applicable.
     */
    public boolean appliesTo(Player player) {
        List<Scope> scopeList = getScopes();
        if (scopeList.isEmpty()) return true;
        for (Scope scope : scopeList) if (scope.appliesTo(player)) return true;
        return false;
    }


    // Serialization

    private static final String TURNS_TO_LEARN_TAG = "turnsToLearn";
    private static final String UNIT_TAG = "unit";


    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeAttributes(FreeColXMLWriter xw) throws XMLStreamException {
        // UnitTypeChange do not have ids, no super.writeAttributes().
        // However, they might in future.

        if (newUnitType != null) {
            xw.writeAttribute(UNIT_TAG, newUnitType);
        }

        if (turnsToLearn != UNDEFINED) {
            xw.writeAttribute(TURNS_TO_LEARN_TAG, turnsToLearn);
        }

        for (Map.Entry<ChangeType, Integer> entry : changeTypes.entrySet()) {
            xw.writeAttribute(tags.get(entry.getKey()),
                                       entry.getValue().toString());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeChildren(FreeColXMLWriter xw) throws XMLStreamException {
        super.writeChildren(xw);

        for (Scope scope : getScopes()) scope.toXML(xw);
    }

    /**
     * {@inheritDoc}
     */
    protected void readAttributes(FreeColXMLReader xr) throws XMLStreamException {
        // UnitTypeChange do not have ids, no super.readAttributes().
        // However, they might in future.

        final Specification spec = getSpecification();

        if (xr.hasAttribute(UNIT_TAG)) {
            newUnitType = xr.getType(spec, UNIT_TAG,
                                     UnitType.class, (UnitType)null);

            turnsToLearn = xr.getAttribute(TURNS_TO_LEARN_TAG, UNDEFINED);
            if (turnsToLearn > 0) {
                changeTypes.put(ChangeType.EDUCATION, 100);
            }

            for (ChangeType type : ChangeType.values()) {
                int value = xr.getAttribute(tags.get(type), -1);
                if (value >= 0) {
                    changeTypes.put(type, Math.min(100, value));
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readChildren(FreeColXMLReader xr) throws XMLStreamException {
        // Clear containers.
        scopes = null;

        super.readChildren(xr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readChild(FreeColXMLReader xr) throws XMLStreamException {
        final String tag = xr.getLocalName();

        if (Scope.getXMLElementTagName().equals(tag)) {
            addScope(new Scope(xr));

        } else {
            super.readChild(xr);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(32);
        sb.append("[").append(newUnitType)
            .append(" ").append(Integer.toString(turnsToLearn));
        for (Map.Entry<ChangeType, Integer> entry : changeTypes.entrySet()) {
            sb.append(" ").append(tags.get(entry.getKey()))
                .append("/").append(entry.getValue());
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * {@inheritDoc}
     */
    public String getXMLTagName() { return getXMLElementTagName(); }

    /**
     * Gets the tag name of the root element representing this object.
     *
     * @return "upgrade".
     */
    public static final String getXMLElementTagName() {
        return "upgrade";
    }
}
