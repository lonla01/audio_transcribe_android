package com.voicetext.transcribe

import android.content.Context
import android.text.format.DateUtils

/** Short date + time in the user's locale, e.g. "3 Oct, 14:32". */
fun formatDate(context: Context, millis: Long): String =
    DateUtils.formatDateTime(
        context,
        millis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
    )
