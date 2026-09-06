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
import okhttp3.Interceptor
import org.jsoup.nodes.Document
import java.net.URLEncoder

class RouVideoProvider : MainAPI() {
    override var mainUrl = "https://rou.video"
    override var name = "RouVideo"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "zh"
    override val hasMainPage = true

    // data 格式: "tag:XXX" 走 /t/XXX 分页, "series" 走 /series 分页
    // 分类取自站点 /cat 索引, 按视频数排序
    override val mainPage = mainPageOf(
        "series" to "劇集更新",
        "tag:自拍流出" to "自拍流出",
        "tag:國產AV" to "國產AV",
        "tag:探花" to "探花",
        "tag:日本" to "日本",
        "tag:麻豆傳媒" to "麻豆傳媒",
        "tag:OnlyFans" to "OnlyFans",
        "tag:中文字幕" to "中文字幕",
        "tag:單體作品" to "單體作品",
        "tag:中出" to "中出",
        "tag:巨乳" to "巨乳",
        "tag:人妻" to "人妻",
        "tag:絲襪" to "絲襪",
        "tag:熟女" to "熟女",
        "tag:NTR" to "NTR",
        "tag:苗條" to "苗條",
        "tag:美少女" to "美少女",
        "tag:中國" to "中國",
        "tag:痴女" to "痴女",
        "tag:口交" to "口交",
        "tag:極限高潮" to "極限高潮",
        "tag:乳交" to "乳交",
        "tag:淫亂" to "淫亂",
        "tag:劇情" to "劇情",
        "tag:多人運動" to "多人運動",
        "tag:接吻" to "接吻",
        "tag:twitter" to "twitter",
        "tag:角色劇情" to "角色劇情",
        "tag:潮吹" to "潮吹",
        "tag:顏射" to "顏射",
        "tag:少女" to "少女",
        "tag:騎乘" to "騎乘",
        "tag:不倫" to "不倫",
        "tag:過膝襪" to "過膝襪",
        "tag:制服" to "制服",
        "tag:OL" to "OL",
        "tag:多P" to "多P",
        "tag:姐姐" to "姐姐",
        "tag:fansone" to "fansone",
        "tag:女高中生" to "女高中生",
        "tag:美乳" to "美乳",
        "tag:羞辱" to "羞辱",
        "tag:番外" to "番外",
        "tag:爆汗" to "爆汗",
        "tag:制服誘惑" to "制服誘惑",
        "tag:umate" to "umate",
        "tag:凌辱" to "凌辱",
        "tag:亂交" to "亂交",
        "tag:女教師" to "女教師",
        "tag:玩偶姊姊" to "玩偶姊姊",
        "tag:主播" to "主播",
        "tag:大屁股" to "大屁股",
        "tag:回春按摩" to "回春按摩",
        "tag:美腿" to "美腿",
        "tag:姐妹" to "姐妹",
        "tag:絲襪美腿" to "絲襪美腿",
        "tag:紀錄片" to "紀錄片",
        "tag:偶像藝人" to "偶像藝人",
        "tag:短髮" to "短髮",
        "tag:羞恥" to "羞恥",
        "tag:色控傳媒" to "色控傳媒",
        "tag:按摩油" to "按摩油",
        "tag:出道作" to "出道作",
        "tag:台灣" to "台灣",
        "tag:薄格" to "薄格",
        "tag:主觀視角" to "主觀視角",
        "tag:護士" to "護士",
        "tag:溫泉" to "溫泉",
        "tag:出軌" to "出軌",
        "tag:口爆" to "口爆",
        "tag:校服" to "校服",
        "tag:手淫" to "手淫",
        "tag:學生" to "學生",
        "tag:女大學生" to "女大學生",
        "tag:自拍" to "自拍",
        "tag:亂倫" to "亂倫",
        "tag:多P群交" to "多P群交",
        "tag:按摩" to "按摩",
        "tag:fortunecutie" to "fortunecutie",
        "tag:黑絲" to "黑絲",
        "tag:淫語" to "淫語",
        "tag:女僕" to "女僕",
        "tag:pornhub" to "pornhub",
        "tag:原創節目企劃" to "原創節目企劃",
        "tag:JVID" to "JVID",
        "tag:泳裝" to "泳裝",
        "tag:強制口交" to "強制口交",
    )

    private val mapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    private val defaultHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/",
        "Accept-Language" to "zh-CN,zh;q=0.9",
    )

    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor? {
        // 站内 m3u8 / TS 分片套了 PNG 壳, 必须拆包播放器才能解析
        return RouVideoInterceptor()
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
                val doc = app.get("$mainUrl/series?page=$page", headers = defaultHeaders).document
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
                val enc = URLEncoder.encode(tag, "utf-8").replace("+", "%20")
                val doc = app.get("$mainUrl/t/$enc?order=createdAt&page=$page", headers = defaultHeaders).document
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
            val enc = URLEncoder.encode(query, "utf-8").replace("+", "%20")
            val doc = app.get("$mainUrl/search?q=$enc&page=$page", headers = defaultHeaders).document
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
            val doc = app.get(url, headers = defaultHeaders, referer = "$mainUrl/").document
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

            val doc = app.get("$mainUrl/v/$videoId", headers = defaultHeaders, referer = "$mainUrl/").document
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
                val resp = app.get(videoUrl, headers = defaultHeaders, referer = "$mainUrl/v/$videoId", allowRedirects = false)
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
