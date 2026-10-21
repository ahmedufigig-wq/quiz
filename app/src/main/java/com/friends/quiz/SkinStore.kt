package com.friends.quiz

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

// ───────────── البيانات ─────────────
data class Skin(val id: String, val ar: String, val en: String, val tier: String, val price: Int)

val ALL_SKINS = listOf(
    Skin("classic", "كلاسيكي", "Classic", "مجاني", 0),
    Skin("mint", "نعناع", "Mint", "رخيص", 25),
    Skin("sky", "سماء", "Sky", "رخيص", 40),
    Skin("ocean", "محيط", "Ocean", "رخيص", 50),
    Skin("rose", "وردة", "Rose", "رخيص", 60),
    Skin("sand", "صحراء", "Desert", "رخيص", 80),
    Skin("sunset", "غروب", "Sunset", "متوسط", 120),
    Skin("snow", "ثلج", "Snow", "متوسط", 150),
    Skin("royal", "ملكي", "Royal", "متوسط", 200),
    Skin("sakura", "ساكورا", "Sakura", "متوسط", 250),
    Skin("fire", "نار", "Fire", "متوسط", 350),
    Skin("galaxy", "مجرّة", "Galaxy", "فاخر", 450),
    Skin("gold", "ذهبي", "Gold", "فاخر", 600),
    Skin("diamond", "ماسي", "Diamond", "فاخر", 1000),
    Skin("legend", "أسطوري", "Legendary", "فاخر", 2000),
)

// ───────────── الرصيد والجوائز ─────────────
object Wallet {
    private fun p(c: Context) = c.getSharedPreferences("skin_wallet", Context.MODE_PRIVATE)
    fun diamonds(c: Context) = p(c).getInt("d", 50)
    fun add(c: Context, n: Int) { p(c).edit().putInt("d", diamonds(c) + n).apply() }
    fun owned(c: Context): Set<String> = (p(c).getStringSet("owned", setOf("classic")) ?: setOf("classic")).toSet()
    fun selected(c: Context): String = p(c).getString("sel", "classic") ?: "classic"
    fun select(c: Context, id: String) { p(c).edit().putString("sel", id).apply() }
    fun buy(c: Context, s: Skin): Boolean {
        if (diamonds(c) < s.price) return false
        p(c).edit().putInt("d", diamonds(c) - s.price)
            .putStringSet("owned", owned(c) + s.id).putString("sel", s.id).apply()
        return true
    }
}

fun rewardForRank(rank: Int) = when (rank) { 1 -> 30; 2 -> 20; 3 -> 10; else -> 5 }

/** استدعها مرة واحدة عند النتيجة النهائية، وترجع عدد الماسات المكتسبة */
fun awardRank(c: Context, rank: Int): Int { val r = rewardForRank(rank); Wallet.add(c, r); return r }

// ───────────── الرسم ─────────────
private val GOLD = Color(0xFFD4AF37)

private fun DrawScope.bg(vararg c: Color) = drawRect(Brush.verticalGradient(c.toList()))

private fun DrawScope.frame(c: Color = GOLD) {
    val a = 4.dp.toPx(); val b = 9.dp.toPx()
    drawRect(c, Offset(a, a), Size(size.width - 2 * a, size.height - 2 * a), style = Stroke(2.dp.toPx()))
    drawRect(c.copy(alpha = .5f), Offset(b, b), Size(size.width - 2 * b, size.height - 2 * b), style = Stroke(1.dp.toPx()))
}

private fun DrawScope.stars(n: Int, seed: Int, c: Color = Color.White, maxY: Float = 1f) {
    val r = Random(seed)
    repeat(n) {
        drawCircle(c.copy(alpha = .4f + r.nextFloat() * .6f), r.nextFloat() * 1.8f.dp.toPx() + .6f,
            Offset(r.nextFloat() * size.width, r.nextFloat() * size.height * maxY))
    }
}

private fun DrawScope.cloud(cx: Float, cy: Float, s: Float, c: Color) {
    listOf(-1f to 0f, -.5f to -.4f, .1f to -.55f, .6f to -.2f, 1.1f to 0f, 0f to .1f).forEach { (dx, dy) ->
        drawCircle(c, s * .5f, Offset(cx + dx * s, cy + dy * s))
    }
}

