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

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.io.FreeColXMLWriter;
import net.sf.freecol.common.model.FreeColObject;
import net.sf.freecol.common.model.NationOptions.NationState;
import net.sf.freecol.common.option.BooleanOption;
import net.sf.freecol.common.option.IntegerOption;
import net.sf.freecol.common.option.MapGeneratorOptions;
import net.sf.freecol.common.option.Option;
import net.sf.freecol.common.option.OptionGroup;
import net.sf.freecol.common.util.Utils;

import org.w3c.dom.Element;


/**
 * The main component of the game model.
 */
public class Game extends FreeColGameObject {

    private static final Logger logger = Logger.getLogger(Game.class.getName());

    /**
     * The next available identifier that can be given to a new
     * <code>FreeColGameObject</code>.
     */
    protected int nextId = 1;

    /** Game UUID, persistent in savegame files */
    private UUID uuid = UUID.randomUUID();

    /** The client player name, null in the server. */
    private String clientUserName;

    /** All the players in the game. */
    protected final List<Player> players = new ArrayList<Player>();

    /** A virtual player to use for enemy privateers. */
    private Player unknownEnemy;

    /** The map of the New World. */
    private Map map = null;

    /**
     * The current nation options.  Mainly used to see if a player
     * nation is available.
     */
    private NationOptions nationOptions = null;

    /** The player whose turn it is. */
    protected Player currentPlayer = null;

    /** The current turn. */
    private Turn turn = new Turn(1);

    /** Whether the War of Spanish Succession has already taken place. */
    private boolean spanishSuccession = false;

    /** The cities of Cibola remaining in this game. */
    protected final List<String> citiesOfCibola = new ArrayList<String>();

    // Serialization not required below.

    /** The Specification this game uses. */
    private Specification specification = null;

    /**
     * References to all objects created in this game.
     * Serialization is not needed directly as these must be completely
     * within { players, unknownEnemy, map } which are directly serialized.
     */
    protected HashMap<String, WeakReference<FreeColGameObject>> freeColGameObjects
        = new HashMap<String, WeakReference<FreeColGameObject>>(10000);

    /**
     * The combat model this game uses. At the moment, the only combat
     * model available is the SimpleCombatModel, which strives to
     * implement the combat model of the original game.  However, it is
     * anticipated that other, more complex combat models will be
     * implemented in future.  As soon as that happens, we will also
     * have to make the combat model selectable.
     */
    protected CombatModel combatModel = null;

    /** The number of removed FCGOs that should trigger a cache clean. */
    private static final int REMOVE_GC_THRESHOLD = 64;

    /** The number of FCGOs removed since last cache clean. */
    private int removeCount = 0;

    /**
     * A FreeColGameObjectListener to watch the objects in the game.
     * Usually this is the AIMain instance.
     * TODO: is this better done with a property change listener?
     */
    protected FreeColGameObjectListener freeColGameObjectListener = null;


    /**
     * Constructor used by the ServerGame constructor.
     *
     * @param specification The <code>Specification</code> for this game.
     */
    protected Game(Specification specification) {
        super(null);

        this.specification = specification;
        this.clientUserName = null;
    }

    /**
     * Creates a new <code>Game</code> object from a <code>Element</code>
     * in a DOM-parsed XML-tree.
     *
     * @param element The <code>Element</code> containing the game.
     * @param clientUserName The name of the owner of this view of the game.
     */
    public Game(Element element, String clientUserName) {
        super(null);
     
        this.clientUserName = clientUserName;
        this.combatModel = new SimpleCombatModel();
        readFromXMLElement(element);
        // setId() does not add Games to the freeColGameObjects
        this.setFreeColGameObject(getId(), this);
    }


    /**
     * Get the specification for this game.
     *
     * @return The <code>Specification</code> for this game.
     */
    @Override
    public Specification getSpecification() {
        return specification;
    }

    /**
     * Get the difficulty level of this game.
     *
     * @return An <code>OptionGroup</code> containing the difficulty settings.
     */
    public final OptionGroup getDifficultyOptionGroup() {
        return specification.getDifficultyOptionGroup();
    }

    /**
     * Gets the game options associated with this game.
     *
     * @return An <code>OptionGroup</code> containing the game options.
     */
    public OptionGroup getGameOptions() {
        return specification.getGameOptions();
    }

