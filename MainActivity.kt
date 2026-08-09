package ng.leafsolar.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

val Green = Color(0xFF3CA506)
val Lime = Color(0xFFA6F25A)
val Dark = Color(0xFF0A0D0B)
val LightBg = Color(0xFFF6FAF3)

data class Order(val id: Long, val number: String, val name: String, val phone: String,
                val email: String, val items: List<String>, val total: Double,
                val status: String, val date: String)
data class Product(val id: Long, val name: String, val sku: String, val price: Double,
                   val manageStock: Boolean, val stockQty: Int?, val stockStatus: String,
                   val type: String)

object Api {
  private val client = OkHttpClient()
  var base = "https://leafsolar.ng/wp-json/wc/v3/"
  var auth: String = ""
  fun basic(user:String,pass:String)=okhttp3.Credentials.basic(user,pass)
  fun setAuth(u:String,p:String){auth=basic(u.trim(),p.trim())}
  private fun exec(path: String, method: String = "GET", bodyJson: String? = null): String {
    val b = Request.Builder().url(base + path).header("Authorization", auth)
    if (bodyJson != null) b.method(method, bodyJson.toRequestBody("application/json".toMediaTypeOrNull()!!))
    else b.method(method, null)
    client.newCall(b.build()).execute().use { r ->
      if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
      return r.body?.string() ?: ""
    }
  }
  suspend fun orders(): List<Order> = withContext(Dispatchers.IO) {
    val arr = JSONArray(exec("orders?per_page=100&status=pending,processing,on-hold,completed,cancelled,refunded,failed"))
    (0 until arr.length()).map { i ->
      val o = arr.getJSONObject(i); val b = o.optJSONObject("billing") ?: JSONObject()
      val items = mutableListOf<String>()
      val li = o.optJSONArray("line_items") ?: JSONArray()
      for (j in 0 until li.length()) items.add(li.getJSONObject(j).let { it.optString("name") + " x" + it.optInt("quantity") })
      Order(o.getLong("id"), o.getString("number"),
        (b.optString("first_name") + " " + b.optString("last_name")).trim(),
        b.optString("phone"), b.optString("email"), items,
        o.optDouble("total", 0.0), o.optString("status", "pending"), o.optString("date_created_gmt", ""))
    }.sortedByDescending { it.id }
  }
  suspend fun products(): List<Product> = withContext(Dispatchers.IO) {
    val all = mutableListOf<Product>(); var page = 1
    while (true) {
      val arr = JSONArray(exec("products?per_page=100&page=$page&status=publish"))
      if (arr.length() == 0) break
      for (i in 0 until arr.length()) {
        val p = arr.getJSONObject(i)
        all.add(Product(p.getLong("id"), p.getString("name"), p.optString("sku"),
          p.optDouble("price", 0.0), p.optBoolean("manage_stock"),
          if (p.isNull("stock_quantity")) null else p.optInt("stock_quantity"),
          p.optString("stock_status", "instock"), p.optString("type", "simple")))
      }
      if (arr.length() < 100) break; page++
    }
    all
  }
  // qty = null means don't change quantity; manage = null means don't change tracking; status = null means don't change status
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
    setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Green)) { App(this) } }
  }
}

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun App(act: ComponentActivity) {
  val prefs = act.getSharedPreferences("leaf-admin", 0)
  var user by remember { mutableStateOf<String?>(null) }
  var pass by remember { mutableStateOf<String?>(null) }
  var logged by remember { mutableStateOf(false) }
  var err by remember { mutableStateOf<String?>(null) }
  var loading by remember { mutableStateOf(false) }
  var tab by remember { mutableStateOf(0) }
  var orders by remember { mutableStateOf<List<Order>>(emptyList()) }
  var products by remember { mutableStateOf<List<Product>>(emptyList()) }
  val scope = rememberCoroutineScope()

  LaunchedEffect(Unit) {
    val u = prefs.getString("u", null); val p = prefs.getString("p", null)
    if (u != null && p != null) { Api.setAuth(u, p); user = u; pass = p; logged = true }
  }
  LaunchedEffect(logged) {
    if (logged) { try { orders = Api.orders(); products = Api.products() } catch (_: Exception) {} }
  }

  if (!logged) {
    Column(Modifier.fillMaxSize().background(Dark).padding(24.dp), verticalArrangement = Arrangement.Center) {
      Text("Leaf Admin", color = Lime, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
      Spacer(Modifier.height(6.dp)); Text("Inventory & orders", color = Color(0xFFB9C7BD), fontSize = 15.sp)
      Spacer(Modifier.height(28.dp))
      OutlinedTextField(user ?: "", { user = it; err = null }, label = { Text("Username or email") },
        modifier = Modifier.fillMaxWidth(), colors = fieldColors())
      Spacer(Modifier.height(12.dp))
      OutlinedTextField(pass ?: "", { pass = it; err = null }, label = { Text("Application password") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
        modifier = Modifier.fillMaxWidth(), colors = fieldColors())
      err?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp) }
      Spacer(Modifier.height(18.dp))
      Button({
        if (user.isNullOrBlank() || pass.isNullOrBlank()) { err = "Enter username and application password"; return@Button }
        loading = true; err = null
        Thread {
          try { Api.setAuth(user!!, pass!!); runBlocking { Api.orders() }
            prefs.edit().putString("u", user).putString("p", pass).apply(); logged = true
          } catch (e: Exception) { err = "Login failed: ${e.message}" }
          loading = false
        }.start()
      }, Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black),
        shape = RoundedCornerShape(12.dp), enabled = !loading) {
        Text(if (loading) "Signing in…" else "Sign in", fontWeight = FontWeight.Bold, fontSize = 16.sp)
      }
      Spacer(Modifier.height(14.dp)); Text("Stored only on this device.", color = Color(0xFF8AA092), fontSize = 12.sp)
    }
  } else {
    Scaffold(containerColor = LightBg, topBar = {
      Surface(color = Dark) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Leaf Admin", color = Lime, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text("Logout", color = Color(0xFF8AA092), fontSize = 13.sp, modifier = Modifier.clickable {
              prefs.edit().clear().apply(); logged = false
            })
          }
          Spacer(Modifier.height(10.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Dashboard" to 0, "Orders" to 1, "Inventory" to 2).forEach { (t, i) ->
              Surface(color = if (tab == i) Green else Color(0xFF1C2A20), shape = RoundedCornerShape(10.dp),
                modifier = Modifier.clickable { tab = i }) {
                Text(t, color = if (tab == i) Color.Black else Color.White, fontWeight = FontWeight.Bold,
                  fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
              }
            }
          }
        }
      }
    }) { pad ->
      Box(Modifier.padding(pad)) {
        when (tab) {
          0 -> Dashboard(orders, products)
          1 -> Orders(orders) { o, s -> scope.launch { try { Api.setStatus(o.id, s); orders = Api.orders() } catch (_: Exception) {} } }
          else -> Inventory(act, products, onUpdate = { p, qty, manage, status ->
            scope.launch {
              try {
                Api.updateProduct(p.id, qty, manage, status)
                products = Api.products()
              } catch (_: Exception) {}
            }
          })
        }
      }
    }
  }
}

