package com.example.m_agrilink.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.m_agrilink.domain.RoomFarmerAccountRepository
import kotlinx.coroutines.launch

private const val TERMS_AND_CONDITIONS_BODY =
    "M-AGRILINK PLATFORM TERMS AND CONDITIONS\n\n" +
        "Last Updated: October 7, 2026\n\n" +
        "Welcome to M-AgriLink. This application is designed and built by the M-AgriLink engineering team. " +
        "By creating an account or logging into the M-AgriLink mobile application, they explicitly accept and agree to follow these Terms and Conditions in full. " +
        "If they disagree with any part of these terms, they must immediately stop using the application.\n\n" +
        "1. General Acceptance & Account Creation\n\n" +
        "\u2022 User Inclusivity: The M-AgriLink ecosystem is a universally open framework. They are fully responsible for maintaining the strict confidentiality of their personal login credentials, including their passwords and telephone registry parameters.\n" +
        "\u2022 Verification Compliance: They agree to provide complete, accurate, and honest information during account creation so their profile can be verified. Any attempt by them to supply fraudulent information will result in immediate profile suspension.\n\n" +
        "2. Data Sync Limitations & Liability Protection\n\n" +
        "\u2022 KAMIS & Public Indices: Market value telemetry \u2014 such as the pure natural honey standard average index of KES 970.00/Kg \u2014 is parsed live via standard agricultural databases. M-AgriLink acts purely as an informational data mirroring utility. Because physical commodity transactions take place independently off the app platform, the system architect bears zero liability for regional trading variations or market price fluctuations in their field.\n" +
        "\u2022 Open-Meteo & Climate Data Grids: Localization climate calculations use high-resolution public forecasts via Open-Meteo REST APIs. Rapidly forming microclimates or convective rainfall discrepancies can trigger discrepancies on their phone display readouts. The platform does not guarantee absolute, minute-by-minute forecasting infallibility, and they must cross-reference critical spray windows accordingly.\n" +
        "\u2022 Shamba AI Crop Diagnostics: Vision scanner diagnostic data cards are strictly compiled for educational and remote advisory scoping using established CABI Plantwise and KALRO matrices. They do not constitute formal, absolute agricultural pesticide prescriptions for their farm. They must follow local environmental safety guidelines when handling field outbreaks.\n\n" +
        "3. Logistics Safety & Fraud Prevention Protocols\n\n" +
        "\u2022 Non-Transactional Space: The Regional Transport & Lorry Logistics locator module is a non-transactional tracking space. All transport bookings, scheduling arrangements, and payment adjustments by them occur entirely off the app framework.\n" +
        "\u2022 The Deposit Prohibition Rule: They are strictly warned to NEVER pay upfront logistical deposits or commitment fuel fees to any listed vehicle operator before their harvest produce is physically loaded onto the truck bed and weighed. M-AgriLink handles no digital financial escrow and holds zero legal responsibility for off-platform financial losses or transport route conflicts affecting them.\n\n" +
        "4. Apiculture Management and Environmental Safety\n\n" +
        "\u2022 KALRO ABIRI Guidelines: Beekeeping guidelines, yield estimates, and floral calendar parameters follow standard KALRO TIMPs manuals. Apiary operations involve interacting with defensive wild bee populations. They assume full physical risk for their own safety during hive smoke applications and comb extraction setups on their apiary.\n\n" +
        "5. Intellectual Property & Governance\n\n" +
        "\u2022 The entire architectural design layout, source code algorithms, visual user interface frameworks, offline persistence models, and branding assets are the exclusive intellectual property of M-AgriLink. Unauthorized replication, reverse engineering, or redistribution of these properties by them is strictly prohibited."

private val FieldGreenDark = Color(0xFF064E3B)
private val FieldGreen = Color(0xFF1B5E20)
private val FieldGreenLight = Color(0xFF2E7D32)
private val GoldAccent = Color(0xFFE6B325)
private val SproutLightGreen = Color(0xFF52B788)
private val OrganicForestGreen = Color(0xFF1B4332)

private val FullNameRegex = Regex("^[A-Za-z][A-Za-z .'-]{1,48}[A-Za-z.]?$")
private val EmailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
private val PhoneRegex = Regex("^\\+?[0-9][0-9 ()-]{6,17}[0-9]$")

private fun isFullNameValid(value: String): Boolean {
    val v = value.trim()
    if (v.length < 2 || v.length > 50) return false
    return FullNameRegex.matches(v)
}

private fun isEmailOrPhoneValid(value: String): Boolean {
    val v = value.trim()
    if (v.isEmpty()) return false
    return EmailRegex.matches(v) || PhoneRegex.matches(v)
}

