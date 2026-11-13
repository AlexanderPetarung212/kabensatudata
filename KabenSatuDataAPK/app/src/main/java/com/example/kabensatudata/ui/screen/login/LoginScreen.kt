package com.example.kabensatudata.ui.screen.login

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.example.kabensatudata.R
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.data.remote.dto.LoginRequest
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.PlusJakartaSansFamily
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val apiService: ApiService,
    private val tokenManager: AuthTokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    init {
        if (tokenManager.isLoggedIn()) {
            _uiState.value = _uiState.value.copy(isLoggedIn = true)
        }
    }

    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(email = email, error = null)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }

    fun login() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "Email dan password harus diisi")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiService.login(
                    LoginRequest(
                        email = state.email,
                        password = state.password,
                        deviceName = android.os.Build.MODEL,
                        deviceId = tokenManager.getDeviceId()
                    )
                )

                if (response.isSuccessful) {
                    val body = response.body()!!
                    tokenManager.saveToken(body.token, body.expiresAt)
                    tokenManager.saveUser(
                        body.user.id,
                        body.user.name,
                        body.user.email,
                        body.user.roles
                    )
                    tokenManager.savePassword(state.password)
                    _uiState.value = _uiState.value.copy(isLoading = false, isLoggedIn = true)
                } else {
                    val errorMsg = when (response.code()) {
                        401, 422 -> "Email atau password salah"
                        403 -> "Akun tidak memiliki akses mobile"
                        else -> "Gagal login: ${response.code()}"
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false, error = errorMsg)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Tidak dapat terhubung ke server. Periksa koneksi internet."
                )
            }
        }
    }
}

// ─── Warna gradient background login ──────────────────────
private val LoginGradientColors = listOf(
    Color(0xFF00C49A),
    Color(0xFF00B894),
    Color(0xFF009B7D),
    Color(0xFF007A63),
    Color(0xFF005C4A)
)

// ─── LoginScreen Composable ──────────────────────────────

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) onLoginSuccess()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(LoginGradientColors))
    ) {
        // ── Blob dekorasi kanan atas ───────────────────
        BlobDecoration(
            modifier = Modifier.align(Alignment.TopEnd).size(220.dp),
            color = Color.White.copy(alpha = 0.06f),
            offsetX = 60f, offsetY = -60f, radius = 180f
        )
        BlobDecoration(
            modifier = Modifier.align(Alignment.TopEnd).size(140.dp),
            color = Color.White.copy(alpha = 0.04f),
            offsetX = 20f, offsetY = 40f, radius = 110f
        )

        // ── Blob dekorasi kiri bawah ───────────────────
        BlobDecoration(
            modifier = Modifier.align(Alignment.BottomStart).size(260.dp),
            color = Color.White.copy(alpha = 0.05f),
            offsetX = -80f, offsetY = 80f, radius = 200f
        )
        BlobDecoration(
            modifier = Modifier.align(Alignment.BottomStart).size(160.dp),
            color = Color(0xFF00E5B0).copy(alpha = 0.08f),
            offsetX = 30f, offsetY = 30f, radius = 120f
        )

        // ── Dots floating ─────────────────────────────
        FloatingDots()

        // ── Konten utama ───────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mascot animasi
            val context = LocalContext.current
            val imageLoader = remember {
                ImageLoader.Builder(context)
                    .components {
                        if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
                        else add(GifDecoder.Factory())
                    }.build()
            }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(R.raw.animasi)
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = "Mascot",
                modifier = Modifier.size(150.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(Modifier.height(8.dp))

            // App title
            Text(
                text = "KabenSatuData",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = PlusJakartaSansFamily,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Sistem Informasi Kesehatan Puskesmas",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = PlusJakartaSansFamily,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // ── Glass Card Form ──────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Color.White.copy(alpha = 0.25f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Email field
                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = viewModel::onEmailChange,
                        label = {
                            Text(
                                "Email",
                                fontFamily = PlusJakartaSansFamily,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Person, null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.12f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                            focusedBorderColor = Color.White.copy(alpha = 0.6f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            cursorColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = PlusJakartaSansFamily,
                            fontSize = 15.sp
                        )
                    )

                    // Password field
                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = viewModel::onPasswordChange,
                        label = {
                            Text(
                                "Password",
                                fontFamily = PlusJakartaSansFamily,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Lock, null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible)
                            VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            viewModel.login()
                        }),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.12f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                            focusedBorderColor = Color.White.copy(alpha = 0.6f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            cursorColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = PlusJakartaSansFamily,
                            fontSize = 15.sp
                        )
                    )

                    // Error message
                    if (uiState.error != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFF6B6B).copy(alpha = 0.25f)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline, null,
                                    tint = Color(0xFFFFCDD2),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    uiState.error!!,
                                    color = Color(0xFFFFCDD2),
                                    fontSize = 13.sp,
                                    fontFamily = PlusJakartaSansFamily,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Login button
                    Button(
                        onClick = viewModel::login,
                        enabled = !uiState.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = AppColors.PrimaryDark,
                            disabledContainerColor = Color.White.copy(alpha = 0.3f),
                            disabledContentColor = Color.White.copy(alpha = 0.5f)
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 1.dp
                        )
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = AppColors.PrimaryDark
                            )
                        } else {
                            Text(
                                "Masuk",
                                fontSize = 16.sp,
                                fontFamily = PlusJakartaSansFamily,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.PrimaryDark
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Gunakan akun yang sama dengan website",
                fontSize = 12.sp,
                fontFamily = PlusJakartaSansFamily,
                color = Color.White.copy(alpha = 0.55f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── Blob dekorasi background ────────────────────────────

@Composable
private fun BlobDecoration(
    modifier: Modifier = Modifier,
    color: Color,
    offsetX: Float,
    offsetY: Float,
    radius: Float
) {
    Canvas(modifier = modifier) {
        drawCircle(
            color = color,
            radius = radius,
            center = Offset(size.width / 2 + offsetX, size.height / 2 + offsetY)
        )
    }
}

// ─── Floating dots ────────────────────────────────────────

@Composable
private fun FloatingDots() {
    val dots = remember {
        listOf(
            Triple(0.15f, 0.12f, 4.dp),
            Triple(0.85f, 0.22f, 3.dp),
            Triple(0.08f, 0.45f, 5.dp),
            Triple(0.92f, 0.55f, 3.dp),
            Triple(0.25f, 0.80f, 4.dp),
            Triple(0.75f, 0.88f, 6.dp),
            Triple(0.50f, 0.05f, 3.dp),
            Triple(0.60f, 0.72f, 4.dp),
        )
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        dots.forEach { (xRatio, yRatio, sizeDp) ->
            drawCircle(
                color = Color.White.copy(alpha = 0.18f),
                radius = sizeDp.toPx(),
                center = Offset(size.width * xRatio, size.height * yRatio)
            )
        }
    }
}
