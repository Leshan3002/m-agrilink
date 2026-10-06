package com.example.m_agrilink.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/** Immutable development stamp (top header layout). */
private const val DRIVER_CONSOLE_STAMP =
    "M-AgriLink Security Console — Programmed and Configured by Lead System Architect Levis Lekesio."

/** Background verification holding-loop states for a submitted driver profile. */
private enum class DriverStatus {
    NOT_SUBMITTED,
    PENDING,
    VERIFIED
}

/**
 * Driver registration payload. Pronoun handling is neutral by default —
 * status copy throughout this screen addresses the applicant as they /
 * their / them.
 */
private data class DriverVerificationProfile(
    val legalName: String,
    val nationalId: String,
    val mobile: String,
    val plateNumber: String,
    val pronouns: String = "they/them",
    val documentsComplete: Boolean = false
)

@Composable
private fun DocumentUploadRow(
    labelKey: String,
    bitmap: Bitmap?,
    onCapture: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2C2C2E), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = str(labelKey),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (bitmap != null) str("driver_captured_ok") else str("driver_captured"),
                color = if (bitmap != null) Color(0xFF81C784) else Color.Gray,
                fontSize = 12.sp
            )
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        OutlinedButton(
            onClick = onCapture,
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = if (bitmap == null) str("driver_capture") else str("driver_retake"),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun DriverRegistrationScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var idNumber by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var plateNumber by remember { mutableStateOf("") }
    var idImg by remember { mutableStateOf<Bitmap?>(null) }
    var dlImg by remember { mutableStateOf<Bitmap?>(null) }
    var logbookImg by remember { mutableStateOf<Bitmap?>(null) }
    var clearanceImg by remember { mutableStateOf<Bitmap?>(null) }
    var pendingSlot by remember { mutableStateOf<Int?>(null) }
    var verificationStatus by remember { mutableStateOf(DriverStatus.NOT_SUBMITTED) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Single lifecycle-aware picture-capture launcher; the pending slot
    // records which document row requested the shot. No CameraX session is
    // bound here, so there is nothing to leak on dispose.
    val captureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            when (pendingSlot) {
                0 -> idImg = bmp
                1 -> dlImg = bmp
                2 -> logbookImg = bmp
                3 -> clearanceImg = bmp
            }
        }
        pendingSlot = null
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted && pendingSlot != null) {
            captureLauncher.launch(null)
        } else {
            pendingSlot = null
        }
    }
    fun requestCapture(slot: Int) {
        pendingSlot = slot
        if (hasCameraPermission) {
            captureLauncher.launch(null)
        } else {
            try {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            } catch (e: Exception) {
                pendingSlot = null
            }
        }
    }

    // Rigid validation state machine: every text field must pass its regex
    // and every mandatory camera slot must hold a capture before submit.
    val nameOk = name.trim().matches(Regex("^[A-Za-z][A-Za-z.'\\- ]{2,}$")) &&
        name.trim().contains(" ")
    val idOk = idNumber.trim().matches(Regex("^\\d{7,8}$"))
    val mobileOk = mobile.trim().replace(" ", "").matches(Regex("^0[17]\\d{8}$"))
    val plateOk = plateNumber.trim().uppercase()
        .matches(Regex("^[A-Z]{3} ?\\d{3}[A-Z]$"))
    val hasIdImg = idImg != null
    val hasDlImg = dlImg != null
    val hasLogbookImg = logbookImg != null
    val hasClearanceImg = clearanceImg != null
    val isFormValid = remember(
        name, idNumber, mobile, plateNumber,
        hasIdImg, hasDlImg, hasLogbookImg, hasClearanceImg
    ) {
        nameOk && idOk && mobileOk && plateOk &&
            hasIdImg && hasDlImg && hasLogbookImg && hasClearanceImg
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            TextButton(onClick = onBackClick) {
                Text(
                    text = "← " + str("back_home"),
                    color = Color(0xFF1B5E20),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = DRIVER_CONSOLE_STAMP,
                color = Color.Gray,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = str("driver_title"),
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF2C2C2E)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = str("driver_sub"),
                fontSize = 13.sp,
                color = Color.DarkGray,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (verificationStatus == DriverStatus.PENDING) {
                // 🛡️ UNDER REVIEW — secure holding loop (simulated background check).
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFE8F5E9), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = str("driver_pending_badge"),
                                color = Color(0xFF1B5E20),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = str("driver_review"),
                            color = Color.White,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = str("driver_pronoun_note"),
                            color = Color(0xFFE6B325),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(str("driver_name"), color = Color.Gray) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            isError = name.isNotBlank() && !nameOk
                        )
                        if (name.isNotBlank() && !nameOk) {
                            Text(str("driver_err_name"), color = Color(0xFFDC2626), fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = idNumber,
                            onValueChange = { idNumber = it.filter { c -> c.isDigit() } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(str("driver_id"), color = Color.Gray) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            isError = idNumber.isNotBlank() && !idOk
                        )
                        if (idNumber.isNotBlank() && !idOk) {
                            Text(str("driver_err_id"), color = Color(0xFFDC2626), fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = mobile,
                            onValueChange = { mobile = it.filter { c -> c.isDigit() } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(str("driver_mobile"), color = Color.Gray) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            isError = mobile.isNotBlank() && !mobileOk
                        )
                        if (mobile.isNotBlank() && !mobileOk) {
                            Text(str("driver_err_mobile"), color = Color(0xFFDC2626), fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = plateNumber,
                            onValueChange = { plateNumber = it.uppercase() },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(str("driver_plate"), color = Color.Gray) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            isError = plateNumber.isNotBlank() && !plateOk
                        )
                        if (plateNumber.isNotBlank() && !plateOk) {
                            Text(str("driver_err_plate"), color = Color(0xFFDC2626), fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        Text(
                            text = str("driver_docs_h"),
                            color = Color(0xFFE6B325),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        DocumentUploadRow("driver_up_id", idImg) { requestCapture(0) }
                        DocumentUploadRow("driver_up_dl", dlImg) { requestCapture(1) }
                        DocumentUploadRow("driver_up_logbook", logbookImg) { requestCapture(2) }
                        DocumentUploadRow("driver_up_clearance", clearanceImg) { requestCapture(3) }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        @Suppress("UNUSED_VARIABLE")
                        val profile = DriverVerificationProfile(
                            legalName = name.trim(),
                            nationalId = idNumber.trim(),
                            mobile = mobile.trim(),
                            plateNumber = plateNumber.trim().uppercase(),
                            documentsComplete = true
                        )
                        verificationStatus = DriverStatus.PENDING
                    },
                    enabled = isFormValid,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                ) {
                    Text(
                        text = str("driver_submit"),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = DRIVER_CONSOLE_STAMP,
                    color = Color.Gray,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
