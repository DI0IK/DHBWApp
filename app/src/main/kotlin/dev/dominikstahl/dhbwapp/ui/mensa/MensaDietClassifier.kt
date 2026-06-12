package dev.dominikstahl.dhbwapp.ui.mensa

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.dominikstahl.dhbwapp.remote.models.MenuItem
import kotlin.math.min

enum class DishDiet {
    VEGAN,
    VEGETARIAN,
    OTHER
}

data class MergedMenuItem(
    val baseItem: MenuItem,
    val variants: List<MenuItem>
)

/**
 * State-of-the-Art Heuristics Engine for German Mensa Data.
 * Uses exact word boundaries (\b) and pre-compiled regex for O(1) initialization 
 * and lightning-fast execution on the main thread.
 */
private object DietHeuristics {
    // Safe to match anywhere in the string (handles compounds like Schweinebraten, Leberkäse)
    val meatCompoundRegex = Regex(
        "schwein|speck|schinken|salami|kassler|kasseler|sülze|rinder|rindfleisch|ochsen|kalb|roastbeef|" +
        "entrecote|bifteki|gyros|souvlaki|suvlaki|kebab|cevapcici|cevapi|carbonara|hähnchen|" +
        "hänchen|hühnchen|huhn|pute|puten|geflügel|ente|enten|truthahn|hahn|nugget|nuggets|fisch|" +
        "lachs|garnele|garnelen|thunfisch|seelachs|kabeljau|forelle|zander|scholle|krabbe|krabben|" +
        "hummer|muschel|muscheln|tintenfisch|calamari|lamm|hirsch|reh|fasan|hasen|schnitzel|" +
        "frikadelle|frikadellen|gulasch|bolognese|bolonese|lasagne|patty|patties|steak|leber|" +
        "wurst|fleisch|chorizo|bacon|pancetta|prosciutto|merguez|sucuk|cordon\\s?bleu|maultaschen",
        RegexOption.IGNORE_CASE
    )

    // Strict boundaries to prevent false positives (e.g., "braten" in "gebraten", "wild" in "Wildreis")
    val meatStrictRegex = Regex(
        "\\b(rind|wild|braten|hase|hack|carne)\\b",
        RegexOption.IGNORE_CASE
    )

    val dairyCompoundRegex = Regex(
        "milch|laktose|quark|käse|sahne|butter|honig|schafskäse|feta|gelatine|lab",
        RegexOption.IGNORE_CASE
    )

    // "Ei" needs strict boundaries to avoid matching "Freitag", "Brei", "Allerlei"
    val eggStrictRegex = Regex(
        "\\b(ei|eier)\\b",
        RegexOption.IGNORE_CASE
    )

    // ONLY explicit tags that directly modify/precede/follow a meat word (e.g. "Veganes Schnitzel", "Schnitzel (vegetarisch)")
    val explicitSubstituteRegex = Regex(
        "\\b(vegan|vegane|veganer|veganes|veganen|veganem|vegetarisch|vegetarische|vegetarischer|vegetarisches|vegetarischen|vegetarischem|veggie|pflanzlich|pflanzliche|pflanzlicher|pflanzliches|pflanzlichen|pflanzlichem|plant-based|meatless|fleischlos)[\\s\\-]*(schnitzel|steak|braten|frikadelle|frikadellen|burger|patty|patties|bolognese|gyros|nuggets|wurst|gulasch|geschnetzeltes|medaillons|maultaschen|hack|fleisch|hähnchen|hänchen|hühnchen|huhn|pute|puten|geflügel|rind|rinder|rindfleisch|schwein|schweine|speck|schinken|salami|kassler|kasseler|schnitzel)|\\b(schnitzel|steak|braten|frikadelle|frikadellen|burger|patty|patties|bolognese|gyros|nuggets|wurst|gulasch|geschnetzeltes|medaillons|maultaschen|hack|fleisch|hähnchen|hänchen|hühnchen|huhn|pute|puten|geflügel|rind|rinder|rindfleisch|schwein|schweine|speck|schinken|salami|kassler|kasseler|schnitzel)[\\s\\-]*\\((vegan|vegane|veganer|veganes|veganen|veganem|vegetarisch|vegetarische|vegetarischer|vegetarisches|vegetarischen|vegetarischem|veggie|pflanzlich|pflanzliche|pflanzlicher|pflanzliches|pflanzlichen|pflanzlichem|plant-based)\\)",
        RegexOption.IGNORE_CASE
    )

