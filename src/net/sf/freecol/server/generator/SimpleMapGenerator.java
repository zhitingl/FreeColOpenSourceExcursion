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

package net.sf.freecol.server.generator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.client.gui.i18n.Messages;
import net.sf.freecol.common.FreeColException;
import net.sf.freecol.common.debug.FreeColDebugger;
import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.io.FreeColSavegameFile;
import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.AbstractUnit;
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.BuildingType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ColonyTile;
import net.sf.freecol.common.model.EuropeanNationType;
import net.sf.freecol.common.model.FreeColObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GameOptions;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsContainer;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.IndianNationType;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.LandMap;
import net.sf.freecol.common.model.LostCityRumour;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Map.Direction;
import net.sf.freecol.common.model.Map.Position;
import net.sf.freecol.common.model.Nation;
import net.sf.freecol.common.model.NationType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileImprovement;
import net.sf.freecol.common.model.TileImprovementType;
import net.sf.freecol.common.model.TileItemContainer;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.option.FileOption;
import net.sf.freecol.common.option.IntegerOption;
import net.sf.freecol.common.option.MapGeneratorOptions;
import net.sf.freecol.common.option.OptionGroup;
import net.sf.freecol.common.util.RandomChoice;
import net.sf.freecol.common.util.Utils;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.model.ServerBuilding;
import net.sf.freecol.server.model.ServerColony;
import net.sf.freecol.server.model.ServerIndianSettlement;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerRegion;
import net.sf.freecol.server.model.ServerUnit;


/**
 * Creates random maps and sets the starting locations for the players.
 *
 * No visibility implications here as this all happens pre-game,
 * so no +/-vis annotations are needed.
 */
public class SimpleMapGenerator implements MapGenerator {

    private static final Logger logger = Logger.getLogger(SimpleMapGenerator.class.getName());

    /**
     * To avoid starting positions too close to the poles, this
     * percentage indicating how much of the half map close to the
     * pole cannot be spawned on.
     */
    private static final float MIN_DISTANCE_FROM_POLE = 0.30f;

    private static class Territory {
        public ServerRegion region;
        public Tile tile;
        public Player player;
        public int numberOfSettlements;

        public Territory(Player player, Tile tile) {
            this.player = player;
            this.tile = tile;
        }

        public Territory(Player player, ServerRegion region) {
            this.player = player;
            this.region = region;
        }

        public Tile getCenterTile(Map map) {
            if (tile != null) return tile;
            int[] xy = region.getCenter();
            return map.getTile(xy[0], xy[1]);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return player + " territory at " + region;
        }
    }

    private final Random random;
    private final Specification specification;

    private TerrainGenerator terrainGenerator = null;


    /**
     * Creates a <code>MapGenerator</code>
     *
     * @param random The <code>Random</code> number source to use.
     * @param specification The <code>Specification</code> to refer to.
     * @see #createMap
     */
    public SimpleMapGenerator(Random random, Specification specification) {
        this.random = random;
        this.specification = specification;
        initialize();
    }


    /**
     * Initialize from the specification.
     *
     * May be called multiple times, as map generator options change.
     */
    private void initialize() {
        final OptionGroup mgo = getMapGeneratorOptions();
        this.terrainGenerator = new TerrainGenerator(mgo, random);
    }
        
    /**
     * Gets the approximate number of land tiles.
     *
     * @return The approximate number of land tiles
     */
    private int getApproximateLandCount() {
        final OptionGroup mgo = getMapGeneratorOptions();
        return mgo.getInteger(MapGeneratorOptions.MAP_WIDTH)
            * mgo.getInteger(MapGeneratorOptions.MAP_HEIGHT)
            * mgo.getInteger(MapGeneratorOptions.LAND_MASS) / 100;
    }

    /**
     * Make lost city rumours on the given map.
     *
     * The number of rumours depends on the map size.
     *
     * @param map The <code>Map</code> to use.
     * @param importGame An optional <code>Game</code> to load lost city
     *     rumours from.
     */
    private void makeLostCityRumours(Map map, Game importGame) {
        final OptionGroup mgo = getMapGeneratorOptions();
        final boolean importRumours = mgo.getBoolean(MapGeneratorOptions.IMPORT_RUMOURS);
        if (importGame != null && importRumours) {
            int nLCRs = 0;
            for (Tile importTile : importGame.getMap().getAllTiles()) {
                LostCityRumour rumour = importTile.getLostCityRumour();
                // no rumor
                if (rumour == null) continue;
                int x = importTile.getX();
                int y = importTile.getY();
                if (map.isValid(x, y)) {
                    final Tile t = map.getTile(x, y);
                    rumour.setLocation(t);
                    t.addLostCityRumour(rumour);
                    nLCRs++;
                }
            }
            if (nLCRs > 0) {
                logger.info("Imported " + nLCRs + " lost city rumours.");
                return;
            }
            // Otherwise fall through and create them
        }

        final int rumourNumber = mgo.getInteger(MapGeneratorOptions.RUMOUR_NUMBER);
        int number = getApproximateLandCount() / rumourNumber;
        int counter = 0;

        // TODO: Remove temporary fix:
        if (importGame != null) {
            number = map.getWidth() * map.getHeight() * 25 / (100 * 35);
        }
        // END TODO

        for (int i = 0; i < number; i++) {
            for (int tries=0; tries<100; tries++) {
                Tile t = map.getRandomLandTile(random);
                if (t.isPolar()) continue; // No polar lost cities
                if (t.isLand() && !t.hasLostCityRumour()
                    && !t.hasSettlement() && t.getUnitCount() == 0) {
                    LostCityRumour r = new LostCityRumour(t.getGame(), t);
                    if (r.chooseType(null, random)
                        == LostCityRumour.RumourType.MOUNDS
                        && t.getOwningSettlement() != null) {
                        r.setType(LostCityRumour.RumourType.MOUNDS);
                    }
                    t.addLostCityRumour(r);
                    counter++;
                    break;
                }
            }
        }
        logger.info("Created " + counter
            + " lost city rumours of maximum " + number + ".");
    }

