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

import java.lang.reflect.Method;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.io.FreeColXMLWriter;
import net.sf.freecol.common.util.Utils;


/**
 * The <code>Scope</code> class determines whether a given
 * <code>FreeColGameObjectType</code> fulfills certain requirements.
 */
public class Scope extends FreeColObject {

    /** 
     * The identifier of a <code>FreeColGameObjectType</code>, or
     * <code>Option</code>.
     */
    private String type = null;

    /** The object identifier of an <code>Ability</code>. */
    private String abilityId = null;

    /** The value of an <code>Ability</code>. */
    private boolean abilityValue = true;

    /** The name of an <code>Method</code>. */
    private String methodName = null;

    /**
     * The <code>String</code> representation of the value of an
     * <code>Method</code>.
     */
    private String methodValue = null;

    /** True if the scope applies to a null object. */
    private boolean matchesNull = true;

    /** Whether the match is negated. */
    private boolean matchNegated = false;


    /**
     * Deliberately empty constructor.
     */
    public Scope() {}

    /**
     * Creates a new <code>Scope</code> instance from a stream.
     *
     * @param xr The <code>FreeColXMLReader</code> to read from.
     * @exception XMLStreamException if there is an error reading the stream.
     */
    public Scope(FreeColXMLReader xr) throws XMLStreamException {
        readFromXML(xr);
    }


    /**
     * Does this scope match null?
     *
     * @return True if this scope matches null.
     */
    public boolean isMatchesNull() {
        return matchesNull;
    }

    /**
     * Set the null match value.
     *
     * @param newMatchesNull The new null match value.
     */
    public void setMatchesNull(final boolean newMatchesNull) {
        this.matchesNull = newMatchesNull;
    }

    /**
     * Is the match negated for this scope?
     *
     * @return True if this match is negated.
     */
    public boolean isMatchNegated() {
        return matchNegated;
    }

    /**
     * Set the match negated value.
     *
     * @param newMatchNegated The new match negated value.
     */
    public void setMatchNegated(final boolean newMatchNegated) {
        this.matchNegated = newMatchNegated;
    }

    /**
     * Gets the type of scope.
     *
     * @return The scope type.
     */
    public String getType() {
        return type;
    }

    /**
     * Set the scope type.
     *
     * @param newType The new scope type.
     */
    public void setType(final String newType) {
        this.type = newType;
    }

    /**
     * Gets the ability identifier.
     *
     * @return The ability id.
     */
    public String getAbilityId() {
        return abilityId;
    }

    /**
     * Sets the ability identifier.
     *
     * @param newAbilityId The new ability id.
     */
    public void setAbilityId(final String newAbilityId) {
        this.abilityId = newAbilityId;
    }

    /**
     * Gets the ability value.
     *
     * @return The ability value.
     */
    public boolean getAbilityValue() {
        return abilityValue;
    }

    /**
     * Sets the ability value.
     *
     * @param newAbilityValue The new ability value.
     */
    public void setAbilityValue(final boolean newAbilityValue) {
        this.abilityValue = newAbilityValue;
    }

    /**
     * Gets the method name.
     *
     * @return The method name.
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * Sets the method name.
     *
     * @param newMethodName The new method name.
     */
    public void setMethodName(final String newMethodName) {
        this.methodName = newMethodName;
    }

    /**
     * Gets the method value.
     *
     * @return The method value.
     */
    public String getMethodValue() {
        return methodValue;
    }

    /**
     * Sets the method value.
     *
     * @param newMethodValue The new method value.
     */
    public void setMethodValue(final String newMethodValue) {
        this.methodValue = newMethodValue;
    }

    /**
     * Does this scope apply to a given object?
     *
     * @param object The <code>FreeColGameObjectType</code> to test.
     * @return True if the scope is applicable.
     */
    public boolean appliesTo(FreeColObject object) {
        if (object == null) {
            return matchesNull;
        }
        if (type != null) {
            if (object instanceof FreeColGameObjectType) {
                if (!type.equals(object.getId())) {
                    return matchNegated;
                }
            } else if (object instanceof FreeColGameObject) {
                try {
                    Method method = object.getClass().getMethod("getType");
                    if (method != null
                        && FreeColGameObjectType.class.isAssignableFrom(method.getReturnType())) {
                        FreeColGameObjectType objectType =
                            (FreeColGameObjectType) method.invoke(object);
                        if (!type.equals(objectType.getId())) {
                            return matchNegated;
                        }
                    } else {
                        return matchNegated;
                    }
                } catch(Exception e) {
                    return matchNegated;
                }
            } else {
                return matchNegated;
            }
        }
        if (abilityId != null && object.hasAbility(abilityId) != abilityValue) {
            return matchNegated;
        }
        if (methodName != null) {
            try {
                Method method = object.getClass().getMethod(methodName);
                if (method != null
                    && !String.valueOf(method.invoke(object)).equals(methodValue)) {
                    return matchNegated;
                }
            } catch(Exception e) {
                return matchNegated;
            }
        }
        return !matchNegated;
    }


