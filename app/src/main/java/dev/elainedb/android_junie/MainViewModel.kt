package dev.elainedb.android_junie

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.elainedb.android_junie.data.VideoItem
import dev.elainedb.android_junie.data.VideoRepository
import dev.elainedb.android_junie.data.YouTubeApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SortField { PUBLICATION_DATE, RECORDING_DATE }
enum class SortDir { DESC, ASC }

data class UiState(
    val allVideos: List<VideoItem> = emptyList(),
    val filteredSorted: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedChannel: String? = null,
    val selectedCountry: String? = null,
    val sortField: SortField = SortField.PUBLICATION_DATE,
    val sortDir: SortDir = SortDir.DESC
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = VideoRepository(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    val channels = YouTubeApi.CHANNEL_IDS

    init {
        refresh(false)
    }

    fun refresh(force: Boolean) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val vids = repo.getVideos(force)
                _state.update { st -> st.copy(allVideos = vids) }
                applyFiltersAndSort()
            } catch (t: Throwable) {
                _state.update { it.copy(error = t.message ?: "Unknown error") }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setChannel(channelId: String?) {
        _state.update { it.copy(selectedChannel = channelId) }
        applyFiltersAndSort()
    }

    fun setCountry(country: String?) {
        _state.update { it.copy(selectedCountry = country) }
        applyFiltersAndSort()
    }

    fun setSort(field: SortField, dir: SortDir) {
        _state.update { it.copy(sortField = field, sortDir = dir) }
        applyFiltersAndSort()
    }

    private fun applyFiltersAndSort() {
        val st = _state.value
        var list = st.allVideos
        st.selectedChannel?.let { ch ->
            list = list.filter { it.channelTitle.equals(ch, ignoreCase = true) || it.channelTitle.contains(ch, ignoreCase = true) }
        }
        st.selectedCountry?.let { country ->
            list = list.filter { (it.country ?: "").equals(country, ignoreCase = true) }
        }
        list = when (st.sortField) {
            SortField.PUBLICATION_DATE -> list.sortedBy { it.publishedAt }
            SortField.RECORDING_DATE -> list.sortedBy { it.recordingDate ?: "" }
        }
        if (st.sortDir == SortDir.DESC) list = list.reversed()
        _state.update { it.copy(filteredSorted = list) }
    }
}