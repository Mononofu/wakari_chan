package org.furidamu.wakari_chan

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isEmpty
import com.ichi2.anki.api.AddContentApi
import java.io.File
import java.lang.Integer.min
import kotlin.math.roundToInt

const val TAG = "WakariChan"
const val ANKI_PERM_REQUEST = 3212

// A japanese term (possibly containing kanji) and its reading in hiragana / katakana.
data class Entry(val term: String, val reading: String)

class DictOverlayActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            val text = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT) ?: ""
            showTranslation(text)
        } else if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            showTranslation(text)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == ANKI_PERM_REQUEST && !grantResults.isEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(
                this,
                "Permission granted, please try to add to Anki again",
                Toast.LENGTH_LONG
            )
        } else {
            Toast.makeText(
                this,
                "Permission denied. Without this, it's not possible to add cards to anki.",
                Toast.LENGTH_LONG
            )
        }
    }

    fun showTranslation(text: String) {
        val db = openDb();
        val translation = translate(text, db);

        val scroll = ScrollView(this);
        scroll.addView(translation);
        scroll.minimumWidth = (resources.displayMetrics.widthPixels * 0.9).roundToInt();

        // Use the Builder class for convenient dialog construction
        val builder = AlertDialog.Builder(this)
        builder.setView(scroll)
            .setOnDismissListener(DialogInterface.OnDismissListener { _ -> finish(); })
        builder.show()
    }

    fun translate(text: String, dict: SQLiteDatabase): View {
        val kind = LinkedHashMap<Entry, String>();
        val translations = LinkedHashMap<Entry, String>();

        // Always try to query the exact string.
        addMatches(text, kind, translations, dict);

        // Also query all prefixes that contain kanji.
        for (len in min(text.length, 15) downTo 1) {
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
            japanese.setPadding(0, if (alertView.isEmpty()) 0 else 20, 0, 6);

            if (hasAnki()) {
                val ankiButton = Button(this, null, R.attr.borderlessButtonStyle)
                ankiButton.text = "[+ Anki]"
                ankiButton.setOnClickListener { _ -> addToAnki(entry, english); }
                ankiButton.setPadding(10, 0, 0, 0)
                japanese.addView(ankiButton)
            }

            alertView.addView(japanese)

            val translationView = TextView(this);
            translationView.text = "(${kind.get(entry)}) $english"
            translationView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.0f);

            alertView.addView(translationView);
        }

        return alertView;
    }

    fun addMatches(
        term: String,
        kind: LinkedHashMap<Entry, String>,
        translations: LinkedHashMap<Entry, String>,
        dict: SQLiteDatabase
    ) {
        val rawTranslations = LinkedHashMap<String, ArrayList<String>>();
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

            rawTranslations.computeIfAbsent(reading) { _ -> arrayListOf<String>() };
            rawTranslations[reading]!!.add(english);
            kind.putIfAbsent(entry, c.getString(kindCol));
        }

        val synonyms = LinkedHashMap<String, ArrayList<String>>();
        for ((reading, english) in rawTranslations) {
            // Combine all translations into a single string.
            val translations = if (english.size > 1) {
                english.mapIndexed { i, e -> "($i) $e"; }.joinToString(" ")
            } else {
                english[0]
            }

            // Group readings by their translation, to find synonyms.
            synonyms.computeIfAbsent(translations) { _ -> arrayListOf<String>() };
            synonyms[translations]!!.add(reading);
        }

        for ((translation, readings) in synonyms) {
            val allReadings = readings.joinToString(", ")
            val entry = Entry(term, allReadings)
            translations[entry] = translation
            kind[entry] = kind[Entry(term, readings.get(0))] ?: ""
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

    fun hasAnki(): Boolean {
        return AddContentApi.getAnkiDroidPackageName(this) != null;
    }

    fun addToAnki(entry: Entry, english: String) {
        if (ContextCompat.checkSelfPermission(
                this,
                AddContentApi.READ_WRITE_PERMISSION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(AddContentApi.READ_WRITE_PERMISSION),
                ANKI_PERM_REQUEST
            )
        }

        val anki = AnkiHelper(this);
        val deck = anki.deck("Japanese::Wakari-chan"); // Get deck name from settings.
        val model = anki.model("Wakari-chan", deck); // Get deck name from settings.

        if (anki.add(model, deck, arrayOf("k_${entry.term}", entry.term, entry.reading, english))) {
            Toast.makeText(this, "Added card to Anki Deck!", Toast.LENGTH_LONG)
        } else {
            Toast.makeText(this, "Failed to add card to Anki", Toast.LENGTH_LONG)
        }
    }
}

const val FRONT_TEMPLATE = """
<div class="ja">
{{#japanese}}{{japanese}}{{/japanese}}
</div>
"""

const val BACK_TEMPLATE = """
{{FrontSide}}

<hr id=answer>

<div class="reading">{{reading}}</div>
<div class="meaning">{{meaning}}</div>

<script>
var numRepetitions = 6;
function checkScroll() {
  numRepetitions -= 1;
  window.scrollTo(0, 0);
  if (numRepetitions > 0) {
    setTimeout(checkScroll, 50);
  }
}
document.addEventListener('DOMContentLoaded', checkScroll, false);

</script>
"""

const val CSS = """
.card {
 font-family: arial;
 font-size: 18px;
 text-align: center;
 color: black;
 background-color: white;
}

.ja {
    color: #fff;
    font-size: 6em;
    font-weight: normal;
    line-height: 1.3em;
    background-color: #a000f1;
    box-shadow: 0 0 10px rgba(0, 0, 0, 0.25) inset;
    text-shadow: 5px 5px 0 #9300dd;
}
"""

class AnkiHelper(val context: Context) {
    val api = AddContentApi(context)

    fun deck(name: String): Long {
        for ((id, deckName) in api.deckList) {
            if (name.equals(deckName, ignoreCase = true)) return id
        }
        return api.addNewDeck(name)
    }

    fun model(name: String, deck: Long): Long {
        for ((id, modelName) in api.modelList) {
            if (name.equals(modelName, ignoreCase = true)) return id
        }
        return api.addNewCustomModel(
            name, arrayOf("key", "japanese", "reading", "meaning"), arrayOf("card"),
            arrayOf(FRONT_TEMPLATE), arrayOf(BACK_TEMPLATE), CSS, deck, 0
        )
    }

    fun add(model: Long, deck: Long, fields: Array<String>): Boolean {
        return api.addNote(model, deck, fields, HashSet()) != null
    }
}