    // Handles compound substitutes like "Tofusteak", "Soja-Bolognese", "Gemüsefrikadelle", "Sojahack"
    val substituteCompoundRegex = Regex(
        "(tofu|soja|seitan|gemüse|weizen|erbsen|linsen|kichererbsen|hafer|grünkern|pflanzen|quorn)[\\s\\-]*(schnitzel|steak|braten|frikadelle|frikadellen|burger|patty|patties|bolognese|gyros|nuggets|wurst|gulasch|geschnetzeltes|medaillons|maultaschen|hack)",
        RegexOption.IGNORE_CASE
    )

    val dairyEggNegators = listOf("hafermilch", "sojamilch", "mandelmilch", "reismilch", "kokosmilch", "veganer käse")
}

/**
 * Determines the diet of a menu item using an NLP-inspired rule engine.
 */
fun getDishDiet(item: MenuItem): DishDiet {
    val typeRaw = item.type.lowercase().trim()
    val nameRaw = item.name.lowercase().trim()

    val cleanedName = extractAllergensAndCleanName(item.name).first.lowercase()
    val hasMeatKeyword = DietHeuristics.meatCompoundRegex.containsMatchIn(cleanedName) || 
                         DietHeuristics.meatStrictRegex.containsMatchIn(cleanedName)
    val isExplicitSubstitute = DietHeuristics.substituteCompoundRegex.containsMatchIn(cleanedName) ||
                               DietHeuristics.explicitSubstituteRegex.containsMatchIn(cleanedName)

    // Early exit: if it has meat and is not a substitute, it is OTHER (Klassiker)
    if (hasMeatKeyword && !isExplicitSubstitute) {
        return DishDiet.OTHER
    }

    // 1. Check explicit API tags first (Highest Trust)
    if (typeRaw.contains("vegan") || (nameRaw.contains("vegan") && !hasMeatKeyword)) {
        // Double check allergens just in case the API tagged "vegan" but includes real cheese
        val (_, additives) = extractAllergensAndCleanName(item.name)
        val (apiAllergens, _) = parseAllergensAndAdditives(item.allergens, item.additives)
        val allAllergens = (additives + apiAllergens).map { it.trim().lowercase() }
        
        if (allAllergens.any { it == "milch/laktose" || it == "ei" || it == "fisch" || it == "lab" }) {
            return if (allAllergens.any { it == "fisch" || it == "krebstiere" || it == "weichtiere" }) DishDiet.OTHER else DishDiet.VEGETARIAN
        }
        return DishDiet.VEGAN
    }

    if (typeRaw.contains("vegetarisch") || typeRaw.contains("veggie") || 
        ((nameRaw.contains("vegetarisch") || nameRaw.contains("veggie")) && !hasMeatKeyword)) {
        return DishDiet.VEGETARIAN
    }

    // 2. Normalize and extract data
    val nameAllergens = extractAllergensAndCleanName(item.name).second
    val (apiAllergens, apiAdditives) = parseAllergensAndAdditives(item.allergens, item.additives)
    val allAllergens = (nameAllergens + apiAllergens + apiAdditives).map { it.trim().lowercase() }

    // 3. Evaluate rules
    val hasMeatAllergen = allAllergens.any { it.contains("fisch") || it.contains("krebstiere") || it.contains("weichtiere") }
    val hasDairyEggAllergen = allAllergens.any { it.contains("milch") || it.contains("laktose") || it == "ei" || it == "lab" }
    
    val hasDairyEggKeyword = (DietHeuristics.dairyCompoundRegex.containsMatchIn(cleanedName) || 
                             DietHeuristics.eggStrictRegex.containsMatchIn(cleanedName)) && 
            !DietHeuristics.dairyEggNegators.any { cleanedName.contains(it) }

    // 4. Resolve Conflicts (Decision Tree)
    if (hasMeatAllergen) return DishDiet.OTHER
    
    if (hasMeatKeyword) {
        if (isExplicitSubstitute) {
            return if (hasDairyEggAllergen || hasDairyEggKeyword) DishDiet.VEGETARIAN else DishDiet.VEGAN
        }
        return DishDiet.OTHER
    }

    if (hasDairyEggAllergen || hasDairyEggKeyword) return DishDiet.VEGETARIAN

    // Fallback: If no meat/dairy identified, but not explicitly tagged vegan, 
    // it's safer to classify as vegetarian unless we are very confident.
    return DishDiet.VEGETARIAN
}

