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

package net.sf.freecol.client.gui.option;

import java.awt.Component;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Locale;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

import net.miginfocom.swing.MigLayout;
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.client.gui.i18n.Messages;
import net.sf.freecol.client.gui.panel.MigPanel;
import net.sf.freecol.client.gui.plaf.FreeColComboBoxRenderer;
import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.AbstractUnit;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.option.AbstractUnitOption;
import net.sf.freecol.common.option.IntegerOption;
import net.sf.freecol.common.option.StringOption;
import net.sf.freecol.common.option.UnitTypeOption;


/**
 * This class provides visualization for an
 * {@link net.sf.freecol.common.option.AbstractUnitOption} in order to enable
 * values to be both seen and changed.
 */
public final class AbstractUnitOptionUI extends OptionUI<AbstractUnitOption>
    implements ItemListener {

    private JPanel panel;
    private IntegerOptionUI numberUI;
    private UnitTypeOptionUI typeUI;
    private StringOptionUI roleUI;

    private boolean roleEditable;

    /**
     * Creates a new <code>AbstractUnitOptionUI</code> for the given
     * <code>AbstractUnitOption</code>.
     *
     * @param option The <code>AbstractUnitOption</code> to make a
     *     user interface for
     * @param editable boolean whether user can modify the setting
     */
    @SuppressWarnings("unchecked") // FIXME in Java7
    public AbstractUnitOptionUI(GUI gui, final AbstractUnitOption option,
                                boolean editable) {
        super(gui, option, editable);

        panel = new MigPanel();
        panel.setLayout(new MigLayout());

        IntegerOption numberOption = option.getNumber();
        UnitTypeOption typeOption = option.getUnitType();
        StringOption roleOption = option.getRole();

        boolean numberEditable = editable
            && (numberOption.getMaximumValue() > numberOption.getMinimumValue());
        numberUI = new IntegerOptionUI(gui, numberOption, numberEditable);
        numberUI.getComponent().setToolTipText(Messages.message("report.numberOfUnits"));
        panel.add(numberUI.getComponent(), "width 30%");

        boolean typeEditable = editable
            && typeOption.getChoices().size() > 1;
        typeUI = new UnitTypeOptionUI(gui, typeOption, typeEditable);

        typeUI.getComponent().setToolTipText(Messages.message("model.unit.type"));
        typeUI.getComponent().addItemListener(this);
        panel.add(typeUI.getComponent(), "width 35%");

        roleEditable = editable
            && roleOption.getChoices().size() > 1;
        roleUI = new StringOptionUI(gui,roleOption, roleEditable);
        roleUI.getComponent().setToolTipText(Messages.message("model.unit.role.name"));
        roleUI.getComponent().setRenderer(new RoleRenderer());
        panel.add(roleUI.getComponent(), "width 35%");

        initialize();

    }

    /**
     * {@inheritDoc}
     */
    public JPanel getComponent() {
        return panel;
    }

    /**
     * Updates the value of the {@link net.sf.freecol.common.option.Option}
     * this object keeps.
     */
    public void updateOption() {
        typeUI.updateOption();
        roleUI.updateOption();
        numberUI.updateOption();
        UnitType type = typeUI.getOption().getValue();
        String roleId = roleUI.getOption().getValue();
        int number = numberUI.getOption().getValue();
        getOption().setValue(new AbstractUnit(type, roleId, number));
    }

    /**
     * Reset with the value from the option.
     */
    public void reset() {
        typeUI.reset();
        roleUI.reset();
        numberUI.reset();
    }

    @SuppressWarnings("unchecked") // FIXME in Java7
    public void itemStateChanged(ItemEvent e) {
        JComboBox box = roleUI.getComponent();
        UnitType type = (UnitType) typeUI.getComponent().getSelectedItem();
        if (type.hasAbility(Ability.CAN_BE_EQUIPPED)) {
            box.setModel(new DefaultComboBoxModel(roleUI.getOption().getChoices().toArray(new String[0])));
            box.setEnabled(roleEditable);
        } else {
            box.setModel(new DefaultComboBoxModel(new String[] {
                        Specification.DEFAULT_ROLE_ID }));
            box.setEnabled(false);
        }
    }


    private static class RoleRenderer extends FreeColComboBoxRenderer {

        @Override
        public void setLabelValues(JLabel label, Object value) {
            label.setText(Messages.message(((String) value) + ".name"));
        }
    }

    public ListCellRenderer getListCellRenderer() {
        return new AbstractUnitRenderer();
    }

    private class AbstractUnitRenderer extends FreeColComboBoxRenderer {

        @Override
        public void setLabelValues(JLabel label, Object value) {
            final Specification spec = getOption().getSpecification();
            AbstractUnit au = (AbstractUnit)((AbstractUnitOption)value)
                .getValue();
            String key = au.getId();
            if (au.getType(spec).hasAbility(Ability.CAN_BE_EQUIPPED)
                && !Specification.DEFAULT_ROLE_ID.equals(au.getRoleId())) {
                key = au.getRoleId();
            }
            StringTemplate template = StringTemplate.template(key + ".name")
                .addAmount("%number%", au.getNumber())
                .add("%unit%", au.getId() + ".name");
            label.setText(Integer.toString(au.getNumber()) + " "
                          + Messages.message(template));
        }
    }
}
