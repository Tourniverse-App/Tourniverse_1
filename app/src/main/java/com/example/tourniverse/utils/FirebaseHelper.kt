package com.example.tourniverse.utils

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseHelper {
    private val db = FirebaseFirestore.getInstance()
    private const val TOURNAMENTS_COLLECTION = "tournaments"
    private const val USERS_COLLECTION = "users"

    /**
     * Adds a tournament to Firestore with the owner as the current user.
     *
     * @param name The name of the tournament.
     * @param teamCount The total number of teams in the tournament.
     * @param description A brief description of the tournament.
     * @param privacy Privacy level of the tournament ("Public" or "Private").
     * @param teamNames List of team names participating in the tournament.
     * @param imageResId Resource ID for the selected image.
     * @param callback Callback to indicate success (Boolean) and optional error message.
     */
    fun addTournament(
        name: String,
        teamCount: Int,
        description: String,
        privacy: String,
        teamNames: List<String>,
        callback: (Boolean, String?) -> Unit
    ) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val ownerId = currentUser?.uid ?: return callback(false, "User not authenticated")

        // Fetch all tournaments to log IDs or perform validation (optional)
        db.collection(TOURNAMENTS_COLLECTION)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    Log.d("FirebaseHelper", "Existing Tournament ID: ${document.id}")
                }

                val tournamentData = hashMapOf(
                    "name" to name,
                    "teamCount" to teamCount,
                    "description" to description,
                    "privacy" to privacy,
                    "teamNames" to teamNames,
                    "ownerId" to ownerId,
                    "viewers" to emptyList<String>(),
                    "createdAt" to System.currentTimeMillis()
                )

                val tournamentRef = db.collection(TOURNAMENTS_COLLECTION)

                tournamentRef.add(tournamentData)
                    .addOnSuccessListener { documentRef ->
                        val tournamentId = documentRef.id
                        Log.d("FirestoreDebug", "Tournament created with ID: $tournamentId")

                        // Initialize subcollections
                        initializeSubcollections(tournamentId, teamNames) { success, error ->
                            if (success) {
                                updateUserOwnedTournaments(ownerId, tournamentId) { userUpdateSuccess, userError ->
                                    if (userUpdateSuccess) {
                                        Log.d("FirestoreDebug", "Successfully updated user's ownedTournaments.")
                                        callback(true, null)
                                    } else {
                                        Log.e("FirestoreDebug", "Error updating user: $userError")
                                        callback(false, "Failed to update user: $userError")
                                    }
                                }
                            } else {
                                Log.e("FirestoreDebug", "Error initializing subcollections: $error")
                                callback(false, "Failed to initialize subcollections: $error")
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("FirestoreDebug", "Error adding tournament: ${e.message}")
                        callback(false, e.message ?: "Failed to create tournament")
                    }
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseHelper", "Error fetching tournaments: ${e.message}")
                callback(false, e.message)
            }
    }


    fun fetchTournamentIds(callback: (List<String>?, String?) -> Unit) {
        db.collection(TOURNAMENTS_COLLECTION)
            .get()
            .addOnSuccessListener { documents ->
                val tournamentIds = documents.map { it.id }
                Log.d("FirebaseHelper", "Fetched Tournament IDs: $tournamentIds")
                callback(tournamentIds, null)
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseHelper", "Error fetching tournaments: ${e.message}")
                callback(null, e.message ?: "Failed to fetch tournament IDs")
            }
    }



    /**
     * Initializes the subcollections for a new tournament.
     */
    private fun initializeSubcollections(
        tournamentId: String,
        teamNames: List<String>,
        callback: (Boolean, String?) -> Unit
    ) {
        val batch = db.batch()

        // Initialize chat collection with a welcome message
        val chatRef = db.collection(TOURNAMENTS_COLLECTION).document(tournamentId).collection("chat").document()
        val welcomeMessage = hashMapOf(
            "senderId" to "System",
            "senderName" to "System",
            "message" to "Welcome to the tournament!",
            "createdAt" to System.currentTimeMillis()
        )
        batch.set(chatRef, welcomeMessage)

        // Initialize scores collection (empty for now)
        val scoresRef = db.collection(TOURNAMENTS_COLLECTION).document(tournamentId).collection("scores").document()
        val placeholderScore = hashMapOf(
            "teamA" to "",
            "teamB" to "",
            "scoreA" to 0,
            "scoreB" to 0,
            "winner" to "",
            "playedAt" to System.currentTimeMillis()
        )
        batch.set(scoresRef, placeholderScore)

        // Initialize teamStats collection
        val teamStatsRef = db.collection(TOURNAMENTS_COLLECTION).document(tournamentId).collection("teamStats")
        teamNames.forEach { teamName ->
            val teamId = teamStatsRef.document().id
            val teamStats = hashMapOf(
                "teamName" to teamName,
                "wins" to 0,
                "losses" to 0,
                "goalsFor" to 0,
                "goalsAgainst" to 0,
                "points" to 0
            )
            batch.set(teamStatsRef.document(teamId), teamStats)
        }

        // Commit the batch
        batch.commit()
            .addOnSuccessListener { callback(true, null) }
            .addOnFailureListener { e -> callback(false, e.message ?: "Failed to initialize subcollections") }
    }

    /**
     * Updates the user's ownedTournaments list.
     */
    private fun updateUserOwnedTournaments(
        userId: String,
        tournamentId: String,
        callback: (Boolean, String?) -> Unit
    ) {
        val userRef = db.collection(USERS_COLLECTION).document(userId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(userRef)
            val ownedTournaments = snapshot.get("ownedTournaments") as? MutableList<String> ?: mutableListOf()
            if (!ownedTournaments.contains(tournamentId)) {
                ownedTournaments.add(tournamentId)
                transaction.update(userRef, "ownedTournaments", ownedTournaments)
            }
        }.addOnSuccessListener {
            callback(true, null)
        }.addOnFailureListener { e ->
            callback(false, e.message ?: "Failed to update user owned tournaments")
        }
    }


    /**
     * Fetches tournaments where the current user is either the owner or a viewer.
     *
     * @param callback Callback to return the list of tournaments as Maps.
     */
    fun getUserTournaments(callback: (List<Map<String, Any>>) -> Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val userId = currentUser?.uid ?: return callback(emptyList())

        db.collection(USERS_COLLECTION).document(userId).get()
            .addOnSuccessListener { document ->
                val ownedTournaments = document["ownedTournaments"] as? List<String> ?: emptyList()
                val viewedTournaments = document["viewedTournaments"] as? List<String> ?: emptyList()

                val tournamentIds = ownedTournaments.union(viewedTournaments).toList()

                if (tournamentIds.isEmpty()) {
                    Log.d("FirestoreDebug", "No tournaments found for user $userId")
                    callback(emptyList())
                    return@addOnSuccessListener
                }

                db.collection(TOURNAMENTS_COLLECTION)
                    .whereIn("__name__", tournamentIds)
                    .get()
                    .addOnSuccessListener { tournamentDocs ->
                        val tournaments = tournamentDocs.mapNotNull { it.data }
                        callback(tournaments)
                    }
                    .addOnFailureListener { e ->
                        Log.e("FirestoreDebug", "Error fetching tournament details: ${e.message}")
                        callback(emptyList())
                    }
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreDebug", "Error fetching user document: ${e.message}")
                callback(emptyList())
            }
    }

