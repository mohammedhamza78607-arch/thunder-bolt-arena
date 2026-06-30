package com.example.matchmaking

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class MatchRequest(
    val playerId: String,
    val skillLevel: Int,
    val region: String,
    val status: String = "waiting", // waiting, matched
    val matchId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class GameRoom(
    val roomId: String,
    val player1Id: String,
    val player2Id: String,
    val status: String = "active" // active, finished
)

class MatchmakingRepository {
    private val db = FirebaseFirestore.getInstance()
    private val matchmakingCollection = db.collection("matchmaking_queue")
    private val roomsCollection = db.collection("game_rooms")

    suspend fun joinQueue(playerId: String, skillLevel: Int, region: String): String {
        val requestId = UUID.randomUUID().toString()
        val request = MatchRequest(
            playerId = playerId,
            skillLevel = skillLevel,
            region = region
        )
        try {
            matchmakingCollection.document(requestId).set(request).await()
            return requestId
        } catch (e: Exception) {
            Log.e("Matchmaking", "Error joining queue", e)
            throw e
        }
    }

    suspend fun leaveQueue(requestId: String) {
        try {
            matchmakingCollection.document(requestId).delete().await()
        } catch (e: Exception) {
            Log.e("Matchmaking", "Error leaving queue", e)
        }
    }

    suspend fun findMatch(currentRequest: MatchRequest, requestId: String): GameRoom? {
        try {
            // Very basic matching logic: find someone in the same region with a similar skill level
            // In a real app, you'd use a cloud function to avoid race conditions.
            val snapshot = matchmakingCollection
                .whereEqualTo("region", currentRequest.region)
                .whereEqualTo("status", "waiting")
                .whereGreaterThanOrEqualTo("skillLevel", currentRequest.skillLevel - 100)
                .whereLessThanOrEqualTo("skillLevel", currentRequest.skillLevel + 100)
                .limit(2)
                .get()
                .await()

            for (doc in snapshot.documents) {
                if (doc.id != requestId) {
                    val opponentId = doc.getString("playerId") ?: continue
                    // Create a game room
                    val roomId = UUID.randomUUID().toString()
                    val room = GameRoom(roomId, currentRequest.playerId, opponentId)
                    
                    roomsCollection.document(roomId).set(room).await()

                    // Update both requests to 'matched' and assign the matchId
                    matchmakingCollection.document(requestId)
                        .update("status", "matched", "matchId", roomId).await()
                    matchmakingCollection.document(doc.id)
                        .update("status", "matched", "matchId", roomId).await()

                    return room
                }
            }
        } catch (e: Exception) {
            Log.e("Matchmaking", "Error finding match", e)
        }
        return null
    }

    fun observeMatchStatus(requestId: String): Flow<MatchRequest?> = callbackFlow {
        val listener = matchmakingCollection.document(requestId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val request = snapshot.toObject(MatchRequest::class.java)
                    trySend(request)
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }
}
