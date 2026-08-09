package ng.leafsolar.admin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// ---------- Brand palette ----------
val Green = Color(0xFF2E7D32)
val GreenDark = Color(0xFF1B5E20)
val Lime = Color(0xFFAED581)
val Leaf = Color(0xFF43A047)
val Bg = Color(0xFFF4F7F5)
val Surface = Color(0xFFFFFFFF)
val Ink = Color(0xFF1B241D)
val InkMuted = Color(0xFF5A6B60)
val Line = Color(0xFFE6EDE8)
val Danger = Color(0xFFC62828)
val DangerBg = Color(0xFFFDECEA)
val Warn = Color(0xFFB26A00)
val WarnBg = Color(0xFFFFF4D6)
val InfoBg = Color(0xFFE8F1FF)
val Info = Color(0xFF0B4FA0)
val OkBg = Color(0xFFE4F7D6)

data class Order(val id: Long, val number: String, val name: String, val phone: String,
                val email: String, val items: List<String>, val total: Double,
                val status: String, val date: String)
data class Product(val id: Long, val name: String, val sku: String, val price: Double,
                   val manageStock: Boolean, val stockQty: Int?, val stockStatus: String,
                   val categories: List<String>, val image: String, val type: String,
                   val regularPrice: Double, val salePrice: Double?)

object Api {
  private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
  var base = "https://leafsolar.ng/wp-json/wc/v3/"
  var auth: String = ""
  fun basic(user:String,pass:String)=okhttp3.Credentials.basic(user,pass)
  fun setAuth(u:String,p:String){auth=basic(u.trim(),p.trim())}
  private fun exec(path: String, method: String = "GET", bodyJson: String? = null): String {
    val b = Request.Builder().url(base + path).header("Authorization", auth)
    if (bodyJson != null) b.method(method, bodyJson.toRequestBody("application/json".toMediaTypeOrNull()!!))
    else b.method(method, null)
    client.newCall(b.build()).execute().use { r ->
      if (!r.isSuccessful) {
        val err = r.body?.string()?.take(300) ?: ""
        throw IOException("HTTP ${r.code} $err")
      }
      return r.body?.string() ?: ""
    }
  }
  suspend fun orders(): List<Order> = withContext(Dispatchers.IO) {
    val arr = JSONArray(exec("orders?per_page=100&orderby=date&order=desc&status=pending,processing,on-hold,completed,cancelled,refunded,failed"))
    (0 until arr.length()).map { i ->
      val o = arr.getJSONObject(i); val b = o.optJSONObject("billing") ?: JSONObject()
      val items = mutableListOf<String>()
      val li = o.optJSONArray("line_items") ?: JSONArray()
      for (j in 0 until li.length()) items.add(li.getJSONObject(j).let { it.optString("name") + " ×" + it.optInt("quantity") })
      Order(o.getLong("id"), o.getString("number"),
        (b.optString("first_name") + " " + b.optString("last_name")).trim(),
        b.optString("phone"), b.optString("email"), items,
        o.optDouble("total", 0.0), o.optString("status", "pending"), o.optString("date_created_gmt", ""))
    }
  }
  suspend fun products(): List<Product> = withContext(Dispatchers.IO) {
    val all = mutableListOf<Product>(); var page = 1
    while (true) {
      val arr = JSONArray(exec("products?per_page=100&page=$page&status=publish&orderby=title&order=asc"))
      if (arr.length() == 0) break
      for (i in 0 until arr.length()) {
        val p = arr.getJSONObject(i)
        val catsArr = p.optJSONArray("categories") ?: JSONArray()
        val catNames = mutableListOf<String>()
        for (c in 0 until catsArr.length()) {
          val nm = catsArr.getJSONObject(c).optString("name", "")
          if (nm.isNotBlank() && nm.lowercase() != "electronics") catNames.add(nm)
        }
        all.add(Product(p.getLong("id"), p.getString("name"), p.optString("sku"),
          p.optDouble("price", 0.0), p.optBoolean("manage_stock"),
          if (p.isNull("stock_quantity")) null else p.optInt("stock_quantity"),
          p.optString("stock_status", "instock"), catNames,
          p.optString("image"), p.optString("type", "simple"),
          p.optDouble("regular_price", 0.0),
          if (p.isNull("sale_price") || p.optString("sale_price").isBlank()) null else p.optDouble("sale_price", 0.0)))
      }
      if (arr.length() < 100) break; page++
    }
    all
  }
  suspend fun updateProduct(id: Long, qty: Int? = null, manage: Boolean? = null, status: String? = null) = withContext(Dispatchers.IO) {
    val o = JSONObject()
    if (manage != null) o.put("manage_stock", manage)
    if (qty != null) o.put("stock_quantity", qty)
    if (status != null) o.put("stock_status", status)
    exec("products/$id", "PUT", o.toString())
  }
  suspend fun setStatus(id: Long, s: String) = withContext(Dispatchers.IO) { exec("orders/$id", "PUT", "{\"status\":\"$s\"}") }
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { LeafTheme { App(this) } }
  }
}

