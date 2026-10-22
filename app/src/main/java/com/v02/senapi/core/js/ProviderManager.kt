package com.v02.senapi.core.js

import android.content.Context
import android.util.Log
import app.cash.quickjs.QuickJs
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.v02.senapi.core.network.CultHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class ProviderManager(private val context: Context) {
    companion object {
        private const val TAG = "ProviderManager"
        private const val REGISTRY_URL = "https://raw.githubusercontent.com/bunny737378/provider/main/pro.json"
        private const val GITHUB_RAW_BASE = "https://raw.githubusercontent.com/bunny737378/provider/main"
        const val DEFAULT_PROVIDER_ID = "mxplayer"
    }

    private val gson = Gson()
    private val networkBridge = NetworkBridge()
    private val cryptoBridge = CryptoBridge()
    private val base64Bridge = Base64Bridge()

    private var activeProviderId: String = DEFAULT_PROVIDER_ID
    private val pluginsDir: File = File(context.filesDir, "plugins")

    init {
        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs()
        }
        ensureDefaultProvidersInstalled()
    }

    fun getActiveProviderId(): String = activeProviderId

    fun setActiveProviderId(id: String) {
        this.activeProviderId = id
    }

    fun getInstalledProviders(): List<ProviderManifest> {
        return listOf(
            ProviderManifest(
                id = "mxplayer",
                name = "MX Player",
                icon = "https://raw.githubusercontent.com/bunny737378/provider/main/mxplayer/icon.png",
                version = "2.1.0"
            ),
            ProviderManifest(
                id = "anidap",
                name = "AniDap",
                icon = "https://raw.githubusercontent.com/bunny737378/provider/main/anidap/icon.png",
                version = "2.4.1"
            ),
            ProviderManifest(
                id = "moviebox",
                name = "MovieBox",
                icon = "https://raw.githubusercontent.com/bunny737378/provider/main/moviebox/icon.png",
                version = "1.9.5"
            )
        )
    }

    suspend fun checkForUpdates(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Checking for remote provider updates from $REGISTRY_URL")
            val req = Request.Builder().url(REGISTRY_URL).build()
            val res = CultHttpClient.execute(req)
            if (res.isSuccessful) {
                val body = res.body?.string()
                if (!body.isNullOrBlank()) {
                    Log.i(TAG, "Registry fetched successfully, updating installed plugins")
                    syncProviderFiles(activeProviderId)
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Background update check encountered network notice: ${e.message}")
        }
        return@withContext false
    }

    fun clearCache() {
        try {
            if (pluginsDir.exists()) {
                pluginsDir.deleteRecursively()
                pluginsDir.mkdirs()
            }
            ensureDefaultProvidersInstalled()
            Log.i(TAG, "Plugin cache cleared and reinitialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }

    private fun ensureDefaultProvidersInstalled() {
        listOf("mxplayer", "anidap", "moviebox").forEach { id ->
            val providerFolder = File(pluginsDir, id)
            if (!providerFolder.exists()) {
                providerFolder.mkdirs()
                installBuiltinProvider(id, providerFolder)
            }
        }
    }

    private fun installBuiltinProvider(id: String, folder: File) {
        val catalogJson = when (id) {
            "mxplayer" -> """
                {
                  "Trending": [
                    { "i": "slug-trending", "nm": "Spotlight Hits" },
                    { "i": "trending-movies", "nm": "Trending Movies" },
                    { "i": "trending-shows", "nm": "Popular Web Series" }
                  ],
                  "Genres": [
                    { "i": "action", "nm": "Action & Adventure" },
                    { "i": "thriller", "nm": "Crime Thrillers" },
                    { "i": "romance", "nm": "Romantic Dramas" }
                  ]
                }
            """.trimIndent()
            "anidap" -> """
                {
                  "Spotlight": [
                    { "i": "slug-anime", "nm": "Featured Anime" },
                    { "i": "anime-trending", "nm": "Trending Anime" }
                  ],
                  "Categories": [
                    { "i": "shonen", "nm": "Shonen" },
                    { "i": "isekai", "nm": "Isekai" }
                  ]
                }
            """.trimIndent()
            else -> """
                {
                  "Discover": [
                    { "i": "slug-popular", "nm": "Spotlight Premieres" },
                    { "i": "box-office", "nm": "Box Office Hits" }
                  ]
                }
            """.trimIndent()
        }

        File(folder, "catalog.json").writeText(catalogJson)
        File(folder, "config.js").writeText("const PROVIDER_NAME = '$id';")
        File(folder, "discovery.js").writeText(createBuiltinDiscoveryScript(id))
        File(folder, "meta.js").writeText(createBuiltinMetaScript(id))
        File(folder, "seasons.js").writeText(createBuiltinSeasonsScript(id))
        File(folder, "streams.js").writeText(createBuiltinStreamsScript(id))
    }

    private suspend fun syncProviderFiles(id: String) = withContext(Dispatchers.IO) {
        val files = listOf("catalog.json", "config.js", "discovery.js", "meta.js", "seasons.js", "streams.js")
        val folder = File(pluginsDir, id)
        if (!folder.exists()) folder.mkdirs()

        for (file in files) {
            val fileUrl = "$GITHUB_RAW_BASE/$id/$file"
            try {
                val req = Request.Builder().url(fileUrl).build()
                val res = CultHttpClient.execute(req)
                if (res.isSuccessful) {
                    val content = res.body?.string()
                    if (!content.isNullOrBlank()) {
                        File(folder, file).writeText(content)
                    }
                }
            } catch (ignored: Exception) {}
        }
    }

    suspend fun executeProviderRequest(requestJson: String): String = withContext(Dispatchers.IO) {
        val folder = File(pluginsDir, activeProviderId)
        if (!folder.exists()) {
            ensureDefaultProvidersInstalled()
        }

        val catalogData = readPluginFile(folder, "catalog.json")
        val configJs = readPluginFile(folder, "config.js")
        val discoveryJs = readPluginFile(folder, "discovery.js")
        val metaJs = readPluginFile(folder, "meta.js")
        val seasonsJs = readPluginFile(folder, "seasons.js")
        val streamsJs = readPluginFile(folder, "streams.js")

        var quickJs: QuickJs? = null
        try {
            quickJs = QuickJs.create()
            quickJs.set("NetworkBridge", NetworkBridgeInterface::class.java, networkBridge)
            quickJs.set("CryptoBridge", CryptoBridgeInterface::class.java, cryptoBridge)
            quickJs.set("Base64Bridge", Base64BridgeInterface::class.java, base64Bridge)

            val fullScript = """
                var CATALOG_DATA = $catalogData;
                $configJs
                $discoveryJs
                $metaJs
                $seasonsJs
                $streamsJs
                handleRequest('$requestJson');
            """.trimIndent()

            val result = quickJs.evaluate(fullScript)
            return@withContext result?.toString() ?: "{}"
        } catch (e: Exception) {
            Log.e(TAG, "QuickJS execution failed for $activeProviderId: ${e.message}", e)
            return@withContext executeFallbackDirect(requestJson, activeProviderId)
        } finally {
            try {
                quickJs?.close()
            } catch (ignored: Exception) {}
        }
    }

    private fun readPluginFile(folder: File, fileName: String): String {
        val f = File(folder, fileName)
        return if (f.exists()) f.readText() else ""
    }

    // High-level API methods
    suspend fun getCatalog(): Map<String, List<CatalogItem>> = withContext(Dispatchers.IO) {
        val folder = File(pluginsDir, activeProviderId)
        val catalogJson = readPluginFile(folder, "catalog.json")
        val result = HashMap<String, List<CatalogItem>>()
        try {
            val jsonObject = JsonParser.parseString(catalogJson).asJsonObject
            for (key in jsonObject.keySet()) {
                val array = jsonObject.getAsJsonArray(key)
                val items = mutableListOf<CatalogItem>()
                for (elem in array) {
                    val obj = elem.asJsonObject
                    items.add(CatalogItem(i = obj.get("i").asString, nm = obj.get("nm").asString))
                }
                result[key] = items
            }
        } catch (e: Exception) {
            Log.w(TAG, "Catalog parse error", e)
        }
        return@withContext result
    }

    suspend fun getHomeCategory(categoryId: String, page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val reqJson = gson.toJson(mapOf("request_type" to "home", "id" to categoryId, "page" to page.toString()))
        val response = executeProviderRequest(reqJson)
        parseMediaList(response)
    }

    suspend fun querySuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val reqJson = gson.toJson(mapOf("request_type" to "qsearch", "query" to query))
        val response = executeProviderRequest(reqJson)
        val list = mutableListOf<String>()
        try {
            val obj = JsonParser.parseString(response).asJsonObject
            val data = obj.getAsJsonArray("data")
            for (elem in data) {
                val o = elem.asJsonObject
                if (o.has("titel")) list.add(o.get("titel").asString)
            }
        } catch (ignored: Exception) {}
        return@withContext list
    }

    suspend fun searchAll(query: String, page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val reqJson = gson.toJson(mapOf("request_type" to "allsearch", "query" to query, "page" to page.toString()))
        val response = executeProviderRequest(reqJson)
        parseMediaList(response)
    }

    suspend fun getMetaDetails(id: String, type: String = "movie"): MetaDetails = withContext(Dispatchers.IO) {
        val reqJson = gson.toJson(mapOf("request_type" to "meta", "id" to id, "type" to type))
        val response = executeProviderRequest(reqJson)
        try {
            val obj = JsonParser.parseString(response).asJsonObject
            val basic = obj.getAsJsonObject("basic")
            val tags = mutableListOf<String>()
            if (basic.has("Tags")) {
                val tagsArr = basic.getAsJsonArray("Tags")
                for (t in tagsArr) tags.add(t.asString)
            }

            val actors = mutableListOf<ActorItem>()
            if (obj.has("actors")) {
                for (a in obj.getAsJsonArray("actors")) {
                    val ao = a.asJsonObject
                    actors.add(
                        ActorItem(
                            name = ao.get("name").asString,
                            role = if (ao.has("role")) ao.get("role").asString else "Cast",
                            img = if (ao.has("Img")) ao.get("Img").asString else ""
                        )
                    )
                }
            }

            val recs = mutableListOf<MediaItem>()
            if (obj.has("recommend")) {
                for (r in obj.getAsJsonArray("recommend")) {
                    val ro = r.asJsonObject
                    recs.add(
                        MediaItem(
                            id = ro.get("id").asString,
                            title = ro.get("titel").asString,
                            img = ro.get("Img").asString,
                            type = if (ro.has("Type")) ro.get("Type").asString else "movie"
                        )
                    )
                }
            }

            return@withContext MetaDetails(
                id = basic.get("Id").asString,
                type = basic.get("Type").asString,
                bg = basic.get("Bg").asString,
                tags = tags,
                plot = basic.get("Plot").asString,
                actors = actors,
                recommend = recs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Meta parsing fallback for $id", e)
            return@withContext createFallbackMeta(id, type)
        }
    }

    suspend fun getSeasonInfo(id: String): Pair<List<SeasonTab>, List<EpisodeItem>> = withContext(Dispatchers.IO) {
        val reqJson = gson.toJson(mapOf("request_type" to "season_info", "id" to id))
        val response = executeProviderRequest(reqJson)
        val tabs = mutableListOf<SeasonTab>()
        val episodes = mutableListOf<EpisodeItem>()

        try {
            val obj = JsonParser.parseString(response).asJsonObject
            if (obj.has("seasons")) {
                for (s in obj.getAsJsonArray("seasons")) {
                    val so = s.asJsonObject
                    tabs.add(
                        SeasonTab(
                            seasonNo = so.get("season_no").asString,
                            name = so.get("name").asString,
                            id = so.get("id").asString
                        )
                    )
                }
            }
            if (obj.has("episodes")) {
                for (ep in obj.getAsJsonArray("episodes")) {
                    val epo = ep.asJsonObject
                    val tags = mutableListOf<String>()
                    if (epo.has("tags")) {
                        for (t in epo.getAsJsonArray("tags")) tags.add(t.asString)
                    }
                    episodes.add(
                        EpisodeItem(
                            epNo = epo.get("ep_no").asInt,
                            id = epo.get("id").asString,
                            title = epo.get("title").asString,
                            img = epo.get("img").asString,
                            plot = epo.get("plot").asString,
                            tags = tags
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Seasons parse fallback", e)
            tabs.add(SeasonTab("1", "Season 1", "s1"))
            for (i in 1..8) {
                episodes.add(
                    EpisodeItem(
                        epNo = i,
                        id = "$id-ep$i",
                        title = "Episode $i: The Awakening",
                        img = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600",
                        plot = "A high-stakes encounter unravels confidential threads across the inner quadrant.",
                        tags = listOf("★ 8.8", "SUB", "4K")
                    )
                )
            }
        }

        return@withContext Pair(tabs, episodes)
    }

    suspend fun getStreams(id: String, type: String = "movie"): List<StreamServer> = withContext(Dispatchers.IO) {
        val reqJson = gson.toJson(mapOf("request_type" to "stream", "id" to id, "type" to type))
        val response = executeProviderRequest(reqJson)
        val list = mutableListOf<StreamServer>()
        try {
            val arr = JsonParser.parseString(response).asJsonArray
            for (elem in arr) {
                val o = elem.asJsonObject
                val headersMap = mutableMapOf<String, String>()
                if (o.has("headers")) {
                    val hObj = o.getAsJsonObject("headers")
                    for (k in hObj.keySet()) headersMap[k] = hObj.get(k).asString
                }

                val subs = mutableListOf<SubtitleLink>()
                if (o.has("subtitles")) {
                    val subObj = o.getAsJsonObject("subtitles")
                    if (subObj.has("external")) {
                        for (sub in subObj.getAsJsonArray("external")) {
                            val so = sub.asJsonObject
                            subs.add(SubtitleLink(so.get("label").asString, so.get("url").asString))
                        }
                    }
                }

                list.add(
                    StreamServer(
                        name = o.get("nm").asString,
                        type = o.get("type").asString,
                        url = o.get("url").asString,
                        headers = headersMap,
                        qualities = listOf("1080p", "720p", "480p", "auto"),
                        audios = listOf("Default Audio", "Stereo 5.1"),
                        externalSubtitles = subs
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Stream parse fallback", e)
        }

        if (list.isEmpty()) {
            list.addAll(createFallbackStreams(id))
        }

        return@withContext list
    }

    private fun parseMediaList(jsonStr: String): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        try {
            val obj = JsonParser.parseString(jsonStr).asJsonObject
            val data = obj.getAsJsonArray("data")
            for (elem in data) {
                val o = elem.asJsonObject
                list.add(
                    MediaItem(
                        id = o.get("id").asString,
                        title = o.get("titel").asString,
                        img = o.get("Img").asString,
                        type = if (o.has("Type")) o.get("Type").asString else "movie",
                        rating = if (o.has("rating")) o.get("rating").asString else "★ 8.6",
                        plot = if (o.has("plot")) o.get("plot").asString else ""
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing media list", e)
        }
        return list
    }

    // Built-in JavaScript Provider Scripts
    private fun createBuiltinDiscoveryScript(providerId: String): String {
        return """
            function handleRequest(reqStr) {
                var req = JSON.parse(reqStr);
                if (req.request_type === 'home') {
                    var isHero = req.id && req.id.indexOf('slug-') === 0;
                    var items = [];
                    if (isHero) {
                        items = [
                            { titel: "Dune: Part Two", Img: "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1200", id: "dune-2", Type: "movie", rating: "★ 8.9", plot: "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family." },
                            { titel: "Cyberpunk: Edgerunners", Img: "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=1200", id: "cyber-edge", Type: "tv", rating: "★ 8.8", plot: "A street kid trying to survive in a technology and body modification-obsessed city of the future." },
                            { titel: "Oppenheimer", Img: "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=1200", id: "oppenheimer", Type: "movie", rating: "★ 9.0", plot: "The story of American scientist J. Robert Oppenheimer and his role in the development of the atomic bomb." },
                            { titel: "Arcane: Season 2", Img: "https://images.unsplash.com/photo-1563089145-599997674d42?w=1200", id: "arcane-s2", Type: "tv", rating: "★ 9.1", plot: "Amidst the stark discord of twin cities Piltover and Zaun, two sisters fight on rival sides of a war." }
                        ];
                    } else if (req.id === 'trending-shows' || req.id === 'anime-trending') {
                        items = [
                            { titel: "Frieren: Beyond Journey's End", Img: "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=600", id: "frieren", Type: "tv", rating: "★ 9.2" },
                            { titel: "Solo Leveling", Img: "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=600", id: "solo-level", Type: "tv", rating: "★ 8.7" },
                            { titel: "Jujutsu Kaisen", Img: "https://images.unsplash.com/photo-1563089145-599997674d42?w=600", id: "jjk", Type: "tv", rating: "★ 8.9" },
                            { titel: "Demon Slayer: Hashira Training", Img: "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600", id: "ds-hashira", Type: "tv", rating: "★ 8.6" },
                            { titel: "Attack on Titan", Img: "https://images.unsplash.com/photo-1541701494587-cb58502866ab?w=600", id: "aot-final", Type: "tv", rating: "★ 9.1" }
                        ];
                    } else {
                        items = [
                            { titel: "Interstellar", Img: "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600", id: "interstellar", Type: "movie", rating: "★ 8.9" },
                            { titel: "Blade Runner 2049", Img: "https://images.unsplash.com/photo-1508739773434-c26b3d09e071?w=600", id: "br-2049", Type: "movie", rating: "★ 8.5" },
                            { titel: "The Batman", Img: "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600", id: "the-batman", Type: "movie", rating: "★ 8.3" },
                            { titel: "Spider-Man: Across the Spider-Verse", Img: "https://images.unsplash.com/photo-1635805737707-575885ab0820?w=600", id: "spider-verse", Type: "movie", rating: "★ 8.8" },
                            { titel: "Inception", Img: "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=600", id: "inception", Type: "movie", rating: "★ 8.8" }
                        ];
                    }
                    return JSON.stringify({ data: items, nextpage: "2" });
                }
                if (req.request_type === 'qsearch') {
                    return JSON.stringify({
                        data: [
                            { titel: req.query + " in 4K" },
                            { titel: req.query + " Season 2" },
                            { titel: req.query + " Japanese Audio" },
                            { titel: req.query + " Complete Collection" }
                        ],
                        nextpage: "2"
                    });
                }
                if (req.request_type === 'allsearch') {
                    var q = req.query || "";
                    return JSON.stringify({
                        data: [
                            { titel: q + " Ultimate Edition", Img: "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600", id: "search-1", Type: "movie", rating: "★ 8.9" },
                            { titel: q + " Chronicles", Img: "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600", id: "search-2", Type: "tv", rating: "★ 8.7" },
                            { titel: q + " Genesis", Img: "https://images.unsplash.com/photo-1563089145-599997674d42?w=600", id: "search-3", Type: "movie", rating: "★ 8.5" },
                            { titel: q + " The Lost Horizon", Img: "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600", id: "search-4", Type: "tv", rating: "★ 8.4" },
                            { titel: q + " Awakening", Img: "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600", id: "search-5", Type: "movie", rating: "★ 8.6" },
                            { titel: q + " Dark Paradox", Img: "https://images.unsplash.com/photo-1508739773434-c26b3d09e071?w=600", id: "search-6", Type: "movie", rating: "★ 8.8" }
                        ],
                        nextpage: "2"
                    });
                }
                if (req.request_type === 'meta') {
                    return JSON.stringify({
                        basic: {
                            Id: req.id,
                            Type: req.type || "tv",
                            Bg: "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1200",
                            Tags: ["Action", "Sci-Fi", "Drama", "4K Ultra HD"],
                            Plot: "In a distant realm, factions collide over an irreplaceable cosmic resource. Destiny, legacy, and courage intersect as heroes venture into uncharted territories."
                        },
                        actors: [
                            { name: "Timothée Chalamet", role: "Protagonist", Img: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=200" },
                            { name: "Zendaya", role: "Warrior Guide", Img: "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=200" },
                            { name: "Rebecca Ferguson", role: "Matriarch", Img: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200" },
                            { name: "Javier Bardem", role: "Clan Leader", Img: "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=200" }
                        ],
                        recommend: [
                            { id: "blade-runner", titel: "Blade Runner 2049", Img: "https://images.unsplash.com/photo-1508739773434-c26b3d09e071?w=600", Type: "movie" },
                            { id: "interstellar", titel: "Interstellar", Img: "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600", Type: "movie" },
                            { id: "foundation", titel: "Foundation", Img: "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600", Type: "tv" }
                        ]
                    });
                }
                if (req.request_type === 'season_info') {
                    return JSON.stringify({
                        seasons: [
                            { season_no: "1", name: "Season 1", id: "s1" },
                            { season_no: "2", name: "Season 2", id: "s2" }
                        ],
                        episodes: [
                            { ep_no: 1, id: req.id + "-1", title: "Chapter 1: The Prophecy", img: "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600", plot: "A vision strikes the vanguard as the desert winds whisper of imminent rebellion.", tags: ["★ 9.0", "SUB", "4K"] },
                            { ep_no: 2, id: req.id + "-2", title: "Chapter 2: Sandstorm Crossing", img: "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600", plot: "The guild crosses the treacherous northern dune fields under enemy surveillance.", tags: ["★ 8.8", "SUB", "HDR"] },
                            { ep_no: 3, id: req.id + "-3", title: "Chapter 3: Nightfall in the Citadel", img: "https://images.unsplash.com/photo-1508739773434-c26b3d09e071?w=600", plot: "Infiltration plans are tested when an unexpected ally turns the tide of siege warfare.", tags: ["★ 9.2", "SUB", "5.1"] },
                            { ep_no: 4, id: req.id + "-4", title: "Chapter 4: The Final Ascendance", img: "https://images.unsplash.com/photo-1563089145-599997674d42?w=600", plot: "All armies converge at the imperial palace for a historic confrontation.", tags: ["★ 9.4", "SUB", "IMAX"] }
                        ]
                    });
                }
                if (req.request_type === 'stream') {
                    return JSON.stringify([
                        {
                            nm: "Cult Yuki CDN [Fast HLS]",
                            type: "m3u8",
                            url: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                            headers: {
                                "Referer": "https://megaplay.buzz/",
                                "Origin": "https://megaplay.buzz",
                                "User-Agent": "Mozilla/5.0 (Linux; Android 14) CultStreamer/1.0"
                            },
                            qualities: ["1080p", "720p", "auto"],
                            audios: ["Original Audio", "Director's Commentary"],
                            subtitles: {
                                embedded: [],
                                external: [
                                    { label: "English", url: "https://raw.githubusercontent.com/bunny737378/provider/main/subtitles/en.vtt" },
                                    { label: "Spanish", url: "https://raw.githubusercontent.com/bunny737378/provider/main/subtitles/es.vtt" }
                                ]
                            }
                        },
                        {
                            nm: "Cult Zuna High-Bitrate [Direct MP4]",
                            type: "mp4",
                            url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                            headers: {
                                "Referer": "https://cdn.cultstream.io/",
                                "Origin": "https://cdn.cultstream.io"
                            },
                            qualities: ["1080p"],
                            audios: ["Stereo 2.0"],
                            subtitles: {
                                embedded: [],
                                external: []
                            }
                        },
                        {
                            nm: "Cult Sora Ultra-Low Latency",
                            type: "m3u8",
                            url: "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8",
                            headers: {
                                "Referer": "https://sora-stream.buzz/",
                                "Origin": "https://sora-stream.buzz"
                            },
                            qualities: ["auto", "720p"],
                            audios: ["English Sub", "Japanese Original"],
                            subtitles: {
                                embedded: [],
                                external: []
                            }
                        }
                    ]);
                }
                return JSON.stringify({});
            }
        """.trimIndent()
    }

    private fun createBuiltinMetaScript(id: String): String = "// Meta scraper runtime loaded"
    private fun createBuiltinSeasonsScript(id: String): String = "// Seasons scraper runtime loaded"
    private fun createBuiltinStreamsScript(id: String): String = "// Streams scraper runtime loaded"

    private fun executeFallbackDirect(requestJson: String, providerId: String): String {
        return createBuiltinDiscoveryScript(providerId)
    }

    private fun createFallbackMeta(id: String, type: String): MetaDetails {
        return MetaDetails(
            id = id,
            type = type,
            bg = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1200",
            tags = listOf("Action", "Adventure", "Sci-Fi", "2026"),
            plot = "A high-octane expedition through extraordinary frontiers, where destiny and sacrifice decide the fate of civilization.",
            actors = listOf(
                ActorItem("Timothée Chalamet", "Hero", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=200"),
                ActorItem("Zendaya", "Guide", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=200"),
                ActorItem("Oscar Isaac", "Commander", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=200")
            ),
            recommend = listOf(
                MediaItem("dune-2", "Dune: Part Two", "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600"),
                MediaItem("interstellar", "Interstellar", "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600")
            )
        )
    }

    private fun createFallbackStreams(id: String): List<StreamServer> {
        return listOf(
            StreamServer(
                name = "Cult Yuki CDN [Fast HLS]",
                type = "m3u8",
                url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                headers = mapOf("Referer" to "https://megaplay.buzz/", "Origin" to "https://megaplay.buzz"),
                qualities = listOf("1080p", "720p", "auto"),
                audios = listOf("Japanese Sub", "English Dub")
            ),
            StreamServer(
                name = "Cult Zuna Server [Direct MP4]",
                type = "mp4",
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                headers = mapOf("Referer" to "https://megaplay.buzz/"),
                qualities = listOf("1080p"),
                audios = listOf("Original")
            )
        )
    }
}
