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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.freecol.common.model.Modifier;
import net.sf.freecol.common.model.Modifier.ModifierType;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitTypeChange.ChangeType;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.common.util.Utils;


/**
 * This class implements the original Colonization combat model.
 * Note that the damage part of any CombatResult is ignored throughout.
 */
public class SimpleCombatModel extends CombatModel {

    private static final Logger logger = Logger.getLogger(SimpleCombatModel.class.getName());

    // The maximum attack power of a Colony's fortifications against a
    // naval unit.
    public static final int MAXIMUM_BOMBARD_POWER = 48;

    public static final Modifier UNKNOWN_DEFENCE_MODIFIER
        = new Modifier("bogus", Modifier.UNKNOWN, ModifierType.ADDITIVE);


    /**
     * Deliberately empty constructor.
     */
    public SimpleCombatModel() {}


    /**
     * Calculates the odds of success in combat.
     *
     * @param attacker The attacker.
     * @param defender The defender.
     * @return The combat odds.
     */
    public CombatOdds calculateCombatOdds(FreeColGameObject attacker,
                                          FreeColGameObject defender) {
        return calculateCombatOdds(attacker, defender, null);
    }

    /**
     * Calculates the odds of success in combat.
     *
     * @param attacker The attacker.
     * @param defender The defender.
     * @param lb An optional <code>LogBuilder</code> to log to.
     * @return The combat odds.
     */
    private CombatOdds calculateCombatOdds(FreeColGameObject attacker,
                                           FreeColGameObject defender,
                                           LogBuilder lb) {
        if (attacker == null || defender == null) {
            if (lb != null) lb.add(" odds=unknowable");
            return new CombatOdds(CombatOdds.UNKNOWN_ODDS);
        }

        if (lb != null) lb.add(" attacker=", attacker, " ");
        float attackPower = getOffencePower(attacker, defender, lb);
        if (lb != null) lb.add(" defender=", defender, " ");
        float defencePower = getDefencePower(attacker, defender, lb);
        if (attackPower == 0.0f && defencePower == 0.0f) {
            if (lb != null) lb.add(" odds=unknown");
            return new CombatOdds(CombatOdds.UNKNOWN_ODDS);
        }
        float victory = attackPower / (attackPower + defencePower);
        if (lb != null) lb.add(" odds=", victory);
        return new CombatOdds(victory);
    }

    /**
     * Get the offensive power of a unit attacking another.
     *
     * @param attacker The attacker.
     * @param defender The defender.
     * @return The offensive power.
     */
    public float getOffencePower(FreeColGameObject attacker,
                                 FreeColGameObject defender) {
        return getOffencePower(attacker, defender, null);
    }

    /**
     * Helper to log modifiers with.
     *
     * @param lb The <code>LogBuilder</code> to log to.
     * @param modSet A set of <code>Modifiers</code> to log.
     */   
    private void logModifiers(LogBuilder lb, Set<Modifier> modSet) {
        List<Modifier> modList = new ArrayList<Modifier>();
        modList.addAll(modSet);
        Collections.sort(modList);
        lb.addCollection(" ", modList);
    }

    /**
     * Get the offensive power of a unit attacking another.
     *
     * @param attacker The attacker.
     * @param defender The defender.
     * @param lb An optional <code>LogBuilder</code> to log to.
     * @return The offensive power.
     */
    private float getOffencePower(FreeColGameObject attacker,
                                  FreeColGameObject defender,
                                  LogBuilder lb) {
        float result = 0.0f;
        if (attacker == null) {
            throw new IllegalStateException("Null attacker");

        } else if (combatIsAttackMeasurement(attacker, defender)
            || combatIsAttack(attacker, defender)
            || combatIsSettlementAttack(attacker, defender)) {
            Set<Modifier> mods = getOffensiveModifiers(attacker, defender);
            Turn turn = attacker.getGame().getTurn(); 
            result = FeatureContainer.applyModifiers(0.0f, turn, mods);
            if (lb != null) {
                logModifiers(lb, mods);
                lb.add(" = ", result);
            }

        } else if (combatIsBombard(attacker, defender)) {
            Settlement attackerSettlement = (Settlement) attacker;
            if (attackerSettlement.hasAbility(Ability.BOMBARD_SHIPS)) {
                for (Unit unit : attackerSettlement.getTile().getUnitList()) {
                    if (unit.hasAbility(Ability.BOMBARD)) {
                        result += unit.getType().getOffence();
                    }
                }
            }
            if (result > MAXIMUM_BOMBARD_POWER) result = MAXIMUM_BOMBARD_POWER;
            if (lb != null) lb.add(" bombard=", result);

        } else {
            throw new IllegalArgumentException("Bogus combat");
        }
        return result;
    }