    private boolean importIndianSettlements(Map map, Game importGame) {
        final Game game = map.getGame();
        final Specification spec = game.getSpecification();
        int nSettlements = 0;

        for (Player player : importGame.getLiveNativePlayers(null)) {
            Player indian = game.getPlayer(player.getNationId());
            if (indian == null) {
                Nation nation = spec.getNation(player.getNationId());
                if (nation == null) {
                    logger.warning("Native nation "
                        + player.getNationId() + " not found in spec.");
                    continue;
                }
                String name = Messages.message(nation.getRulerNameKey());
                indian = new ServerPlayer(game, name, false, nation, 
                                          null, null);
                logger.info("Imported new native nation "
                    + player.getNationId() + ": " + indian.getId());
                game.addPlayer(indian);
            } else {
                logger.info("Found native nation " + player.getNationId()
                    + " for import: " + indian.getId());
            }
            for (IndianSettlement is : player.getIndianSettlements()) {
                int x = is.getTile().getX();
                int y = is.getTile().getY();
                Tile tile = map.getTile(x, y);
                if (tile == null) continue;
                UnitType skill = is.getLearnableSkill();
                ServerIndianSettlement settlement
                    = new ServerIndianSettlement(game, indian,
                        is.getName(), tile, is.isCapital(), skill, null);
                settlement.placeSettlement(false);

                List<Unit> units = is.getUnitList();
                if (units.isEmpty()) {
                    settlement.addUnits(random);
                } else {
                    for (Unit u : units) {
                        UnitType t = spec.getUnitType(u.getType().getId());
                        if (t != null) {
                            Unit unit = new ServerUnit(game, settlement,
                                                       indian, t);
                            settlement.add(unit);
                            settlement.addOwnedUnit(unit);
                        }
                    }
                }

                List<Goods> goods = is.getCompactGoods();
                if (goods.isEmpty()) {
                    settlement.addRandomGoods(random);
                } else {
                    for (Goods g : goods) {
                        GoodsType t = spec.getGoodsType(g.getType().getId());
                        if (t != null) {
                            settlement.addGoods(t, g.getAmount());
                        }
                    }
                }
                settlement.setWantedGoods(is.getWantedGoods());
                nSettlements++;
            }
        }

        if (nSettlements > 0) {
            for (Tile t : importGame.getMap().getAllTiles()) {
                if (t.getOwner() == null) continue;
                String nationId = t.getOwner().getNationId();
                Player owner = game.getPlayer(nationId);
                if (owner == null) continue;
                Tile tile = map.getTile(t.getX(), t.getY());
                if (tile == null) continue;
                tile.setOwner(owner);
                if (tile.getOwningSettlement() != null) {
                    String name = tile.getOwningSettlement().getName();
                    Settlement is = game.getSettlement(name);
                    tile.setOwningSettlement(is);
                }
            }
        }
        logger.info("Imported " + nSettlements + " native settlements.");
        return nSettlements > 0;
    }