private val LightColors = lightColorScheme(
  primary = Green, onPrimary = Color.White,
  primaryContainer = OkBg, onPrimaryContainer = GreenDark,
  secondary = Leaf, background = Bg, surface = Surface,
  onBackground = Ink, onSurface = Ink,
  error = Danger, onError = Color.White,
  outline = Line
)
@Composable fun LeafTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = LightColors, content = content)

// ---------- Utilities ----------
fun money(v: Double) = "₦" + "%,.0f".format(Locale.US, v)
fun timeAgo(gmt: String): String {
  return try {
    val s = gmt.replace(" ", "T") + if (gmt.endsWith("Z")) "" else "Z"
    val then = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).parse(s) ?: return gmt
    val diff = System.currentTimeMillis() - then.time
    val m = TimeUnit.MILLISECONDS.toMinutes(diff)
    when {
      m < 1 -> "just now"
      m < 60 -> "${m}m ago"
      m < 1440 -> "${m/60}h ago"
      m < 43200 -> "${m/1440}d ago"
      else -> SimpleDateFormat("dd MMM", Locale.US).format(then)
    }
  } catch (e: Exception) { gmt }
}
fun initials(name: String): String {
  val p = name.trim().split(" ").filter { it.isNotBlank() }
  return when {
    p.isEmpty() -> "•"
    p.size == 1 -> p[0].take(2).replaceFirstChar { it.uppercase() }
    else -> "${p[0].first()}${p[1].first()}".uppercase()
  }
}
val AvatarColors = listOf(Color(0xFF3949AB), Color(0xFF00897B), Color(0xFFEF6C00),
  Color(0xFF8E24AA), Color(0xFFD81B60), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFFC62828))
fun avatarColor(name: String) = AvatarColors[(name.hashCode() and 0x7fffffff) % AvatarColors.size]

