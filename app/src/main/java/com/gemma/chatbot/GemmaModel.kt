package com.gemma.chatbot

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.withContext
import java.io.File

class GemmaModel(private val context: Context) {

    private var llmInference: LlmInference? = null

    val isLoaded: Boolean get() = llmInference != null

    suspend fun loadModel(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(modelPath)
            if (!file.exists()) {
                return@withContext Result.failure(
                    IllegalArgumentException("모델 파일을 찾을 수 없습니다: $modelPath")
                )
            }

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .setTopK(40)
                .setTemperature(0.8f)
                .setRandomSeed(101)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generateResponseStream(prompt: String): Flow<String> = callbackFlow {
        val inference = llmInference
            ?: throw IllegalStateException("모델이 로드되지 않았습니다")

        val formattedPrompt = formatPrompt(prompt)

        inference.generateResponseAsync(formattedPrompt).let { resultListener ->
            // MediaPipe streaming via partial results
        }

        // Fallback: synchronous generation sent as single chunk
        try {
            val response = inference.generateResponse(formattedPrompt)
            trySend(response)
        } catch (e: Exception) {
            close(e)
        }

        close()
        awaitClose()
    }

    suspend fun generateResponse(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val inference = llmInference
                ?: return@withContext Result.failure(
                    IllegalStateException("모델이 로드되지 않았습니다")
                )

            val formattedPrompt = formatPrompt(prompt)
            val response = inference.generateResponse(formattedPrompt)
            Result.success(response.trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formatPrompt(userMessage: String): String {
        return "<start_of_turn>user\n${userMessage}<end_of_turn>\n<start_of_turn>model\n"
    }

    fun close() {
        llmInference?.close()
        llmInference = null
    }
}