    /**
     * Make the native settlements, at least a capital for every
     * nation and random numbers of other settlements.
     *
     * @param map The <code>Map</code> to place the indian settlements on.
     * @param importGame An optional <code>Game</code> to import
     *     settlements from.
     */
    private void makeNativeSettlements(final Map map, Game importGame) {
        final OptionGroup mgo = getMapGeneratorOptions();
        final boolean importSettlements = mgo.getBoolean(MapGeneratorOptions.IMPORT_SETTLEMENTS);
        if (importSettlements && importGame != null) {
            if (importIndianSettlements(map, importGame)) return;
            // Fall through and create them
        }
        
        final Game game = map.getGame();
        final Specification spec = game.getSpecification();
        float shares = 0f;
        List<IndianSettlement> settlements = new ArrayList<IndianSettlement>();
        List<Player> indians = new ArrayList<Player>();
        HashMap<String, Territory> territoryMap
            = new HashMap<String, Territory>();

        for (Player player : game.getLiveNativePlayers(null)) {
            switch (((IndianNationType)player.getNationType())
                    .getNumberOfSettlements()) {
            case HIGH:
                shares += 4;
                break;
            case AVERAGE:
                shares += 3;
                break;
            case LOW:
                shares += 2;
                break;
            }
            indians.add(player);
            List<String> regionNames
                = ((IndianNationType) player.getNationType()).getRegionNames();
            Territory territory = null;
            if (regionNames == null || regionNames.isEmpty()) {
                territory = new Territory(player, map.getRandomLandTile(random));
                territoryMap.put(player.getId(), territory);
            } else {
                for (String name : regionNames) {
                    if (territoryMap.get(name) == null) {
                        ServerRegion region = (ServerRegion) map.getRegion(name);
                        if (region == null) {
                            territory = new Territory(player, map.getRandomLandTile(random));
                        } else {
                            territory = new Territory(player, region);
                        }
                        territoryMap.put(name, territory);
                        logger.fine("Allocated region " + name
                                    + " for " + player + ".");
                        break;
                    }
                }
                if (territory == null) {
                    logger.warning("Failed to allocate preferred region " + regionNames.get(0)
                                   + " for " + player.getNation());
                    outer: for (String name : regionNames) {
                        Territory otherTerritory = territoryMap.get(name);
                        for (String otherName : ((IndianNationType) otherTerritory.player.getNationType())
                                 .getRegionNames()) {
                            if (territoryMap.get(otherName) == null) {
                                ServerRegion foundRegion = otherTerritory.region;
                                otherTerritory.region = (ServerRegion) map.getRegion(otherName);
                                territoryMap.put(otherName, otherTerritory);
                                territory = new Territory(player, foundRegion);
                                territoryMap.put(name, territory);
                                break outer;
                            }
                        }
                    }
                    if (territory == null) {
                        logger.warning("Unable to find free region for "
                                       + player.getName());
                        territory = new Territory(player, map.getRandomLandTile(random));
                        territoryMap.put(player.getId(), territory);
                    }
                }
            }
        }
        if (indians.isEmpty()) return;

        // Examine all the non-polar settleable tiles in a random
        // order picking out as many as possible suitable tiles for
        // native settlements such that can be guaranteed at least one
        // layer of surrounding tiles to own.
        int minSettlementDistance
            = spec.getRangeOption(GameOptions.SETTLEMENT_NUMBER).getValue();
        List<Tile> settlementTiles = new ArrayList<Tile>();
        tiles: for (Tile tile : map.getAllTiles()) {
            if (!tile.isPolar() && suitableForNativeSettlement(tile)) {
                for (Tile t : settlementTiles) {
                    if (tile.getDistanceTo(t) < minSettlementDistance) {
                        continue tiles;
                    }
                }
                settlementTiles.add(tile);
            }
        }
        Utils.randomShuffle(logger, "Settlement tiles",
                            settlementTiles, random);

        // Check number of settlements.
        int settlementsToPlace = settlementTiles.size();
        float share = settlementsToPlace / shares;
        if (settlementTiles.size() < indians.size()) {
            // TODO: something drastic to boost the settlement number
            logger.warning("There are only " + settlementTiles.size()
                           + " settlement sites."
                           + " This is smaller than " + indians.size()
                           + " the number of tribes.");
        }

        // Find the capitals
        List<Territory> territories
            = new ArrayList<Territory>(territoryMap.values());
        int settlementsPlaced = 0;
        for (Territory territory : territories) {
            switch (((IndianNationType) territory.player.getNationType())
                    .getNumberOfSettlements()) {
            case HIGH:
                territory.numberOfSettlements = Math.round(4 * share);
                break;
            case AVERAGE:
                territory.numberOfSettlements = Math.round(3 * share);
                break;
            case LOW:
                territory.numberOfSettlements = Math.round(2 * share);
                break;
            }
            int radius = territory.player.getNationType().getCapitalType()
                .getClaimableRadius();
            IndianSettlement is = placeCapital(map, territory, radius,
                new ArrayList<Tile>(settlementTiles));
            if (is != null) {
                settlements.add(is);
                settlementsPlaced++;
                settlementTiles.remove(is.getTile());
            }
        }

        // Sort tiles from the edges of the map inward
        Collections.sort(settlementTiles, new Comparator<Tile>() {
                public int compare(Tile tile1, Tile tile2) {
                    int distance1 = Math.min(Math.min(tile1.getX(), map.getWidth() - tile1.getX()),
                        Math.min(tile1.getY(), map.getHeight() - tile1.getY()));
                    int distance2 = Math.min(Math.min(tile2.getX(), map.getWidth() - tile2.getX()),
                        Math.min(tile2.getY(), map.getHeight() - tile2.getY()));
                    return distance1 - distance2;
                }
            });

        // Now place other settlements
        while (!settlementTiles.isEmpty() && !territories.isEmpty()) {
            Tile tile = settlementTiles.remove(0);
            if (tile.getOwner() != null) continue; // No close overlap

            Territory territory = getClosestTerritory(map, tile, territories);
            int radius = territory.player.getNationType().getSettlementType(false)
                .getClaimableRadius();
            // Insist that the settlement can not be linear
            if (territory.player.getClaimableTiles(tile, radius).size()
                > 2 * radius + 1) {
                String name = (territory.region == null) ? "default region"
                    : territory.region.getNameKey();
                logger.fine("Placing a " + territory.player
                            + " camp in region: " + name
                            + " at tile: " + tile);
                settlements.add(placeIndianSettlement(territory.player,
                                                      false, tile, map));
                settlementsPlaced++;
                territory.numberOfSettlements--;
                if (territory.numberOfSettlements <= 0) {
                    territories.remove(territory);
                }

            }
        }

        // Grow some more tiles.
        // TODO: move the magic numbers below to the spec RSN
        // Also collect the skills provided
        HashMap<UnitType, List<IndianSettlement>> skills
            = new HashMap<UnitType, List<IndianSettlement>>();
        Utils.randomShuffle(logger, "Settlements", settlements, random);
        for (IndianSettlement is : settlements) {
            List<Tile> tiles = new ArrayList<Tile>();
            for (Tile tile : is.getOwnedTiles()) {
                for (Tile t : tile.getSurroundingTiles(1)) {
                    if (t.getOwningSettlement() == null) {
                        tiles.add(tile);
                        break;
                    }
                }
            }
            Utils.randomShuffle(logger, "Settlement tiles", tiles, random);
            int minGrow = is.getType().getMinimumGrowth();
            int maxGrow = is.getType().getMaximumGrowth();
            if (maxGrow > minGrow) {
                for (int i = Utils.randomInt(logger, "Gdiff", random,
                                             maxGrow - minGrow) + minGrow;
                     i > 0; i--) {
                    Tile tile = findFreeNeighbouringTile(is, tiles, random);
                    if (tile == null) break;
                    tile.changeOwnership(is.getOwner(), is);
                    tiles.add(tile);
                }
            }

            // Collect settlements by skill
            UnitType skill = is.getLearnableSkill();
            List<IndianSettlement> isList = skills.get(skill);
            if (isList == null) {
                isList = new ArrayList<IndianSettlement>();
                isList.add(is);
                skills.put(skill, isList);
            } else {
                isList.add(is);
            }
        }

        // Require that there be experts for all the new world goods types.
        // Collect the list of needed experts
        List<UnitType> expertsNeeded = new ArrayList<UnitType>();
        for (GoodsType goodsType : spec.getNewWorldGoodsTypeList()) {
            UnitType expert = spec.getExpertForProducing(goodsType);
            if (!skills.containsKey(expert)) expertsNeeded.add(expert);
        }
        // Extract just the settlement lists.
        List<List<IndianSettlement>> isList
            = new ArrayList<List<IndianSettlement>>(skills.values());
        Comparator<List<IndianSettlement>> listComparator
            = new Comparator<List<IndianSettlement>>() {
                public int compare(List<IndianSettlement> l1,
                                   List<IndianSettlement> l2) {
                    return l2.size() - l1.size();
                }
            };
        // For each missing skill...
        while (!expertsNeeded.isEmpty()) {
            UnitType neededSkill = expertsNeeded.remove(0);
            Collections.sort(isList, listComparator);
            List<IndianSettlement> extras = isList.remove(0);
            UnitType extraSkill = extras.get(0).getLearnableSkill();
            List<RandomChoice<IndianSettlement>> choices
                = new ArrayList<RandomChoice<IndianSettlement>>();
            // ...look at the settlements with the most common skill
            // with a bit of favoritism to capitals as the needed skill
            // is so rare,...
            for (IndianSettlement is : extras) {
                IndianNationType nation
                    = (IndianNationType) is.getOwner().getNationType();
                int cm = (is.isCapital()) ? 2 : 1;
                RandomChoice<IndianSettlement> rc = null;
                for (RandomChoice<UnitType> c : nation.generateSkillsForTile(is.getTile())) {
                    if (c.getObject() == neededSkill) {
                        rc = new RandomChoice<IndianSettlement>(is, c.getProbability() * cm);
                        break;
                    }
                }
                choices.add((rc != null) ? rc
                            : new RandomChoice<IndianSettlement>(is, 1));
            }
            if (!choices.isEmpty()) {
                // ...and pick one that could do the missing job.
                IndianSettlement chose
                    = RandomChoice.getWeightedRandom(logger, "expert", choices,
                                                     random);
                logger.finest("At " + chose.getName()
                              + " replaced " + extraSkill
                              + " (one of " + extras.size() + ")"
                              + " by missing " + neededSkill);
                chose.setLearnableSkill(neededSkill);
                extras.remove(chose);
                isList.add(0, extras); // Try to stay well sorted
                List<IndianSettlement> neededList
                    = new ArrayList<IndianSettlement>();
                neededList.add(chose);
                isList.add(neededList);
            } else { // `can not happen'
                logger.finest("Game is missing skill: " + neededSkill);
            }
        }
        String msg = "Settlement skills:";
        for (List<IndianSettlement> iss : isList) {
            if (iss.isEmpty()) {
                msg += "  0 x <none>";
            } else {
                msg += "  " + iss.size() + " x " + iss.get(0).getLearnableSkill();
            }
        }
        logger.info(msg);

        logger.info("Created " + settlementsPlaced
                    + " Indian settlements of maximum " + settlementsToPlace);
    }

