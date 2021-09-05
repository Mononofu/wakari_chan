package org.furidamu.wakari_chan

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Log

class DictOverlayActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            val selectedText = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT) ?: ""
            Log.i("WakariChan", "$selectedText \n\n Text Length is: ${selectedText.length}");

            // Use the Builder class for convenient dialog construction
            val builder = AlertDialog.Builder(this)
            builder.setMessage("$selectedText")
                .setNeutralButton("Cancel",
                    DialogInterface.OnClickListener { dialog, id ->
                        finish();
                    })
                .setOnDismissListener(DialogInterface.OnDismissListener { dialog -> finish(); })
            builder.show()

        }
    }
}