private fun DrawScope.ridge(color: Color, baseY: Float, amp: Float, seed: Int) {
    val r = Random(seed); val p = Path(); p.moveTo(0f, size.height)
    var x = 0f
    while (x <= size.width + 40f) { p.lineTo(x, baseY - r.nextFloat() * amp); x += size.width / 7f }
    p.lineTo(size.width, size.height); p.close(); drawPath(p, color)
}

private fun DrawScope.wave(color: Color, y: Float, amp: Float, ph: Float) {
    val p = Path(); p.moveTo(0f, size.height)
    var x = 0f
    while (x <= size.width) { p.lineTo(x, y + amp * sin(x / size.width * 6f * PI.toFloat() + ph)); x += 6f }
    p.lineTo(size.width, size.height); p.close(); drawPath(p, color)
}

private fun DrawScope.leaf(x: Float, y: Float, l: Float, ang: Float, c: Color) = rotate(ang, Offset(x, y)) {
    val p = Path(); p.moveTo(x, y); p.quadraticBezierTo(x + l * .5f, y - l * .35f, x + l, y)
    p.quadraticBezierTo(x + l * .5f, y + l * .35f, x, y); drawPath(p, c)
}

private fun DrawScope.flower(c: Offset, r: Float, petal: Color, core: Color, n: Int = 8) {
    repeat(n) { i -> rotate(i * 360f / n, c) {
        drawOval(petal, Offset(c.x - r * .26f, c.y - r), Size(r * .52f, r * .95f))
        drawOval(core.copy(alpha = .35f), Offset(c.x - r * .26f, c.y - r), Size(r * .52f, r * .95f), style = Stroke(1.dp.toPx()))
    } }
    drawCircle(core, r * .3f, c)
    drawCircle(GOLD, r * .3f, c, style = Stroke(1.5.dp.toPx()))
}

private fun DrawScope.crown(c: Offset, w: Float, col: Color) {
    val h = w * .55f; val p = Path()
    p.moveTo(c.x - w / 2, c.y + h / 2); p.lineTo(c.x - w / 2, c.y - h / 4); p.lineTo(c.x - w / 4, c.y + h / 8)
    p.lineTo(c.x, c.y - h / 2); p.lineTo(c.x + w / 4, c.y + h / 8); p.lineTo(c.x + w / 2, c.y - h / 4)
    p.lineTo(c.x + w / 2, c.y + h / 2); p.close()
    drawPath(p, col); drawPath(p, Color(0xFF7A5A00), style = Stroke(1.5.dp.toPx()))
    listOf(-.5f, 0f, .5f).forEach { drawCircle(Color(0xFFB0306C), w * .05f, Offset(c.x + it * w * .8f, c.y + h * .25f)) }
}

private fun DrawScope.flame(x: Float, w: Float, top: Float, c: Color) {
    val h = size.height; val p = Path()
    p.moveTo(x - w, h); p.quadraticBezierTo(x - w * .2f, h * .55f, x, top)
    p.quadraticBezierTo(x + w * .3f, h * .55f, x + w, h); p.close(); drawPath(p, c)
}

private fun DrawScope.gem(c: Offset, r: Float, col: Color) {
    val p = Path(); p.moveTo(c.x - r, c.y - r * .3f); p.lineTo(c.x - r * .5f, c.y - r * .8f)
    p.lineTo(c.x + r * .5f, c.y - r * .8f); p.lineTo(c.x + r, c.y - r * .3f)
    p.lineTo(c.x, c.y + r); p.close()
    drawPath(p, col); drawPath(p, Color.White.copy(alpha = .8f), style = Stroke(1.2.dp.toPx()))
    drawLine(Color.White.copy(alpha = .6f), Offset(c.x - r, c.y - r * .3f), Offset(c.x + r, c.y - r * .3f), 1.dp.toPx())
}