@Composable fun fieldColors() = OutlinedTextFieldDefaults.colors(
  focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
  cursorColor = Lime, focusedBorderColor = Lime, unfocusedBorderColor = Color(0xFF34493A),
  focusedLabelColor = Lime, unfocusedLabelColor = Color(0xFF8AA092),
  focusedTextColor = Color.White, unfocusedTextColor = Color.White)

@Composable fun Dashboard(orders: List<Order>, products: List<Product>) {
  val rev = orders.filter { it.status in listOf("processing", "completed") }.sumOf { it.total }
  val pend = orders.count { it.status == "pending" || it.status == "on-hold" }
  val done = orders.count { it.status == "completed" }
  val low = products.count { it.stockStatus == "outofstock" || (it.manageStock && ((it.stockQty ?: 0) <= 5)) }
  val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
  val tod = orders.count { it.date.startsWith(today) }
  Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Stat("Today's orders", tod.toString())
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
      Mini("Revenue", "₦%,d".format(rev.toLong()), Modifier.weight(1f)); Mini("Pending", pend.toString(), Modifier.weight(1f)) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
      Mini("Completed", done.toString(), Modifier.weight(1f)); Mini("Low stock", low.toString(), Modifier.weight(1f)) }
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
      Column(Modifier.padding(14.dp)) {
        Text("Recent orders", fontWeight = FontWeight.Bold, fontSize = 15.sp); Spacer(Modifier.height(8.dp))
        orders.take(5).forEach { o ->
          Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("#${o.number}", fontWeight = FontWeight.Bold, fontSize = 13.5.sp); Text(o.name, fontSize = 12.sp, color = Color(0xFF5b6b61)) }
            Text("₦%,d".format(o.total.toLong()), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Green); Spacer(Modifier.width(8.dp)); Pill(o.status)
          }
        }
      }
    }
  }
}
@Composable fun Stat(l: String, v: String) { Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(16.dp)) { Text(v, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Green); Text(l, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5b6b61)) } } }
@Composable fun Mini(l: String, v: String, m: Modifier) { Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = m) { Column(Modifier.padding(14.dp)) { Text(v, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Green); Text(l, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5b6b61)) } } }

