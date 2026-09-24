package com.movie.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/**
 * Java port of the private helpers at the bottom of DashboardScreen.kt:
 *   copyToClipboard(context, text, label)
 *   launchUrlInUlaa(context, url)
 */
public final class UiUtils {

    private UiUtils() {
    }

    public static void copyToClipboard(Context context, String text, String label) {
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        ClipData clip = ClipData.newPlainText(label, text == null ? "" : text);
        clipboard.setPrimaryClip(clip);
    }

    /** Tries the Ulaa browser sandbox first, then falls back to the default browser. */
    public static void launchUrlInUlaa(Context context, String url) {
        Intent ulaaIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        ulaaIntent.setPackage("com.zoho.ulaa");
        ulaaIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(ulaaIntent);
        } catch (Exception e) {
            Intent normalIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            normalIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(normalIntent);
                Toast.makeText(context,
                        "Ulaa Sandbox not present. Opened in default safety browser.",
                        Toast.LENGTH_LONG).show();
            } catch (Exception ex) {
                Toast.makeText(context, "No internet browser detected to unpack link!",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
}
