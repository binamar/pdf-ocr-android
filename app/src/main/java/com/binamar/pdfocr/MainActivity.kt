package com.binamar.pdfocr

import android.app.Activity
import android.content.*
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : Activity() {
    private lateinit var out: EditText
    private lateinit var status: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 80, 32, 32) }
        fun btn(t: String, a: () -> Unit) = Button(this).apply {
            text = t; setOnClickListener { a() }
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val row = LinearLayout(this)
        row.addView(btn("اختيار PDF") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/pdf"), 1)
        })
        row.addView(btn("نسخ") {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("ocr", out.text))
            Toast.makeText(this, "تم النسخ", Toast.LENGTH_SHORT).show()
        })
        row.addView(btn("حفظ TXT") {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE, "ocr.txt"), 2)
        })
        status = TextView(this).apply { text = "اختر ملف PDF ممسوح ضوئيًا"; setPadding(0, 16, 0, 16) }
        out = EditText(this).apply {
            gravity = Gravity.TOP or Gravity.START
            textDirection = View.TEXT_DIRECTION_LTR
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        root.addView(row); root.addView(status); root.addView(out)
        setContentView(root)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        val uri = data?.data
        if (res != RESULT_OK || uri == null) return
        if (req == 1) ocr(uri) else if (req == 2) {
            contentResolver.openOutputStream(uri)?.use { it.write(out.text.toString().toByteArray()) }
            Toast.makeText(this, "تم الحفظ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ocr(uri: Uri) {
        out.setText("")
        Thread {
            try {
                val r = PdfRenderer(contentResolver.openFileDescriptor(uri, "r")!!)
                val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                for (i in 0 until r.pageCount) {
                    runOnUiThread { status.text = "صفحة ${i + 1} من ${r.pageCount}" }
                    val p = r.openPage(i)
                    val bmp = Bitmap.createBitmap((p.width * 2.5f).toInt(), (p.height * 2.5f).toInt(), Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    p.close()
                    val t = Tasks.await(rec.process(InputImage.fromBitmap(bmp, 0))).text
                    bmp.recycle()
                    runOnUiThread { out.append("--- صفحة ${i + 1} ---\n$t\n\n") }
                }
                r.close()
                runOnUiThread { status.text = "تم الانتهاء" }
            } catch (e: Exception) {
                runOnUiThread { status.text = "خطأ: ${e.message}" }
            }
        }.start()
    }
}
