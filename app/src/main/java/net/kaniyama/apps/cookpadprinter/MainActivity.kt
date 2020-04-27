package net.kaniyama.apps.cookpadprinter

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintJob
import android.print.PrintManager
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    private var mWebView: WebView? = null
    private var mLatestPrintJob: PrintJob? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /* URL抽出がなかった(=takeIfでEmptyによりnull)場合はonErr#URL認識できない
         * レシピIDが抽出できなかった(=null)場合はonErr#レシピ認識できない
         * 抽出完了時はレンダーしてプリントジョブをポイする
         *
         * ・run loop@構文はここから
         * 　https://qiita.com/sudachi808/items/9146c4263d3a5a47b4dd
         */
        run loop@{
            getExtraURL().takeIf {
                it.isNotEmpty()
            }?.forEach { url ->
                extractRecipeId(url)?.let { recipeId ->
                    renderWebPage(
                        url = CookpadURL.printURL(recipeId),
                        onRendered = { createWebPrintJob(it) }
                    )
                    return@loop
                } ?: onErr("Cookpadのレシピを認識できませんでした...")
            } ?: onErr("URLを認識できませんでした...")
        }
    }

    override fun onResume() {
        super.onResume()
        // finish when printJob was generated
        val latestPrintJob = mLatestPrintJob
        if (latestPrintJob is PrintJob &&
            (
                    latestPrintJob.isQueued ||
                            latestPrintJob.isBlocked ||
                            latestPrintJob.isCancelled ||
                            latestPrintJob.isCompleted ||
                            latestPrintJob.isFailed ||
                            latestPrintJob.isStarted
                    )
        ) finish()
    }

    /***
     * extract URL from intent msg
     * @return List of URL strings(almost this length is 1 when cookpad) or EmptyList
     */
    private fun getExtraURL(): List<String> {
        if (intent!!.action != Intent.ACTION_SEND) return emptyList()
        val extras = intent.extras ?: return emptyList()
        val ext = extras.getCharSequence(Intent.EXTRA_TEXT).toString()

        /* 正規表現について
         * https"?"     <- "s"があってもなくても良い
         * [\\w...]+    <- []+内の文字が存在している間切り取る
         * \\w          <- 英数字か_
         * []内のほか文字<- 記号等。
         */
        val regex = "https?://[\\w!?/+\\-_~=;.,*&@#\$%()'\\[\\]]+"
        val urls = regex.toRegex(RegexOption.IGNORE_CASE).findAll(ext).map { it.value }
        return urls.toList()
    }

    /***
     * extract cookpad's RecipeId from source URL
     * @param url source url from intent msg
     * @return recipeId or null
     */
    private fun extractRecipeId(url: String): String? {
        val regex = "https://cookpad.com/recipe/[0-9]+"
        val src = regex.toRegex(RegexOption.IGNORE_CASE)
            .findAll(url)
            .map { it.value }
            .toList()
        return if (src.size == 1) src[0] else null
    }

    /***
     * render web page for printing
     * @param url URL rendered
     * @param onRendered process on rendered
     */
    private fun renderWebPage(url: String, onRendered: ((webView: WebView) -> Unit)) {
        // setup renderer
        val webView = WebView(this)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                false

            override fun onPageFinished(view: WebView, url: String) {
                Log.i(ContentValues.TAG, "page finished loading $url")
                onRendered(view)
                mWebView = null//開放
            }
        }
        // loading
        webView.loadUrl(url)

        mWebView = webView
    }

    /***
     * create PrintJob from WebView
     * @param webView rendered webView
     */
    private fun createWebPrintJob(webView: WebView) {
        // Get a PrintManager instance
        (getSystemService(Context.PRINT_SERVICE) as? PrintManager)?.let { printManager ->

            val jobName = "${getString(R.string.app_name)} Document"

            // Get a print adapter instance
            val printAdapter = webView.createPrintDocumentAdapter(jobName)

            // set A4 Size & Color
            val printAttributes = PrintAttributes.Builder().also {
                it.setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                it.setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            }.build()

            // Create a print job with name and adapter instance
            printManager.print(
                jobName,
                printAdapter,
                printAttributes
            ).let {
                mLatestPrintJob = it
            }
        }
    }

    /***
     * Called when got an Error and want to finish application.
     * @param msg message for User. It'll be shown user.
     */
    private fun onErr(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        finish()
    }
}
