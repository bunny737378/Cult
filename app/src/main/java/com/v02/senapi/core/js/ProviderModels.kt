package com.v02.senapi.core.js

data class ProviderManifest(
    val id: String,
    val name: String,
    val icon: String,
    val version: String = "1.0.0",
    val isInstalled: Boolean = true
)

data class CatalogItem(
    val i: String,
    val nm: String
)

data class MediaItem(
    val id: String,
    val title: String,
    val img: String,
    val type: String = "movie",
    val rating: String = "8.5",
    val tags: List<String> = emptyList(),
    val plot: String = ""
)

data class ActorItem(
    val name: String,
    val role: String,
    val img: String
)

data class MetaDetails(
    val id: String,
    val type: String,
    val bg: String,
    val tags: List<String>,
    val plot: String,
    val actors: List<ActorItem>,
    val recommend: List<MediaItem>
)

data class SeasonTab(
    val seasonNo: String,
    val name: String,
    val id: String
)

data class EpisodeItem(
    val epNo: Int,
    val id: String,
    val title: String,
    val img: String,
    val plot: String,
    val tags: List<String> = emptyList()
)

data class SubtitleLink(
    val label: String,
    val url: String
)

data class StreamServer(
    val name: String,
    val type: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val qualities: List<String> = listOf("auto"),
    val audios: List<String> = listOf("Default"),
    val externalSubtitles: List<SubtitleLink> = emptyList()
)
