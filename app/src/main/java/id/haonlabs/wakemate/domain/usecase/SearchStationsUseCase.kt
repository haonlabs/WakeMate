package id.haonlabs.wakemate.domain.usecase

import id.haonlabs.wakemate.domain.model.StationWithLine
import id.haonlabs.wakemate.domain.model.TransitMode
import id.haonlabs.wakemate.domain.repository.StationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchStationsUseCase @Inject constructor(
    private val repository: StationRepository,
) {
    operator fun invoke(mode: TransitMode, query: String): Flow<List<StationWithLine>> =
        repository.search(mode, query.trim())
}
