package com.example.data

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {
    fun exportToPdf(context: Context, measurements: List<HealthMeasurement>, filterType: String) {
        val pdfDocument = PdfDocument()
        val titlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 20f
            color = Color.parseColor("#1978E5")
        }
        val subtitlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 12f
            color = Color.GRAY
        }
        val headerPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 11f
            color = Color.BLACK
        }
        val bodyPaint = Paint().apply {
            textSize = 10f
            color = Color.DKGRAY
        }
        val linePaint = Paint().apply {
            strokeWidth = 1f
            color = Color.LTGRAY
        }

        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1
        
        var myPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var myPage = pdfDocument.startPage(myPageInfo)
        var canvas = myPage.canvas

        var yPosition = 50f

        // Document Title
        canvas.drawText("Health Tracker - Medical Report", 40f, yPosition, titlePaint)
        yPosition += 25f

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val generatedOn = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        canvas.drawText("Generated on: $generatedOn", 40f, yPosition, subtitlePaint)
        yPosition += 15f
        canvas.drawText("Report Type: ${if (filterType == "ALL") "All Measurements" else filterType}", 40f, yPosition, subtitlePaint)
        yPosition += 35f

        // Table Headers
        canvas.drawText("Type", 40f, yPosition, headerPaint)
        canvas.drawText("Value", 160f, yPosition, headerPaint)
        canvas.drawText("Date & Time", 300f, yPosition, headerPaint)
        canvas.drawText("Notes", 440f, yPosition, headerPaint)
        yPosition += 10f
        canvas.drawLine(40f, yPosition, 550f, yPosition, linePaint)
        yPosition += 20f

        for (item in measurements) {
            // Check for page overflow
            if (yPosition > 800) {
                pdfDocument.finishPage(myPage)
                pageNumber++
                myPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                myPage = pdfDocument.startPage(myPageInfo)
                canvas = myPage.canvas
                yPosition = 50f
                
                // Draw headers on the new page
                canvas.drawText("Type", 40f, yPosition, headerPaint)
                canvas.drawText("Value", 160f, yPosition, headerPaint)
                canvas.drawText("Date & Time", 300f, yPosition, headerPaint)
                canvas.drawText("Notes", 440f, yPosition, headerPaint)
                yPosition += 10f
                canvas.drawLine(40f, yPosition, 550f, yPosition, linePaint)
                yPosition += 20f
            }

            val typeStr = when (item.type) {
                "GLUCOSE" -> "Blood Glucose"
                "PRESSURE" -> "Blood Pressure"
                "HEART_RATE" -> "Heart Rate"
                else -> item.type
            }

            val valueStr = when (item.type) {
                "GLUCOSE" -> {
                    val rate = item.value1
                    val classification = when {
                        rate in 0.8..2.0 -> " (Normal)"
                        rate in 2.1..2.6 -> " (Caution)"
                        rate in 2.7..3.5 -> " (High)"
                        rate > 3.5 -> " (Very High)"
                        else -> ""
                    }
                    String.format(Locale.US, "%.2f g/L%s", rate, classification)
                }
                "PRESSURE" -> "${item.value1.toInt()}/${item.value2?.toInt() ?: 0} mmHg"
                "HEART_RATE" -> "${item.value1.toInt()} bpm"
                else -> item.value1.toString()
            }

            val dateStr = dateFormat.format(Date(item.timestamp))
            val noteStr = item.notes ?: "-"

            canvas.drawText(typeStr, 40f, yPosition, bodyPaint)
            canvas.drawText(valueStr, 160f, yPosition, bodyPaint)
            canvas.drawText(dateStr, 300f, yPosition, bodyPaint)

            val truncatedNote = if (noteStr.length > 20) noteStr.substring(0, 17) + "..." else noteStr
            canvas.drawText(truncatedNote, 440f, yPosition, bodyPaint)

            yPosition += 25f
        }

        pdfDocument.finishPage(myPage)

        val cacheFile = File(context.cacheDir, "health_report.pdf")
        try {
            val fos = FileOutputStream(cacheFile)
            pdfDocument.writeTo(fos)
            pdfDocument.close()
            fos.close()

            val pdfUri = FileProvider.getUriForFile(context, "com.example.fileprovider", cacheFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, pdfUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share Health Report"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