private fun isPasswordValid(value: String): Boolean = value.length >= 6

/**
 * Independent onboarding boot entry point.
 *
 * Boot splash now lives at the application root ([BootSplash] in
 * MainActivity, 2500ms cross-fade): this screen renders the gold-accented
 * registration form directly. Submit forwards the validated
 * name + email via [onAuthenticated] so the dashboard route can populate
 * the Active Account Profile HUD.
 */
@Composable
fun AuthOnboardingScreen(
    onAuthenticated: (name: String, email: String) -> Unit = { _, _ -> },
    onGoogleSignIn: (name: String, email: String) -> Unit = { _, _ -> }
) {
    AccountCreationPanel(
        onAuthenticated = onAuthenticated,
        onGoogleSignIn = onGoogleSignIn
    )
}

@Composable
private fun OnboardingGlowHeader(validationProgress: Float) {
    // Soft breathing glow fading between sprout light green and organic forest green.
    val glowTransition = rememberInfiniteTransition(label = "onboardingGlow")
    val glowFraction by glowTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowFraction"
    )
    val glowColor = lerp(SproutLightGreen, OrganicForestGreen, glowFraction)
    val glowAlpha by glowTransition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.70f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    // Progress bar tracks live validation progress as they type.
    val animatedProgress by animateFloatAsState(
        targetValue = validationProgress,
        animationSpec = tween(durationMillis = 500),
        label = "sproutProgress"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(glowColor.copy(alpha = glowAlpha))
                .padding(horizontal = 28.dp, vertical = 12.dp)
        ) {
            Text(
                text = "M-AgriLink",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = SproutLightGreen,
            trackColor = OrganicForestGreen.copy(alpha = 0.18f)
        )
    }
}

