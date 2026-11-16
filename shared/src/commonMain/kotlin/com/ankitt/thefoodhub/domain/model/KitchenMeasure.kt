package com.ankitt.thefoodhub.domain.model

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/** What a [MeasureUnit] measures. */
enum class MeasureDimension { Mass, Volume }

/**
 * Kitchen units. [baseSize] is the unit's size in grams (mass) or millilitres (volume), in US
 * customary measures, which is what Spoonacular uses.
 *
 * Units of the same [dimension] convert with fixed factors, offline and for any ingredient.
 * Converting between mass and volume needs the ingredient's density (see [KitchenMath]).
 */
enum class MeasureUnit(val dimension: MeasureDimension, internal val baseSize: Double) {
    Grams(MeasureDimension.Mass, 1.0),
    Kilograms(MeasureDimension.Mass, 1_000.0),
    Ounces(MeasureDimension.Mass, 28.349523125),
    Pounds(MeasureDimension.Mass, 453.59237),
    Teaspoons(MeasureDimension.Volume, 4.92892159375),
    Tablespoons(MeasureDimension.Volume, 14.78676478125),
    Cups(MeasureDimension.Volume, 236.5882365),
    FluidOunces(MeasureDimension.Volume, 29.5735295625),
    Milliliters(MeasureDimension.Volume, 1.0),
    Liters(MeasureDimension.Volume, 1_000.0),
}

/** Unit conversion and display formatting shared by both apps, so they always show the same numbers. */
object KitchenMath {

    /** "  Brown   Sugar " → "brown sugar": the form ingredients are looked up and saved under. */
    fun normalizeIngredient(name: String): String =
        name.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

    fun needsDensity(from: MeasureUnit, to: MeasureUnit): Boolean = from.dimension != to.dimension

    /**
     * Converts [amount] of [from] into [to]. [gramsPerCup] is the ingredient's density, needed only
     * when [needsDensity]; returns null when it is needed but missing or not positive.
     */
    fun convert(amount: Double, from: MeasureUnit, to: MeasureUnit, gramsPerCup: Double? = null): Double? {
        val base = amount * from.baseSize
        if (from.dimension == to.dimension) return base / to.baseSize
        val density = gramsPerCup?.takeIf { it > 0 }?.let { it / MeasureUnit.Cups.baseSize } ?: return null
        val converted = when (from.dimension) {
            MeasureDimension.Volume -> base * density // ml → g
            MeasureDimension.Mass -> base / density // g → ml
        }
        return converted / to.baseSize
    }

    /**
     * Reads a typed amount: "2", "2.5", "2,5", "1/2" and "1 1/2". Null for anything else, or for
     * amounts that are not positive.
     */
    fun parseAmount(text: String): Double? {
        val parts = text.trim().replace(',', '.').split(Regex("\\s+")).filter { it.isNotEmpty() }
        val value = when (parts.size) {
            1 -> parseSimple(parts[0])
            2 -> {
                val whole = parts[0].toDoubleOrNull()
                val fraction = parts[1].takeIf { '/' in it }?.let(::parseSimple)
                if (whole != null && fraction != null) whole + fraction else null
            }
            else -> null
        }
        return value?.takeIf { it > 0 && it.isFinite() }
    }

    private fun parseSimple(part: String): Double? {
        if ('/' !in part) return part.toDoubleOrNull()
        val (numerator, denominator) = part.split('/').takeIf { it.size == 2 } ?: return null
        val d = denominator.toDoubleOrNull()?.takeIf { it != 0.0 } ?: return null
        return numerator.toDoubleOrNull()?.div(d)
    }

    /**
     * Rounds to what is useful in a kitchen: whole numbers from 100, one decimal from 10, two below
     * that, and three significant digits for tiny amounts. Trailing zeros are dropped: 312.5, 125, 0.33.
     */
    fun formatAmount(value: Double): String {
        val magnitude = abs(value)
        val decimals = when {
            magnitude >= 100 -> 0
            magnitude >= 10 -> 1
            magnitude >= 0.1 || magnitude == 0.0 -> 2
            else -> 3
        }
        val factor = 10.0.pow(decimals)
        val scaled = (magnitude * factor).roundToLong()
        val whole = scaled / factor.toLong()
        val fraction = (scaled % factor.toLong()).toString().padStart(decimals, '0').trimEnd('0')
        val sign = if (value < 0 && scaled != 0L) "-" else ""
        return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
    }
}