@Composable
fun App(act: ComponentActivity) {
  val prefs = act.getSharedPreferences("leaf-admin", 0)
  var user by remember { mutableStateOf<String?>(null) }
  var pass by remember { mutableStateOf<String?>(null) }
  var logged by remember { mutableStateOf(false) }
  var err by remember { mutableStateOf<String?>(null) }
  var loading by remember { mutableStateOf(false) }
  val orders = remember { mutableStateListOf<Order>() }
  val products = remember { mutableStateListOf<Product>() }
  var refreshing by remember { mutableStateOf(false) }
  var toast by remember { mutableStateOf<String?>(null) }
  var lastSync by remember { mutableStateOf(0L) }
  val scope = rememberCoroutineScope()
  val ctx = act.applicationContext
  val lifecycleOwner = LocalLifecycleOwner.current

  // global data + a flow that ticks every 45s for auto-refresh
  val data = remember { Pair(orders, products) }
  val tickFlow = remember { MutableStateFlow(0L) }

  fun refresh(after: () -> Unit = {}) {
    scope.launch {
      refreshing = true
      try { orders.replaceAll(Api.orders()); products.replaceAll(Api.products()); lastSync = System.currentTimeMillis() }
      catch (e: Exception) { toast = "Couldn't refresh: ${e.message}" }
      refreshing = false; after()
    }
  }

  fun registerPush() {
    if (!Fcm.available()) return
    Fcm.token { token ->
      if (token != null) {
        PushStore.saveToken(ctx, token)
        Thread { try { PushRegistrar.register(ctx, token) } catch (_: Exception) {} }.start()
      }
    }
  }

  val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
  fun ensureNotifPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(act, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  LaunchedEffect(Unit) {
    val u = prefs.getString("u", null); val p = prefs.getString("p", null)
    if (u != null && p != null) { Api.setAuth(u, p); user = u; pass = p; logged = true }
  }
  LaunchedEffect(logged) {
    if (logged) { refresh(); ensureNotifPermission(); registerPush() }
  }
  // Auto-refresh every 45 seconds while in foreground
  LaunchedEffect(logged) {
    while (logged) { delay(45_000); refresh() }
  }
  // Refresh when app returns to foreground
  DisposableEffect(lifecycleOwner) {
    val obs = LifecycleEventObserver { _, ev ->
      if (logged && ev == Lifecycle.Event.ON_RESUME) refresh()
    }
    lifecycleOwner.lifecycle.addObserver(obs)
    onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
  }
  toast?.let { m -> LaunchedEffect(m) { Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show(); toast = null } }

  if (!logged) {
    LoginScreen(user, pass, { user = it; err = null }, { pass = it; err = null }, err, loading, {
      if (user.isNullOrBlank() || pass.isNullOrBlank()) { err = "Enter username and application password"; return@LoginScreen }
      loading = true; err = null
      Thread {
        try { Api.setAuth(user!!, pass!!); runBlocking { Api.orders() }
          prefs.edit().putString("u", user).putString("p", pass).apply(); logged = true
        } catch (e: Exception) { err = "Login failed: ${e.message}" }
        loading = false
      }.start()
    })
  } else {
    var tab by remember {
      val openOrders = act.intent?.getBooleanExtra("open_orders", false) ?: false
      mutableStateOf(if (openOrders) 1 else 0)
    }
    fun mutateProduct(id: Long, transform: (Product) -> Product) {
      val i = products.indexOfFirst { it.id == id }
      if (i >= 0) products[i] = transform(products[i])
    }

    val onUpdate: (Product, Int?, Boolean?, String?) -> Unit = { p, qty, manage, status ->
      mutateProduct(p.id) { it.copy(
        manageStock = manage ?: it.manageStock,
        stockQty = qty ?: it.stockQty,
        stockStatus = status ?: it.stockStatus,
      ) }
      scope.launch {
        try {
          Api.updateProduct(p.id, qty, manage, status)
          runCatching { products.replaceAll(Api.products()) }
        } catch (e: Exception) {
          toast = "Save failed: ${e.message}"
          runCatching { products.replaceAll(Api.products()) }
        }
      }
    }

    val onBulkStock: (List<Long>, String) -> Unit = bulk@ { ids, status ->
      if (ids.isEmpty()) return@bulk
      val target = ids.toHashSet()
      for (i in products.indices) {
        if (products[i].id in target) {
          products[i] = products[i].copy(
            stockStatus = status,
            manageStock = if (status == "outofstock") true else products[i].manageStock,
            stockQty = if (status == "outofstock") 0 else products[i].stockQty)
        }
      }
      scope.launch {
        var ok = 0; var fail = 0
        coroutineScope {
          ids.map { id ->
            async(Dispatchers.IO) {
              val r = runCatching {
                if (status == "outofstock") Api.updateProduct(id, 0, true, "outofstock")
                else Api.updateProduct(id, null, null, "instock")
              }
              if (r.isSuccess) ok++ else fail++
            }
          }.awaitAll()
        }
        toast = if (fail == 0) "Marked $ok product${if (ok==1)"" else "s"} ${if (status=="outofstock") "out of stock" else "in stock"}"
                else "Done ($ok ok, $fail failed)"
        runCatching { products.replaceAll(Api.products()) }
      }
    }

    val onStatus: (Order, String) -> Unit = { o, s ->
      scope.launch {
        try { Api.setStatus(o.id, s); orders.replaceAll(Api.orders()); toast = "Order #${o.number} → ${s.replaceFirstChar { it.uppercase() }} · customer emailed" }
        catch (e: Exception) { toast = "Update failed: ${e.message}" }
      }
    }

    MainScaffold(tab, { tab = it }, orders, products, refreshing, lastSync,
      onLogout = {
        val t = PushStore.getToken(ctx)
        if (!t.isNullOrBlank()) Thread { try { PushRegistrar.unregister(ctx, t) } catch (_: Exception) {} }.start()
        prefs.edit().clear().apply(); logged = false
      },
      onRefresh = { refresh() },
      onStatus = onStatus,
      onUpdate = onUpdate,
      onBulkStock = onBulkStock
    )
  }
}

// ---------- Login ----------
@Composable fun LoginScreen(user: String?, pass: String?, onUser: (String) -> Unit, onPass: (String) -> Unit,
  err: String?, loading: Boolean, onLogin: () -> Unit) {
  Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0E1B12), GreenDark, Green)))) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
      Text("🌿 Leaf Admin", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
      Spacer(Modifier.height(4.dp))
      Text("Inventory & order management", color = Color(0xFFCFE9D2), fontSize = 15.sp)
      Spacer(Modifier.height(30.dp))
      Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = .97f)) {
        Column(Modifier.padding(20.dp)) {
          OutlinedTextField(user ?: "", onUser, label = { Text("Username or email") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            shape = RoundedCornerShape(12.dp))
          Spacer(Modifier.height(12.dp))
          OutlinedTextField(pass ?: "", onPass, label = { Text("Application password") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
          err?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Danger, fontSize = 13.sp) }
          Spacer(Modifier.height(16.dp))
          Button(onLogin, Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White),
            shape = RoundedCornerShape(12.dp), enabled = !loading) {
            Text(if (loading) "Signing in…" else "Sign in", fontWeight = FontWeight.Bold, fontSize = 16.sp)
          }
        }
      }
      Spacer(Modifier.height(14.dp))
      Text("Stored only on this device. Notifications for new orders.", color = Color(0xFFB5D7BA), fontSize = 12.sp)
    }
  }
}