    /**
     * Is a tile suitable for a native settlement?
     * Require the tile be settleable, and at least half its neighbours
     * also be settleable.  TODO: degrade the second test to usability,
     * but fix this when the natives-use-water situation is sorted.
     *
     * @param tile The <code>Tile</code> to examine.
     * @return True if this tile is suitable.
     */
    private boolean suitableForNativeSettlement(Tile tile) {
        if (!tile.getType().canSettle()) return false;
        int good = 0, n = 0;
        for (Tile t : tile.getSurroundingTiles(1)) {
            if (t.getType().canSettle()) good++;
            n++;
        }
        return good >= n / 2;
    }

    private Tile findFreeNeighbouringTile(IndianSettlement is,
                                          List<Tile> tiles, Random random) {
        for (Tile tile : tiles) {
            for (Direction d : Direction.getRandomDirections("freeTile", random)) {
                Tile t = tile.getNeighbourOrNull(d);
                if ((t != null)
                    && (t.getOwningSettlement() == null)
                    && (is.getOwner().canClaimForSettlement(t))) return t;
            }
        }
        return null;
    }

    private Territory getClosestTerritory(Map map, Tile tile, List<Territory> territories) {
        Territory result = null;
        int minimumDistance = Integer.MAX_VALUE;
        for (Territory territory : territories) {
            int distance = map.getDistance(tile, territory.getCenterTile(map));
            if (distance < minimumDistance) {
                minimumDistance = distance;
                result = territory;
            }
        }
        return result;
    }