    /**
     * Sets the game options associated with this game.
     *
     * @param go An <code>OptionGroup</code> containing the game options.
     */
    public void setGameOptions(OptionGroup go) {
        specification.setGameOptions(go);
    }

    /**
     * Gets the map generator options associated with this game.
     *
     * @return An <code>OptionGroup</code> containing the map
     *     generator options.
     */
    public OptionGroup getMapGeneratorOptions() {
        return specification.getMapGeneratorOptions();
    }

    /**
     * Sets the map generator options associated with this game.
     *
     * @param mgo An <code>OptionGroup</code> containing the map
     *     generator options.
     */
    public void setMapGeneratorOptions(OptionGroup mgo) {
        specification.setMapGeneratorOptions(mgo);
    }

    /**
     * Stub for routine only meaningful in the server.
     *
     * @return Nothing.
     * @exception IllegalStateException, unimplemented in the client.
     */
    public String getNextId() {
        throw new IllegalStateException("game.getNextId not implemented");
    }

    /**
     * Gets the <code>FreeColGameObject</code> with the given identifier.
     *
     * @param id The object identifier.
     * @return The game object, or null if not found.
     */
    public FreeColGameObject getFreeColGameObject(String id) {
        if (id != null && !id.isEmpty()) {
            final WeakReference<FreeColGameObject> ro
                = freeColGameObjects.get(id);
            if (ro != null) {
                final FreeColGameObject o = ro.get();
                if (o != null) return o;
                freeColGameObjects.remove(id);
            }
        }
        return null;
    }

    /**
     * Gets the <code>FreeColGameObject</code> with the specified
     * identifier and class.
     *
     * @param id The object identifier.
     * @param returnClass The expected class of the object.
     * @return The game object, or null if not found.
     */
    public <T extends FreeColGameObject> T getFreeColGameObject(String id,
        Class<T> returnClass) {
        FreeColGameObject fcgo = getFreeColGameObject(id);
        try {
            return returnClass.cast(fcgo);
        } catch (ClassCastException e) {
            return null;
        }
    }

