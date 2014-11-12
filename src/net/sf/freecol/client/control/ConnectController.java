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

package net.sf.freecol.client.control;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;
import javax.xml.stream.XMLStreamException;

import net.sf.freecol.FreeCol;
import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.client.FreeColClient; 
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.client.gui.i18n.Messages;
import net.sf.freecol.client.gui.panel.ChoiceItem;
import net.sf.freecol.client.gui.panel.LoadingSavegameDialog;
import net.sf.freecol.common.FreeColException;
import net.sf.freecol.common.ServerInfo;
import net.sf.freecol.common.debug.FreeColDebugger;
import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.io.FreeColModFile;
import net.sf.freecol.common.io.FreeColSavegameFile;
import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.model.FreeColObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.networking.Connection;
import net.sf.freecol.common.networking.DOMMessage;
import net.sf.freecol.common.networking.LoginMessage;
import net.sf.freecol.common.networking.NoRouteToServerException;
import net.sf.freecol.common.option.OptionGroup;
import net.sf.freecol.common.resources.ResourceManager;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.FreeColServer.GameState;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;


/**
 * The controller responsible for starting a server and connecting to it.
 * {@link PreGameInputHandler} will be set as the input handler when a
 * successful login has been completed,
 */
public final class ConnectController {

    private static final Logger logger = Logger.getLogger(ConnectController.class.getName());

    private final FreeColClient freeColClient;

    private final GUI gui;


    /**
     * Creates a new <code>ConnectController</code>.
     *
     * @param freeColClient The <code>FreeColClient</code> for the game.
     */
    public ConnectController(FreeColClient freeColClient) {
        this.freeColClient = freeColClient;
        this.gui = freeColClient.getGUI();
    }


    /**
     * The game is finishing.  Release/unhook everything.
     */
    private void finish() {
        ResourceManager.setScenarioMapping(null);
        ResourceManager.setCampaignMapping(null);

        if (!freeColClient.isHeadless()) {
            freeColClient.setInGame(false);
        }
        freeColClient.setGame(null);
        freeColClient.setMyPlayer(null);
        freeColClient.askServer().reset();
        freeColClient.setLoggedIn(false);
    }

