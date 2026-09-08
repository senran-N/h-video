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
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.Interceptor
import java.net.URLEncoder

/**
 * rou.video 源 (Next.js SSR 站, 数据全部内嵌在页面 #__NEXT_DATA__ JSON 里).
 *
 * 页面 -> pageProps 结构:
 *  - /series?page=N          -> list[], pageNum, totalPage
 *  - /t/{tag}?order=createdAt&page=N -> videos[], pageNum, totalPage
 *  - /search?q=&page=N       -> videos[], pageNum, totalPage
 *  - /v/{videoId}            -> video, relatedVideos[], series(可空), episodes[], moreSeries[], ev
 *  - /s/{seriesId}           -> series, episodes[], more[]
 *
 * 播放链:
 *  ev = { d: base64, k: int } ，每字节减 k 后得到 JSON { videoUrl: "/api/hls/{id}" }
 *  /api/hls/{id} 302 到 CDN 签名的 index.png (PNG 壳 m3u8)，分片同为 PNG 壳，
 *  由 RouVideoInterceptor 在 OkHttp 层拆包。
 */
class RouVideoProvider : MainAPI() {
    override var mainUrl = "https://rou.video"
    override var name = "RouVideo"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "zh"
    override val hasMainPage = true

    // data 格式: "series" 走 /series 分页, "tag:XXX" 走 /t/XXX 分页
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

    private val browserUA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private val defaultHeaders = mapOf(
        "User-Agent" to browserUA,
        "Referer" to "$mainUrl/",
        "Accept-Language" to "zh-CN,zh;q=0.9",
    )

    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor {
        // 站内 m3u8 / TS 分片套了 PNG 壳, 必须拆包播放器才能解析
        return RouVideoInterceptor()
    }

    // ---------------- JSON 工具 ----------------

    /** 抓取页面并取出 props.pageProps 节点; 失败返回 null */
    private suspend fun fetchPageProps(url: String, referer: String = "$mainUrl/"): JsonNode? {
        val json = try {
            app.get(url, headers = defaultHeaders, referer = referer).document
                .selectFirst("script#__NEXT_DATA__")?.data()
        } catch (e: Exception) {
            null
        } ?: return null
        return try {
            mapper.readTree(json).path("props").path("pageProps")
        } catch (e: Exception) {
            null
        }
    }

    private fun JsonNode.text(field: String): String = path(field).asText("").trim()

    private fun JsonNode.textOrNull(field: String): String? =
        path(field).asText(null)?.takeIf { it.isNotBlank() }

    private fun JsonNode.displayName(): String = textOrNull("nameZh") ?: textOrNull("name") ?: ""

    private fun JsonNode.poster(): String? = textOrNull("coverImageUrl")

    private fun JsonNode.tagsZh(): List<String>? =
        (path("tagsZh").takeIf { it.isArray && it.size() > 0 } ?: path("tags"))
            .mapNotNull { it.asText(null)?.takeIf { s -> s.isNotBlank() } }
            .takeIf { it.isNotEmpty() }

    /** 把数组节点映射成搜索结果列表 */
    private fun JsonNode.toSearchList(mapperFn: (JsonNode) -> SearchResponse?): List<SearchResponse> {
        if (!isArray) return emptyList()
        return mapNotNull { runCatching { mapperFn(it) }.getOrNull() }
    }

    // ---------------- 列表项构建 ----------------

    private fun videoToSearch(node: JsonNode): SearchResponse? {
        val id = node.text("id").ifBlank { return null }
        val title = node.displayName().ifBlank { return null }
        val poster = node.poster()
        val seriesId = node.textOrNull("seriesId")
        // 属于剧集的视频直接指向剧集页, load 时一次带出全部剧集
        return if (seriesId != null) {
            newTvSeriesSearchResponse(title, "$mainUrl/s/$seriesId", TvType.TvSeries) {
                this.posterUrl = poster
            }
        } else {
            newMovieSearchResponse(title, "$mainUrl/v/$id", TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }

    private fun seriesToSearch(node: JsonNode): SearchResponse? {
        val id = node.text("id").ifBlank { return null }
        val title = node.displayName().ifBlank { return null }
        return newTvSeriesSearchResponse(title, "$mainUrl/s/$id", TvType.TvSeries) {
            this.posterUrl = node.poster()
        }
    }

    // ---------------- 主页 ----------------

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val empty = newHomePageResponse(request.name, emptyList(), false)
        try {
            val props = when {
                request.data == "series" ->
                    fetchPageProps("$mainUrl/series?page=$page")

                request.data.startsWith("tag:") -> {
                    val tag = URLEncoder.encode(request.data.removePrefix("tag:"), "utf-8")
                        .replace("+", "%20")
                    fetchPageProps("$mainUrl/t/$tag?order=createdAt&page=$page")
                }

                else -> null
            } ?: return empty

            val isSeries = request.data == "series"
            val items = (if (isSeries) props.path("list") else props.path("videos"))
                .toSearchList(if (isSeries) ::seriesToSearch else ::videoToSearch)

            val pageNum = props.path("pageNum").asInt(page)
            val totalPage = props.path("totalPage").asInt(page)
            return newHomePageResponse(request.name, items, pageNum < totalPage)
        } catch (e: Exception) {
            return empty
        }
    }

    // ---------------- 搜索 ----------------

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        return try {
            val enc = URLEncoder.encode(query, "utf-8").replace("+", "%20")
            val props = fetchPageProps("$mainUrl/search?q=$enc&page=$page")
                ?: return newSearchResponseList(emptyList(), false)
            val list = props.path("videos").toSearchList(::videoToSearch)
            val hasNext = props.path("pageNum").asInt(page) < props.path("totalPage").asInt(page)
            newSearchResponseList(list, hasNext)
        } catch (e: Exception) {
            null
        }
    }