    /**
     * Registers a new <code>FreeColGameObject</code> with a given
     * identifier.
     *
     * @param id The object identifier.
     * @param fcgo The <code>FreeColGameObject</code> to add to this
     *     <code>Game</code>.
     * @exception IllegalArgumentException If either the identifier or
     *     object are null.
     */
    public void setFreeColGameObject(String id, FreeColGameObject fcgo) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Null/empty id.");
        } else if (fcgo == null) {
            throw new IllegalArgumentException("Null FreeColGameObject.");
        }

        final WeakReference<FreeColGameObject> wr
            = new WeakReference<FreeColGameObject>(fcgo);
        final FreeColGameObject old = getFreeColGameObject(id);
        if (old != null) {
            throw new IllegalArgumentException("Replacing FreeColGameObject "
                + id + " : " + old.getClass()
                + " with " + fcgo.getId() + " : " + fcgo.getClass());
        }
        freeColGameObjects.put(id, wr);

        notifySetFreeColGameObject(id, fcgo);
    }

    /**
     * Removes the <code>FreeColGameObject</code> with the specified
     * identifier.
     *
     * @param id The object identifier.
     * @exception IllegalArgumentException If the identifier is null or empty.
     */
    public void removeFreeColGameObject(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Null/empty identifier.");
        }

        freeColGameObjects.remove(id);
        notifyRemoveFreeColGameObject(id);

        // Garbage collect the FCGOs if enough have been removed.
        if (++removeCount > REMOVE_GC_THRESHOLD) {
            Iterator<FreeColGameObject> i = getFreeColGameObjectIterator();
            while (i.hasNext()) i.next();
            removeCount = 0;
            System.gc(); // Probably a good opportunity.
        }
    }

    /**
     * Convenience wrapper to find a location (which is an interface,
     * precluding using the typed version of getFreeColGameObject())
     * by identifier.
     *
     * Use this routine when the object should already be present in the game.
     *
     * @param id The object identifier.
     * @return The <code>Location</code> if any.
     */
    public Location findFreeColLocation(String id) {
        FreeColGameObject fcgo = getFreeColGameObject(id);
        return (fcgo instanceof Location) ? (Location)fcgo : null;
    }

    /**
     * Convenience wrapper to find or make a location (which is an
     * interface, precluding using the typed version of
     * getFreeColGameObject()) by identifier.
     *
     * Use this routine when the object may not necessarily already be
     * present in the game, but is expected to be defined eventually.
     *
     * @param id The object identifier.
     * @return The <code>Location</code> if any.
     */
    public Location makeFreeColLocation(String id) {
        FreeColGameObject fcgo = getFreeColGameObject(id);
        if (fcgo != null) {
            if (fcgo instanceof Location) return (Location)fcgo;
            logger.warning("Not a location: " + id);
            return null;
        }

        int idx = id.indexOf(':');
        final String tag = (idx >= 0) ? id.substring(0, id.indexOf(':'))
            : id;
        if ("newWorld".equals(tag)) {
            // do nothing
        } else if (Building.getXMLElementTagName().equals(tag)) {
            return new Building(this, id);
        } else if (Colony.getXMLElementTagName().equals(tag)) {
            return new Colony(this, id);
        } else if (ColonyTile.getXMLElementTagName().equals(tag)) {
            return new ColonyTile(this, id);
        } else if (Europe.getXMLElementTagName().equals(tag)) {
            return new Europe(this, id);
        } else if (HighSeas.getXMLElementTagName().equals(tag)) {
            return new HighSeas(this, id);
        } else if (IndianSettlement.getXMLElementTagName().equals(tag)) {
            return new IndianSettlement(this, id);
        } else if (Map.getXMLElementTagName().equals(tag)) {
            return new Map(this, id);
        } else if (Tile.getXMLElementTagName().equals(tag)) {
            return new Tile(this, id);
        } else if (Unit.getXMLElementTagName().equals(tag)) {
            return new Unit(this, id);
        } else {
            throw new IllegalArgumentException("Not a FCGO: " + id);
        }
        return null;
    }

    /**
     * Gets an <code>Iterator</code> over every registered
     * <code>FreeColGameObject</code>.
     *
     * This <code>Iterator</code> should be iterated at least once in
     * a while since it cleans the <code>FreeColGameObject</code>
     * cache.  Very few routines call this any more, so there is a
     * thresholded call in removeFreeColGameObject to ensure the cache
     * is still cleaned.  Reconsider this if the situation changes.
     *
     * @return An <code>Iterator</code> containing every registered
     *     <code>FreeColGameObject</code>.
     * @see #setFreeColGameObject
     */
    public Iterator<FreeColGameObject> getFreeColGameObjectIterator() {
        return new Iterator<FreeColGameObject>() {
            final Iterator<Entry<String, WeakReference<FreeColGameObject>>> it
                = freeColGameObjects.entrySet().iterator();
            FreeColGameObject nextValue = null;
            String lastId = null;

            public boolean hasNext() {
                while (nextValue == null) {
                    if (!it.hasNext()) return false;

                    final Entry<String, WeakReference<FreeColGameObject>> entry
                        = it.next();
                    final WeakReference<FreeColGameObject> wr
                        = entry.getValue();
                    final FreeColGameObject o = wr.get();
                    if (o == null) {
                        lastId = null;
                        notifyRemoveFreeColGameObject(entry.getKey());
                        it.remove();
                    } else {
                        lastId = o.getId();
                        nextValue = o;
                    }
                }
                return true;
            }

            public FreeColGameObject next() {
                hasNext();
                final FreeColGameObject o = nextValue;
                nextValue = null;
                return o;
            }

            public void remove() {
                if (lastId != null) notifyRemoveFreeColGameObject(lastId);
                it.remove();
            }
        };
    }

    /**
     * Gets the unique identifier for this game. 
     * A game UUID persists in save game files.
     *
     * @return The game <code>UUID</code>.
     */
    public UUID getUUID () {
       return uuid;
    }

    /**
     * Get all the players in the game.
     *
     * @return The list of <code>Player</code>s.
     */
    public List<Player> getPlayers() {
        return players;
    }

    /**
     * Get live players in the game.
     *
     * @param other An optional <code>Player</code> to omit.
     * @return The list of live <code>Player</code>s.
     */
    public List<Player> getLivePlayers(Player other) {
        List<Player> result = new ArrayList<Player>();
        for (Player player : players) {
            if (player.isUnknownEnemy() || player.isDead()
                || player == other) continue;
            result.add(player);
        }
        return result;
    }

    /**
     * Get the live European players in this game.
     *
     * @param other An optional <code>Player</code> to omit.
     * @return A list of live European <code>Player</code>s in this game.
     */
    public List<Player> getLiveEuropeanPlayers(Player other) {
        List<Player> result = new ArrayList<Player>();
        for (Player player : players) {
            if (player.isUnknownEnemy() || player.isDead()
                || !player.isEuropean() || player == other) continue;
            result.add(player);
        }
        return result;
    }

    /**
     * Get the live native players in this game.
     *
     * @param other An optional <code>Player</code> to omit.
     * @return A list of live native <code>Player</code>s in this game.
     */
    public List<Player> getLiveNativePlayers(Player other) {
        List<Player> result = new ArrayList<Player>();
        for (Player player : players) {
            if (player.isUnknownEnemy() || player.isDead()
                || !player.isIndian() || player == other) continue;
            result.add(player);
        }
        return result;
    }

    /**
     * Get a <code>Player</code> identified by its nation identifier.
     *
     * @param nationId The nation identifier to search for.
     * @return The <code>Player</code> of the given nation, or null if
     *     not found.
     */
    public Player getPlayer(String nationId) {
        for (Player player : players) {
            if (player.getNationId().equals(nationId)) return player;
        }
        return null;
    }

    /**
     * Gets the next current player.
     *
     * @return The <code>Player</code> whose turn follows the current player.
     */
    public Player getNextPlayer() {
        return getPlayerAfter(currentPlayer);
    }

    /**
     * Gets the live player after the given player.
     *
     * @param beforePlayer The <code>Player</code> before the
     *     <code>Player</code> to be returned.
     * @return The <code>Player</code> after the <code>beforePlayer</code>
     *     in the list which determines the order each player becomes the
     *     current player.
     * @see #getNextPlayer
     */
    public Player getPlayerAfter(Player beforePlayer) {
        if (players.isEmpty()) return null;

        final int start = players.indexOf(beforePlayer);
        int index = start;
        do {
            if (++index >= players.size()) index = 0;
            Player player = players.get(index);
            if (!player.isUnknownEnemy() && !player.isDead()) return player;
        } while (index != start);
        return null;
    }

    /**
     * Get the first player in this game.
     *
     * @return The first player, or null if none present.
     */
    public Player getFirstPlayer() {
        return (players.isEmpty()) ? null : players.get(0);
    }

    /**
     * Gets a player specified by a name.
     *
     * @param name The name identifying the <code>Player</code>.
     * @return The <code>Player</code> or null if none found.
     */
    public Player getPlayerByName(String name) {
        for (Player player : players) {
            if (player.getName().equals(name)) return player;
        }
        return null;
    }

    /**
     * Checks if the specified player name is in use.
     *
     * @param name The name to check.
     * @return True if the name is already in use.
     */
    public boolean playerNameInUse(String name) {
        for (Player player : players) {
            if (player.getName().equals(name)) return true;
        }
        return false;
    }

    /**
     * Adds the specified player to the game.
     *
     * @param player The <code>Player</code> to add.
     * @return True if the player was added.
     */
    public boolean addPlayer(Player player) {
        if (player.isAI() || canAddNewPlayer()) {
            players.add(player);
            Nation nation = getSpecification().getNation(player.getNationId());
            nationOptions.getNations().put(nation, NationState.NOT_AVAILABLE);
            if (currentPlayer == null) currentPlayer = player;
            return true;
        }
        logger.warning("Game already full, but tried to add: "
            + player.getName());
        return false;
    }

    /**
     * Removes the specified player from the game.
     *
     * @param player The <code>Player</code> to remove.
     * @return True if the player was removed.
     */
    public boolean removePlayer(Player player) {
        Player newCurrent = (currentPlayer != player) ? null
            : getPlayerAfter(currentPlayer);

        if (!players.remove(player)) return false;

        Nation nation = getSpecification().getNation(player.getNationId());
        nationOptions.getNations().put(nation, NationState.AVAILABLE);
        player.dispose();

        if (newCurrent != null) currentPlayer = newCurrent;
        return true;
    }

    /**
     * Gets the unknown enemy player, which is used for privateers.
     *
     * @return The unknown enemy <code>Player</code>.
     */
    public Player getUnknownEnemy() {
        return unknownEnemy;
    }

    /**
     * Sets the unknown enemy player.
     *
     * @param player The <code>Player</code> to serve as the unknown enemy.
     */
    public void setUnknownEnemy(Player player) {
        this.unknownEnemy = player;
    }

    /**
     * Get the client player this thread is operating for.  If in the server
     * there will be none.
     *
     * @return The client <code>Player</code>.
     */
    public Player getClientPlayer() {
        return (clientUserName == null) ? null
            : getPlayerByName(clientUserName);
    }

    /**
     * Are we executing in a client?
     *
     * @return True in a client.
     */
    public boolean isInClient() {
        return clientUserName != null;
    }

    /**
     * Are we executing in the server?
     *
     * @return True in the server.
     */
    public boolean isInServer() {
        return clientUserName == null;
    }

    /**
     * Gets the current player.
     *
     * @return The current player.
     */
    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    /**
     * Sets the current player.
     *
     * @param newCurrentPlayer The new current <code>Player</code>.
     */
    public void setCurrentPlayer(Player newCurrentPlayer) {
        if (newCurrentPlayer == null) {
            logger.info("Current player set to 'null'.");
        }
        currentPlayer = newCurrentPlayer;
    }

    /**
     * Gets the map that is being used in this game.
     *
     * @return The game <code>Map</code>.
     */
    public Map getMap() {
        return map;
    }

    /**
     * Sets the game map.
     *
     * @param newMap The new <code>Map</code> to use.
     */
    public void setMap(Map newMap) {
        if (this.map != newMap) {
            for (Player player : getLivePlayers(null)) {
                if (player.getHighSeas() != null) {
                    player.getHighSeas().removeDestination(this.map);
                    player.getHighSeas().addDestination(newMap);
                }
            }
        }
        this.map = newMap;
    }

    /**
     * Get the current nation options.
     *
     * @return The current <code>NationOptions</code>.
     */
    public final NationOptions getNationOptions() {
        return nationOptions;
    }

    /**
     * Set the current ntion options.
     *
     * @param newNationOptions The new <code>NationOptions<code> value.
     */
    public final void setNationOptions(final NationOptions newNationOptions) {
        this.nationOptions = newNationOptions;
    }

    /**
     * Find an available (i.e. vacant) nation.
     *
     * @return A vacant <code>Nation</code> or null if none found.
     */
    public Nation getVacantNation() {
        for (Entry<Nation, NationState> entry
                 : nationOptions.getNations().entrySet()) {
            if (entry.getValue() == NationState.AVAILABLE) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Get the currently available nations.
     *
     * @return A list of available <code>Nation</code>s.
     */
    public final List<Nation> getVacantNations() {
        List<Nation> result = new ArrayList<Nation>();
        for (Entry<Nation, NationState> entry
                 : nationOptions.getNations().entrySet()) {
            if (entry.getValue() == NationState.AVAILABLE) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Can a new player be added to this game?
     *
     * @return True if a new player can be added.
     */
    public boolean canAddNewPlayer() {
        return getVacantNation() != null;
    }

    /**
     * Gets the current turn in this game.
     *
     * @return The current <code>Turn</code>.
     */
    public Turn getTurn() {
        return turn;
    }

    /**
     * Sets the current turn in this game.
     *
     * @param newTurn The new <code>Turn</code> to set.
     */
    public void setTurn(Turn newTurn) {
        turn = newTurn;
    }

    /**
     * Get the combat model in this game.
     *
     * @return The <code>CombatModel</code>.
     */
    public final CombatModel getCombatModel() {
        return combatModel;
    }

    /**
     * Set the game combat model.
     *
     * @param newCombatModel The new <code>CombatModel</code> value.
     */
    public final void setCombatModel(final CombatModel newCombatModel) {
        this.combatModel = newCombatModel;
    }

    /**
     * Has the Spanish Succession event occured?
     *
     * @return True if the Spanish Succession has occurred.
     */
    public final boolean getSpanishSuccession() {
        return spanishSuccession;
    }

    /**
     * Set the Spanish Succession value.
     *
     * @param spanishSuccession The new Spanish Succession value.
     */
    public final void setSpanishSuccession(final boolean spanishSuccession) {
        this.spanishSuccession = spanishSuccession;
    }

    /**
     * Get the next name for a city of Cibola, removing it from the
     * list of available names.
     *
     * @return The next name key for a city of Cibola, or null if none
     *     available.
     */
    public String nextCityOfCibola() {
        return (citiesOfCibola.isEmpty()) ? null : citiesOfCibola.remove(0);
    }


    /**
     * Sets the <code>FreeColGameObjectListener</code> attached to this game.
     *
     * @param fcgol The new <code>FreeColGameObjectListener</code>.
     */
    public void setFreeColGameObjectListener(FreeColGameObjectListener fcgol) {
        freeColGameObjectListener = fcgol;
    }

    /**
     * Notify a listener (if any) of a new game object.
     *
     * @param id The object identifier.
     * @param fcgo The new <code>FreeColGameObject</code>.
     */
    public void notifySetFreeColGameObject(String id, FreeColGameObject fcgo) {
        if (freeColGameObjectListener != null) {
            freeColGameObjectListener.setFreeColGameObject(id, fcgo);
        }
    }

    /**
     * Notify a listener (if any) of that a game object has gone.
     *
     * @param id The object identifier.
     */
    public void notifyRemoveFreeColGameObject(String id) {
        if (freeColGameObjectListener != null) {
            freeColGameObjectListener.removeFreeColGameObject(id);
        }
    }

    /**
     * Notify a listener (if any) of that a game object has changed owner.
     *
     * @param source The <code>FreeColGameObject</code> that changed owner.
     * @param oldOwner The old owning <code>Player</code>.
     * @param newOwner The new owning <code>Player</code>.
     */
    public void notifyOwnerChanged(FreeColGameObject source,
                                   Player oldOwner, Player newOwner) {
        if (freeColGameObjectListener != null) {
            freeColGameObjectListener.ownerChanged(source, oldOwner, newOwner);
        }
    }

    /**
     * Maintain the player containers for certain ownables.
     * Mainly useful in the client, informing the player that it has
     * gained or lost an ownable.
     *
     * @param o The <code>Ownable</code> that may have changed.
     * @param oldOwner The previous (possible unchanged) owning
     *     <code>Player</code>.
     */
    public void checkOwners(Ownable o, Player oldOwner) {
        Player newOwner = o.getOwner();
        if (oldOwner == newOwner) return;

        if (oldOwner != null && oldOwner.removeOwnable(o)) {
            oldOwner.invalidateCanSeeTiles();//+vis
        }
        if (newOwner != null && newOwner.addOwnable(o)) {
            newOwner.invalidateCanSeeTiles();//+vis
        }
    }


    // Miscellaneous utilities.

    /**
     * Checks if all players are ready to launch.
     *
     * @return True if all players are ready to launch.
     */
    public boolean allPlayersReadyToLaunch() {
        for (Player player : getLivePlayers(null)) {
            if (!player.isReady()) return false;
        }
        return true;
    }

    /**
     * Finds a settlement by name.
     *
     * @param name The name of the <code>Settlement</code>.
     * @return The <code>Settlement</code> found, or <code>null</code>
     *     if there is no known <code>Settlement</code> with the
     *     specified name (the settlement might not be visible to a client).
     */
    public Settlement getSettlement(String name) {
        for (Player p : getLivePlayers(null)) {
            for (Settlement s : p.getSettlements()) {
                if (name.equals(s.getName())) return s;
            }
        }
        return null;
    }

    /**
     * Helper function to get the source object of a message in this game.
     *
     * @param message The <code>ModelMessage</code> to find the object in.
     * @return The source object.
     */
    public FreeColGameObject getMessageSource(ModelMessage message) {
        return getFreeColGameObject(message.getSourceId());
    }

    /**
     * Helper function to get the object to display with a message in
     * this game.
     *
     * @param message The <code>ModelMessage</code> to find the object in.
     * @return An object to display.
     */
    public FreeColObject getMessageDisplay(ModelMessage message) {
        String id = message.getDisplayId();
        if (id == null) id = message.getSourceId();
        FreeColObject o = getFreeColGameObject(id);
        if (o == null) {
            try {
                o = getSpecification().findType(id);
            } catch (Exception e) {
                o = null; // Ignore
            }
        }
        return o;
    }

    /**
     * Gets the statistics of this game.
     *
     * @return A <code>Map</code> of the statistics.
     */
    public java.util.Map<String, String> getStatistics() {
        java.util.Map<String, String> stats = new HashMap<String, String>();

        // Memory
        System.gc();
        long free = Runtime.getRuntime().freeMemory()/(1024*1024);
        long total = Runtime.getRuntime().totalMemory()/(1024*1024);
        long max = Runtime.getRuntime().maxMemory()/(1024*1024);
        stats.put("freeMemory", Long.toString(free));
        stats.put("totalMemory", Long.toString(total));
        stats.put("maxMemory", Long.toString(max));

        // Game objects
        java.util.Map<String, Long> objStats = new HashMap<String, Long>();
        long disposed = 0;
        Iterator<FreeColGameObject> iter = getFreeColGameObjectIterator();
        while (iter.hasNext()) {
            FreeColGameObject obj = iter.next();
            String className = obj.getClass().getSimpleName();
            if (objStats.containsKey(className)) {
                Long count = objStats.get(className);
                count++;
                objStats.put(className, count);
            } else {
                Long count = new Long(1);
                objStats.put(className, count);
            }
            if (obj.isDisposed()) disposed++;
        }
        stats.put("disposed", Long.toString(disposed));
        for (Entry<String, Long> entry : objStats.entrySet()) {
            stats.put(entry.getKey(), Long.toString(entry.getValue()));
        }

        return stats;
    }


    /**
     * Instantiate an uninitialized FreeColObject class within this game.
     *
     * @param returnClass The required FreeColObject class.
     * @return The new uninitialized object, or null on error.
     * @exception IOException on error.
     */
    public <T extends FreeColObject> T newInstance(Class<T> returnClass) throws IOException {
        try {
            Constructor<T> c = returnClass.getConstructor(Game.class,
                                                          String.class);
            return c.newInstance(this, (String)null);

        } catch (NoSuchMethodException nsme) { // Specific to getConstructor
            throw new IOException(nsme);
        } catch (Exception e) { // Handles multiple fails from newInstance
            throw new IOException(e);
        }
    }

    /**
     * Unserialize from XML to a FreeColObject in this game.
     *
     * @param xml The xml serialized version of an object.
     * @param returnClass The required object class.
     * @return The unserialized object.
     * @exception XMLStreamException if there are any problems reading from
     *     the stream.
     */
    public <T extends FreeColObject> T unserialize(String xml,
                                                   Class<T> returnClass) throws XMLStreamException {
        try {
            FreeColXMLReader xr = new FreeColXMLReader(new StringReader(xml));
            xr.nextTag();
            T ret = newInstance(returnClass);
            ret.readFromXML(xr);
            return ret;

        } catch (IOException ioe) {
            throw new XMLStreamException(ioe);
        }
    }


    // Override FreeColGameObject

    /**
     * {@inheritDoc}
     */
    @Override
    public int checkIntegrity(boolean fix) {
        int result = super.checkIntegrity(fix);
        Iterator<FreeColGameObject> iterator = getFreeColGameObjectIterator();
        while (iterator.hasNext()) {
            FreeColGameObject fcgo = iterator.next();
            if (fcgo.isUninitialized()) {
                if (fix) {
                    logger.warning("Uninitialized object: " + fcgo.getId()
                        + " (" + Utils.lastPart(fcgo.getClass().getName(), ".")
                        + "), dropping.");
                    iterator.remove();
                    result = 0;
                } else {
                    logger.warning("Uninitialized object: " + fcgo.getId()
                        + " (" + Utils.lastPart(fcgo.getClass().getName(), ".")
                        + ").");
                    result = -1;
                }
            }
        }

        Map map = getMap();
        if (map != null) {
            result = Math.min(result, getMap().checkIntegrity(fix));
        }
        for (Player player : getPlayers()) {
            result = Math.min(result, player.checkIntegrity(fix));
        }
        return result;
    }


    // Override Object
    //
    // Two games are not the same just because they have the same
    // identifier, but to avoid having to check everything in the Game
    // just insist on object equality for the equals() test, and
    // accept the basic id-based hashCode().

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Utils.hashCode(getId());
    }


    // Serialization

    private static final String CIBOLA_TAG = "cibola";
    private static final String CURRENT_PLAYER_TAG = "currentPlayer";
    private static final String NEXT_ID_TAG = "nextId";
    private static final String SPANISH_SUCCESSION_TAG = "spanishSuccession";
    private static final String TURN_TAG = "turn";
    private static final String UUID_TAG = "UUID";
    // @compat 0.10.x
    private static final String OLD_NEXT_ID_TAG = "nextID";
    // end @compat


    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeAttributes(FreeColXMLWriter xw) throws XMLStreamException {
        super.writeAttributes(xw);

        if (xw.validForSave()) {

            xw.writeAttribute(NEXT_ID_TAG, nextId);
        }

        xw.writeAttribute(UUID_TAG, getUUID());

        xw.writeAttribute(TURN_TAG, getTurn().getNumber());

        xw.writeAttribute(SPANISH_SUCCESSION_TAG, spanishSuccession);

        if (currentPlayer != null) {
            xw.writeAttribute(CURRENT_PLAYER_TAG, currentPlayer);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeChildren(FreeColXMLWriter xw) throws XMLStreamException {
        super.writeChildren(xw);

        specification.toXML(xw);

        for (String cityName : citiesOfCibola) { // Preserve existing order
            xw.writeStartElement(CIBOLA_TAG);

            xw.writeAttribute(ID_ATTRIBUTE_TAG, cityName);

            xw.writeEndElement();
        }

        nationOptions.toXML(xw);

        List<Player> players = getSortedCopy(getPlayers());
        Player unknown = getUnknownEnemy();
        if (unknown != null) players.add(unknown);
        for (Player p : players) p.toXML(xw);

        if (map != null) map.toXML(xw);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readAttributes(FreeColXMLReader xr) throws XMLStreamException {
        super.readAttributes(xr);

        nextId = xr.getAttribute(NEXT_ID_TAG, -1);
        // @compat 0.10.x
        if (nextId < 0) nextId = xr.getAttribute(OLD_NEXT_ID_TAG, 0);
        // end @compat

        String str = xr.getAttribute(UUID_TAG, (String)null);
        if (str != null) {
            try {
                UUID u = UUID.fromString(str);
                this.uuid = u;
            } catch (IllegalArgumentException iae) {
                ;// Preserve existing uuid
            }
        }

        turn = new Turn(xr.getAttribute(TURN_TAG, 1));

        spanishSuccession = xr.getAttribute(SPANISH_SUCCESSION_TAG, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readChildren(FreeColXMLReader xr) throws XMLStreamException {
        // Clear containers.
        citiesOfCibola.clear();
        players.clear();
        unknownEnemy = null;

        // Special case for the current player.  Defer lookup of the
        // current player tag until we read the children, because that
        // is where the players are defined.
        String current = xr.getAttribute(CURRENT_PLAYER_TAG, (String)null);

        super.readChildren(xr);

        // @compat 0.10.7
        for (Player player : getLiveEuropeanPlayers(null)) {
            for (Unit unit : player.getUnits()) {
                if (unit.isInColony()) {
                    unit.getWorkLocation().updateProductionType();
                }
            }
        }
        // end @compat

        currentPlayer = (current == null) ? null
            : getFreeColGameObject(current, Player.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readChild(FreeColXMLReader xr) throws XMLStreamException {
        final Game game = getGame();
        final String tag = xr.getLocalName();
        logger.finest("Found game tag " + tag + " id=" + xr.readId());

        if (CIBOLA_TAG.equals(tag)) {
            citiesOfCibola.add(xr.readId());
            xr.closeTag(CIBOLA_TAG);

        } else if (Map.getXMLElementTagName().equals(tag)) {
            map = xr.readFreeColGameObject(game, Map.class);

        } else if (NationOptions.getXMLElementTagName().equals(tag)) {
            nationOptions = new NationOptions(xr, specification);

        } else if (OptionGroup.getXMLElementTagName().equals(tag)) {
            specification.applyDifficultyLevel(new OptionGroup(xr, specification));

        } else if (Player.getXMLElementTagName().equals(tag)) {
            Player player = xr.readFreeColGameObject(game, Player.class);
            if (player.isUnknownEnemy()) {
                setUnknownEnemy(player);
            } else {
                players.add(player);
            }

        } else if (Specification.getXMLElementTagName().equals(tag)) {
            logger.info(((specification == null) ? "Loading" : "Reloading")
                + " specification.");
            specification = new Specification(xr);

        } else {
            super.readChild(xr);
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getXMLTagName() { return getXMLElementTagName(); }

    /**
     * Gets the tag name of the root element representing this object.
     *
     * @return "game".
     */
    public static String getXMLElementTagName() {
        return "game";
    }
}
