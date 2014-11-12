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

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;

import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.client.gui.plaf.FreeColComboBoxRenderer;
import net.sf.freecol.common.option.LanguageOption;
import net.sf.freecol.common.option.LanguageOption.Language;


/**
 * This class provides visualization for a
 * {@link net.sf.freecol.common.option.LanguageOption} in order to enable
 * values to be both seen and changed.
 */
public final class LanguageOptionUI extends OptionUI<LanguageOption>  {

    private JComboBox box = new JComboBox();


    /**
     * Creates a new <code>LanguageOptionUI</code> for the given
     * <code>LanguageOption</code>.
     *
     * @param option The <code>LanguageOption</code> to make a user
     *     interface for.
     * @param editable boolean whether user can modify the setting
     */
    @SuppressWarnings("unchecked") // FIXME in Java7
    public LanguageOptionUI(GUI gui, final LanguageOption option,
                            boolean editable) {
        super(gui, option, editable);

        Language[] languages = option.getChoices().toArray(new Language[0]);
        box.setModel(new DefaultComboBoxModel(languages));
        box.setSelectedItem(option.getValue());
        box.setRenderer(new FreeColComboBoxRenderer("", false));

        initialize();
    }

    /**
     * {@inheritDoc}
     */
    public JComboBox getComponent() {
        return box;
    }

    /**
     * {@inheritDoc}
     */
    public void updateOption() {
        getOption().setValue((Language) box.getSelectedItem());
    }

    /**
     * {@inheritDoc}
     */
    public void reset() {
        box.setSelectedItem(getOption().getValue());
    }
}
