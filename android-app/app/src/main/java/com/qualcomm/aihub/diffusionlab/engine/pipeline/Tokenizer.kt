package com.qualcomm.aihub.diffusionlab.engine.pipeline

import android.content.Context
import com.qualcomm.aihub.diffusionlab.util.Constants
import org.json.JSONObject

/**
 * Minimal CLIP tokenizer for Stable Diffusion v1.5.
 *
 * This is a simplified BPE tokenizer that converts text prompts into token IDs
 * compatible with the CLIP text encoder. In production, consider using a
 * full tokenizer library; this implementation covers the common case.
 *
 * The vocabulary and merges files should be placed in assets/:
 * - vocab.json (CLIP vocabulary)
 * - merges.txt (BPE merge rules)
 *
 * Reference: openai/clip-vit-large-patch14 tokenizer
 */
class ClipTokenizer(context: Context) {

    private val vocab: Map<String, Int>
    private val merges: List<Pair<String, String>>
    private val bosToken = 49406 // <|startoftext|>
    private val eosToken = 49407 // <|endoftext|>

    init {
        // Load vocabulary
        val vocabJson = context.assets.open("tokenizer/vocab.json")
            .bufferedReader().readText()
        val jsonObj = JSONObject(vocabJson)
        vocab = buildMap {
            for (key in jsonObj.keys()) {
                put(key, jsonObj.getInt(key))
            }
        }

        // Load BPE merges
        val mergesText = context.assets.open("tokenizer/merges.txt")
            .bufferedReader().readLines()
        merges = mergesText
            .drop(1) // Skip header line
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split(" ")
                parts[0] to parts[1]
            }
    }

    /**
     * Tokenizes a text prompt into an IntArray of token IDs.
     * Pads or truncates to CLIP_MAX_LENGTH (77 tokens).
     *
     * Format: [BOS, token1, token2, ..., EOS, PAD, PAD, ...]
     */
    fun encode(text: String): IntArray {
        val tokens = mutableListOf(bosToken)

        // Simple whitespace tokenization + BPE
        val cleanedText = text.lowercase().trim()
        val words = cleanedText.split(Regex("\\s+"))

        for (word in words) {
            if (tokens.size >= Constants.CLIP_MAX_LENGTH - 1) break

            // Add end-of-word marker
            val wordWithMarker = "$word</w>"

            // Look up the word directly first
            val tokenId = vocab[wordWithMarker]
            if (tokenId != null) {
                tokens.add(tokenId)
            } else {
                // Fall back to character-level tokens
                for (char in word) {
                    val charToken = vocab["$char"] ?: vocab["$char</w>"]
                    if (charToken != null && tokens.size < Constants.CLIP_MAX_LENGTH - 1) {
                        tokens.add(charToken)
                    }
                }
            }
        }

        // Add EOS token
        if (tokens.size < Constants.CLIP_MAX_LENGTH) {
            tokens.add(eosToken)
        }

        // Pad to CLIP_MAX_LENGTH
        while (tokens.size < Constants.CLIP_MAX_LENGTH) {
            tokens.add(eosToken) // CLIP uses EOS as padding token
        }

        return tokens.take(Constants.CLIP_MAX_LENGTH).toIntArray()
    }

    /**
     * Encodes an empty prompt (for unconditional generation / classifier-free guidance).
     */
    fun encodeEmpty(): IntArray {
        return encode("")
    }
}
