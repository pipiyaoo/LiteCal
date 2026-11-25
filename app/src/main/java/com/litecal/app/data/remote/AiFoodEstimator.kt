package com.litecal.app.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.litecal.app.domain.model.AiConfig
import com.litecal.app.domain.model.AiMealEstimateResponse
import com.litecal.app.domain.model.AiProviderProtocol
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 双协议直连多模态饮食营养估算器（无第三方中间服务器，手机端直连模型端点）：
 * 1. OpenAI 兼容格式 (支持通义千问 Qwen-VL、豆包、硅基流动、DeepSeek、OpenRouter、OpenAI 等)
 * 2. Google Gemini 原生 generateContent 接口
 */
class AiFoodEstimator(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val systemInstruction = """
        你是一位精通《中国食物成分表》及中式餐饮、外卖、便利店食物热量的注册营养师。
        请根据用户提供的食物照片和/或文字描述，拆解出其中包含的每项食物，估算其常见合理份量、可食部克重(g)、总热量(kcal)及三大宏量营养素(蛋白质g、碳水g、脂肪g)。
        注意：中餐炒菜和外卖需合理计入烹饪油脂热量。
        你必须且只能输出严格的 JSON 对象（不要输出 Markdown 代码块以外的多余寒暄），JSON 格式如下：
        {
          "summary": "简短一句话总结这餐整体热量与蛋白质量级",
          "items": [
            {
              "name": "食物名称",
              "portionDesc": "生活化份量描述，如 半碗(约120g) 或 去皮鸡腿1个",
              "estimatedGrams": 120,
              "kcal": 180,
              "proteinG": 18.5,
              "carbsG": 12.0,
              "fatG": 6.5
            }
          ],
          "confidenceNote": "如有隐藏油酱或分量不确定处，给出15字以内微调建议"
        }
    """.trimIndent()

    suspend fun estimateMeal(
        config: AiConfig,
        userText: String,
        imageUri: Uri?
    ): Result<AiMealEstimateResponse> = withContext(Dispatchers.IO) {
        runCatching {
            if (config.apiKey.isBlank()) {
                throw IllegalStateException("请先在「词库与设置 → AI 接口配置」中填写 API Key")
            }
            if (userText.isBlank() && imageUri == null) {
                throw IllegalArgumentException("请输入一句话饮食描述或选择一张餐食照片")
            }

            val base64Jpeg = imageUri?.let { encodeDownscaledJpegBase64(it) }
            val promptText = buildString {
                if (userText.isNotBlank()) {
                    append("用户饮食描述：").append(userText.trim()).append("\n")
                } else {
                    append("请识别照片中的全部餐食并估算营养热量。\n")
                }
                append("请严格按指定 JSON 结构返回。")
            }

            val rawModelReply = when (config.protocol) {
                AiProviderProtocol.OPENAI_COMPATIBLE -> callOpenAiCompatible(config, promptText, base64Jpeg)
                AiProviderProtocol.GEMINI_NATIVE -> callGeminiNative(config, promptText, base64Jpeg)
            }

            val cleanJson = extractJsonObject(rawModelReply)
            val parsed = json.decodeFromString<AiMealEstimateResponse>(cleanJson)
            if (parsed.items.isEmpty()) {
                throw IllegalStateException("AI 未能从描述或图片中识别出具体食物条目，请补充简短说明重试")
            }
            parsed
        }
    }

    suspend fun testConnection(config: AiConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (config.apiKey.isBlank()) {
                throw IllegalStateException("API Key 不能为空")
            }
            val pingPrompt = "请直接回复严格的 JSON: {\"summary\":\"连接成功\",\"items\":[{\"name\":\"水煮鸡蛋\",\"portionDesc\":\"1个\",\"estimatedGrams\":55,\"kcal\":79,\"proteinG\":7.3,\"carbsG\":1.5,\"fatG\":4.8}],\"confidenceNote\":\"ok\"}"
            val reply = when (config.protocol) {
                AiProviderProtocol.OPENAI_COMPATIBLE -> callOpenAiCompatible(config, pingPrompt, null)
                AiProviderProtocol.GEMINI_NATIVE -> callGeminiNative(config, pingPrompt, null)
            }
            val parsed = json.decodeFromString<AiMealEstimateResponse>(extractJsonObject(reply))
            "连通正常 (${config.modelName}) · 示例解析：${parsed.items.firstOrNull()?.name ?: "OK"}"
        }
    }

    private fun callOpenAiCompatible(
        config: AiConfig,
        userPrompt: String,
        base64Jpeg: String?
    ): String {
        val normalizedBase = config.baseUrl.trim().trimEnd('/')
            .removeSuffix("/chat/completions")
        val endpoint = "$normalizedBase/chat/completions"

        val payload = buildJsonObject {
            put("model", config.modelName.trim())
            put("temperature", 0.2)
            put(
                "messages",
                buildJsonArray {
                    addJsonObject {
                        put("role", "system")
                        put("content", systemInstruction)
                    }
                    addJsonObject {
                        put("role", "user")
                        if (base64Jpeg != null) {
                            put(
                                "content",
                                buildJsonArray {
                                    addJsonObject {
                                        put("type", "text")
                                        put("text", userPrompt)
                                    }
                                    addJsonObject {
                                        put("type", "image_url")
                                        putJsonObject("image_url") {
                                            put("url", "data:image/jpeg;base64,$base64Jpeg")
                                        }
                                    }
                                }
                            )
                        } else {
                            put("content", userPrompt)
                        }
                    }
                }
            )
        }

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer ${config.apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("接口请求失败 (HTTP ${response.code}): ${bodyStr.take(240)}")
            }
            val root = json.parseToJsonElement(bodyStr).jsonObject
            val contentElement = root["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("message")
                ?.jsonObject
                ?.get("content")
                ?: throw IllegalStateException("未找到 choices[0].message.content 字段")

            return contentElement.jsonPrimitive.contentOrNull
                ?: contentElement.toString()
        }
    }

    private fun callGeminiNative(
        config: AiConfig,
        userPrompt: String,
        base64Jpeg: String?
    ): String {
        val host = config.baseUrl.trim().trimEnd('/').ifBlank {
            "https://generativelanguage.googleapis.com"
        }
        val model = config.modelName.trim().removePrefix("models/")
        val endpoint = "$host/v1beta/models/$model:generateContent"

        val payload = buildJsonObject {
            putJsonObject("systemInstruction") {
                put(
                    "parts",
                    buildJsonArray {
                        addJsonObject { put("text", systemInstruction) }
                    }
                )
            }
            put(
                "contents",
                buildJsonArray {
                    addJsonObject {
                        put("role", "user")
                        put(
                            "parts",
                            buildJsonArray {
                                addJsonObject { put("text", userPrompt) }
                                if (base64Jpeg != null) {
                                    addJsonObject {
                                        putJsonObject("inlineData") {
                                            put("mimeType", "image/jpeg")
                                            put("data", base64Jpeg)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            )
            putJsonObject("generationConfig") {
                put("temperature", 0.2)
                put("responseMimeType", "application/json")
            }
        }

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("x-goog-api-key", config.apiKey.trim())
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Gemini 请求失败 (HTTP ${response.code}): ${bodyStr.take(240)}")
            }
            val root: JsonObject = json.parseToJsonElement(bodyStr).jsonObject
            return root["candidates"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("content")
                ?.jsonObject
                ?.get("parts")
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("text")
                ?.jsonPrimitive
                ?.contentOrNull
                ?: throw IllegalStateException("Gemini 响应为空: ${bodyStr.take(200)}")
        }
    }

    private fun encodeDownscaledJpegBase64(uri: Uri): String {
        val inputBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("无法读取所选照片")

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size, boundsOpts)

        val maxDim = max(boundsOpts.outWidth, boundsOpts.outHeight)
        var sampleSize = 1
        while (maxDim / sampleSize > 1280) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size, decodeOpts)
            ?: throw IllegalArgumentException("照片解码失败")

        val targetMax = 1024f
        val longer = max(decoded.width, decoded.height).toFloat()
        val finalBitmap: Bitmap = if (longer > targetMax) {
            val ratio = targetMax / longer
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * ratio).toInt().coerceAtLeast(1),
                (decoded.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            decoded
        }

        val out = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun extractJsonObject(raw: String): String {
        val trimmed = raw.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1)
        }
        throw IllegalStateException("模型未返回有效 JSON 格式: ${trimmed.take(180)}")
    }
}
