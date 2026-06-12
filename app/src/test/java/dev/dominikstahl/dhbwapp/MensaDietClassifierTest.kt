package dev.dominikstahl.dhbwapp

import dev.dominikstahl.dhbwapp.remote.models.MenuItem
import dev.dominikstahl.dhbwapp.ui.mensa.DishDiet
import dev.dominikstahl.dhbwapp.ui.mensa.getDishDiet
import dev.dominikstahl.dhbwapp.ui.mensa.matchesFilters
import dev.dominikstahl.dhbwapp.ui.mensa.filterVariants
import org.junit.Assert.assertEquals
import org.junit.Test

class MensaDietClassifierTest {

    @Test
    fun testKnoblauchPasteIsVeganOrVegetarian() {
        val item = MenuItem(
            id = 1,
            name = "Scharfe Knoblauch-Paste mit mediteranem Gemüse und Tofu",
            type = "Veganes Gericht",
            allergens = listOf("n"),
            additives = emptyList()
        )
        val diet = getDishDiet(item)
        assertEquals(DishDiet.VEGAN, diet)
    }

    @Test
    fun testReispfanneMitHaehnchenIsOther() {
        val item = MenuItem(
            id = 2,
            name = "Reispfanne mit Gemüse, Hähnchenstreifen und Zitrone-Dip",
            type = "Klassiker",
            allergens = emptyList(),
            additives = emptyList()
        )
        val diet = getDishDiet(item)
        assertEquals(DishDiet.OTHER, diet)
    }

    @Test
    fun testVeganRiceDoesNotTriggerEggAllergen() {
        val item = MenuItem(
            id = 3,
            name = "Gemüse-Pfanne mit Wildreis und Tofu",
            type = "Veganes Gericht",
            allergens = emptyList(),
            additives = emptyList()
        )
        val diet = getDishDiet(item)
        assertEquals(DishDiet.VEGAN, diet)
    }

    @Test
    fun testVeganSideDishDoesNotTriggerEggAllergen() {
        val item = MenuItem(
            id = 4,
            name = "Falafel-Bällchen mit Salat-Beilage",
            type = "Veganes Gericht",
            allergens = emptyList(),
            additives = emptyList()
        )
        val diet = getDishDiet(item)
        assertEquals(DishDiet.VEGAN, diet)
    }

    @Test
    fun testVariantMergingAndFiltering() {
        val baseItem = MenuItem(
            id = 5,
            name = "Käsespätzle",
            type = "Vegetarisches Gericht",
            allergens = listOf("mi"),
            additives = emptyList(),
            priceStudent = 4.20
        )
        val variantItem = MenuItem(
            id = 6,
            name = "mit Speck",
            type = "Klassiker",
            allergens = emptyList(),
            additives = emptyList(),
            priceStudent = 4.90
        )

        val items = listOf(baseItem, variantItem)
        val grouped = dev.dominikstahl.dhbwapp.ui.mensa.groupMenuItems(items)

        assertEquals(1, grouped.size)
        val merged = grouped[0]
        assertEquals("Käsespätzle", merged.baseItem.name)
        assertEquals(1, merged.variants.size)
        assertEquals("mit Speck", merged.variants[0].name)

        // Test filtering veggie: should keep the group, but we need to check how the variant is filtered
        val matchesVeggie = merged.matchesFilters(veggie = true, under5 = false, lowCo2 = false)
        assertEquals(true, matchesVeggie)

        val filteredVeggie = merged.filterVariants(veggie = true, under5 = false, lowCo2 = false)
        assertEquals(0, filteredVeggie.variants.size)

        // Without veggie filter, variant should be present
        val filteredNone = merged.filterVariants(veggie = false, under5 = false, lowCo2 = false)
        assertEquals(1, filteredNone.variants.size)
        assertEquals("mit Speck", filteredNone.variants[0].name)
    }

    @Test
    fun testLeadingVariantMerging() {
        val variantItem = MenuItem(
            id = 7,
            name = "mit Schinken",
            type = "Klassiker",
            allergens = emptyList(),
            additives = emptyList(),
            priceStudent = 1.50
        )
        val baseItem = MenuItem(
            id = 8,
            name = "Portion Stangenspargel",
            type = "Vegetarisches Gericht",
            allergens = emptyList(),
            additives = emptyList(),
            priceStudent = 3.50
        )

        val items = listOf(variantItem, baseItem)
        val grouped = dev.dominikstahl.dhbwapp.ui.mensa.groupMenuItems(items)

        assertEquals(1, grouped.size)
        val merged = grouped[0]
        assertEquals("Portion Stangenspargel", merged.baseItem.name)
        assertEquals(1, merged.variants.size)
        assertEquals("mit Schinken", merged.variants[0].name)
    }

    @Test
    fun testMeatDishWithVeganSubcomponent() {
        val item = MenuItem(
            id = 9,
            name = "Ofenkartoffel mit Hähnchenstreifen und veganer Kräuterdip",
            type = "Aktion",
            allergens = emptyList(),
            additives = emptyList()
        )
        val diet = getDishDiet(item)
        assertEquals(DishDiet.OTHER, diet)
    }

    @Test
    fun testZucchinigulaschMitVeganemHaehnchen() {
        val item = MenuItem(
            id = 10,
            name = "Zuchinigulsch mit veganem Hähnchen aus Erbsenprotein mit Kräuter, Paprikasoße und Reis",
            type = "Aktion",
            allergens = emptyList(),
            additives = emptyList()
        )
        val diet = getDishDiet(item)
        assertEquals(DishDiet.VEGAN, diet)
    }
}


