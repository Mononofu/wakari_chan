package org.furidamu.wakari_chan

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.w3c.dom.Text
import java.io.File

val TAG = "WakariChan";

class DictOverlayActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            val selectedText = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT) ?: ""
            Log.i("WakariChan", "$selectedText \n\n Text Length is: ${selectedText.length}");

            val db = openDb();
            val translation = translate(selectedText, db);


            // Use the Builder class for convenient dialog construction
            val builder = AlertDialog.Builder(this)
            builder.setView(translation)
                .setOnDismissListener(DialogInterface.OnDismissListener { dialog -> finish(); })
            builder.show()

        }
    }

    fun translate(text: String, dict: SQLiteDatabase): View {
        val c = dict.rawQuery(
            "SELECT reading, kind, english FROM translations where word = ? ORDER BY priority DESC LIMIT 100",
            arrayOf(text)
        )
        val readingCol = c.getColumnIndex("reading")
        val englishCol = c.getColumnIndex("english")
        val kindCol = c.getColumnIndex("kind");

        val kind = LinkedHashMap<String, String>();
        val translations = LinkedHashMap<String, ArrayList<String>>();
        while (c.moveToNext()) {
            val reading = c.getString(readingCol)
            val english = c.getString(englishCol)

            translations.computeIfAbsent(reading) { k -> arrayListOf<String>() };
            translations[reading]!!.add(english);
            kind.putIfAbsent(reading, c.getString(kindCol));
        }


        val alertView = LinearLayout(this);
        alertView.setOrientation(LinearLayout.VERTICAL);
        alertView.setPadding(20, 10, 10, 20);

        var i = 0;
        for ((reading, english) in translations) {
            val wordView = TextView(this);
            wordView.text = text;
            wordView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20.0f);
            wordView.setTextColor(0xff9fc5e8.toInt()); // light blue

            val readingView = TextView(this);
            readingView.text = "  $reading";
            readingView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20.0f);
            readingView.setTextColor(0xffb6d7a8.toInt()); // light green

            val japanese = LinearLayout(this);
            japanese.addView(wordView);
            japanese.addView(readingView);
            japanese.setPadding(0,  if (i == 0)  0 else 20 , 0, 6);

            alertView.addView(japanese);

            var text = "(${kind.get(reading)})";
            if (english.size > 1) {
                english.forEachIndexed { i, e ->
                   text += " ($i) $e";
                }
            } else {
                text += " ${english[0]}";
            }

            val translationView = TextView(this);
            translationView.text = text;
            translationView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.0f);

            alertView.addView(translationView);

            ++i;
        }

        return alertView;
    }

    fun openDb(): SQLiteDatabase {
        val dbFile = File(dbPath());
        if (!dbFile.exists()) {
            Log.i(TAG, "first execution, copying db");
            assets.open("dict.db").copyTo(dbFile.outputStream());
        }
        return SQLiteDatabase.openDatabase(
            dbPath(), null,
            SQLiteDatabase.OPEN_READONLY
        );
    }

    fun dbPath(): String {
        return cacheDir.absolutePath + "/dict.db";
    }
}
