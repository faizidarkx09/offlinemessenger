package com.offlinemessenger.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinemessenger.ui.rememberLocator
import com.offlinemessenger.ui.theme.IOSBlack
import com.offlinemessenger.ui.theme.IOSBlue
import com.offlinemessenger.ui.theme.IOSGray6
import com.offlinemessenger.ui.theme.IOSSecondary
import com.offlinemessenger.ui.theme.IOSText
import java.io.ByteArrayOutputStream

/**
 * First-launch profile: name + photo. Stored once per device, sent to peers
 * inside HELLO — stable, never changes unless the user edits it.
 */
@Composable
fun ProfileSetupScreen(onDone: () -> Unit) {
    val locator = rememberLocator()
    val context = LocalContext.current
    val store = remember(locator) { locator.secureStore }
    var name by remember { mutableStateOf(store.displayName().takeIf { !it.startsWith("User-") } ?: "") }
    var photoBytes by remember { mutableStateOf<ByteArray?>(store.profilePhoto()) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) cropUri = uri
    }

    // Crop editor overlay.
    cropUri?.let { uri ->
        PhotoCropScreen(uri = uri, onDone = { bytes ->
            cropUri = null
            if (bytes != null) photoBytes = bytes
            else error = "Could not read that image"
        })
        return
    }

    error?.let {
        androidx.compose.runtime.LaunchedEffect(it) {
            snackbar.showSnackbar(it)
            error = null
        }
    }

    Scaffold(containerColor = IOSBlack, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(64.dp))
            Text("Set up your profile", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = IOSText)
            Spacer(Modifier.height(8.dp))
            Text(
                "Your name and photo are shown to people you chat with. They stay the same on this device.",
                fontSize = 15.sp,
                color = IOSSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            // Photo picker
            val bmp = remember(photoBytes) {
                photoBytes?.let {
                    runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull()
                }
            }
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(120.dp).clip(CircleShape)
                        .clickable { picker.launch("image/*") },
                )
            } else {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.size(120.dp).clip(CircleShape)
                        .background(IOSGray6)
                        .clickable { picker.launch("image/*") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Person, "add photo", tint = IOSSecondary, modifier = Modifier.size(48.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Tap to choose a photo", fontSize = 13.sp, color = IOSSecondary)
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Your name") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    val clean = name.trim()
                    if (clean.isEmpty()) {
                        error = "Please enter your name"
                        return@Button
                    }
                    store.setDisplayName(clean)
                    store.setProfilePhoto(photoBytes)
                    store.setProfileSetupDone()
                    onDone()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IOSBlue),
            ) {
                Text("Continue", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Decodes an image URI into a small JPEG thumbnail (<= 192px, ~8KB). */
fun thumbnailBytes(context: android.content.Context, uri: Uri): ByteArray? {
    val input = context.contentResolver.openInputStream(uri) ?: return null
    val original = input.use { BitmapFactory.decodeStream(it) } ?: return null
    val scale = 192f / maxOf(original.width, original.height).coerceAtLeast(1)
    val resized = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            original,
            (original.width * scale).toInt().coerceAtLeast(1),
            (original.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else original
    val out = ByteArrayOutputStream()
    resized.compress(Bitmap.CompressFormat.JPEG, 70, out)
    return out.toByteArray().takeIf { it.size <= 12 * 1024 }
}