// ---------- Main shell ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MainScaffold(
  tab: Int, onTab: (Int) -> Unit,
  orders: List<Order>, products: List<Product>,
  refreshing: Boolean, lastSync: Long,
  onLogout: () -> Unit, onRefresh: () -> Unit,
  onStatus: (Order, String) -> Unit,
  onUpdate: (Product, Int?, Boolean?, String?) -> Unit,
  onBulkStock: (List<Long>, String) -> Unit
) {
  val pending = orders.count { it.status == "pending" || it.status == "on-hold" }
  val titles = listOf("Dashboard", "Orders", "Inventory")
  Scaffold(
    containerColor = Bg,
    topBar = {
      Surface(color = Surface, shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text(titles[tab], fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
              val syncTxt = if (lastSync == 0L) "Syncing…" else "Updated ${timeAgo(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(lastSync)))}"
              Text(syncTxt, fontSize = 11.5.sp, color = InkMuted)
            }
            if (refreshing) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp), color = Green)
            else IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh", tint = InkMuted) }
          }
        }
      }
    },
    bottomBar = {
      NavigationBar(containerColor = Surface, tonalElevation = 8.dp) {
        listOf(
          Triple("Dashboard", Icons.Default.Dashboard, 0),
          Triple("Orders", Icons.Default.ReceiptLong, 1),
          Triple("Inventory", Icons.Default.Inventory2, 2)
        ).forEach { (label, icon, idx) ->
          NavigationBarItem(
            selected = tab == idx, onClick = { onTab(idx) },
            icon = {
              BadgedBox(badge = {
                if (idx == 1 && pending > 0) Badge(containerColor = Danger, contentColor = Color.White) { Text("$pending") }
              }) { Icon(icon, label) }
            },
            label = { Text(label, fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, indicatorColor = OkBg)
          )
        }
      }
    }
  ) { pad ->
    Box(Modifier.padding(pad).fillMaxSize()) {
      when (tab) {
        0 -> DashboardScreen(orders, products, onRefresh)
        1 -> OrdersScreen(orders, onRefresh, onStatus)
        else -> InventoryScreen(products, onRefresh, onUpdate, onBulkStock)
      }
    }
  }
}

