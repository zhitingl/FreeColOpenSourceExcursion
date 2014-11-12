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

package net.sf.freecol.server.control;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.freecol.FreeCol;
import net.sf.freecol.client.gui.i18n.Messages;
import net.sf.freecol.common.FreeColException;
import net.sf.freecol.common.debug.FreeColDebugger;
import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.AbstractUnit;
import net.sf.freecol.common.model.BuildableType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ColonyTile;
import net.sf.freecol.common.model.CombatModel.CombatResult;
import net.sf.freecol.common.model.DiplomaticTrade;
import net.sf.freecol.common.model.DiplomaticTrade.TradeStatus;
import net.sf.freecol.common.model.Disaster;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.Europe.MigrationType;
import net.sf.freecol.common.model.ExportData;
import net.sf.freecol.common.model.FoundingFather;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GameOptions;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsContainer;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.HighScore;
import net.sf.freecol.common.model.HighSeas;
import net.sf.freecol.common.model.HistoryEvent;
import net.sf.freecol.common.model.IndianNationType;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.Market.Access;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Monarch;
import net.sf.freecol.common.model.Monarch.MonarchAction;
import net.sf.freecol.common.model.Nameable;
import net.sf.freecol.common.model.Nation;
import net.sf.freecol.common.model.NationSummary;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Player.PlayerType;
import net.sf.freecol.common.model.Player.Stance;
import net.sf.freecol.common.model.RandomRange;
import net.sf.freecol.common.model.Region;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tension;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileImprovement;
import net.sf.freecol.common.model.TileImprovementType;
import net.sf.freecol.common.model.TradeItem;
import net.sf.freecol.common.model.TradeRoute;
import net.sf.freecol.common.model.TradeRouteStop;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Unit.UnitState;
import net.sf.freecol.common.model.UnitLocation;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.model.UnitTypeChange;
import net.sf.freecol.common.model.UnitTypeChange.ChangeType;
import net.sf.freecol.common.model.WorkLocation;
import net.sf.freecol.common.networking.ChatMessage;
import net.sf.freecol.common.networking.Connection;
import net.sf.freecol.common.networking.CurrentPlayerNetworkRequestHandler;
import net.sf.freecol.common.networking.DOMMessage;
import net.sf.freecol.common.networking.DiplomacyMessage;
import net.sf.freecol.common.networking.GoodsForSaleMessage;
import net.sf.freecol.common.networking.IndianDemandMessage;
import net.sf.freecol.common.networking.LootCargoMessage;
import net.sf.freecol.common.networking.MonarchActionMessage;
import net.sf.freecol.common.networking.NetworkRequestHandler;
import net.sf.freecol.common.networking.RearrangeColonyMessage;
import net.sf.freecol.common.networking.RearrangeColonyMessage.UnitChange;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.common.util.RandomChoice;
import net.sf.freecol.common.util.Utils;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.ai.AIPlayer;
import net.sf.freecol.server.ai.REFAIPlayer;
import net.sf.freecol.server.control.ChangeSet.ChangePriority;
import net.sf.freecol.server.control.ChangeSet.See;
import net.sf.freecol.server.control.InputHandler;
import net.sf.freecol.server.model.DiplomacySession;
import net.sf.freecol.server.model.LootSession;
import net.sf.freecol.server.model.MonarchSession;
import net.sf.freecol.server.model.ServerColony;
import net.sf.freecol.server.model.ServerEurope;
import net.sf.freecol.server.model.ServerGame;
import net.sf.freecol.server.model.ServerIndianSettlement;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.server.model.TradeSession;
import net.sf.freecol.server.model.TransactionSession;

import org.w3c.dom.Element;


/**
 * The main server controller.
 */
public final class InGameController extends Controller {

    private static Logger logger = Logger.getLogger(InGameController.class.getName());

    /**
     * Score bonus on declaring independence = (1780, Spring) - turn
     * 1780 is documented in the Col1 manual:
     *   ``if you've declared your independence before 1780, your score
     *     is increased; the sooner you declare; the better your Bonus.''
     * which suggests this needs to be turn based and cut off at the earliest
     * turn in 1780.
     */
    public static final int SCORE_INDEPENDENCE_YEAR = 1780;
    public static final Turn.Season SCORE_INDEPENDENCE_SEASON
        = Turn.Season.SPRING;
    
    /** The server random number source. */
    private final Random random;

    /** Debug helpers, do not serialize. */
    private int debugOnlyAITurns = 0;
    private MonarchAction debugMonarchAction = null;
    private ServerPlayer debugMonarchPlayer = null;


    /**
     * The constructor to use.
     *
     * @param freeColServer The main server object.
     * @param random The pseudo-random number source to use.
     */
    public InGameController(FreeColServer freeColServer, Random random) {
        super(freeColServer);

        this.random = random;
    }

    /**
     * Gets the number of AI turns to skip through.
     *
     * @return The number of terms to skip.
     */
    public int getSkippedTurns() {
        return (FreeColDebugger.isInDebugMode(FreeColDebugger.DebugMode.MENUS))
            ? debugOnlyAITurns : -1;
    }

    /**
     * Sets the number of AI turns to skip through as a debug helper.
     *
     * @param turns The number of turns to skip through.
     */
    public void setSkippedTurns(int turns) {
        if (FreeColDebugger.isInDebugMode(FreeColDebugger.DebugMode.MENUS)) {
            debugOnlyAITurns = turns;
        }
    }

    /**
     * Sets a monarch action to debug/test.
     *
     * @param serverPlayer The <code>ServerPlayer</code> whose monarch
     *     should act.
     * @param action The <code>MonarchAction</code> to be taken.
     */
    public void setMonarchAction(ServerPlayer serverPlayer,
                                 MonarchAction action) {
        if (FreeColDebugger.isInDebugMode(FreeColDebugger.DebugMode.MENUS)) {
            debugMonarchPlayer = serverPlayer;
            debugMonarchAction = action;
        }
    }

    /**
     * Debug convenience to step the random number generator.
     *
     * @return The next random number in series, in the range 0-99.
     */
    public int stepRandom() {
        return Utils.randomInt(logger, "step random", random, 100);
    }

    /**
     * Public version of the yearly goods adjust (public so it can be
     * use in the Market test code).  Sends the market and change
     * messages to the player.
     *
     * @param serverPlayer The <code>ServerPlayer</code> whose market
     *            is to be updated.
     */
    public void yearlyGoodsAdjust(ServerPlayer serverPlayer) {
        ChangeSet cs = new ChangeSet();
        serverPlayer.csYearlyGoodsAdjust(random, cs);
        sendElement(serverPlayer, cs);
    }

    /**
     * Public version of csAddFoundingFather so it can be used in the
     * test code and DebugMenu.
     *
     * @param serverPlayer The <code>ServerPlayer</code> who gains a father.
     * @param father The <code>FoundingFather</code> to add.
     */
    public void addFoundingFather(ServerPlayer serverPlayer,
                                  FoundingFather father) {
        ChangeSet cs = new ChangeSet();
        serverPlayer.csAddFoundingFather(father, random, cs);
        cs.addAttribute(See.only(serverPlayer), "flush",
                        Boolean.TRUE.toString());
        sendElement(serverPlayer, cs);
    }

    /**
     * Public change stance and inform all routine.  Mostly used in the
     * test suite, but the AIs also call it.
     *
     * @param serverPlayer The originating <code>ServerPlayer</code>.
     * @param stance The new <code>Stance</code>.
     * @param other The <code>ServerPlayer</code> wrt which the
     *     stance changes.
     * @param symmetric If true, change the otherPlayer stance as well.
     */
    public void changeStance(ServerPlayer serverPlayer, Stance stance,
                             ServerPlayer other, boolean symmetric) {
        ChangeSet cs = new ChangeSet();
        if (serverPlayer.csChangeStance(stance, other, symmetric, cs)) {
            sendToAll(cs);
        }
    }

    /**
     * Change colony owner.  Public for DebugUtils.
     *
     * @param colony The <code>ServerColony</code> to change.
     * @param serverPlayer The <code>ServerPlayer</code> to change to.
     */
    public void debugChangeOwner(ServerColony colony,
                                 ServerPlayer serverPlayer) {
        ChangeSet cs = new ChangeSet();
        ServerPlayer owner = (ServerPlayer)colony.getOwner();
        colony.csChangeOwner(serverPlayer, cs);//-vis(serverPlayer,owner)
        cs.add(See.perhaps().always(owner), colony.getOwnedTiles());
        serverPlayer.invalidateCanSeeTiles();//+vis(serverPlayer)
        owner.invalidateCanSeeTiles();//+vis(owner)
        sendToAll(cs);
    }

    /**
     * Change unit owner.  Public for DebugUtils.
     *
     * @param unit The <code>ServerUnit</code> to change.
     * @param serverPlayer The <code>ServerPlayer</code> to change to.
     */
    public void debugChangeOwner(ServerUnit unit, ServerPlayer serverPlayer) {
        ChangeSet cs = new ChangeSet();
        ServerPlayer owner = (ServerPlayer)unit.getOwner();
        owner.csChangeOwner(unit, serverPlayer, null, null,
                            cs);//-vis(serverPlayer,owner)
        cs.add(See.perhaps().always(owner), unit.getTile());
        serverPlayer.invalidateCanSeeTiles();//+vis(serverPlayer)
        owner.invalidateCanSeeTiles();//+vis(owner)
        sendToAll(cs);
    }

    /**
     * Apply a disaster to a colony.  Public for DebugUtils.
     *
     * @param colony The <code>Colony</code> to apply the disaster to.
     * @param disaster The <code>Disaster</code> to apply.
     * @return The number of messages generated.
     */
    public int debugApplyDisaster(ServerColony colony, Disaster disaster) {
        ChangeSet cs = new ChangeSet();
        ServerPlayer owner = (ServerPlayer)colony.getOwner();
        List<ModelMessage> messages
            = owner.csApplyDisaster(random, colony, disaster, cs);
        if (!messages.isEmpty()) {
            cs.addMessage(See.all(),
                new ModelMessage(ModelMessage.MessageType.DEFAULT,
                    "model.disaster.strikes", owner)
                .addName("%colony%", colony.getName())
                .addName("%disaster%", disaster));
            for (ModelMessage message : messages) {
                cs.addMessage(See.all(), message);
            }
            sendToAll(cs);
        }
        return messages.size();
    }

    /**
     * Gets a nation summary.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is querying.
     * @param player The <code>Player</code> to summarize.
     * @return An <code>Element</code> encapsulating this action.
     */
    public NationSummary getNationSummary(ServerPlayer serverPlayer,
                                          Player player) {
        return new NationSummary(player, serverPlayer);
    }

    /**
     * Move goods from current location to another.
     *
     * @param goods The <code>Goods</code> to move.
     * @param loc The new <code>Location</code>.
     */
    public void moveGoods(Goods goods, Location loc)
        throws IllegalStateException {
        Location oldLoc = goods.getLocation();
        if (oldLoc == null) {
            throw new IllegalStateException("Goods in null location.");
        } else if (loc == null) {
            ; // Dumping is allowed
        } else if (loc instanceof Unit) {
            if (((Unit) loc).isInEurope()) {
                if (!(oldLoc instanceof Unit && ((Unit) oldLoc).isInEurope())) {
                    throw new IllegalStateException("Goods and carrier not both in Europe.");
                }
            } else if (loc.getTile() == null) {
                throw new IllegalStateException("Carrier not on the map.");
            } else if (oldLoc instanceof Settlement) {
                // Can not be co-located when buying from natives, or
                // when natives are demanding goods from a colony.
            } else if (loc.getTile() != oldLoc.getTile()) {
                throw new IllegalStateException("Goods and carrier not co-located.");
            }
        } else if (loc instanceof IndianSettlement) {
            // Can not be co-located when selling to natives.
        } else if (loc instanceof Colony) {
            if (oldLoc instanceof Unit
                && ((Unit) oldLoc).getOwner() != ((Colony) loc).getOwner()) {
                // Gift delivery
            } else if (loc.getTile() != oldLoc.getTile()) {
                throw new IllegalStateException("Goods and carrier not both in Colony.");
            }
        } else if (loc.getGoodsContainer() == null) {
            throw new IllegalStateException("New location with null GoodsContainer.");
        }

        // Save state of the goods container/s, allowing simpler updates.
        oldLoc.getGoodsContainer().saveState();
        if (loc != null) loc.getGoodsContainer().saveState();

        oldLoc.remove(goods);
        goods.setLocation(null);

        if (loc != null) {
            loc.add(goods);
            goods.setLocation(loc);
        }
    }

    /**
     * Create the Royal Expeditionary Force player corresponding to
     * a given player that is about to rebel.
     * Public for the test suite.
     *
     * TODO: this should eventually generate changes for the REF player.
     *
     * @param serverPlayer The <code>ServerPlayer</code> about to rebel.
     * @return The REF player.
     */
    public ServerPlayer createREFPlayer(ServerPlayer serverPlayer) {
        Nation refNation = serverPlayer.getNation().getREFNation();
        Monarch monarch = serverPlayer.getMonarch();
        ServerPlayer refPlayer = getFreeColServer().addAIPlayer(refNation);
        Europe europe = refPlayer.getEurope();

        // Inherit rebel player knowledge of the seas, coasts, claimed
        // land but not full detailed scouting knowledge.
        List<Tile> explore = new ArrayList<Tile>();
        for (Tile t : getGame().getMap().getAllTiles()) {
            if (!t.isExploredBy(serverPlayer)) continue;
            if (!t.isLand()
                || t.isCoastland()
                || t.getOwner() == serverPlayer) {
                explore.add(t);
            }
        }
        refPlayer.exploreTiles(explore);

        // Trigger initial placement routine
        refPlayer.setEntryLocation(null);
        // Will change, setup only
        Player.makeContact(serverPlayer, refPlayer);

        // Instantiate the REF in Europe
        Monarch.Force exf = monarch.getExpeditionaryForce();
        // @compat 0.10.5
        // There was a bug that seriously under provisioned the navy back
        // around 0.10.5, the game in BR#2435 has it.  For now, just add
        // enough ships to carry all the units.
        UnitType ut = monarch.collectREFUnitTypes(true).get(0);
        while (exf.getSpaceRequired() < exf.getCapacity()) {
            AbstractUnit au
                = new AbstractUnit(ut, Specification.DEFAULT_ROLE_ID, 1);
            exf.add(au);
        }
        // end @compat 0.10.5
        List<Unit> landUnits = refPlayer.createUnits(exf.getLandUnits(),
                                                     europe);//-vis: safe!map
        List<Unit> navalUnits = refPlayer.createUnits(exf.getNavalUnits(),
                                                      europe);//-vis: safe!map
        refPlayer.loadShips(landUnits, navalUnits, random);//-vis: safe!map
        return refPlayer;
    }


    // Client-server communication utilities

    // A handler interface to pass to askFuture().
    // This will change from DOMMessage to Message when DOM goes away.
    private interface DOMMessageHandler {
        public DOMMessage handle(DOMMessage message);
    };

    private static class DOMMessageCallable implements Callable<DOMMessage> {

        private Connection connection;
        private Game game;
        private DOMMessage message;
        private DOMMessageHandler handler;


        public DOMMessageCallable(Connection connection, Game game,
                                  DOMMessage message,
                                  DOMMessageHandler handler) {
            this.connection = connection;
            this.game = game;
            this.message = message;
            this.handler = handler;
        }

        public DOMMessage call() {
            Element reply;
            try {
                reply = connection.askDumping(message.toXMLElement());
            } catch (IOException e) {
                return null;
            }
            DOMMessage replyMessage = DOMMessage.createMessage(game, reply);
            return (replyMessage == null) ? null
                : handler.handle(replyMessage);
        }
    };

    // A service to run the futures.
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Asks a question of a player in a Future.
     *
     * @param serverPlayer The <code>ServerPlayer</code> to ask.
     * @param question The <code>DOMMessage</code> question.
     * @param handler The <code>DOMMessageHandler</code> handler to process
     *     the reply with.
     * @return A future encapsulating the result.
     */
    private Future<DOMMessage> askFuture(ServerPlayer serverPlayer,
                                         DOMMessage question,
                                         DOMMessageHandler handler) {
        Callable<DOMMessage> callable
            = new DOMMessageCallable(serverPlayer.getConnection(), getGame(),
                                     question, handler);
        return executor.submit(callable);
    }

    /**
     * Asks a question of a player with a timeout.
     *
     * @param serverPlayer The <code>ServerPlayer</code> to ask.
     * @param request The <code>DOMMessage</code> question.
     * @return The response to the question, or null if none.
     */
    private DOMMessage askTimeout(ServerPlayer serverPlayer,
                                  DOMMessage request) {
        Future<DOMMessage> future = askFuture(serverPlayer, request,
            new DOMMessageHandler() {
                public DOMMessage handle(DOMMessage message) {
                    return message;
                }
            });
        DOMMessage reply;
        try {
            boolean single = getFreeColServer().isSinglePlayer();
            reply = future.get(FreeCol.getTimeout(single), TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            sendElement(serverPlayer,
                new ChangeSet().addTrivial(See.only(serverPlayer),
                    "closeMenus", ChangePriority.CHANGE_NORMAL));
            reply = null;
        } catch (Exception e) {
            reply = null;
            logger.log(Level.WARNING, "Exception completing future", e);
        }
        return reply;
    }


    /**
     * Get a list of all server players, optionally excluding supplied ones.
     *
     * @param serverPlayers The <code>ServerPlayer</code>s to exclude.
     * @return A list of all connected server players, with exclusions.
     */
    private List<ServerPlayer> getOtherPlayers(ServerPlayer... serverPlayers) {
        List<ServerPlayer> result = new ArrayList<ServerPlayer>();
        outer: for (Player otherPlayer : getGame().getLivePlayers(null)) {
            ServerPlayer enemyPlayer = (ServerPlayer) otherPlayer;
            if (!enemyPlayer.isConnected()) continue;
            for (ServerPlayer exclude : serverPlayers) {
                if (enemyPlayer == exclude) continue outer;
            }
            result.add(enemyPlayer);
        }
        return result;
    }

    /**
     * Send a set of changes to all players.
     *
     * @param cs The <code>ChangeSet</code> to send.
     */
    private void sendToAll(ChangeSet cs) {
        sendToList(getOtherPlayers(), cs);
    }


    /**
     * Send an update to all players except one.
     *
     * @param serverPlayer A <code>ServerPlayer</code> to exclude.
     * @param cs The <code>ChangeSet</code> encapsulating the update.
     */
    private void sendToOthers(ServerPlayer serverPlayer, ChangeSet cs) {
        sendToList(getOtherPlayers(serverPlayer), cs);
    }

    /**
     * Send an element to all players except one.
     * Deprecated, please avoid if possible.
     *
     * @param serverPlayer A <code>ServerPlayer</code> to exclude.
     * @param element An <code>Element</code> to send.
     */
    private void sendToOthers(ServerPlayer serverPlayer, Element element) {
        sendToList(getOtherPlayers(serverPlayer), element);
    }

    /**
     * Send an update to a list of players.
     *
     * @param serverPlayers The <code>ServerPlayer</code>s to send to.
     * @param cs The <code>ChangeSet</code> encapsulating the update.
     */
    private void sendToList(List<ServerPlayer> serverPlayers, ChangeSet cs) {
        for (ServerPlayer s : serverPlayers) sendElement(s, cs);
    }

    /**
     * Send an element to a list of players.
     * Deprecated, please avoid if possible.
     *
     * @param serverPlayers The <code>ServerPlayer</code>s to send to.
     * @param element An <code>Element</code> to send.
     */
    private void sendToList(List<ServerPlayer> serverPlayers, Element element) {
        if (element != null) {
            for (ServerPlayer s : serverPlayers) {
                askElement(s, element);
            }
        }
    }

    /**
     * Send an element to a specific player.
     *
     * @param serverPlayer The <code>ServerPlayer</code> to update.
     * @param cs A <code>ChangeSet</code> to build an <code>Element</code> with.
     */
    private void sendElement(ServerPlayer serverPlayer, ChangeSet cs) {
        askElement(serverPlayer, cs.build(serverPlayer));
    }

    /**
     * Send an element to a specific player.
     * Deprecated, please avoid if possible.
     *
     * @param serverPlayer The <code>ServerPlayer</code> to update.
     * @param request An <code>Element</code> containing the update.
     */
    private void askElement(ServerPlayer serverPlayer, Element request) {
        Connection connection = serverPlayer.getConnection();
        if (connection == null) return;

        while (request != null) {
            Element reply;
            try {
                reply = connection.askDumping(request);
                if (reply == null) break;
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not send \""
                    + request.getTagName() + "\"-message.", e);
                break;
            }

            try {
                request = connection.handle(reply);
            } catch (FreeColException fce) {
                logger.log(Level.WARNING, "Exception processing reply \""
                    + reply.getTagName() + "\"-message.", fce);
                break;
            }
        }
    }