    /**
     * Shut down an existing server on a given port.
     *
     * @param port The port to unblock.
     * @return True if there should be no blocking server remaining.
     */
    private boolean unblockServer(int port) {
        FreeColServer freeColServer = freeColClient.getFreeColServer();
        if (freeColServer != null
            && freeColServer.getServer().getPort() == port) {
            if (gui.showSimpleConfirmDialog("stopServer.text", "stopServer.yes",
                                            "stopServer.no")) {
                freeColServer.getController().shutdown();
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * Start a server.
     *
     * @param publicServer If true, add to the meta-server.
     * @param singlePlayer True if this is a single player game.
     * @param spec The <code>Specification</code> to use in this game.
     * @param port The TCP port to use for the public socket.
     * @return A new <code>FreeColServer</code> or null on error.
     */
    private FreeColServer startServer(boolean publicServer,
        boolean singlePlayer, Specification spec, int port) {
        try {
            return new FreeColServer(publicServer, singlePlayer, spec, port,
                                     null);
        } catch (NoRouteToServerException e) {
            gui.showErrorMessage("server.noRouteToServer");
            logger.log(Level.WARNING, "No route to server.", e);
        } catch (IOException e) {
            gui.showErrorMessage("server.couldNotStart");
            logger.log(Level.WARNING, "Could not start server.", e);
        }
        return null;
    }

    /**
     * Get a connection to a server.
     *
     * @param host The name of the machine running the
     *     <code>FreeColServer</code>.
     * @param port The port to use when connecting to the host.
     * @return A <code>Connection</code> to the server.
     */
    private Connection getConnection(String host, int port) {
        try {
            return new Connection(host, port, null, FreeCol.CLIENT_THREAD);
        } catch (IOException e) {
            gui.showErrorMessage("server.couldNotConnect", e.getMessage());
            logger.log(Level.WARNING, "Could not connect to " + host
                + ":" + port, e);
        }
        return null;
    }

    /**
     * Gets a the game state on the remote server.
     *
     * @param host The name of the machine running the
     *     <code>FreeColServer</code>.
     * @param port The port to use when connecting to the host.
     * @return The <code>GameState</code>.
     */
    private GameState getGameState(String host, int port) {
        Connection mc = getConnection(host, port);
        if (mc == null) return null;
        
        String state = null;
        Element element = DOMMessage.createMessage("gameState");
        try {
            Element reply = mc.askDumping(element);
            if (reply == null) {
                gui.showErrorMessage("server.couldNotConnect", "no reply");
                return null;
            } else if (!"gameState".equals(reply.getTagName())) {
                logger.warning("The reply has an unknown type: "
                    + reply.getTagName());
                gui.showErrorMessage("server.couldNotConnect",
                    "bad reply: " + reply.getTagName());
                return null;
            }
            state = reply.getAttribute("state");

        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not send message to server.", e);
            gui.showErrorMessage("server.couldNotConnect", e.getMessage());
            return null;
        } finally {
            mc.close();
        }

        try {
            return Enum.valueOf(GameState.class, state);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Bad state: " + state, e);
            gui.showErrorMessage("server.couldNotConnect", e.getMessage());
        }
        return null;
    }

    /**
     * Gets a list of available players on a given server.
     *
     * @param host The name of the machine running the
     *     <code>FreeColServer</code>.
     * @param port The port to use when connecting to the host.
     * @return A list of available {@link Player#getName() user names}.
     */
    private List<String> getVacantPlayers(String host, int port) {
        Connection mc = getConnection(host, port);
        if (mc == null) return null;

        List<String> items = new ArrayList<String>();
        Element element = DOMMessage.createMessage("getVacantPlayers");
        try {
            Element reply = mc.askDumping(element);
            if (reply == null) {
                logger.warning("The server did not return a list.");
                return null;
            } else if (!"vacantPlayers".equals(reply.getTagName())) {
                logger.warning("The reply has an unknown type: "
                    + reply.getTagName());
                return null;
            }

            NodeList nl = reply.getChildNodes();
            for (int i = 0; i < nl.getLength(); i++) {
                items.add(((Element)nl.item(i)).getAttribute("username"));
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not send message to server.", e);
        } finally {
            mc.close();
        }

        return items;
    }

    /**
     * Starts the client and connects to <i>host:port</i>.
     *
     * Public for the test suite.
     *
     * @param user The name of the player to use.
     * @param host The name of the machine running the
     *            <code>FreeColServer</code>.
     * @param port The port to use when connecting to the host.
     * @return True if the login succeeds.
     */
    public boolean login(String user, String host, int port) {
        freeColClient.setMapEditor(false);
 
        freeColClient.askServer().disconnect();

        String message = null;
        try {
            if (!freeColClient.askServer().connect(FreeCol.CLIENT_THREAD + user,
                    host, port, freeColClient.getPreGameInputHandler())) {
                message = "repeated failure";
            }
        } catch (Exception e) {
            message = e.getMessage();
        }
        if (message != null) {
            gui.showErrorMessage("server.couldNotConnect", message);
            return false;
        }
        logger.info("Connected to " + host + ":" + port);

        LoginMessage msg = freeColClient.askServer().login(user,
            FreeCol.getVersion());
        Game game;
        if (msg == null || (game = msg.getGame()) == null) return false;

        // This completes the client's view of the spec with options
        // obtained from the server difficulty.  It should not be
        // required in the client, to be removed later, when newTurn()
        // only runs in the server
        freeColClient.setGame(game);
        Player player = game.getPlayerByName(user);
        if (player == null) {
            logger.warning("New game does not contain player: " + user);
            return false;
        }
        freeColClient.setMyPlayer(player);
        freeColClient.addSpecificationActions(game.getSpecification());
        freeColClient.updateActions();
        logger.info("FreeColClient logged in as " + user
            + "/" + player.getId());

        // Reconnect
        if (msg.getStartGame()) {
            Tile entryTile = (player.getEntryLocation() == null) ? null
                : player.getEntryLocation().getTile();
            freeColClient.setSinglePlayer(msg.isSinglePlayer());
            boolean play = freeColClient.getPreGameController().startGame();
            if (play) {
                gui.setActiveUnit(null);
                if (msg.isCurrentPlayer()) {
                    freeColClient.getInGameController()
                        .setCurrentPlayer(player);
                    Unit activeUnit = msg.getActiveUnit();
                    if (activeUnit != null) {
                        activeUnit.getOwner().resetIterators();
                        activeUnit.getOwner().setNextActiveUnit(activeUnit);
                        gui.setActiveUnit(activeUnit);
                    } else {
                    gui.setSelectedTile(entryTile, false);
                    }
                } else {
                    gui.setSelectedTile(entryTile, false);
                }
            }
        }

        // All done.
        freeColClient.setLoggedIn(true);
        return true;
    }

    //
    // There are several ways to start a game.
    // - multi-player
    // - single player
    // - restore from saved game
    //
    // They all ultimately have to establish a connection to the server,
    // and get the game from there, which is done in {@link #login()}.
    //
    // When restoring from saved we are mostly done at this point and
    // the game will begin.  Otherwise we may still need to select a
    // nation and change game or map options, which is done in the
    // {@link StartGamePanel}.  The start game panel can then send a
    // requestLaunch message which will tell the server to generate
    // the game and map with the required parameters.  The updated
    // game is sent to all clients with an "updateGame" message.
    //
    // The server then tells the clients that the game is starting
    // with a "startGame" message.  Except for saved games where it
    // cheats and sets a "startGame" flag in the login response that
    // short circuits this.  TODO: which is awkward, tidy?
    //
    // "startGame" ends up in PreGameController.startGame, where the
    // inGame state is finally set to true, and the game begins.
    //

    /**
     * Starts a multiplayer server and connects to it.
     *
     * @param specification The <code>Specification</code> for the game.
     * @param publicServer Whether to make the server public.
     * @param port The port in which the server should listen for new clients.
     * @return True if the game is started successfully.
     */
    public boolean startMultiplayerGame(Specification specification,
                                        boolean publicServer, int port) {
        freeColClient.setMapEditor(false);

        if (freeColClient.isLoggedIn()) logout(true);

        if (!unblockServer(port)) return false;

        FreeColServer freeColServer = startServer(publicServer, false,
                                                  specification, port);
        if (freeColServer == null) return false;

        freeColClient.setFreeColServer(freeColServer);
        return joinMultiplayerGame(FreeColServer.LOCALHOST, port);
    }

    /**
     * Join an existing multiplayer game.
     *
     * @param host The name of the machine running the server.
     * @param port The port to use when connecting to the host.
     * @return True if the game starts successfully.
     */
    public boolean joinMultiplayerGame(String host, int port) {
        freeColClient.setMapEditor(false);

        if (freeColClient.isLoggedIn()) logout(true);

        GameState state = getGameState(host, port);
        if (state == null) return false;
        switch (state) {
        case STARTING_GAME:
            if (!login(FreeCol.getName(), host, port)) return false;
            gui.showStartGamePanel(freeColClient.getGame(),
                                   freeColClient.getMyPlayer(), false);
            freeColClient.setSinglePlayer(false);
            break;

        case IN_GAME:
            // Disable this check if you need to debug a multiplayer client.
            // TODO: allow if the server is in debug mode.
            if (FreeColDebugger.isInDebugMode(FreeColDebugger.DebugMode.MENUS)) {
                // TODO: gui.showErrorMessage("Can not connect to existing game in debug mode");
                return false;
            }
            List<String> names = getVacantPlayers(host, port);
            if (names == null || names.isEmpty()) {
                // TODO: gui.showErrorMessage("no names")
                return false;
            }

            List<ChoiceItem<String>> choices
                = new ArrayList<ChoiceItem<String>>();
            for (String n : names) {
                choices.add(new ChoiceItem<String>(Messages.getName(n), n));
            }
            String id = gui.showChoiceDialog(true, null,
                Messages.message("connectController.choicePlayer"), null,
                "cancel", choices);
            if (id == null) return false; // User cancelled

            if (!login(id + ".ruler", host, port)) {
                // TODO: gui.showErrorMessage() or better, have login() do it
                return false;
            }
            freeColClient.setSinglePlayer(false);
            break;

        case ENDING_GAME: default:
            // TODO: gui.showErrorMessage("game is ending");
            return false;
        }
        return true;
    }

    /**
     * Starts a new single player game by connecting to the server.
     * TODO: connect client/server directly (not using network-classes)
     *
     * @param spec The <code>Specification</code> for the game.
     * @param skip Skip the start game panel.
     * @return True if the game starts successfully.
     */
    public boolean startSinglePlayerGame(Specification spec, boolean skip) {
        freeColClient.setMapEditor(false);

        if (freeColClient.isLoggedIn()) logout(true);

        if (!unblockServer(FreeCol.getServerPort())) return false;

        // Load the player mods into the specification that is about to be
        // used to initialize the server.
        //
        // ATM we only allow mods in single player games.
        // TODO: allow in stand alone server starts?
        spec.loadMods(freeColClient.getClientOptions().getActiveMods());

        FreeColServer freeColServer = startServer(false, true, spec, -1);
        if (freeColServer == null) return false;

        freeColClient.setFreeColServer(freeColServer);
        freeColClient.setSinglePlayer(true);
        if (!login(FreeCol.getName(), FreeColServer.LOCALHOST, 
                freeColServer.getPort())) return false;

        final ClientOptions co = freeColClient.getClientOptions();
        if (co.getBoolean(ClientOptions.AUTOSAVE_DELETE)) {
            FreeColServer.removeAutosaves(co.getText(ClientOptions.AUTO_SAVE_PREFIX));
        }
        freeColClient.getPreGameController().setReady(true);
        if (skip) {
            freeColClient.getPreGameController().requestLaunch();
        } else {
            gui.showStartGamePanel(freeColClient.getGame(),
                                   freeColClient.getMyPlayer(), true);
        }
        return true;
    }

    /**
     * Loads and starts a game from the given file.
     *
     * @param file The saved game.
     * @param userMsg An optional message key to be displayed early.
     * @return True if the game starts successully.
     */
    public boolean startSavedGame(File file, final String userMsg) {
        freeColClient.setMapEditor(false);

        class ErrorJob implements Runnable {
            private final String message;

            ErrorJob( String message ) {
                this.message = message;
            }

            public void run() {
                gui.closeMenus();
                gui.showErrorMessage(message);
            }
        }

        final boolean defaultSinglePlayer;
        final boolean defaultPublicServer;
        final FreeColSavegameFile fis;
        FreeColXMLReader xr = null;
        try {
            // Get suggestions for "singlePlayer" and "publicServer"
            // settings from the file
            fis = new FreeColSavegameFile(file);
            xr = fis.getFreeColXMLReader();
            xr.nextTag();

            String str = xr.getAttribute(FreeColServer.OWNER_TAG,
                                         (String)null);
            if (str != null) FreeCol.setName(str);

            defaultSinglePlayer
                = xr.getAttribute(FreeColServer.SINGLE_PLAYER_TAG, false);
            defaultPublicServer
                = xr.getAttribute(FreeColServer.PUBLIC_SERVER_TAG, false);

        } catch (FileNotFoundException e) {
            SwingUtilities.invokeLater(new ErrorJob("fileNotFound"));
            logger.log(Level.WARNING, "Can not find file: " + file.getName(),
                e);
            return false;
        } catch (XMLStreamException e) {
            logger.log(Level.WARNING, "Error reading game from: "
                + file.getName(), e);
            SwingUtilities.invokeLater(new ErrorJob("server.couldNotStart") );
            return false;
        } catch (Exception e) {
            SwingUtilities.invokeLater(new ErrorJob("couldNotLoadGame"));
            logger.log(Level.WARNING, "Could not load game from: "
                + file.getName(), e);
            return false;
        } finally {
            if (xr != null) xr.close();
        }

        // Reload the client options saved with this game.
        try {
            ClientOptions options = freeColClient.getClientOptions();
            options.updateOptions(fis.getInputStream(FreeColSavegameFile.CLIENT_OPTIONS));
            options.fixClientOptions();
        } catch (FileNotFoundException e) {
            ; // Do not care if there are no client options
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "Error getting client option stream.",
                       ioe);
        }

        final boolean singlePlayer;
        final String name;
        final int port;
        final int sgo = freeColClient.getClientOptions()
            .getInteger(ClientOptions.SHOW_SAVEGAME_SETTINGS);
        boolean show = sgo == ClientOptions.SHOW_SAVEGAME_SETTINGS_ALWAYS
            || (!defaultSinglePlayer
                && sgo == ClientOptions.SHOW_SAVEGAME_SETTINGS_MULTIPLAYER);
        if (show) {
            if (!gui.showLoadingSavegameDialog(defaultPublicServer,
                                               defaultSinglePlayer))
                return false;
            LoadingSavegameDialog lsd = gui.getLoadingSavegameDialog();
            singlePlayer = lsd.isSinglePlayer();
            name = lsd.getServerName();
            port = lsd.getPort();
        } else {
            singlePlayer = defaultSinglePlayer;
            name = null;
            port = -1;
        }

        if (!unblockServer(port)) return false;
        gui.showStatusPanel(Messages.message("status.loadingGame"));

        final File theFile = file;
        Runnable loadGameJob = new Runnable() {
            public void run() {
                FreeColServer freeColServer = null;
                String err = null;
                try {
                    final FreeColSavegameFile saveGame
                        = new FreeColSavegameFile(theFile);
                    freeColServer = new FreeColServer(saveGame,
                        (Specification)null, port, name);
                    freeColClient.setFreeColServer(freeColServer);
                    // Server might have bounced to another port.
                    final int serverPort = freeColServer.getPort();
                    freeColClient.setSinglePlayer(singlePlayer);
                    freeColClient.getInGameController().setGameConnected();
                    if (login(FreeCol.getName(), FreeColServer.LOCALHOST, 
                            serverPort)) {
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                ResourceManager.setScenarioMapping(saveGame.getResourceMapping());
                                if (userMsg != null) gui.showInformationMessage(userMsg);
                                gui.closeStatusPanel();
                            }
                        });
                        return; // Success!
                    }
                    err = "server.loginFail";
                    logger.warning("Could not log in.");
                } catch (FileNotFoundException e) {
                    err = "fileNotFound";
                    logger.log(Level.WARNING, "Can not find file.", e);
                } catch (FreeColException e) {
                    err = e.getMessage();
                    logger.log(Level.WARNING, "FreeCol error.", e);
                } catch (IOException e) {
                    err = "server.couldNotStart";
                    logger.log(Level.WARNING, "Error starting game.", e);
                } catch (NoRouteToServerException e) {
                    err = "server.noRouteToServer";
                    logger.log(Level.WARNING, "No route to server.", e);
                } catch (XMLStreamException e) {
                    err = "server.streamError";
                    logger.log(Level.WARNING, "Stream error.", e);
                }
                if (err != null) {
                    // If this is a debug run, fail hard.
                    if (freeColClient.isHeadless()
                        || FreeColDebugger.getDebugRunTurns() >= 0) {
                        FreeCol.fatal(Messages.message(err));
                    }
                    SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                gui.closeMainPanel();
                                gui.showMainPanel(null);
                            }
                        });
                    SwingUtilities.invokeLater(new ErrorJob(err));
                }
            }
        };
        freeColClient.setWork(loadGameJob);
        return true;
    }

    /**
     * Reconnects to the server.
     *
     * @return True if the reconnection succeeds.
     */
    public boolean reconnect() {
        final String host = freeColClient.askServer().getHost();
        final int port = freeColClient.askServer().getPort();

        gui.removeInGameComponents();
        logout(true);
        if (!login(FreeCol.getName(), host, port)) return false;
        freeColClient.getInGameController().nextModelMessage();
        return true;
    }

    /**
     * Sends a logout message to the server.
     *
     * @param notifyServer Whether or not the server should be
     *     notified of the logout.  For example: if the server kicked us
     *     out then we don't need to confirm with a logout message.
     */
    public void logout(boolean notifyServer) {
        if (notifyServer) {
            freeColClient.askServer().logout();
        }
        freeColClient.askServer().disconnect();
        finish();
    }

    /**
     * Quits the current game, optionally notifying and stopping the server.
     *
     * @param stopServer Whether to stop the server.
     * @param notifyServer Whether or not the server should be
     *     notified of the logout.  For example: if the server kicked us
     *     out then we don't need to confirm with a logout message.
     */
    public void quitGame(boolean stopServer, boolean notifyServer) {
        final FreeColServer server = freeColClient.getFreeColServer();
        if (stopServer && server != null) {
            server.getController().shutdown();
            freeColClient.setFreeColServer(null);
            finish();
        } else {
            if (freeColClient.isLoggedIn()) logout(notifyServer);
        }
    }

    /**
     * Quits the current game. If a server is running it will be
     * stopped if stopServer is <i>true</i>.  The server and perhaps
     * the clients (if a server is running through this client and
     * stopServer is true) will be notified.
     *
     * @param stopServer Indicates whether or not a server that was
     *     started through this client should be stopped.
     */
    public void quitGame(boolean stopServer) {
        quitGame(stopServer, true);
    }

    /**
     * Gets a list of servers from the meta server.
     *
     * @return A list of {@link ServerInfo} objects.
     */
    public List<ServerInfo> getServerList() {
        Connection mc;
        try {
            mc = new Connection(FreeCol.META_SERVER_ADDRESS,
                                FreeCol.META_SERVER_PORT, null,
                                FreeCol.CLIENT_THREAD);
        } catch (IOException e) {
            gui.showErrorMessage("metaServer.couldNotConnect");
            logger.log(Level.WARNING, "Could not connect to meta-server.", e);
            return null;
        }

        try {
            Element reply = mc.askDumping(DOMMessage.createMessage("getServerList"));
            if (reply == null) {
                gui.showErrorMessage("metaServer.communicationError");
                logger.warning("The meta-server did not return a list.");
                return null;
            } else {
                List<ServerInfo> items = new ArrayList<ServerInfo>();
                NodeList nl = reply.getChildNodes();
                for (int i = 0; i < nl.getLength(); i++) {
                    items.add(new ServerInfo((Element)nl.item(i)));
                }
                return items;
            }
        } catch (IOException e) {
            gui.showErrorMessage("metaServer.communicationError");
            logger.log(Level.WARNING, "Network error with meta-server.", e);
            return null;
        } finally {
            mc.close();
        }
    }
}