    // @compat 0.10.7
    /**
     * Helper for scope fixups.
     *
     * @return A new scope to negatively match on persons.
     */
    public static Scope makeNegatedPersonScope() {
        Scope scope = new Scope();
        scope.setAbilityId("model.ability.person");
        scope.setMatchNegated(true);
        return scope;
    }
    // end @compat 0.10.7


    // Override Object

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o instanceof Scope) {
            Scope otherScope = (Scope) o;
            if (matchNegated != otherScope.matchNegated) {
                return false;
            }
            if (matchesNull != otherScope.matchesNull) {
                return false;
            }
            if (type == null) {
                if (otherScope.getType() != type) {
                    return false;
                }
            } else if (!type.equals(otherScope.getType())) {
                return false;
            }
            if (abilityId == null) {
                if (!Utils.equals(otherScope.getAbilityId(), abilityId)) {
                    return false;
                }
            } else if (!abilityId.equals(otherScope.getAbilityId())) {
                return false;
            }
            if (abilityValue != otherScope.getAbilityValue()) {
                return false;
            }
            if (methodName == null) {
                if (!Utils.equals(otherScope.getMethodName(), methodName)) {
                    return false;
                }
            } else if (!methodName.equals(otherScope.getMethodName())) {
                return false;
            }
            if (methodValue == null) {
                if (!Utils.equals(otherScope.getMethodValue(), methodValue)) {
                    return false;
                }
            } else if (!methodValue.equals(otherScope.getMethodValue())) {
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = 31 * hash + (type == null ? 0 : type.hashCode());
        hash = 31 * hash + (abilityId == null ? 0 : abilityId.hashCode());
        hash = 31 * hash + (abilityValue ? 1 : 0);
        hash = 31 * hash + (methodName == null ? 0 : methodName.hashCode());
        hash = 31 * hash + (methodValue == null ? 0 : methodValue.hashCode());
        hash = 31 * hash + (matchesNull ? 1 : 0);
        return 31 * hash + (matchNegated ? 1 : 0);
    }


    // Serialization

    private static final String ABILITY_ID_TAG = "ability-id";
    private static final String ABILITY_VALUE_TAG = "ability-value";
    private static final String MATCH_NEGATED_TAG = "matchNegated";
    private static final String MATCHES_NULL_TAG = "matchesNull";
    private static final String METHOD_NAME_TAG = "method-name";
    private static final String METHOD_VALUE_TAG = "method-value";
    private static final String TYPE_TAG = "type";


    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeAttributes(FreeColXMLWriter xw) throws XMLStreamException {
        // Scopes do not have ids, no super.writeAttributes().
        // However, they might in future.

        xw.writeAttribute(MATCH_NEGATED_TAG, matchNegated);

        xw.writeAttribute(MATCHES_NULL_TAG, matchesNull);

        if (type != null) {
            xw.writeAttribute(TYPE_TAG, type);
        }

        if (abilityId != null) {
            xw.writeAttribute(ABILITY_ID_TAG, abilityId);

            xw.writeAttribute(ABILITY_VALUE_TAG, abilityValue);
        }

        if (methodName != null) {
            xw.writeAttribute(METHOD_NAME_TAG, methodName);

            if (methodValue != null) {
                // methodValue may be null in the Operand sub-class
                xw.writeAttribute(METHOD_VALUE_TAG, methodValue);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readAttributes(FreeColXMLReader xr) throws XMLStreamException {
        // Scopes do not have ids, no super.readAttributes().
        // However, they might in future.

        matchNegated = xr.getAttribute(MATCH_NEGATED_TAG, false);

        matchesNull = xr.getAttribute(MATCHES_NULL_TAG, true);

        type = xr.getAttribute(TYPE_TAG, (String)null);
        // @compat 0.10.x
        if ("model.equipment.muskets".equals(type)) {
            type = "model.role.soldier";
        } else if ("model.equipment.indian.horses".equals(type)) {
            type = "model.role.mountedBrave";
        } else if ("model.equipment.indian.muskets".equals(type)) {
            type = "model.role.armedBrave";
        }
        // end @compat 0.10.x

        abilityId = xr.getAttribute(ABILITY_ID_TAG, (String)null);

        abilityValue = xr.getAttribute(ABILITY_VALUE_TAG, true);

        methodName = xr.getAttribute(METHOD_NAME_TAG, (String)null);

        methodValue = xr.getAttribute(METHOD_VALUE_TAG, (String)null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(64);
        sb.append("[Scope ").append(type);
        if (abilityId != null) {
            sb.append(" ").append(abilityId).append("=").append(abilityValue);
        }
        if (methodName != null) {
            sb.append(" ").append(methodName).append("=").append(methodValue);
        }
        if (matchesNull) sb.append(" matchesNull");
        if (matchNegated) sb.append(" matchNegated");
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
     * @return "scope".
     */
    public static String getXMLElementTagName() {
        return "scope";
    }
}
