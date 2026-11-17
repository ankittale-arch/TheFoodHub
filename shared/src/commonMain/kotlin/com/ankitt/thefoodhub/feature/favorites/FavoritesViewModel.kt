package com.ankitt.thefoodhub.feature.favorites

import androidx.lifecycle.viewModelScope
import com.ankitt.thefoodhub.core.mvi.MviViewModel
import com.ankitt.thefoodhub.domain.model.CookbookNameResult
import com.ankitt.thefoodhub.domain.model.RecipeSummary
import com.ankitt.thefoodhub.domain.repository.CookbookRepository
import com.ankitt.thefoodhub.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class FavoritesViewModel(
    private val favoritesRepository: FavoritesRepository,
    private val cookbookRepository: CookbookRepository,
) : MviViewModel<FavoritesState, FavoritesIntent, FavoritesEffect>(FavoritesState()) {

    private val selectedCookbookId = MutableStateFlow<Long?>(null)
    private val pickerRecipe = MutableStateFlow<RecipeSummary?>(null)

    /**
     * Removing a favorite also takes it out of its cookbooks (the junction rows cascade), so the
     * cookbooks are remembered here until the undo either happens or the screen goes away.
     */
    private val cookbooksOfRemoved = mutableMapOf<Int, Set<Long>>()

    init {
        favoritesRepository.observeFavorites()
            .onEach { favorites -> updateState { copy(favorites = favorites, isLoading = false) } }
            .launchIn(viewModelScope)

        cookbookRepository.observeCookbooks()
            .onEach { cookbooks -> updateState { copy(cookbooks = cookbooks) } }
            .launchIn(viewModelScope)

        // Selection and its recipes change together, so the grid never shows one cookbook's
        // name over another's recipes.
        selectedCookbookId
            .flatMapLatest { id ->
                if (id == null) flowOf(null to emptyList())
                else cookbookRepository.observeRecipes(id).map { id to it }
            }
            .onEach { (id, recipes) -> updateState { copy(selectedCookbookId = id, cookbookRecipes = recipes) } }
            .launchIn(viewModelScope)

        pickerRecipe
            .flatMapLatest { recipe ->
                if (recipe == null) flowOf(null)
                else cookbookRepository.observeCookbookIds(recipe.id).map { CookbookPicker(recipe, it) }
            }
            .onEach { picker -> updateState { copy(cookbookPicker = picker) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: FavoritesIntent) {
        when (intent) {
            is FavoritesIntent.Remove -> viewModelScope.launch {
                cookbooksOfRemoved[intent.recipe.id] = cookbookRepository.cookbookIds(intent.recipe.id)
                favoritesRepository.setFavorite(intent.recipe, favorite = false)
                emitEffect(FavoritesEffect.Removed(intent.recipe))
            }
            is FavoritesIntent.UndoRemove -> viewModelScope.launch {
                favoritesRepository.setFavorite(intent.recipe, favorite = true)
                cookbooksOfRemoved.remove(intent.recipe.id)?.let { cookbookRepository.addRecipe(intent.recipe.id, it) }
            }

            is FavoritesIntent.SelectCookbook -> selectedCookbookId.value = intent.cookbookId

            is FavoritesIntent.OpenCookbookPicker -> pickerRecipe.value = intent.recipe
            FavoritesIntent.CloseCookbookPicker -> pickerRecipe.value = null
            is FavoritesIntent.ToggleCookbook -> toggleCookbook(intent.cookbookId)

            is FavoritesIntent.StartNewCookbook ->
                updateState { copy(nameEditor = CookbookNameEditor(recipeToAdd = intent.recipeToAdd)) }
            is FavoritesIntent.StartRenameCookbook -> {
                val cookbook = currentState.cookbooks.firstOrNull { it.id == intent.cookbookId } ?: return
                updateState { copy(nameEditor = CookbookNameEditor(cookbookId = cookbook.id, initialName = cookbook.name)) }
            }
            is FavoritesIntent.SaveCookbookName -> saveCookbookName(intent.name)
            FavoritesIntent.CancelCookbookName -> updateState { copy(nameEditor = null) }

            is FavoritesIntent.DeleteCookbook -> {
                if (selectedCookbookId.value == intent.cookbookId) selectedCookbookId.value = null
                viewModelScope.launch { cookbookRepository.deleteCookbook(intent.cookbookId) }
            }
        }
    }

    private fun toggleCookbook(cookbookId: Long) {
        val picker = currentState.cookbookPicker ?: return
        viewModelScope.launch {
            if (picker.contains(cookbookId)) {
                cookbookRepository.removeRecipe(picker.recipe.id, cookbookId)
            } else {
                cookbookRepository.addRecipe(picker.recipe.id, setOf(cookbookId))
            }
        }
    }

    private fun saveCookbookName(name: String) {
        val editor = currentState.nameEditor ?: return
        viewModelScope.launch {
            val result = when (val cookbookId = editor.cookbookId) {
                null -> cookbookRepository.createCookbook(name)
                else -> cookbookRepository.renameCookbook(cookbookId, name)
            }
            when (result) {
                is CookbookNameResult.Rejected -> updateState { copy(nameEditor = editor.copy(error = result.error)) }
                is CookbookNameResult.Saved -> {
                    updateState { copy(nameEditor = null) }
                    if (editor.isRename) return@launch
                    val recipe = editor.recipeToAdd
                    if (recipe != null) {
                        // Started from the picker: file the recipe and stay in the picker.
                        cookbookRepository.addRecipe(recipe.id, setOf(result.cookbookId))
                    } else {
                        // Started from the cookbook row: open the new, empty cookbook.
                        selectedCookbookId.value = result.cookbookId
                    }
                }
            }
        }
    }
}
