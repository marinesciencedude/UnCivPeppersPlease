package com.unciv.models.gamebasics

import com.unciv.Constants
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.IConstruction
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.gamebasics.tech.Technology
import com.unciv.models.stats.NamedStats
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats

class Building : NamedStats(), IConstruction{

    override val description: String
        get() = getDescription(false, null)

    var requiredTech: String? = null

    var cost: Int = 0
    var maintenance = 0
    var percentStatBonus: Stats? = null
    var specialistSlots: Stats? = null
    var greatPersonPoints: Stats? = null
    /** Extra cost percentage when purchasing */
    var hurryCostModifier: Int = 0
    var isWonder = false
    var isNationalWonder = false
    var requiredBuilding: String? = null
    var requiredBuildingInAllCities: String? = null
    /** A strategic resource that will be consumed by this building */
    var requiredResource: String? = null
    /** City can only be built if one of these resources is nearby - it must be improved! */
    var requiredNearbyImprovedResources: List<String>? = null
    var cannotBeBuiltWith: String? = null
    var cityStrength=0
    var cityHealth=0
    var xpForNewUnits=0
    var replaces:String?=null
    var uniqueTo:String?=null

    // Uniques
    var providesFreeBuilding: String? = null
    var freeTechs: Int = 0
    var uniques = ArrayList<String>()


    /**
     * The bonus stats that a resource gets when this building is built
     */
    var resourceBonusStats: Stats? = null

    fun getRequiredTech(): Technology = GameBasics.Technologies[requiredTech]!!

    fun getShortDescription(): String { // should fit in one line
        val infoList= mutableListOf<String>()
        val str = getStats(null).toString()
        if(str.isNotEmpty()) infoList += str
        for(stat in getStatPercentageBonuses(null).toHashMap())
            if(stat.value!=0f) infoList+="+${stat.value.toInt()}% ${stat.key.toString().tr()}"

        val improvedResources = GameBasics.TileResources.values.filter { it.building==name }.map { it.name.tr() }
        if(improvedResources.isNotEmpty()){
            // buildings that improve resources
            infoList += improvedResources.joinToString()+ " {provide} ".tr()+ resourceBonusStats.toString()
        }
        if(requiredNearbyImprovedResources!=null)
            infoList += ("Requires worked ["+requiredNearbyImprovedResources!!.joinToString("/"){it.tr()}+"] near city").tr()
        if(uniques.isNotEmpty()) infoList += uniques.joinToString { it.tr() }
        if(cityStrength!=0) infoList+="{City strength} +".tr()+cityStrength
        if(cityHealth!=0) infoList+="{City health} +".tr()+cityHealth
        if(xpForNewUnits!=0) infoList+= "+$xpForNewUnits {XP for new units}".tr()
        return infoList.joinToString()
    }

