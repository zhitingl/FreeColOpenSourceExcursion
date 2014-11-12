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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map.Entry;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;

import net.miginfocom.swing.MigLayout;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.client.gui.i18n.Messages;
import net.sf.freecol.common.model.GameOptions;
import net.sf.freecol.common.model.Nation;
import net.sf.freecol.common.model.NationOptions;
import net.sf.freecol.common.model.NationOptions.NationState;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.option.FileOption;
import net.sf.freecol.common.option.MapGeneratorOptions;
import net.sf.freecol.common.option.OptionGroup;


/**
 * The panel where you choose your nation and color and connected players are
 * shown.
 */
public final class StartGamePanel extends FreeColPanel {

    private static final Logger logger = Logger.getLogger(StartGamePanel.class.getName());

    private static final int START = 0, CANCEL = 1,
        READY = 3, CHAT = 4, GAME_OPTIONS = 5, MAP_GENERATOR_OPTIONS = 6;

    private boolean singlePlayerGame;

    private JCheckBox readyBox;

    private JTextField chat;

    private JTextArea chatArea;

    private JButton start, cancel, gameOptions, mapGeneratorOptions;

    private PlayersTable table;


    /**
     * Create the panel from which to start a game.
     *
     * @param freeColClient The <code>FreeColClient</code> for the game.
     */
    public StartGamePanel(FreeColClient freeColClient) {
        super(freeColClient, new MigLayout("fill, wrap 2"));
    }


