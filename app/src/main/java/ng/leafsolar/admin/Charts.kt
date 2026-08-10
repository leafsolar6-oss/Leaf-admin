package ng.leafsolar.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Locale

// Simple bar chart of revenue per day for the last N days
@Composable
fun RevenueChart(orders: List<Order>, days: Int = 7) {
  val today = SimpleDateFormat("yyyy-MM-dd", Locale.US)
  val paid = orders.filter { it.status in listOf("processing","completed","on-hold") }
  val buckets = (days-1 downTo 0).map { back ->
    val key = today.format(java.util.Date(System.currentTimeMillis() - back*86400_000L))
    key to paid.filter { it.date.startsWith(key) }.sumOf { it.total }
  }
  val max = (buckets.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)
  Surface(shape = RoundedCornerShape(18.dp), color = Surface, tonalElevation = 2.dp, shadowElevation = 2.dp) {
    Column(Modifier.padding(14.dp)) {
      Text("Revenue · last $days days", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Ink)
      Spacer(Modifier.height(10.dp))
      Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().height(110.dp)) {
        buckets.forEach { (label, value) ->
          val h = ((value / max) * 90).toInt().coerceAtLeast(if (value > 0) 6 else 2)
          Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom, modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (value > 0) Text(money(value).replace(",000","k"), fontSize = 7.sp, color = InkMuted, maxLines = 1)
            Box(Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(Green))
            Spacer(Modifier.height(2.dp))
            Text(label.substring(8), fontSize = 7.sp, color = InkMuted)
          }
        }
      }
    }
  }
}

@Composable
fun OrdersStatusChart(orders: List<Order>) {
  val groups = listOf("pending" to { o: Order -> o.status == "pending" || o.status == "on-hold" },
    "processing" to { o: Order -> o.status == "processing" },
    "done" to { o: Order -> o.status == "completed" },
    "cancelled" to { o: Order -> o.status in listOf("cancelled","failed","refunded") })
  Surface(shape = RoundedCornerShape(18.dp), color = Surface, tonalElevation = 2.dp, shadowElevation = 2.dp) {
    Column(Modifier.padding(14.dp)) {
      Text("Orders by status", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Ink)
      Spacer(Modifier.height(8.dp))
      groups.forEach { (label, pred) ->
        val n = orders.count { pred(it) }
        val max = orders.size.coerceAtLeast(1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 3.dp)) {
          Text(label.replaceFirstChar { it.uppercase() }, fontSize = 11.sp, color = InkMuted, modifier = Modifier.width(72.dp))
          Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(99.dp)).background(SurfaceAlt)) {
            Box(Modifier.fillMaxWidth(n.toFloat()/max).height(10.dp).clip(RoundedCornerShape(99.dp)).background(
              when(label) { "pending" -> Warn; "processing" -> Info; "done" -> Green; else -> Danger }
            ))
          }
          Text("$n", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ink)
        }
      }
    }
  }
}
