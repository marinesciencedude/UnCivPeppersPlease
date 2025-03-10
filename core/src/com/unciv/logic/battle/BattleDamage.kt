package com.unciv.logic.battle

import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.unit.UnitType
import kotlin.math.max

class BattleDamageModifier(val vs:String,val modificationAmount:Float){
    fun getText(): String = "vs $vs"
}

class BattleDamage{

    private fun getBattleDamageModifiersOfUnit(unit:MapUnit): MutableList<BattleDamageModifier> {
        val modifiers = mutableListOf<BattleDamageModifier>()
        for (ability in unit.getUniques()) {
            // This beut allows us to have generic unit uniques: "Bonus vs City 75%", "Penatly vs Mounted 25%" etc.
            val regexResult = Regex("""(Bonus|Penalty) vs (.*) (\d*)%""").matchEntire(ability)
            if (regexResult == null) continue
            val vs = regexResult.groups[2]!!.value
            val modificationAmount = regexResult.groups[3]!!.value.toFloat() / 100  // if it says 15%, that's 0.15f in modification
            if (regexResult.groups[1]!!.value == "Bonus")
                modifiers.add(BattleDamageModifier(vs, modificationAmount))
            else
                modifiers.add(BattleDamageModifier(vs, -modificationAmount))
        }
        return modifiers
    }


    private fun getGeneralModifiers(combatant: ICombatant, enemy: ICombatant): HashMap<String, Float> {
        val modifiers = HashMap<String, Float>()
        fun addToModifiers(BDM:BattleDamageModifier){
            val text = BDM.getText()
            if(!modifiers.containsKey(text)) modifiers[text]=0f
            modifiers[text]=modifiers[text]!!+BDM.modificationAmount
        }

        if (combatant is MapUnitCombatant) {
            for (BDM in getBattleDamageModifiersOfUnit(combatant.unit)) {
                if (BDM.vs == enemy.getUnitType().toString())
                    addToModifiers(BDM)
                if(BDM.vs == "wounded units" && enemy is MapUnitCombatant && enemy.getHealth()<100)
                    addToModifiers(BDM)
                if(BDM.vs == "land units" && enemy.getUnitType().isLandUnit())
                    addToModifiers(BDM)
            }

            //https://www.carlsguides.com/strategy/civilization5/war/combatbonuses.php
            val civHappiness = combatant.getCivInfo().getHappiness()
            if (civHappiness < 0)
                modifiers["Unhappiness"] = max(0.02f * civHappiness,-0.9f) // otherwise it could exceed -100% and start healing enemy units...

            if(combatant.getCivInfo().policies.isAdopted("Populism") && combatant.getHealth() < 100){
                modifiers["Populism"] = 0.25f
            }

            if(combatant.getCivInfo().policies.isAdopted("Discipline") && combatant.isMelee()
                && combatant.getTile().neighbors.flatMap { it.getUnits() }
                            .any { it.civInfo==combatant.getCivInfo() && !it.type.isCivilian()})
                modifiers["Discipline"] = 0.15f

            val requiredResource = combatant.unit.baseUnit.requiredResource
            if(requiredResource!=null && combatant.getCivInfo().getCivResourcesByName()[requiredResource]!!<0
                    && !combatant.getCivInfo().isBarbarianCivilization()){
                modifiers["Missing resource"]=-0.25f
            }

            //to do : performance improvement
            if (combatant.getUnitType().isLandUnit()) {
                val nearbyCivUnits = combatant.unit.getTile().getTilesInDistance(2)
                        .filter {it.civilianUnit?.civInfo == combatant.unit.civInfo}
                        .map {it.civilianUnit}
                if (nearbyCivUnits.any { it!!.hasUnique("Bonus for land units in 2 radius 15%") }) {
                    modifiers["Great general"]= when {
                        combatant.unit.civInfo.getNation().unique == "Great general provides double combat bonus, and spawns 50% faster" -> 0.3f
                        else -> 0.15f
                    }
                }
            }
        }

        if (combatant.getCivInfo().policies.isAdopted("Honor") && enemy.getCivInfo().isBarbarianCivilization())
            modifiers["vs Barbarians"] = 0.25f

        return modifiers
    }

    fun getAttackModifiers(attacker: ICombatant, defender: ICombatant): HashMap<String, Float> {
        val modifiers = getGeneralModifiers(attacker, defender)

        if(attacker is MapUnitCombatant) {
            modifiers.putAll(getTileSpecificModifiers(attacker,defender.getTile()))

            val defenderTile = defender.getTile()
            val isDefenderInRoughTerrain = defenderTile.isRoughTerrain()
            for (BDM in getBattleDamageModifiersOfUnit(attacker.unit)) {
                val text = BDM.getText()
                if (BDM.vs == "units in open terrain" && !isDefenderInRoughTerrain) {
                    if(modifiers.containsKey(text))
                        modifiers[text] =modifiers[text]!! + BDM.modificationAmount
                    else modifiers[text] = BDM.modificationAmount
                }
                if (BDM.vs == "units in rough terrain" && isDefenderInRoughTerrain) {
                    if (modifiers.containsKey(text))
                        modifiers[text] = modifiers[text]!! + BDM.modificationAmount
                    else modifiers[text] = BDM.modificationAmount
                }
            }

            for (ability in attacker.unit.getUniques()) {
                val regexResult = Regex("""Bonus as Attacker (\d*)%""").matchEntire(ability) //to do: extend to defender, and penalyy
                if (regexResult == null) continue
                val bonus = regexResult.groups[1]!!.value.toFloat() / 100
                if (modifiers.containsKey("Attacker Bonus"))
                    modifiers["Attacker Bonus"] =modifiers["Attacker Bonus"]!! + bonus
                else modifiers["Attacker Bonus"] = bonus
            }
        }

        else if (attacker is CityCombatant) {
            if (attacker.getCivInfo().policies.isAdopted("Oligarchy") && attacker.city.getCenterTile().militaryUnit != null)
                modifiers["Oligarchy"] = 0.5f
        }

        if (attacker.isMelee()) {
            val numberOfAttackersSurroundingDefender = defender.getTile().neighbors.count {
                it.militaryUnit != null
                        && it.militaryUnit!!.owner == attacker.getCivInfo().civName
                        && MapUnitCombatant(it.militaryUnit!!).isMelee()
            }
            if (numberOfAttackersSurroundingDefender > 1)
                modifiers["Flanking"] = 0.1f * (numberOfAttackersSurroundingDefender-1) //https://www.carlsguides.com/strategy/civilization5/war/combatbonuses.php
        }

        if(attacker is MapUnitCombatant && attacker.unit.isEmbarked())
            modifiers["Landing"] = -0.5f

        return modifiers
    }

