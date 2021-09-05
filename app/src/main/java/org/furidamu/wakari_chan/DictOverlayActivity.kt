package org.furidamu.wakari_chan

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.util.Log
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
            builder.setMessage(translation)
                .setNeutralButton("Cancel",
                    DialogInterface.OnClickListener { dialog, id ->
                        finish();
                    })
                .setOnDismissListener(DialogInterface.OnDismissListener { dialog -> finish(); })
            builder.show()

        }
    }

    fun translate(text: String, dict: SQLiteDatabase): String {
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

        var translation = "";
        for ((reading, english) in translations) {
            translation += "$text ($reading)\n";
            translation += "(${kind.get(reading)})";
            if (english.size > 1) {
                english.forEachIndexed { i, e ->
                    translation += " ($i) $e";
                }
            } else {
                translation += " ${english[0]}";
            }
            translation += "\n";
        }

        return translation;
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