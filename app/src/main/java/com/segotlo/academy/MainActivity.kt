package com.segotlo.academy

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var activePopupDialog: Dialog? = null
    private var activePopupWebView: WebView? = null

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private val webClientId = "484885784255-u4p9fe267s4kr4io5gllf8q1a3hqgi8i.apps.googleusercontent.com"

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    firebaseAuthWithGoogle(idToken)
                } else {
                    Toast.makeText(this, "Failed to get Google ID token", Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                Log.e("MainActivity", "Google Sign-In failed code: ${e.statusCode}", e)
                Toast.makeText(this, "Google Sign-In failed: ${e.statusCode}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupGoogleSignIn()

        webView = findViewById(R.id.webview)

        // Modify User-Agent to avoid Google OAuth "disallowed_useragent" error in WebView
        val defaultUserAgent = webView.settings.userAgentString
        val customUserAgent = defaultUserAgent.replace("; wv", "").replace("Version/4.0 ", "")

        // Enable cookies and third-party cookies
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            userAgentString = customUserAgent
        }

        // Add JavaScript Interface for Web App communication
        webView.addJavascriptInterface(WebAppInterface(), "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?,
            ): Boolean {
                val popupDialog = Dialog(this@MainActivity, android.R.style.Theme_Light_NoTitleBar_Fullscreen)
                val popupWebView = WebView(this@MainActivity).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.userAgentString = customUserAgent
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                CookieManager.getInstance().setAcceptThirdPartyCookies(popupWebView, true)

                popupWebView.webChromeClient = object : WebChromeClient() {
                    override fun onCloseWindow(window: WebView?) {
                        popupWebView.destroy()
                        popupDialog.dismiss()
                        activePopupDialog = null
                        activePopupWebView = null
                    }
                }

                popupWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        return false
                    }
                }

                popupDialog.setContentView(popupWebView)
                popupDialog.setOnDismissListener {
                    popupWebView.destroy()
                    activePopupDialog = null
                    activePopupWebView = null
                }

                activePopupDialog = popupDialog
                activePopupWebView = popupWebView

                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = popupWebView
                resultMsg?.sendToTarget()

                // Trigger Native Device Account Chooser
                startNativeGoogleSignIn()

                return true
            }

            override fun onCloseWindow(window: WebView?) {
                activePopupDialog?.dismiss()
                activePopupDialog = null
                activePopupWebView = null
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (activePopupDialog?.isShowing == true) {
                    activePopupDialog?.dismiss()
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        webView.loadUrl("https://segotlo-academy.web.app/")
    }

    private fun setupGoogleSignIn() {
        auth = Firebase.auth
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun startNativeGoogleSignIn() {
        runOnUiThread {
            auth.signOut()
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        auth.signOut()
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                val uid = user?.uid ?: ""
                val email = user?.email ?: ""
                val displayName = user?.displayName ?: ""
                val photoUrl = user?.photoUrl?.toString() ?: ""

                user?.getIdToken(true)?.addOnCompleteListener { tokenTask ->
                    val firebaseToken = tokenTask.result?.token ?: ""

                    val injectScript = """
                        (function() {
                            console.log("Injecting authenticated Google user profile:", "$displayName", "$email");
                            try {
                                var userObj = {
                                    uid: "$uid",
                                    email: "$email",
                                    displayName: "$displayName",
                                    name: "$displayName",
                                    photoURL: "$photoUrl",
                                    photoUrl: "$photoUrl"
                                };
                                localStorage.setItem("user", JSON.stringify(userObj));
                                localStorage.setItem("currentUser", JSON.stringify(userObj));
                                localStorage.setItem("userProfile", JSON.stringify(userObj));
                                localStorage.setItem("userName", "$displayName");
                                localStorage.setItem("displayName", "$displayName");
                                localStorage.setItem("userEmail", "$email");
                                localStorage.setItem("email", "$email");
                                localStorage.setItem("uid", "$uid");
                                window.currentUser = userObj;
                            } catch(e) {
                                console.error("Storage error:", e);
                            }

                            function proceedWithSignIn() {
                                if (window.handleAndroidAuth) {
                                    window.handleAndroidAuth('$firebaseToken', '$idToken', '$displayName', '$email');
                                } else if (window.firebase && window.firebase.auth) {
                                    var cred = window.firebase.auth.GoogleAuthProvider.credential('$idToken');
                                    window.firebase.auth().signInWithCredential(cred).then(function(res) {
                                        if (res && res.user && "$displayName") {
                                            res.user.updateProfile({ displayName: "$displayName", photoURL: "$photoUrl" });
                                        }
                                        location.reload();
                                    }).catch(function() {
                                        location.reload();
                                    });
                                } else {
                                    location.reload();
                                }
                            }

                            if (window.firebase && window.firebase.auth && window.firebase.auth().currentUser) {
                                window.firebase.auth().signOut().then(proceedWithSignIn).catch(proceedWithSignIn);
                            } else {
                                proceedWithSignIn();
                            }
                        })();
                    """.trimIndent()

                    webView.evaluateJavascript(injectScript, null)
                    activePopupWebView?.evaluateJavascript(injectScript, null)

                    // Dismiss the popup dialog if visible
                    activePopupDialog?.dismiss()
                    activePopupDialog = null
                    activePopupWebView = null
                }
            } else {
                Toast.makeText(this, "Firebase Authentication failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun launchGoogleSignIn() {
            startNativeGoogleSignIn()
        }

        @JavascriptInterface
        fun clearCookies() {
            runOnUiThread {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
        }
    }
}