    /**
     * Get the defensive power wrt an attacker.
     *
     * @param attacker The attacker.
     * @param defender The defender.
     * @return The defensive power.
     */
    public float getDefencePower(FreeColGameObject attacker,
                                 FreeColGameObject defender) {
        return getDefencePower(attacker, defender, null);
    }

    /**
     * Get the defensive power wrt an attacker.
     *
     * @param attacker The attacker.
     * @param defender The defender.
     * @param lb An optional <code>LogBuilder</code> to log to.
     * @return The defensive power.
     */
    public float getDefencePower(FreeColGameObject attacker,
                                 FreeColGameObject defender,
                                 LogBuilder lb) {
        float result;
        if (combatIsDefenceMeasurement(attacker, defender)
            || combatIsAttack(attacker, defender)
            || combatIsSettlementAttack(attacker, defender)
            || combatIsBombard(attacker, defender)) {
            Set<Modifier> mods = getDefensiveModifiers(attacker, defender);
            Turn turn = defender.getGame().getTurn();
            result = FeatureContainer.applyModifiers(0.0f, turn, mods);
            if (lb != null) {
                logModifiers(lb, mods);
                lb.add(" = ", result);
            }

        } else {
            throw new IllegalArgumentException("Bogus combat");
        }
        return result;
    }

    /**
     * Collect all the offensive modifiers that apply to an attack.
     *
     * @param attacker The attacker.
     * @param defender The defender.
     * @return All the applicable offensive modifiers.
     */
    public Set<Modifier> getOffensiveModifiers(FreeColGameObject attacker,
                                               FreeColGameObject defender) {
        Set<Modifier> result = new HashSet<Modifier>();
        if (attacker == null) {
            throw new IllegalStateException("Null attacker");
        } else if (combatIsAttackMeasurement(attacker, defender)
            || combatIsAttack(attacker, defender)
            || combatIsSettlementAttack(attacker, defender)) {
            final Unit attackerUnit = (Unit)attacker;

            // Base offense
            result.add(new Modifier(Modifier.OFFENCE,
                                    attackerUnit.getType().getBaseOffence(),
                                    ModifierType.ADDITIVE,
                                    Specification.BASE_OFFENCE_SOURCE));

            // Unit offensive modifiers, including role+equipment,
            // qualified by unit type so that scopes work
            result.addAll(attackerUnit.getModifiers(Modifier.OFFENCE,
                          attackerUnit.getType()));

            // Special bonuses against certain nation types
            if (defender instanceof Ownable) {
                Player owner = ((Ownable)defender).getOwner();
                result.addAll(attackerUnit
                    .getModifiers(Modifier.OFFENCE_AGAINST,
                                  owner.getNationType()));
            }

            // Land/naval specific
            if (attackerUnit.isNaval()) {
                addNavalOffensiveModifiers(attackerUnit, result);
            } else {
                addLandOffensiveModifiers(attackerUnit, defender, result);
            }

        } else if (combatIsBombard(attacker, defender)) {
            ; // Bombard strength handled by getOffensePower

        } else {
            throw new IllegalArgumentException("Bogus combat");
        }
        
        return result;
    }

    /**
     * Add all the offensive modifiers that apply to a naval attack.
     *
     * @param attacker The attacker.
     * @param result The set of modifiers to add to.
     */
    private void addNavalOffensiveModifiers(Unit attacker,
                                            Set<Modifier> result) {
        // Attack bonus
        Specification spec = attacker.getSpecification();
        result.addAll(spec.getModifiers(Modifier.ATTACK_BONUS));

        // Goods penalty always applies
        int count = attacker.getGoodsSpaceTaken();
        if (count > 0) {
            // TODO: shouldn't this be -cargo/capacity?
            // TODO: magic number to spec
            result.add(new Modifier(Modifier.OFFENCE, -12.5f * count,
                                    ModifierType.PERCENTAGE,
                                    Specification.CARGO_PENALTY_SOURCE));
        }
    }

