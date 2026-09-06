dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}

// Use an integer for version numbers
version = 1

cloudstream {
    // All of these properties are optional, you can safely remove any of them.

    description = "rou.video 肉视频源: 国产AV/日本/自拍/探花/剧集, HLS 直连"
    authors = listOf("h视频")

    /**
    * Status int as one of the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta-only
    **/
    status = 1 // Will be 3 if unspecified

    tvTypes = listOf("Movie", "TvSeries")

    requiresResources = false
    language = "zh"

    // 站点 favicon 做插件图标
    iconUrl = "https://rou.video/favicon-32x32.png"
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}