//    fun getUserTournaments(callback: (List<Map<String, Any>>) -> Unit) {
//        val currentUser = FirebaseAuth.getInstance().currentUser
//        val userId = currentUser?.uid ?: return callback(emptyList())
//
//        val userRef = db.collection(USERS_COLLECTION).document(userId)
//
//        userRef.get()
//            .addOnSuccessListener { document ->
//                val ownedTournaments = document["ownedTournaments"] as? List<String> ?: emptyList()
//                val viewedTournaments = document["viewedTournaments"] as? List<String> ?: emptyList()
//
//                val tournamentIds = ownedTournaments.union(viewedTournaments).toList()
//
//                if (tournamentIds.isEmpty()) {
//                    Log.d("FirestoreDebug", "No tournaments found for user $userId")
//                    callback(emptyList())
//                    return@addOnSuccessListener
//                }
//
//                val tournaments = mutableListOf<Map<String, Any>>()
//                val tasks = tournamentIds.map { tournamentId ->
//                    db.collection(TOURNAMENTS_COLLECTION).document(tournamentId).get()
//                }
//
//                // Fetch all tournaments using Tasks.whenAllSuccess
//                com.google.android.gms.tasks.Tasks.whenAllSuccess<Any>(tasks)
//                    .addOnSuccessListener { results ->
//                        results.forEach { result ->
//                            val snapshot = result as? com.google.firebase.firestore.DocumentSnapshot
//                            if (snapshot != null && snapshot.exists()) {
//                                snapshot.data?.let { data ->
//                                    tournaments.add(data)
//                                    Log.d("FirestoreDebug", "Fetched tournament: ${snapshot.id} -> $data")
//                                }
//                            } else {
//                                Log.w("FirestoreDebug", "Tournament document not found: ${snapshot?.id}")
//                            }
//                        }
//                        callback(tournaments)
//                    }
//                    .addOnFailureListener { e ->
//                        Log.e("FirestoreDebug", "Error fetching tournaments: ${e.message}")
//                        callback(emptyList())
//                    }
//            }
//            .addOnFailureListener { e ->
//                Log.e("FirestoreDebug", "Error fetching user document: ${e.message}")
//                callback(emptyList())
//            }
//    }




    /**
     * Adds a viewer to a specific tournament document.
     *
     * @param tournamentId ID of the tournament document.
     * @param newViewerId User ID of the viewer to be added.
     * @param callback Callback to indicate success (Boolean) and optional error message.
     */
    fun addViewerToTournament(
        tournamentId: String,
        newViewerId: String,
        callback: (Boolean, String?) -> Unit
    ) {
        val tournamentRef = db.collection(TOURNAMENTS_COLLECTION).document(tournamentId)
        val userRef = db.collection(USERS_COLLECTION).document(newViewerId)

        db.runTransaction { transaction ->
            val tournamentSnapshot = transaction.get(tournamentRef)
            val ownerId = tournamentSnapshot.get("ownerId") as? String ?: return@runTransaction
            val viewers = tournamentSnapshot.get("viewers") as? MutableList<String> ?: mutableListOf()

            // If the user is the owner, prevent adding as viewer
            if (ownerId == newViewerId) {
                throw IllegalStateException("Owner cannot be added as a viewer")
            }

            // Update viewers list
            if (!viewers.contains(newViewerId)) {
                viewers.add(newViewerId)
                transaction.update(tournamentRef, "viewers", viewers)
            }

            // Update user's viewed tournaments
            val userSnapshot = transaction.get(userRef)
            val ownedTournaments = userSnapshot.get("ownedTournaments") as? MutableList<String> ?: mutableListOf()
            val viewedTournaments = userSnapshot.get("viewedTournaments") as? MutableList<String> ?: mutableListOf()

            // Prevent user from being a viewer if they are already the owner
            if (tournamentId in ownedTournaments) {
                throw IllegalStateException("User cannot view a tournament they own")
            }

            if (!viewedTournaments.contains(tournamentId)) {
                viewedTournaments.add(tournamentId)
                transaction.update(userRef, "viewedTournaments", viewedTournaments)
            }
        }.addOnSuccessListener {
            callback(true, null)
        }.addOnFailureListener { e ->
            callback(false, e.message ?: "Failed to add viewer")
        }
    }


    /**
     * Updates a field in a specific tournament document.
     *
     * @param tournamentId ID of the tournament document.
     * @param field The field name to update.
     * @param value The new value for the field.
     * @param callback Callback to indicate success (Boolean) and optional error message.
     */
    fun updateTournamentField(
        tournamentId: String,
        field: String,
        value: Any,
        callback: (Boolean, String?) -> Unit
    ) {
        db.collection(TOURNAMENTS_COLLECTION)
            .document(tournamentId)
            .update(field, value)
            .addOnSuccessListener {
                callback(true, null)
            }
            .addOnFailureListener { e ->
                callback(false, e.message ?: "Failed to update field")
            }
    }

    /**
     * Ensures that a user document exists in Firestore, initializing it if needed.
     *
     * @param userId The ID of the user.
     */
    fun createUserDocumentIfNotExists(userId: String) {
        val userRef = db.collection(USERS_COLLECTION).document(userId)

        userRef.get().addOnSuccessListener { document ->
            if (!document.exists()) {
                val userData = hashMapOf(
                    "username" to "",
                    "bio" to "",
                    "image" to null,
                    "ownedTournaments" to mutableListOf<String>(),
                    "viewedTournaments" to mutableListOf<String>()
                )
                userRef.set(userData)
                    .addOnSuccessListener {
                        println("User document created successfully")
                    }
                    .addOnFailureListener { e ->
                        println("Failed to create user document: ${e.message}")
                    }
            }
        }.addOnFailureListener { e ->
            println("Failed to check user document: ${e.message}")
        }
    }

    /**
     * Fetches the user document for a specific user ID.
     *
     * @param userId The ID of the user.
     * @param callback Callback to return the user document as a Map.
     */
    fun getUserDocument(userId: String, callback: (Map<String, Any>?) -> Unit) {
        val userRef = db.collection(USERS_COLLECTION).document(userId)
        userRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    callback(document.data)
                } else {
                    callback(null)
                }
            }
            .addOnFailureListener { e ->
                callback(null)
            }
    }

    /**
     * Fetches tournaments where the current user is either the owner or a viewer.
     *
     * @param includeViewed If true, includes viewed tournaments. Otherwise, only owned tournaments are fetched.
     * @param callback Callback to return the list of tournaments as Maps.
     */
    fun getUserTournaments(includeViewed: Boolean, callback: (List<Map<String, Any>>) -> Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val userId = currentUser?.uid ?: return callback(emptyList())

        val userRef = db.collection(USERS_COLLECTION).document(userId)

        userRef.get()
            .addOnSuccessListener { document ->
                val ownedTournaments = document["ownedTournaments"] as? List<String> ?: emptyList()
                val viewedTournaments = if (includeViewed) {
                    document["viewedTournaments"] as? List<String> ?: emptyList()
                } else {
                    emptyList()
                }

                // If includeViewed is true, we just append the viewed tournaments to the owned ones.
                val tournamentIds = if (includeViewed) {
                    ownedTournaments + viewedTournaments
                } else {
                    ownedTournaments
                }

                if (tournamentIds.isEmpty()) {
                    Log.d("FirestoreDebug", "No tournaments found for user $userId")
                    callback(emptyList())
                    return@addOnSuccessListener
                }

                val tournaments = mutableListOf<Map<String, Any>>()
                val tasks = tournamentIds.map { tournamentId ->
                    db.collection(TOURNAMENTS_COLLECTION).document(tournamentId).get()
                }

                com.google.android.gms.tasks.Tasks.whenAllSuccess<Any>(tasks)
                    .addOnSuccessListener { results ->
                        results.forEach { result ->
                            val snapshot = result as? com.google.firebase.firestore.DocumentSnapshot
                            if (snapshot != null && snapshot.exists()) {
                                snapshot.data?.let { data ->
                                    tournaments.add(data)
                                }
                            } else {
                                Log.w("FirestoreDebug", "Tournament not found or deleted")
                            }
                        }
                        callback(tournaments)
                    }
                    .addOnFailureListener { e ->
                        Log.e("FirestoreDebug", "Error fetching tournaments: ${e.message}")
                        callback(emptyList())
                    }
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreDebug", "Error fetching user document: ${e.message}")
                callback(emptyList())
            }
    }

}
