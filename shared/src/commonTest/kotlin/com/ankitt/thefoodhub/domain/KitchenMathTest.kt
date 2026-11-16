package com.ankitt.thefoodhub.domain

import com.ankitt.thefoodhub.domain.model.KitchenMath
import com.ankitt.thefoodhub.domain.model.MeasureUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KitchenMathTest {

    @Test
    fun convertsWithinADimensionWithoutADensity() {
        assertEquals(3.0, KitchenMath.convert(1.0, MeasureUnit.Tablespoons, MeasureUnit.Teaspoons)!!, 1e-9)
        assertEquals(16.0, KitchenMath.convert(1.0, MeasureUnit.Pounds, MeasureUnit.Ounces)!!, 1e-9)
        assertEquals(236.5882365, KitchenMath.convert(1.0, MeasureUnit.Cups, MeasureUnit.Milliliters)!!, 1e-9)
        assertEquals(8.0, KitchenMath.convert(1.0, MeasureUnit.Cups, MeasureUnit.FluidOunces)!!, 1e-9)
    }

    @Test
    fun convertsBetweenMassAndVolumeWithTheDensity() {
        // Flour: 125 g per cup.
        assertEquals(312.5, KitchenMath.convert(2.5, MeasureUnit.Cups, MeasureUnit.Grams, gramsPerCup = 125.0)!!, 1e-9)
        assertEquals(2.0, KitchenMath.convert(250.0, MeasureUnit.Grams, MeasureUnit.Cups, gramsPerCup = 125.0)!!, 1e-9)
        assertEquals(16.0, KitchenMath.convert(1.0, MeasureUnit.Cups, MeasureUnit.Tablespoons, gramsPerCup = 125.0)!!, 1e-9)
    }

    @Test
    fun massToVolumeWithoutADensityIsNull() {
        assertNull(KitchenMath.convert(1.0, MeasureUnit.Grams, MeasureUnit.Cups))
        assertNull(KitchenMath.convert(1.0, MeasureUnit.Grams, MeasureUnit.Cups, gramsPerCup = 0.0))
        assertTrue(KitchenMath.needsDensity(MeasureUnit.Grams, MeasureUnit.Cups))
    }

    @Test
    fun parsesDecimalsCommasAndFractions() {
        assertEquals(2.5, KitchenMath.parseAmount("2.5"))
        assertEquals(2.5, KitchenMath.parseAmount(" 2,5 "))
        assertEquals(0.5, KitchenMath.parseAmount("1/2"))
        assertEquals(1.5, KitchenMath.parseAmount("1 1/2"))
        assertNull(KitchenMath.parseAmount(""))
        assertNull(KitchenMath.parseAmount("0"))
        assertNull(KitchenMath.parseAmount("-1"))
        assertNull(KitchenMath.parseAmount("1/0"))
        assertNull(KitchenMath.parseAmount("abc"))
        assertNull(KitchenMath.parseAmount("1 2"))
    }

    @Test
    fun formatsForTheKitchen() {
        assertEquals("313", KitchenMath.formatAmount(312.5))
        assertEquals("125", KitchenMath.formatAmount(125.0))
        assertEquals("14.8", KitchenMath.formatAmount(14.7867))
        assertEquals("2", KitchenMath.formatAmount(2.0))
        assertEquals("0.33", KitchenMath.formatAmount(1.0 / 3))
        assertEquals("1", KitchenMath.formatAmount(0.999))
        assertEquals("0.042", KitchenMath.formatAmount(0.0417))
        assertEquals("0", KitchenMath.formatAmount(0.0))
    }

    @Test
    fun normalizesIngredientNames() {
        assertEquals("brown sugar", KitchenMath.normalizeIngredient("  Brown   Sugar "))
        assertEquals("", KitchenMath.normalizeIngredient("   "))
    }
}