@Composable
fun getDietColors(diet: DishDiet): Triple<Color, Color, String> {
    val isDark = MaterialTheme.colorScheme.surface.let { color ->
        val luminance = (color.red * 0.299f + color.green * 0.587f + color.blue * 0.114f)
        luminance < 0.5f
    }
    return when (diet) {
        DishDiet.VEGAN -> if (isDark) {
            Triple(Color(0xFF81C784), Color(0xFF1B5E20).copy(alpha = 0.4f), "🌱 Vegan")
        } else {
            Triple(Color(0xFF1B5E20), Color(0xFFE8F5E9), "🌱 Vegan")
        }
        DishDiet.VEGETARIAN -> if (isDark) {
            Triple(Color(0xFF4DB6AC), Color(0xFF004D40).copy(alpha = 0.4f), "🥛 Veggie")
        } else {
            Triple(Color(0xFF004D40), Color(0xFFE0F2F1), "🥛 Veggie")
        }
        DishDiet.OTHER -> if (isDark) {
            Triple(Color(0xFFFF8A65), Color(0xFFBF360C).copy(alpha = 0.4f), "🍖 Klassiker")
        } else {
            Triple(Color(0xFFBF360C), Color(0xFFFBE9E7), "🍖 Klassiker")
        }
    }
}

/**
 * Groups variants using a combination of semantic prefixes and fuzzy string matching (Levenshtein).
 * Supports both trailing and leading variants (where a variant appears before the base item due to sorting/indexing).
 */
fun groupMenuItems(items: List<MenuItem>): List<MergedMenuItem> {
    if (items.isEmpty()) return emptyList()

    val isExplicitVariant = BooleanArray(items.size) { idx ->
        val name = items[idx].name.trim()
        name.startsWith("mit ", ignoreCase = true) ||
                name.startsWith("oder ", ignoreCase = true) ||
                name.startsWith("und ", ignoreCase = true) ||
                name.startsWith("inkl. ", ignoreCase = true) ||
                (name.isNotEmpty() && name[0].isLowerCase())
    }

    val tempMerged = mutableListOf<MergedMenuItem>()
    for (i in items.indices) {
        val item = items[i]
        var added = false

        if (tempMerged.isNotEmpty()) {
            val lastMerged = tempMerged.last()
            
            if (isExplicitVariant[i]) {
                tempMerged[tempMerged.lastIndex] = lastMerged.copy(variants = lastMerged.variants + item)
                added = true
            } else {
                val baseName = extractAllergensAndCleanName(lastMerged.baseItem.name).first
                val currentName = extractAllergensAndCleanName(item.name).first
                val isFuzzyVariant = calculateLevenshteinDistance(baseName, currentName) < (baseName.length * 0.3)
                if (isFuzzyVariant) {
                    tempMerged[tempMerged.lastIndex] = lastMerged.copy(variants = lastMerged.variants + item)
                    added = true
                }
            }
        }

        if (!added) {
            tempMerged.add(MergedMenuItem(baseItem = item, variants = emptyList()))
        }
    }

    val result = mutableListOf<MergedMenuItem>()
    val leadingVariants = mutableListOf<MenuItem>()

    for (mergedItem in tempMerged) {
        val baseItemIndex = items.indexOf(mergedItem.baseItem)
        val isLeadingVariant = baseItemIndex >= 0 && isExplicitVariant[baseItemIndex]
        
        if (isLeadingVariant && result.isEmpty()) {
            leadingVariants.add(mergedItem.baseItem)
            leadingVariants.addAll(mergedItem.variants)
        } else {
            if (leadingVariants.isNotEmpty()) {
                result.add(mergedItem.copy(variants = leadingVariants + mergedItem.variants))
                leadingVariants.clear()
            } else {
                result.add(mergedItem)
            }
        }
    }

    if (leadingVariants.isNotEmpty()) {
        leadingVariants.forEach {
            result.add(MergedMenuItem(baseItem = it, variants = emptyList()))
        }
    }

    return result
}


fun MergedMenuItem.matchesFilters(
    veggie: Boolean,
    under5: Boolean,
    lowCo2: Boolean
): Boolean {
    if (veggie) {
        val diet = getDishDiet(baseItem)
        if (diet != DishDiet.VEGAN && diet != DishDiet.VEGETARIAN) return false
    }
    if (under5) {
        val price = baseItem.priceStudent ?: 0.0
        if (price >= 5.0) return false
    }
    if (lowCo2) {
        val co2 = baseItem.co2Portion ?: Double.MAX_VALUE
        if (co2 >= 200.0) return false
    }
    return true
}