@Composable
fun SkinArt(id: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        when (id) {
            "classic" -> { bg(Color(0xFF3A3A4A), Color(0xFF22222E)); frame(Color(0xFF9A9AB0)) }
            "mint" -> {
                bg(Color(0xFF2E8B67), Color(0xFF1B5E47))
                val r = Random(3)
                repeat(70) { drawCircle(GOLD.copy(alpha = .6f), 1.2.dp.toPx(), Offset(r.nextFloat() * w * .7f, h * (.25f + r.nextFloat() * .5f))) }
                val v = Path(); v.moveTo(0f, h * .8f); v.cubicTo(w * .2f, h * .1f, w * .35f, h * .95f, w * .6f, h * .3f)
                drawPath(v, Color(0xFF7FD6A6), style = Stroke(2.dp.toPx()))
                repeat(7) { i -> leaf(w * (.1f + i * .1f), h * (.3f + (i % 3) * .15f), 26.dp.toPx(), -50f + i * 25f, Color(0xFF8ED9AE).copy(alpha = .8f)) }
                leaf(w * .78f, h * .55f, 70.dp.toPx(), -70f, Color(0xFF4FAF7F)); leaf(w * .78f, h * .55f, 70.dp.toPx(), -110f, Color(0xFF63C892))
                frame()
            }
            "sky" -> {
                bg(Color(0xFF0E1236), Color(0xFF2B1F5C)); stars(70, 5, maxY = .8f)
                drawCircle(Color(0xFF7B52AB).copy(alpha = .3f), h * .8f, Offset(w * .35f, h * .4f))
                cloud(w * .22f, h * .5f, 38.dp.toPx(), Color(0xFFBFD4F2)); cloud(w * .5f, h * .6f, 46.dp.toPx(), Color(0xFFDDE8FA)); cloud(w * .65f, h * .75f, 30.dp.toPx(), Color(0xFF9DB6E0))
                val c = Offset(w * .88f, h * .35f)
                drawCircle(GOLD, 22.dp.toPx(), c); drawCircle(Color(0xFF14194A), 19.dp.toPx(), c + Offset(8.dp.toPx(), -3.dp.toPx()))
                frame()
            }
            "ocean" -> {
                bg(Color(0xFF5CC3E8), Color(0xFF1B6FA8)); drawCircle(Color(0xFFFFF3B0), 22.dp.toPx(), Offset(w * .8f, h * .3f))
                wave(Color(0xFF2E8FC4), h * .55f, 8.dp.toPx(), 0f); wave(Color(0xFF1F77B0), h * .7f, 10.dp.toPx(), 2f); wave(Color(0xFF0F4C85), h * .85f, 8.dp.toPx(), 4f)
                frame(Color(0xFFE8F6FF))
            }
            "rose" -> {
                bg(Color(0xFFC0707F), Color(0xFF9A4A62))
                val r = Random(9); repeat(30) { drawCircle(GOLD.copy(alpha = .5f), 2.dp.toPx(), Offset(r.nextFloat() * w, r.nextFloat() * h)) }
                val c = Offset(w * .5f, h * .5f)
                leaf(c.x, c.y, 60.dp.toPx(), 25f, Color(0xFF5E8F5A)); leaf(c.x, c.y, 60.dp.toPx(), 155f, Color(0xFF5E8F5A))
                flower(c, 34.dp.toPx(), Color(0xFFD94F7E), Color(0xFF8A1F4B), 10)
                drawCircle(Color(0xFFB02F63), 14.dp.toPx(), c, style = Stroke(2.dp.toPx())); frame()
            }
            "sand" -> {
                bg(Color(0xFFF6C77A), Color(0xFFE08A3A)); drawCircle(Color(0xFFFFF0B8), 20.dp.toPx(), Offset(w * .78f, h * .3f))
                listOf(Triple(.55f, 0xFFD9964A, 0f), Triple(.7f, 0xFFC27A35, 2f), Triple(.85f, 0xFFA45F28, 4f)).forEach { (y, col, ph) -> wave(Color(col), h * y, 9.dp.toPx(), ph) }
                frame(Color(0xFF7A4A12))
            }
            "sunset" -> {
                bg(Color(0xFFFFC857), Color(0xFFE2572B)); drawCircle(Color(0xFFFFE9A0), 36.dp.toPx(), Offset(w * .5f, h * .45f))
                ridge(Color(0xFFB8532F), h * .65f, h * .35f, 1); ridge(Color(0xFF8A3A3A), h * .8f, h * .3f, 2); ridge(Color(0xFF4A2250), h * .95f, h * .25f, 3)
                frame()
            }
            "snow" -> {
                bg(Color(0xFFCFE7FA), Color(0xFF7FA8D6)); val r = Random(7)
                repeat(40) { drawCircle(Color.White, r.nextFloat() * 2.5f.dp.toPx() + 1f, Offset(r.nextFloat() * w, r.nextFloat() * h)) }
                ridge(Color(0xFFEFF6FF), h * .8f, h * .35f, 11)
                val c = Offset(w * .5f, h * .4f)
                repeat(3) { i -> rotate(i * 60f, c) { drawLine(Color.White, Offset(c.x, c.y - 26.dp.toPx()), Offset(c.x, c.y + 26.dp.toPx()), 3.dp.toPx()) } }
                frame(Color.White)
            }
            "royal" -> {
                bg(Color(0xFF3B1466), Color(0xFF1C0A38)); stars(60, 8, GOLD)
                drawCircle(Color(0xFF8E44AD).copy(alpha = .3f), h * .9f, Offset(w * .5f, h * .5f))
                crown(Offset(w * .5f, h * .5f), 90.dp.toPx(), GOLD); crown(Offset(w * .9f, h * .4f), 34.dp.toPx(), GOLD); frame()
            }
            "sakura" -> {
                bg(Color(0xFFFBD3E0), Color(0xFFE79AB8))
                val br = Path(); br.moveTo(0f, h * .85f); br.cubicTo(w * .3f, h * .6f, w * .5f, h * .8f, w, h * .2f)
                drawPath(br, Color(0xFF5A3A2E), style = Stroke(5.dp.toPx()))
                val r = Random(4)
                repeat(14) { flower(Offset(w * (.1f + r.nextFloat() * .85f), h * (.2f + r.nextFloat() * .6f)), (7 + r.nextInt(8)).dp.toPx(), Color.White.copy(alpha = .9f), Color(0xFFE0507F), 5) }
                frame(Color(0xFFB03060))
            }
            "fire" -> {
                bg(Color(0xFF2A0505), Color(0xFF7A1A05)); stars(25, 6, Color(0xFFFFB347))
                listOf(.15f, .35f, .55f, .75f, .92f).forEachIndexed { i, x ->
                    flame(w * x, 34.dp.toPx(), h * (.15f + (i % 2) * .15f), Color(0xFFE8421A)); flame(w * x, 22.dp.toPx(), h * (.35f + (i % 2) * .1f), Color(0xFFFF9A1F)); flame(w * x, 11.dp.toPx(), h * .6f, Color(0xFFFFE066))
                }
                frame(Color(0xFFFFB347))
            }
            "galaxy" -> {
                bg(Color(0xFF05020F), Color(0xFF1A0A3A)); val r = Random(12)
                repeat(260) { i -> val t = i / 260f * 14f; val rad = t * 9.dp.toPx()
                    drawCircle(listOf(Color(0xFFB57BFF), Color(0xFF5BC0FF), Color(0xFFFF7BD0))[i % 3].copy(alpha = .8f), r.nextFloat() * 1.8f.dp.toPx() + .5f,
                        Offset(w * .5f + rad * kotlin.math.cos(t + r.nextFloat() * .5f) * 1.4f, h * .5f + rad * sin(t + r.nextFloat() * .5f) * .5f)) }
                drawCircle(Color.White, 5.dp.toPx(), Offset(w * .5f, h * .5f)); stars(40, 13); frame(Color(0xFFB57BFF))
            }
            "gold" -> {
                bg(Color(0xFFFFE27A), Color(0xFFB8860B))
                repeat(6) { i -> drawLine(Color.White.copy(alpha = .25f), Offset(w * (i * .22f - .1f), h), Offset(w * (i * .22f + .15f), 0f), 10.dp.toPx()) }
                drawCircle(Color(0xFFFFF4B0), 24.dp.toPx(), Offset(w * .5f, h * .5f)); drawCircle(Color(0xFF8A6508), 24.dp.toPx(), Offset(w * .5f, h * .5f), style = Stroke(2.dp.toPx()))
                stars(20, 14, Color.White); frame(Color(0xFF6B4E00))
            }
            "diamond" -> {
                bg(Color(0xFF0B3C5D), Color(0xFF1B8FB8)); stars(40, 15)
                gem(Offset(w * .5f, h * .5f), 36.dp.toPx(), Color(0xFF7DE3FF)); gem(Offset(w * .2f, h * .65f), 17.dp.toPx(), Color(0xFFA8F0FF)); gem(Offset(w * .82f, h * .35f), 20.dp.toPx(), Color(0xFF5CD0F5))
                frame(Color(0xFFBFF3FF))
            }
            "legend" -> {
                bg(Color(0xFF12002B), Color(0xFF4B0A63)); val c = Offset(w * .5f, h * .5f)
                repeat(12) { i -> rotate(i * 30f, c) { drawLine(GOLD.copy(alpha = .5f), c, Offset(c.x, c.y - h), 2.dp.toPx()) } }
                drawCircle(Color(0xFFFF4DA6).copy(alpha = .35f), 55.dp.toPx(), c); crown(c, 80.dp.toPx(), GOLD)
                gem(Offset(w * .12f, h * .5f), 14.dp.toPx(), Color(0xFFFF6BD6)); gem(Offset(w * .88f, h * .5f), 14.dp.toPx(), Color(0xFF6BE6FF))
                stars(50, 16, GOLD); frame()
            }
        }
    }
}

