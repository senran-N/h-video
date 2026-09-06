package com.rou

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Document
import java.net.URLEncoder

class RouVideoProvider : MainAPI() {
    override var mainUrl = "https://rou.video"
    override var name = "RouVideo"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "zh"
    override val hasMainPage = true

    // data 格式: "tag:XXX" 走 /t/XXX 分页, "series" 走 /series 分页
    override val mainPage = mainPageOf(
        "tag:國產AV" to "國產AV",
        "tag:日本" to "日本",
        "tag:自拍流出" to "自拍流出",
        "tag:探花" to "探花",
        "tag:中文字幕" to "中文字幕",
        "tag:OnlyFans" to "OnlyFans",
        "series" to "劇集更新",
    )

    private val mapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    private fun getPageProps(doc: Document): JsonNode? {
        val json = doc.selectFirst("script#__NEXT_DATA__")?.data() ?: return null
        return try {
            mapper.readTree(json).path("props").path("pageProps")
        } catch (e: Exception) {
            null
        }
    }

    private fun pickName(node: JsonNode): String {
        val zh = node.path("nameZh").asText("")
        if (zh.isNotBlank()) return zh
        return node.path("name").asText("")
    }

    private fun videoNodeToSearch(node: JsonNode): SearchResponse? {
        val id = node.path("id").asText("").trim()
        if (id.isBlank()) return null
        val title = pickName(node).ifBlank { return null }
        val url = "/v/$id"
        val poster = node.path("coverImageUrl").asText(null)?.takeIf { it.isNotBlank() }
        val seriesId = node.path("seriesId").asText("")
        return if (seriesId.isNotBlank()) {
            newTvSeriesSearchResponse(title, url, TvType.TvSeries) {
                this.posterUrl = poster
            }
        } else {
            newMovieSearchResponse(title, url, TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }

    private fun seriesNodeToSearch(node: JsonNode): SearchResponse? {
        val id = node.path("id").asText("").trim()
        if (id.isBlank()) return null
        val title = pickName(node).ifBlank { return null }
        val poster = node.path("coverImageUrl").asText(null)?.takeIf { it.isNotBlank() }
        return newTvSeriesSearchResponse(title, "/s/$id", TvType.TvSeries) {
            this.posterUrl = poster
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = request.data
        return try {
            if (data == "series") {
                val doc = app.get("$mainUrl/series?page=$page").document
                val props = getPageProps(doc)
                val arr = props?.path("list")
                val list = mutableListOf<SearchResponse>()
                if (arr != null && arr.isArray) {
                    for (n in arr) {
                        seriesNodeToSearch(n)?.let { list.add(it) }
                    }
                }
                newHomePageResponse(request.name, list)
            } else if (data.startsWith("tag:")) {
                val tag = data.removePrefix("tag:")
                val enc = URLEncoder.encode(tag, "utf-8")
                val doc = app.get("$mainUrl/t/$enc?order=createdAt&page=$page").document
                val props = getPageProps(doc)
                val arr = props?.path("videos")
                val list = mutableListOf<SearchResponse>()
                if (arr != null && arr.isArray) {
                    for (n in arr) {
                        videoNodeToSearch(n)?.let { list.add(it) }
                    }
                }
                newHomePageResponse(request.name, list)
            } else {
                newHomePageResponse(request.name, emptyList())
            }
        } catch (e: Exception) {
            newHomePageResponse(request.name, emptyList())
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        return try {
            val enc = URLEncoder.encode(query, "utf-8")
            val doc = app.get("$mainUrl/search?q=$enc&page=$page").document
            val props = getPageProps(doc) ?: return newSearchResponseList(emptyList(), false)
            val arr = props.path("videos")
            val list = mutableListOf<SearchResponse>()
            if (arr.isArray) {
                for (n in arr) {
                    // 搜索结果可能是单视频, 也可能带 seriesId
                    videoNodeToSearch(n)?.let { list.add(it) }
                }
            }
            val pageNum = props.path("pageNum").asInt(page)
            val totalPage = props.path("totalPage").asInt(page)
            newSearchResponseList(list, pageNum < totalPage)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        return try {
            val doc = app.get(url).document
            val props = getPageProps(doc) ?: return null

            val seriesNode = props.path("series")
            val episodesNode = props.path("episodes")
            val videoNode = props.path("video")
            val hasSeries = !seriesNode.isMissingNode && !seriesNode.isNull && seriesNode.path("id").asText("").isNotBlank()
            val hasEpisodes = episodesNode.isArray && episodesNode.size() > 0
            val hasVideo = !videoNode.isMissingNode && !videoNode.isNull && videoNode.path("id").asText("").isNotBlank()

            if (hasSeries && hasEpisodes) {
                val seriesId = seriesNode.path("id").asText("")
                val seriesTitle = pickName(seriesNode).ifBlank { seriesId }
                val poster = seriesNode.path("coverImageUrl").asText(null)?.takeIf { it.isNotBlank() }
                val plot = seriesNode.path("description").asText(null)?.takeIf { it.isNotBlank() }
                val tags = seriesNode.path("tags").mapNotNull { it.asText(null)?.takeIf { s -> s.isNotBlank() } }.takeIf { it.isNotEmpty() }

                val episodes = episodesNode.mapNotNull { ep ->
                    val epId = ep.path("id").asText("").trim().ifBlank { return@mapNotNull null }
                    val epNum = ep.path("episode").asInt(0).takeIf { it > 0 }
                    val epName = ep.path("nameZh").asText("").ifBlank { ep.path("name").asText("") }
                    val epPoster = ep.path("coverImageUrl").asText(null)?.takeIf { it.isNotBlank() }
                    newEpisode("/v/$epId") {
                        this.name = epName.ifBlank { "第 ${epNum ?: "?"} 集" }
                        this.episode = epNum
                        this.posterUrl = epPoster
                    }
                }

                // 推荐: more / moreSeries
                val recs = mutableListOf<SearchResponse>()
                val moreNode = props.path("more")
                if (moreNode.isArray) {
                    for (n in moreNode) {
                        seriesNodeToSearch(n)?.let { recs.add(it) }
                    }
                }
                val moreSeriesNode = props.path("moreSeries")
                if (moreSeriesNode.isArray) {
                    for (n in moreSeriesNode) {
                        seriesNodeToSearch(n)?.let { recs.add(it) }
                    }
                }

                newTvSeriesLoadResponse(seriesTitle, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.plot = plot
                    this.tags = tags
                    this.recommendations = recs.takeIf { it.isNotEmpty() }
                }
            } else if (hasVideo) {
                val videoId = videoNode.path("id").asText("")
                val title = pickName(videoNode).ifBlank { videoId }
                val poster = videoNode.path("coverImageUrl").asText(null)?.takeIf { it.isNotBlank() }
                val plot = videoNode.path("description").asText(null)?.takeIf { it.isNotBlank() }
                val tags = videoNode.path("tags").mapNotNull { it.asText(null)?.takeIf { s -> s.isNotBlank() } }.takeIf { it.isNotEmpty() }
                // LoadResponse.duration 单位是分钟, 站点给的是秒
                val durationMin = videoNode.path("duration").asDouble(0.0).takeIf { it > 0 }?.div(60)?.toInt()

                val recs = mutableListOf<SearchResponse>()
                val related = props.path("relatedVideos")
                if (related.isArray) {
                    for (n in related) {
                        videoNodeToSearch(n)?.let { recs.add(it) }
                    }
                }

                newMovieLoadResponse(title, url, TvType.Movie, videoId) {
                    this.posterUrl = poster
                    this.plot = plot
                    this.tags = tags
                    this.duration = durationMin
                    this.recommendations = recs.takeIf { it.isNotEmpty() }
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun decryptEv(d: String, k: Int): JsonNode? {
        return try {
            val bytes = base64DecodeArray(d)
            val chars = CharArray(bytes.size) { i ->
                (((bytes[i].toInt() and 0xFF) - k)).toChar()
            }
            mapper.readTree(String(chars))
        } catch (e: Exception) {
            null
        }
    }

    private fun guessQuality(url: String): Int {
        return when {
            url.contains("-1080") -> Qualities.P1080.value
            url.contains("-720") -> Qualities.P720.value
            url.contains("-1280") -> Qualities.P720.value
            url.contains("-480") -> Qualities.P480.value
            else -> Qualities.Unknown.value
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val videoId = data.substringAfterLast("/").substringBefore("?").trim()
            if (videoId.isBlank()) return false

            val doc = app.get("$mainUrl/v/$videoId", referer = "$mainUrl/").document
            val props = getPageProps(doc) ?: return false
            val ev = props.path("ev")
            if (ev.isMissingNode || ev.isNull) return false
            val d = ev.path("d").asText("")
            val k = ev.path("k").asInt(-1)
            if (d.isBlank() || k < 0) return false

            val evJson = decryptEv(d, k) ?: return false
            var videoUrl = evJson.path("videoUrl").asText("")
            if (videoUrl.isBlank()) return false
            if (videoUrl.startsWith("/")) videoUrl = mainUrl + videoUrl

            // /api/hls/xxx 会 302 到带签名的 m3u8 (index.jpg/png), 需要拿到最终地址
            var finalUrl = videoUrl
            try {
                val resp = app.get(videoUrl, referer = "$mainUrl/v/$videoId", allowRedirects = false)
                val loc = resp.headers["location"] ?: resp.headers["Location"]
                if (!loc.isNullOrBlank()) {
                    finalUrl = if (loc.startsWith("/")) mainUrl + loc else loc
                } else if (resp.url.isNotBlank() && resp.url != videoUrl) {
                    finalUrl = resp.url
                }
            } catch (e: Exception) {
                // 保持 videoUrl, ExoPlayer 自己跟 302
            }

            callback(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = finalUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = guessQuality(finalUrl)
                    this.headers = mapOf(
                        "Referer" to "$mainUrl/",
                        "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36"
                    )
                }
            )
            true
        } catch (e: Exception) {
            false
        }
    }
}
