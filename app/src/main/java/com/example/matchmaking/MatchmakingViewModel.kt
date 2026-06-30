package com.example.matchmaking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class MatchmakingState {
    IDLE, SEARCHING, MATCH_FOUND, ERROR
}

class MatchmakingViewModel : ViewModel() {
    private val repository = MatchmakingRepository()

    private val _matchmakingState = MutableStateFlow(MatchmakingState.IDLE)
    val matchmakingState: StateFlow<MatchmakingState> = _matchmakingState.asStateFlow()

    private val _currentMatchId = MutableStateFlow<String?>(null)
    val currentMatchId: StateFlow<String?> = _currentMatchId.asStateFlow()

    private var currentRequestId: String? = null
    private var searchJob: Job? = null

    // For demo purposes. In a real app, these would come from the user's profile.
    private val mockPlayerId = "player_${(1000..9999).random()}"
    private val mockSkillLevel = 1500
    private val mockRegion = "us-east"

    fun startMatchmaking() {
        _matchmakingState.value = MatchmakingState.SEARCHING
        searchJob = viewModelScope.launch {
            try {
                val requestId = repository.joinQueue(mockPlayerId, mockSkillLevel, mockRegion)
                currentRequestId = requestId

                // Observe changes to our request doc
                launch {
                    repository.observeMatchStatus(requestId).collect { request ->
                        if (request?.status == "matched" && request.matchId != null) {
                            _currentMatchId.value = request.matchId
                            _matchmakingState.value = MatchmakingState.MATCH_FOUND
                            stopSearchJob()
                        }
                    }
                }

                // Periodically try to find a match (Simulating cloud function logic locally)
                while (_matchmakingState.value == MatchmakingState.SEARCHING) {
                    delay(3000) // Wait a bit before checking
                    val requestObj = MatchRequest(mockPlayerId, mockSkillLevel, mockRegion)
                    val room = repository.findMatch(requestObj, requestId)
                    if (room != null) {
                        // Match found locally
                        _currentMatchId.value = room.roomId
                        _matchmakingState.value = MatchmakingState.MATCH_FOUND
                        break
                    }
                }
            } catch (e: Exception) {
                _matchmakingState.value = MatchmakingState.ERROR
            }
        }
    }

    fun cancelMatchmaking() {
        searchJob?.cancel()
        viewModelScope.launch {
            currentRequestId?.let {
                repository.leaveQueue(it)
                currentRequestId = null
            }
            _matchmakingState.value = MatchmakingState.IDLE
        }
    }

    private fun stopSearchJob() {
        searchJob?.cancel()
    }
}