    /**
     * Add all the offensive modifiers that apply to a land attack.
     *
     * @param attacker The attacker <code>Unit</code>.
     * @param defender The defender.
     * @param result The set of modifiers to add to.
     */
    private void addLandOffensiveModifiers(Unit attacker,
                                           FreeColGameObject defender,
                                           Set<Modifier> result) {
        final Specification spec = attacker.getSpecification();

        // Attack bonus
        result.addAll(spec.getModifiers(Modifier.ATTACK_BONUS));

        // Movement penalty
        switch (attacker.getMovesLeft()) {
        case 1:
            result.addAll(spec.getModifiers(Modifier.BIG_MOVEMENT_PENALTY));
            break;
        case 2:
            result.addAll(spec.getModifiers(Modifier.SMALL_MOVEMENT_PENALTY));
            break;
        default:
            break;
        }

        // Amphibious attack?
        if (combatIsAmphibious(attacker, defender)) {
            result.addAll(spec.getModifiers(Modifier.AMPHIBIOUS_ATTACK));
        }

        if (combatIsAttackMeasurement(attacker, defender)) {
            ; // No defender information available

        } else if (combatIsSettlementAttack(attacker, defender)) {
            // Settlement present, apply bombardment bonus
            result.addAll(attacker.getModifiers(Modifier.BOMBARD_BONUS));

            // Popular support bonus
            if (combatIsWarOfIndependence(attacker, defender)) {
                Colony colony = (Colony)defender;
                int bonus = colony.getSoLPercentage();
                if (bonus >= 0) {
                    if (attacker.getOwner().isREF()) bonus = 100 - bonus;
                    result.add(new Modifier(Modifier.POPULAR_SUPPORT, bonus,
                            ModifierType.PERCENTAGE, colony));
                }
            }

        } else if (combatIsAttack(attacker, defender)) {
            Unit defenderUnit = (Unit) defender;
            Tile tile = defenderUnit.getTile();
            if (tile != null) {
                if (tile.hasSettlement()) {
                    // Bombard bonus applies to settlement defence
                    result.addAll(attacker
                                  .getModifiers(Modifier.BOMBARD_BONUS));

                    // Popular support bonus
                    if (combatIsWarOfIndependence(attacker, defender)) {
                        Colony colony = (Colony)tile.getSettlement();
                        int bonus = colony.getSoLPercentage();
                        if (bonus >= 0) {
                            if (attacker.getOwner().isREF()) {
                                bonus = 100 - bonus;
                            }
                            result.add(new Modifier(Modifier.POPULAR_SUPPORT,
                                    bonus, ModifierType.PERCENTAGE, colony));
                        }
                    }
                } else {
                    // Ambush bonus in the open = defender's defence
                    // bonus, if defender is REF, or attacker is indian.
                    if (isAmbush(attacker, defender)) {
                        for (Modifier m : tile.getType()
                                 .getModifiers(Modifier.DEFENCE)) {
                            Modifier mod = new Modifier(Modifier.OFFENCE, m);
                            mod.setSource(Specification.AMBUSH_BONUS_SOURCE);
                            result.add(mod);
                        }
                    }
                }
            }

            // Artillery in the open penalty, attacker must be on a
            // tile and neither unit can be in a settlement.
            if (attacker.hasAbility(Ability.BOMBARD)
                && attacker.getLocation() instanceof Tile
                && attacker.getSettlement() == null
                && defenderUnit.getSettlement() == null) {
                result.addAll(spec.getModifiers(Modifier.ARTILLERY_IN_THE_OPEN));
            }
        } else {
            throw new IllegalStateException("Bogus combat");
        }
    }