// ---------- Reusable pieces ----------
@Composable fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
  Surface(shape = RoundedCornerShape(16.dp), color = Surface, shadowElevation = 1.dp) {
    Column(Modifier.padding(14.dp), content = content)
  }
}
@Composable fun StatusPill(s: String) {
  val (bg, fg, label) = when (s) {
    "completed" -> Triple(OkBg, GreenDark, "Completed")
    "processing" -> Triple(InfoBg, Info, "Processing")
    "pending", "on-hold" -> Triple(WarnBg, Warn, if (s == "on-hold") "On hold" else "Pending")
    "cancelled", "failed", "refunded" -> Triple(DangerBg, Danger, s.replaceFirstChar { it.uppercase() })
    else -> Triple(Line, InkMuted, s.replaceFirstChar { it.uppercase() })
  }
  Surface(color = bg, shape = RoundedCornerShape(999.dp)) {
    Text(label, color = fg, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
      modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
  }
}

@Composable fun PullRefresh(refreshing: Boolean, onRefresh: () -> Unit, content: @Composable () -> Unit) {
  SwipeRefresh(state = rememberSwipeRefreshState(refreshing), onRefresh = onRefresh,
    indicator = { s, trigger ->
      com.google.accompanist.swiperefresh.SwipeRefreshIndicator(
        s, trigger, contentColor = Green, largeIndication = true, elevation = 4.dp)
    }) { content() }
}

// ---------- Dashboard ----------
@Composable fun DashboardScreen(orders: List<Order>, products: List<Product>, onRefresh: () -> Unit) {
  val revenue = orders.filter { it.status in listOf("processing","completed") }.sumOf { it.total }
  val pending = orders.count { it.status == "pending" || it.status == "on-hold" }
  val done = orders.count { it.status == "completed" }
  val out = products.count { it.stockStatus == "outofstock" }
  val low = products.count { it.manageStock && (it.stockQty ?: 0) in 1..5 }
  val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
  val todays = orders.count { it.date.startsWith(today) }
  PullRefresh(false, onRefresh) {
    LazyColumn(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      item {
        Surface(shape = RoundedCornerShape(18.dp), color = GreenDark) {
          Column(Modifier.padding(16.dp)) {
            Text("Today's revenue", color = Color(0xFFCDE9CF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(money(revenue), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            Text("$todays order${if (todays==1) "" else "s"} today", color = Color(0xFFDCEFDE), fontSize = 12.5.sp)
          }
        }
      }
      item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          MiniStat("Pending", "$pending", Modifier.weight(1f), Warn)
          MiniStat("Completed", "$done", Modifier.weight(1f), Green)
        }
      }
      item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          MiniStat("Out of stock", "$out", Modifier.weight(1f), Danger)
          MiniStat("Low stock", "$low", Modifier.weight(1f), Warn)
        }
      }
      item {
        SectionCard {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recent orders", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
          }
          Spacer(Modifier.height(6.dp))
          if (orders.isEmpty()) Text("No orders yet.", fontSize = 12.5.sp, color = InkMuted)
          orders.take(6).forEach { o ->
            Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
              Box(Modifier.size(36.dp).clip(CircleShape).background(avatarColor(o.name)), contentAlignment = Alignment.Center) {
                Text(initials(o.name), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
              Spacer(Modifier.width(10.dp))
              Column(Modifier.weight(1f)) {
                Text("#${o.number}", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                Text(o.name.ifBlank { "Guest" }, fontSize = 12.sp, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
              Text(money(o.total), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Green)
              Spacer(Modifier.width(8.dp)); StatusPill(o.status)
            }
          }
        }
      }
      item { Spacer(Modifier.height(4.dp)) }
    }
  }
}
@Composable fun MiniStat(label: String, value: String, m: Modifier, accent: Color) {
  Surface(shape = RoundedCornerShape(16.dp), color = Surface, shadowElevation = 1.dp, modifier = m) {
    Column(Modifier.padding(14.dp)) {
      Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = accent)
      Spacer(Modifier.height(2.dp))
      Text(label, fontSize = 11.5.sp, color = InkMuted, fontWeight = FontWeight.SemiBold)
    }
  }
}

// ---------- Orders ----------
@OptIn(ExperimentalLayoutApi::class)
@Composable fun OrdersScreen(orders: List<Order>, onRefresh: () -> Unit, onStatus: (Order, String) -> Unit) {
  var filter by remember { mutableStateOf("all") }
  val ctx = LocalContext.current
  val filters = listOf("all" to "All", "pending" to "Pending", "processing" to "Processing", "completed" to "Done", "cancelled" to "Cancelled")
  val shown = remember(orders, filter) {
    when (filter) {
      "all" -> orders
      "pending" -> orders.filter { it.status == "pending" || it.status == "on-hold" }
      else -> orders.filter { it.status == filter }
    }
  }
  Column(Modifier.fillMaxSize()) {
    LazyRow(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      items(filters) { (k, label) ->
        val active = filter == k
        val n = when (k) {
          "pending" -> orders.count { it.status == "pending" || it.status == "on-hold" }
          "processing" -> orders.count { it.status == "processing" }
          "completed" -> orders.count { it.status == "completed" }
          "cancelled" -> orders.count { it.status in listOf("cancelled","failed","refunded") }
          else -> orders.size
        }
        FilterPill(label, n, active) { filter = k }
      }
    }
    PullRefresh(false, onRefresh) {
      if (shown.isEmpty()) EmptyState("No orders in this view")
      else LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(shown, key = { it.id }) { o -> OrderCard(o, ctx, onStatus) }
        item { Spacer(Modifier.height(60.dp)) }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun OrderCard(o: Order, ctx: android.content.Context, onStatus: (Order, String) -> Unit) {
  var exp by remember { mutableStateOf(false) }
  SectionCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(42.dp).clip(CircleShape).background(avatarColor(o.name)), contentAlignment = Alignment.Center) {
        Text(initials(o.name), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
      }
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text("#${o.number}", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        Text((o.name.ifBlank { "Guest" }) + " · " + timeAgo(o.date), fontSize = 12.sp, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      StatusPill(o.status)
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(money(o.total), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Green, modifier = Modifier.weight(1f))
      if (o.phone.isNotBlank()) {
        Icon(Icons.Default.Call, "Call", tint = InkMuted, modifier = Modifier.size(20.dp).clickable {
          runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${o.phone.trim()}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        })
        Spacer(Modifier.width(14.dp))
        Icon(Icons.Default.Chat, "WhatsApp", tint = Green, modifier = Modifier.size(20.dp).clickable {
          val raw = o.phone.replace(Regex("[^0-9]"), "")
          val num = if (raw.startsWith("0")) "234" + raw.trimStart('0') else raw
          val msg = "Hello ${o.name}, regarding your Leaf Solar order #${o.number}."
          runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$num?text=" + Uri.encode(msg))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        })
      }
      Spacer(Modifier.width(14.dp))
      Text(if (exp) "Hide" else "Manage", color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        textDecoration = TextDecoration.Underline, modifier = Modifier.clickable { exp = !exp })
    }
    AnimatedVisibility(visible = exp) {
      Column {
        HorizontalDiviner()
        o.items.forEach { Text("• $it", fontSize = 13.sp, color = Ink, modifier = Modifier.padding(vertical = 2.dp)) }
        if (o.email.isNotBlank()) {
          Spacer(Modifier.height(6.dp))
          Text(o.email, fontSize = 12.sp, color = Green, modifier = Modifier.clickable {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${o.email}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
          })
        }
        Spacer(Modifier.height(12.dp))
        Text("Update status — customer gets an email", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = InkMuted)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf("pending" to "Pending", "on-hold" to "On hold", "processing" to "Processing", "completed" to "Completed", "cancelled" to "Cancelled").forEach { (s, label) ->
            val cur = s == o.status
            Surface(color = if (cur) Green else Color(0xFFF1F5F2), shape = RoundedCornerShape(9.dp),
              modifier = Modifier.clickable(enabled = !cur) { onStatus(o, s) }) {
              Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                color = if (cur) Color.White else Ink,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
          }
        }
      }
    }
  }
}

// ---------- Inventory ----------
@OptIn(ExperimentalLayoutApi::class)
@Composable fun InventoryScreen(
  products: List<Product>, onRefresh: () -> Unit,
  onUpdate: (Product, Int?, Boolean?, String?) -> Unit,
  onBulkStock: (List<Long>, String) -> Unit
) {
  var q by remember { mutableStateOf("") }
  var stockFilter by remember { mutableStateOf(0) }
  var category by remember { mutableStateOf("all") }
  var confirmBulk by remember { mutableStateOf<String?>(null) } // "outofstock" | "instock"
  val ctx = LocalContext.current
  val categories = remember(products) {
    val c = LinkedHashMap<String, Int>()
    products.forEach { p -> if (p.categories.isEmpty()) c["Uncategorized"] = (c["Uncategorized"] ?: 0)+1 else p.categories.forEach { n -> c[n] = (c[n] ?: 0)+1 } }
    c.entries.sortedByDescending { it.value }.map { it.key to it.value }
  }
  val shown = remember(products, q, stockFilter, category) {
    products.filter { p ->
      (q.isBlank() || p.name.contains(q, true) || p.sku.contains(q, true)) &&
      when (stockFilter) { 1 -> p.stockStatus == "outofstock"; 2 -> p.manageStock && (p.stockQty ?: 0) in 1..5; else -> true } &&
      when (category) { "all" -> true; "Uncategorized" -> p.categories.isEmpty(); else -> p.categories.contains(category) }
    }
  }
  fun scan() = try {
    val opts = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128, Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
    GmsBarcodeScanning.getClient(ctx, opts).startScan().addOnSuccessListener { b -> b.rawValue?.let { q = it } }.addOnFailureListener {}
  } catch (_: Exception) {}

  confirmBulk?.let { target ->
    AlertDialog(
      onDismissRequest = { confirmBulk = null },
      title = { Text(if (target == "outofstock") "Mark all as out of stock?" else "Mark all as in stock?") },
      text = { Text("This will update ${shown.size} product${if (shown.size==1) "" else "s"} currently shown (filtered by search/category/stock). It saves immediately and cannot be undone in bulk.") },
      confirmButton = {
        Button({ onBulkStock(shown.map { it.id }, target); confirmBulk = null },
          colors = ButtonDefaults.buttonColors(containerColor = if (target=="outofstock") Danger else Green, contentColor = Color.White)) {
          Text(if (target=="outofstock") "Mark out of stock" else "Mark in stock")
        }
      },
      dismissButton = { TextButton({ confirmBulk = null }) { Text("Cancel") } }
    )
  }

  Column(Modifier.fillMaxSize()) {
    Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(q, { q = it }, label = { Text("Search or scan SKU") }, singleLine = true,
        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
      Spacer(Modifier.width(8.dp))
      Button({ scan() }, shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) { Icon(Icons.Default.QrCodeScanner, "Scan") }
    }
    Spacer(Modifier.height(8.dp))
    LazyRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      item { FilterPill("All", products.size, category == "all") { category = "all" } }
      items(categories, key = { it.first }) { (name, n) ->
        FilterPill(shortCat(name), n, category == name) { category = name }
      }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      listOf("All" to 0, "Out of stock" to 1, "Low stock" to 2).forEach { (l, i) ->
        val a = stockFilter == i
        Surface(color = if (a) Green else Surface, shape = RoundedCornerShape(999.dp),
          border = if (a) null else androidx.compose.foundation.BorderStroke(1.dp, Line),
          modifier = Modifier.clickable { stockFilter = i }) {
          Text(l, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (a) Color.White else InkMuted,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
      }
    }
    Spacer(Modifier.height(8.dp))
    // Bulk actions bar — acts on the currently filtered/shown products
    Surface(color = Surface, shadowElevation = 2.dp) {
      Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${shown.size} shown", fontSize = 12.sp, color = InkMuted, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        OutlinedButton({ confirmBulk = "instock" }, enabled = shown.isNotEmpty(),
          shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
          Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp)); Text("All in stock", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Button({ confirmBulk = "outofstock" }, enabled = shown.isNotEmpty(), shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = Color.White),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
          Icon(Icons.Default.RemoveShoppingCart, null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp)); Text("All out of stock", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
      }
    }
    PullRefresh(false, onRefresh) {
      if (shown.isEmpty()) EmptyState("No products match")
      else LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(shown, key = { it.id }) { ProductRow(it, onUpdate) }
        item { Spacer(Modifier.height(60.dp)) }
      }
    }
  }
}

@Composable fun ProductRow(p: Product, onUpdate: (Product, Int?, Boolean?, String?) -> Unit) {
  var qty by remember(p.id) { mutableStateOf((p.stockQty ?: 0).toString()) }
  var syncing by remember(p.id) { mutableStateOf(false) }
  val out = p.stockStatus == "outofstock"
  val low = p.manageStock && (p.stockQty ?: 0) in 1..5
  // Reset syncing when the reconciled product matches what we set
  LaunchedEffect(p.stockStatus, p.stockQty) { syncing = false }
  fun apply(qty: Int? = null, manage: Boolean? = null, status: String? = null) {
    syncing = true
    onUpdate(p, qty, manage, status)
  }
  SectionCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
      // Thumbnail with corner out-of-stock badge
      Box {
        if (p.image.isNotBlank()) {
          AsyncImage(model = p.image, contentDescription = p.name,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF1F4F2)))
        } else {
          Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEAF1EB)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Inventory2, null, tint = InkMuted)
          }
        }
        if (out) {
          Surface(color = Danger, shape = RoundedCornerShape(8.dp),
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
            Text("OUT", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp))
          }
        }
      }
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(p.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (p.categories.isNotEmpty()) Text(p.categories.joinToString(" · "), fontSize = 10.5.sp, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(money(p.price), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Green)
          if (p.salePrice != null && p.regularPrice > p.price) {
            Spacer(Modifier.width(6.dp))
            Text(money(p.regularPrice), fontSize = 11.sp, color = InkMuted, textDecoration = TextDecoration.LineThrough)
          }
          if (p.manageStock) {
            Spacer(Modifier.width(8.dp))
            val c = when { out -> Danger; low -> Warn; else -> GreenDark }
            Surface(color = if (out) DangerBg else if (low) WarnBg else OkBg, shape = RoundedCornerShape(6.dp)) {
              Text("${p.stockQty ?: 0} in stock", color = c, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
            }
          }
        }
      }
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("Track stock", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = InkMuted)
      Spacer(Modifier.width(6.dp))
      Switch(checked = p.manageStock, onCheckedChange = { v ->
        apply(if (v) (qty.toIntOrNull() ?: 0) else null, v, null)
      }, colors = SwitchDefaults.colors(checkedTrackColor = Green, checkedThumbColor = Color.White))
      Spacer(Modifier.weight(1f))
      if (syncing) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp), color = Green)
      }
      if (p.manageStock) {
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(qty, { qty = it.filter { c -> c.isDigit() }; syncing = false }, label = { Text("Qty", fontSize = 10.sp) },
          singleLine = true, modifier = Modifier.width(80.dp),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          textStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp),
          shape = RoundedCornerShape(10.dp))
        Spacer(Modifier.width(6.dp))
        Button({ val n = qty.toIntOrNull() ?: 0; apply(n, true, if (n > 0) "instock" else "outofstock") },
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), modifier = Modifier.height(46.dp)) { Text("Save", fontWeight = FontWeight.Bold) }
      }
    }
    Spacer(Modifier.height(8.dp))
    Button({
      if (p.manageStock && !out) { qty = "0"; apply(0, true, "outofstock") }
      else apply(null, null, if (out) "instock" else "outofstock")
    }, Modifier.fillMaxWidth().height(42.dp), shape = RoundedCornerShape(10.dp),
      colors = ButtonDefaults.buttonColors(
        containerColor = if (out) Green else DangerBg, contentColor = if (out) Color.White else Danger)) {
      if (syncing) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp), color = if (out) Color.White else Danger)
      else Icon(if (out) Icons.Default.CheckCircle else Icons.Default.RemoveShoppingCart, null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(6.dp))
      Text(if (out) "Mark in stock" else "Mark out of stock", fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
  }
}

