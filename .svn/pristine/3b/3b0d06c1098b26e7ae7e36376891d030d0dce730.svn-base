package at.dasz.KolabDroid;

import org.acra.CrashReportingApplication;

import android.os.Bundle;

public class CrashReporterApplication extends CrashReportingApplication
{

	@Override
	public String getFormId()
	{
		return "dF9DbXo3ZmRTZ0swWWd6SThVNEpvbEE6MQ";
	}
	
	@Override
	public Bundle getCrashResources() {
	    Bundle result = new Bundle();
	    result.putInt(RES_NOTIF_TICKER_TEXT, R.string.crash_notif_ticker_text);
	    result.putInt(RES_NOTIF_TITLE, R.string.crash_notif_title);
	    result.putInt(RES_NOTIF_TEXT, R.string.crash_notif_text);
	    result.putInt(RES_NOTIF_ICON, android.R.drawable.stat_notify_error); // optional. default is a warning sign
	    result.putInt(RES_DIALOG_TEXT, R.string.crash_dialog_text);
	    result.putInt(RES_DIALOG_ICON, android.R.drawable.ic_dialog_info); //optional. default is a warning sign
	    result.putInt(RES_DIALOG_TITLE, R.string.crash_dialog_title); // optional. default is your application name 
	    result.putInt(RES_DIALOG_COMMENT_PROMPT, R.string.crash_dialog_comment_prompt); // optional. when defined, adds a user text field input with this text resource as a label
	    result.putInt(RES_DIALOG_OK_TOAST, R.string.crash_dialog_ok_toast); // optional. Displays a Toast when the user accepts to send a report ("Thank you !" for example)
	    return result;
	}

}