    /**
     * Collect all defensive modifiers when defending against an attack.
     *
     * @param attacker The attacker.
     * @param defender The defender.
     * @return All the applicable defensive modifiers.
     */
    public Set<Modifier> getDefensiveModifiers(FreeColGameObject attacker,
                                               FreeColGameObject defender) {
        Set<Modifier> result = new HashSet<Modifier>();
        if (combatIsDefenceMeasurement(attacker, defender)
            || combatIsAttack(attacker, defender)
            || combatIsBombard(attacker, defender)) {
            final Unit defenderUnit = (Unit)defender;

            // Base defence
            result.add(new Modifier(Modifier.DEFENCE,
                                    defenderUnit.getType().getBaseDefence(),
                                    ModifierType.ADDITIVE,
                                    Specification.BASE_DEFENCE_SOURCE));

            // Unit specific
            result.addAll(defenderUnit.getModifiers(Modifier.DEFENCE,
                    defenderUnit.getType(), null));

            // Land/naval split
            if (defenderUnit.isNaval()) {
                addNavalDefensiveModifiers(defenderUnit, result);
            } else {
                addLandDefensiveModifiers(attacker, defenderUnit, result);
            }

        } else if (combatIsSettlementAttack(attacker, defender)) {
            Settlement settlement = (Settlement)defender;
            // Tile defence bonus
            Tile tile = settlement.getTile();
            result.addAll(tile.getType().getDefenceModifiers());

            // Settlement defence bonus
            result.addAll(settlement.getModifiers(Modifier.DEFENCE));

            // Not allowed to see inside the settlement.  This only applies 
            // to the pre-combat dialog--- the actual attack is on the
            // unit chosen to defend.
            result.add(UNKNOWN_DEFENCE_MODIFIER);

        } else {
            throw new IllegalArgumentException("Bogus combat");
        }
        return result;
    }

    /**
     * Add all the defensive modifiers that apply to a naval attack.
     *
     * @param defender The defender <code>Unit</code>.
     * @param result The set of modifiers to add to.
     */
    private void addNavalDefensiveModifiers(Unit defender,
                                            Set<Modifier> result) {
        // Cargo penalty always applies
        int goodsCount = defender.getVisibleGoodsCount();
        if (goodsCount > 0) {
            // TODO: should this be -cargo/capacity?
            result.add(new Modifier(Modifier.DEFENCE, -12.5f * goodsCount,
                                    ModifierType.PERCENTAGE,
                                    Specification.CARGO_PENALTY_SOURCE));
        }
    }

    /**
     * Add all the defensive modifiers that apply to a land attack.
     *
     * @param attacker The attacker.
     * @param defender The defender <code>Unit</code>.
     * @param result The set of modifiers to add to.
     */
    private void addLandDefensiveModifiers(FreeColGameObject attacker,
                                           Unit defender,
                                           Set<Modifier> result) {
        final Specification spec = defender.getSpecification();
        final Tile tile = defender.getTile();
        final Settlement settlement = (tile == null) ? null
            : tile.getSettlement();

        // Fortify bonus
        if (defender.getState() == Unit.UnitState.FORTIFIED) {
            result.addAll(spec.getModifiers(Modifier.FORTIFIED));
        }

        if (tile != null) {
            // Tile defence bonus
            result.addAll(tile.getType().getDefenceModifiers());

            if (settlement == null) {
                // Artillery in the Open penalty
                if (defender.hasAbility(Ability.BOMBARD)
                    && defender.getState() != Unit.UnitState.FORTIFIED) {
                    result.addAll(spec.getModifiers(Modifier.ARTILLERY_IN_THE_OPEN));
                }

            } else { // In settlement
                // Settlement defence bonus
                result.addAll(settlement.getModifiers(Modifier.DEFENCE));

                // Artillery defence bonus against an Indian raid
                if (defender.hasAbility(Ability.BOMBARD)
                    && attacker != null
                    && ((Unit)attacker).getOwner().isIndian()) {
                    result.addAll(spec.getModifiers(Modifier.ARTILLERY_AGAINST_RAID));
                }

                // Automatic defensive role (e.g. Revere)
                Role autoRole = defender.getAutomaticRole();
                if (autoRole != null) {
                    result.addAll(autoRole.getModifiers(Modifier.DEFENCE));
                }
            }
        }
    }