    fun getDescription(forBuildingPickerScreen: Boolean, civInfo: CivilizationInfo?): String {
        val stats = getStats(civInfo)
        val stringBuilder = StringBuilder()
        if(uniqueTo!=null) stringBuilder.appendln("Unique to [$uniqueTo], replaces [$replaces]".tr())
        if (!forBuildingPickerScreen) stringBuilder.appendln("{Cost}: $cost".tr())
        if (isWonder) stringBuilder.appendln("Wonder".tr())
        if(isNationalWonder) stringBuilder.appendln("National Wonder".tr())
        if (!forBuildingPickerScreen && requiredTech != null)
            stringBuilder.appendln("Required tech: [$requiredTech]".tr())
        if (!forBuildingPickerScreen && requiredBuilding != null)
            stringBuilder.appendln("Requires [$requiredBuilding] to be built in the city".tr())
        if (!forBuildingPickerScreen && requiredBuildingInAllCities != null)
            stringBuilder.appendln("Requires [$requiredBuildingInAllCities] to be built in all cities".tr())
        if(requiredResource!=null)
            stringBuilder.appendln("Requires [$requiredResource]".tr())
        if (providesFreeBuilding != null)
            stringBuilder.appendln("Provides a free [$providesFreeBuilding] in the city".tr())
        if(uniques.isNotEmpty()) stringBuilder.appendln(uniques.asSequence().map { it.tr() }.joinToString("\n"))
        if (stats.toString() != "")
            stringBuilder.appendln(stats)

        val percentStats = getStatPercentageBonuses(civInfo)
        if (percentStats.production != 0f) stringBuilder.append("+" + percentStats.production.toInt() + "% {Production}\n".tr())
        if (percentStats.gold != 0f) stringBuilder.append("+" + percentStats.gold.toInt() + "% {Gold}\n".tr())
        if (percentStats.science != 0f) stringBuilder.append("+" + percentStats.science.toInt() + "% {Science}\r\n".tr())
        if (percentStats.food != 0f) stringBuilder.append("+" + percentStats.food.toInt() + "% {Food}\n".tr())
        if (percentStats.culture != 0f) stringBuilder.append("+" + percentStats.culture.toInt() + "% {Culture}\r\n".tr())

        if (this.greatPersonPoints != null) {
            val gpp = this.greatPersonPoints!!
            if (gpp.production != 0f) stringBuilder.appendln("+" + gpp.production.toInt()+" " + "[Great Engineer] points".tr())
            if (gpp.gold != 0f) stringBuilder.appendln("+" + gpp.gold.toInt() + " "+"[Great Merchant] points".tr())
            if (gpp.science != 0f) stringBuilder.appendln("+" + gpp.science.toInt() + " "+"[Great Scientist] points".tr())
            if (gpp.culture != 0f) stringBuilder.appendln("+" + gpp.culture.toInt() + " "+"[Great Artist] points".tr())
        }
        if (resourceBonusStats != null) {
            val resources = GameBasics.TileResources.values.filter { name == it.building }.joinToString { it.name.tr() }
            stringBuilder.appendln("$resources {provide} $resourceBonusStats".tr())
        }

        if(requiredNearbyImprovedResources!=null)
            stringBuilder.appendln(("Requires worked ["+requiredNearbyImprovedResources!!.joinToString("/"){it.tr()}+"] near city").tr())

        if(cityStrength!=0) stringBuilder.appendln("{City strength} +".tr() + cityStrength)
        if(cityHealth!=0) stringBuilder.appendln("{City health} +".tr() + cityHealth)
        if(xpForNewUnits!=0) stringBuilder.appendln("+$xpForNewUnits {XP for new units}".tr())
        if (maintenance != 0)
            stringBuilder.appendln("{Maintenance cost}: $maintenance {Gold}".tr())
        return stringBuilder.toString().trim()
    }

    val cultureBuildings = hashSetOf("Monument", "Temple", "Monastery")

    fun getStats(civInfo: CivilizationInfo?): Stats {
        val stats = this.clone()
        if(civInfo != null) {
            val adoptedPolicies = civInfo.policies.adoptedPolicies
            val baseBuildingName = getBaseBuilding().name

            if (adoptedPolicies.contains("Organized Religion") && cultureBuildings.contains(baseBuildingName ))
                stats.happiness += 1

            if (adoptedPolicies.contains("Free Religion") && cultureBuildings.contains(baseBuildingName ))
                stats.culture += 1f

            if (adoptedPolicies.contains("Entrepreneurship") && hashSetOf("Mint", "Market", "Bank", "Stock Market").contains(baseBuildingName ))
                stats.science += 1f

            if (adoptedPolicies.contains("Humanism") && hashSetOf("University", "Observatory", "Public School").contains(baseBuildingName ))
                stats.happiness += 1f

            if (adoptedPolicies.contains("Rationalism Complete") && !isWonder && stats.science > 0)
                stats.gold += 1f

            if (adoptedPolicies.contains("Constitution") && isWonder)
                stats.culture += 2f

            if (adoptedPolicies.contains("Autocracy Complete") && cityStrength > 0)
                stats.happiness += 1

            if (baseBuildingName == "Castle"
                    && civInfo.getBuildingUniques().contains("+1 happiness, +2 culture and +3 gold from every Castle")){
                stats.happiness+=1
                stats.culture+=2
                stats.gold+=3
            }
        }
        return stats
    }

