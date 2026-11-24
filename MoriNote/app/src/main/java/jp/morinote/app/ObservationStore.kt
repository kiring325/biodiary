package jp.morinote.app

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object ObservationStore {
    private const val DATA_FILE = "observations.json"

    suspend fun load(context: Context): List<Observation> = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, DATA_FILE)
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                Observation(
                    id = item.getString("id"),
                    name = item.optString("name"),
                    photoPath = item.getString("photoPath"),
                    dateMillis = item.getLong("dateMillis"),
                    latitude = item.getDouble("latitude"),
                    longitude = item.getDouble("longitude"),
                    place = item.optString("place"),
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun add(context: Context, source: Uri, observation: Observation): Observation = withContext(Dispatchers.IO) {
        val photoDir = File(context.filesDir, "photos").apply { mkdirs() }
        val mime = context.contentResolver.getType(source).orEmpty()
        val extension = if (mime.contains("png")) "png" else "jpg"
        val destination = File(photoDir, "${observation.id}.$extension")
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "写真を読み込めませんでした" }.use { photoInput ->
                destination.outputStream().use { output -> photoInput.copyTo(output) }
            }
        }
        val saved = observation.copy(photoPath = destination.absolutePath)
        val all = load(context) + saved
        write(context, all)
        saved
    }

    private fun write(context: Context, items: List<Observation>) {
        val json = JSONArray()
        items.forEach { item ->
            json.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("photoPath", item.photoPath)
                put("dateMillis", item.dateMillis)
                put("latitude", item.latitude)
                put("longitude", item.longitude)
                put("place", item.place)
            })
        }
        File(context.filesDir, DATA_FILE).writeText(json.toString())
    }

    fun newId(): String = UUID.randomUUID().toString()
}