    // ---------------- 详情 ----------------

    override suspend fun load(url: String): LoadResponse? {
        return try {
            // 兼容旧版保存的相对路径
            val fixed = if (url.startsWith("http")) url else mainUrl + url
            val props = fetchPageProps(fixed, referer = "$mainUrl/") ?: return null

            val series = props.path("series").takeIf {
                !it.isMissingNode && !it.isNull && it.text("id").isNotBlank()
            }
            val episodesNode = props.path("episodes")
            val video = props.path("video").takeIf {
                !it.isMissingNode && !it.isNull && it.text("id").isNotBlank()
            }

            val seriesRecs = (props.path("moreSeries").toSearchList(::seriesToSearch) +
                    props.path("more").toSearchList(::seriesToSearch)).distinctBy { it.url }
            val videoRecs = props.path("relatedVideos").toSearchList(::videoToSearch)

            if (series != null && episodesNode.isArray && episodesNode.size() > 0) {
                // 剧集页 (/s/id 或带剧集的 /v/id 都统一走这里)
                val episodes = episodesNode.mapNotNull { ep ->
                    val epId = ep.text("id").ifBlank { return@mapNotNull null }
                    val epNum = ep.path("episode").asInt(0).takeIf { it > 0 }
                    newEpisode("$mainUrl/v/$epId") {
                        this.name = ep.displayName().ifBlank { "第 ${epNum ?: "?"} 集" }
                        this.episode = epNum
                        this.posterUrl = ep.poster()
                    }
                }
                newTvSeriesLoadResponse(series.displayName().ifBlank { series.text("id") }, fixed, TvType.TvSeries, episodes) {
                    this.posterUrl = series.poster()
                    this.plot = series.textOrNull("description")
                    this.tags = series.tagsZh()
                    this.recommendations = (seriesRecs + videoRecs)
                        .distinctBy { it.url }.takeIf { it.isNotEmpty() }
                }
            } else if (video != null) {
                newMovieLoadResponse(video.displayName().ifBlank { video.text("id") }, fixed, TvType.Movie, video.text("id")) {
                    this.posterUrl = video.poster()
                    this.plot = video.textOrNull("description")
                    this.tags = video.tagsZh()
                    // LoadResponse.duration 单位是分钟, 站点给的是秒
                    this.duration = video.path("duration").asDouble(0.0)
                        .takeIf { it > 0 }?.let { (it / 60).toInt() }
                    this.recommendations = (videoRecs + seriesRecs)
                        .distinctBy { it.url }.takeIf { it.isNotEmpty() }
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---------------- 播放链接 ----------------

    /** ev 解密: base64 解码后每字节减 k, 得到 JSON */
    private fun decryptEv(d: String, k: Int): JsonNode? {
        return try {
            val bytes = base64DecodeArray(d)
            val chars = CharArray(bytes.size) { i -> ((bytes[i].toInt() and 0xFF) - k).toChar() }
            mapper.readTree(String(chars))
        } catch (e: Exception) {
            null
        }
    }

    /** 从 m3u8 URL 中的 folder 后缀解析真实分辨率, 如 "…-720/index.png" -> 720 */
    private fun parseQuality(url: String): Int {
        return Regex("""[-_](\d{3,4})[/.]""").find(url)
            ?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    /** /api/hls/{id} 会 302 到带签名的 m3u8, 取最终地址; 失败则保留原地址交给播放器自己跳转 */
    private suspend fun resolveHlsUrl(apiUrl: String, videoId: String): String {
        return try {
            val resp = app.get(
                apiUrl,
                headers = defaultHeaders,
                referer = "$mainUrl/v/$videoId",
                allowRedirects = false,
            )
            val loc = resp.headers["location"] ?: resp.headers["Location"]
            when {
                !loc.isNullOrBlank() -> if (loc.startsWith("/")) mainUrl + loc else loc
                resp.url.isNotBlank() && resp.url != apiUrl -> resp.url
                else -> apiUrl
            }
        } catch (e: Exception) {
            apiUrl
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            // data 可能是裸 id 或 /v/{id} 路径
            val videoId = data.substringAfterLast("/").substringBefore("?").trim()
            if (videoId.isBlank()) return false

            // 1) 优先解析页面 ev 得到权威播放地址
            var videoUrl = fetchPageProps("$mainUrl/v/$videoId", referer = "$mainUrl/")
                ?.path("ev")
                ?.takeIf { !it.isMissingNode && !it.isNull }
                ?.let { ev ->
                    val d = ev.path("d").asText("")
                    val k = ev.path("k").asInt(-1)
                    if (d.isNotBlank() && k >= 0) decryptEv(d, k)?.path("videoUrl")?.asText("") else null
                }
                ?.takeIf { !it.isNullOrBlank() }

            // 2) ev 缺失时回退到已知的 /api/hls/{id} 约定
            if (videoUrl.isNullOrBlank()) videoUrl = "/api/hls/$videoId"
            val apiUrl = if (videoUrl.startsWith("/")) mainUrl + videoUrl else videoUrl

            val finalUrl = resolveHlsUrl(apiUrl, videoId)
            val quality = parseQuality(finalUrl)

            callback(
                newExtractorLink(
                    source = name,
                    name = if (quality > 0) "$name ${quality}p" else name,
                    url = finalUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = quality
                    this.headers = mapOf(
                        "Referer" to "$mainUrl/",
                        "User-Agent" to browserUA
                    )
                }
            )
            true
        } catch (e: Exception) {
            false
        }
    }
}
