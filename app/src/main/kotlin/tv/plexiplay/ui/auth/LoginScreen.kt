package tv.plexiplay.ui.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import tv.plexiplay.plex.auth.PinAuthClient
import tv.plexiplay.plex.auth.TwoFactorRequired
import tv.plexiplay.ui.theme.Accent
import tv.plexiplay.ui.theme.FocusRing
import tv.plexiplay.ui.theme.Ink
import tv.plexiplay.ui.theme.Panel
import tv.plexiplay.ui.theme.Paper
import tv.plexiplay.ui.theme.Signal

@Composable
fun LoginScreen(auth: PinAuthClient, onToken: (String) -> Unit) {
    var code by remember { mutableStateOf("----") }
    var pinId by remember { mutableStateOf<Long?>(null) }
    var qrId by remember { mutableStateOf<Long?>(null) }
    var qrCode by remember { mutableStateOf("") }
    var qrUrl by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    var classic by remember { mutableStateOf(false) }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var verification by remember { mutableStateOf("") }
    var needsCode by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Création du code…") }
    var submit by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        message = "Création du code…"
        runCatching { withContext(Dispatchers.IO) { auth.createPin() } }
            .onSuccess {
                code = it.code.filter { ch -> ch.isLetterOrDigit() }.take(4).uppercase()
                pinId = it.id
                message = "Sur un ordinateur ou un téléphone : plex.tv/link, puis ce code"
            }
            .onFailure { message = it.message ?: "Code indisponible" }
        // Deuxième code, « fort », pour le QR code (Google, Apple ou e-mail sur le téléphone).
        runCatching { withContext(Dispatchers.IO) { auth.createPin(strong = true) } }
            .onSuccess {
                qrId = it.id
                qrCode = it.code
                qrUrl = auth.webAuthUrl(it.code)
            }
            .onFailure { qrUrl = "" }
    }

    LaunchedEffect(pinId, code) {
        val id = pinId ?: return@LaunchedEffect
        while (true) {
            delay(2000)
            val token = runCatching { withContext(Dispatchers.IO) { auth.poll(id, code) } }.getOrNull()?.authToken
            if (!token.isNullOrBlank()) {
                onToken(token)
                return@LaunchedEffect
            }
        }
    }

    LaunchedEffect(qrId, qrCode) {
        val id = qrId ?: return@LaunchedEffect
        while (true) {
            delay(2000)
            val token = runCatching { withContext(Dispatchers.IO) { auth.poll(id, qrCode) } }.getOrNull()?.authToken
            if (!token.isNullOrBlank()) {
                onToken(token)
                return@LaunchedEffect
            }
        }
    }

    LaunchedEffect(submit) {
        if (!submit) return@LaunchedEffect
        message = "Connexion…"
        runCatching { withContext(Dispatchers.IO) { auth.signIn(login, password, verification) } }
            .onSuccess { onToken(it) }
            .onFailure {
                if (it is TwoFactorRequired) {
                    needsCode = true
                    message = "Ce compte a la double authentification : saisissez le code à 6 chiffres"
                } else {
                    message = it.message ?: "Connexion refusée"
                }
            }
        submit = false
    }

    Row(
        modifier = Modifier.fillMaxSize().background(Ink).padding(48.dp),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text("Plexiplay", color = Accent, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text("Connexion au compte", color = Paper, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(
                code.padEnd(4, '·').take(4),
                color = Paper,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(message, color = Color(0xFF9AA3AD), fontSize = 20.sp, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Google, Apple ou e-mail : choisissez-les sur la page plex.tv/link ou avec le QR code.",
                color = Color(0xFF9AA3AD),
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(modifier = Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Chip("Nouveau code") { attempt += 1 }
                Chip(if (classic) "Masquer l'e-mail" else "E-mail et mot de passe") { classic = !classic }
            }
            if (classic) {
                Text("Adresse e-mail ou identifiant", color = Paper, modifier = Modifier.padding(top = 20.dp))
                Field(login) { login = it }
                Text("Mot de passe", color = Paper, modifier = Modifier.padding(top = 12.dp))
                Field(password, secret = true) { password = it }
                if (needsCode) {
                    Text("Code de vérification (6 chiffres)", color = Paper, modifier = Modifier.padding(top = 12.dp))
                    Field(verification) { verification = it.filter { ch -> ch.isDigit() }.take(8) }
                }
                Row(Modifier.padding(top = 12.dp)) { Chip("Valider", primary = true) { submit = true } }
            }
        }
        if (qrUrl.isNotBlank()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(300.dp)) {
                QrCode(qrUrl, Modifier.size(260.dp))
                Text(
                    "Scannez avec votre téléphone",
                    color = Paper, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    "Connexion avec Google, Apple ou e-mail",
                    color = Color(0xFF9AA3AD), fontSize = 15.sp, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun QrCode(content: String, modifier: Modifier) {
    val matrix = remember(content) {
        runCatching {
            QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
        }.getOrNull()
    }
    Canvas(modifier.background(Color.White)) {
        val m = matrix ?: return@Canvas
        val cell = size.minDimension / m.width
        for (y in 0 until m.height) for (x in 0 until m.width) {
            if (m.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.6f, cell + 0.6f))
        }
    }
}

@Composable
private fun Chip(label: String, primary: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fill = when {
        primary -> Signal
        focused -> Accent
        else -> Panel
    }
    Text(
        label,
        color = if (primary || focused) Ink else Paper,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onClick()
                    true
                } else false
            }
            .focusable()
            .border(2.dp, if (focused) FocusRing else Color.Transparent, RoundedCornerShape(8.dp))
            .background(fill, RoundedCornerShape(8.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

@Composable
private fun Field(value: String, secret: Boolean = false, onValue: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        textStyle = TextStyle(color = Paper, fontSize = 22.sp),
        modifier = Modifier.width(420.dp).padding(top = 6.dp).background(Panel).padding(12.dp).focusable(),
    )
}
