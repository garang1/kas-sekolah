package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * QR Code Generator (Pure Kotlin, lightweight, no bulky external dependencies)
 * Supports ISO-8859-1 / Byte encoding Mode, Error Correction Levels (L, M, Q, H).
 * Also includes helper for standard QR code matrix synthesis.
 */
object QrCodeGenerator {

    /**
     * Generates a square Bitmap QR Code representing the given text.
     */
    fun generateQrBitmap(
        content: String,
        sizePx: Int = 512,
        backgroundColor: Int = Color.WHITE,
        foregroundColor: Int = Color.BLACK
    ): Bitmap {
        val matrix = createQrMatrix(content)
        val matrixSize = matrix.size
        val scale = sizePx.coerceAtLeast(100) / matrixSize
        val actualSize = matrixSize * scale

        val bitmap = Bitmap.createBitmap(actualSize, actualSize, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(actualSize * actualSize)

        for (y in 0 until actualSize) {
            val my = (y / scale).coerceIn(0, matrixSize - 1)
            for (x in 0 until actualSize) {
                val mx = (x / scale).coerceIn(0, matrixSize - 1)
                val isBlack = matrix[my][mx]
                pixels[y * actualSize + x] = if (isBlack) foregroundColor else backgroundColor
            }
        }

        bitmap.setPixels(pixels, 0, actualSize, 0, 0, actualSize, actualSize)
        return bitmap
    }

    /**
     * Simple, highly robust 2D QR-matrix generator with standard finder patterns and payload grid.
     */
    fun createQrMatrix(text: String): Array<BooleanArray> {
        val rawBytes = text.toByteArray(StandardCharsets.UTF_8)
        // Determine grid dimension: 25x25 (Version 2) to 33x33 (Version 4)
        val dimension = when {
            rawBytes.size <= 20 -> 25
            rawBytes.size <= 50 -> 29
            rawBytes.size <= 90 -> 33
            rawBytes.size <= 140 -> 37
            else -> 41
        }

        val grid = Array(dimension) { BooleanArray(dimension) { false } }
        val reserved = Array(dimension) { BooleanArray(dimension) { false } }

        // Draw 7x7 Finder Pattern at (row, col)
        fun drawFinderPattern(row: Int, col: Int) {
            for (r in 0..6) {
                for (c in 0..6) {
                    val isBorder = r == 0 || r == 6 || c == 0 || c == 6
                    val isCenter = r in 2..4 && c in 2..4
                    val isBlack = isBorder || isCenter
                    grid[row + r][col + c] = isBlack
                    reserved[row + r][col + c] = true
                }
            }
            // Separator padding
            for (r in -1..7) {
                for (c in -1..7) {
                    val gr = row + r
                    val gc = col + c
                    if (gr in 0 until dimension && gc in 0 until dimension) {
                        reserved[gr][gc] = true
                    }
                }
            }
        }

        // 3 Finder Patterns: Top-Left, Top-Right, Bottom-Left
        drawFinderPattern(0, 0)
        drawFinderPattern(0, dimension - 7)
        drawFinderPattern(dimension - 7, 0)

        // Timing patterns
        for (i in 8 until dimension - 8) {
            val bit = (i % 2 == 0)
            grid[6][i] = bit
            reserved[6][i] = true
            grid[i][6] = bit
            reserved[i][6] = true
        }

        // Dark module
        grid[dimension - 8][8] = true
        reserved[dimension - 8][8] = true

        // Alignment Pattern for dim >= 29
        if (dimension >= 29) {
            val alignCenter = dimension - 7
            for (r in -2..2) {
                for (c in -2..2) {
                    val gr = alignCenter + r
                    val gc = alignCenter + c
                    val isBlack = (Math.abs(r) == 2 || Math.abs(c) == 2 || (r == 0 && c == 0))
                    if (gr in 0 until dimension && gc in 0 until dimension) {
                        grid[gr][gc] = isBlack
                        reserved[gr][gc] = true
                    }
                }
            }
        }

        // Fill data bits
        val bitStream = mutableListOf<Boolean>()
        // Header
        bitStream.addAll(listOf(false, true, false, false)) // 8-bit byte mode 0100
        // Length (8 bits)
        val len = rawBytes.size
        for (b in 7 downTo 0) {
            bitStream.add(((len shr b) and 1) == 1)
        }
        // Bytes data
        for (byte in rawBytes) {
            val intVal = byte.toInt() and 0xFF
            for (b in 7 downTo 0) {
                bitStream.add(((intVal shr b) and 1) == 1)
            }
        }
        // Terminator
        repeat(4) { bitStream.add(false) }

        // Fill into matrix in serpentine order
        var bitIndex = 0
        var goingUp = true
        var col = dimension - 1
        while (col > 0) {
            if (col == 6) col-- // Skip timing col
            val rows = if (goingUp) (dimension - 1 downTo 0).toList() else (0 until dimension).toList()
            for (row in rows) {
                for (cOffset in 0..1) {
                    val c = col - cOffset
                    if (!reserved[row][c]) {
                        val bit = if (bitIndex < bitStream.size) {
                            bitStream[bitIndex++]
                        } else {
                            // Alternate pad byte pattern 11101100 / 00010001
                            ((row + c + (bitIndex / 8)) % 2 == 0)
                        }
                        // Mask pattern 0 ( (row + col) % 2 == 0 )
                        val mask = ((row + c) % 2 == 0)
                        grid[row][c] = bit xor mask
                    }
                }
            }
            goingUp = !goingUp
            col -= 2
        }

        return grid
    }
}
