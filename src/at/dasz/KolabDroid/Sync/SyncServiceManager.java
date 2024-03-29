/*
 * Copyright 2010 Arthur Zaczek <arthur@dasz.at>, dasz.at OG; All rights reserved.
 * Copyright 2010 David Schmitt <david@dasz.at>, dasz.at OG; All rights reserved.
 *
 *  This file is part of Kolab Sync for Android.

 *  Kolab Sync for Android is free software: you can redistribute it
 *  and/or modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation, either version 3 of
 *  the License, or (at your option) any later version.

 *  Kolab Sync for Android is distributed in the hope that it will be
 *  useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 *  of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  General Public License for more details.

 *  You should have received a copy of the GNU General Public License
 *  along with Kolab Sync for Android.
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package at.dasz.KolabDroid.Sync;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

/**
 * Part of the infrastructure to keep the background worker alive.
 */
public class SyncServiceManager extends BroadcastReceiver
{
	public static final String	TAG				= "SyncServiceManager";
	private static final int	INITIAL_DELAY	= 1000 * 60 * 10; // 10 minutes
	private static final int	SLEEP_TIME		= 1000 * 60 * 60 * 3; // 3 hours
	private static final String	CRON_TRIGGER	= "at.dasz.KolabDroid.Sync.CRON_TRIGGER";

	@Override
	public void onReceive(Context context, Intent intent)
	{
		if ("android.intent.action.BOOT_COMPLETED".equals(intent.getAction()))
		{
			initAlarmManager(context);
		}
		else if (CRON_TRIGGER.equals(intent.getAction()))
		{
			Log.i(TAG, "Starting synchronization from CRON");
			SyncService.startSync(context);
		}
		else
		{
			Log.e(TAG, "Received unexpected intent " + intent.toString());
		}
	}

	/**
	 * Initializes the Alarm Manager
	 * This Method can be called more than once.
	 * An existing repeating Intent will be replaced.
	 * */
	public static void initAlarmManager(Context context)
	{
		AlarmManager mgr = (AlarmManager) context
				.getSystemService(Context.ALARM_SERVICE);
		Intent i = new Intent(CRON_TRIGGER, null, context,
				SyncServiceManager.class);
		PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);

		// From Android Documentation: If there is already an alarm scheduled for the same IntentSender, it will first be canceled.
		mgr.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock
				.elapsedRealtime()
				+ INITIAL_DELAY, SLEEP_TIME, pi);
	}
}