@Composable fun FilterPill(label: String, count: Int, active: Boolean, onClick: () -> Unit) {
  Surface(color = if (active) Green else Surface, shape = RoundedCornerShape(999.dp),
    border = if (active) null else androidx.compose.foundation.BorderStroke(1.dp, Line),
    modifier = Modifier.clickable { onClick() }) {
    Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else Ink)
      Spacer(Modifier.width(6.dp))
      Surface(color = if (active) Color(0x33FFFFFF) else Color(0xFFF1F5F2), shape = RoundedCornerShape(999.dp)) {
        Text("$count", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = if (active) Color.White else InkMuted,
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
      }
    }
  }
}

@Composable fun EmptyState(msg: String) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(Icons.Default.Inbox, null, tint = Line, modifier = Modifier.size(48.dp))
      Spacer(Modifier.height(8.dp)); Text(msg, color = InkMuted, fontSize = 13.sp)
    }
  }
}

@Composable fun HorizontalDiviner() {
  Spacer(Modifier.height(10.dp)); Box(Modifier.fillMaxWidth().height(1.dp).background(Line)); Spacer(Modifier.height(10.dp))
}

// helper: replace list contents
fun <T> MutableList<T>.replaceAll(items: List<T>) { clear(); addAll(items) }

fun shortCat(name: String): String = when (name) {
  "Kitchen & Cooking" -> "Kitchen"
  "Fridges & Freezers" -> "Fridges"
  "Air Conditioners" -> "ACs"
  "Washers & Dryers" -> "Washers"
  "Audio & Sound" -> "Audio"
  "Fans & Coolers" -> "Fans"
  "Generators & Power" -> "Generators"
  "Water Dispensers" -> "Dispensers"
  "Solar & Inverters" -> "Solar/Inv"
  "Solar Packages" -> "Solar Pkgs"
  "Tubular Packages" -> "Tubular"
  "Lithium Packages" -> "Lithium"
  "Commercial Packages" -> "Commercial"
  "Industrial Packages" -> "Industrial"
  else -> name
}
