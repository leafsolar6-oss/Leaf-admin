package ng.leafsolar.admin

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object Export {
  fun productsCsv(ctx: Context, products: List<Product>) {
    val f = File(ctx.cacheDir, "products_${System.currentTimeMillis()}.csv")
    f.bufferedWriter().use { w ->
      w.write("ID,Name,SKU,Price,Stock,Status,Categories\n")
      products.forEach { p ->
        w.write("${p.id},\"${p.name.replace("\"","\"\"")}\",${p.sku},${p.price},${p.stockQty ?: 0},${p.stockStatus},\"${p.categories.joinToString(";")}\"\n")
      }
    }
    share(ctx, f, "text/csv")
  }
  fun ordersCsv(ctx: Context, orders: List<Order>) {
    val f = File(ctx.cacheDir, "orders_${System.currentTimeMillis()}.csv")
    f.bufferedWriter().use { w ->
      w.write("ID,Number,Customer,Date,Status,Total\n")
      orders.forEach { o ->
        w.write("${o.id},${o.number},\"${o.name.replace("\"","\"\"")}\",${o.date},${o.status},${o.total}\n")
      }
    }
    share(ctx, f, "text/csv")
  }
  private fun share(ctx: Context, f: File, mime: String) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    val i = Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    ctx.startActivity(Intent.createChooser(i, "Share CSV"))
  }
}
