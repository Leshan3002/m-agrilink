package com.example.m_agrilink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val SPLASH_TIMEOUT_MS = 2500L
private const val IDENTITY_ATTRIBUTION =
    "M-AgriLink Identity Protection System \u2014 Configured and Directed by Lead System Architect Levis Lekesio."

private val FieldGreenDark = Color(0xFF064E3B)
private val FieldGreen = Color(0xFF1B5E20)
private val FieldGreenLight = Color(0xFF2E7D32)
private val GoldAccent = Color(0xFFE6B325)

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
 * Phase 1: full-bleed field-green splash reading "Welcome to M-AgriLink",
 * auto-dismissed after a non-blocking 2.5s delay.
 * Phase 2: scrollable account creation form. Submit navigates to dashboard
 * via [onAuthenticated].
 */
@Composable
fun AuthOnboardingScreen(
    onAuthenticated: () -> Unit = {},
    onGoogleSignIn: () -> Unit = {}
) {
    var showSplash by remember { mutableStateOf(true) }

    // Non-blocking boot timeout: fades splash out, reveals the form.
    LaunchedEffect(Unit) {
        delay(SPLASH_TIMEOUT_MS)
        showSplash = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Account creation panel is always composed underneath so the
        // splash fade reveals it without a navigation jump.
        AccountCreationPanel(
            onAuthenticated = onAuthenticated,
            onGoogleSignIn = onGoogleSignIn
        )

        AnimatedVisibility(
            visible = showSplash,
            enter = fadeIn(animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(600))
        ) {
            WelcomeSplash()
        }
    }
}

@Composable
private fun WelcomeSplash() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(FieldGreenDark, FieldGreen, FieldGreenLight)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Welcome to M-AgriLink",
                color = GoldAccent,
                fontSize = 30.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(GoldAccent.copy(alpha = 0.85f))
            )
        }
    }
}

@Composable
private fun AccountCreationPanel(
    onAuthenticated: () -> Unit,
    onGoogleSignIn: () -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var emailOrPhone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }

    val fullNameOk = isFullNameValid(fullName)
    val contactOk = isEmailOrPhoneValid(emailOrPhone)
    val passwordOk = isPasswordValid(password)
    val confirmOk = confirmPassword.isNotEmpty() && confirmPassword == password
    val formValid = fullNameOk && contactOk && passwordOk && confirmOk

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // HEADER METRICS
        Text(
            text = "M-AgriLink",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = FieldGreen,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Welcome \u2794 Create Account",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.DarkGray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = IDENTITY_ATTRIBUTION,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))

        // THIRD-PARTY FEDERATED SIGN-IN ROW
        OutlinedButton(
            onClick = onGoogleSignIn,
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
            text = "They can link their Google identity to keep their account secure.",
            fontSize = 12.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))

        // FORM INPUT FIELDS
        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Full Name") },
            placeholder = { Text("Enter their full name (e.g. Amina Odhiambo)") },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = "Full name") },
            supportingText = {
                if (fullName.isNotEmpty() && !fullNameOk) {
                    Text("Use letters only so they are recognised correctly (min 2 characters).")
                } else {
                    Text("They should enter the name on their national ID.")
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
            placeholder = { Text("They can use their email or phone number") },
            leadingIcon = { Icon(Icons.Filled.Email, contentDescription = "Email or phone") },
            supportingText = {
                if (emailOrPhone.isNotEmpty() && !contactOk) {
                    Text("Enter a valid email or phone number so they can be reached.")
                } else {
                    Text("They will receive their verification code here.")
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
            placeholder = { Text("They should choose a private password (min 6 characters)") },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = "Password") },
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
                    Text("Their password needs at least 6 characters.")
                } else {
                    Text("They should keep their password secret from others.")
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
            placeholder = { Text("They should repeat their password to confirm it") },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = "Confirm password") },
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
                    Text("Their passwords must match perfectly before they continue.")
                } else {
                    Text("They confirm their password so nothing is mistyped.")
                }
            },
            isError = confirmPassword.isNotEmpty() && !confirmOk,
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))

        // FORM TRIGGER RULES
        Button(
            onClick = {
                submitted = true
                if (formValid) onAuthenticated()
            },
            enabled = formValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FieldGreen)
        ) {
            Text("Create Account", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        if (submitted && !formValid) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "They still need to fix the highlighted fields before they can continue.",
                fontSize = 12.sp,
                color = Color(0xFFB71C1C),
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "By creating an account they agree to keep their harvest data protected under the Identity Protection System.",
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
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