    fun getDefenceModifiers(attacker: ICombatant, defender: MapUnitCombatant): HashMap<String, Float> {
        if(defender.unit.isEmbarked()) // embarked units get no defensive modifiers
            return HashMap()

        val modifiers = getGeneralModifiers(defender, attacker)

        modifiers.putAll(getTileSpecificModifiers(defender, defender.getTile()))

        if (!defender.unit.hasUnique("No defensive terrain bonus")) {
            val tileDefenceBonus = defender.getTile().getDefensiveBonus()
            if (tileDefenceBonus > 0) modifiers["Terrain"] = tileDefenceBonus
        }

        if(attacker.isRanged()){
            val defenceVsRanged = 0.25f * defender.unit.getUniques().count{it=="+25% Defence against ranged attacks"}
            if(defenceVsRanged>0) modifiers["defence vs ranged"] = defenceVsRanged
        }

        val defenderTile = defender.getTile()
        val isDefenderInRoughTerrain = defenderTile.isRoughTerrain()
        for (BDM in getBattleDamageModifiersOfUnit(defender.unit)) {
            val text = BDM.getText()
            if (BDM.vs == "units in open terrain" && !isDefenderInRoughTerrain) {
                if (modifiers.containsKey(text))
                    modifiers[text] = modifiers[text]!! + BDM.modificationAmount
                else modifiers[text] = BDM.modificationAmount
            }
            if (BDM.vs == "units in rough terrain" && isDefenderInRoughTerrain) {
                if (modifiers.containsKey(text))
                    modifiers[text] = modifiers[text]!! + BDM.modificationAmount
                else modifiers[text] = BDM.modificationAmount
            }
        }

        if (defender.unit.isFortified())
            modifiers["Fortification"] = 0.2f * defender.unit.getFortificationTurns()

        return modifiers
    }

    private fun getTileSpecificModifiers(unit: MapUnitCombatant, tile: TileInfo): HashMap<String,Float> {
        val modifiers = HashMap<String,Float>()
        val isFriendlyTerritory = tile.getOwner()!=null && !unit.getCivInfo().isAtWarWith(tile.getOwner()!!)
        if(isFriendlyTerritory && unit.getCivInfo().getBuildingUniques().contains("+15% combat strength for units fighting in friendly territory"))
            modifiers["Himeji Castle"] = 0.15f
        if(isFriendlyTerritory && unit.unit.hasUnique("+25% bonus inside friendly territory"))
			modifiers["Pepperstotzkan Propaganda Brainwashing"] = 0.25f
        if(!isFriendlyTerritory && unit.unit.hasUnique("+20% bonus outside friendly territory"))
            modifiers["Foreign Land"] = 0.2f

        return modifiers
    }

    private fun modifiersToMultiplicationBonus(modifiers: HashMap<String, Float>): Float {
        // modifiers are like 0.1 for a 10% bonus, -0.1 for a 10% loss
        var finalModifier = 1f
        for (modifierValue in modifiers.values) finalModifier *= (1 + modifierValue)
        return finalModifier
    }

    private fun getHealthDependantDamageRatio(combatant: ICombatant): Float {
        if (combatant.getUnitType() == UnitType.City
                || combatant.getCivInfo().getNation().unique == "Units fight as though they were at full strength even when damaged")
            return 1f
        return 1/2f + combatant.getHealth()/200f // Each point of health reduces damage dealt by 0.5%
    }


    /**
     * Includes attack modifiers
     */
    fun getAttackingStrength(attacker: ICombatant, defender: ICombatant): Float {
        val attackModifier = modifiersToMultiplicationBonus(getAttackModifiers(attacker,defender))
        return attacker.getAttackingStrength() * attackModifier
    }


    /**
     * Includes defence modifiers
     */
    fun getDefendingStrength(attacker: ICombatant, defender: ICombatant): Float {
        var defenceModifier = 1f
        if(defender is MapUnitCombatant) defenceModifier = modifiersToMultiplicationBonus(getDefenceModifiers(attacker,defender))
        return defender.getDefendingStrength() * defenceModifier
    }

    fun calculateDamageToAttacker(attacker: ICombatant, defender: ICombatant): Int {
        if(attacker.isRanged()) return 0
        if(defender.getUnitType().isCivilian()) return 0
        val ratio = getDefendingStrength(attacker,defender) / getAttackingStrength(attacker,defender)
        return (ratio * 30 * getHealthDependantDamageRatio(defender)).toInt()
    }

    fun calculateDamageToDefender(attacker: ICombatant, defender: ICombatant): Int {
        val ratio = getAttackingStrength(attacker,defender) / getDefendingStrength(attacker,defender)
        return (ratio * 30 * getHealthDependantDamageRatio(attacker)).toInt()
    }
}