    /**
     * Place a native capital in a territory.
     *
     * @param map The <code>Map</code> to place the settlement in.
     * @param territory The <code>Territory</code> within the map.
     * @param radius The settlement radius.
     * @param tiles A list of <code>Tile</code>s to select from.
     * @return The <code>IndianSettlement</code> placed, or null if
     *     none placed.
     */
    private IndianSettlement placeCapital(final Map map, Territory territory,
                                          int radius, List<Tile> tiles) {
        final Tile center = territory.getCenterTile(map);
        Collections.sort(tiles, new Comparator<Tile>() {
                public int compare(Tile t1, Tile t2) {
                    return t1.getDistanceTo(center) - t2.getDistanceTo(center);
                }
            });
        for (Tile t : tiles) {
            // Choose this tile if it is free and half the expected tile
            // claim can succeed (preventing capitals on small islands).
            if (territory.player.getClaimableTiles(t, radius).size()
                >= (2 * radius + 1) * (2 * radius + 1) / 2) {
                String name = (territory.region == null) ? "default region"
                    : territory.region.getNameKey();
                logger.fine("Placing the " + territory.player
                    + " capital in region: " + name + " at tile: " + t);
                IndianSettlement is
                    = placeIndianSettlement(territory.player, true, t, map);
                territory.numberOfSettlements--;
                territory.tile = t;
                return is;
            }
        }
        return null;
    }

    /**
     * Builds a <code>IndianSettlement</code> at the given position.
     *
     * @param player The player owning the new settlement.
     * @param capital <code>true</code> if the settlement should be a
     *      {@link IndianSettlement#isCapital() capital}.
     * @param tile The <code>Tile</code> to place the settlement.
     * @param map The map that should get a new settlement.
     * @return The <code>IndianSettlement</code> just being placed
     *      on the map.
     */
    private IndianSettlement placeIndianSettlement(Player player,
        boolean capital, Tile tile, Map map) {
        String name = (capital) ? player.getCapitalName(random)
            : player.getSettlementName(random);
        UnitType skill
            = generateSkillForLocation(map, tile, player.getNationType());
        ServerIndianSettlement settlement
            = new ServerIndianSettlement(map.getGame(), player, name, tile,
                                         capital, skill, null);
        player.addSettlement(settlement);
        logger.fine("Generated skill: " + settlement.getLearnableSkill());

        settlement.placeSettlement(true);
        settlement.addRandomGoods(random);
        settlement.addUnits(random);

        return settlement;
    }

