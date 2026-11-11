package dev.bastian.pixela.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bastian.pixela.domain.ProjectRepository
import dev.bastian.pixela.domain.ProjectSummary
import dev.bastian.pixela.ui.Strings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The gallery list; [loaded] is false until the first read of the index finishes. */
data class GalleryUiState(val projects: List<ProjectSummary> = emptyList(), val loaded: Boolean = false)

class GalleryViewModel(
    private val repository: ProjectRepository,
    private val strings: Strings,
) : ViewModel() {

    val state: StateFlow<GalleryUiState> = repository.projects
        .map { GalleryUiState(it, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState())

    /** Creates a project and reports its id once it exists, so the editor can open it. */
    fun create(name: String, size: Int, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val finalName = name.trim().ifEmpty { strings.newProjectName(state.value.projects.size + 1) }
            onCreated(repository.create(finalName, size, strings.layerName(1)))
        }
    }

    fun rename(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.rename(id, trimmed) }
    }

    fun duplicate(project: ProjectSummary) {
        viewModelScope.launch { repository.duplicate(project.id, strings.copyOf(project.name)) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}
