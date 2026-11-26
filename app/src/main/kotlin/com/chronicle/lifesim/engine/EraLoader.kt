package com.chronicle.lifesim.engine

import android.content.Context
import kotlinx.serialization.json.Json

object EraLoader {
  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  fun loadModern2020s(context: Context): EraPack {
    fun readAsset(name: String): String =
      context.assets.open("eras/modern_2020s/$name").bufferedReader().use { it.readText() }

    return EraPack(
      meta = json.decodeFromString(readAsset("era.json")),
      countries = json.decodeFromString(readAsset("countries.json")),
      education = json.decodeFromString(readAsset("education.json")),
      careers = json.decodeFromString(readAsset("careers.json")),
      assets = json.decodeFromString(readAsset("assets.json")),
      conditions = json.decodeFromString(readAsset("conditions.json")),
      events = json.decodeFromString(readAsset("events.json")),
      names = json.decodeFromString(readAsset("names.json")),
    )
  }
}