    /**
     * Generates a skill that could be taught from a settlement on the
     * given tile.
     *
     * @param map The <code>Map</code>.
     * @param tile The <code>Tile</code> where the settlement will be located.
     * @param nationType The <code>NationType</code> to generate a skill for.
     * @return A skill that can be taught to Europeans.
     */
    private UnitType generateSkillForLocation(Map map, Tile tile,
                                              NationType nationType) {
        List<RandomChoice<UnitType>> skills
            = ((IndianNationType)nationType).getSkills();
        java.util.Map<GoodsType, Integer> scale
            = new HashMap<GoodsType, Integer>();
        for (RandomChoice<UnitType> skill : skills) {
            scale.put(skill.getObject().getExpertProduction(), 1);
        }

        for (Tile t: tile.getSurroundingTiles(1)) {
            for (Entry<GoodsType, Integer> entry : scale.entrySet()) {
                GoodsType goodsType = entry.getKey();
                scale.put(goodsType, entry.getValue().intValue()
                          + t.getPotentialProduction(goodsType, null));
            }
        }

        List<RandomChoice<UnitType>> scaledSkills
            = new ArrayList<RandomChoice<UnitType>>();
        for (RandomChoice<UnitType> skill : skills) {
            UnitType unitType = skill.getObject();
            int scaleValue = scale.get(unitType.getExpertProduction()).intValue();
            scaledSkills.add(new RandomChoice<UnitType>(unitType,
                    skill.getProbability() * scaleValue));
        }
        UnitType skill = RandomChoice.getWeightedRandom(null, null,
                                                        scaledSkills, random);
        if (skill == null) {
            // Seasoned Scout
            List<UnitType> unitList = map.getSpecification().getUnitTypesWithAbility(Ability.EXPERT_SCOUT);
            return Utils.getRandomMember(logger, "Scout", unitList, random);
        } else {
            return skill;
        }
    }

    /**
     * Create two ships, one with a colonist, for each player, and
     * select suitable starting positions.
     *
     * @param map The <code>Map</code> to place the european units on.
     * @param players The players to create <code>Settlement</code>s
     *      and starting locations for. That is; both indian and
     *      european players.
     */
    private void createEuropeanUnits(Map map, List<Player> players) {
        Game game = map.getGame();
        Specification spec = game.getSpecification();
        final int width = map.getWidth();
        final int height = map.getHeight();
        final int poleDistance = (int)(MIN_DISTANCE_FROM_POLE*height/2);

        List<Player> europeanPlayers = new ArrayList<Player>();
        for (Player player : players) {
            if (player.isREF()) {
                // eastern edge of the map
                int x = width - 2;
                // random latitude, not too close to the pole
                int y = Utils.randomInt(logger, "Pole", random,
                                        height - 2*poleDistance) + poleDistance;
                player.setEntryLocation(map.getTile(x, y));
                continue;
            }
            if (player.isEuropean()) {
                europeanPlayers.add(player);
                logger.finest("found European player " + player);
            }
        }

        List<Position> positions = generateStartingPositions(map, europeanPlayers);
        List<Tile> startingTiles = new ArrayList<Tile>();
        List<Unit> carriers = new ArrayList<Unit>();
        List<Unit> passengers = new ArrayList<Unit>();

        for (int index = 0; index < europeanPlayers.size(); index++) {
            Player player = europeanPlayers.get(index);
            Position position = positions.get(index);
            logger.fine("generating units for player " + player);

            carriers.clear();
            passengers.clear();
            List<AbstractUnit> unitList = ((EuropeanNationType) player.getNationType())
                .getStartingUnits();
            for (AbstractUnit startingUnit : unitList) {
                UnitType type = startingUnit.getType(spec);
                Role role = startingUnit.getRole(spec);
                Unit newUnit = new ServerUnit(game, null, player, type, role);
                newUnit.setName(player.getNameForUnit(type, random));
                if (newUnit.isNaval()) {
                    if (newUnit.canCarryUnits()) {
                        newUnit.setState(Unit.UnitState.ACTIVE);
                        carriers.add(newUnit);
                    }
                } else {
                    newUnit.setState(Unit.UnitState.SENTRY);
                    passengers.add(newUnit);
                }

            }

            boolean startAtSea = true;
            if (carriers.isEmpty()) {
                logger.warning("No carriers defined for player " + player);
                startAtSea = false;
            }

            Tile startTile = null;
            int x = position.getX();
            int y = position.getY();
            for (int i = 0; i < 2 * map.getHeight(); i++) {
                int offset = (i % 2 == 0) ? i / 2 : -(1 + i / 2);
                int row = y + offset;
                if (row < 0 || row >= map.getHeight()) continue;
                startTile = findTileFor(map, row, x, startAtSea);
                if (startTile != null) {
                    if (startingTiles.contains(startTile)) {
                        startTile = null;
                    } else {
                        startingTiles.add(startTile);
                        break;
                    }
                }
            }
            if (startTile == null) {
                String err = "Failed to find start tile "
                    + ((startAtSea) ? "at sea" : "on land")
                    + " for player " + player
                    + " from (" + x + "," + y + ")"
                    + " avoiding:";
                for (Tile t : startingTiles) err += " " + t;
                err += " with map: ";
                for (int xx = 0; xx < map.getWidth(); xx++) {
                    err += map.getTile(xx, y);
                }
                throw new RuntimeException(err);
            }

            player.setEntryLocation(startTile);

            if (startAtSea) {
                for (Unit carrier : carriers) {
                    carrier.setLocation(startTile);
                    ((ServerPlayer)player).exploreForUnit(carrier);
                }
                passengers: for (Unit unit : passengers) {
                    for (Unit carrier : carriers) {
                        if (carrier.canAdd(unit)) {
                            unit.setLocation(carrier);
                            continue passengers;
                        }
                    }
                    // no space left on carriers
                    unit.setLocation(player.getEurope());
                }
            } else {
                for (Unit unit : passengers) {
                    unit.setLocation(startTile);
                    ((ServerPlayer)player).exploreForUnit(unit);
                }
            }

            if (FreeColDebugger.isInDebugMode(FreeColDebugger.DebugMode.INIT)) {
                createDebugUnits(map, player, startTile);
                IntegerOption op = spec.getIntegerOption(GameOptions.STARTING_MONEY);
                if (op != null) op.setValue(10000);
            }
        }
    }

