package com.malfreyt.alexandre.pops_app.data

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context
import android.util.Log
import com.malfreyt.alexandre.pops_app.R
import java.io.IOException
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.UnknownHostException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OasisRemoteDataSource(
    private val context: Context,
) {
    suspend fun fetchSemesters(settings: AppSettings): List<RemoteSemesterData> {
        return withContext(Dispatchers.IO) {
            requireInternetConnection()

            val baseUrl = ensureTrailingSlash(settings.oasisBaseUrl)
            val client = buildClient(settings.ignoreTlsErrors)
            login(client, baseUrl, settings)

            val semesters = mutableListOf<RemoteSemesterData>()
            var emptyYears = 0

            for (academicYear in currentAcademicYear() downTo currentAcademicYear() - 10) {
                var yearHasContent = false
                for (semester in listOf(1, 2)) {
                    val semesterData = fetchSemester(client, baseUrl, settings.login, academicYear, semester)
                    if (semesterData.hasContent()) {
                        yearHasContent = true
                        semesters += semesterData
                    }
                }

                emptyYears = if (yearHasContent) 0 else emptyYears + 1
                if (semesters.isNotEmpty() && emptyYears >= 2) {
                    break
                }
            }

            semesters
        }
    }

    suspend fun testConnection(settings: AppSettings) {
        withContext(Dispatchers.IO) {
            requireInternetConnection()

            val baseUrl = ensureTrailingSlash(settings.oasisBaseUrl)
            val client = buildClient(settings.ignoreTlsErrors)
            login(client, baseUrl, settings)
        }
    }

    private fun login(client: OkHttpClient, baseUrl: String, settings: AppSettings) {
        val loginUrl = (baseUrl + LOGIN_PATH).toHttpUrl()
        Log.d(TAG, "Opening Oasis session on $loginUrl")
        try {
            client.newCall(Request.Builder().url(loginUrl).get().build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException(context.getString(R.string.error_oasis_session_init))
                }
            }

            val requestBody = FormBody.Builder()
                .add("login", settings.login)
                .add("password", settings.password)
                .add("url", "codepage=MYMARKS")
                .build()

            client.newCall(
                Request.Builder()
                    .url(loginUrl)
                    .post(requestBody)
                    .build()
            ).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(context.getString(R.string.error_oasis_auth_impossible))
                }
                val json = JSONObject(body)
                if (!json.optBoolean("success")) {
                    throw IllegalStateException(json.optString("text", context.getString(R.string.error_oasis_auth_denied)))
                }
                Log.d(TAG, "Oasis authentication succeeded")
            }
        } catch (error: IOException) {
            throw mapNetworkException(error)
        }
    }

    private fun fetchSemester(
        client: OkHttpClient,
        baseUrl: String,
        login: String,
        academicYear: Int,
        semester: Int,
    ): RemoteSemesterData {
        val requestBody = FormBody.Builder()
            .add("student", login)
            .add("year", academicYear.toString())
            .add("semester_in_year", semester.toString())
            .add("tab", "Tests")
            .build()

        try {
            client.newCall(
                Request.Builder()
                    .url((baseUrl + RELOAD_SEMESTER_PATH).toHttpUrl())
                    .post(requestBody)
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Semester fetch failed for year=$academicYear semester=$semester with code=${response.code}")
                    return RemoteSemesterData(
                        academicYear = academicYear,
                        semester = semester,
                        exams = emptyList(),
                        modules = emptyList(),
                        units = emptyList(),
                    )
                }
                val html = response.body?.string().orEmpty()
                Log.d(TAG, "Fetched year=$academicYear semester=$semester, ${html.length} chars")
                return parseSemesterHtml(html, academicYear, semester)
            }
        } catch (error: IOException) {
            throw mapNetworkException(error)
        }
    }

    private fun requireInternetConnection() {
        if (!hasInternetConnection()) {
            throw NoInternetConnectionException(context.getString(R.string.error_no_internet))
        }
    }

    private fun hasInternetConnection(): Boolean {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun mapNetworkException(error: IOException): IllegalStateException {
        if (error is UnknownHostException || error is ConnectException || error is SocketTimeoutException || !hasInternetConnection()) {
            return NoInternetConnectionException(context.getString(R.string.error_no_internet), error)
        }
        return IllegalStateException(context.getString(R.string.error_connection_impossible), error)
    }

    private fun parseSemesterHtml(html: String, academicYear: Int, semester: Int): RemoteSemesterData {
        val document = Jsoup.parse(html)
        return RemoteSemesterData(
            academicYear = academicYear,
            semester = semester,
            exams = parseExamRows(document, academicYear, semester),
            modules = parseModuleRows(document, academicYear, semester),
            units = parseUnitRows(document, academicYear, semester),
        )
    }

    private fun parseExamRows(document: org.jsoup.nodes.Document, academicYear: Int, semester: Int): List<RemoteGrade> {
        return document.select("div[id^=TabTestsSemester] tbody tr").mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size < 4) {
                return@mapNotNull null
            }

            val courseLineNode = cells[0].selectFirst("div.courseLine") ?: return@mapNotNull null
            val courseLine = courseLineNode.text().trim()
            if (courseLine.isBlank()) {
                return@mapNotNull null
            }

            val (subjectId, subject) = parseCodeAndTitle(courseLineNode.attr("data-code"), courseLine)
            val name = cells[1].text().trim()
            val dateText = cells[2].text().trim()
            if (name.isBlank() || dateText.isBlank() || dateText == "—") {
                return@mapNotNull null
            }

            val gradeText = cells[3].text().trim().replace(',', '.')
            val grade = gradeText.takeUnless { it.isBlank() || it == "—" }?.toDoubleOrNull()

            RemoteGrade(
                subjectId = subjectId,
                subject = subject,
                name = name,
                grade = grade,
                dateText = dateText,
                date = parseFrenchDate(dateText),
                semester = semester,
                academicYear = academicYear,
            )
        }
    }

    private fun parseModuleRows(document: org.jsoup.nodes.Document, academicYear: Int, semester: Int): List<ModuleSummary> {
        return document.select("div[id^=TabCoursesSemester] tbody tr").mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size < 9) {
                return@mapNotNull null
            }

            val moduleLineNode = cells[0].selectFirst("div.moduleLine") ?: return@mapNotNull null
            val moduleLine = moduleLineNode.text().trim()
            val (groupCode, groupTitle) = parseCodeAndTitle(moduleLineNode.attr("data-code"), moduleLine)

            ModuleSummary(
                groupCode = groupCode,
                groupTitle = groupTitle,
                code = displayText(cells[1].text()),
                title = displayText(cells[2].text()),
                coefficientLabel = displayText(cells[3].text()),
                blockLabel = displayText(cells[4].text()),
                gradeLabel = displayText(cells[5].text()),
                averageLabel = displayText(cells[6].text()),
                rankLabel = displayText(cells[7].text()),
                creditsLabel = displayText(cells[8].text()),
                semester = semester,
                academicYear = academicYear,
            )
        }
    }

    private fun parseUnitRows(document: org.jsoup.nodes.Document, academicYear: Int, semester: Int): List<UnitSummary> {
        return document.select("div[id^=TabModulesSemester] tbody tr").mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size < 8) {
                return@mapNotNull null
            }

            UnitSummary(
                code = displayText(cells[0].text()),
                title = displayText(cells[1].text()),
                ectsLabel = displayText(cells[2].text()),
                isCommonCore = cells[3].selectFirst("i.icon-radio-checked") != null,
                gradeLabel = displayText(cells[4].text()),
                averageLabel = displayText(cells[5].text()),
                rankLabel = displayText(cells[6].text()),
                resultLabel = displayText(cells[7].text()),
                semester = semester,
                academicYear = academicYear,
            )
        }
    }

    private fun parseCodeAndTitle(codeHint: String?, rawText: String): Pair<String, String> {
        val parts = rawText.split("—")
        val code = codeHint?.trim().orEmpty().ifBlank { parts.firstOrNull()?.trim().orEmpty().ifBlank { rawText } }
        val title = parts.drop(1).joinToString("—").trim().ifBlank { rawText }
        return code to title
    }

    private fun displayText(value: String): String {
        return value.trim().ifBlank { "—" }
    }

    private fun buildClient(ignoreTlsErrors: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .cookieJar(JavaNetCookieJar(CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) }))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (!ignoreTlsErrors) {
            return builder.build()
        }

        val trustManager = InsecureTrustManager
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())

        return builder
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier(InsecureHostnameVerifier)
            .build()
    }

    companion object {
        private const val LOGIN_PATH = "prod/bo/core/Router/Ajax/ajax.php?targetProject=oasis_polytech_paris&route=BO\\Connection\\User::login"
        private const val RELOAD_SEMESTER_PATH = "prod/bo/core/Router/Ajax/ajax.php?targetProject=oasis_polytech_paris&route=Oasis\\Common\\Model\\Cursus\\StudentCursus\\StudentCursus::reload_semester"
        private const val TAG = "PoPS-Oasis"
    }
}

private fun ensureTrailingSlash(url: String): String {
    return if (url.endsWith('/')) url else "$url/"
}

private object InsecureTrustManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

private object InsecureHostnameVerifier : HostnameVerifier {
    override fun verify(hostname: String?, session: javax.net.ssl.SSLSession?): Boolean = true
}

private class NoInternetConnectionException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)