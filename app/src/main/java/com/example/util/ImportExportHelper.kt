package com.example.util

import com.example.data.model.CustomList
import com.example.data.model.MovieSummary
import com.example.data.model.WatchedMovie
import com.example.data.model.WatchlistItem
import com.example.data.model.todayIsoDate
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

enum class ImportSourceFormat(val label: String) {
    IMDB("IMDb CSV"),
    LETTERBOXD("Letterboxd CSV"),
    CINELOG("CineLog CSV"),
    GENERIC("CSV"),
}

data class ParsedCsvRow(
    val rowIndex: Int,
    val sourceTitle: String,
    val year: Int?,
    val isTv: Boolean,
    val userRating: Int,
    val watchDate: String?,
    val note: String,
)

data class ImportCandidateRow(
    val rowIndex: Int,
    val sourceTitle: String,
    val year: Int?,
    val isTv: Boolean,
    val userRating: Int,
    val watchDate: String?,
    val note: String,
    val matchedMovie: MovieSummary? = null,
    val isSkipped: Boolean = false,
) {
    val isMatched: Boolean
        get() = matchedMovie != null && !isSkipped
}

data class ParsedCsvResult(
    val format: ImportSourceFormat,
    val rows: List<ParsedCsvRow>,
)

object ImportExportHelper {
    private val YEAR_REGEX = Regex("(19\\d{2}|20\\d{2})")
    private val ISO_DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    fun parseCsv(csvContent: String): ParsedCsvResult {
        val records = parseRfc4180Csv(csvContent.trimStart('\uFEFF'))
        if (records.size < 2) {
            return ParsedCsvResult(ImportSourceFormat.GENERIC, emptyList())
        }

        val headers = records.first().map { it.trim().lowercase(Locale.US) }
        val headerIndex = headers.withIndex().associate { (idx, name) -> name to idx }

        val format = when {
            headerIndex.containsKey("record_type") && headerIndex.containsKey("title") ->
                ImportSourceFormat.CINELOG
            headerIndex.containsKey("const") ||
                headerIndex.containsKey("your rating") ||
                headerIndex.containsKey("title type") ->
                ImportSourceFormat.IMDB
            headerIndex.containsKey("letterboxd uri") ||
                headerIndex.containsKey("watched date") ||
                (headerIndex.containsKey("name") && headerIndex.containsKey("year")) ->
                ImportSourceFormat.LETTERBOXD
            else -> ImportSourceFormat.GENERIC
        }

        val rows = mutableListOf<ParsedCsvRow>()
        for (i in 1 until records.size) {
            val row = records[i]
            if (row.all { it.isBlank() }) continue

            fun col(vararg names: String): String {
                for (name in names) {
                    val idx = headerIndex[name]
                    if (idx != null && idx in row.indices) {
                        return row[idx].trim()
                    }
                }
                return ""
            }

            if (format == ImportSourceFormat.CINELOG) {
                val recordType = col("record_type").lowercase(Locale.US)
                if (recordType.isNotBlank() && recordType != "watched") continue
            }

            val title = col("title", "name", "original title")
            if (title.isBlank()) continue

            val rawYear = col("year", "release_date", "release date")
            val parsedYear = YEAR_REGEX.find(rawYear)?.value?.toIntOrNull()

            val titleType = col("title type", "media_type", "type").lowercase(Locale.US)
            val isTv = titleType.contains("series") ||
                titleType == "tv" ||
                titleType.contains("tv mini") ||
                titleType.contains("tv movie").not() && titleType.startsWith("tv")

            val rawRating = col("your rating", "rating", "user_rating").toDoubleOrNull()
            val userRating = when {
                rawRating == null || rawRating <= 0.0 -> 0
                format == ImportSourceFormat.IMDB || rawRating > 5.0 ->
                    (rawRating / 2.0).roundToInt().coerceIn(1, 5)
                else -> rawRating.roundToInt().coerceIn(1, 5)
            }

            val rawDate = col("watched date", "date rated", "date", "watch_log", "watched_at")
                .split(';')
                .firstOrNull()
                ?.trim()
                ?.take(10)
            val cleanDate = rawDate?.takeIf { ISO_DATE_REGEX.matches(it) }

            val note = col("review", "note", "notes", "comments").take(1000)

            rows.add(
                ParsedCsvRow(
                    rowIndex = rows.size + 1,
                    sourceTitle = title,
                    year = parsedYear,
                    isTv = isTv,
                    userRating = userRating,
                    watchDate = cleanDate,
                    note = note,
                )
            )
        }

        return ParsedCsvResult(format = format, rows = rows)
    }