    fun getStatPercentageBonuses(civInfo: CivilizationInfo?): Stats {
        val stats = percentStatBonus
        if(stats==null) return Stats() // empty
        if(civInfo==null) return stats // initial stats

        val adoptedPolicies = civInfo.policies.adoptedPolicies
        val baseBuildingName = getBaseBuilding().name

        if (adoptedPolicies.contains("Theocracy") && baseBuildingName == "Temple")
            stats.gold = 10f

        if (adoptedPolicies.contains("Free Thought") && baseBuildingName == "University")
            stats.science = 50f

        if(uniques.contains("+5% Production for every Trade Route with a City-State in the empire"))
            stats.production += 5*civInfo.citiesConnectedToCapital.count { it.civInfo.isCityState() }

        return stats
    }

    override fun canBePurchased(): Boolean {
        return !isWonder && !isNationalWonder
    }

    override fun getProductionCost(adoptedPolicies: HashSet<String>): Int {
        if (!isWonder && culture != 0f && adoptedPolicies.contains("Piety"))
            return (cost * 0.85).toInt()
        else return cost
    }

    override fun getGoldCost(civInfo: CivilizationInfo, baseCost: Boolean): Int {
        // https://forums.civfanatics.com/threads/rush-buying-formula.393892/
        var cost: Double
        if (baseCost) {
            cost = Math.pow((30 * getProductionCost(hashSetOf())).toDouble(), 0.75) * (1 + hurryCostModifier / 100)
        } else {
            cost = Math.pow((30 * getProductionCost(civInfo.policies.adoptedPolicies)).toDouble(), 0.75) * (1 + hurryCostModifier / 100)
            if (civInfo.policies.adoptedPolicies.contains("Mercantilism")) cost *= 0.75
            if (civInfo.getBuildingUniques().contains("-15% to purchasing items in cities")) cost *= 0.85
            if (civInfo.policies.adoptedPolicies.contains("Patronage")
                    && listOf("Monument", "Temple", "Opera House", "Museum", "Broadcast Tower").contains(name)) cost *= 0.5
        }

        return (cost / 10).toInt() * 10
    }


    override fun shouldBeDisplayed(construction: CityConstructions): Boolean {
        val rejectionReason = getRejectionReason(construction)
        return rejectionReason==""
                || rejectionReason.startsWith("Requires")
                || rejectionReason == "Wonder is being built elsewhere"
    }

    fun getRejectionReason(construction: CityConstructions):String{
        if (construction.isBuilt(name)) return "Already built"

        if ("Must be next to desert" in uniques
                && !construction.cityInfo.getCenterTile().getTilesInDistance(1).any { it.baseTerrain == "Desert" })
            return "Must be next to desert"

        if ("Must be next to mountain" in uniques
                && !construction.cityInfo.getCenterTile().neighbors.any { it.baseTerrain == Constants.mountain })
            return "Must be next to mountain"

        if("Can only be built in coastal cities" in uniques
                && !construction.cityInfo.getCenterTile().neighbors.any { it.baseTerrain==Constants.coast })
            return "Can only be built in coastal cities"

        val civInfo = construction.cityInfo.civInfo
        if (uniqueTo!=null && uniqueTo!=civInfo.civName) return "Unique to $uniqueTo"
        if (GameBasics.Buildings.values.any { it.uniqueTo==civInfo.civName && it.replaces==name }) return "Our unique building replaces this"
        if (requiredTech != null && !civInfo.tech.isResearched(requiredTech!!)) return "$requiredTech not researched"

        // Regular wonders
        if (isWonder){
            if(civInfo.gameInfo.civilizations.flatMap { it.cities }
                            .any {it.cityConstructions.isBuilt(name)})
                return "Wonder is already built"

            if(civInfo.cities.any { it!=construction.cityInfo && it.cityConstructions.isBeingConstructed(name) })
                return "Wonder is being built elsewhere"

            if(civInfo.isCityState())
                return "No world wonders for city-states"
        }


        // National wonders
        if(isNationalWonder) {
            if (civInfo.cities.any {it.cityConstructions.isBuilt(name) })
                return "National Wonder is already built"
            if (requiredBuildingInAllCities!=null
                    && civInfo.cities.any { !it.cityConstructions
                            .containsBuildingOrEquivalent(requiredBuildingInAllCities!!) })
                return "Requires a [$requiredBuildingInAllCities] in all cities"
            if (civInfo.cities.any {it!=construction.cityInfo && it.cityConstructions.isBeingConstructed(name) })
                return "National Wonder is being built elsewhere"
            if(civInfo.isCityState())
                return "No national wonders for city-states"
        }

        if (requiredBuilding != null && !construction.containsBuildingOrEquivalent(requiredBuilding!!))
            return "Requires a [$requiredBuilding] in this city"
        if (cannotBeBuiltWith != null && construction.isBuilt(cannotBeBuiltWith!!))
            return "Cannot be built with $cannotBeBuiltWith"

        if (requiredResource != null && !civInfo.hasResource(requiredResource!!))
            return "Requires [$requiredResource]"

        if (requiredNearbyImprovedResources != null) {
            val containsResourceWithImprovement = construction.cityInfo.getTilesInRange()
                    .any {
                        it.resource != null
                                && requiredNearbyImprovedResources!!.contains(it.resource!!)
                                && it.getTileResource().improvement == it.improvement
                                && it.getOwner() == civInfo
                    }
            if (!containsResourceWithImprovement) return "Nearby $requiredNearbyImprovedResources required"
        }

        if ("Spaceship part" in uniques) {
            if (!civInfo.getBuildingUniques().contains("Enables construction of Spaceship parts")) return "Apollo project not built!"
            if (civInfo.victoryManager.unconstructedSpaceshipParts()[name] == 0) return "Don't need to build any more of these!"
        }
        return ""
    }