    /**
     * Visits a native settlement, possibly scouting it full if it is
     * as a result of a scout actually asking to speak to the chief,
     * or for other settlement-contacting events such as missionary
     * actions, demanding tribute, learning skills and trading if the
     * settlementActionsContactChief game option is enabled.  It is
     * still unclear what Col1 did here.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is contacting
     *     the settlement.
     * @param is The <code>IndianSettlement</code> to contact.
     * @param scout Positive if this contact is due to a scout asking to
     *     speak to the chief, zero if it is another unit, negative if
     *     this is from the greeting dialog generation.
     * @param cs A <code>ChangeSet</code> to update.
     */
    private void csVisit(ServerPlayer serverPlayer, IndianSettlement is,
                         int scout, ChangeSet cs) {
        serverPlayer.csContact((ServerPlayer)is.getOwner(), cs);
        is.setVisited(serverPlayer);
        if (scout > 0 || (scout == 0 && getGame().getSpecification()
                .getBoolean(GameOptions.SETTLEMENT_ACTIONS_CONTACT_CHIEF))) {
            is.setScouted(serverPlayer);
        }
    }

    // Routines that follow implement the controller response to
    // messages.
    // The convention is to return an element to be passed back to the
    // client by the invoking message handler.

    /**
     * Ends the turn of the given player.
     *
     * Note: sends messages to other players.
     *
     * @param serverPlayer The <code>ServerPlayer</code> to end the turn of.
     * @return An <code>Element</code> encapsulating the end of turn changes
     *     for the given player.
     */
    public Element endTurn(ServerPlayer serverPlayer) {
        final FreeColServer freeColServer = getFreeColServer();
        final ServerGame game = getGame();
        final ServerPlayer winner = (ServerPlayer)game.checkForWinner();

        ServerPlayer player = (ServerPlayer)game.getCurrentPlayer();
        if (serverPlayer != player) {
            throw new IllegalArgumentException("It is not "
                + serverPlayer.getName() + "'s turn, it is "
                + ((player == null) ? "noone" : player.getName()) + "'s!");
        }

        for (;;) {
            logger.finest("Ending turn for " + player.getName());
            player.clearModelMessages();

            // Check for new turn
            ChangeSet cs = new ChangeSet();
            if (game.isNextPlayerInNewTurn()) {
                ChangeSet next = new ChangeSet();
                game.csNextTurn(next);
                sendToAll(next);

                LogBuilder lb = new LogBuilder(512);
                lb.add("New turn ", game.getTurn(), " for ");
                game.csNewTurn(random, lb, cs);
                lb.shrink(", ");
                lb.log(logger, Level.FINEST);
                if (debugOnlyAITurns > 0) {
                    if (--debugOnlyAITurns <= 0) {
                        // If this was a debug run, complete it.  This will
                        // signal the client to save and quit at the next
                        // suitable opportunity.
                        FreeColDebugger.signalEndDebugRun();
                    }
                }
            }

            if ((player = (ServerPlayer)game.getNextPlayer()) == null) {
                // "can not happen"
                return DOMMessage.clientError("Can not get next player");
            }
            player.csFlushMarket(cs);

            // Remove dead players and retry
            switch (player.checkForDeath()) {
            case ServerPlayer.IS_DEAD:
                player.csWithdraw(cs);
                sendToAll(cs);
                logger.info(player.getNation() + " is dead.");
                continue;
            case ServerPlayer.IS_ALIVE:
                if (player.isREF() && player.checkForREFDefeat()) {
                    for (Player p : player.getRebels()) {
                        csGiveIndependence(player, (ServerPlayer) p, cs);
                    }
                    player.csWithdraw(cs);
                    sendToAll(cs);
                    logger.info(player.getNation() + " is defeated.");
                    continue;
                }
                break;
            default: // Need to autorecruit a unit to keep alive.
                ((ServerPlayer)player).csEmigrate(0, MigrationType.SURVIVAL,
                                                  random, cs);
                break;
            }
            // Are there humans left?
            // TODO: see if this can be relaxed so we can run large
            // AI-only simulations.
            boolean human = false;
            for (Player p : game.getLivePlayers(null)) {
                if (!p.isAI() && ((ServerPlayer)p).isConnected()) {
                    human = true;
                    break;
                }
            }
            if (!human) {
                if (debugOnlyAITurns > 0) { // Complete debug runs
                    FreeColDebugger.signalEndDebugRun();
                }
                game.setCurrentPlayer(null);

                cs.addTrivial(See.all(), "gameEnded",
                              ChangePriority.CHANGE_NORMAL);
                sendToOthers(serverPlayer, cs);
                return cs.build(serverPlayer);
            }

            // Has the player won?
            // Do not end single player games where an AI has won,
            // that would stop revenge mode.
            if (winner == player
                && !(freeColServer.isSinglePlayer() && winner.isAI())) {
                boolean highScore = !winner.isAI()
                    && HighScore.newHighScore((ServerPlayer)winner);
                cs.addTrivial(See.all(), "gameEnded",
                              ChangePriority.CHANGE_NORMAL,
                              "highScore", String.valueOf(highScore),
                              "winner", winner.getId());
            }

            // Do "new turn"-like actions that need to wait until right
            // before the player is about to move.
            game.setCurrentPlayer(player);
            if (player.isREF() && player.getEntryLocation() == null) {
                // Initialize this newly created REF
                // If the teleportREF option is enabled, teleport it in.
                REFAIPlayer refAIPlayer = (REFAIPlayer)freeColServer
                    .getAIPlayer(player);
                boolean teleport = getGame().getSpecification()
                    .getBoolean(GameOptions.TELEPORT_REF);
                if (refAIPlayer.initialize(teleport)) {
                    csLaunchREF(player, teleport, cs);
                } else {
                    logger.severe("REF failed to initialize.");
                }
            }
            player.csStartTurn(random, cs);

            cs.addTrivial(See.all(), "setCurrentPlayer",
                          ChangePriority.CHANGE_LATE,
                          "player", player.getId());
            if (player.getPlayerType() == PlayerType.COLONIAL) {
                Monarch monarch = player.getMonarch();
                MonarchAction action = null;
                if (debugMonarchAction != null
                    && player == debugMonarchPlayer) {
                    action = debugMonarchAction;
                    debugMonarchAction = null;
                    debugMonarchPlayer = null;
                    logger.finest("Debug monarch action: " + action);
                } else {
                    action = RandomChoice.getWeightedRandom(logger,
                            "Choose monarch action",
                        monarch.getActionChoices(), random);
                }
                if (action != null) {
                    if (monarch.actionIsValid(action)) {
                        logger.finest("Monarch action: " + action);
                        csMonarchAction(player, action, cs);
                    } else {
                        logger.finest("Skipping invalid monarch action: "
                            + action);
                    }
                }
            }

            // Flush accumulated changes.  Send to all players, but
            // take care that the new player is last so that it does
            // not immediately start moving and cause further changes
            // which conflict with these updates.  Finally return to the
            // current player which requested the end-of-turn, unless
            // it is doing a debug run.
            boolean debugSkip = !player.isAI()
                && freeColServer.isSinglePlayer()
                && debugOnlyAITurns > 0;
            if (debugSkip) {
                sendToOthers(player, cs);
                sendElement(player, cs);
                continue;
            }
            sendToList(getOtherPlayers(player, serverPlayer), cs);
            if (player != serverPlayer) sendElement(player, cs);
            return cs.build(serverPlayer);
        }
    }

    /**
     * Launch the REF.
     *
     * @param serverPlayer The REF <code>ServerPlayer</code>.
     * @param teleport If true, teleport the REF in.
     * @param cs A <code>ChangeSet</code> to update.
     */
    private void csLaunchREF(ServerPlayer serverPlayer, boolean teleport,
                             ChangeSet cs) {
        // Set the REF player entry location from the rebels.  Note
        // that the REF units will have their own unit-specific entry
        // locations set by their missions.  This just flags that the
        // REF is initialized.
        for (Player p : serverPlayer.getRebels()) {
            serverPlayer.setEntryLocation(p.getEntryLocation().getTile());
            break;
        }

        if (teleport) { // Teleport in the units.
            Set<Tile> seen = new HashSet<Tile>();
            for (Unit u : serverPlayer.getUnits()) {
                if (!u.isNaval()) continue;
                Tile entry = u.getEntryLocation().getTile();
                u.setLocation(entry);//-vis(serverPlayer)
                u.setWorkLeft(-1);
                u.setState(Unit.UnitState.ACTIVE);
                if (!seen.contains(entry)) {
                    seen.add(entry);
                    cs.add(See.only(serverPlayer),
                           serverPlayer.exploreForUnit(u));
                    cs.add(See.perhaps().except(serverPlayer), entry);
                }
            }
            serverPlayer.invalidateCanSeeTiles();//+vis(serverPlayer)
        } else {
            // Put navy on the high seas, with 1-turn sail time
            for (Unit u : serverPlayer.getUnits()) {
                if (!u.isNaval()) continue;
                u.setWorkLeft(1);
                u.setDestination(u.getEntryLocation());
                u.setLocation(u.getOwner().getHighSeas());//-vis: safe!map
            }
        }
    }

    /**
     * Give independence.  Note that the REF player is granting, but
     * most of the changes happen to the newly independent player.
     * hence the special handling.
     *
     * @param serverPlayer The REF <code>ServerPlayer</code> that is granting.
     * @param independent The newly independent <code>ServerPlayer</code>.
     * @param cs A <code>ChangeSet</code> to update.
     */
    private void csGiveIndependence(ServerPlayer serverPlayer,
                                    ServerPlayer independent, ChangeSet cs) {
        serverPlayer.csChangeStance(Stance.PEACE, independent, true, cs);
        independent.changePlayerType(PlayerType.INDEPENDENT);
        Game game = getGame();
        Turn turn = game.getTurn();
        independent.setTax(0);
        independent.reinitialiseMarket();
        HistoryEvent h = new HistoryEvent(turn,
            HistoryEvent.EventType.INDEPENDENCE, independent);

        // The score for actual independence is actually a percentage
        // bonus depending on how many other nations are independent.
        // If we ever go for a more complex scoring algorithm it might
        // be better to replace the score int with a Modifier, but for
        // now we just set the score value to the number of
        // independent players other than the one we are granting
        // here, and generate the bonus with a special case in
        // ServerPlayer.updateScore().
        int n = 0;
        for (Player p : game.getLiveEuropeanPlayers(independent)) {
            if (p.getPlayerType() == PlayerType.INDEPENDENT) n++;
        }
        h.setScore(n);
        cs.addGlobalHistory(game, h);
        cs.addMessage(See.only(independent),
            new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                "model.player.independence", independent)
            .addStringTemplate("%ref%", serverPlayer.getNationName()));