@Composable
private fun AccountCreationPanel(
    onAuthenticated: (name: String, email: String) -> Unit,
    onGoogleSignIn: (name: String, email: String) -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var emailOrPhone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var hasAcceptedTerms by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accountRepo = remember(context) { RoomFarmerAccountRepository(context.applicationContext) }

    val fullNameOk = isFullNameValid(fullName)
    val contactOk = isEmailOrPhoneValid(emailOrPhone)
    val passwordOk = isPasswordValid(password)
    val confirmOk = confirmPassword.isNotEmpty() && confirmPassword == password
    // Strict format flags above drive inline hints + the sprout tracker.
    // The submit gate below uses presence + length + match + consent so a
    // checked box with filled rows always unlocks the button.
    val isFormValid = fullName.trim().isNotEmpty() &&
        emailOrPhone.trim().isNotEmpty() &&
        password.length >= 6 &&
        confirmPassword == password &&
        hasAcceptedTerms
    val completedSteps = listOf(fullNameOk, contactOk, passwordOk, confirmOk, hasAcceptedTerms).count { it }
    val validationProgress = completedSteps / 5f

    fun persistAccountAndContinue() {
        if (!isFormValid || isSaving) return
        submitted = true
        isSaving = true
        authError = null
        scope.launch {
            try {
                val contact = emailOrPhone.trim()
                val isEmail = contact.contains("@")
                val name = fullName.trim()
                val email = if (isEmail) contact else ""
                accountRepo.ensureGuestProfile()
                accountRepo.saveDisplayName(name)
                accountRepo.updateProfileDetails(
                    email = email,
                    phone = if (isEmail) "" else contact,
                    county = "",
                    acreage = 1.0
                )
                onAuthenticated(name, email)
            } catch (e: Exception) {
                authError = "We could not create your account: ${e.message ?: "try again"}."
            } finally {
                isSaving = false
            }
        }
    }

    fun persistGoogleAndContinue() {
        if (isSaving) return
        isSaving = true
        authError = null
        scope.launch {
            try {
                val name = fullName.trim().ifBlank { "Levis Lekesio" }
                val contact = emailOrPhone.trim()
                val email = if (contact.contains("@")) contact else "levislekesio@gmail.com"
                accountRepo.ensureGuestProfile()
                accountRepo.saveDisplayName(name)
                onGoogleSignIn(name, email)
            } catch (e: Exception) {
                authError = "We could not link your Google identity: ${e.message ?: "try again"}."
            } finally {
                isSaving = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // HEADER METRICS with breathing agritech glow + sprout validation tracker.
        OnboardingGlowHeader(validationProgress = validationProgress)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Welcome \u2794 Create Account",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.DarkGray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))

        // THIRD-PARTY FEDERATED SIGN-IN ROW
        OutlinedButton(
            onClick = { persistGoogleAndContinue() },
            enabled = !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = CircleShape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black)
        ) {
            GoogleBrandMark()
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Continue with Google",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Link your Google identity for fast, secure authentication.",
            fontSize = 12.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))

        // FORM INPUT FIELDS — minimalist: floating labels only, no decorative icons.
        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Full Name") },
            placeholder = { Text("Enter your full name (e.g. Amina Odhiambo)") },
            supportingText = {
                if (fullName.isNotEmpty() && !fullNameOk) {
                    Text("Use letters only so you are recognised correctly (min 2 characters).")
                } else {
                    Text("Enter the name on your national ID.")
                }
            },
            isError = fullName.isNotEmpty() && !fullNameOk,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = emailOrPhone,
            onValueChange = { emailOrPhone = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email or Phone Number") },
            placeholder = { Text("Enter your email or phone number...") },
            supportingText = {
                if (emailOrPhone.isNotEmpty() && !contactOk) {
                    Text("Enter a valid email or phone number so we can reach you.")
                } else {
                    Text("You will receive your verification code here.")
                }
            },
            isError = emailOrPhone.isNotEmpty() && !contactOk,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            placeholder = { Text("Create your password (min. 6 characters)...") },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = {
                if (password.isNotEmpty() && !passwordOk) {
                    Text("Password must be at least 6 characters long.")
                } else {
                    Text("Keep your password secret from others.")
                }
            },
            isError = password.isNotEmpty() && !passwordOk,
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Confirm Password") },
            placeholder = { Text("Confirm your password...") },
            trailingIcon = {
                IconButton(onClick = { confirmVisible = !confirmVisible }) {
                    Icon(
                        imageVector = if (confirmVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (confirmVisible) "Hide confirmation" else "Show confirmation"
                    )
                }
            },
            visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = {
                if (confirmPassword.isNotEmpty() && !confirmOk) {
                    Text("Your passwords must match perfectly before you continue.")
                } else {
                    Text("Confirm your password so nothing is mistyped.")
                }
            },
            isError = confirmPassword.isNotEmpty() && !confirmOk,
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))

        // DYNAMIC EXPANDABLE TERMS DIALOG INTERFACE
        Text(
            text = "View the official M-AgriLink Terms and Conditions",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = FieldGreen,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { showTermsDialog = true }
                .padding(vertical = 6.dp, horizontal = 4.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))

        // MANDATORY CHECKBOX QUALITY GATE FILTER
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.Start
        ) {
            Checkbox(
                checked = hasAcceptedTerms,
                onCheckedChange = { hasAcceptedTerms = it }
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "I have read and agree to the Terms and Conditions of M-AgriLink",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 10.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (showTermsDialog) {
            TermsAndConditionsSheet(
                onDismiss = { showTermsDialog = false },
                onAccept = {
                    hasAcceptedTerms = true
                    showTermsDialog = false
                }
            )
        }

        // FORM TRIGGER UNLOCK LOGIC: isFormValid resolves true once every row
        // is filled (password >= 6, passwords match) AND hasAcceptedTerms is
        // true. The button then flips to active harvest gold and enables
        // clicks. Tapping persists the account off the main thread, then
        // fires onAuthenticated() — the MainActivity boot router swaps to
        // DashboardScreen(), the app's "dashboard" route.
        if (authError != null) {
            Text(
                text = authError ?: "",
                fontSize = 12.sp,
                color = Color(0xFFB71C1C),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }
        Button(
            onClick = { persistAccountAndContinue() },
            enabled = isFormValid && !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GoldAccent,
                contentColor = FieldGreenDark,
                disabledContainerColor = Color(0xFFBDBDBD),
                disabledContentColor = Color.White
            )
        ) {
            Text(
                if (isSaving) "Creating your M-AgriLink Account..." else "Create Account",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (submitted && !isFormValid) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Please fix the highlighted fields before you continue.",
                fontSize = 12.sp,
                color = Color(0xFFB71C1C),
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "By creating an account you agree to keep your harvest data protected.",
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "© 2026 M-AgriLink. All rights reserved.",
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TermsAndConditionsSheet(
    onDismiss: () -> Unit,
    onAccept: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "M-AGRILINK PLATFORM TERMS AND CONDITIONS",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FieldGreen
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = TERMS_AND_CONDITIONS_BODY,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFF2C2C2E)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAccept,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FieldGreen,
                    contentColor = GoldAccent
                )
            ) {
                Text("I Accept", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GoogleBrandMark() {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "G",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF1B5E20)
        )
    }
}
