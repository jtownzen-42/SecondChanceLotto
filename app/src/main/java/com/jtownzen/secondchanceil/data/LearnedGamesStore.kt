package com.jtownzen.secondchanceil.data

import android.content.Context

/** Keeps learned games across restarts. Only game numbers, names and code letters: nothing personal. */
class LearnedGamesStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): LearnedGamesState = GameLearner.decode(prefs.getString(KEY, null).orEmpty())

    fun save(state: LearnedGamesState) {
        prefs.edit().putString(KEY, GameLearner.encode(state)).apply()
    }

    private companion object {
        const val PREFS = "learned_games"
        const val KEY = "state"
    }
}