    private Tile findTileFor(Map map, int row, int start, boolean startAtSea) {
        Tile tile = null;
        Tile seas = null;
        int offset = (start == 0) ? 1 : -1;
        for (int x = start; 0 <= x && x < map.getWidth(); x += offset) {
            tile = map.getTile(x, row);
            if (tile.isDirectlyHighSeasConnected()) {
                seas = tile;
            } else if (tile.isLand()) {
                return (startAtSea) ? seas : tile;
            } 
        }
        logger.warning("No land in row " + row);
        return null;
    }

    private void createDebugUnits(Map map, Player player, Tile startTile) {
        final Game game = map.getGame();
        final Specification spec = game.getSpecification();

        // In debug mode give each player a few more units and a colony.
        UnitType unitType = spec.getUnitType("model.unit.galleon");
        Unit unit4 = new ServerUnit(game, startTile, player, unitType);
        unitType = spec.getUnitType("model.unit.privateer");
        Unit privateer = new ServerUnit(game, startTile, player, unitType);
        ((ServerPlayer)player).exploreForUnit(privateer);
        unitType = spec.getUnitType("model.unit.freeColonist");
        Unit unit5 = new ServerUnit(game, unit4, player, unitType);
        unitType = spec.getUnitType("model.unit.veteranSoldier");
        Unit unit6 = new ServerUnit(game, unit4, player, unitType);
        unitType = spec.getUnitType("model.unit.jesuitMissionary");
        Unit unit7 = new ServerUnit(game, unit4, player, unitType);

        Tile colonyTile = null;
        for (Tile tempTile : map.getCircleTiles(startTile, true, 
                                                FreeColObject.INFINITY)) {
            if (tempTile.isPolar()) continue; // No initial polar colonies
            if (player.canClaimToFoundSettlement(tempTile)) {
                colonyTile = tempTile;
                break;
            }
        }

        if (colonyTile == null) {
            logger.warning("Could not find a debug colony site.");
            return;
        }
        for (TileType t : spec.getTileTypeList()) {
            if (!t.isWater()) {
                colonyTile.setType(t);
                break;
            }
        }
        unitType = spec.getUnitType("model.unit.expertFarmer");
        Unit buildColonyUnit = new ServerUnit(game, colonyTile,
                                              player, unitType);
        String colonyName = Messages.message(player.getNationName())
            + " Colony";
        Colony colony = new ServerColony(game, player, colonyName, colonyTile);
        player.addSettlement(colony);
        colony.placeSettlement(true);
        for (Tile tile : colonyTile.getSurroundingTiles(1)) {
            if (!tile.hasSettlement()
                && (tile.getOwner() == null
                    || !tile.getOwner().isEuropean())) {
                tile.changeOwnership(player, colony);
                if (tile.hasLostCityRumour()) {
                    tile.removeLostCityRumour();
                }
            }
        }
        buildColonyUnit.setLocation(colony);
        if (buildColonyUnit.getLocation() instanceof ColonyTile) {
            Tile ct = ((ColonyTile) buildColonyUnit.getLocation()).getWorkTile();
            for (TileType t : spec.getTileTypeList()) {
                if (!t.isWater()) {
                    ct.setType(t);
                    TileImprovementType plowType = map.getSpecification()
                        .getTileImprovementType("model.improvement.plow");
                    TileImprovement plow = new TileImprovement(game, ct, plowType);
                    plow.setTurnsToComplete(0);
                    ct.add(plow);
                    break;
                }
            }
        }
        BuildingType schoolType = spec.getBuildingType("model.building.schoolhouse");
        Building schoolhouse = new ServerBuilding(game, colony, schoolType);
        colony.addBuilding(schoolhouse);
        unitType = spec.getUnitType("model.unit.masterCarpenter");
        Unit carpenter = new ServerUnit(game, colonyTile, player, unitType);
        carpenter.setLocation(colony.getWorkLocationFor(carpenter,
                carpenter.getType().getExpertProduction()));

        unitType = spec.getUnitType("model.unit.elderStatesman");
        Unit statesman = new ServerUnit(game, colonyTile, player, unitType);
        statesman.setLocation(colony.getWorkLocationFor(statesman,
                statesman.getType().getExpertProduction()));

        unitType = spec.getUnitType("model.unit.expertLumberJack");
        Unit lumberjack = new ServerUnit(game, colony, player, unitType);
        if (lumberjack.getLocation() instanceof ColonyTile) {
            Tile lt = ((ColonyTile) lumberjack.getLocation()).getWorkTile();
            for (TileType t : spec.getTileTypeList()) {
                if (t.isForested()) {
                    lt.setType(t);
                    break;
                }
            }
            lumberjack.changeWorkType(lumberjack.getType()
                .getExpertProduction());
        }

        unitType = spec.getUnitType("model.unit.seasonedScout");
        Unit scout = new ServerUnit(game, colonyTile, player, unitType);
        ((ServerPlayer)player).exploreForUnit(scout);

        unitType = spec.getUnitType("model.unit.veteranSoldier");
        Unit unit8 = new ServerUnit(game, colonyTile, player, unitType);
        Unit unit9 = new ServerUnit(game, colonyTile, player, unitType);
        unitType = spec.getUnitType("model.unit.artillery");
        Unit unit10 = new ServerUnit(game, colonyTile, player, unitType);
        Unit unit11 = new ServerUnit(game, colonyTile, player, unitType);
        Unit unit12 = new ServerUnit(game, colonyTile, player, unitType);
        unitType = spec.getUnitType("model.unit.treasureTrain");
        Unit unit13 = new ServerUnit(game, colonyTile, player, unitType);
        unit13.setTreasureAmount(10000);
        unitType = spec.getUnitType("model.unit.wagonTrain");
        Unit unit14 = new ServerUnit(game, colonyTile, player, unitType);
        GoodsType cigarsType = spec.getGoodsType("model.goods.cigars");
        Goods cigards = new Goods(game, unit14, cigarsType, 5);
        unit14.add(cigards);
        unitType = spec.getUnitType("model.unit.jesuitMissionary");
        Unit unit15 = new ServerUnit(game, colonyTile, player, unitType);
        Unit unit16 = new ServerUnit(game, colonyTile, player, unitType);

        ((ServerPlayer)player).exploreForSettlement(colony);
    }

