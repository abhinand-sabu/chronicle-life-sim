package com.chronicle.lifesim.engine

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
data class SaveSlot(
  val slotId: Int,
  val updatedAt: Long,
  val state: GameState,
)

@Serializable
private data class SaveFilePayload(
  val activeSlotId: Int = 1,
  val slots: Map<Int, SaveSlot> = emptyMap(),
)

class SaveManager(context: Context) {
  private val file = File(context.filesDir, "chronicle_qa_saves.json")
  private val backupFile = File(context.filesDir, "chronicle_qa_saves.json.bak")
  private val tempFile = File(context.filesDir, "chronicle_qa_saves.json.tmp")
  private val backupTempFile = File(context.filesDir, "chronicle_qa_saves.json.bak.tmp")
  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  private fun decodeFile(target: File): SaveFilePayload? {
    if (!target.exists()) return null
    return runCatching {
      json.decodeFromString<SaveFilePayload>(target.readText())
    }.getOrNull()
  }

  private fun readPayload(): SaveFilePayload {
    return decodeFile(file) ?: decodeFile(backupFile) ?: SaveFilePayload()
  }

  private fun writePayload(payload: SaveFilePayload) {
    val encoded = json.encodeToString(payload)
    FileOutputStream(tempFile).use { output ->
      output.write(encoded.toByteArray(Charsets.UTF_8))
      output.fd.sync()
    }
    // Preserve a readable backup if the primary file failed to decode.
    if (decodeFile(file) != null) {
      file.copyTo(backupTempFile, overwrite = true)
      Files.move(backupTempFile.toPath(), backupFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }
    Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE)
  }

  fun loadAll(): Pair<Int, Map<Int, SaveSlot>> {
    val payload = readPayload()
    return payload.activeSlotId.coerceIn(1, 5) to payload.slots
  }

  fun saveToSlot(slotId: Int, state: GameState): Map<Int, SaveSlot> {
    val current = readPayload()
    val nextSlots = current.slots.toMutableMap()
    nextSlots[slotId] = SaveSlot(slotId = slotId, updatedAt = System.currentTimeMillis(), state = state)
    writePayload(SaveFilePayload(activeSlotId = slotId, slots = nextSlots))
    return nextSlots
  }

  fun deleteSlot(slotId: Int): Map<Int, SaveSlot> {
    val current = readPayload()
    val nextSlots = current.slots.toMutableMap()
    nextSlots.remove(slotId)
    writePayload(current.copy(slots = nextSlots))
    return nextSlots
  }

  fun setActiveSlotId(slotId: Int) {
    val current = readPayload()
    writePayload(current.copy(activeSlotId = slotId.coerceIn(1, 5)))
  }

  fun findEmptySlot(slots: Map<Int, SaveSlot>): Int? {
    for (id in 1..5) {
      if (!slots.containsKey(id)) return id
    }
    return null
  }

  fun pickSlotForNewLife(slots: Map<Int, SaveSlot>, currentSlotId: Int): Int {
    return findEmptySlot(slots) ?: currentSlotId.coerceIn(1, 5)
  }
}