        // Who surrenders?
        List<Unit> surrenderUnits = new ArrayList<Unit>();
        for (Unit u : serverPlayer.getUnits()) {
            if (!u.isNaval() && !u.isOnCarrier() && !u.isInEurope()) {
                surrenderUnits.add(u);
            }
        }
        if (!surrenderUnits.isEmpty()) {
            for (Unit u : surrenderUnits) {
                Tile oldTile = u.getTile();
                if (serverPlayer.csChangeOwner(u, independent,
                        ChangeType.CAPTURE, null, cs)) {//-vis(both)
                    u.setMovesLeft(0);
                    u.setState(Unit.UnitState.ACTIVE);
                    cs.add(See.perhaps().always(serverPlayer), oldTile);
                }
            }
            cs.addMessage(See.only(independent),
                new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                    "model.player.independence.unitsAcquired", independent)
                .addStringTemplate("%units%",
                    unitTemplate(", ", surrenderUnits)));
            independent.invalidateCanSeeTiles();//+vis(independent)
            serverPlayer.invalidateCanSeeTiles();//+vis(serverPlayer)
        }

        // Update player type.  Again, a pity to have to do a whole
        // player update, but a partial update works for other players.
        cs.addPartial(See.all().except(independent), independent, "playerType");
        cs.addMessage(See.all().except(independent),
            new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                "model.player.independence.announce", independent)
                .addStringTemplate("%nation%", independent.getNationName())
                .addStringTemplate("%ref%", serverPlayer.getNationName()));
        cs.add(See.only(independent), independent);

        // Reveal the map on independence.
        cs.add(See.only(independent), independent.exploreMap(true));
    }

    private StringTemplate unitTemplate(String base, List<Unit> units) {
        StringTemplate template = StringTemplate.label(base);
        for (Unit u : units) {
            template.addStringTemplate(u.getLabel());
        }
        return template;
    }

    private StringTemplate abstractUnitTemplate(String base,
                                                List<AbstractUnit> units) {
        StringTemplate template = StringTemplate.label(base);
        for (AbstractUnit au : units) {
            template.addStringTemplate(au.getLabel());
        }
        return template;
    }

    private String getNonPlayerNation() {
        int nations = Nation.EUROPEAN_NATIONS.size();
        int start = Utils.randomInt(logger, "Random nation", random, nations);
        for (int index = 0; index < nations; index++) {
            String nationId = "model.nation."
                + Nation.EUROPEAN_NATIONS.get((start + index) % nations);
            if (getGame().getPlayer(nationId) == null) {
                return nationId + ".name";
            }
        }
        // this should never happen
        return "";
    }

    /**
     * Resolves a tax raise.
     *
     * @param serverPlayer The <code>ServerPlayer</code> whose tax is rising.
     * @param taxRaise The amount of tax raise.
     * @param goods The <code>Goods</code> for a goods party.
     * @param result Whether the tax was accepted or not.
     */
    private void raiseTax(ServerPlayer serverPlayer, int taxRaise, Goods goods,
                          boolean result) {
        ChangeSet cs = new ChangeSet();
        serverPlayer.csRaiseTax(taxRaise, goods, result, cs);
        sendElement(serverPlayer, cs);
    }

    /**
     * Performs a monarch action.
     *
     * Note that CHANGE_LATE is used so that these actions follow
     * setting the current player, so that it is the players turn when
     * they respond to a monarch action.
     *
     * @param serverPlayer The <code>ServerPlayer</code> being acted upon.
     * @param action The monarch action.
     * @param cs A <code>ChangeSet</code> to update.
     */
    private void csMonarchAction(final ServerPlayer serverPlayer,
                                 MonarchAction action, ChangeSet cs) {
        final Monarch monarch = serverPlayer.getMonarch();
        final Specification spec = getGame().getSpecification();
        boolean valid = monarch.actionIsValid(action);
        if (!valid) return;
        String messageId = "model.monarch.action." + action;
        StringTemplate template;
        MonarchActionMessage message;
        String monarchKey = serverPlayer.getMonarchKey();

        switch (action) {
        case NO_ACTION:
            break;
        case RAISE_TAX_WAR: case RAISE_TAX_ACT:
            final int taxRaise = monarch.raiseTax(random);
            final Goods goods = serverPlayer.getMostValuableGoods();
            if (goods == null) {
                logger.finest("Ignoring tax raise, no goods to boycott.");
                break;
            }
            template = StringTemplate.template("model.monarch.action." + action)
                .addStringTemplate("%goods%", goods.getType().getLabel())
                .addAmount("%amount%", taxRaise);
            if (action == MonarchAction.RAISE_TAX_WAR) {
                template = template.add("%nation%", getNonPlayerNation());
            } else if (action == MonarchAction.RAISE_TAX_ACT) {
                template = template.addAmount("%number%",
                    Utils.randomInt(logger, "Tax act goods", random, 6))
                    .addName("%newWorld%", serverPlayer.getNewLandName());
            }
            message = new MonarchActionMessage(action, template, monarchKey)
                .setTax(taxRaise);
            cs.add(See.only(serverPlayer), ChangePriority.CHANGE_EARLY,
                   message);
            new MonarchSession(serverPlayer, action, taxRaise, goods);
            break;
        case LOWER_TAX_WAR: case LOWER_TAX_OTHER:
            int oldTax = serverPlayer.getTax();
            int taxLower = monarch.lowerTax(random);
            serverPlayer.csSetTax(taxLower, cs);
            template = StringTemplate.template(messageId)
                .addAmount("%difference%", oldTax - taxLower)
                .addAmount("%newTax%", taxLower);
            if (action == MonarchAction.LOWER_TAX_WAR) {
                template = template.add("%nation%", getNonPlayerNation());
            } else {
                template = template.addAmount("%number%",
                    Utils.randomInt(logger, "Lower tax reason", random, 5));
            }
            cs.add(See.only(serverPlayer), ChangePriority.CHANGE_LATE,
                new MonarchActionMessage(action, template, monarchKey));
            break;
        case WAIVE_TAX:
            cs.add(See.only(serverPlayer), ChangePriority.CHANGE_NORMAL,
                new MonarchActionMessage(action,
                    StringTemplate.template(messageId), monarchKey));
            break;
        case ADD_TO_REF:
            AbstractUnit refAdditions = monarch.chooseForREF(random);
            if (refAdditions == null) break;
            monarch.getExpeditionaryForce().add(refAdditions);
            template = StringTemplate.template(messageId)
                .addAmount("%number%", refAdditions.getNumber())
                .add("%unit%", refAdditions.getType(spec).getNameKey());
            cs.add(See.only(serverPlayer), monarch);
            cs.add(See.only(serverPlayer), ChangePriority.CHANGE_LATE,
                new MonarchActionMessage(action, template, monarchKey));
            break;
        case DECLARE_PEACE:
            List<Player> friends = monarch.collectPotentialFriends();
            if (friends.isEmpty()) break;
            Player friend = Utils.getRandomMember(logger, "Choose friend",
                                                  friends, random);
            serverPlayer.csChangeStance(Stance.PEACE, (ServerPlayer)friend,
                                        true, cs);
            cs.add(See.only(serverPlayer), ChangePriority.CHANGE_LATE,
                new MonarchActionMessage(action,
                    StringTemplate.template(messageId)
                        .addStringTemplate("%nation%", friend.getNationName()),
                    monarchKey));
            break;
        case DECLARE_WAR:
            List<Player> enemies = monarch.collectPotentialEnemies();
            if (enemies.isEmpty()) break;
            Player enemy = Utils.getRandomMember(logger, "Choose enemy",
                                                 enemies, random);
            serverPlayer.csChangeStance(Stance.WAR, (ServerPlayer)enemy,
                                        true, cs);
            cs.add(See.only(serverPlayer), ChangePriority.CHANGE_LATE,
                new MonarchActionMessage(action,
                    StringTemplate.template(messageId)
                        .addStringTemplate("%nation%", enemy.getNationName()),
                    monarchKey));
            break;
        case SUPPORT_LAND: case SUPPORT_SEA:
            boolean sea = action == MonarchAction.SUPPORT_SEA;
            List<AbstractUnit> support = monarch.getSupport(random, sea);
            if (support.isEmpty()) break;
            serverPlayer.createUnits(support,
                serverPlayer.getEurope());//-vis: safe, Europe
            cs.add(See.only(serverPlayer), serverPlayer.getEurope());
            cs.add(See.only(serverPlayer), ChangePriority.CHANGE_LATE,
                new MonarchActionMessage(action,
                    StringTemplate.template(messageId)
                        .addStringTemplate("%addition%",
                            abstractUnitTemplate(", ", support)),
                    monarchKey));
            break;
        case MONARCH_MERCENARIES:
            final List<AbstractUnit> mercenaries
                = monarch.getMercenaries(random);
            if (mercenaries.isEmpty()) break;
            final int mercPrice = serverPlayer.priceMercenaries(mercenaries);
            message = new MonarchActionMessage(action,
                StringTemplate.template("model.monarch.action.MONARCH_MERCENARIES")
                    .addAmount("%gold%", mercPrice)
                    .addStringTemplate("%mercenaries%",
                        abstractUnitTemplate(", ", mercenaries)),
                monarchKey);
            cs.add(See.only(serverPlayer), ChangePriority.CHANGE_EARLY,
                   message);
            new MonarchSession(serverPlayer, action, mercenaries, mercPrice);
            break;
        case HESSIAN_MERCENARIES:
            final List<AbstractUnit> hessians
                = monarch.getMercenaries(random);
            if (hessians.isEmpty()) break;
            int n = Messages.getMercenaryLeaderCount();
            n = Utils.randomInt(logger, "Mercenary leader", random, n);
            final int hessPrice = serverPlayer.priceMercenaries(hessians);
            message = new MonarchActionMessage(action,
                StringTemplate.template("model.monarch.action.HESSIAN_MERCENARIES")
                    .addName("%leader%", Messages.getMercenaryLeaderName(n))
                    .addAmount("%gold%", hessPrice)
                    .addStringTemplate("%mercenaries%",
                        abstractUnitTemplate(", ", hessians)),
                "model.mercenaries." + n + ".image");
            cs.add(See.only(serverPlayer), ChangePriority.CHANGE_EARLY,
                   message);
            new MonarchSession(serverPlayer, action, hessians, hessPrice);
            break;
        case DISPLEASURE: default:
            logger.warning("Bogus action: " + action);
            break;
        }
    }

    public Element monarchAction(ServerPlayer serverPlayer,
                                 MonarchAction action, boolean result) {
        MonarchSession session = TransactionSession.lookup(MonarchSession.class,
            serverPlayer.getId(), "");
        if (session == null) {
            return DOMMessage.clientError("Bogus monarch action: " + action);
        } else if (action != session.getAction()) {
            return DOMMessage.clientError("Session action mismatch, "
                + session.getAction() + " expected: " + action);
        }

        ChangeSet cs = new ChangeSet();
        session.complete(result, cs);
        return cs.build(serverPlayer);
    }

    /**
     * Handle a player retiring.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is retiring.
     * @return An element cleaning up the player.
     */
    public Element retire(ServerPlayer serverPlayer) {
        boolean highScore = HighScore.newHighScore(serverPlayer);
        ChangeSet cs = new ChangeSet();
        serverPlayer.csWithdraw(cs); // Clean up the player.
        sendToOthers(serverPlayer, cs);
        cs.addAttribute(See.only(serverPlayer),
                        "highScore", Boolean.toString(highScore));
        return cs.build(serverPlayer);
    }

    /**
     * Continue playing after winning.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that plays on.
     * @return Null.
     */
    public Element continuePlaying(ServerPlayer serverPlayer) {
        final ServerGame game = (ServerGame)getGame();
        Element reply = null;
        if (!getFreeColServer().isSinglePlayer()) {
            logger.warning("Can not continue playing in multiplayer!");
        } else if (serverPlayer != game.checkForWinner()) {
            logger.warning("Can not continue playing, as "
                           + serverPlayer.getName()
                           + " has not won the game!");
        } else {
            final Specification spec = game.getSpecification();
            spec.getBooleanOption(GameOptions.VICTORY_DEFEAT_REF)
                .setValue(Boolean.FALSE);
            spec.getBooleanOption(GameOptions.VICTORY_DEFEAT_EUROPEANS)
                .setValue(Boolean.FALSE);
            spec.getBooleanOption(GameOptions.VICTORY_DEFEAT_HUMANS)
                .setValue(Boolean.FALSE);
            logger.info("Disabled victory conditions, as "
                        + serverPlayer.getName()
                        + " has won, but is continuing to play.");
        }
        return null;
    }

    /**
     * Cash in a treasure train.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is cashing in.
     * @param unit The treasure train <code>Unit</code> to cash in.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element cashInTreasureTrain(ServerPlayer serverPlayer, Unit unit) {
        ChangeSet cs = new ChangeSet();

        // Work out the cash in amount and the message to send.
        int fullAmount = unit.getTreasureAmount();
        int cashInAmount;
        String messageId;
        if (serverPlayer.getPlayerType() == PlayerType.COLONIAL) {
            // Charge transport fee and apply tax
            cashInAmount = (fullAmount - unit.getTransportFee())
                * (100 - serverPlayer.getTax()) / 100;
            messageId = "model.unit.cashInTreasureTrain.colonial";
        } else {
            // No fee possible, no tax applies.
            cashInAmount = fullAmount;
            messageId = "model.unit.cashInTreasureTrain.independent";
        }

        serverPlayer.modifyGold(cashInAmount);
        cs.addPartial(See.only(serverPlayer), serverPlayer, "gold", "score");
        cs.addMessage(See.only(serverPlayer),
            new ModelMessage(messageId, serverPlayer, unit)
                .addAmount("%amount%", fullAmount)
                .addAmount("%cashInAmount%", cashInAmount));
        messageId = (serverPlayer.isRebel()
                     || serverPlayer.getPlayerType() == PlayerType.INDEPENDENT)
            ? "model.unit.cashInTreasureTrain.other.independent"
            : "model.unit.cashInTreasureTrain.other.colonial";
        cs.addMessage(See.all().except(serverPlayer),
            new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                             messageId, serverPlayer)
                .addAmount("%amount%", fullAmount)
                .addStringTemplate("%nation%", serverPlayer.getNationName()));

        // Dispose of the unit, only visible to the owner.
        cs.add(See.only(serverPlayer), (FreeColGameObject)unit.getLocation());
        cs.addDispose(See.only(serverPlayer), null, unit);//-vis: safe in colony

        // Others can see the cash in message.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Declare independence.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is declaring.
     * @param nationName The new name for the independent nation.
     * @param countryName The new name for its residents.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element declareIndependence(ServerPlayer serverPlayer,
                                       String nationName, String countryName) {
        ChangeSet cs = new ChangeSet();

        // Cross the Rubicon
        StringTemplate oldNation = serverPlayer.getNationName();
        serverPlayer.setIndependentNationName(nationName);
        serverPlayer.setNewLandName(countryName);
        serverPlayer.changePlayerType(PlayerType.REBEL);

        // Do not add history event to cs as we are going to update the
        // entire player.  Likewise clear model messages.
        Turn turn = getGame().getTurn();
        HistoryEvent h = new HistoryEvent(turn,
            HistoryEvent.EventType.DECLARE_INDEPENDENCE, serverPlayer);
        h.setScore(Math.max(0, Turn.yearToTurn(SCORE_INDEPENDENCE_YEAR,
                                               SCORE_INDEPENDENCE_SEASON)
                - turn.getNumber()));
        cs.addGlobalHistory(getGame(), h);
        serverPlayer.clearModelMessages();
        cs.addMessage(See.only(serverPlayer),
            new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                "warOfIndependence.independenceDeclared", serverPlayer));

        // Dispose of units in Europe.
        Europe europe = serverPlayer.getEurope();
        StringTemplate seized = StringTemplate.label(", ");
        for (Unit u : europe.getUnitList()) {
            seized.addStringTemplate(u.getLabel());
        }
        if (!seized.getReplacements().isEmpty()) {
            cs.addMessage(See.only(serverPlayer),
                new ModelMessage(ModelMessage.MessageType.UNIT_LOST,
                                 "model.player.independence.unitsSeized",
                                 serverPlayer)
                    .addStringTemplate("%units%", seized));
        }

        // Generalized continental army muster.
        // Do not use UnitType.getTargetType.
        java.util.Map<UnitType, UnitType> upgrades
            = new HashMap<UnitType, UnitType>();
        final Specification spec = getGame().getSpecification();
        for (UnitType unitType : spec.getUnitTypeList()) {
            UnitType upgrade = unitType.getTargetType(ChangeType.INDEPENDENCE,
                                                      serverPlayer);
            if (upgrade != null) upgrades.put(unitType, upgrade);
        }
        java.util.Map<UnitType, List<Unit>> unitMap
            = new HashMap<UnitType, List<Unit>>();
        for (Colony colony : serverPlayer.getColonies()) {
            List<Unit> allUnits = new ArrayList<Unit>();
            allUnits.addAll(colony.getTile().getUnitList());
            allUnits.addAll(colony.getUnitList());
            int limit = (allUnits.size() + 2) * (colony.getSoL() - 50) / 100;
            if (limit <= 0) continue;

            unitMap.clear();
            for (Unit unit : allUnits) {
                if (upgrades.containsKey(unit.getType())) {
                    List<Unit> unitList = unitMap.get(unit.getType());
                    if (unitList == null) {
                        unitList = new ArrayList<Unit>();
                        unitMap.put(unit.getType(), unitList);
                    }
                    unitList.add(unit);
                }
            }
            for (Entry<UnitType, List<Unit>> entry : unitMap.entrySet()) {
                int n = 0;
                UnitType type = entry.getKey();
                List<Unit> units = entry.getValue();
                while (!units.isEmpty() && n < limit) {
                    Unit unit = units.remove(0);
                    unit.changeType(upgrades.get(type));//-vis
                    cs.add(See.only(serverPlayer), unit);
                    n++;
                }
                cs.addMessage(See.only(serverPlayer),
                    new ModelMessage(ModelMessage.MessageType.UNIT_IMPROVED,
                        "model.player.continentalArmyMuster",
                        serverPlayer, colony)
                    .addName("%colony%", colony.getName())
                    .addAmount("%number%", n)
                    .add("%oldUnit%", type.getNameKey())
                    .add("%unit%", upgrades.get(type).getNameKey()));
                limit -= n;
            }
        }

        // Create the REF.
        ServerPlayer refPlayer = createREFPlayer(serverPlayer);
        // Update the intervention force
        serverPlayer.getMonarch().updateInterventionForce();
        cs.addMessage(See.only(serverPlayer),
                      new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                                       "declareIndependence.interventionForce",
                                       serverPlayer)
                      .add("%nation%", getNonPlayerNation())
                      .addAmount("%number%", spec.getInteger(GameOptions.INTERVENTION_BELLS)));

        // Now the REF is ready, we can dispose of the European connection.
        serverPlayer.getHighSeas().removeDestination(europe);
        cs.addDispose(See.only(serverPlayer), null, europe);//-vis: not on map
        serverPlayer.setEurope(null);
        // Do not clean up the Monarch, it contains the intervention force

        // Pity to have to update such a heavy object as the player,
        // but we do this, at most, once per player.  Other players
        // only need a partial player update and the stance change.
        // Put the stance change after the name change so that the
        // other players see the new nation name declaring war.  The
        // REF is hardwired to declare war on rebels so there is no
        // need to adjust its stance or tension.
        cs.addPartial(See.all().except(serverPlayer), serverPlayer,
                      "playerType", "independentNationName", "newLandName");
        cs.addMessage(See.all().except(serverPlayer),
            new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                             "declareIndependence.announce",
                             serverPlayer)
                .addStringTemplate("%oldNation%", oldNation)
                .addStringTemplate("%newNation%", serverPlayer.getNationName())
                .add("%ruler%", serverPlayer.getRulerNameKey()));
        cs.add(See.only(serverPlayer), serverPlayer);
        serverPlayer.csChangeStance(Stance.WAR, refPlayer, true, cs);
        serverPlayer.invalidateCanSeeTiles();//+vis(serverPlayer)

        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }

    /**
     * Rename an object.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is naming.
     * @param object The <code>Nameable</code> to rename.
     * @param newName The new name.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element renameObject(ServerPlayer serverPlayer, Nameable object,
                                String newName) {
        ChangeSet cs = new ChangeSet();

        if (object instanceof Settlement) {
            ((Settlement)object).getTile().cacheUnseen();//+til
        }
        object.setName(newName);//-til?
        FreeColGameObject fcgo = (FreeColGameObject)object;
        cs.addPartial(See.all(), fcgo, "name");

        // Others may be able to see the name change.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Gets a settlement transaction session, either existing or
     * newly opened.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is trading.
     * @param unit The <code>Unit</code> that is trading.
     * @param settlement The <code>Settlement</code> that is trading.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element getTransaction(ServerPlayer serverPlayer, Unit unit,
                                  Settlement settlement) {
        ChangeSet cs = new ChangeSet();
        TradeSession session = TransactionSession.lookup(TradeSession.class,
            unit, settlement);
        if (session == null) {
            if (unit.getMovesLeft() <= 0) {
                return DOMMessage.clientError("Unit " + unit.getId()
                    + " has no moves left.");
            }
            session = new TradeSession(unit, settlement);
            // Sets unit moves to zero to avoid cheating.  If no
            // action is taken, the moves will be restored when
            // closing the session
            unit.setMovesLeft(0);
            cs.addPartial(See.only(serverPlayer), unit, "movesLeft");
        }

        // Add just the attributes the client needs.
        cs.addAttribute(See.only(serverPlayer), "canBuy",
            Boolean.toString(session.getBuy()));
        cs.addAttribute(See.only(serverPlayer), "canSell",
            Boolean.toString(session.getSell()));
        cs.addAttribute(See.only(serverPlayer), "canGift",
            Boolean.toString(session.getGift()));

        // Others can not see transactions.
        return cs.build(serverPlayer);
    }


    /**
     * Close a transaction.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is trading.
     * @param unit The <code>Unit</code> that is trading.
     * @param settlement The <code>Settlement</code> that is trading.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element closeTransaction(ServerPlayer serverPlayer, Unit unit,
                                    Settlement settlement) {
        TradeSession session
            = TradeSession.lookup(TradeSession.class, unit, settlement);
        if (session == null) {
            return DOMMessage.clientError("No such transaction session.");
        }

        ChangeSet cs = new ChangeSet();
        // Restore unit movement if no action taken.
        if (!session.getActionTaken()) {
            unit.setMovesLeft(session.getMovesLeft());
            cs.addPartial(See.only(serverPlayer), unit, "movesLeft");
        }
        session.complete(cs);

        // Others can not see end of transaction.
        return cs.build(serverPlayer);
    }


    /**
     * Get the goods for sale in a settlement.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is enquiring.
     * @param unit The <code>Unit</code> that is trading.
     * @param settlement The <code>Settlement</code> that is trading.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element getGoodsForSale(ServerPlayer serverPlayer, Unit unit,
                                   Settlement settlement) {
        List<Goods> sellGoods = null;

        if (settlement instanceof IndianSettlement) {
            IndianSettlement indianSettlement = (IndianSettlement) settlement;
            AIPlayer aiPlayer = getFreeColServer()
                .getAIPlayer(indianSettlement.getOwner());
            sellGoods = indianSettlement.getSellGoods(3, unit);
            for (Goods goods : sellGoods) {
                aiPlayer.registerSellGoods(goods);
            }
        } else { // Colony might be supported one day?
            return DOMMessage.clientError("Bogus settlement");
        }
        return new GoodsForSaleMessage(unit, settlement, sellGoods)
            .toXMLElement();
    }


    /**
     * Price some goods for sale from a settlement.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is buying.
     * @param unit The <code>Unit</code> that is trading.
     * @param settlement The <code>Settlement</code> that is trading.
     * @param goods The <code>Goods</code> to buy.
     * @param price The buyers proposed price for the goods.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element buyProposition(ServerPlayer serverPlayer,
                                  Unit unit, Settlement settlement,
                                  Goods goods, int price) {
        TradeSession session
            = TradeSession.lookup(TradeSession.class, unit, settlement);
        if (session == null) {
            return DOMMessage.clientError("Proposing to buy without opening a transaction session?!");
        }
        if (!session.getBuy()) {
            return DOMMessage.clientError("Proposing to buy in a session where buying is not allowed.");
        }
        ChangeSet cs = new ChangeSet();
        if (settlement instanceof IndianSettlement) {
            csVisit(serverPlayer, (IndianSettlement)settlement, 0, cs);
        }

        // AI considers the proposition, return with a gold value
        AIPlayer ai = getFreeColServer().getAIPlayer(settlement.getOwner());
        int gold = ai.buyProposition(unit, settlement, goods, price);

        // Others can not see proposals.
        cs.addAttribute(See.only(serverPlayer),
                        "gold", Integer.toString(gold));
        return cs.build(serverPlayer);
    }

    /**
     * Price some goods for sale to a settlement.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is buying.
     * @param unit The <code>Unit</code> that is trading.
     * @param settlement The <code>Settlement</code> that is trading.
     * @param goods The <code>Goods</code> to sell.
     * @param price The sellers proposed price for the goods.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element sellProposition(ServerPlayer serverPlayer,
                                   Unit unit, Settlement settlement,
                                   Goods goods, int price) {
        TradeSession session
            = TradeSession.lookup(TradeSession.class, unit, settlement);
        if (session == null) {
            return DOMMessage.clientError("Proposing to sell without opening a transaction session");
        }
        if (!session.getSell()) {
            return DOMMessage.clientError("Proposing to sell in a session where selling is not allowed.");
        }
        ChangeSet cs = new ChangeSet();
        if (settlement instanceof IndianSettlement) {
            csVisit(serverPlayer, (IndianSettlement)settlement, 0, cs);
        }

        // AI considers the proposition, return with a gold value
        AIPlayer ai = getFreeColServer().getAIPlayer(settlement.getOwner());
        int gold = ai.sellProposition(unit, settlement, goods, price);

        // Others can not see proposals.
        cs.addAttribute(See.only(serverPlayer),
                        "gold", Integer.toString(gold));
        return cs.build(serverPlayer);
    }

    /**
     * Buy goods in Europe.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is buying.
     * @param unit The <code>Unit</code> to carry the goods.
     * @param type The <code>GoodsType</code> to buy.
     * @param amount The amount of goods to buy.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element buyGoods(ServerPlayer serverPlayer, Unit unit,
                            GoodsType type, int amount) {
        if (!serverPlayer.canTrade(type, Access.EUROPE)) {
            return DOMMessage.clientError("Can not trade boycotted goods");
        }
        ChangeSet cs = new ChangeSet();
        GoodsContainer container = unit.getGoodsContainer();
        container.saveState();
        if ((amount = serverPlayer.buy(container, type, amount)) < 0) {
            return DOMMessage.clientError("Player " + serverPlayer.getName()
                + " tried to buy " + amount + " " + type);
        }
        serverPlayer.propagateToEuropeanMarkets(type, -amount, random);
        serverPlayer.csFlushMarket(type, cs);
        unit.setMovesLeft(0);
        cs.addPartial(See.only(serverPlayer), serverPlayer, "gold");
        cs.add(See.only(serverPlayer), unit);
        // Action occurs in Europe, nothing is visible to other players.
        return cs.build(serverPlayer);
    }

    /**
     * Sell goods in Europe.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is selling.
     * @param unit The <code>Unit</code> carrying the goods.
     * @param type The <code>GoodsType</code> to sell.
     * @param amount The amount of goods to sell.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element sellGoods(ServerPlayer serverPlayer, Unit unit,
                             GoodsType type, int amount) {
        if (!serverPlayer.canTrade(type, Access.EUROPE)) {
            return DOMMessage.clientError("Can not trade boycotted goods");
        }
        ChangeSet cs = new ChangeSet();
        GoodsContainer container = unit.getGoodsContainer();
        container.saveState();
        amount = serverPlayer.sell(container, type, amount);
        if (amount > 0) {
            serverPlayer.propagateToEuropeanMarkets(type, amount, random);
        }
        serverPlayer.csFlushMarket(type, cs);
        unit.setMovesLeft(0);
        cs.addPartial(See.only(serverPlayer), serverPlayer, "gold");
        cs.add(See.only(serverPlayer), unit);
        // Action occurs in Europe, nothing is visible to other players.
        return cs.build(serverPlayer);
    }


    /**
     * A unit migrates from Europe.
     *
     * @param serverPlayer The <code>ServerPlayer</code> whose unit it will be.
     * @param slot The slot within <code>Europe</code> to select the unit from.
     * @param type The type of migration occurring.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element emigrate(ServerPlayer serverPlayer, int slot,
                            MigrationType type) {
        ChangeSet cs = new ChangeSet();
        serverPlayer.csEmigrate(slot, type, random, cs);

        // Do not update others, emigration is private.
        return cs.build(serverPlayer);
    }


    /**
     * Move a unit.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is moving.
     * @param unit The <code>ServerUnit</code> to move.
     * @param newTile The <code>Tile</code> to move to.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element move(ServerPlayer serverPlayer, ServerUnit unit,
                        Tile newTile) {
        ChangeSet cs = new ChangeSet();
        unit.csMove(newTile, random, cs);
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }

    /**
     * Decline to investigate strange mounds.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that owns the unit.
     * @param tile The <code>Tile</code> where the mounds are.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element declineMounds(ServerPlayer serverPlayer, Tile tile) {
        tile.cacheUnseen();//+til
        tile.removeLostCityRumour();//-til

        // Others might see rumour disappear
        ChangeSet cs = new ChangeSet();
        cs.add(See.perhaps(), tile);
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }

    /**
     * Set land name.
     *
     * @param serverPlayer The <code>ServerPlayer</code> who landed.
     * @param unit The <code>Unit</code> that has come ashore.
     * @param name The new land name.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element setNewLandName(ServerPlayer serverPlayer, Unit unit,
                                  String name) {
        ChangeSet cs = new ChangeSet();

        // Special case of a welcome from an adjacent native unit,
        // offering the land the landing unit is on if a peace treaty
        // is accepted.
        serverPlayer.setNewLandName(name);

        // Update the name and note the history.
        cs.addPartial(See.only(serverPlayer), serverPlayer, "newLandName");
        Turn turn = serverPlayer.getGame().getTurn();
        HistoryEvent h = new HistoryEvent(turn,
            HistoryEvent.EventType.DISCOVER_NEW_WORLD, serverPlayer)
                .addName("%name%", name);
        cs.addHistory(serverPlayer, h);

        return cs.build(serverPlayer);
    }

    /**
     * Set region name.
     *
     * @param serverPlayer The <code>ServerPlayer</code> discovering.
     * @param region The <code>Region</code> to discover.
     * @param name The new region name.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element setNewRegionName(ServerPlayer serverPlayer,
                                    Region region, String name) {
        ChangeSet cs = new ChangeSet();
        cs.addRegion(serverPlayer, region, name);

        // Others do find out about region name changes.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }

    /**
     * Move a unit across the high seas.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that owns the unit.
     * @param unit The <code>Unit</code> to move.
     * @param destination The <code>Location</code> to move to.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element moveTo(ServerPlayer serverPlayer, Unit unit,
                          Location destination) {
        ChangeSet cs = new ChangeSet();
        HighSeas highSeas = serverPlayer.getHighSeas();
        Location current = unit.getDestination();
        boolean others = false; // Notify others?
        boolean invalid = false; // Not a highSeas move?

        if (!unit.getType().canMoveToHighSeas()) {
            invalid = true;
        } else if (destination instanceof Europe) {
            if (!highSeas.getDestinations().contains(destination)) {
                return DOMMessage.clientError("HighSeas does not connect to: "
                    + destination.getId());
            } else if (unit.getLocation() == highSeas) {
                if (!(current instanceof Europe)) {
                    // Changed direction
                    unit.setWorkLeft(unit.getSailTurns()
                        - unit.getWorkLeft() + 1);
                }
                unit.setDestination(destination);
                cs.add(See.only(serverPlayer), unit, highSeas);
            } else if (unit.hasTile()) {
                Tile tile = unit.getTile();
                unit.setEntryLocation(tile);
                unit.setWorkLeft(unit.getSailTurns());
                unit.setDestination(destination);
                unit.setMovesLeft(0);
                unit.setLocation(highSeas);//-vis(serverPlayer)
                serverPlayer.invalidateCanSeeTiles();//+vis(serverPlayer)
                cs.addDisappear(serverPlayer, tile, unit);
                cs.add(See.only(serverPlayer), tile, highSeas);
                others = true;
            } else {
                invalid = true;
            }
        } else if (destination instanceof Map) {
            if (!highSeas.getDestinations().contains(destination)) {
                return DOMMessage.clientError("HighSeas does not connect to: "
                    + destination.getId());
            } else if (unit.getLocation() == highSeas) {
                if (current != destination && (current == null
                        || current.getTile() == null
                        || current.getTile().getMap() != destination)) {
                    // Changed direction
                    unit.setWorkLeft(unit.getSailTurns()
                        - unit.getWorkLeft() + 1);
                }
                unit.setDestination(destination);
                cs.add(See.only(serverPlayer), highSeas);
            } else if (unit.getLocation() instanceof Europe) {
                Europe europe = (Europe) unit.getLocation();
                unit.setWorkLeft(unit.getSailTurns());
                unit.setDestination(destination);
                unit.setMovesLeft(0);
                unit.setLocation(highSeas);//-vis: safe!map
                cs.add(See.only(serverPlayer), europe, highSeas);
            } else {
                invalid = true;
            }
        } else if (destination instanceof Settlement) {
            Tile tile = destination.getTile();
            if (!highSeas.getDestinations().contains(tile.getMap())) {
                return DOMMessage.clientError("HighSeas does not connect to: "
                    + destination.getId());
            } else if (unit.getLocation() == highSeas) {
                // Direction is somewhat moot, so just reset.
                unit.setWorkLeft(unit.getSailTurns());
                unit.setDestination(destination);
                cs.add(See.only(serverPlayer), highSeas);
            } else if (unit.getLocation() instanceof Europe) {
                Europe europe = (Europe) unit.getLocation();
                unit.setWorkLeft(unit.getSailTurns());
                unit.setDestination(destination);
                unit.setMovesLeft(0);
                unit.setLocation(highSeas);//-vis: safe!map
                cs.add(See.only(serverPlayer), europe, highSeas);
            } else {
                invalid = true;
            }
        } else {
            return DOMMessage.clientError("Bogus moveTo destination: "
                + destination.getId());
        }
        if (invalid) {
            return DOMMessage.clientError("Invalid moveTo: unit=" + unit.getId()
                + " from=" + unit.getLocation().getId()
                + " to=" + destination.getId());
        }

        if (others) sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }

    /**
     * Embark a unit onto a carrier.
     * Checking that the locations are appropriate is not done here.
     *
     * @param serverPlayer The <code>ServerPlayer</code> embarking.
     * @param serverUnit The <code>ServerUnit</code> that is embarking.
     * @param carrier The <code>Unit</code> to embark onto.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element embarkUnit(ServerPlayer serverPlayer, ServerUnit serverUnit,
                              Unit carrier) {
        if (serverUnit.isNaval()) {
            return DOMMessage.clientError("Naval unit " + serverUnit.getId()
                + " can not embark.");
        }
        UnitLocation.NoAddReason reason = carrier.getNoAddReason(serverUnit);
        if (reason != UnitLocation.NoAddReason.NONE) {
            return DOMMessage.clientError("Carrier: " + carrier.getId()
                + " can not carry " + serverUnit.getId() + ": " + reason);
        }

        ChangeSet cs = new ChangeSet();
        serverUnit.csEmbark(carrier, cs);

        // Others might see the unit disappear, or the carrier capacity.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }

    /**
     * Disembark unit from a carrier.
     *
     * @param serverPlayer The <code>ServerPlayer</code> whose unit is
     *                     embarking.
     * @param serverUnit The <code>ServerUnit</code> that is disembarking.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element disembarkUnit(ServerPlayer serverPlayer,
                                 ServerUnit serverUnit) {
        if (serverUnit.isNaval()) {
            return DOMMessage.clientError("Naval unit " + serverUnit.getId()
                + " can not disembark.");
        }
        Unit carrier = serverUnit.getCarrier();
        if (carrier == null) {
            return DOMMessage.clientError("Unit " + serverUnit.getId()
                + " is not embarked.");
        }

        ChangeSet cs = new ChangeSet();

        Location newLocation = carrier.getLocation();
        List<Tile> newTiles = (newLocation.getTile() == null) ? null
            : serverUnit.collectNewTiles(newLocation.getTile());
        serverUnit.setLocation(newLocation);//-vis(serverPlayer)
        serverPlayer.invalidateCanSeeTiles();//+vis(serverPlayer)
        serverUnit.setMovesLeft(0); // In Col1 disembark consumes whole move.
        cs.add(See.perhaps(), (FreeColGameObject)newLocation);
        if (newTiles != null) {
            serverPlayer.csSeeNewTiles(newTiles, cs);
        }

        // Others can (potentially) see the location.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Combat.  Public for the test suite.
     *
     * @param attackerPlayer The <code>ServerPlayer</code> who is attacking.
     * @param attacker The <code>FreeColGameObject</code> that is attacking.
     * @param defender The <code>FreeColGameObject</code> that is defending.
     * @param crs A list of <code>CombatResult</code>s defining the result.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element combat(ServerPlayer attackerPlayer,
                          FreeColGameObject attacker,
                          FreeColGameObject defender,
                          List<CombatResult> crs) {
        ChangeSet cs = new ChangeSet();
        try {
            attackerPlayer.csCombat(attacker, defender, crs, random, cs);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Combat FAIL", e);
            return DOMMessage.clientError(e.getMessage());
        }
        sendToOthers(attackerPlayer, cs);
        return cs.build(attackerPlayer);
    }

    /**
     * Ask about learning a skill at an IndianSettlement.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is learning.
     * @param unit The <code>Unit</code> that is learning.
     * @param settlement The <code>Settlement</code> to learn from.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element askLearnSkill(ServerPlayer serverPlayer, Unit unit,
                                 IndianSettlement settlement) {
        ChangeSet cs = new ChangeSet();

        csVisit(serverPlayer, settlement, 0, cs);
        Tile tile = settlement.getTile();
        tile.updateIndianSettlement(serverPlayer);
        cs.add(See.only(serverPlayer), tile);
        unit.setMovesLeft(0);
        cs.addPartial(See.only(serverPlayer), unit, "movesLeft");

        // Do not update others, nothing to see yet.
        return cs.build(serverPlayer);
    }

    /**
     * Learn a skill at an IndianSettlement.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is learning.
     * @param unit The <code>Unit</code> that is learning.
     * @param settlement The <code>Settlement</code> to learn from.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element learnFromIndianSettlement(ServerPlayer serverPlayer,
                                             Unit unit,
                                             IndianSettlement settlement) {
        // Sanity checks.
        UnitType skill = settlement.getLearnableSkill();
        if (skill == null) {
            return DOMMessage.clientError("No skill to learn at "
                + settlement.getName());
        }
        if (!unit.getType().canBeUpgraded(skill, ChangeType.NATIVES)) {
            return DOMMessage.clientError("Unit " + unit
                + " can not learn skill " + skill
                + " at " + settlement.getName());
        }

        // Try to learn
        final Specification spec = getGame().getSpecification();
        ChangeSet cs = new ChangeSet();
        unit.setMovesLeft(0);
        csVisit(serverPlayer, settlement, 0, cs);
        switch (settlement.getAlarm(serverPlayer).getLevel()) {
        case HATEFUL: // Killed, might be visible to other players.
            cs.add(See.perhaps().always(serverPlayer),
                   (FreeColGameObject)unit.getLocation());
            cs.addDispose(See.perhaps().always(serverPlayer),
                          unit.getLocation(), unit);//-vis(serverPlayer)
            serverPlayer.invalidateCanSeeTiles();//+vis(serverPlayer)
            break;
        case ANGRY: // Learn nothing, not even a pet update
            cs.addPartial(See.only(serverPlayer), unit, "movesLeft");
            break;
        default:
            // Teach the unit, and expend the skill if necessary.
            // Do a full information update as the unit is in the settlement.
            unit.changeType(skill);//-vis(serverPlayer)
            serverPlayer.invalidateCanSeeTiles();//+vis(serverPlayer)
            cs.add(See.perhaps(), unit);
            if (!settlement.isCapital()
                && !(settlement.hasMissionary(serverPlayer)
                    && spec.getBoolean(GameOptions.ENHANCED_MISSIONARIES))) {
                settlement.setLearnableSkill(null);
            }
            break;
        }
        Tile tile = settlement.getTile();
        tile.updateIndianSettlement(serverPlayer);
        cs.add(See.only(serverPlayer), tile);

        // Others always see the unit, it may have died or been taught.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Demand a tribute from a native settlement.
     *
     * @param serverPlayer The <code>ServerPlayer</code> demanding the tribute.
     * @param unit The <code>Unit</code> that is demanding the tribute.
     * @param settlement The <code>ServerIndianSettlement</code> demanded of.
     * @return An <code>Element</code> encapsulating this action.
     * TODO: Move TURNS_PER_TRIBUTE magic number to the spec.
     */
    public Element demandTribute(ServerPlayer serverPlayer, Unit unit,
                                 ServerIndianSettlement settlement) {
        ChangeSet cs = new ChangeSet();
        final int TURNS_PER_TRIBUTE = 5;

        csVisit(serverPlayer, settlement, 0, cs);

        Player indianPlayer = settlement.getOwner();
        int gold = 0;
        int year = getGame().getTurn().getNumber();
        RandomRange gifts = settlement.getType().getGifts(unit);
        if (settlement.getLastTribute() + TURNS_PER_TRIBUTE < year
            && gifts != null) {
            switch (indianPlayer.getTension(serverPlayer).getLevel()) {
            case HAPPY: case CONTENT:
                gold = Math.min(gifts.getAmount("Tribute", random, true) / 10,
                                100);
                break;
            case DISPLEASED:
                gold = Math.min(gifts.getAmount("Tribute", random, true) / 20,
                                100);
                break;
            case ANGRY: case HATEFUL:
            default:
                gold = 0; // No tribute for you.
                break;
            }
        }

        // Increase tension whether we paid or not.  Apply tension
        // directly to the settlement and let propagation work.
        settlement.csModifyAlarm(serverPlayer, Tension.TENSION_ADD_NORMAL,
                                 true, cs);
        settlement.setLastTribute(year);
        ModelMessage m;
        if (gold > 0) {
            indianPlayer.modifyGold(-gold);
            serverPlayer.modifyGold(gold);
            cs.addPartial(See.only(serverPlayer), serverPlayer, "gold", "score");
            m = new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                                 "scoutSettlement.tributeAgree",
                                 unit, settlement)
                .addAmount("%amount%", gold);
        } else {
            m = new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                                 "scoutSettlement.tributeDisagree",
                                 unit, settlement);
        }
        cs.addMessage(See.only(serverPlayer), m);
        Tile tile = settlement.getTile();
        tile.updateIndianSettlement(serverPlayer);
        cs.add(See.only(serverPlayer), tile);
        unit.setMovesLeft(0);
        cs.addPartial(See.only(serverPlayer), unit, "movesLeft");

        // Do not update others, this is all private.
        return cs.build(serverPlayer);
    }

    /**
     * Scout a native settlement, that is, the contacting action
     * that generates the greeting dialog.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is scouting.
     * @param unit The scout <code>Unit</code>.
     * @param settlement The <code>IndianSettlement</code> to scout.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element scoutIndianSettlement(ServerPlayer serverPlayer,
                                         Unit unit,
                                         IndianSettlement settlement) {
        ChangeSet cs = new ChangeSet();
        Tile tile = settlement.getTile();

        csVisit(serverPlayer, settlement, -1, cs);
        tile.updateIndianSettlement(serverPlayer);
        cs.add(See.only(serverPlayer), tile);
        cs.addAttribute(See.only(serverPlayer), "settlements",
            Integer.toString(settlement.getOwner().getSettlements().size()));

        // This is private.
        return cs.build(serverPlayer);
    }

    /**
     * Speak to the chief at a native settlement.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is scouting.
     * @param unit The scout <code>Unit</code>.
     * @param settlement The <code>IndianSettlement</code> to scout.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element scoutSpeakToChief(ServerPlayer serverPlayer,
                                     Unit unit, IndianSettlement settlement) {
        ChangeSet cs = new ChangeSet();
        Tile tile = settlement.getTile();
        boolean tileDirty = settlement.setVisited(serverPlayer);
        String result;

        // Hateful natives kill the scout right away.
        Tension tension = settlement.getAlarm(serverPlayer);
        if (tension.getLevel() == Tension.Level.HATEFUL) {
            cs.add(See.perhaps().always(serverPlayer),
                   (FreeColGameObject)unit.getLocation());
            cs.addDispose(See.perhaps().always(serverPlayer),
                          unit.getLocation(), unit);//-vis(serverPlayer)
            serverPlayer.invalidateCanSeeTiles();//+vis(serverPlayer)
            result = "die";
        } else {
            // Otherwise player gets to visit, and learn about the settlement.
            List<UnitType> scoutTypes = getGame().getSpecification()
                .getUnitTypesWithAbility(Ability.EXPERT_SCOUT);
            UnitType scoutSkill = (scoutTypes.isEmpty()) ? null
                : scoutTypes.get(0);
            int radius = unit.getLineOfSight();
            UnitType skill = settlement.getLearnableSkill();
            int rnd = Utils.randomInt(logger, "scouting", random, 10);
            if (settlement.hasAnyScouted()) {
                // Do nothing if already spoken to.
                result = "nothing";
            } else if (scoutSkill != null && unit.getType() != scoutSkill
                && ((skill != null && skill.hasAbility(Ability.EXPERT_SCOUT))
                    || rnd == 0)) {
                // If the scout can be taught to be an expert it will be.
                unit.changeType(scoutSkill);//-vis(serverPlayer)
                serverPlayer.invalidateCanSeeTiles();//+vis(serverPlayer)
                result = "expert";
            } else {
                // Choose tales 1/3 of the time, or if there are no beads.
                RandomRange gifts = settlement.getType().getGifts(unit);
                int gold = (gifts == null) ? 0
                    : gifts.getAmount("Base beads amount", random, true);
                if (gold <= 0 || rnd <= 3) {
                    radius = Math.max(radius, IndianSettlement.TALES_RADIUS);
                    result = "tales";
                } else {
                    if (unit.hasAbility(Ability.EXPERT_SCOUT)) {
                        gold = (gold * 11) / 10; // TODO: magic number
                    }
                    serverPlayer.modifyGold(gold);
                    settlement.getOwner().modifyGold(-gold);
                    result = "beads";
                }
            }

            // Have now spoken to the chief.
            csVisit(serverPlayer, settlement, 1, cs);
            tileDirty = true;

            // Update settlement tile with new information, and any
            // newly visible tiles, possibly with enhanced radius.
            List<Tile> tiles = new ArrayList<Tile>();
            for (Tile t : tile.getSurroundingTiles(1, radius)) {
                if (!serverPlayer.canSee(t) && (t.isLand() || t.isShore())) {
                    tiles.add(t);
                }
            }
            cs.add(See.only(serverPlayer), serverPlayer.exploreTiles(tiles));

            // If the unit was promoted, update it completely, otherwise just
            // update moves and possibly gold+score.
            unit.setMovesLeft(0);
            if ("expert".equals(result)) {
                cs.add(See.perhaps(), unit);
            } else {
                cs.addPartial(See.only(serverPlayer), unit, "movesLeft");
                if ("beads".equals(result)) {
                    cs.addPartial(See.only(serverPlayer), serverPlayer,
                                  "gold", "score");
                }
            }
        }
        if (tileDirty) {
            tile.updateIndianSettlement(serverPlayer);
            cs.add(See.only(serverPlayer), tile);
        }

        // Always add result.
        cs.addAttribute(See.only(serverPlayer), "result", result);

        // Other players may be able to see unit disappearing, or
        // learning.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Denounce an existing mission.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is denouncing.
     * @param unit The <code>Unit</code> denouncing.
     * @param settlement The <code>ServerIndianSettlement</code>
     *     containing the mission to denounce.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element denounceMission(ServerPlayer serverPlayer, Unit unit,
                                   ServerIndianSettlement settlement) {
        ChangeSet cs = new ChangeSet();
        csVisit(serverPlayer, settlement, 0, cs);

        // Determine result
        Unit missionary = settlement.getMissionary();
        if (missionary == null) {
            return DOMMessage.clientError("Denouncing null missionary");
        }
        ServerPlayer enemy = (ServerPlayer) missionary.getOwner();
        double denounce = Utils.randomDouble(logger, "Denounce base", random)
            * enemy.getImmigration() / (serverPlayer.getImmigration() + 1);
        if (missionary.hasAbility(Ability.EXPERT_MISSIONARY)) {
            denounce += 0.2;
        }
        if (unit.hasAbility(Ability.EXPERT_MISSIONARY)) {
            denounce -= 0.2;
        }

        if (denounce < 0.5) { // Success, remove old mission and establish ours
            return establishMission(serverPlayer, unit, settlement);
        }

        // Denounce failed
        Player owner = settlement.getOwner();
        cs.add(See.only(serverPlayer), settlement);
        cs.addMessage(See.only(serverPlayer),
            new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                             "indianSettlement.mission.noDenounce",
                             serverPlayer, unit)
                .addStringTemplate("%nation%", owner.getNationName()));
        cs.addMessage(See.only(enemy),
            new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                             "indianSettlement.mission.enemyDenounce",
                             enemy, settlement)
                .addStringTemplate("%enemy%", serverPlayer.getNationName())
                .addStringTemplate("%settlement%", settlement.getLocationNameFor(enemy))
                .addStringTemplate("%nation%", owner.getNationName()));
        cs.add(See.perhaps().always(serverPlayer),
               (FreeColGameObject)unit.getLocation());
        cs.addDispose(See.perhaps().always(serverPlayer),
                      unit.getLocation(), unit);//-vis(serverPlayer)
        serverPlayer.invalidateCanSeeTiles();//+vis(serverPlayer)

        // Others can see missionary disappear
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }

    /**
     * Establish a new mission.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is establishing.
     * @param unit The missionary <code>Unit</code>.
     * @param settlement The <code>ServerIndianSettlement</code> to
     *     establish at.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element establishMission(ServerPlayer serverPlayer, Unit unit,
                                    ServerIndianSettlement settlement) {
        ChangeSet cs = new ChangeSet();
        csVisit(serverPlayer, settlement, 0, cs);

        // Result depends on tension wrt this settlement.
        // Establish if at least not angry.
        final Tension tension = settlement.getAlarm(serverPlayer);
        switch (tension.getLevel()) {
        case HATEFUL: case ANGRY:
            cs.add(See.perhaps().always(serverPlayer),
                   (FreeColGameObject)unit.getLocation());
            cs.addDispose(See.perhaps().always(serverPlayer),
                          unit.getLocation(), unit);//-vis(serverPlayer)
            serverPlayer.invalidateCanSeeTiles();//+vis(serverPlayer)
            break;

        case HAPPY: case CONTENT: case DISPLEASED:
            if (settlement.hasMissionary()) {
                settlement.csKillMissionary("indianSettlement.mission.denounced", cs);
            }
            // Always show the tile the unit was on
            cs.add(See.perhaps().always(serverPlayer), unit.getTile());
            
            settlement.csChangeMissionary(unit, cs);//+vis(serverPlayer)
            break;
        }

        // Add the descriptive message.
        final StringTemplate nation = settlement.getOwner().getNationName();
        final String messageId = "indianSettlement.mission." + tension.getKey();
        cs.addMessage(See.only(serverPlayer),
            new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                             messageId, serverPlayer, unit)
                .addStringTemplate("%nation%", nation));

        // Others can see missionary disappear and settlement acquire
        // mission.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }

    /**
     * Incite a settlement against an enemy.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is inciting.
     * @param unit The missionary <code>Unit</code> inciting.
     * @param settlement The <code>IndianSettlement</code> to incite.
     * @param enemy The <code>ServerPlayer</code> to be incited against.
     * @param gold The amount of gold in the bribe.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element incite(ServerPlayer serverPlayer, Unit unit,
                          IndianSettlement settlement,
                          ServerPlayer enemy, int gold) {
        ChangeSet cs = new ChangeSet();

        Tile tile = settlement.getTile();
        csVisit(serverPlayer, settlement, 0, cs);
        tile.updateIndianSettlement(serverPlayer);
        cs.add(See.only(serverPlayer), tile);

        // How much gold will be needed?
        ServerPlayer enemyPlayer = enemy;
        ServerPlayer nativePlayer = (ServerPlayer)settlement.getOwner();
        int payingValue = nativePlayer.getTension(serverPlayer).getValue();
        int targetValue = nativePlayer.getTension(enemyPlayer).getValue();
        int goldToPay = (payingValue > targetValue) ? 10000 : 5000;
        goldToPay += 20 * (payingValue - targetValue);
        goldToPay = Math.max(goldToPay, 650);

        // Try to incite?
        if (gold < 0) { // Initial enquiry.
            cs.addAttribute(See.only(serverPlayer),
                            "gold", Integer.toString(goldToPay));
        } else if (gold < goldToPay || !serverPlayer.checkGold(gold)) {
            cs.addMessage(See.only(serverPlayer),
                new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                                 "indianSettlement.inciteGoldFail",
                                 serverPlayer, settlement)
                    .addStringTemplate("%player%", enemyPlayer.getNationName())
                    .addAmount("%amount%", goldToPay));
            cs.addAttribute(See.only(serverPlayer), "gold", "0");
            unit.setMovesLeft(0);
            cs.addPartial(See.only(serverPlayer), unit, "movesLeft");
        } else {
            // Success.  Raise the tension for the native player with respect
            // to the European player.  Let resulting stance changes happen
            // naturally in the AI player turn/s.
            nativePlayer.csModifyTension(enemyPlayer,
                Tension.WAR_MODIFIER, cs);//+til
            enemyPlayer.csModifyTension(serverPlayer,
                Tension.TENSION_ADD_WAR_INCITER, cs);//+til
            cs.addAttribute(See.only(serverPlayer),
                            "gold", Integer.toString(gold));
            serverPlayer.modifyGold(-gold);
            nativePlayer.modifyGold(gold);
            cs.addPartial(See.only(serverPlayer), serverPlayer, "gold");
            unit.setMovesLeft(0);
            cs.addPartial(See.only(serverPlayer), unit, "movesLeft");
        }

        // Others might include enemy.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Set a unit destination.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that owns the unit.
     * @param unit The <code>Unit</code> to set the destination for.
     * @param destination The <code>Location</code> to set as destination.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element setDestination(ServerPlayer serverPlayer, Unit unit,
                                  Location destination) {
        if (unit.getTradeRoute() != null) {
            // Override destination to bring the unit to port.
            if (destination == null && unit.isAtSea()) {
                destination = unit.getStop().getLocation();
            }
            unit.setTradeRoute(null);
        }
        unit.setDestination(destination);

        // Others can not see a destination change.
        return new ChangeSet().add(See.only(serverPlayer), unit)
            .build(serverPlayer);
    }


    /**
     * Set current stop of a unit to the next valid stop if any.
     *
     * @param serverPlayer The <code>ServerPlayer</code> the unit belongs to.
     * @param serverUnit The <code>ServerUnit</code> to update.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element updateCurrentStop(ServerPlayer serverPlayer,
                                     ServerUnit serverUnit) {
        // Check if there is a valid current stop?
        int current = serverUnit.validateCurrentStop();
        if (current < 0) { // No valid stop
            serverUnit.setTradeRoute(null);
            return new ChangeSet().add(See.only(serverPlayer), serverUnit)
                .build(serverPlayer);
        }

        List<TradeRouteStop> stops = serverUnit.getTradeRoute().getStops();
        int next = current;
        for (;;) {
            if (++next >= stops.size()) next = 0;
            if (next == current) return null; // No work at any stop, stay put.
            TradeRouteStop nextStop = stops.get(next);
            boolean work = serverUnit.hasWorkAtStop(nextStop);
            logger.finest("Unit " + serverUnit
                + " in trade route " + serverUnit.getTradeRoute().getName()
                + " found" + ((work) ? "" : " no")
                + " work at: " + (FreeColGameObject)nextStop.getLocation());
            if (work) break;
        }

        // Next is the updated stop.
        // Could do just a partial update of currentStop if we did not
        // also need to set the unit destination.
        serverUnit.setCurrentStop(next);

        // Others can not see a stop change.
        return new ChangeSet().add(See.only(serverPlayer), serverUnit)
            .build(serverPlayer);
    }


    /**
     * Buy from a settlement.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is buying.
     * @param unit The <code>Unit</code> that will carry the goods.
     * @param settlement The <code>ServerIndianSettlement</code> to buy from.
     * @param goods The <code>Goods</code> to buy.
     * @param amount How much gold to pay.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element buyFromSettlement(ServerPlayer serverPlayer, Unit unit,
                                     ServerIndianSettlement settlement,
                                     Goods goods, int amount) {
        ChangeSet cs = new ChangeSet();
        csVisit(serverPlayer, settlement, 0, cs);

        TradeSession session
            = TradeSession.lookup(TradeSession.class, unit, settlement);
        if (session == null) {
            return DOMMessage.clientError("Trying to buy without opening a transaction session");
        }
        if (!session.getBuy()) {
            return DOMMessage.clientError("Trying to buy in a session where buying is not allowed.");
        }
        if (!unit.hasSpaceLeft()) {
            return DOMMessage.clientError("Unit is full, unable to buy.");
        }

        // Check that this is the agreement that was made
        AIPlayer ai = getFreeColServer().getAIPlayer(settlement.getOwner());
        int returnGold = ai.buyProposition(unit, settlement, goods, amount);
        if (returnGold != amount) {
            return DOMMessage.clientError("This was not the price we agreed upon! Cheater?");
        }
        if (!serverPlayer.checkGold(amount)) { // Check this is funded.
            return DOMMessage.clientError("Insufficient gold to buy.");
        }

        // Valid, make the trade.
        moveGoods(goods, unit);
        cs.add(See.perhaps(), unit);

        Player settlementPlayer = settlement.getOwner();
        Tile tile = settlement.getTile();
        settlement.updateWantedGoods();
        settlementPlayer.modifyGold(amount);
        serverPlayer.modifyGold(-amount);
        settlement.csModifyAlarm(serverPlayer, -amount / 50, true, cs);
        tile.updateIndianSettlement(serverPlayer);
        cs.add(See.only(serverPlayer), tile);
        cs.addPartial(See.only(serverPlayer), serverPlayer, "gold");
        session.setBuy();
        logger.finest(serverPlayer.getName() + " " + unit + " buys " + goods
                      + " at " + settlement.getName() + " for " + amount);

        // Others can see the unit capacity.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }

    /**
     * Sell to a settlement.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is selling.
     * @param unit The <code>Unit</code> carrying the goods.
     * @param settlement The <code>ServerIndianSettlement</code> to sell to.
     * @param goods The <code>Goods</code> to sell.
     * @param amount How much gold to expect.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element sellToSettlement(ServerPlayer serverPlayer, Unit unit,
                                    ServerIndianSettlement settlement,
                                    Goods goods, int amount) {
        ChangeSet cs = new ChangeSet();
        csVisit(serverPlayer, settlement, 0, cs);

        TradeSession session
            = TransactionSession.lookup(TradeSession.class, unit, settlement);
        if (session == null) {
            return DOMMessage.clientError("Trying to sell without opening a transaction session");
        }
        if (!session.getSell()) {
            return DOMMessage.clientError("Trying to sell in a session where selling is not allowed.");
        }

        // Check that the gold is the agreed amount
        AIPlayer ai = getFreeColServer().getAIPlayer(settlement.getOwner());
        int returnGold = ai.sellProposition(unit, settlement, goods, amount);
        if (returnGold != amount) {
            return DOMMessage.clientError("This was not the price we agreed upon! Cheater?");
        }

        // Valid, make the trade.
        moveGoods(goods, settlement);
        cs.add(See.perhaps(), unit);

        Player settlementPlayer = settlement.getOwner();
        settlementPlayer.modifyGold(-amount);
        serverPlayer.modifyGold(amount);
        settlement.csModifyAlarm(serverPlayer, -amount / 500, true, cs);
        Tile tile = settlement.getTile();
        settlement.updateWantedGoods();
        tile.updateIndianSettlement(serverPlayer);
        cs.add(See.only(serverPlayer), tile);
        cs.addPartial(See.only(serverPlayer), serverPlayer, "gold");
        session.setSell();
        cs.addSale(serverPlayer, settlement, goods.getType(),
                   (int) Math.round((float) amount / goods.getAmount()));
        logger.finest(serverPlayer.getName() + " " + unit + " sells " + goods
                      + " at " + settlement.getName() + " for " + amount);

        // Others can see the unit capacity.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }

    /**
     * Deliver gift to settlement.
     * Note that this includes both European and native gifts.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is delivering.
     * @param unit The <code>Unit</code> that is delivering.
     * @param goods The <code>Goods</code> to deliver.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element deliverGiftToSettlement(ServerPlayer serverPlayer,
                                           Unit unit, Settlement settlement,
                                           Goods goods) {
        TradeSession session
            = TransactionSession.lookup(TradeSession.class, unit, settlement);
        if (session == null) {
            return DOMMessage.clientError("Trying to deliver gift without opening a session");
        }
        if (!session.getGift()) {
            return DOMMessage.clientError("Trying to deliver gift in a session where gift giving is not allowed: " + unit + " " + settlement + " " + session);
        }

        ChangeSet cs = new ChangeSet();
        Tile tile = settlement.getTile();
        moveGoods(goods, settlement);
        cs.add(See.perhaps(), unit);
        if (settlement instanceof ServerIndianSettlement) {
            ServerIndianSettlement sis = (ServerIndianSettlement)settlement;
            csVisit(serverPlayer, sis, 0, cs);
            sis.csModifyAlarm(serverPlayer, -sis.getPriceToBuy(goods) / 50,
                              true, cs);
            sis.updateWantedGoods();
            tile.updateIndianSettlement(serverPlayer);
            cs.add(See.only(serverPlayer), tile);
        }
        session.setGift();

        // Inform the receiver of the gift.
        ModelMessage m = new ModelMessage(ModelMessage.MessageType.GIFT_GOODS,
                                          "model.unit.gift",
                                          settlement, goods.getType())
            .addStringTemplate("%player%", serverPlayer.getNationName())
            .add("%type%", goods.getNameKey())
            .addAmount("%amount%", goods.getAmount())
            .addName("%settlement%", settlement.getName());
        cs.addMessage(See.only(serverPlayer), m);
        ServerPlayer receiver = (ServerPlayer) settlement.getOwner();
        if (receiver.isConnected() && settlement instanceof Colony) {
            cs.add(See.only(receiver), unit);
            cs.add(See.only(receiver), settlement);
            cs.addMessage(See.only(receiver), m);
        }
        logger.info("Gift delivered by unit: " + unit.getId()
                    + " to settlement: " + settlement.getName());

        // Others can see unit capacity, receiver gets it own items.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Load cargo.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is loading.
     * @param unit The <code>Unit</code> to load.
     * @param goods The <code>Goods</code> to load.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element loadCargo(ServerPlayer serverPlayer, Unit unit,
                             Goods goods) {
        ChangeSet cs = new ChangeSet();
        Location oldLocation = goods.getLocation();

        goods.adjustAmount();
        moveGoods(goods, unit);
        boolean moved = false;
        if (unit.getInitialMovesLeft() != unit.getMovesLeft()) {
            unit.setMovesLeft(0);
            moved = true;
        }
        Unit oldUnit = null;
        if (oldLocation instanceof Unit) {
            oldUnit = (Unit) oldLocation;
            if (oldUnit.getInitialMovesLeft() != oldUnit.getMovesLeft()) {
                oldUnit.setMovesLeft(0);
            } else {
                oldUnit = null;
            }
        }

        // Update the goodsContainers only if within a settlement,
        // otherwise update the shared location so that others can
        // see capacity changes.
        if (unit.getSettlement() != null) {
            cs.add(See.only(serverPlayer), oldLocation.getGoodsContainer());
            cs.add(See.only(serverPlayer), unit.getGoodsContainer());
            if (moved) {
                cs.addPartial(See.only(serverPlayer), unit, "movesLeft");
            }
            if (oldUnit != null) {
                cs.addPartial(See.only(serverPlayer), oldUnit, "movesLeft");
            }
        } else {
            cs.add(See.perhaps(), (FreeColGameObject)unit.getLocation());
        }

        // Others might see capacity change.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }

    /**
     * Unload cargo.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is unloading.
     * @param unit The <code>Unit</code> to unload.
     * @param goods The <code>Goods</code> to unload.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element unloadCargo(ServerPlayer serverPlayer, Unit unit,
                               Goods goods) {
        ChangeSet cs = new ChangeSet();

        Location loc;
        Settlement settlement = null;
        if (unit.isInEurope()) { // Must be a dump of boycotted goods
            loc = null;
        } else if (!unit.hasTile()) {
            return DOMMessage.clientError("Unit not on the map.");
        } else if (unit.getSettlement() != null) {
            settlement = unit.getTile().getSettlement();
            loc = settlement;
        } else { // Dump of goods onto a tile
            loc = null;
        }
        goods.adjustAmount();
        moveGoods(goods, loc);
        boolean moved = false;
        if (unit.getInitialMovesLeft() != unit.getMovesLeft()) {
            unit.setMovesLeft(0);
            moved = true;
        }

        if (settlement != null) {
            cs.add(See.only(serverPlayer), settlement.getGoodsContainer());
            cs.add(See.only(serverPlayer), unit.getGoodsContainer());
            if (moved) cs.addPartial(See.only(serverPlayer), unit, "movesLeft");
        } else {
            cs.add(See.perhaps(), (FreeColGameObject)unit.getLocation());
        }

        // Others might see a capacity change.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Clear the specialty of a unit.
     *
     * TODO: why not clear speciality in the open?  You can disband!
     * If we implement this remember to fix the visibility.
     *
     * @param serverPlayer The owner of the unit.
     * @param unit The <code>Unit</code> to clear the speciality of.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element clearSpeciality(ServerPlayer serverPlayer, Unit unit) {
        UnitType newType = unit.getTypeChange(ChangeType.CLEAR_SKILL,
                                              serverPlayer);
        if (newType == null) {
            return DOMMessage.clientError("Can not clear unit speciality: "
                + unit.getId());
        }

        // There can be some restrictions that may prevent the
        // clearing of the speciality.  AFAICT the only ATM is that a
        // teacher can not lose its speciality, but this will need to
        // be revisited if we invent a work location that requires a
        // particular unit type.
        if (unit.getStudent() != null) {
            return DOMMessage.clientError("Can not clear speciality of a teacher.");
        }

        // Valid, change type.
        unit.changeType(newType);//-vis: safe in colony

        // Update just the unit, others can not see it as this only happens
        // in-colony.
        return new ChangeSet().add(See.only(serverPlayer), unit)
            .build(serverPlayer);
    }


    /**
     * Disband a unit.
     *
     * @param serverPlayer The owner of the unit.
     * @param unit The <code>Unit</code> to disband.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element disbandUnit(ServerPlayer serverPlayer, Unit unit) {
        ChangeSet cs = new ChangeSet();

        // Dispose of the unit.
        cs.add(See.perhaps().always(serverPlayer),
               (FreeColGameObject)unit.getLocation());
        cs.addDispose(See.perhaps().always(serverPlayer),
                      unit.getLocation(), unit);//-vis(serverPlayer)
        serverPlayer.invalidateCanSeeTiles();//+vis(serverPlayer)

        // Others can see the unit removal and the space it leaves.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Build a settlement.
     *
     * +til: Resolves many tile appearance changes.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is building.
     * @param unit The <code>Unit</code> that is building.
     * @param name The new settlement name.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element buildSettlement(ServerPlayer serverPlayer, Unit unit,
                                   String name) {
        final Game game = serverPlayer.getGame();
        final Specification spec = game.getSpecification();
        ChangeSet cs = new ChangeSet();

        // Build settlement
        Tile tile = unit.getTile();
        Settlement settlement;
        if (Player.ASSIGN_SETTLEMENT_NAME.equals(name)) {
            name = serverPlayer.getSettlementName(random);
        }
        if (serverPlayer.isEuropean()) {
            StringTemplate nation = serverPlayer.getNationName();
            settlement = new ServerColony(game, serverPlayer, name, tile);
            for (Tile t : tile.getSurroundingTiles(settlement.getRadius())) {
                t.cacheUnseen();//+til
            }
            serverPlayer.addSettlement(settlement);
            settlement.placeSettlement(false);//-vis(serverPlayer,?),-til
            cs.add(See.only(serverPlayer),
                   serverPlayer.exploreForSettlement(settlement));

            cs.addHistory(serverPlayer, new HistoryEvent(game.getTurn(),
                    HistoryEvent.EventType.FOUND_COLONY, serverPlayer)
                .addName("%colony%", settlement.getName()));

            // Remove equipment from founder in case role confuses
            // placement.
            settlement.equipForRole(unit, spec.getDefaultRole(), 0);

            // Coronado
            for (ServerPlayer sp : getOtherPlayers(serverPlayer)) {
                if (!sp.hasAbility(Ability.SEE_ALL_COLONIES)) continue;
                cs.add(See.only(serverPlayer),
                       sp.exploreForSettlement(settlement));//-vis(sp)
                sp.invalidateCanSeeTiles();//+vis(sp)
                cs.addMessage(See.only(sp),
                    new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                                     "buildColony.others", settlement)
                        .addStringTemplate("%nation%", nation)
                        .addStringTemplate("%colony%", settlement.getLocationNameFor(sp))
                        .addName("%region%", tile.getRegion().getName()));
            }
        } else {
            IndianNationType nationType
                = (IndianNationType) serverPlayer.getNationType();
            UnitType skill = RandomChoice
                .getWeightedRandom(logger, "Choose skill",
                                   nationType.generateSkillsForTile(tile),
                                   random);
            if (skill == null) { // Seasoned Scout
                List<UnitType> scouts = spec
                    .getUnitTypesWithAbility(Ability.EXPERT_SCOUT);
                skill = Utils.getRandomMember(logger, "Choose scout",
                                              scouts, random);
            }
            settlement = new ServerIndianSettlement(game, serverPlayer, name,
                                                    tile, false, skill, null);
            for (Tile t : tile.getSurroundingTiles(settlement.getRadius())) {
                t.cacheUnseen();//+til
            }
            serverPlayer.addSettlement(settlement);
            settlement.placeSettlement(true);//-vis(serverPlayer),-til
            for (Player p : getGame().getLivePlayers(serverPlayer)) {
                if ((ServerPlayer)p == serverPlayer) continue;
                ((IndianSettlement)settlement).setAlarm(p, (p.isIndian())
                    ? new Tension(Tension.Level.CONTENT.getLimit())
                    : serverPlayer.getTension(p));//-til
            }
        }

        // Join the settlement.
        unit.setLocation(settlement);//-vis(serverPlayer),-til
        unit.setMovesLeft(0);

        // Update with settlement tile, and newly owned tiles.
        cs.add(See.perhaps(), settlement.getOwnedTiles());
        serverPlayer.invalidateCanSeeTiles();//+vis(serverPlayer)

        // Others can see tile changes.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }

    /**
     * Join a colony.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that owns the unit.
     * @param unit The <code>Unit</code> that is joining.
     * @param colony The <code>Colony</code> to join.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element joinColony(ServerPlayer serverPlayer, Unit unit,
                              Colony colony) {
        final Specification spec = getGame().getSpecification();
        ChangeSet cs = new ChangeSet();
        Set<Tile> ownedTiles = colony.getOwnedTiles();
        Tile tile = colony.getTile();

        // Join.
        tile.cacheUnseen();//+til
        unit.setLocation(colony);//-vis: safe/colony,-til
        unit.setMovesLeft(0);
        colony.equipForRole(unit, spec.getDefaultRole(), 0);

        // Update with colony tile, and tiles now owned.
        cs.add(See.only(serverPlayer), tile);
        for (Tile t : tile.getSurroundingTiles(colony.getRadius())) {
            if (t.getOwningSettlement() == colony && !ownedTiles.contains(t)) {
                cs.add(See.perhaps(), t);
            }
        }

        // Others might see a tile ownership change.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Abandon a settlement.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is abandoning.
     * @param settlement The <code>Settlement</code> to abandon.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element abandonSettlement(ServerPlayer serverPlayer,
                                     Settlement settlement) {
        ChangeSet cs = new ChangeSet();

        // Create history event before disposing.
        if (settlement instanceof Colony) {
            cs.addHistory(serverPlayer,
                new HistoryEvent(getGame().getTurn(),
                    HistoryEvent.EventType.ABANDON_COLONY, serverPlayer)
                    .addName("%colony%", settlement.getName()));
        }

        // Comprehensive dispose.
        serverPlayer.csDisposeSettlement(settlement, cs);//+vis

        // TODO: Player.settlements is still being fixed on the client side.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Claim land.
     *
     * @param serverPlayer The <code>ServerPlayer</code> claiming.
     * @param tile The <code>Tile</code> to claim.
     * @param settlement The <code>Settlement</code> to claim for.
     * @param price The price to pay for the land, which must agree
     *     with the owner valuation, unless negative which denotes stealing.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element claimLand(ServerPlayer serverPlayer, Tile tile,
                             Settlement settlement, int price) {
        ChangeSet cs = new ChangeSet();
        serverPlayer.csClaimLand(tile, settlement, price, cs);

        if (settlement != null && serverPlayer.isEuropean()) {
            // Define Coronado to make all colony-owned tiles visible
            for (ServerPlayer sp : getOtherPlayers(serverPlayer)) {
                if (sp.isEuropean()
                    && sp.hasAbility(Ability.SEE_ALL_COLONIES)) {
                    sp.exploreTile(tile);
                    cs.add(See.only(sp), tile);
                    sp.invalidateCanSeeTiles();//+vis(sp)
                }
            }
        }

        // Others can see the tile.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Accept a diplomatic trade.  Handles the transfers of TradeItems.
     *
     * Note that first contact contexts may not necessarily have a settlement,
     * but this is ok because first contact trade can only include stance
     * and gold trade items.
     *
     * @param agreement The <code>DiplomacyTrade</code> agreement.
     * @param session The <code>DiplomacySession</code> in scope.
     * @param cs A <code>ChangeSet</code> to update.
     */
    private void csAcceptTrade(DiplomaticTrade agreement,
                               DiplomacySession session, ChangeSet cs) {
        final ServerPlayer srcPlayer = (ServerPlayer)agreement.getSender();
        final ServerPlayer dstPlayer = (ServerPlayer)agreement.getRecipient();
        final Unit unit = session.getUnit();
        final Settlement settlement = session.getSettlement();
        boolean visibilityChange = false;

        for (TradeItem tradeItem : agreement.getTradeItems()) {
            // Check trade carefully before committing.
            if (!tradeItem.isValid()) {
                logger.warning("Trade with invalid tradeItem: " + tradeItem);
                continue;
            }
            ServerPlayer source = (ServerPlayer)tradeItem.getSource();
            if (source != srcPlayer && source != dstPlayer) {
                logger.warning("Trade with invalid source: "
                               + ((source == null) ? "null" : source.getId()));
                continue;
            }
            ServerPlayer dest = (ServerPlayer)tradeItem.getDestination();
            if (dest != srcPlayer && dest != dstPlayer) {
                logger.warning("Trade with invalid destination: "
                               + ((dest == null) ? "null" : dest.getId()));
                continue;
            }

            // Collect changes for updating.  Not very OO but
            // TradeItem should not know about server internals.
            // Take care to show items that change hands to the *old*
            // owner too.
            Stance stance = tradeItem.getStance();
            if (stance != null
                && !source.csChangeStance(stance, dest, true, cs)) {
                logger.warning("Stance trade failure: " + stance);
            }
            Colony colony = tradeItem.getColony(getGame());
            if (colony != null) {
                ServerPlayer former = (ServerPlayer) colony.getOwner();
                for (Tile t : colony.getOwnedTiles()) {
                    t.cacheUnseen(dest);//+til
                }
                ((ServerColony)colony).csChangeOwner(dest, cs);//-vis(both),-til
                cs.add(See.only(dest), dest.exploreForSettlement(colony));
                cs.add(See.perhaps().always(former), colony.getOwnedTiles());
                visibilityChange = true;
            }
            int gold = tradeItem.getGold();
            if (gold > 0) {
                source.modifyGold(-gold);
                dest.modifyGold(gold);
                cs.addPartial(See.only(source), source, "gold", "score");
                cs.addPartial(See.only(dest), dest, "gold", "score");
            }
            Goods goods = tradeItem.getGoods();
            if (goods != null && settlement != null) {
                if (settlement.getOwner() == dest) {
                    moveGoods(goods, settlement);
                    cs.add(See.only(source), unit);
                    cs.add(See.only(dest), settlement.getGoodsContainer());
                } else {
                    moveGoods(goods, unit);
                    cs.add(See.only(dest), unit);
                    cs.add(See.only(source), settlement.getGoodsContainer());
                }
            }
            ServerPlayer victim = (ServerPlayer)tradeItem.getVictim();
            if (victim != null
                && !source.csChangeStance(Stance.WAR, victim, true, cs)) {
                logger.warning("Incite trade failure: " + victim);
            }                
            ServerUnit newUnit = (ServerUnit)tradeItem.getUnit();
            if (newUnit != null && settlement != null) {
                ServerPlayer former = (ServerPlayer)newUnit.getOwner();
                Tile oldTile = newUnit.getTile();
                Location newLoc;
                if (unit.isOnCarrier()) {
                    Unit carrier = unit.getCarrier();
                    if (!carrier.couldCarry(newUnit)) {
                        logger.warning("Can not add " + newUnit
                            + " to " + carrier);
                        continue;
                    }
                    newLoc = carrier;
                } else if (dest == unit.getOwner()) {
                    newLoc = unit.getTile();
                } else {
                    newLoc = settlement.getTile();
                }
                if (source.csChangeOwner(newUnit, dest, ChangeType.CAPTURE,
                                         newLoc, cs)) {//-vis(both)
                    newUnit.setMovesLeft(0);
                    cs.add(See.perhaps().always(former), oldTile);
                }
                visibilityChange = true;
            }
        }
        if (visibilityChange) {
            srcPlayer.invalidateCanSeeTiles();//+vis(srcPlayer)
            dstPlayer.invalidateCanSeeTiles();//+vis(dstPlayer)
        }
    }

    /**
     * Handle first contact between European and native player.
     *
     * Note that we check for a diplomacy session, but do only bother
     * in the case of tile!=null as that is the only possibility for
     * some benefit.
     *
     * @param serverPlayer The <code>ServerPlayer</code> making contact.
     * @param other The native <code>ServerPlayer</code> to contact.
     * @param tile A <code>Tile</code> on offer at first landing.
     * @param result Whether the initial peace treaty was accepted.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element nativeFirstContact(ServerPlayer serverPlayer,
                                      ServerPlayer other, Tile tile,
                                      boolean result) {
        ChangeSet cs = new ChangeSet();
        if (result) {
            if (tile != null) {
                Unit u = tile.getFirstUnit();
                Settlement s = tile.getOwningSettlement();
                DiplomacySession session
                    = TransactionSession.lookup(DiplomacySession.class, u, s);
                if (session == null) {
                    return DOMMessage.clientError("No diplomacy in effect for: "
                        + tile.getId());
                }
                tile.cacheUnseen();//+til
                tile.changeOwnership(serverPlayer, null);//-til
                cs.add(See.perhaps(), tile);
            }
        } else { // Consider not accepting the treaty to be an insult.
            other.csModifyTension(serverPlayer,
                Tension.TENSION_ADD_MAJOR, cs);//+til
        }

        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Process a European diplomacy session according to an agreement.
     *
     * @param serverPlayer The <code>ServerPlayer</code> in the session.
     * @param otherPlayer The other <code>ServerPlayer</code> in the session.
     * @param agreement The <code>DiplomaticTrade</code> to consider.
     * @param session The <code>DiplomacySession</code> underway.
     * @param message A <code>DiplomacyMessage</code> to send.
     * @param cs A <code>ChangeSet</code> to contain the trade changes
     *     if accepted.
     * @return True if a new DiplomacyMessage reply needs to be sent.
     */
    private boolean csDiplomacySession(ServerPlayer serverPlayer,
                                       ServerPlayer otherPlayer,
                                       DiplomaticTrade agreement, 
                                       DiplomacySession session,
                                       DiplomacyMessage message,
                                       ChangeSet cs) {
        // Consider the status, process acceptance and rejection,
        // which need no further action.
        TradeStatus status = agreement.getStatus();
        switch (status) {
        case PROPOSE_TRADE:
            session.setAgreement(agreement);
            break;
        case ACCEPT_TRADE:
            // Process the agreement that was sent!
            agreement = session.getAgreement();
            agreement.setStatus(status);
            csAcceptTrade(agreement, session, cs);
            session.complete(cs);
            sendToOthers(serverPlayer, cs);
            return false;
        case REJECT_TRADE: default:
            agreement = session.getAgreement();
            agreement.setStatus(status);
            session.complete(cs);
            sendToOthers(serverPlayer, cs);
            return false;
        }

        // Ask the other player to consider the agreement.
        // Treat a missing reply as a rejection.
        DOMMessage reply = askTimeout(otherPlayer, message);
        if (reply instanceof DiplomacyMessage) {
            agreement = ((DiplomacyMessage)reply).getAgreement();
            status = agreement.getStatus();
        } else {
            status = TradeStatus.REJECT_TRADE;
            agreement.setStatus(status);
        }
        agreement.incrementVersion();

        // Process the result.  Always return true here as the serverPlayer
        // needs to be informed of the other player's response.
        switch (status) {
        case PROPOSE_TRADE:
            session.setAgreement(agreement);
            break;
        case ACCEPT_TRADE:
            agreement = session.getAgreement(); // Accept offered agreement
            agreement.setStatus(status);
            csAcceptTrade(agreement, session, cs);
            session.complete(cs);
            sendToOthers(serverPlayer, cs);
            break;
        case REJECT_TRADE: default:
            session.setAgreement(agreement);
            session.complete(cs);
            sendToOthers(serverPlayer, cs);
            break;
        }
        return true;
    }

    /**
     * Handle first contact between European players.
     *
     * @param serverPlayer The <code>ServerPlayer</code> making contact.
     * @param ourUnit Our <code>Unit</code>.
     * @param otherUnit The other <code>unit</code>.
     * @param agreement The <code>DiplomaticTrade</code> to consider.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element europeanFirstContact(ServerPlayer serverPlayer,
                                        Unit ourUnit, Unit otherUnit,
                                        DiplomaticTrade agreement) {
        ChangeSet cs = new ChangeSet();
        DiplomacySession session
            = TransactionSession.lookup(DiplomacySession.class, 
                                        ourUnit, otherUnit);
        if (session == null) {
            session = TransactionSession.lookup(DiplomacySession.class,
                                                otherUnit, ourUnit);
        }
        if (session == null) {
            if (agreement.getStatus() != TradeStatus.PROPOSE_TRADE) {
                return DOMMessage.clientError("Missing session for "
                    + ourUnit.getId() + "," + otherUnit.getId());
            }
            session = new DiplomacySession(ourUnit, otherUnit);
            ourUnit.setMovesLeft(0);
            cs.addPartial(See.only(serverPlayer), ourUnit, "movesLeft");
        }
        ServerPlayer otherPlayer = (ServerPlayer)otherUnit.getOwner();
        if (csDiplomacySession(serverPlayer, otherPlayer,
                agreement, session,
                new DiplomacyMessage(otherUnit, ourUnit, agreement), cs)) {
            cs.add(See.only(serverPlayer), ChangePriority.CHANGE_LATE,
                new DiplomacyMessage(ourUnit, otherUnit, 
                                     session.getAgreement()));
        }
        return cs.build(serverPlayer);
    }

    /**
     * Handle first contact between European players.
     *
     * @param serverPlayer The <code>ServerPlayer</code> making contact.
     * @param ourUnit Our <code>Unit</code>.
     * @param otherColony The other <code>Colony</code>.
     * @param agreement The <code>DiplomaticTrade</code> to consider.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element europeanFirstContact(ServerPlayer serverPlayer,
                                        Unit ourUnit, Colony otherColony,
                                        DiplomaticTrade agreement) {
        ChangeSet cs = new ChangeSet();
        DiplomacySession session
            = TransactionSession.lookup(DiplomacySession.class,
                                        ourUnit, otherColony);
        if (session == null) {
            if (agreement.getStatus() != TradeStatus.PROPOSE_TRADE) {
                return DOMMessage.clientError("Missing diplomacy session for "
                    + ourUnit.getId() + "," + otherColony.getId());
            }
            session = new DiplomacySession(ourUnit, otherColony);
            ourUnit.setMovesLeft(0);
            cs.addPartial(See.only(serverPlayer), ourUnit, "movesLeft");
        }
        ServerPlayer otherPlayer = (ServerPlayer)otherColony.getOwner();
        if (csDiplomacySession(serverPlayer, otherPlayer,
                agreement, session,
                new DiplomacyMessage(otherColony, ourUnit, agreement), cs)) {
            cs.add(See.only(serverPlayer), ChangePriority.CHANGE_LATE,
                new DiplomacyMessage(ourUnit, otherColony, 
                                     session.getAgreement()));
        }
        return cs.build(serverPlayer);
    }

    /**
     * Handle first contact between European players.
     *
     * @param serverPlayer The <code>ServerPlayer</code> making contact.
     * @param ourColony Our <code>Colony</code>.
     * @param otherUnit The other <code>Unit</code>.
     * @param agreement The <code>DiplomaticTrade</code> to consider.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element europeanFirstContact(ServerPlayer serverPlayer,
                                        Colony ourColony, Unit otherUnit,
                                        DiplomaticTrade agreement) {
        ChangeSet cs = new ChangeSet();
        DiplomacySession session
            = TransactionSession.lookup(DiplomacySession.class,
                                        otherUnit, ourColony);
        if (session == null) {
            return DOMMessage.clientError("Missing diplomacy session for "
                + ourColony.getId() + "," + otherUnit.getId());
        }
        ServerPlayer otherPlayer = (ServerPlayer)otherUnit.getOwner();
        if (csDiplomacySession(serverPlayer, otherPlayer,
                agreement, session,
                new DiplomacyMessage(otherUnit, ourColony, agreement), cs)) {
            cs.add(See.only(serverPlayer), ChangePriority.CHANGE_LATE,
                new DiplomacyMessage(ourColony, otherUnit,
                                     session.getAgreement()));
        }
        return cs.build(serverPlayer);
    }

    /**
     * Diplomacy.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is trading.
     * @param ourUnit The <code>Unit</code> that is trading.
     * @param otherColony The <code>Colony</code> to trade with.
     * @param agreement The <code>DiplomaticTrade</code> to consider.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element diplomacy(ServerPlayer serverPlayer, Unit ourUnit,
                             Colony otherColony, DiplomaticTrade agreement) {
        ChangeSet cs = new ChangeSet();
        TradeStatus status = agreement.getStatus();
        DiplomacySession session
            = TransactionSession.lookup(DiplomacySession.class,
                                        ourUnit, otherColony);
        if (session == null) {
            if (status != TradeStatus.PROPOSE_TRADE) {
                return DOMMessage.clientError("Mission session for "
                    + ourUnit.getId() + "/" + otherColony.getId());
            }
            session = new DiplomacySession(ourUnit, otherColony);
        }
        ServerPlayer otherPlayer = (ServerPlayer)otherColony.getOwner();
        if (csDiplomacySession(serverPlayer, otherPlayer, agreement, session,
                new DiplomacyMessage(otherColony, ourUnit, agreement), cs)) {
            cs.add(See.only(serverPlayer), ChangePriority.CHANGE_LATE,
                new DiplomacyMessage(ourUnit, otherColony, 
                                     session.getAgreement()));
        }
        return cs.build(serverPlayer);
    }

    /**
     * Diplomacy.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is trading.
     * @param ourColony Our <code>Colony</code>.
     * @param otherUnit The other <code>Unit</code> that is trading.
     * @param agreement The <code>DiplomaticTrade</code> to consider.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element diplomacy(ServerPlayer serverPlayer, Colony ourColony,
                             Unit otherUnit, DiplomaticTrade agreement) {
        ChangeSet cs = new ChangeSet();
        DiplomacySession session
            = TransactionSession.lookup(DiplomacySession.class,
                                        otherUnit, ourColony);
        if (session == null) {
            return DOMMessage.clientError("Mission session for "
                + otherUnit.getId() + "/" + ourColony.getId());
        }
        ServerPlayer otherPlayer = (ServerPlayer)otherUnit.getOwner();
        if (csDiplomacySession(serverPlayer, otherPlayer, agreement, session,
                new DiplomacyMessage(otherUnit, ourColony, agreement), cs)) {
            cs.add(See.only(serverPlayer), ChangePriority.CHANGE_LATE,
                new DiplomacyMessage(ourColony, otherUnit,
                                     session.getAgreement()));
        }
        return cs.build(serverPlayer);
    }

    /**
     * Spy on a settlement.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is spying.
     * @param unit The <code>Unit</code> that is spying.
     * @param settlement The <code>Settlement</code> to spy on.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element spySettlement(ServerPlayer serverPlayer, Unit unit,
                                 Settlement settlement) {
        ChangeSet cs = new ChangeSet();

        cs.addSpy(See.only(serverPlayer), settlement);
        unit.setMovesLeft(0);
        cs.addPartial(See.only(serverPlayer), unit, "movesLeft");
        return cs.build(serverPlayer);
    }


    /**
     * Change work location.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that owns the unit.
     * @param unit The <code>Unit</code> to change the work location of.
     * @param workLocation The <code>WorkLocation</code> to change to.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element work(ServerPlayer serverPlayer, Unit unit,
                        WorkLocation workLocation) {
        final Specification spec = getGame().getSpecification();
        ChangeSet cs = new ChangeSet();
        Colony colony = workLocation.getColony();
        colony.getGoodsContainer().saveState();

        if (workLocation instanceof ColonyTile) {
            Tile tile = ((ColonyTile) workLocation).getWorkTile();
            if (tile.getOwningSettlement() != colony) {
                // Claim known free land (because canAdd() succeeded).
                serverPlayer.csClaimLand(tile, colony, 0, cs);
            }
        }

        colony.equipForRole(unit, spec.getDefaultRole(), 0);

        // Check for upgrade.
        UnitType newType = unit.getTypeChange(ChangeType.ENTER_COLONY,
                                              unit.getOwner());
        if (newType != null) unit.changeType(newType);//-vis: safe in colony

        // Change the location.
        // We could avoid updating the whole tile if we knew that this
        // was definitely a move between locations and no student/teacher
        // interaction occurred.
        if (!unit.isInColony()) unit.getColony().getTile().cacheUnseen();//+til
        unit.setLocation(workLocation);//-vis: safe/colony,-til if not in colony
        cs.add(See.perhaps(), colony.getTile());
        // Others can see colony change size
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Loot cargo.
     *
     * Note loser is passed by identifier, as by the time we get here
     * the unit may have been sunk.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that owns the winner.
     * @param winner The <code>Unit</code> that looting.
     * @param loserId The object identifier of the <code>Unit</code>
     *     that is looted.
     * @param loot The <code>Goods</code> to loot.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element lootCargo(ServerPlayer serverPlayer, Unit winner,
                             String loserId, List<Goods> loot) {
        LootSession session = TransactionSession.lookup(LootSession.class,
            winner.getId(), loserId);
        if (session == null) {
            return DOMMessage.clientError("Bogus looting!");
        }
        if (!winner.hasSpaceLeft()) {
            return DOMMessage.clientError("No space to loot to: "
                + winner.getId());
        }

        ChangeSet cs = new ChangeSet();
        List<Goods> available = session.getCapture();
        if (loot == null) { // Initial inquiry
            cs.add(See.only(serverPlayer), ChangePriority.CHANGE_LATE,
                   new LootCargoMessage(winner, loserId, available));
        } else {
            for (Goods g : loot) {
                if (!available.contains(g)) {
                    return DOMMessage.clientError("Invalid loot: " + g);
                }
                available.remove(g);
                if (!winner.canAdd(g)) {
                    return DOMMessage.clientError("Loot failed: " + g);
                }
                winner.add(g);
            }

            // Others can see cargo capacity change.
            session.complete(cs);
            cs.add(See.perhaps(), winner);
            sendToOthers(serverPlayer, cs);
        }
        return cs.build(serverPlayer);
    }


    /**
     * Pay arrears.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that owns the unit.
     * @param type The <code>GoodsType</code> to pay the arrears for.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element payArrears(ServerPlayer serverPlayer, GoodsType type) {
        int arrears = serverPlayer.getArrears(type);
        if (arrears <= 0) {
            return DOMMessage.clientError("No arrears for pay for: "
                + type.getId());
        } else if (!serverPlayer.checkGold(arrears)) {
            return DOMMessage.clientError("Not enough gold to pay arrears for: "
                + type.getId());
        }

        ChangeSet cs = new ChangeSet();
        Market market = serverPlayer.getMarket();
        serverPlayer.modifyGold(-arrears);
        market.setArrears(type, 0);
        cs.addPartial(See.only(serverPlayer), serverPlayer, "gold");
        cs.add(See.only(serverPlayer), market.getMarketData(type));
        // Arrears payment is private.
        return cs.build(serverPlayer);
    }

    /**
     * Equip a unit for a specific role.
     * Currently the unit is either in Europe or in a settlement.
     * Might one day allow the unit to be on a tile co-located with
     * an equipment-bearing wagon.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that owns the unit.
     * @param unit The <code>Unit</code> to equip.
     * @param role The <code>Role</code> to equip for.
     * @param roleCount The role count.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element equipForRole(ServerPlayer serverPlayer, Unit unit,
                                Role role, int roleCount) {
        UnitLocation loc = (unit.isInEurope()) ? serverPlayer.getEurope()
            : unit.getSettlement();
        if (loc == null) {
            return DOMMessage.clientError("Unsuitable equip location for: "
                + unit.getId());
        } else if (!loc.equipForRole(unit, role, roleCount)) {
            return DOMMessage.clientError("Can not build " + role.getId()
                + "." + roleCount + " at " + loc + " for: " + unit.getId());
        }

        ChangeSet cs = new ChangeSet();
        if (loc instanceof Europe) {
            cs.addPartial(See.only(serverPlayer), serverPlayer, "gold");
        } else if (unit.isInColony()) {
            loc.getTile().cacheUnseen();//+til
            unit.setLocation(loc.getTile());//-vis: safe/colony,-til
            cs.add(See.perhaps(), loc.getTile());
        } else { // Settlement tile
            cs.add(See.only(serverPlayer), unit.getTile());
        }
        if (unit.getInitialMovesLeft() != unit.getMovesLeft()) {
            unit.setMovesLeft(0);
        }
        Unit carrier = unit.getCarrier();
        if (carrier != null
            && carrier.getInitialMovesLeft() != carrier.getMovesLeft()
            && carrier.getMovesLeft() != 0) {
            carrier.setMovesLeft(0);
        }
        return cs.build(serverPlayer);
    }

    /**
     * Pay for a building.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that owns the
     *     colony.
     * @param colony The <code>Colony</code> that is building.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element payForBuilding(ServerPlayer serverPlayer, Colony colony) {
        BuildableType build = colony.getCurrentlyBuilding();
        if (build == null) {
            return DOMMessage.clientError("Colony " + colony.getId()
                + " is not building anything!");
        }
        List<AbstractGoods> required = colony.getRequiredGoods(build);
        int price = colony.priceGoodsForBuilding(required);
        if (!serverPlayer.checkGold(price)) {
            return DOMMessage.clientError("Insufficient funds to pay for build.");
        }

        // Save the correct final gold for the player, as we are going to
        // use buy() below, but it deducts the normal uninflated price for
        // the goods being bought.  We restore this correct amount later.
        int savedGold = serverPlayer.modifyGold(-price);
        serverPlayer.modifyGold(price);

        ChangeSet cs = new ChangeSet();
        GoodsContainer container = colony.getGoodsContainer();
        container.saveState();
        for (AbstractGoods ag : required) {
            GoodsType type = ag.getType();
            int amount = ag.getAmount();
            if (type.isStorable()) {
                // TODO: should also check canTrade(type, Access.?)
                if ((amount = serverPlayer.buy(container, type, amount)) < 0) {
                    return DOMMessage.clientError("Can not buy " + amount
                        + " " + type + " for " + build);
                }
                serverPlayer.propagateToEuropeanMarkets(type, -amount, random);
                serverPlayer.csFlushMarket(type, cs);
            } else {
                container.addGoods(type, amount);
            }
        }
        colony.invalidateCache();

        // Nothing to see for others, colony internal.
        serverPlayer.setGold(savedGold);
        cs.addPartial(See.only(serverPlayer), serverPlayer, "gold");
        cs.add(See.only(serverPlayer), container);
        return cs.build(serverPlayer);
    }


    /**
     * Indians making demands of a colony.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is demanding.
     * @param unit The <code>Unit</code> making the demands.
     * @param colony The <code>Colony</code> that is demanded of.
     * @param type The <code>GoodsType</code> being demanded, null
     *     implies gold.
     * @param amount The amount of goods/gold being demanded.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element indianDemand(final ServerPlayer serverPlayer, Unit unit,
                                Colony colony, GoodsType type, int amount) {
        final Game game = getGame();
        final Specification spec = game.getSpecification();
        ServerPlayer victim = (ServerPlayer) colony.getOwner();
        int difficulty = spec.getInteger(GameOptions.NATIVE_DEMANDS);
        ChangeSet cs = new ChangeSet();

        DOMMessage reply = askTimeout(victim,
            new IndianDemandMessage(unit, colony, type, amount));
        boolean result = (reply instanceof IndianDemandMessage)
            ? ((IndianDemandMessage)reply).getResult()
            : false;
        logger.info(serverPlayer.getName() + " unit " + unit
            + " demands " + amount + " " + ((type == null) ? "gold" : type)
            + " from " + colony.getName() + " accepted: " + result);

        IndianDemandMessage message = new IndianDemandMessage(unit, colony,
                                                              type, amount);
        message.setResult(result);
        cs.add(See.only(serverPlayer), ChangePriority.CHANGE_NORMAL, message);
        if (result) {
            if (type == null) {
                victim.modifyGold(-amount);
                serverPlayer.modifyGold(amount);
                cs.addPartial(See.only(victim), victim, "gold");
                //cs.addPartial(See.only(serverPlayer), serverPlayer, "gold");
            } else {
                GoodsContainer colonyContainer = colony.getGoodsContainer();
                colonyContainer.saveState();
                GoodsContainer unitContainer = unit.getGoodsContainer();
                unitContainer.saveState();
                moveGoods(new Goods(game, colony, type, amount), unit);
                cs.add(See.only(victim), colonyContainer);
                //cs.add(See.only(serverPlayer), unitContainer);
            }
            int tension = -(5 - difficulty) * 50;
            ServerIndianSettlement is = (ServerIndianSettlement)
                unit.getHomeIndianSettlement();
            if (is == null) {
                serverPlayer.csModifyTension(victim, tension, cs);
            } else {
                is.csModifyAlarm(victim, tension, true, cs);
            }
        }

        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Train a unit in Europe.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is demanding.
     * @param type The <code>UnitType</code> to train.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element trainUnitInEurope(ServerPlayer serverPlayer, UnitType type) {
        Europe europe = serverPlayer.getEurope();
        if (europe == null) {
            return DOMMessage.clientError("No Europe to train in.");
        }
        int price = europe.getUnitPrice(type);
        if (price <= 0) {
            return DOMMessage.clientError("Bogus price: " + price);
        } else if (!serverPlayer.checkGold(price)) {
            return DOMMessage.clientError("Not enough gold ("
                + serverPlayer.getGold() + " < " + price
                + ") to train " + type);
        }

        Unit unit = new ServerUnit(getGame(), europe, serverPlayer,
                                   type);//-vis: safe, Europe
        unit.setName(serverPlayer.getNameForUnit(type, random));
        serverPlayer.modifyGold(-price);
        ((ServerEurope)europe).increasePrice(type, price);

        // Only visible in Europe
        ChangeSet cs = new ChangeSet();
        cs.addPartial(See.only(serverPlayer), serverPlayer, "gold");
        cs.add(See.only(serverPlayer), europe);
        return cs.build(serverPlayer);
    }


    /**
     * Set build queue.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that owns the colony.
     * @param colony The <code>Colony</code> to set the queue of.
     * @param queue The new build queue.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element setBuildQueue(ServerPlayer serverPlayer, Colony colony,
                                 List<BuildableType> queue) {
        colony.setBuildQueue(queue);
        colony.invalidateCache();

        // Only visible to player.
        ChangeSet cs = new ChangeSet();
        cs.add(See.only(serverPlayer), colony);
        return cs.build(serverPlayer);
    }


    /**
     * Set goods levels.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that owns the colony.
     * @param colony The <code>Colony</code> to set the goods levels in.
     * @param exportData The new <code>ExportData</code>.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element setGoodsLevels(ServerPlayer serverPlayer, Colony colony,
                                  ExportData exportData) {
        colony.setExportData(exportData);
        return new ChangeSet().add(See.only(serverPlayer), colony)
            .build(serverPlayer);
    }


    /**
     * Put outside colony.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that owns the unit.
     * @param unit The <code>Unit</code> to be put out.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element putOutsideColony(ServerPlayer serverPlayer, Unit unit) {
        Tile tile = unit.getTile();
        Colony colony = unit.getColony();
        if (unit.isInColony()) tile.cacheUnseen();//+til
        unit.setLocation(tile);//-vis: safe/colony,-til if in colony

        // Full tile update for the player, the rest get their limited
        // view of the colony so that population changes.
        ChangeSet cs = new ChangeSet();
        cs.add(See.only(serverPlayer), tile);
        cs.add(See.perhaps().except(serverPlayer), colony);
        return cs.build(serverPlayer);
    }


    /**
     * Change work type.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that owns the unit.
     * @param unit The <code>Unit</code> to change the work type of.
     * @param type The new <code>GoodsType</code> to produce.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element changeWorkType(ServerPlayer serverPlayer, Unit unit,
                                  GoodsType type) {
        if (unit.getWorkType() != type) {
            unit.setExperience(0);
            unit.changeWorkType(type);
        }

        // Private update of the colony.
        return new ChangeSet().add(See.only(serverPlayer), unit.getColony())
            .build(serverPlayer);
    }


    /**
     * Change improvement work type.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that owns the unit.
     * @param unit The <code>Unit</code> to change the work type of.
     * @param type The new <code>TileImprovementType</code> to produce.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element changeWorkImprovementType(ServerPlayer serverPlayer,
                                             Unit unit,
                                             TileImprovementType type) {
        Tile tile = unit.getTile();
        TileImprovement improvement = tile.getTileImprovement(type);
        if (improvement == null) { // Create the new improvement.
            improvement = new TileImprovement(getGame(), tile, type);
            tile.add(improvement);
        }

        unit.setWorkImprovement(improvement);
        unit.setState(UnitState.IMPROVING);

        // Private update of the tile.
        return new ChangeSet().add(See.only(serverPlayer), tile)
            .build(serverPlayer);
    }


    /**
     * Change a units state.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that owns the unit.
     * @param unit The <code>Unit</code> to change the state of.
     * @param state The new <code>UnitState</code>.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element changeState(ServerPlayer serverPlayer, Unit unit,
                               UnitState state) {
        ChangeSet cs = new ChangeSet();

        Tile tile = unit.getTile();
        boolean tileDirty = tile != null && tile.getIndianSettlement() != null;
        if (state == UnitState.FORTIFYING && tile != null) {
            ServerColony colony = (tile.getOwningSettlement() instanceof Colony)
                ? (ServerColony) tile.getOwningSettlement()
                : null;
            Player owner = (colony == null) ? null : colony.getOwner();
            if (owner != null
                && owner != unit.getOwner()
                && serverPlayer.getStance(owner) != Stance.ALLIANCE
                && serverPlayer.getStance(owner) != Stance.PEACE) {
                if (colony.isTileInUse(tile)) {
                    colony.csEvictUsers(unit, cs);
                }
                if (serverPlayer.getStance(owner) == Stance.WAR) {
                    tile.changeOwnership(null, null); // Clear owner if at war
                    tileDirty = true;
                }
            }
        }

        unit.setState(state);
        if (tileDirty) {
            cs.add(See.perhaps(), tile);
        } else {
            cs.add(See.perhaps(), (FreeColGameObject)unit.getLocation());
        }

        // Others might be able to see the unit.
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Assign a student to a teacher.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that owns the unit.
     * @param student The student <code>Unit</code>.
     * @param teacher The teacher <code>Unit</code>.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element assignTeacher(ServerPlayer serverPlayer, Unit student,
                                 Unit teacher) {
        Unit oldStudent = teacher.getStudent();
        Unit oldTeacher = student.getTeacher();

        // Only update units that changed their teaching situation.
        ChangeSet cs = new ChangeSet();
        if (oldTeacher != null) {
            oldTeacher.setStudent(null);
            cs.add(See.only(serverPlayer), oldTeacher);
        }
        if (oldStudent != null) {
            oldStudent.setTeacher(null);
            cs.add(See.only(serverPlayer), oldStudent);
        }
        teacher.setStudent(student);
        teacher.changeWorkType(null);
        student.setTeacher(teacher);
        cs.add(See.only(serverPlayer), student, teacher);
        return cs.build(serverPlayer);
    }


    /**
     * Assign a trade route to a unit.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that owns the unit.
     * @param unit The unit <code>Unit</code> to assign to.
     * @param tradeRoute The <code>TradeRoute</code> to assign.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element assignTradeRoute(ServerPlayer serverPlayer, Unit unit,
                                    TradeRoute tradeRoute) {
        // If clearing a trade route and the unit is at sea, set
        // the destination to the next stop.  Otherwise just clear
        // the destination.
        TradeRouteStop stop;
        unit.setDestination((tradeRoute == null && unit.isAtSea()
                && (stop = unit.getStop()) != null) ? stop.getLocation()
            : null);
        unit.setTradeRoute(tradeRoute);
        if (tradeRoute != null) {
            List<TradeRouteStop> stops = tradeRoute.getStops();
            int found = -1;
            for (int i = 0; i < stops.size(); i++) {
                if (unit.getLocation() == stops.get(i).getLocation()) {
                    found = i;
                    break;
                }
            }
            if (found < 0) found = 0;
            unit.setCurrentStop(found);
        }

        // Only visible to the player
        return new ChangeSet().add(See.only(serverPlayer), unit)
            .build(serverPlayer);
    }

    /**
     * Set trade routes for a player.
     *
     * @param serverPlayer The <code>ServerPlayer</code> to set trade
     *    routes for.
     * @param routes The new list of <code>TradeRoute</code>s.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element setTradeRoutes(ServerPlayer serverPlayer,
                                  List<TradeRoute> routes) {
        serverPlayer.setTradeRoutes(routes);
        // Have to update the whole player alas.
        return new ChangeSet().add(See.only(serverPlayer), serverPlayer)
            .build(serverPlayer);
    }

    /**
     * Get a new trade route for a player.
     *
     * @param serverPlayer The <code>ServerPlayer</code> to get a trade
     *    route for.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element getNewTradeRoute(ServerPlayer serverPlayer) {
        List<TradeRoute> routes = serverPlayer.getTradeRoutes();
        TradeRoute route = new TradeRoute(getGame(), 
            serverPlayer.getNewTradeRouteName(), serverPlayer);
        routes.add(route);
        return new ChangeSet().addTradeRoute(serverPlayer, route)
            .build(serverPlayer);
    }


    /**
     * Get a list of abstract REF units for a player.
     *
     * @param serverPlayer The <code>ServerPlayer</code> to query the REF of.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element getREFUnits(ServerPlayer serverPlayer) {
        final Game game = getGame();
        final Specification spec = game.getSpecification();
        List<AbstractUnit> units = new ArrayList<AbstractUnit>();
        final UnitType defaultType = spec.getDefaultUnitType();

        if (serverPlayer.getPlayerType() == PlayerType.COLONIAL) {
            units = serverPlayer.getMonarch().getExpeditionaryForce().getUnits();
        } else {
            ServerPlayer REFPlayer = (ServerPlayer) serverPlayer.getREFPlayer();
            java.util.Map<UnitType, HashMap<String, Integer>> unitHash
                = new HashMap<UnitType, HashMap<String, Integer>>();
            for (Unit unit : REFPlayer.getUnits()) {
                if (unit.isOffensiveUnit()) {
                    UnitType unitType = defaultType;
                    if (unit.getType().getOffence() > 0
                        || unit.hasAbility(Ability.EXPERT_SOLDIER)) {
                        unitType = unit.getType();
                    }
                    HashMap<String, Integer> roleMap = unitHash.get(unitType);
                    if (roleMap == null) {
                        roleMap = new HashMap<String, Integer>();
                    }
                    String roleId = unit.getRole().getId();
                    Integer count = roleMap.get(roleId);
                    roleMap.put(roleId, Integer.valueOf((count == null) ? 1
                            : count.intValue() + 1));
                    unitHash.put(unitType, roleMap);
                }
            }
            for (java.util.Map.Entry<UnitType, HashMap<String, Integer>> typeEntry : unitHash.entrySet()) {
                for (java.util.Map.Entry<String, Integer> roleEntry : typeEntry.getValue().entrySet()) {
                    units.add(new AbstractUnit(typeEntry.getKey(), roleEntry.getKey(), roleEntry.getValue()));
                }
            }
        }

        ChangeSet cs = new ChangeSet();
        cs.addTrivial(See.only(serverPlayer), "getREFUnits",
                      ChangePriority.CHANGE_NORMAL);
        Element reply = cs.build(serverPlayer);
        // TODO: eliminate explicit Element hackery
        for (AbstractUnit unit : units) {
            reply.appendChild(unit.toXMLElement(reply.getOwnerDocument()));
        }
        return reply;
    }


    /**
     * Gets the list of high scores.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is querying.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element getHighScores(ServerPlayer serverPlayer) {
        List<HighScore> scores = HighScore.loadHighScores();
        ChangeSet cs = new ChangeSet();
        cs.addTrivial(See.only(serverPlayer), "getHighScores",
                      ChangePriority.CHANGE_NORMAL);
        Element reply = cs.build(serverPlayer);
        if (scores != null) {
            for (HighScore score : scores) {
                reply.appendChild(score.toXMLElement(reply.getOwnerDocument()));
            }
        }
        return reply;
    }


    /**
     * Chat.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is chatting.
     * @param message The chat message.
     * @param pri A privacy setting, currently a noop.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element chat(ServerPlayer serverPlayer, String message,
                        boolean pri) {
        sendToOthers(serverPlayer,
                     new ChatMessage(serverPlayer, message, false)
                     .toXMLElement());
        return null;
    }

    /**
     * Get the current game statistics.
     *
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element getStatistics(ServerPlayer serverPlayer) {
        // Convert statistics map to a list.
        java.util.Map<String, String> stats = getGame()
            .getStatistics();


        stats.putAll(getFreeColServer().getAIMain().getAIStatistics());


        List<String> all = new ArrayList<String>();
        List<String> keys = new ArrayList<String>(stats.keySet());
        Collections.sort(keys);
        for (String k : keys) {
            all.add(k);
            all.add(stats.get(k));
        }

        // Return as statistics element.
        ChangeSet cs = new ChangeSet();
        cs.addTrivial(See.only(serverPlayer), "statistics",
                      ChangePriority.CHANGE_NORMAL,
                      all.toArray(new String[0]));
        return cs.build(serverPlayer);
    }

    /**
     * Enters revenge mode against those evil AIs.
     *
     * @param serverPlayer The <code>ServerPlayer</code> entering revenge mode.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element enterRevengeMode(ServerPlayer serverPlayer) {
        if (!getFreeColServer().isSinglePlayer()) {
            return DOMMessage.clientError("Can not enter revenge mode,"
                + " as this is not a single player game.");
        }
        Game game = getGame();
        List<UnitType> undeads = game.getSpecification()
            .getUnitTypesWithAbility(Ability.UNDEAD);
        List<UnitType> navalUnits = new ArrayList<UnitType>();
        List<UnitType> landUnits = new ArrayList<UnitType>();
        for (UnitType undead : undeads) {
            if (undead.hasAbility(Ability.NAVAL_UNIT)) {
                navalUnits.add(undead);
            } else if (undead.hasAbility(Ability.MULTIPLE_ATTACKS)) {
                landUnits.add(undead);
            }
        }
        if (navalUnits.isEmpty() || landUnits.isEmpty()) {
            return DOMMessage.clientError("Can not enter revenge mode,"
                + " because we can not find the undead units.");
        }

        ChangeSet cs = new ChangeSet();
        UnitType navalType = navalUnits.get(Utils.randomInt(logger,
                "Choose undead navy", random, navalUnits.size()));
        Tile start = ((Tile)serverPlayer.getEntryLocation())
            .getSafeTile(serverPlayer, random);
        Unit theFlyingDutchman
            = new ServerUnit(game, start, serverPlayer,
                             navalType);//-vis(serverPlayer)
        UnitType landType = landUnits.get(Utils.randomInt(logger,
                "Choose undead army", random, landUnits.size()));
        new ServerUnit(game, theFlyingDutchman, serverPlayer, landType);//-vis
        serverPlayer.setDead(false);
        serverPlayer.changePlayerType(PlayerType.UNDEAD);
        serverPlayer.invalidateCanSeeTiles();//+vis(serverPlayer)

        // No one likes the undead.
        for (Player p : game.getLivePlayers(serverPlayer)) {
            if (serverPlayer.hasContacted(p)) {
                serverPlayer.csChangeStance(Stance.WAR, (ServerPlayer)p,
                                            true, cs);
            }
        }

        // Others can tell something has happened to the player,
        // and possibly see the units.
        cs.add(See.all(), serverPlayer);
        cs.add(See.perhaps(), start);
        sendToOthers(serverPlayer, cs);
        return cs.build(serverPlayer);
    }


    /**
     * Rearrange a colony.
     *
     * @param serverPlayer The <code>ServerPlayer</code> that is querying.
     * @param colony The <code>Colony</code> to rearrange.
     * @param unitChanges A list of <code>UnitChange</code>s to apply.
     * @return An <code>Element</code> encapsulating this action.
     */
    public Element rearrangeColony(ServerPlayer serverPlayer, Colony colony,
                                   List<UnitChange> unitChanges) {
        final Role defaultRole = getGame().getSpecification().getDefaultRole();
        Tile tile = colony.getTile();
        tile.cacheUnseen();//+til

        // Move everyone out of the way and stockpile their equipment.
        for (UnitChange uc : unitChanges) {
            uc.unit.setLocation(tile);//-til
            if (!uc.unit.hasDefaultRole()) {
                colony.equipForRole(uc.unit, defaultRole, 0);
            }
        }

        List<UnitChange> todo = new ArrayList<UnitChange>(unitChanges);
        while (!todo.isEmpty()) {
            UnitChange uc = todo.remove(0);
            if (uc.loc == tile) continue;
            WorkLocation wl = (WorkLocation)uc.loc;
            // Adding to wl can fail, and in the worst case there
            // might be a circular dependency.  If the move can
            // succeed, do it, but if not, retry.
            switch (wl.getNoAddReason(uc.unit)) {
            case NONE:
                uc.unit.setLocation(wl);
                // Fall through
            case ALREADY_PRESENT:
                if (uc.unit.getWorkType() != uc.work) {
                    uc.unit.changeWorkType(uc.work);
                }
                break;
            case CAPACITY_EXCEEDED:
                todo.add(todo.size(), uc);
                break;
            default:
                logger.warning("Bad move for " + uc.unit + " to " + wl);
                break;
            }
        }

        Iterator<UnitChange> uci = unitChanges.iterator();
        while (uci.hasNext()) {
            UnitChange uc = uci.next();
            if (uc.unit.getRole() == uc.role) uci.remove();
        }
        if (!unitChanges.isEmpty()) {
            Collections.sort(unitChanges,
                             RearrangeColonyMessage.roleComparator);
            Collections.reverse(unitChanges);
            for (UnitChange uc : unitChanges) {
                if (uc.role != defaultRole) {
                    if (!colony.equipForRole(uc.unit, uc.role, uc.roleCount)) {
                        // Should not happen if we equip simplest first
                        return DOMMessage.clientError("Failed to equip "
                            + uc.unit.getId() + " for role " + uc.role
                            + " at " + colony);
                    }
                }
            }
        }
        
        // Just update the whole tile, including for other players
        // which might see colony population change.
        return new ChangeSet().add(See.perhaps(), tile).build(serverPlayer);
    }
}