    public void initialize(boolean singlePlayer) {
        removeAll();
        this.singlePlayerGame = singlePlayer;

        if (singlePlayer || getMyPlayer().isAdmin()) {
            Specification spec = getSpecification();
            spec.loadOptionsFile(GameOptions.getXMLElementTagName(),
                FreeColDirectories.getOptionsFile(FreeColDirectories.GAME_OPTIONS_FILE_NAME));
            spec.loadOptionsFile(MapGeneratorOptions.getXMLElementTagName(),
                FreeColDirectories.getOptionsFile(FreeColDirectories.MAP_GENERATOR_OPTIONS_FILE_NAME));
        }

        NationOptions nationOptions = getGame().getNationOptions();

        cancel = new JButton(Messages.message("cancel"));
        setCancelComponent(cancel);

        JScrollPane chatScroll = null, tableScroll;

        table = new PlayersTable(getFreeColClient(), nationOptions,
                                 getMyPlayer());

        start = new JButton(Messages.message("startGame"));

        gameOptions = new JButton(Messages.message(GameOptions.getXMLElementTagName()));

        mapGeneratorOptions = new JButton(Messages.message(MapGeneratorOptions.getXMLElementTagName()));

        readyBox = new JCheckBox(Messages.message("iAmReady"));

        if (singlePlayerGame) {
            // If we set the ready flag to false then the player will
            // be able to change the settings as he likes.
            getMyPlayer().setReady(false);
            // Pretend as if the player is ready.
            readyBox.setSelected(true);
        } else {
            readyBox.setSelected(getMyPlayer().isReady());
            chat = new JTextField();
            chatArea = new JTextArea();
            chatScroll = new JScrollPane(chatArea, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                                         ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        }

        refreshPlayersTable();
        tableScroll = new JScrollPane(table, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        tableScroll.getViewport().setOpaque(false);

        add(tableScroll, "width 600:, grow");
        if (!singlePlayerGame) {
            add(chatScroll, "width 250:, grow");
        }
        add(mapGeneratorOptions, "newline, split 2, growx, top, sg");
        add(gameOptions, "growx, top, sg");
        if (!singlePlayerGame) {
            add(chat, "grow, top");
        }
        add(readyBox, "newline");
        add(start, "newline, span, split 2, tag ok");
        add(cancel, "tag cancel");

        start.setActionCommand(String.valueOf(START));
        cancel.setActionCommand(String.valueOf(CANCEL));
        readyBox.setActionCommand(String.valueOf(READY));
        gameOptions.setActionCommand(String.valueOf(GAME_OPTIONS));
        mapGeneratorOptions.setActionCommand(String.valueOf(MAP_GENERATOR_OPTIONS));

        if (!singlePlayerGame) {
            chat.setActionCommand(String.valueOf(CHAT));
            chat.addActionListener(this);
            chatArea.setEditable(false);
            chatArea.setLineWrap(true);
            chatArea.setWrapStyleWord(true);
            chatArea.setText("");
        }

        start.addActionListener(this);
        cancel.addActionListener(this);
        readyBox.addActionListener(this);
        gameOptions.addActionListener(this);
        mapGeneratorOptions.addActionListener(this);

        setEnabled(true);
    }

    @Override
    public void requestFocus() {
        start.requestFocus();
    }

    /**
     * Updates the map generator options displayed on this panel.
     */
    public void updateMapGeneratorOptions() {
        getFreeColClient().getGame().getMapGeneratorOptions()
            .getOption(MapGeneratorOptions.MAP_WIDTH);
        getFreeColClient().getGame().getMapGeneratorOptions()
            .getOption(MapGeneratorOptions.MAP_HEIGHT);
    }

    /**
     * Updates the game options displayed on this panel.
     */
    public void updateGameOptions() {
        // Nothing yet.
    }

    /**
     * Sets whether or not this component is enabled. It also does this for its
     * children.
     *
     * @param enabled 'true' if this component and its children should be
     *            enabled, 'false' otherwise.
     */
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        Component[] components = getComponents();
        for (int i = 0; i < components.length; i++) {
            components[i].setEnabled(enabled);
        }

        if (singlePlayerGame && enabled) {
            readyBox.setEnabled(false);
        }

        if (enabled) {
            start.setEnabled(getFreeColClient().isAdmin());
        }

        gameOptions.setEnabled(enabled);
    }

    /**
     * Check that the user has not specified degenerate victory conditions
     * that are automatically true.
     *
     * @return True if the victory conditions are sensible.
     */
    private boolean checkVictoryConditions() {
        Specification spec = getSpecification();
        if (singlePlayerGame
            && spec.getBoolean(GameOptions.VICTORY_DEFEAT_EUROPEANS)
            && !spec.getBoolean(GameOptions.VICTORY_DEFEAT_REF)) {
            int n = 0;
            for (Entry<Nation, NationState> e
                     : getGame().getNationOptions().getNations().entrySet()) {
                if (e.getKey().getType().isEuropean()
                    && !e.getKey().isUnknownEnemy()
                    && e.getValue() != NationState.NOT_AVAILABLE) n++;
            }
            if (n == 0) {
                getGUI().showErrorMessage("victory.noEuropeans");
                return false;
            }
        }
        return true;
    }

    /**
     * Displays a chat message to the user.
     *
     * @param senderName The name of the player who sent the chat message to the
     *            server.
     * @param message The chat message.
     * @param privateChat 'true' if the message is a private one, 'false'
     *            otherwise.
     */
    public void displayChat(String senderName, String message, boolean privateChat) {
        if (privateChat) {
            chatArea.append(senderName + " (private): " + message + '\n');
        } else {
            chatArea.append(senderName + ": " + message + '\n');
        }
    }

    /**
     * Refreshes the table that displays the players and the choices that
     * they've made.
     */
    public void refreshPlayersTable() {
        if (table != null) {
            table.update();
        }
    }


    // Interface ActionListener

    /**
     * {@inheritDoc}
     */
    public void actionPerformed(ActionEvent event) {
        final String command = event.getActionCommand();
        final FreeColClient fcc = getFreeColClient();
        final GUI gui = getGUI();
        try {
            switch (Integer.parseInt(command)) {
            case START:
                int row = table.getSelectedRow();
                int col = table.getSelectedColumn();
                if (row > -1 && col > -1){
                    table.getCellEditor(row, col).stopCellEditing();
                }
                if (!checkVictoryConditions()) break;

                // The ready flag was set to false for single player
                // mode in order to allow the player to change
                // whatever he wants.
                if (singlePlayerGame) {
                    getMyPlayer().setReady(true);
                }

                fcc.getPreGameController().requestLaunch();
                break;
            case CANCEL:
                fcc.getConnectController().quitGame(true);
                gui.removeFromCanvas(this);
                gui.showNewPanel();
                break;
            case READY:
                fcc.getPreGameController().setReady(readyBox.isSelected());
                refreshPlayersTable();
                break;
            case CHAT:
                if (!chat.getText().isEmpty()) {
                    fcc.getPreGameController().chat(chat.getText());
                    displayChat(getMyPlayer().getName(), chat.getText(),
                                false);
                    chat.setText("");
                }
                break;
            case GAME_OPTIONS:
                OptionGroup go = gui.showGameOptionsDialog(fcc.isAdmin(), true);
                if (go != null) {
                    fcc.getGame().setGameOptions(go);
                    fcc.getPreGameController().updateGameOptions();
                }
                break;
            case MAP_GENERATOR_OPTIONS:
                OptionGroup mgo = gui.showMapGeneratorOptionsDialog(fcc.isAdmin());
                if (mgo != null) {
                    fcc.getGame().setMapGeneratorOptions(mgo);
                    fcc.getPreGameController().updateMapGeneratorOptions();
                }
                break;
            default:
                super.actionPerformed(event);
            }
        } catch (NumberFormatException e) {
            logger.warning("Invalid ActionEvent, not a number: " + command);
        }
    }


    // Override Component

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeNotify() {
        // Do not propagate to superclass.  This panel is reused so
        // avoid the destructive cleanups in FreeColPanel.removeNotify.

        start.removeActionListener(this);
        cancel.removeActionListener(this);
        readyBox.removeActionListener(this);
        gameOptions.removeActionListener(this);
        mapGeneratorOptions.removeActionListener(this);
        if (chat != null) chat.removeActionListener(this);
    }
}
