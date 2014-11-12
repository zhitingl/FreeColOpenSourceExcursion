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

import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ItemListener;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.client.gui.i18n.Messages;
import net.sf.freecol.client.gui.plaf.FreeColComboBoxRenderer;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.StringTemplate;

import net.miginfocom.swing.MigLayout;


/**
 * Centers the map on a known settlement or colony.  Pressing ENTER
 * opens a panel if appropriate.
 */
public final class FindSettlementPanel extends FreeColPanel
    implements ListSelectionListener, ItemListener {

    @SuppressWarnings("unused")
    private static final Logger logger = Logger.getLogger(FindSettlementPanel.class.getName());

    private final JComboBox displayOptionBox;

    private JList settlementList;

    private static enum DisplayListOption {
        ALL,
        ONLY_NATIVES,
        ONLY_EUROPEAN
    }


    /**
     * The constructor to use.
     */
    @SuppressWarnings("unchecked") // FIXME in Java7
    public FindSettlementPanel(FreeColClient freeColClient) {
        super(freeColClient, new MigLayout("wrap 1", "[align center]",
                                           "[]30[]30[]"));

        JLabel header = new JLabel(Messages.message("findSettlementPanel.name"));
        header.setFont(GUI.SMALL_HEADER_FONT);
        add(header);

        settlementList = new JList();
        updateSearch(DisplayListOption.valueOf("ALL"));
        settlementList.setCellRenderer(new SettlementRenderer());
        settlementList.setFixedCellHeight(48);
        JScrollPane listScroller = new JScrollPane(settlementList);
        listScroller.setPreferredSize(new Dimension(250, 250));
        settlementList.addListSelectionListener(this);

        Action selectAction = new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    selectSettlement();
                }
            };

        Action quitAction = new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    getGUI().removeFromCanvas(FindSettlementPanel.this);
                }
            };

        settlementList.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "select");
        settlementList.getActionMap().put("select", selectAction);
        settlementList.getInputMap().put(KeyStroke.getKeyStroke("ESCAPE"), "quit");
        settlementList.getActionMap().put("quit", quitAction);

        MouseListener mouseListener = new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        selectSettlement();
                    }
                }
            };
        settlementList.addMouseListener(mouseListener);

        add(listScroller, "width max(300, 100%), height max(300, 100%)");

        displayOptionBox = new JComboBox(new String[] {
                Messages.message("findSettlementPanel.displayAll"),
                Messages.message("findSettlementPanel.displayOnlyNatives"),
                Messages.message("findSettlementPanel.displayOnlyEuropean"),
            });
        displayOptionBox.addItemListener(this);
        add(displayOptionBox);

        add(okButton, "tag ok");

        getGUI().restoreSavedSize(this, getPreferredSize());
    }

    @SuppressWarnings("unchecked") // FIXME in Java7
    private void updateSearch(DisplayListOption displayListOption) {
        DefaultListModel model = new DefaultListModel();
        Object selected = settlementList.getSelectedValue();

        for (Player player : getGame().getLivePlayers(null)) {
            boolean ok;
            switch (displayListOption) {
            case ONLY_NATIVES:
                ok = player.isIndian();
                break;
            case ONLY_EUROPEAN:
                ok = player.isEuropean();
                break;
            case ALL:
                ok = true;
                break;
            default:
                ok = false;
                break;
            }
            if (ok) {
                for (Settlement s : player.getSettlements()) {
                    model.addElement(s);
                }
            }
        }

        settlementList.setModel(model);
        settlementList.setSelectedValue(selected, true);
        if (settlementList.getSelectedIndex() == -1) {
            settlementList.setSelectedIndex(0);
        }
    }

    private void selectSettlement() {
        Settlement settlement = (Settlement) settlementList.getSelectedValue();
        if (settlement instanceof Colony
            && settlement.getOwner() == getMyPlayer()) {
            getGUI().removeFromCanvas(FindSettlementPanel.this);
            getGUI().showColonyPanel((Colony)settlement, null);
        } else if (settlement instanceof IndianSettlement) {
            getGUI().removeFromCanvas(FindSettlementPanel.this);
            getGUI().showIndianSettlementPanel((IndianSettlement) settlement);
        }
    }

    /**
     * This function analyses an event and calls the right methods to take care
     * of the user's requests.
     *
     * @param e a <code>ListSelectionEvent</code> value
     */
    public void valueChanged(ListSelectionEvent e) {
        Settlement settlement = (Settlement) settlementList.getSelectedValue();
        getGUI().setFocus(settlement.getTile());
    }

    public void itemStateChanged(ItemEvent event) {
        switch(displayOptionBox.getSelectedIndex()) {
        case 0:
        default:
            updateSearch(DisplayListOption.valueOf("ALL"));
            break;
        case 1:
            updateSearch(DisplayListOption.valueOf("ONLY_NATIVES"));
            break;
        case 2:
            updateSearch(DisplayListOption.valueOf("ONLY_EUROPEAN"));
        }
    }

    private class SettlementRenderer extends FreeColComboBoxRenderer {

        @Override
        public void setLabelValues(JLabel label, Object value) {
            Settlement settlement = (Settlement) value;
            String messageId = settlement.isCapital()
                ? "indianCapitalOwner"
                : "indianSettlementOwner";
            label.setText(Messages.message(StringTemplate.template(messageId)
                                           .addName("%name%", settlement.getName())
                                           .addStringTemplate("%nation%", settlement.getOwner().getNationName())));
            label.setIcon(new ImageIcon(getLibrary().getSettlementImage(settlement)
                                        .getScaledInstance(64, -1, Image.SCALE_SMOOTH)));
        }
    }


    // Override Component

    /**
     * {@inheritDoc}
     */
    @Override
    public void requestFocus() {
        settlementList.requestFocus();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeNotify() {
        super.removeNotify();

        removeAll();
        settlementList = null;
    }
}