fun MergedMenuItem.filterVariants(
    veggie: Boolean,
    under5: Boolean,
    lowCo2: Boolean
): MergedMenuItem {
    val filtered = variants.filter { variant ->
        if (veggie) {
            val diet = getDishDiet(variant)
            if (diet != DishDiet.VEGAN && diet != DishDiet.VEGETARIAN) return@filter false
        }
        if (under5) {
            val price = variant.priceStudent ?: 0.0
            if (price >= 5.0) return@filter false
        }
        if (lowCo2) {
            val co2 = variant.co2Portion ?: Double.MAX_VALUE
            if (co2 >= 200.0) return@filter false
        }
        true
    }
    return this.copy(variants = filtered)
}


fun parseAllergensAndAdditives(
    itemAllergens: List<String>?,
    itemAdditives: List<String>?
): Pair<List<String>, List<String>> {
    val rawAllergens = (itemAllergens.orEmpty() + itemAdditives.orEmpty())
        .filter { it.isNotBlank() }
        .distinct()

    val allergensList = mutableListOf<String>()
    val additivesList = mutableListOf<String>()

    rawAllergens.forEach { code ->
        val cleaned = code.trim().lowercase()
        if (cleaned.toIntOrNull() != null) {
            val name = when (cleaned) {
                "1" -> "Farbstoff"
                "2" -> "Konservierungsstoffe"
                "3" -> "Antioxidationsmittel"
                "4" -> "Geschmacksverstärker"
                "5" -> "Geschwefelt"
                "6" -> "Geschwärzt"
                "7" -> "Gewachst"
                "8" -> "Phosphat"
                "9" -> "Süßungsmittel"
                "10" -> "Phenylalaninquelle"
                "12" -> "Unter Schutzatmosphäre verpackt"
                else -> "Zusatzstoff $code"
            }
            additivesList.add(name)
        } else {
            val name = when (cleaned) {
                "we", "gl", "a" -> "Gluten/Weizen"
                "mi", "la", "ml", "g" -> "Milch/Laktose"
                "ei", "c" -> "Ei"
                "so", "f" -> "Soja"
                "se", "l" -> "Sellerie"
                "sf", "m" -> "Senf"
                "sn", "sa", "i" -> "Sesam"
                "fi", "d" -> "Fisch"
                "kr", "b" -> "Krebstiere"
                "er", "e" -> "Erdnüsse"
                "nu", "h", "n" -> "Nüsse"
                "su", "o" -> "Schwefeldioxid"
                "lu", "p" -> "Lupinen"
                "wt" -> "Weichtiere"
                "lab" -> "Lab"
                "di" -> "Dinkel"
                else -> null
            }
            if (name != null) {
                allergensList.add(name)
            } else {
                if (code.length > 2 && code.all { it.isUpperCase() }) {
                    additivesList.add(code)
                } else {
                    allergensList.add(code)
                }
            }
        }
    }

    return Pair(allergensList.distinct().sorted(), additivesList.distinct().sorted())
}

fun extractAllergensAndCleanName(fullName: String): Pair<String, List<String>> {
    val regex = Regex("\\(([^)]+)\\)\\s*$")
    val matchResult = regex.find(fullName)

    if (matchResult != null) {
        val codesString = matchResult.groupValues[1]
        val cleanName = fullName.replace(regex, "").trim()
        val codes = codesString.split(",").map { it.trim() }

        val translated = codes.map { code ->
            when (code.lowercase()) {
                "gl", "we", "a" -> "Gluten"
                "la", "mi", "g" -> "Milch/Laktose"
                "ei", "c" -> "Ei"
                "so", "f" -> "Soja"
                "se", "l" -> "Sellerie"
                "sf", "m" -> "Senf"
                "nu", "h", "n" -> "Nüsse"
                "fi", "d" -> "Fisch"
                "kr", "b" -> "Krebstiere"
                "er", "e" -> "Erdnüsse"
                "sa", "i" -> "Sesam"
                "su", "o" -> "Schwefeldioxid"
                "lu", "p" -> "Lupinen"
                "wt" -> "Weichtiere"
                else -> code
            }
        }
        return Pair(cleanName, translated)
    }

    return Pair(fullName, emptyList())
}

/**
 * Computes the Levenshtein distance between two strings.
 * Used to intelligently detect menu item variants (e.g. "Pizza Salami klein" vs "Pizza Salami groß").
 */
private fun calculateLevenshteinDistance(s1: String, s2: String): Int {
    if (s1 == s2) return 0
    if (s1.isEmpty()) return s2.length
    if (s2.isEmpty()) return s1.length

    val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

    for (i in 0..s1.length) dp[i][0] = i
    for (j in 0..s2.length) dp[0][j] = j

    for (i in 1..s1.length) {
        for (j in 1..s2.length) {
            val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
            dp[i][j] = min(
                min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                dp[i - 1][j - 1] + cost
            )
        }
    }
    return dp[s1.length][s2.length]
}