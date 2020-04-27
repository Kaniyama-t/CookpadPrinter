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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        onReceive(this, intent)
    }

    private var mWebView : WebView? = null

    private var mLatestPrintJob: PrintJob? = null

    private fun onReceive(context: Context?, intent: Intent?) {
        // Loading some data
        if (intent!!.action != Intent.ACTION_SEND) return
        val extras = intent.extras ?: return
        val ext = extras.getCharSequence(Intent.EXTRA_TEXT).toString()

        // return when it haven't been found cookpad URL.
        if (!ext.contains(CookpadURL.sharedURLHead)){
            Toast.makeText(context,"cookpadのURLを認識できませんでした", Toast.LENGTH_LONG).show()
            finish()
        }

        // cutting from intent's extra text
        val recipeId = ext.substring(
            ext.indexOf(CookpadURL.sharedURLHead) + CookpadURL.sharedURLHead.length ,
            ext.length
        )

        // Create a WebView object specifically for printing
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
            override fun onPageFinished(view: WebView, url: String) {
                Log.i(ContentValues.TAG, "page finished loading $url")
                createWebPrintJob(context!!,view)
                mWebView = null
            }
        }

        // Generate an HTML document on the fly:
        webView.loadUrl(CookpadURL.printURL(recipeId))


        // Keep a reference to WebView object until you pass the PrintDocumentAdapter
        // to the PrintManager
        mWebView = webView
    }

    private fun createWebPrintJob(context: Context, webView: WebView) {
        // Get a PrintManager instance
        (context.getSystemService(Context.PRINT_SERVICE) as? PrintManager)?.let { printManager ->

            val jobName = "${context.getString(R.string.app_name)} Document"

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

    override fun onResume() {
        super.onResume()
        val latestPrintJob = mLatestPrintJob
        if(latestPrintJob is PrintJob &&
            (
                latestPrintJob.isQueued ||
                latestPrintJob.isBlocked ||
                latestPrintJob.isCancelled ||
                latestPrintJob.isCompleted ||
                latestPrintJob.isFailed ||
                latestPrintJob.isStarted
            )
        ) finish();
    }
}