@Composable
fun SkinBanner(skinId: String, playerName: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)), contentAlignment = Alignment.CenterEnd) {
        SkinArt(skinId, Modifier.matchParentSize())
        Text(playerName, Modifier.padding(end = 28.dp),
            style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                shadow = Shadow(Color.Black, Offset(2f, 2f), 6f)))
    }
}

// ───────────── نافذة الجائزة ─────────────
@Composable
fun RewardDialog(rank: Int, onDone: () -> Unit) {
    val c = LocalContext.current
    val reward = remember { awardRank(c, rank) }
    val medal = when (rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "🎖️" }
    Dialog(onDismissRequest = onDone) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF14194A)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(medal, fontSize = 64.sp)
                Text("المركز $rank", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("+$reward 💎", color = Color(0xFF7DE3FF), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("رصيدك الآن: ${Wallet.diamonds(c)} 💎", color = Color.White.copy(alpha = .8f))
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDone) { Text("تمام") }
            }
        }
    }
}

// ───────────── المتجر ─────────────
@Composable
fun SkinStoreScreen(playerName: String, onClose: () -> Unit) {
    val c = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val diamonds = remember(tick) { Wallet.diamonds(c) }
    val owned = remember(tick) { Wallet.owned(c) }
    val sel = remember(tick) { Wallet.selected(c) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0F1230))) {
            Row(Modifier.fillMaxWidth().background(Color(0xFF6B1414)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🛍️ متجر السكنات", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("$diamonds 💎", color = Color(0xFF7DE3FF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onClose) { Text("✕", color = Color.White) }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(ALL_SKINS) { s ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1F4A)), border = BorderStroke(1.dp, GOLD.copy(alpha = .6f))) {
                        Column {
                            SkinBanner(s.id, playerName, Modifier.fillMaxWidth().height(105.dp))
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${s.ar} • ${s.tier}", color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(if (s.price == 0) "مجاني" else "${s.price} 💎", color = Color(0xFF7DE3FF))
                                }
                                when {
                                    s.id == sel -> OutlinedButton(onClick = {}, enabled = false) { Text("مفعّل ✓") }
                                    s.id in owned -> Button(onClick = { Wallet.select(c, s.id); tick++ }) { Text("تفعيل") }
                                    diamonds >= s.price -> Button(onClick = { Wallet.buy(c, s); tick++ }) { Text("شراء") }
                                    else -> OutlinedButton(onClick = {}, enabled = false) { Text("🔒 ماسات ناقصة") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