    override fun isBuildable(construction: CityConstructions): Boolean {
        return getRejectionReason(construction)==""
    }

    override fun postBuildEvent(construction: CityConstructions) {
        val civInfo = construction.cityInfo.civInfo

        if ("Spaceship part" in uniques) {
            civInfo.victoryManager.currentsSpaceshipParts.add(name, 1)
            return
        }
        construction.addBuilding(name)

        if (providesFreeBuilding != null && !construction.containsBuildingOrEquivalent(providesFreeBuilding!!)) {
            var buildingToAdd = providesFreeBuilding!!

            for(building in GameBasics.Buildings.values)
                if(building.replaces == buildingToAdd && building.uniqueTo==civInfo.civName)
                    buildingToAdd = building.name

            construction.addBuilding(buildingToAdd)
        }

        when {
            "Empire enters golden age" in uniques -> civInfo.goldenAges.enterGoldenAge()
            "Free Great Artist Appears" in uniques -> civInfo.addGreatPerson("Great Artist", construction.cityInfo)
            "Free great scientist appears" in uniques -> civInfo.addGreatPerson("Great Scientist", construction.cityInfo)
            "2 free great scientists appear" in uniques -> {
                civInfo.addGreatPerson("Great Scientist", construction.cityInfo)
                civInfo.addGreatPerson("Great Scientist", construction.cityInfo)
            }
            "Provides 2 free workers" in uniques -> {
                civInfo.placeUnitNearTile(construction.cityInfo.location, Constants.worker)
                civInfo.placeUnitNearTile(construction.cityInfo.location, Constants.worker)
            }
            "Free Social Policy" in uniques -> civInfo.policies.freePolicies++
            "Free Great Person" in uniques -> {
                if (civInfo.isPlayerCivilization()) civInfo.greatPeople.freeGreatPeople++
                else civInfo.addGreatPerson(GameBasics.Units.keys.filter { it.startsWith("Great") }.random())
            }
            "+1 population in each city" in uniques -> {
                for(city in civInfo.cities){
                    city.population.population += 1
                    city.population.autoAssignPopulation()
                }
            }
            "Enemy land units must spend 1 extra movement point when inside your territory (obsolete upon Dynamite)" in uniques ->
                civInfo.updateHasActiveGreatWall()
        }

        if (freeTechs != 0) civInfo.tech.freeTechs += freeTechs
    }

    fun isStatRelated(stat: Stat): Boolean {
        if (get(stat) > 0) return true
        if (getStatPercentageBonuses(null).get(stat)>0) return true
        if (specialistSlots!=null && specialistSlots!!.get(stat)>0) return true
        return false
    }

    fun getBaseBuilding(): Building {
        if(replaces==null) return this
        else return GameBasics.Buildings[replaces!!]!!
    }
}