    private List<Position> generateStartingPositions(Map map, List<Player> players) {
        int number = players.size();
        List<Position> positions = new ArrayList<Position>(number);
        if (number > 0) {
            int west = 0;
            int east = map.getWidth() - 1;
            switch(map.getSpecification().getInteger(GameOptions.STARTING_POSITIONS)) {
            case GameOptions.STARTING_POSITIONS_CLASSIC:
                int distance = map.getHeight() / number;
                int row = distance/2;
                for (int index = 0; index < number; index++) {
                    positions.add(new Position(east, row));
                    row += distance;
                }
                Utils.randomShuffle(logger, "Classic starting positions",
                                    positions, random);
                break;
            case GameOptions.STARTING_POSITIONS_RANDOM:
                distance = 2 * map.getHeight() / number;
                row = distance/2;
                for (int index = 0; index < number; index++) {
                    if (index % 2 == 0) {
                        positions.add(new Position(east, row));
                    } else {
                        positions.add(new Position(west, row));
                        row += distance;
                    }
                }
                Utils.randomShuffle(logger, "Random starting positions",
                                    positions, random);
                break;
            case GameOptions.STARTING_POSITIONS_HISTORICAL:
                for (Player player : players) {
                    Nation nation = player.getNation();
                    positions.add(new Position(nation.startsOnEastCoast() ? east : west,
                                               map.getRow(nation.getPreferredLatitude())));
                }
                break;
            }
        }
        return positions;
    }


    // Implement MapGenerator

    /**
     * {@inheritDoc}
     */
    public OptionGroup getMapGeneratorOptions() {
        return specification.getMapGeneratorOptions();
    }

    /**
     * {@inheritDoc}
     */
    public void createEmptyMap(Game game, int width, int height) {
        terrainGenerator.createMap(game, null, new LandMap(width, height));
    }

    /**
     * {@inheritDoc}
     */
    public void createMap(Game game) throws FreeColException {
        // Reinitialize.
        initialize();

        // Prepare imports:
        final OptionGroup mgo = getMapGeneratorOptions();
        final File importFile = ((FileOption)mgo.getOption(MapGeneratorOptions.IMPORT_FILE)).getValue();
        final Game importGame;
        if (importFile != null) {
            Game g = null;
            try {
                g = FreeColServer.readGame(new FreeColSavegameFile(importFile),
                                           game.getSpecification(), null);
                logger.info("Imported file " + importFile.getPath());
            } catch (Exception e) {
                throw new FreeColException("Import failed for "
                    + importFile.getPath(), e);
            }
            importGame = g;
        } else {
            importGame = null;
        }

        // Create land map.
        LandMap landMap = (importGame != null) ? new LandMap(importGame)
            : new LandMap(mgo, random);

        // Create terrain.
        terrainGenerator.createMap(game, importGame, landMap);

        // Decorate the map.
        Map map = game.getMap();
        makeNativeSettlements(map, importGame);
        makeLostCityRumours(map, importGame);
        createEuropeanUnits(map, game.getLiveEuropeanPlayers(null));

        // If importing as a result of "Start Game" in the map editor,
        // consume the file.
        if (importFile != null) {
            File startGame = FreeColDirectories.getStartMapFile();
            if (startGame != null
                && startGame.getPath().equals(importFile.getPath())) {
                importFile.delete();
            }
        }
    }
}