@Composable fun Orders(orders: List<Order>, onStatus: (Order, String) -> Unit) {
  LazyColumn(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(orders, key = { it.id }) { OrderCard(it, onStatus) } }
}
@Composable fun OrderCard(o: Order, onStatus: (Order, String) -> Unit) {
  var exp by remember { mutableStateOf(false) }
  Surface(color = Color.White, shape = RoundedCornerShape(14.dp), shadowElevation = 2.dp) {
    Column(Modifier.padding(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("#${o.number}", fontWeight = FontWeight.Bold, fontSize = 16.sp); Text(o.name, fontSize = 13.sp, color = Color(0xFF425347)); if (o.phone.isNotEmpty()) Text(o.phone, fontSize = 12.sp, color = Color(0xFF6E7D72)) }
        Pill(o.status)
      }
      if (exp) {
        Spacer(Modifier.height(10.dp)); o.items.forEach { Text("• $it", fontSize = 13.sp, color = Color(0xFF334038)) }
        Spacer(Modifier.height(8.dp)); Text(o.email, fontSize = 12.sp, color = Color(0xFF6E7D72))
        Spacer(Modifier.height(10.dp)); Text("Change status", fontSize = 12.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("pending", "on-hold", "processing", "completed", "cancelled").forEach { s ->
          Surface(color = if (s == o.status) Green else Color(0xFFEFF4EC), shape = RoundedCornerShape(8.dp), modifier = Modifier.clickable { onStatus(o, s) }) {
            Text(s.take(4).replaceFirstChar { it.uppercase() }, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (s == o.status) Color.Black else Color(0xFF334038), modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) } } } }
      }
      Spacer(Modifier.height(10.dp)); Row(verticalAlignment = Alignment.CenterVertically) {
        Text("₦%,d".format(o.total.toLong()), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Green, modifier = Modifier.weight(1f))
        Text(if (exp) "Hide" else "Details", fontSize = 12.5.sp, color = Green, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { exp = !exp }) }
    }
  }

@Composable fun Pill(s: String) {
  val bg = when (s) { "completed" -> Color(0xFFE4F7D6); "processing" -> Color(0xFFE3F0FF); "pending", "on-hold" -> Color(0xFFFFF4D6); else -> Color(0xFFFBE0DD) }
  val fg = when (s) { "completed" -> Color(0xFF2F7A05); "processing" -> Color(0xFF0B4FA0); "pending", "on-hold" -> Color(0xFF8A6300); else -> Color(0xFF9B1C17) }
  Surface(color = bg, shape = RoundedCornerShape(999.dp)) { Text(s.uppercase(), color = fg, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) }
}

@Composable fun Inventory(act: ComponentActivity, products: List<Product>, onUpdate: (Product, Int?, Boolean?, String?) -> Unit) {
  var q by remember { mutableStateOf("") }
  var filter by remember { mutableStateOf(0) } // 0=all, 1=out, 2=low
  val ctx = LocalContext.current
  val shown = remember(products, q, filter) {
    products.filter { p ->
      (q.isBlank() || p.name.contains(q, true) || p.sku.contains(q, true)) &&
      when (filter) {
        1 -> p.stockStatus == "outofstock"
        2 -> p.manageStock && (p.stockQty ?: 0) in 1..5
        else -> true
      }
    }
  }
  fun scan() { try {
    val opts = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128, Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
    GmsBarcodeScanning.getClient(ctx, opts).startScan().addOnSuccessListener { b -> b.rawValue?.let { q = it } }.addOnFailureListener {}
  } catch (_: Exception) {} }
  Column(Modifier.padding(12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(q, { q = it }, label = { Text("Search name or SKU") }, modifier = Modifier.weight(1f), singleLine = true,
        shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(cursorColor = Green, focusedBorderColor = Green, unfocusedBorderColor = Color(0xFF34493A), focusedLabelColor = Lime))
      Spacer(Modifier.width(8.dp))
      Button({ scan() }, colors = ButtonDefaults.buttonColors(containerColor = Dark, contentColor = Lime),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp), shape = RoundedCornerShape(12.dp)) { Text("Scan", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      listOf("All" to 0, "Out of stock" to 1, "Low stock" to 2).forEach { (label, i) ->
        val active = filter == i
        Surface(color = if (active) Green else Color.White, shape = RoundedCornerShape(999.dp),
          border = if (active) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD8E3D2)),
          modifier = Modifier.clickable { filter = i }) {
          Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (active) Color.Black else Color(0xFF334038),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
        }
      }
    }
    Spacer(Modifier.height(4.dp))
    Text("${products.count { it.manageStock }} of ${products.size} tracking stock  •  ${products.count { it.stockStatus == "outofstock" }} out of stock",
      fontSize = 11.sp, color = Color(0xFF6E7D72))
    Spacer(Modifier.height(8.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(shown, key = { it.id }) { ProductRow(it, onUpdate) } }
  }
}

