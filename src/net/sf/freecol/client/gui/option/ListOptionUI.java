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

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import net.miginfocom.swing.MigLayout;
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.client.gui.i18n.Messages;
import net.sf.freecol.client.gui.panel.MigPanel;
import net.sf.freecol.common.option.AbstractOption;
import net.sf.freecol.common.option.ListOption;
import net.sf.freecol.common.option.Option;


/**
 * This class provides visualization for a list of
 * {@link net.sf.freecol.common.option.AbstractOption}s in order to enable
 * values to be both seen and changed.
 */
public final class ListOptionUI<T> extends OptionUI<ListOption<T>>
    implements ListSelectionListener {

    private static Logger logger = Logger.getLogger(ListOptionUI.class.getName());

    private JPanel panel = new MigPanel();
    private JList list;
    private DefaultListModel model;

    private JButton editButton = new JButton(Messages.message("list.edit"));
    private JButton addButton = new JButton(Messages.message("list.add"));
    private JButton removeButton = new JButton(Messages.message("list.remove"));
    private JButton upButton = new JButton(Messages.message("list.up"));
    private JButton downButton = new JButton(Messages.message("list.down"));


    /**
     * Creates a new <code>ListOptionUI</code> for the given
     * <code>ListOption</code>.
     *
     * @param gui The <code>GUI</code> to display on.
     * @param option The <code>ListOption</code> to display.
     * @param editable boolean whether user can modify the setting
     */
    @SuppressWarnings("unchecked") // FIXME in Java7
    public ListOptionUI(final GUI gui, final ListOption<T> option,
                        boolean editable) {
        super(gui, option, editable);

        panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.BLACK),
                                                         super.getLabel().getText()));
        panel.setLayout(new MigLayout("wrap 2, fill", "[fill, grow]20[fill]"));

        model = new DefaultListModel();
        for (AbstractOption<T> o : option.getValue()) {
            try {
                AbstractOption<T> c = o.clone();
                model.addElement(c);
            } catch (CloneNotSupportedException e) {
                logger.log(Level.WARNING, "Can not clone" + o.getId(), e);
            }
        }
        list = new JList(model);
        AbstractOption<T> o = option.getValue().isEmpty()
            ? option.getTemplate()
            : option.getValue().get(0);
        if (o != null) {
            OptionUI ui = OptionUI.getOptionUI(gui, o, editable);
            if (ui != null && ui.getListCellRenderer() != null) {
                list.setCellRenderer(ui.getListCellRenderer());
            }
            list.setSelectedIndex(0);
        }
        list.setVisibleRowCount(4);
        JScrollPane pane = new JScrollPane(list);
        panel.add(pane, "grow, spany 5");

        for (JButton button : new JButton[] {
                editButton, addButton, removeButton, upButton, downButton}) {
            button.setEnabled(editable);
            panel.add(button);
        }

        addButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                AbstractOption<T> oldValue
                    = (AbstractOption<T>)list.getSelectedValue();
                if (oldValue == null) oldValue = option.getTemplate();
                try {
                    AbstractOption<T> newValue = oldValue.clone();
                    if (gui.showEditOptionDialog(newValue)) {
                        if (option.canAdd(newValue)) {
                            model.addElement(newValue);
                            list.setSelectedValue(newValue, true);
                            list.repaint();
                        }
                    }
                } catch (CloneNotSupportedException e) {
                    logger.log(Level.WARNING, "Can not clone: " + oldValue, e);
                }
            }
        });
        editButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Object object = list.getSelectedValue();
                if (object != null) {
                    if (gui.showEditOptionDialog((Option) object)) {
                        list.repaint();
                    }
                }
            }
        });
        removeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                model.removeElementAt(list.getSelectedIndex());
            }
        });
        upButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (list.getSelectedIndex() == 0) return;
                final int index = list.getSelectedIndex();
                final Object temp = model.getElementAt(index);
                model.setElementAt(model.getElementAt(index-1), index);
                model.setElementAt(temp, index-1);
                list.setSelectedIndex(index-1);
            }
        });
        downButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (list.getSelectedIndex() == model.getSize() - 1) return;
                final int index = list.getSelectedIndex();
                final Object temp = model.getElementAt(index);
                model.setElementAt(model.getElementAt(index+1), index);
                model.setElementAt(temp, index+1);
                list.setSelectedIndex(index+1);
            }
        });

        list.addListSelectionListener(this);
        initialize();
    }


    /**
     * Returns <code>null</code>, since this OptionUI does not require
     * an external label.
     *
     * @return Null.
     */
    @Override
    public final JLabel getLabel() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public JPanel getComponent() {
        return panel;
    }

    /**
     * Updates the value of the
     * {@link net.sf.freecol.common.option.Option} this object keeps.
     */
    public void updateOption() {
        getOption().setValue(getValue());
    }

    @SuppressWarnings("unchecked")
    private List<AbstractOption<T>> getValue() {
        List<AbstractOption<T>> result = new ArrayList<AbstractOption<T>>();
        for (Enumeration e = model.elements(); e.hasMoreElements();) {
            result.add((AbstractOption<T>) e.nextElement());
        }
        return result;
    }

    /**
     * Reset with the value from the Option.
     */
    @SuppressWarnings("unchecked") // FIXME in Java7
    public void reset() {
        model.clear();
        for (AbstractOption<T> o : getOption().getValue()) {
            model.addElement(o);
        }
    }

    public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting() == false) {
            boolean enabled = (isEditable() && list.getSelectedValue() != null);
            editButton.setEnabled(enabled);
            removeButton.setEnabled(enabled);
            upButton.setEnabled(enabled);
            downButton.setEnabled(enabled);
        }
    }
}