    fun exportToCsv(
        watched: List<WatchedMovie>,
        watchlist: List<WatchlistItem>,
        customLists: List<CustomList>,
    ): String {
        val sb = StringBuilder()
        sb.appendLine(
            "record_type,list_name,movie_id,media_type,title,release_date,tmdb_rating,user_rating,reaction,language,runtime_min,genres,watch_count,watch_log,note"
        )

        watched.forEach { item ->
            sb.appendLine(
                listOf(
                    "watched",
                    "",
                    item.movieId.toString(),
                    item.resolvedMediaType.value,
                    item.title,
                    item.releaseDate,
                    String.format(Locale.US, "%.1f", item.rating),
                    item.userRating.toString(),
                    item.reaction,
                    item.originalLanguage,
                    item.runtime.toString(),
                    item.genres.joinToString("; "),
                    item.watchCount.toString(),
                    item.resolvedWatchDates.joinToString("; "),
                    item.note,
                ).joinToString(",") { escapeCsv(it) }
            )
        }

        watchlist.forEach { item ->
            sb.appendLine(
                listOf(
                    "watchlist",
                    "",
                    item.movieId.toString(),
                    item.resolvedMediaType.value,
                    item.title,
                    item.releaseDate,
                    String.format(Locale.US, "%.1f", item.rating),
                    "",
                    "",
                    item.originalLanguage,
                    item.runtime.toString(),
                    item.genres.joinToString("; "),
                    "",
                    "",
                    "",
                ).joinToString(",") { escapeCsv(it) }
            )
        }

        customLists.forEach { list ->
            list.items.forEach { entry ->
                sb.appendLine(
                    listOf(
                        "custom_list",
                        list.name,
                        entry.movieId.toString(),
                        entry.resolvedMediaType.value,
                        entry.title,
                        entry.releaseDate,
                        String.format(Locale.US, "%.1f", entry.rating),
                        "",
                        "",
                        entry.originalLanguage,
                        "",
                        "",
                        "",
                        "",
                        "",
                    ).joinToString(",") { escapeCsv(it) }
                )
            }
        }

        return sb.toString()
    }

    fun exportToJson(
        watched: List<WatchedMovie>,
        watchlist: List<WatchlistItem>,
        customLists: List<CustomList>,
    ): String {
        val root = JSONObject().apply {
            put("app", "CineLog")
            put("exportedAt", todayIsoDate())
            put(
                "counts",
                JSONObject().apply {
                    put("watched", watched.size)
                    put("watchlist", watchlist.size)
                    put("customLists", customLists.size)
                }
            )
            put(
                "watched",
                JSONArray().apply {
                    watched.forEach { item ->
                        put(
                            JSONObject().apply {
                                put("movieId", item.movieId)
                                put("mediaType", item.resolvedMediaType.value)
                                put("title", item.title)
                                put("posterPath", item.posterPath ?: "")
                                put("releaseDate", item.releaseDate)
                                put("tmdbRating", item.rating)
                                put("userRating", item.userRating)
                                put("reaction", item.reaction)
                                put("note", item.note)
                                put("originalLanguage", item.originalLanguage)
                                put("runtime", item.runtime)
                                put("genres", JSONArray(item.genres))
                                put("directors", JSONArray(item.directors))
                                put("cast", JSONArray(item.cast))
                                put("watchCount", item.watchCount)
                                put("watchLog", JSONArray(item.resolvedWatchDates))
                            }
                        )
                    }
                }
            )
            put(
                "watchlist",
                JSONArray().apply {
                    watchlist.forEach { item ->
                        put(
                            JSONObject().apply {
                                put("movieId", item.movieId)
                                put("mediaType", item.resolvedMediaType.value)
                                put("title", item.title)
                                put("posterPath", item.posterPath ?: "")
                                put("releaseDate", item.releaseDate)
                                put("tmdbRating", item.rating)
                                put("originalLanguage", item.originalLanguage)
                                put("runtime", item.runtime)
                                put("genres", JSONArray(item.genres))
                            }
                        )
                    }
                }
            )
            put(
                "customLists",
                JSONArray().apply {
                    customLists.forEach { list ->
                        put(
                            JSONObject().apply {
                                put("id", list.id)
                                put("name", list.name)
                                put("description", list.description)
                                put(
                                    "items",
                                    JSONArray().apply {
                                        list.items.forEach { entry ->
                                            put(
                                                JSONObject().apply {
                                                    put("movieId", entry.movieId)
                                                    put("mediaType", entry.resolvedMediaType.value)
                                                    put("title", entry.title)
                                                    put("releaseDate", entry.releaseDate)
                                                    put("tmdbRating", entry.rating)
                                                    put("originalLanguage", entry.originalLanguage)
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            )
        }
        return root.toString(2)
    }

    private fun escapeCsv(value: String): String {
        val needsQuote = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        if (!needsQuote) return value
        return "\"${value.replace("\"", "\"\"")}\""
    }

    internal fun parseRfc4180Csv(input: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < input.length && input[i + 1] == '"') {
                        field.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> {
                        currentRow.add(field.toString())
                        field.setLength(0)
                    }
                    '\r' -> {
                        if (i + 1 < input.length && input[i + 1] == '\n') {
                            i++
                        }
                        currentRow.add(field.toString())
                        field.setLength(0)
                        rows.add(currentRow.toList())
                        currentRow.clear()
                    }
                    '\n' -> {
                        currentRow.add(field.toString())
                        field.setLength(0)
                        rows.add(currentRow.toList())
                        currentRow.clear()
                    }
                    else -> field.append(ch)
                }
            }
            i++
        }
        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(field.toString())
            rows.add(currentRow.toList())
        }
        return rows
    }
}