@Composable fun ProductRow(p: Product, onUpdate: (Product, Int?, Boolean?, String?) -> Unit) {
  var qty by remember(p.id) { mutableStateOf((p.stockQty ?: 0).toString()) }
  var busy by remember(p.id) { mutableStateOf(false) }
  val outOfStock = p.stockStatus == "outofstock"
  val low = p.manageStock && (p.stockQty ?: 0) in 1..5
  val statusColor = when {
    outOfStock -> Color(0xFF9B1C17)
    low -> Color(0xFFB97300)
    else -> Color(0xFF2F7A05)
  }
  val statusLabel = when {
    outOfStock -> "OUT OF STOCK"
    !p.manageStock -> "IN STOCK (not tracked)"
    low -> "LOW STOCK"
    else -> "IN STOCK"
  }
  Surface(color = Color.White, shape = RoundedCornerShape(12.dp)) {
    Column(Modifier.padding(12.dp)) {
      Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
          Text(p.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2)
          Spacer(Modifier.height(3.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("₦%,d".format(p.price.toLong()), fontSize = 12.5.sp, color = Green, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            if (p.sku.isNotBlank()) Text("SKU: ${p.sku}", fontSize = 11.sp, color = Color(0xFF6E7D72))
          }
        }
        Spacer(Modifier.width(8.dp))
        Surface(color = when { outOfStock -> Color(0xFFFBE0DD); low -> Color(0xFFFFF4D6); else -> Color(0xFFE4F7D6) },
          shape = RoundedCornerShape(999.dp)) {
          Text(statusLabel, color = statusColor, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
        }
      }
      Spacer(Modifier.height(10.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        // Track stock toggle
        Text("Track", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334038))
        Spacer(Modifier.width(6.dp))
        Switch(checked = p.manageStock, onCheckedChange = { v ->
          // When enabling tracking with no qty, default to current field value
          val newQty = if (v) (qty.toIntOrNull() ?: 0) else null
          busy = true
          onUpdate(p, newQty, v, null)
        }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Green))
        Spacer(Modifier.width(10.dp))
        if (p.manageStock) {
          OutlinedTextField(qty, { qty = it.filter { c -> c.isDigit() } },
            label = { Text("Qty", fontSize = 11.sp) }, modifier = Modifier.width(82.dp), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            shape = RoundedCornerShape(9.dp))
          Spacer(Modifier.width(6.dp))
          Button({
            val n = qty.toIntOrNull() ?: 0
            busy = true
            // Saving a positive qty also marks in stock automatically
            onUpdate(p, n, true, if (n > 0) "instock" else "outofstock")
          }, modifier = Modifier.height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(9.dp), enabled = !busy) {
            Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
      Spacer(Modifier.height(8.dp))
      // In stock / Out of stock quick toggle
      Row(verticalAlignment = Alignment.CenterVertically) {
        Button({
          busy = true
          // Marking out of stock with tracking on sets qty to 0 too
          if (p.manageStock && outOfStock.not()) { qty = "0"; onUpdate(p, 0, true, "outofstock") }
          else onUpdate(p, null, null, if (outOfStock) "instock" else "outofstock")
        }, modifier = Modifier.weight(1f).height(42.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (outOfStock) Green else Color(0xFFFBE0DD),
            contentColor = if (outOfStock) Color.Black else Color(0xFF9B1C17)),
          shape = RoundedCornerShape(9.dp), enabled = !busy) {
          Text(if (outOfStock) "✓ Mark in stock" else "Mark out of stock", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}
