package com.kienqb.dontv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.conscrypt.Conscrypt
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL
import java.security.Security
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        const val LAUNCHER_PKG = "com.gathin.tvhome"
        const val LAUNCHER_COMPONENT = "com.gathin.tvhome/.MainActivity"
        const val GOOGLE_TV_HOME = "com.google.android.apps.tv.launcherx"
        const val GOOGLE_TV_SETUP = "com.google.android.tungsten.setupwraith"
        const val REMOTE_APK = "/data/local/tmp/dontv-tvhome.apk"
    }

    private val worker = Executors.newSingleThreadExecutor()
    private var adb: TvAdb? = null
    private var host: String = ""
    private var stateId: String = ""
    private var installed: Set<String> = emptySet()
    private var disabledOnTv: Set<String> = emptySet()
    private var currentHome: String? = null
    private var models: List<TvModel> = emptyList()
    private var currentEntries: List<DebloatEntry> = emptyList()
    private val entryChecks = ArrayList<Pair<String, CheckBox>>()

    private lateinit var stepLabel: TextView
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var updateBanner: Button
    private var pendingUpdate: ReleaseInfo? = null
    private val steps = HashMap<Int, View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TLS providers for ADB pairing (libadb needs a modern JSSE + crypto).
        if (Security.getProvider("Conscrypt") == null) {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        }
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
        setContentView(R.layout.activity_main)

        stepLabel = findViewById(R.id.stepLabel)
        logView = findViewById(R.id.logView)
        logScroll = findViewById(R.id.logScroll)
        // A Sony Android 9 TV smeared this screen: every scroll frame left a
        // ghost copy of the text behind (seen on the user's own TV, v1.0.2).
        // The two scroll views now have opaque backgrounds in the layout, and
        // rendering them in software layers keeps the old TV GPU/driver out
        // of the composition path entirely. The UI is static text and forms,
        // so software rendering costs nothing noticeable.
        findViewById<ScrollView>(R.id.mainScroll).setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        logScroll.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        steps[1] = findViewById(R.id.step1)
        steps[2] = findViewById(R.id.step2)
        steps[3] = findViewById(R.id.step3)
        steps[4] = findViewById(R.id.step4)
        steps[5] = findViewById(R.id.step5)

        findViewById<Button>(R.id.selfIpButton).setOnClickListener {
            val ip = ownIpAddress()
            if (ip != null) {
                findViewById<EditText>(R.id.ipInput).setText(ip)
                log("Đã điền IP của thiết bị này: $ip")
            } else {
                toast("Không tìm thấy IP Wi-Fi. Hãy kiểm tra kết nối Wi-Fi.")
            }
        }
        findViewById<Button>(R.id.pairButton).setOnClickListener { doPair() }
        findViewById<Button>(R.id.connectButton).setOnClickListener { doConnect() }
        findViewById<Button>(R.id.backupNextButton).setOnClickListener { doBackupAndPrepare() }
        findViewById<Button>(R.id.restoreButton).setOnClickListener { doRestore() }
        findViewById<Button>(R.id.restoreButton2).setOnClickListener { doRestore() }
        findViewById<Button>(R.id.selectAllButton).setOnClickListener { setAllChecks(true) }
        findViewById<Button>(R.id.selectNoneButton).setOnClickListener { setAllChecks(false) }
        findViewById<Button>(R.id.disableButton).setOnClickListener { doDisable() }
        findViewById<Button>(R.id.skipDebloatButton).setOnClickListener { showStep(4) }
        findViewById<Button>(R.id.installHomeButton).setOnClickListener { doInstallHome() }
        findViewById<Button>(R.id.testHomeButton).setOnClickListener {
            val m = adbOrNull() ?: return@setOnClickListener
            runBg("Mở thử launcher") {
                m.exec("am start -n $LAUNCHER_COMPONENT")
                log("Đã gửi lệnh mở launcher. Nhìn lên TV kiểm tra giúp nhé.")
            }
        }
        findViewById<Button>(R.id.restartButton).setOnClickListener {
            runBg("Ngắt kết nối") {
                try { adb?.disconnect() } catch (_: Exception) {}
                adb = null
                ui { showStep(1) }
                log("Đã ngắt kết nối.")
            }
        }
        updateBanner = findViewById(R.id.updateBanner)
        updateBanner.setOnClickListener { pendingUpdate?.let { startUpdateDownload(it) } }
        maybeCheckForUpdate()
        log("Sẵn sàng. Nhập IP của TV rồi bấm KẾT NỐI.")
    }

    // -------------------------------------------------- in-app self update
    // Releases on github.com/kienqb/tidytv are the update source. The check
    // runs at most once a day, fetches a few KB of JSON, and only ever shows
    // the banner — downloading the APK takes an explicit tap.

    private fun currentVersionName(): String = try {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0).versionName ?: "0"
    } catch (_: Exception) {
        "0"
    }

    private fun maybeCheckForUpdate() {
        val prefs = getSharedPreferences("tidytv-update", MODE_PRIVATE)
        val cachedVersion = prefs.getString("version", null)
        val cachedUrl = prefs.getString("url", null)
        if (cachedVersion != null && cachedUrl != null &&
            AppUpdate.isNewer(cachedVersion, currentVersionName())
        ) {
            showUpdateBanner(ReleaseInfo(cachedVersion, cachedUrl))
        }
        val last = prefs.getLong("lastCheck", 0)
        if (System.currentTimeMillis() - last < 24 * 60 * 60 * 1000L) return
        worker.execute {
            try {
                val json = httpGetText("https://api.github.com/repos/kienqb/tidytv/releases/latest")
                prefs.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
                val info = AppUpdate.parseLatestRelease(json)
                if (info != null && AppUpdate.isNewer(info.version, currentVersionName())) {
                    prefs.edit().putString("version", info.version)
                        .putString("url", info.apkUrl).apply()
                    ui { showUpdateBanner(info) }
                } else {
                    prefs.edit().remove("version").remove("url").apply()
                }
            } catch (_: Exception) {
                // Silent: an update check must never disturb the wizard.
            }
        }
    }

    private fun showUpdateBanner(info: ReleaseInfo) {
        pendingUpdate = info
        updateBanner.text = "⬆ Có bản cập nhật TidyTV ${info.version} — bấm để tải & cài"
        updateBanner.isEnabled = true
        updateBanner.visibility = View.VISIBLE
    }

    private fun startUpdateDownload(info: ReleaseInfo) {
        updateBanner.isEnabled = false
        worker.execute {
            try {
                val dir = File(cacheDir, "updates").apply { mkdirs() }
                val out = File(dir, "TidyTV-update.apk")
                val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", "TidyTV")
                conn.inputStream.use { input ->
                    val total = conn.contentLengthLong
                    FileOutputStream(out).use { fos ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            fos.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                ui { updateBanner.text = "Đang tải bản mới… $pct%" }
                            }
                        }
                    }
                }
                ui {
                    updateBanner.text = "Đã tải xong — đang mở trình cài đặt…"
                    installUpdate(out)
                }
                log("Đã tải xong TidyTV ${info.version}. Nếu TV hỏi quyền cài ứng dụng không rõ nguồn gốc, hãy đồng ý rồi bấm lại nút cập nhật.")
            } catch (e: Exception) {
                ui {
                    updateBanner.text = "Tải bản mới thất bại (${e.message}) — bấm để thử lại"
                    updateBanner.isEnabled = true
                }
            }
        }
    }

    private fun installUpdate(apk: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(this, "com.kienqb.dontv.fileprovider", apk)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            toast("Không mở được trình cài đặt: ${e.message}")
        }
    }

    private fun httpGetText(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "TidyTV")
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    override fun onDestroy() {
        try { adb?.disconnect() } catch (_: Exception) {}
        try { adb?.close() } catch (_: Exception) {}
        worker.shutdownNow()
        super.onDestroy()
    }

    // ------------------------------------------------------------- helpers

    private fun ui(block: () -> Unit) = runOnUiThread(block)

    private fun toast(msg: String) = ui { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }

    private fun log(msg: String) = ui {
        logView.append(if (logView.text.isEmpty()) msg else "\n$msg")
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun showStep(n: Int) {
        for ((i, v) in steps) v.visibility = if (i == n) View.VISIBLE else View.GONE
        stepLabel.text = when (n) {
            1 -> "Bước 1/5: Kết nối tới TV"
            2 -> "Bước 2/5: Thông tin TV & sao lưu"
            3 -> "Bước 3/5: Dọn ứng dụng rác"
            4 -> "Bước 4/5: Cài launcher làm màn hình chính"
            else -> "Bước 5/5: Hoàn tất"
        }
    }

    private fun runBg(title: String, block: () -> Unit) {
        worker.execute {
            try {
                block()
            } catch (e: Exception) {
                log("✗ $title thất bại: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun ownIpAddress(): String? {
        try {
            for (iface in NetworkInterface.getNetworkInterfaces()) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    val h = addr.hostAddress ?: continue
                    if (!addr.isLoopbackAddress && h.contains('.')) return h
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun adbOrNull(): TvAdb? {
        if (adb == null) toast("Chưa kết nối tới TV. Quay lại Bước 1.")
        return adb
    }

    // ------------------------------------------------------------- step 1

    private fun readHost(): String? {
        val ip = findViewById<EditText>(R.id.ipInput).text.toString().trim()
        if (ip.isEmpty()) {
            toast("Chưa nhập IP của TV.")
            return null
        }
        return ip
    }

    private fun doPair() {
        val ip = readHost() ?: return
        val pairPort = findViewById<EditText>(R.id.pairPortInput).text.toString().trim().toIntOrNull()
        val code = findViewById<EditText>(R.id.pairCodeInput).text.toString().trim()
        if (pairPort == null || code.length != 6) {
            toast("Nhập đúng cổng ghép nối và mã 6 số hiện trên TV.")
            return
        }
        runBg("Ghép nối") {
            val m = TvAdb(this)
            val ok = m.pair(ip, pairPort, code)
            try { m.close() } catch (_: Exception) {}
            if (ok) log("✓ Ghép nối thành công. Giờ bấm KẾT NỐI TỚI TV.")
            else log("✗ Ghép nối không thành công. Kiểm tra lại mã (mã chỉ dùng được 1 lần, hết hạn phải lấy mã mới trên TV).")
        }
    }

    private fun doConnect() {
        val ip = readHost() ?: return
        val port = findViewById<EditText>(R.id.portInput).text.toString().trim().toIntOrNull() ?: 5555
        runBg("Kết nối") {
            log("Đang kết nối tới $ip:$port …")
            // Drop any previous connection first so retries never leak sockets.
            try { adb?.disconnect() } catch (_: Exception) {}
            try { adb?.close() } catch (_: Exception) {}
            adb = null
            val m = TvAdb(this)
            val ok = m.connect(ip, port)
            if (!ok) {
                log("✗ TV chưa chấp nhận kết nối. Nếu trên TV có hộp thoại «Cho phép gỡ lỗi USB?» (TV tự ghi vậy — đó là hỏi quyền ADB qua Wi-Fi) hãy bấm Cho phép rồi thử lại. TV đời mới thì phải Ghép nối bằng mã trước.")
                try { m.close() } catch (_: Exception) {}
                return@runBg
            }
            val check = m.exec("echo ok").trim()
            if (check != "ok") {
                log("✗ Kết nối được nhưng TV không trả lời lệnh. Thử lại sau ít giây.")
                try { m.close() } catch (_: Exception) {}
                return@runBg
            }
            adb = m
            host = ip
            log("✓ Đã kết nối tới TV $ip.")
            // Gather TV info
            val props = HashMap<String, String>()
            for (line in m.exec("getprop").lineSequence()) {
                val mm = Regex("""\[(.+?)\]: \[(.*)\]""").find(line) ?: continue
                props[mm.groupValues[1]] = mm.groupValues[2]
            }
            val brand = props["ro.product.brand"] ?: ""
            val model = props["ro.product.model"] ?: ""
            val device = props["ro.product.device"] ?: ""
            val androidVer = props["ro.build.version.release"] ?: ""
            installed = m.exec("pm list packages").lineSequence()
                .map { it.trim().removePrefix("package:") }
                .filter { it.isNotEmpty() }
                .toSet()
            disabledOnTv = m.exec("pm list packages -d").lineSequence()
                .map { it.trim().removePrefix("package:") }
                .filter { it.isNotEmpty() }
                .toSet()
            // State is keyed by the TV's serial when available: a DHCP change
            // of the TV's IP must not orphan the restore record.
            val serial = props["ro.serialno"]?.trim().orEmpty()
            stateId = serial.ifEmpty { ip }
            currentHome = parseHome(m.exec(
                "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"))
            models = loadModels()
            val suggestion = DebloatLogic.suggestModel(models, brand, model, device)
            ui {
                findViewById<TextView>(R.id.tvInfoText).text =
                    "TV: $brand $model\nAndroid $androidVer • ${installed.size} ứng dụng • Màn hình chính hiện tại: ${currentHome ?: "không rõ"}"
                val names = listOf("— Không dọn theo danh sách mẫu —") + models.map { it.name }
                val spinner = findViewById<Spinner>(R.id.modelSpinner)
                spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
                if (suggestion != null) {
                    spinner.setSelection(models.indexOf(suggestion) + 1)
                    log("App gợi ý danh sách: ${suggestion.name} (đối chiếu lại trên màn hình Bước 2).")
                }
                showStep(2)
            }
            log("Đã đọc thông tin TV xong. Kiểm tra ở Bước 2 rồi bấm SAO LƯU.")
        }
    }

    private fun parseHome(resolveOutput: String): String? {
        for (line in resolveOutput.lineSequence()) {
            val t = line.trim()
            if (t.contains('/')) return t.substringBefore('/').trim()
        }
        return null
    }

    private fun loadModels(): List<TvModel> {
        val text = assets.open("models/index.txt").bufferedReader().readText()
        return text.lineSequence().mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size == 2) TvModel(parts[0].trim(), parts[1].trim()) else null
        }.toList()
    }

    // ------------------------------------------------------------- step 2/3

    private fun stateFile() = File(filesDir, "state-$stateId.txt")

    private fun loadState(): MutableMap<String, String> {
        val map = HashMap<String, String>()
        // Fall back to the most recent state file when this TV has none yet
        // (e.g. its serial could not be read on a previous run).
        val f = stateFile()
        val src = if (f.exists()) f else File(filesDir, "state-last.txt")
        if (src.exists()) {
            for (line in src.readLines()) {
                val i = line.indexOf('=')
                if (i > 0) map[line.substring(0, i)] = line.substring(i + 1)
            }
        }
        return map
    }

    private fun saveState(map: Map<String, String>) {
        val text = map.entries.joinToString("\n") { "${it.key}=${it.value}" }
        stateFile().writeText(text)
        File(filesDir, "state-last.txt").writeText(text)
    }

    private fun doBackupAndPrepare() {
        val m = adbOrNull() ?: return
        val spinner = findViewById<Spinner>(R.id.modelSpinner)
        val pos = spinner.selectedItemPosition
        val chosen: TvModel? = if (pos > 0) models.getOrNull(pos - 1) else null
        val anim = findViewById<CheckBox>(R.id.animCheckbox).isChecked
        runBg("Sao lưu") {
            // Full package list backup, kept on this device and copied to the
            // TV as a real multi-line file (binary push, size-verified).
            val listText = m.exec("pm list packages")
            File(filesDir, "backup-$stateId-packages.txt").writeText(listText)
            val tvCopyOk = try {
                m.pushBytes(listText.toByteArray(Charsets.UTF_8), "/sdcard/TidyTV-sao-luu.txt")
            } catch (_: Exception) { false }
            val state = loadState()
            state["host"] = host
            if (state["stockHome"].isNullOrEmpty() && currentHome != null && currentHome != LAUNCHER_PKG) {
                state["stockHome"] = currentHome!!
            }
            if (anim) {
                // Record the TV's own current scales first so Restore can put
                // back exactly what was there instead of a hardcoded 1.0.
                fun readScale(key: String): String {
                    val v = m.exec("settings get global $key").trim()
                    return v.toFloatOrNull()?.toString() ?: "1.0"
                }
                if (state["animFrom"].isNullOrEmpty()) {
                    state["animFrom"] = listOf(
                        readScale("window_animation_scale"),
                        readScale("transition_animation_scale"),
                        readScale("animator_duration_scale"),
                    ).joinToString(",")
                }
                m.exec("settings put global window_animation_scale 0.5")
                m.exec("settings put global transition_animation_scale 0.5")
                m.exec("settings put global animator_duration_scale 0.5")
                state["anim"] = "0.5"
                log("✓ Đã giảm hiệu ứng chuyển cảnh xuống 0.5x.")
            }
            saveState(state)
            log("✓ Đã sao lưu danh sách ${installed.size} ứng dụng trong app này" +
                (if (tvCopyOk) " và trên TV (/sdcard/TidyTV-sao-luu.txt)." else " (bản sao trên TV gửi không thành — bản trong app vẫn đủ để khôi phục)."))
            // Prepare the debloat checklist
            currentEntries = if (chosen != null) {
                val text = assets.open("models/${chosen.slug}.txt").bufferedReader().readText()
                DebloatLogic.parseList(text)
            } else emptyList()
            ui { buildChecklist(chosen) }
            ui { showStep(3) }
        }
    }

    private fun buildChecklist(chosen: TvModel?) {
        val container = findViewById<LinearLayout>(R.id.checkContainer)
        container.removeAllViews()
        entryChecks.clear()
        val summary = findViewById<TextView>(R.id.debloatSummary)
        findViewById<Button>(R.id.disableButton).isEnabled = chosen != null
        if (chosen == null) {
            summary.text = "Bạn đã chọn không dọn theo danh sách mẫu. Bấm «Bỏ qua bước này» để sang cài launcher."
            return
        }
        var shown = 0
        var skippedProtected = 0
        var skippedAlreadyOff = 0
        for (e in currentEntries) {
            if (e.pkg !in installed) continue
            if (e.pkg in disabledOnTv) {
                // Already off before this app ever ran: not ours to record or
                // to re-enable on Restore.
                skippedAlreadyOff++
                continue
            }
            if (e.pkg in DebloatLogic.PROTECTED || e.pkg == currentHome) {
                skippedProtected++
                continue
            }
            val cb = CheckBox(this)
            cb.text = if (e.note.isNotEmpty()) "${e.pkg}\n(${e.note})" else e.pkg
            cb.textSize = 14.5f
            cb.isChecked = true
            container.addView(cb)
            entryChecks.add(e.pkg to cb)
            shown++
        }
        summary.text = "Danh sách ${chosen.name}: ${currentEntries.size} mục, TV này có $shown mục tắt được" +
            (if (skippedProtected > 0) " (đã tự loại $skippedProtected mục nhạy cảm/màn hình chính)" else "") +
            (if (skippedAlreadyOff > 0) " (bỏ qua $skippedAlreadyOff mục vốn đã tắt từ trước)" else "") +
            ". Chỉ TẮT chứ không xoá — khôi phục lại được. Bỏ tích mục nào muốn giữ."
    }

    private fun setAllChecks(value: Boolean) {
        for ((_, cb) in entryChecks) cb.isChecked = value
    }

    private fun doDisable() {
        val m = adbOrNull() ?: return
        val checked = entryChecks.filter { it.second.isChecked }.map { it.first }.toSet()
        val plan = DebloatLogic.buildPlan(currentEntries, checked, installed, currentHome, disabledOnTv)
        if (plan.isEmpty()) {
            toast("Không có ứng dụng nào được chọn/khả thi để tắt.")
            return
        }
        runBg("Dọn rác") {
            log("Bắt đầu tắt ${plan.size} ứng dụng…")
            for (pkg in plan) {
                m.exec("pm disable-user --user 0 $pkg")
            }
            // Verify by reading the TV's disabled list back: only packages
            // that are really disabled now get recorded (and later restored).
            val disabledAfter = m.exec("pm list packages -d").lineSequence()
                .map { it.trim().removePrefix("package:") }
                .filter { it.isNotEmpty() }
                .toSet()
            val done = plan.filter { it in disabledAfter }
            val failed = plan.filter { it !in disabledAfter }
            for (pkg in done) log("✓ Đã tắt $pkg")
            for (pkg in failed) log("✗ Không tắt được $pkg")
            disabledOnTv = disabledAfter
            val state = loadState()
            val prev = state["disabled"]?.split(',')?.filter { it.isNotEmpty() } ?: emptyList()
            state["disabled"] = (prev + done).distinct().joinToString(",")
            saveState(state)
            log("Xong: đã tắt ${done.size}/${plan.size} ứng dụng. Sang Bước 4: cài launcher.")
            ui { showStep(4) }
        }
    }

    // ------------------------------------------------------------- step 4

    private fun doInstallHome() {
        val m = adbOrNull() ?: return
        val googleTv = findViewById<CheckBox>(R.id.googletvCheckbox).isChecked
        runBg("Cài launcher") {
            val apkBytes = assets.open("tvhome.apk").readBytes()
            log("Đang gửi launcher (${apkBytes.size / 1024} KB) sang TV…")
            if (!m.pushBytes(apkBytes, REMOTE_APK)) {
                log("✗ Gửi file sang TV không trọn vẹn. Kiểm tra Wi-Fi rồi thử lại.")
                return@runBg
            }
            val installOut = m.exec("pm install -r $REMOTE_APK")
            if (!installOut.contains("Success")) {
                log("✗ Cài đặt thất bại: ${installOut.trim().take(120)}")
                return@runBg
            }
            log("✓ Đã cài launcher lên TV.")
            m.exec("cmd package set-home-activity $LAUNCHER_COMPONENT")
            var home = parseHome(m.exec(
                "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"))
            if (home != LAUNCHER_PKG && googleTv) {
                log("Đang tắt launcher gốc của Google TV…")
                m.exec("pm disable-user --user 0 $GOOGLE_TV_SETUP")
                m.exec("pm disable-user --user 0 $GOOGLE_TV_HOME")
                val state = loadState()
                state["googleHomeDisabled"] = "1"
                saveState(state)
                home = parseHome(m.exec(
                    "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"))
                if (home != LAUNCHER_PKG) {
                    // Safety: never leave a Google TV without a working Home.
                    log("⚠ Launcher mới chưa giành được nút Home — tự bật lại launcher gốc để TV dùng bình thường.")
                    m.exec("pm enable $GOOGLE_TV_HOME")
                    m.exec("pm enable $GOOGLE_TV_SETUP")
                    val stillOff = m.exec("pm list packages -d").lineSequence()
                        .map { it.trim().removePrefix("package:") }.toSet()
                    if (GOOGLE_TV_HOME in stillOff || GOOGLE_TV_SETUP in stillOff) {
                        // Rollback itself failed: KEEP the state flag so a later
                        // Restore retries re-enabling the stock launcher.
                        log("✗ Bật lại launcher gốc chưa thành công — app đã giữ lại ghi chép, hãy dùng nút KHÔI PHỤC ở Bước 2 để thử lại.")
                    } else {
                        val st = loadState(); st.remove("googleHomeDisabled"); saveState(st)
                    }
                    return@runBg
                }
            }
            if (home == LAUNCHER_PKG) {
                log("✓ Launcher đã là màn hình chính của TV.")
            } else {
                // Not Google TV and Home not won yet: do NOT advance to the
                // "Hoàn tất" screen — the user must pick TV Home on the TV.
                log("⚠ Đã cài launcher nhưng TV chưa chọn làm màn hình chính (hiện là $home). Bấm nút Home trên remote, nếu TV hỏi thì chọn TV Home. Xong quay lại đây bấm lại nút cài đặt để kiểm tra.")
                ui { findViewById<Button>(R.id.testHomeButton).isEnabled = true }
                return@runBg
            }
            ui {
                findViewById<Button>(R.id.testHomeButton).isEnabled = true
                findViewById<TextView>(R.id.doneText).text =
                    "Hoàn tất! Bấm nút Home trên remote để về màn hình chính mới.\n\nLưu ý: nếu đây là Google TV đã tắt launcher gốc, ĐỪNG tắt Gỡ lỗi trên TV — đó là đường để khôi phục khi cần. Muốn trả TV về như cũ: kết nối lại bằng app này và bấm KHÔI PHỤC."
                showStep(5)
            }
        }
    }

    // ------------------------------------------------------------- restore

    private fun doRestore() {
        val m = adbOrNull() ?: return
        runBg("Khôi phục") {
            val state = loadState()
            val recorded = state["disabled"]?.split(',')?.filter { it.isNotEmpty() } ?: emptyList()
            val wantGoogle = state["googleHomeDisabled"] == "1"
            if (recorded.isEmpty() && !wantGoogle && state["anim"] != "0.5" && state["stockHome"].isNullOrEmpty()) {
                log("Chưa có ghi chép thay đổi nào cho TV này — không có gì để khôi phục.")
                return@runBg
            }
            for (pkg in recorded) m.exec("pm enable $pkg")
            if (wantGoogle) {
                m.exec("pm enable $GOOGLE_TV_HOME")
                m.exec("pm enable $GOOGLE_TV_SETUP")
            }
            // Verify against the TV's real disabled list; only what is truly
            // re-enabled gets cleared from the record.
            val stillOff = m.exec("pm list packages -d").lineSequence()
                .map { it.trim().removePrefix("package:") }
                .filter { it.isNotEmpty() }
                .toSet()
            disabledOnTv = stillOff
            val stillRecorded = recorded.filter { it in stillOff }
            val restoredCount = recorded.size - stillRecorded.size
            val googleOk = !wantGoogle || (GOOGLE_TV_HOME !in stillOff && GOOGLE_TV_SETUP !in stillOff)
            if (stillRecorded.isEmpty()) state.remove("disabled")
            else state["disabled"] = stillRecorded.joinToString(",")
            if (googleOk) state.remove("googleHomeDisabled")
            // Stock Home back, verified afterwards.
            val stock = state["stockHome"]
            var homeNote = ""
            if (!stock.isNullOrEmpty()) {
                val resolved = parseHomeComponent(m.exec(
                    "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"))
                if (resolved?.startsWith(LAUNCHER_PKG) == true) {
                    val stockComponent = findHomeComponent(m, stock)
                    if (stockComponent != null) {
                        m.exec("cmd package set-home-activity $stockComponent")
                    }
                }
                val nowHome = parseHome(m.exec(
                    "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"))
                homeNote = if (nowHome == stock) " Màn hình chính đã về launcher gốc ($stock)."
                    else " Màn hình chính hiện là $nowHome — bấm Home và chọn launcher gốc nếu TV hỏi."
            }
            if (state["anim"] == "0.5") {
                val from = state["animFrom"]?.split(',') ?: listOf("1.0", "1.0", "1.0")
                m.exec("settings put global window_animation_scale ${from.getOrElse(0) { "1.0" }}")
                m.exec("settings put global transition_animation_scale ${from.getOrElse(1) { "1.0" }}")
                m.exec("settings put global animator_duration_scale ${from.getOrElse(2) { "1.0" }}")
                state.remove("anim"); state.remove("animFrom")
            }
            saveState(state)
            if (stillRecorded.isEmpty() && googleOk) {
                log("✓ Đã khôi phục xong: bật lại $restoredCount ứng dụng" +
                    (if (wantGoogle) " + launcher gốc Google TV" else "") + ", trả hiệu ứng về như trước.$homeNote")
                toast("Đã khôi phục TV về như cũ.")
            } else {
                log("⚠ Khôi phục chưa trọn: đã bật lại $restoredCount ứng dụng; còn chưa bật lại được: " +
                    (stillRecorded + listOfNotNull(
                        if (wantGoogle && !googleOk) GOOGLE_TV_HOME else null)).joinToString(", ") +
                    ". Ghi chép đã được giữ lại — kết nối lại và bấm KHÔI PHỤC lần nữa.$homeNote")
            }
        }
    }

    private fun parseHomeComponent(resolveOutput: String): String? {
        for (line in resolveOutput.lineSequence()) {
            val t = line.trim()
            if (t.contains('/')) return t
        }
        return null
    }

    private fun findHomeComponent(m: TvAdb, pkg: String): String? {
        // Ask the package manager which activity of the stock package answers HOME.
        val out = m.exec("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
        for (line in out.lineSequence()) {
            val t = line.trim()
            if (t.startsWith(pkg)) return t
        }
        return null
    }
}
