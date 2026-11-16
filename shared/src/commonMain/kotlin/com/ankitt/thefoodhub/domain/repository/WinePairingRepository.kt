package com.ankitt.thefoodhub.domain.repository

import com.ankitt.thefoodhub.core.Outcome
import com.ankitt.thefoodhub.domain.model.WinePairing

interface WinePairingRepository {

    /**
     * The wine pairing for [food], a simple term such as "pork", "salmon" or "italian"
     * (see `PopularRecipe.winePairingFood`). Succeeds with null when Spoonacular has no pairing for it.
     *
     * Results, including "no pairing", are cached on the device, so each food costs quota at most
     * once per cache period.
     */
    suspend fun getPairing(food: String): Outcome<WinePairing?>
}
