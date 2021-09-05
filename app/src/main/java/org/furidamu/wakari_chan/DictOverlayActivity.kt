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
import android.widget.ScrollView
import android.widget.TextView
import org.w3c.dom.Text
import java.io.File

val TAG = "WakariChan";

// A japanese term (possibly containing kanji) and its reading in hiragana / katakana.
data class Entry(val term: String, val reading: String);

class DictOverlayActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            val selectedText = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT) ?: ""
            Log.i("WakariChan", "$selectedText \n\n Text Length is: ${selectedText.length}");

            val db = openDb();
            val translation = translate(selectedText, db);

            val scroll = ScrollView(this);
            scroll.addView(translation);

            // Use the Builder class for convenient dialog construction
            val builder = AlertDialog.Builder(this)
            builder.setView(scroll)
                .setOnDismissListener(DialogInterface.OnDismissListener { dialog -> finish(); })
            builder.show()

        }
    }

    fun translate(text: String, dict: SQLiteDatabase): View {
        val kind = LinkedHashMap<Entry, String>();
        val translations = LinkedHashMap<Entry, ArrayList<String>>();

        // Always try to query the exact string.
        addMatches(text, kind, translations, dict);

        // Also query all prefixes that contain kanji.
        for (len in text.length downTo 1) {
            val s = text.substring(0, len);
            if (s.codePoints().anyMatch { c -> c in 0x4e00..0x9fbf }) {
                addMatches(s, kind, translations, dict);
            }
        }

        // Build the view to show the translations.
        val alertView = LinearLayout(this);
        alertView.orientation = LinearLayout.VERTICAL;
        alertView.setPadding(20, 10, 10, 20);

        if (translations.isEmpty()) {
            val msg = TextView(this);
            msg.text = "No results for query '$text'";
            msg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20.0f);
            alertView.addView(msg);
            return alertView;
        }

        // Add one "card" per reading.
        var i = 0;
        for ((entry, english) in translations) {
            val wordView = TextView(this);
            wordView.text = entry.term;
            wordView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20.0f);
            wordView.setTextColor(0xff9fc5e8.toInt()); // light blue

            val readingView = TextView(this);
            readingView.text = "  ${entry.reading}";
            readingView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20.0f);
            readingView.setTextColor(0xffb6d7a8.toInt()); // light green

            val japanese = LinearLayout(this);
            japanese.addView(wordView);
            japanese.addView(readingView);
            japanese.setPadding(0, if (i == 0) 0 else 20, 0, 6);

            alertView.addView(japanese);

            var explanation = "(${kind.get(entry)})";
            if (english.size > 1) {
                english.forEachIndexed { i, e ->
                    explanation += " ($i) $e";
                }
            } else {
                explanation += " ${english[0]}";
            }

            val translationView = TextView(this);
            translationView.text = explanation;
            translationView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.0f);

            alertView.addView(translationView);

            ++i;
        }

        return alertView;
    }

    fun addMatches(
        term: String,
        kind: LinkedHashMap<Entry, String>,
        translations: LinkedHashMap<Entry, ArrayList<String>>,
        dict: SQLiteDatabase
    ) {
        val c = dict.rawQuery(
            "SELECT reading, kind, english FROM translations where word = ? ORDER BY priority DESC LIMIT 100",
            arrayOf(term)
        )
        val readingCol = c.getColumnIndex("reading")
        val englishCol = c.getColumnIndex("english")
        val kindCol = c.getColumnIndex("kind");

        // Read match results from the database.
        while (c.moveToNext()) {
            val reading = c.getString(readingCol)
            val english = c.getString(englishCol)
            val entry = Entry(term, reading);

            translations.computeIfAbsent(entry) { k -> arrayListOf<String>() };
            translations[entry]!!.add(english);
            kind.putIfAbsent(entry, c.getString(kindCol));
        }
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
