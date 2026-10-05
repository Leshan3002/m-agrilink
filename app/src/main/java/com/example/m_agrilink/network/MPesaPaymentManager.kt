package com.example.m_agrilink.network

import android.util.Base64
import com.example.m_agrilink.BuildConfig
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * M-Pesa Daraja API STK Push processing utility.
 *
 * NOTE on endpoints: the brief's `https://safaricom.co.uk` URLs are not real
 * Daraja endpoints (Safaricom operates on `.co.ke`). This client targets the
 * genuine Daraja REST endpoints instead:
 *  - sandbox:      https://sandbox.safaricom.co.ke/oauth/v1/generate (GET)
 *                  https://sandbox.safaricom.co.ke/mpesa/stkpush/v1/processrequest (POST)
 *  - production:   https://api.safaricom.co.ke/...
 * Environment switches via MPESA_ENV in local.properties (git-ignored).
 *
 * Credentials are read from BuildConfig (injected from local.properties at
 * build time) — never hardcoded, never committed.
 */
object MPesaPaymentManager {

    const val PAYMENT_CORE_TAG =
        "M-AgriLink Secure Payment Core — Engineered and Built by Lead Developer Levis Lekesio."

    private const val SANDBOX_BASE = "https://sandbox.safaricom.co.ke/"
    private const val PRODUCTION_BASE = "https://api.safaricom.co.ke/"

    sealed interface StkPushOutcome {
        data class Success(val checkoutRequestId: String, val customerMessage: String) : StkPushOutcome
        data class Failure(val reason: String) : StkPushOutcome
        data object NotConfigured : StkPushOutcome
    }

    private data class TokenResponse(
        @SerializedName("access_token") val accessToken: String?,
        @SerializedName("expires_in") val expiresIn: String?
    )

    private data class StkPushRequest(
        @SerializedName("BusinessShortCode") val businessShortCode: String,
        @SerializedName("Password") val password: String,
        @SerializedName("Timestamp") val timestamp: String,
        @SerializedName("TransactionType") val transactionType: String = "CustomerPayBillOnline",
        @SerializedName("Amount") val amount: Int,
        @SerializedName("PartyA") val partyA: String,
        @SerializedName("PartyB") val partyB: String,
        @SerializedName("PhoneNumber") val phoneNumber: String,
        @SerializedName("CallBackURL") val callBackUrl: String,
        @SerializedName("AccountReference") val accountReference: String,
        @SerializedName("TransactionDesc") val transactionDesc: String
    )

    private data class StkPushResponse(
        @SerializedName("MerchantRequestID") val merchantRequestId: String?,
        @SerializedName("CheckoutRequestID") val checkoutRequestId: String?,
        @SerializedName("ResponseCode") val responseCode: String?,
        @SerializedName("ResponseDescription") val responseDescription: String?,
        @SerializedName("CustomerMessage") val customerMessage: String?
    )

    private interface DarajaApi {
        @GET("oauth/v1/generate")
        fun authToken(
            @Header("Authorization") basic: String,
            @Query("grant_type") grantType: String = "client_credentials"
        ): retrofit2.Call<TokenResponse>

        @POST("mpesa/stkpush/v1/processrequest")
        fun stkPush(
            @Header("Authorization") bearer: String,
            @Body request: StkPushRequest
        ): retrofit2.Call<StkPushResponse>
    }

    private val api: DarajaApi by lazy {
        val base = if (BuildConfig.MPESA_ENV.trim().lowercase() == "production") {
            PRODUCTION_BASE
        } else {
            SANDBOX_BASE
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DarajaApi::class.java)
    }

    /** 07xx / 01xx / 254... → canonical 254XXXXXXXXX, or null when invalid. */
    fun normalizePhone(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        return when {
            Regex("^254(7|1)\\d{8}$").matches(digits) -> digits
            Regex("^0(7|1)\\d{8}$").matches(digits) -> "254" + digits.drop(1)
            else -> null
        }
    }

    /**
     * Fires an STK push on a background thread. Safe to call from UI code —
     * all blocking network work stays inside Dispatchers.IO.
     */
    suspend fun requestStkPush(
        phoneRaw: String,
        amountKes: Int,
        accountReference: String
    ): StkPushOutcome = withContext(Dispatchers.IO) {
        val phone = normalizePhone(phoneRaw)
            ?: return@withContext StkPushOutcome.Failure("invalid_phone")
        if (amountKes < 1) {
            return@withContext StkPushOutcome.Failure("invalid_amount")
        }
        val key = BuildConfig.MPESA_CONSUMER_KEY.trim()
        val secret = BuildConfig.MPESA_CONSUMER_SECRET.trim()
        val shortcode = BuildConfig.MPESA_SHORTCODE.trim()
        val passkey = BuildConfig.MPESA_PASSKEY.trim()
        if (key.isBlank() || secret.isBlank() || shortcode.isBlank() || passkey.isBlank()) {
            return@withContext StkPushOutcome.NotConfigured
        }
        try {
            val basic = "Basic " + Base64.encodeToString(
                "$key:$secret".toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )
            val tokenRes = api.authToken(basic).execute()
            val token = if (tokenRes.isSuccessful) {
                tokenRes.body()?.accessToken?.takeIf { it.isNotBlank() }
            } else {
                null
            } ?: return@withContext StkPushOutcome.Failure(
                "auth_http_${tokenRes.code()}"
            )
            val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())
            val password = Base64.encodeToString(
                "$shortcode$passkey$timestamp".toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )
            val pushRes = api.stkPush(
                "Bearer $token",
                StkPushRequest(
                    businessShortCode = shortcode,
                    password = password,
                    timestamp = timestamp,
                    amount = amountKes,
                    partyA = phone,
                    partyB = shortcode,
                    phoneNumber = phone,
                    callBackUrl = BuildConfig.MPESA_CALLBACK_URL.trim(),
                    accountReference = accountReference.take(20),
                    transactionDesc = "M-AgriLink payment $accountReference"
                )
            ).execute()
            val body = if (pushRes.isSuccessful) pushRes.body() else null
            if (body != null && body.responseCode == "0") {
                StkPushOutcome.Success(
                    checkoutRequestId = body.checkoutRequestId.orEmpty(),
                    customerMessage = body.customerMessage
                        ?.takeIf { it.isNotBlank() }
                        ?: "Success. Enter your M-Pesa PIN to complete."
                )
            } else {
                StkPushOutcome.Failure(
                    body?.responseDescription?.takeIf { it.isNotBlank() }
                        ?: "stk_http_${pushRes.code()}"
                )
            }
        } catch (e: java.net.SocketTimeoutException) {
            StkPushOutcome.Failure("timeout")
        } catch (e: java.net.UnknownHostException) {
            StkPushOutcome.Failure("offline")
        } catch (e: Exception) {
            StkPushOutcome.Failure(e.message?.take(120) ?: "unexpected")
        }
    }
}
