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

package at.dasz.KolabDroid.Calendar;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import at.dasz.KolabDroid.Sync.SyncException;

public class CalendarProvider
{
	public static Uri CALENDAR_URI;
	public static Uri CALENDAR_ALERT_URI;
	public static Uri CALENDAR_REMINDER_URI;
	public static final String		_ID				= "_id";

	public static final String[]	projection		= new String[] { "_id",
			"calendar_id", "title", "allDay", "dtstart", "dtend",
			"description", "eventLocation", "visibility", "hasAlarm", "rrule", "exdate" };
	private ContentResolver			cr;

	public CalendarProvider(ContentResolver cr)
	{
		this.cr = cr;
		
		if(Build.VERSION.SDK_INT == 7) // android 2.1
		{
			CALENDAR_URI	= Uri.parse("content://calendar/events");
			CALENDAR_ALERT_URI = Uri.parse("content://calendar/calendar_alerts");
			CALENDAR_REMINDER_URI = Uri.parse("content://calendar/reminders");
		}
		else if(Build.VERSION.SDK_INT == 8) //android 2.2
		{
			CALENDAR_URI	= Uri.parse("content://com.android.calendar/events");
			CALENDAR_ALERT_URI = Uri.parse("content://com.android.calendar/calendar_alerts");
			CALENDAR_REMINDER_URI = Uri.parse("content://com.android.calendar/reminders");
		}
	}

	public CalendarEntry loadCalendarEntry(int id, String uid) throws SyncException
	{
		if (id == 0) return null;
		Uri uri = ContentUris.withAppendedId(CALENDAR_URI, id);
		Cursor cur = cr.query(uri, projection, null, null, null);
		if (cur == null) throw new SyncException(Integer.toString(id), "cr.query returned null");
		try
		{
			if (cur.moveToFirst()) { return loadCalendarEntry(cur, uid); }
			return null;
		}
		finally
		{
			cur.close();
		}
	}

	public CalendarEntry loadCalendarEntry(Cursor cur, String uid)
	{
		CalendarEntry e = new CalendarEntry();
		e.setId(cur.getInt(0));
		e.setUid(uid);
		e.setCalendar_id(cur.getInt(1));
		e.setTitle(cur.getString(2));
		e.setAllDay(cur.getInt(3) != 0);

		Time start = new Time();
		start.set(cur.getLong(4));
		e.setDtstart(start);

		Time end = new Time();
		end.set(cur.getLong(5));
		e.setDtend(end);

		e.setDescription(cur.getString(6));
		e.setEventLocation(cur.getString(7));
		e.setVisibility(cur.getInt(8));
		e.setHasAlarm(cur.getInt(9));
		
		if(e.getHasAlarm() == 1)
		{
			//for now, just use the reminder that is the farthest away from the appointment
			//Cursor alertCur = cr.query(CALENDAR_ALERT_URI, null, "event_id=?", new String[]{Integer.toString(e.getId())}, "minutes DESC");
			
			//use reminder which include snoozed events (i.e. moving the alarm towards the future) 
			Cursor alertCur = cr.query(CALENDAR_REMINDER_URI, null, "event_id=?", new String[]{Integer.toString(e.getId())}, "minutes DESC");
			if(alertCur != null && alertCur.moveToFirst())
			{
				int colIdx = alertCur.getColumnIndex("minutes");
				e.setReminderTime(alertCur.getInt(colIdx));
			}			
		}
		
		e.setrRule(cur.getString(10));

		return e;
	}

	public void delete(CalendarEntry e)
	{
		//if (e.getId() == 0) return;
		delete(e.getId());
		//Uri uri = ContentUris.withAppendedId(CALENDAR_URI, e.getId());
		//cr.delete(uri, null, null);
	}

	public void delete(int id)
	{
		if (id == 0) return;
		Uri uri = ContentUris.withAppendedId(CALENDAR_URI, id);
		cr.delete(uri, null, null);
		
		//delete matching alerts; trigger will do this for us		
		//cr.delete(CALENDAR_ALERT_URI, "event_id=?", new String[]{Integer.toString(id)});		
	}

	public void save(CalendarEntry e) throws SyncException
	{
		ContentValues values = new ContentValues();
		if (e == null) {
			Log.e("CalendarProvider.save()", "e == null ; cannot save calendar entry");
			return;
		}
		if (e.getDtstart() == null) {
			Log.e("CalendarProvider.save()", "e.getDtstart() == null ; cannot save calendar entry with id=" + e.getCalendar_id());
			return;
		}
		if (e.getDtend() == null) {
			Log.e("CalendarProvider.save()", "e.getDtend() == null ; cannot save calendar entry with id=" + e.getCalendar_id());
			return;
		}
		long start = e.getDtstart().toMillis(true);
		long end = e.getDtend().toMillis(true);

		String duration;
		if (e.getAllDay())
		{
			long days = (end - start + DateUtils.DAY_IN_MILLIS - 1)
					/ DateUtils.DAY_IN_MILLIS;
			duration = "P" + days + "D";
		}
		else
		{
			long seconds = (end - start) / DateUtils.SECOND_IN_MILLIS;
			duration = "P" + seconds + "S";
		}

		values.put("calendar_id", e.getCalendar_id());
		values.put("title", e.getTitle());
		values.put("allDay", e.getAllDay() ? 1 : 0);
		values.put("dtstart", start);
		values.put("dtend", end);
		values.put("duration", duration);
		values.put("description", e.getDescription());
		values.put("eventLocation", e.getEventLocation());
		values.put("visibility", e.getVisibility());
		values.put("hasAlarm", e.getHasAlarm());
		values.put("rrule", e.getrRule());
		values.put("exdate", e.getexDate());

		if (e.getId() == 0)
		{
			Uri newUri = cr.insert(CALENDAR_URI, values);
			if(newUri == null) throw new SyncException(e.getTitle(), "Unable to create new Calender Entry, provider returned null uri.");
			e.setId((int) ContentUris.parseId(newUri));
		}
		else
		{
			Uri uri = ContentUris.withAppendedId(CALENDAR_URI, e.getId());
			cr.update(uri, values, null, null);
		}
		
		if(e.getHasAlarm() == 1)
		{
			//delete existing alerts to replace them with the ones from the synchronisation		
			cr.delete(CALENDAR_ALERT_URI, "event_id=?", new String[]{Integer.toString(e.getId())});
			
			//remove reminder entry
			cr.delete(CALENDAR_REMINDER_URI, "event_id=?", new String[]{Integer.toString(e.getId())});
			
			//create reminder
			ContentValues reminderValues = new ContentValues();			
			reminderValues.put("event_id", e.getId());
			reminderValues.put("method", 1);			
			reminderValues.put("minutes", e.getReminderTime());
			
			cr.insert(CALENDAR_REMINDER_URI, reminderValues);
			
			//create alert
			ContentValues alertValues = new ContentValues();
			
			alertValues.put("event_id", e.getId());
			alertValues.put("begin", start);
			alertValues.put("end", end);
			alertValues.put("alarmTime", (start - e.getReminderTime()*60000));
			alertValues.put("state", 0);
			//alertValues.put("creationTime", value);
			alertValues.put("minutes", e.getReminderTime());
			
			cr.insert(CALENDAR_ALERT_URI, alertValues);			
		}
	}
}