    /**
     * Generates a result of a unit attacking.
     * Takes care to only call the pseudo-random source *once*.
     *
     * @param random A pseudo-random number source.
     * @param attacker The attacker.
     * @param defender The defender.
     * @return The results of the combat.
     */
    public List<CombatResult> generateAttackResult(Random random,
        FreeColGameObject attacker, FreeColGameObject defender) {
        LogBuilder lb = new LogBuilder(256);
        lb.add("Combat");
        ArrayList<CombatResult> crs = new ArrayList<CombatResult>();
        CombatOdds odds = calculateCombatOdds(attacker, defender, lb);
        float r = Utils.randomFloat(logger, "AttackResult", random);
        lb.add(" random(1.0)=", r);
        boolean great = false; // Great win or loss?
        String action;

        if (combatIsAttack(attacker, defender)) {
            Unit attackerUnit = (Unit) attacker;
            Unit defenderUnit = (Unit) defender;
            action = "Attack";

            // For random float 0 <= r < 1.0:
            // Partition this range into wins < odds.win and losses above.
            // Within the 0 <= r < odds.win range, partition the first 10%
            // to be great wins and the rest to be ordinary wins.
            //   r < 0.1 * odds.win  => great win
            //   else r < odds.win   => win
            // Within the odds.win <= r < 1.0 range, partition the first
            // 20% to be evasions (if defender has the evadeAttack ability),
            // the next 70% to be ordinary losses, and the rest great losses.
            //   r < odds.win + 0.2 * (1.0 - odds.win) = 0.8 * odds.win + 0.2
            //     => evade
            //   else r < odds.win + (0.2 + 0.7) * (1.0 - odds.win)
            //     = 0.1 * odds.win + 0.9 => loss
            //   else => great loss
            // ...and beached ships always lose.
            if (r < odds.win || defenderUnit.isBeached()) {
                great = r < 0.1f * odds.win; // Great Win
                crs.add(CombatResult.WIN);
                resolveAttack(attackerUnit, defenderUnit, great,
                    // Rescale to 0 <= r < 1
                    r / (0.1f * odds.win), crs);
            } else if (r < 0.8f * odds.win + 0.2f
                    && defenderUnit.hasAbility(Ability.EVADE_ATTACK)) {
                crs.add(CombatResult.NO_RESULT);
                crs.add(CombatResult.EVADE_ATTACK);
            } else {
                great = r >= 0.1f * odds.win + 0.9f; // Great Loss
                crs.add(CombatResult.LOSE);
                resolveAttack(defenderUnit, attackerUnit, great,
                    // Rescaling to 0 <= r < 1
                    // (rearrange: 0.8 * odds.win + 0.2 <= r < 1.0)
                    (1.25f * r - 0.25f - odds.win)/(1.0f - odds.win), crs);
            }

        } else if (combatIsBombard(attacker, defender)) {
            Unit defenderUnit = (Unit) defender;
            if (!defenderUnit.isNaval()) {
                // One day we might want:
                //   crs.add(CombatResult.SLAUGHTER_UNIT_BOMBARD);
                throw new IllegalStateException("Bombard of non-naval");
            }
            action = "Bombard";

            // The bombard succeeds.
            if (r <= odds.win) {
                crs.add(CombatResult.WIN);

                // Great wins occur at most in 1 in 3 of successful bombards,
                // Good defences reduce this proportion.
                float offencePower = getOffencePower(attacker, defender);
                float defencePower = getDefencePower(attacker, defender);
                float diff = Math.max(3f, defencePower * 2f - offencePower);
                great = r < odds.win / diff;

                // Sink the defender on great wins or lack of repair
                // location, otherwise just damage.
                if (great || defenderUnit.getRepairLocation() == null) {
                    crs.add(CombatResult.SINK_SHIP_BOMBARD);
                } else {
                    crs.add(CombatResult.DAMAGE_SHIP_BOMBARD);
                }

            // The bombard fails but this is not a win for the
            // defender, just an evasion, as it is not currently given
            // an opportunity to return fire.
            } else {
                crs.add(CombatResult.NO_RESULT);
                crs.add(CombatResult.EVADE_BOMBARD);
            }

        } else {
            throw new IllegalStateException("Bogus combat");
        }

        // Log the results so that we have a solid record of combat
        // determinations for debugging and investigation of user
        // `I just lost N combats' complaints.
        lb.add(" great=", great, " ", action);
        for (CombatResult cr : crs) lb.add(" ", cr);
        lb.log(logger, Level.INFO);

        return crs;
    }

