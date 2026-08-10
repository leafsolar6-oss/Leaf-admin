package ng.leafsolar.admin

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockInScreen(products: List<Product>, onAdjust: (Product, Int) -> Unit, onBack: () -> Unit) {
  val ctx = LocalContext.current; val scope = rememberCoroutineScope()
  var sku by remember { mutableStateOf("") }; var qty by remember { mutableStateOf("1") }
  var found by remember { mutableStateOf<Product?>(null) }; var msg by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
  fun search(s: String) { if (s.isBlank()) return; busy=true; msg=null; scope.launch(Dispatchers.IO){ val p=Api.findBySku(products,s); withContext(Dispatchers.Main){ found=p; busy=false; msg=if(p==null)"No product for \"$s\"" else null } } }
  fun scan()=try{ val opts=com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13 or com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_8 or com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_A or com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_128 or com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE
    com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder().setBarcodeFormats(opts).enableAutoZoom().build().let{ com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(ctx,it).startScan().addOnSuccessListener{b->b.rawValue?.let{sku=it;search(it)}} } }catch(_:Exception){}
  Scaffold(topBar={Surface(color=GreenDark){Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null,tint=Color.White)};Text("Stock in (scan)",color=Color.White,fontWeight=FontWeight.ExtraBold,fontSize=17.sp)}}}){pad->
    Column(Modifier.padding(pad).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
      OutlinedTextField(sku,{sku=it},label={Text("Scan or type SKU")},singleLine=true,modifier=Modifier.fillMaxWidth())
      Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({scan()},shape=RoundedCornerShape(10.dp),modifier=Modifier.weight(1f),contentPadding=PaddingValues(vertical=12.dp)){Icon(Icons.Default.QrCodeScanner,null,modifier=Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("SCAN")};OutlinedButton({search(sku)},shape=RoundedCornerShape(10.dp),modifier=Modifier.weight(1f),contentPadding=PaddingValues(vertical=12.dp),enabled=!busy){Text(if(busy)"…"else "FIND")}}
      found?.let{p->Surface(shape=RoundedCornerShape(14.dp),color=Surface,tonalElevation=2.dp,shadowElevation=2.dp){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(p.name,fontWeight=FontWeight.ExtraBold,fontSize=15.sp);Text("SKU: ${p.sku.ifBlank { "—" }} · Current: ${p.stockQty?:0}",color=InkMuted,fontSize=12.sp)
        OutlinedTextField(qty,{qty=it.filter{c->c.isDigit()}},label={Text("Quantity to add")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({onAdjust(p,qty.toIntOrNull()?:1);msg="Added $qty to ${p.name}";found=null;qty="1"},shape=RoundedCornerShape(10.dp),modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=Green)){Text("ADD STOCK")};OutlinedButton({found=null},shape=RoundedCornerShape(10.dp)){Text("CANCEL")}}
      }}}
      msg?.let{Surface(color=WarnBg,shape=RoundedCornerShape(10.dp)){Text(it,color=Warn,fontSize=12.sp,modifier=Modifier.padding(10.dp))}}
    }}
}

@Composable
fun ReorderScreen(products: List<Product>, onBack: () -> Unit) {
  val low=products.filter{it.reorderPoint!=null && (it.stockQty?:0)<=(it.reorderPoint?:0) && it.manageStock}.sortedBy{it.stockQty?:0}
  val out=products.count{it.stockStatus=="outofstock"}
  Scaffold(topBar={Surface(color=GreenDark){Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null,tint=Color.White)};Text("Low stock & reorder",color=Color.White,fontWeight=FontWeight.ExtraBold,fontSize=17.sp)}}}){pad->
    LazyColumn(Modifier.padding(pad).fillMaxSize(),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
      item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){MiniStat("Reorder",low.size,Warn,Modifier.weight(1f));MiniStat("Out of stock",out,Danger,Modifier.weight(1f))}}
      if(low.isEmpty())item{EmptyState("Nothing below reorder point. Set reorder points on products.")}
      items(low,key={it.id}){p->Surface(shape=RoundedCornerShape(14.dp),color=Surface,tonalElevation=1.dp,shadowElevation=1.dp){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(p.name,fontWeight=FontWeight.Bold,fontSize=14.sp,maxLines=2);Text("${p.stockQty?:0} left · reorder at ${p.reorderPoint}",color=Warn,fontSize=11.sp,fontWeight=FontWeight.Bold)};Surface(color=WarnBg,shape=CircleShape){Text("ORDER",color=Warn,fontSize=9.sp,fontWeight=FontWeight.ExtraBold,modifier=Modifier.padding(horizontal=8.dp,vertical=4.dp))}}}}
    }}
}
@Composable private fun MiniStat(label:String,value:Int,accent:Color,m:Modifier){Surface(shape=RoundedCornerShape(14.dp),color=Surface,tonalElevation=2.dp,shadowElevation=2.dp,modifier=m){Column(Modifier.padding(14.dp)){Text("$value",fontWeight=FontWeight.ExtraBold,fontSize=24.sp,color=accent);Text(label,color=InkMuted,fontSize=11.sp)}}}
@Composable fun EmptyStateMsg(msg:String){Surface(shape=RoundedCornerShape(14.dp),color=Surface,border=androidx.compose.foundation.BorderStroke(1.dp,Line)){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Inventory2,null,tint=InkMuted);Spacer(Modifier.width(10.dp));Text(msg,color=InkMuted,fontSize=13.sp)}}}