    /**
     * Resolve all the consequences of a normal attack.
     *
     * @param winner The winning <code>Unit</code>.
     * @param loser The losing <code>Unit</code>.
     * @param great True if this is a great win/loss.
     * @param r A "residual" random value (for convert/burn mission).
     * @param crs A list of <code>CombatResult</code>s to add to.
     */
    private void resolveAttack(Unit winner, Unit loser, boolean great,
                               float r, List<CombatResult> crs) {
        Player loserPlayer = loser.getOwner();
        Tile tile = loser.getTile();
        Player winnerPlayer = winner.getOwner();
        boolean attackerWon = crs.get(0) == CombatResult.WIN;
        boolean loserMustDie = loser.hasAbility(Ability.DISPOSE_ON_COMBAT_LOSS);

        if (loser.isNaval()) {
            // Naval victors get to loot the defenders hold.  Sink the
            // loser on great win/loss, lack of repair location, or
            // beached.
            if (winner.isNaval() && winner.canCaptureGoods()
                && !loser.getGoodsList().isEmpty()) {
                crs.add(CombatResult.LOOT_SHIP);
            }
            if (great || loserMustDie
                || loser.getRepairLocation() == null
                || loser.isBeached()) {
                crs.add(CombatResult.SINK_SHIP_ATTACK);
            } else {
                crs.add(CombatResult.DAMAGE_SHIP_ATTACK);
            }

        } else { // loser is land unit
            // Autoequip the defender?
            Role autoRole = (attackerWon) ? loser.getAutomaticRole() : null;
            if (autoRole != null) crs.add(CombatResult.AUTOEQUIP_UNIT);

            // Special handling for settlements
            boolean done = false;
            Settlement settlement = tile.getSettlement();
            if (settlement instanceof Colony) {
                final Colony colony = (Colony)settlement;
                // A Colony falls to Europeans when the last defender
                // is unarmed.  Natives will pillage if possible but
                // otherwise proceed to kill colonists incrementally
                // until the colony falls for lack of survivors.
                // Ships in a falling colony will be damaged or sunk
                // if they have no repair location.
                if (!loser.isDefensiveUnit() && autoRole == null) {
                    List<Unit> ships = colony.getTile().getNavalUnits();
                    final CombatResult shipResult = (ships.isEmpty()) ? null
                        : (ships.get(0).getRepairLocation() == null)
                        ? CombatResult.SINK_COLONY_SHIPS
                        : CombatResult.DAMAGE_COLONY_SHIPS;

                    if (winnerPlayer.isEuropean()) {
                        if (loserMustDie) {
                            crs.add(CombatResult.SLAUGHTER_UNIT);
                        }
                        if (shipResult != null) crs.add(shipResult);
                        crs.add(CombatResult.CAPTURE_COLONY);
                        done = true;

                    } else if (!great && colony.canBePillaged(winner)) {
                        crs.add(CombatResult.PILLAGE_COLONY);
                        done = true;

                    } else if (colony.getUnitCount() > 1
                        || loser.getLocation() == tile) {
                        loserMustDie = true;
                        done = false; // Treat as ordinary combat

                    } else {
                        crs.add(CombatResult.SLAUGHTER_UNIT);
                        if (shipResult != null) crs.add(shipResult);
                        crs.add(CombatResult.DESTROY_COLONY);
                        done = true;
                    }
                }
 
            } else if (settlement instanceof IndianSettlement) {
                final IndianSettlement is = (IndianSettlement)settlement;
                // Attacking and defeating the defender of a native
                // settlement with a mission may yield converts but
                // also may provoke the burning of all missions.
                // Native settlements fall when there are no units
                // present either in-settlement or on the settlement
                // tile.
                int lose = 0;
                if (loserMustDie) {
                    // Add death of loser before any convert captures,
                    // or the RNG might randomly decide to convert the
                    // unit that is then slaughtered.
                    crs.add(CombatResult.SLAUGHTER_UNIT);
                    lose++;
                    // For now, no usual unit combat actions can proceed,
                    // which means we can not expect to capture equipment
                    // from settlements without untangling this dependency.
                    done = true;
                }
                if (attackerWon) {
                    if (r < winner.getConvertProbability()) {
                        if (is.getUnitCount() + tile.getUnitCount() > lose
                            && is.hasMissionary(winnerPlayer)
                            && !combatIsAmphibious(winner, loser)) {
                            crs.add(CombatResult.CAPTURE_CONVERT);
                            lose++;
                        }
                    } else if (r >= 1.0f - winner.getBurnProbability()) {
                        for (IndianSettlement s
                                 : loserPlayer.getIndianSettlements()) {
                            if (s.hasMissionary(winnerPlayer)) {
                                crs.add(CombatResult.BURN_MISSIONS);
                                break;
                            }
                        }
                    }
                }
                if (settlement.getUnitCount() + tile.getUnitCount() <= lose) {
                    crs.add(CombatResult.DESTROY_SETTLEMENT);
                    done = true;
                }
            }

            if (!done) {
                final Role loserRole = loser.getRole();
                // First check if the loser was automatically armed, and
                // if so see if the winner can capture that equipment,
                // which may kill or demote the loser.
                if (autoRole != null) {
                    crs.add((winner.canCaptureEquipment(autoRole) != null)
                        ? CombatResult.CAPTURE_AUTOEQUIP
                        : CombatResult.LOSE_AUTOEQUIP);
                    if (loserMustDie) {
                        crs.add(CombatResult.SLAUGHTER_UNIT);
                    } else if (loser.hasAbility(Ability.DEMOTE_ON_ALL_EQUIPMENT_LOST)) {
                        crs.add(CombatResult.DEMOTE_UNIT);
                    }

                // Some losers are just doomed (e.g. seasonedScout), do not
                // check for capture/demote/lose-equipment.
                } else if (loserMustDie) {
                    crs.add(CombatResult.SLAUGHTER_UNIT);

                // Then check if the user had other offensive
                // role-equipment, that can be captured or lost, which
                // may kill or demote the loser.
                } else if (loserRole.isOffensive()) {
                    crs.add((winner.canCaptureEquipment(loserRole) != null)
                        ? CombatResult.CAPTURE_EQUIP
                        : CombatResult.LOSE_EQUIP);
                    if (loserMustDie
                        || loser.losingEquipmentKillsUnit()) {
                        crs.add(CombatResult.SLAUGHTER_UNIT);
                    } else if (loser.losingEquipmentDemotesUnit()) {
                        crs.add(CombatResult.DEMOTE_UNIT);
                    }

                // But some can be captured.
                } else if (loser.hasAbility(Ability.CAN_BE_CAPTURED)
                    && winner.hasAbility(Ability.CAPTURE_UNITS)
                    && !combatIsAmphibious(winner, loser)) {
                    // Demotion on capture is handled by capture routine.
                    crs.add(CombatResult.CAPTURE_UNIT);

                // Or losing just causes a demotion.
                } else if (loser.getTypeChange(ChangeType.DEMOTION,
                                               loserPlayer) != null) {
                    crs.add(CombatResult.DEMOTE_UNIT);

                // But finally, the default is to kill them.
                } else {
                    crs.add(CombatResult.SLAUGHTER_UNIT);
                }
            }
        }

        // Promote great winners or with automatic promotion, if possible.
        UnitTypeChange promotion = winner.getType()
            .getUnitTypeChange(ChangeType.PROMOTION, winnerPlayer);
        if (promotion != null
            && (winner.hasAbility(Ability.AUTOMATIC_PROMOTION)
                || (great
                    && (100 * (r - Math.floor(r))
                        <= promotion.getProbability(ChangeType.PROMOTION))))) {
            crs.add(CombatResult.PROMOTE_UNIT);
        }
    }

    /**
     * Could this attack be an ambush?
     *
     * @param attacker The attacker.
     * @param defender The defender.
     * @return True if the attack can be an ambush.
     */
    private boolean isAmbush(FreeColGameObject attacker,
                             FreeColGameObject defender) {
        if (attacker instanceof Unit && defender instanceof Unit) {
            Unit attackerUnit = (Unit)attacker;
            Unit defenderUnit = (Unit)defender;
            return attackerUnit.getSettlement() == null
                && attackerUnit.hasTile()
                && defenderUnit.getSettlement() == null
                && defenderUnit.hasTile()
                && (attackerUnit.hasAbility(Ability.AMBUSH_BONUS)
                    || defenderUnit.hasAbility(Ability.AMBUSH_PENALTY))
                && (attackerUnit.getTile().hasAbility(Ability.AMBUSH_TERRAIN)
                    || defenderUnit.getTile().hasAbility(Ability.AMBUSH_TERRAIN));
        }
        return false;
    }
}
