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

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;

import javax.mail.MessagingException;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.text.format.Time;
import android.util.Log;
import at.dasz.KolabDroid.Utils;
import at.dasz.KolabDroid.Provider.LocalCacheProvider;
import at.dasz.KolabDroid.Settings.Settings;
import at.dasz.KolabDroid.Sync.AbstractSyncHandler;
import at.dasz.KolabDroid.Sync.CacheEntry;
import at.dasz.KolabDroid.Sync.SyncContext;
import at.dasz.KolabDroid.Sync.SyncException;

public class SyncCalendarHandler extends AbstractSyncHandler
{
	private final String					defaultFolderName;
	private final LocalCacheProvider		cacheProvider;

	private final CalendarProvider			calendarProvider;
	private final ContentResolver			cr;

	private HashMap<Integer, CalendarEntry>	localItemsCache;

	public SyncCalendarHandler(Context context)
	{
		super(context);
		Settings s = new Settings(context);
		settings = s;
		defaultFolderName = s.getCalendarFolder();
		cacheProvider = new LocalCacheProvider.CalendarCacheProvider(context);
		calendarProvider = new CalendarProvider(context.getContentResolver());
		cr = context.getContentResolver();
		status.setTask("Calendar");
	}

	public String getDefaultFolderName()
	{
		return defaultFolderName;
	}

	public boolean shouldProcess()
	{
		boolean hasFolder = (defaultFolderName != null && !""
				.equals(defaultFolderName));
		return settings.getSyncCalendar() && hasFolder;
	}

	public LocalCacheProvider getLocalCacheProvider()
	{
		return cacheProvider;
	}

	public Set<Integer> getAllLocalItemsIDs()
	{
		return localItemsCache.keySet();
	}

	public void fetchAllLocalItems()
	{
		localItemsCache = new HashMap<Integer, CalendarEntry>();
		Cursor cur = cr.query(CalendarProvider.CALENDAR_URI,
				CalendarProvider.projection, null, null, null);
		try
		{
			while (cur.moveToNext())
			{
				CalendarEntry e = calendarProvider.loadCalendarEntry(cur,
						"empty");
				localItemsCache.put(e.getId(), e);
			}
		}
		finally
		{
			cur.close();
		}
	}

	public int getIdColumnIndex(Cursor c)
	{
		return c.getColumnIndex(CalendarProvider._ID);
	}

	public Cursor getAllLocalItemsCursor()
	{
		return cr.query(CalendarProvider.CALENDAR_URI,
				new String[] { CalendarProvider._ID }, null, null, null);
	}

	@Override
	public void deleteLocalItem(int localId)
	{
		calendarProvider.delete(localId);
	}

	@Override
	protected String getMimeType()
	{
		return "application/x-vnd.kolab.event";
	}

	public boolean hasLocalItem(SyncContext sync) throws SyncException
	{
		return getLocalItem(sync) != null;
	}

	public boolean hasLocalChanges(SyncContext sync) throws SyncException
	{
		CacheEntry e = sync.getCacheEntry();
		CalendarEntry cal = getLocalItem(sync);
		String entryHash = e.getLocalHash();
		String calHash = cal != null ? cal.getLocalHash() : "";
		return !entryHash.equals(calHash);
	}

	@Override
	protected void updateLocalItemFromServer(SyncContext sync, Document xml)
			throws SyncException
	{
		CalendarEntry cal = (CalendarEntry) sync.getLocalItem();
		if (cal == null)
		{
			cal = new CalendarEntry();
		}
		Element root = xml.getDocumentElement();

		cal.setUid(Utils.getXmlElementString(root, "uid"));
		cal.setDescription(Utils.getXmlElementString(root, "body"));
		cal.setTitle(Utils.getXmlElementString(root, "summary"));
		cal.setEventLocation(Utils.getXmlElementString(root, "location"));

		int reminderTime = Utils.getXmlElementInt(root, "alarm", -1);
		if (reminderTime > -1)
		{
			cal.setHasAlarm(1);
			cal.setReminderTime(reminderTime);
		}

		try
		{
			Time start = Utils.getXmlElementTime(root, "start-date");
			Time end = Utils.getXmlElementTime(root, "end-date");

			cal.setAllDay(start.hour == 0 && end.hour == 0 && start.minute == 0
					&& end.minute == 0 && start.second == 0 && end.second == 0);

			cal.setDtstart(start);

			// allday events of length n days have dtend == dtstart + (n-1) in
			// kolab,
			// android calendar has dtend == dtstart + n.
			if (cal.getAllDay())
			{
				end.monthDay += 1;
				end.toMillis(true);
			}
			cal.setDtend(end);

			Element recurrence = Utils.getXmlElement(root, "recurrence");
			if (recurrence != null)
			{
				StringBuilder sb = new StringBuilder();
				String cycle = Utils.getXmlAttributeString(recurrence, "cycle")
						.toUpperCase();
				sb.append("FREQ=");
				sb.append(cycle);

				sb.append(";WKST=");
				int firstDayOfWeek = Calendar.getInstance().getFirstDayOfWeek();
				switch (firstDayOfWeek)
				{
				case Calendar.MONDAY:
					sb.append("MO");
					break;
				case Calendar.TUESDAY:
					sb.append("TU");
					break;
				case Calendar.WEDNESDAY:
					sb.append("WE");
					break;
				case Calendar.THURSDAY:
					sb.append("TH");
					break;
				case Calendar.FRIDAY:
					sb.append("FR");
					break;
				case Calendar.SATURDAY:
					sb.append("SA");
					break;
				case Calendar.SUNDAY:
					sb.append("SU");
					break;
				}

				int daynumber = Utils.getXmlElementInt(recurrence, "daynumber",
						0);
				NodeList days = Utils.getXmlElements(recurrence, "day");
				int daysLength = days.getLength();
				if (daysLength > 0)
				{
					sb.append(";BYDAY=");
					for (int i = 0; i < daysLength; i++)
					{
						if (daynumber > 1) sb.append(daynumber);

						Element day = (Element) days.item(i);
						String d = Utils.getXmlElementString(day);
						sb.append(CalendarEntry.kolabWeekDayToWeekDay(d));

						if ((i + 1) < daysLength) sb.append(",");
					}

					if (CalendarEntry.YEARLY.equals(cycle))
					{
						String month = Utils.getXmlElementString(recurrence,
								"month");
						if (month != null && !"".equals(month))
						{
							sb.append(";BYMONTH=");
							sb.append(CalendarEntry.kolabMonthToMonth(month));
						}
					}
				}
				else if (daynumber != 0 && CalendarEntry.MONTHLY.equals(cycle))
				{
					sb.append(";BYMONTHDAY=" + daynumber);
				}

				int interval = Utils
						.getXmlElementInt(recurrence, "interval", 0);
				if (interval > 1)
				{
					sb.append(";INTERVAL=" + interval);
				}

				Element range = Utils.getXmlElement(recurrence, "range");
				if (range != null)
				{
					String rangestr = Utils.getXmlElementString(range);
					String rangeType = Utils.getXmlAttributeString(range,
							"type");
					Log.d("sync", "rangeType=" + rangeType + "   value="
							+ rangestr);
					if ("date".equals(rangeType))
					{
						Time rangeEndDate = new Time("UTC");
						rangeEndDate.parse3339(rangestr);
						rangeEndDate.allDay = false;
						// jump to last second of current day
						// (== next day minus 1 second)
						// otherwise daily recurrences last one day too much
						rangeEndDate.monthDay += 1;
						rangeEndDate.second -= 1;
						rangeEndDate.normalize(true);
						sb.append(";UNTIL=" + rangeEndDate.format2445());
					}
					else
					{
						Log.e("sync", "rangeType=" + rangeType
								+ " is not implemented!");
					}
				}

				String exclusionDateString = new String();
				NodeList exclusions = Utils.getXmlElements(recurrence,
						"exclusion");
				int exclusionNumber = exclusions.getLength();
				for (int i = 0; i < exclusionNumber; i++)
				{
					Element exclusion = (Element) exclusions.item(i);
					String day = Utils.getXmlElementString(exclusion);
					Time exclusionDate = new Time("UTC");
					exclusionDate.parse3339(day);
					exclusionDate.allDay = false;
					if (!(start.allDay))
					{
						// if event is not a whole-day-event then use specific
						// date+time as exception instead of date-only.
						exclusionDate.hour = start.hour;
						exclusionDate.minute = start.minute;
						exclusionDate.second = start.second;
					}
					if (exclusionDateString.length() > 0)
					{
						exclusionDateString = exclusionDateString + ",";
					}
					exclusionDateString = exclusionDateString
							+ exclusionDate.format2445();
				}
				if (exclusionDateString.length() > 0)
				{
					cal.setexDate(exclusionDateString);
				}

				cal.setrRule(sb.toString());
				Log.d("sync", "RRule = " + cal.getrRule());
				Log.d("sync", "ExDate = " + cal.getexDate());
			}

			sync.setCacheEntry(saveCalender(cal));
		}
		catch (Exception ex)
		{
			throw new SyncException(cal.getTitle(),
					"Unable to parse server item: " + ex.getMessage());
		}
	}

	private CacheEntry saveCalender(CalendarEntry cal) throws SyncException
	{
		cal.setCalendar_id(1);
		calendarProvider.save(cal);
		CacheEntry result = new CacheEntry();
		result.setLocalId(cal.getId());
		result.setLocalHash(cal.getLocalHash());
		result.setRemoteId(cal.getUid());

		localItemsCache.put(cal.getId(), cal);
		return result;
	}

	private String getNewUid()
	{
		// Create Application and Type specific id
		// kd == Kolab Droid, ev == event
		return "kd-ev-" + UUID.randomUUID().toString();
	}

	@Override
	protected void updateServerItemFromLocal(SyncContext sync, Document xml)
			throws SyncException
	{
		CalendarEntry source = getLocalItem(sync);
		CacheEntry entry = sync.getCacheEntry();

		entry.setLocalHash(source.getLocalHash());
		final Date lastChanged = new Date();
		entry.setRemoteChangedDate(lastChanged);

		writeXml(xml, source, lastChanged);

	}

	private final static java.util.regex.Pattern	regFREQ				= java.util.regex.Pattern
																				.compile("FREQ=(\\w*);.*");
	// private final static java.util.regex.Pattern regWKST =
	// java.util.regex.Pattern
	// .compile(";WKST=(\\w*)");
	private final static java.util.regex.Pattern	regUNTIL			= java.util.regex.Pattern
																				.compile(".*;UNTIL=(\\d{8})T(\\d*)");
	private final static java.util.regex.Pattern	regBYDAY			= java.util.regex.Pattern
																				.compile(".*;BYDAY=([\\+\\-\\,0-9A-Z]*);?.*");
	private final static java.util.regex.Pattern	regBYDAYSubPattern	= java.util.regex.Pattern
																				.compile("(?:([+-]?)([\\d]*)([A-Z]{2}),?)");
	private final static java.util.regex.Pattern	regINTERVAL			= java.util.regex.Pattern
																				.compile(".*;INTERVAL=(\\d*);?.*");
	private final static java.util.regex.Pattern	regBYMONTHDAY		= java.util.regex.Pattern
																				.compile(".*;BYMONTHDAY=(\\d*);?.*");
	private final static java.util.regex.Pattern	regBYMONTH			= java.util.regex.Pattern
																				.compile(".*;BYMONTH=(\\d*);?.*");

	private void writeXml(Document xml, CalendarEntry source,
			final Date lastChanged)
	{
		Element root = xml.getDocumentElement();

		Utils.setXmlElementValue(xml, root, "uid", source.getUid());
		Utils.setXmlElementValue(xml, root, "body", source.getDescription());
		Utils.setXmlElementValue(xml, root, "last-modification-date",
				Utils.toUtc(lastChanged));
		Utils.setXmlElementValue(xml, root, "summary", source.getTitle());
		Utils.setXmlElementValue(xml, root, "location",
				source.getEventLocation());

		// times have to be in UTC, according to
		// http://www.kolab.org/doc/kolabformat-2.0rc7-html/x123.html
		Time startTime = source.getDtstart();
		startTime.switchTimezone("UTC");

		Utils.setXmlElementValue(xml, root, "start-date",
				startTime.format3339(source.getAllDay()));

		if (source.getHasAlarm() != 0)
		{
			Utils.setXmlElementValue(xml, root, "alarm",
					Integer.toString(source.getReminderTime()));
		}

		Time endTime = source.getDtend();
		endTime.switchTimezone("UTC");

		if (source.getAllDay())
		{
			// whole day events (1 day) do start and end on the same day in
			// kolab XML.
			// so subtract one day to get proper XML.
			endTime.monthDay -= 1;
			endTime.normalize(true);
		}
		Utils.setXmlElementValue(xml, root, "end-date",
				endTime.format3339(source.getAllDay()));

		String rrule = source.getrRule();
		if (rrule != null && !"".equals(rrule))
		{
			Element recurrence = Utils.getOrCreateXmlElement(xml, root,
					"recurrence");
			Utils.deleteXmlElements(recurrence, "day");

			Matcher result;

			// /////////// Frequency /////////////
			result = regFREQ.matcher(rrule);
			String cycle = "";
			if (result.matches())
			{
				cycle = result.group(1);
				Utils.setXmlAttributeValue(xml, recurrence, "cycle",
						cycle.toLowerCase());
			}

			// /////////// Interval /////////////
			result = regINTERVAL.matcher(rrule);
			if (result.matches())
			{
				String f = result.group(1);
				Utils.setXmlElementValue(xml, recurrence, "interval", f);
			}
			else
			{
				// TODO: kmail/kontact need this default value?
				Utils.setXmlElementValue(xml, recurrence, "interval", "1");
			}

			// /////////// weekday recurrence /////////////
			String daynumber = "";
			result = regBYDAY.matcher(rrule);
			if (result.matches())
			{
				if (CalendarEntry.MONTHLY.equals(cycle)
						|| CalendarEntry.YEARLY.equals(cycle))
				{
					Utils.setXmlAttributeValue(xml, recurrence, "type",
							"weekday");
				}

				final String group = result.group(1);
				Matcher grpResult = regBYDAYSubPattern.matcher(group);
				while (grpResult.find())
				{
					String plusMinus = grpResult.group(1);
					String d = grpResult.group(2);
					if (d != null && !"".equals(d))
					{
						if ("-".equals(plusMinus))
						{
							daynumber = "4";
						}
						else
						{
							daynumber = d;
						}
					}
					String day = CalendarEntry.weekDayToKolabWeekDay(grpResult
							.group(3));
					if (!"".equals(day)) Utils.addXmlElementValue(xml,
							recurrence, "day", day);
				}
			}
			// Not a weekday recurrence - must be daynumber or monthday
			if ("".equals(daynumber))
			{
				if (CalendarEntry.MONTHLY.equals(cycle))
				{
					Utils.setXmlAttributeValue(xml, recurrence, "type",
							"daynumber");
				}
				if (CalendarEntry.YEARLY.equals(cycle))
				{
					Utils.setXmlAttributeValue(xml, recurrence, "type",
							"monthday");
				}
			}

			// /////////// Monthday /////////////
			result = regBYMONTHDAY.matcher(rrule);
			if (result.matches())
			{
				daynumber = result.group(1);
			}

			// If daynumber is empty, get the daynumber from startdate for
			// MONTHLY and YEARLY recurrences
			if ("".equals(daynumber)
					&& (CalendarEntry.MONTHLY.equals(cycle) || CalendarEntry.YEARLY
							.equals(cycle)))
			{
				daynumber = Integer.toString(source.getDtstart().monthDay);
			}

			Utils.setXmlElementValue(xml, recurrence, "daynumber", daynumber);

			// /////////// Month /////////////
			result = regBYMONTH.matcher(rrule);
			String month = "";
			if (result.matches())
			{
				month = CalendarEntry.monthToKolabMonth(result.group(1));
			}
			// Get month only for YEARLY recurrences from startdate
			if (CalendarEntry.YEARLY.equals(cycle) && "".equals(month))
			{
				month = CalendarEntry.monthToKolabMonth(Integer.toString(source
						.getDtstart().month + 1)); // 0-11
			}
			Utils.setXmlElementValue(xml, recurrence, "month", month);

			// Android does know UNTIL but only if an event and following
			// recurrences have
			// been deleted
			Element range = Utils.getOrCreateXmlElement(xml, recurrence,
					"range");
			result = regUNTIL.matcher(rrule);
			if (result.matches())
			{
				Time rangeEndDate = new Time("UTC");
				Utils.setXmlAttributeValue(xml, range, "type", "date");
				rangeEndDate.parse(result.group(1));
				rangeEndDate.monthDay -= 1;
				rangeEndDate.normalize(true);
				Utils.setXmlElementValue(xml, recurrence, "range",
						rangeEndDate.format3339(true));
			}
			else
			{
				Utils.setXmlAttributeValue(xml, range, "type", "none");
				Utils.setXmlElementValue(xml, recurrence, "range", "");
			}
		}
	}

	@Override
	protected String writeXml(SyncContext sync)
			throws ParserConfigurationException, SyncException
	{
		CalendarEntry source = getLocalItem(sync);
		CacheEntry entry = sync.getCacheEntry();

		entry.setLocalHash(source.getLocalHash());
		final Date lastChanged = new Date();
		entry.setRemoteChangedDate(lastChanged);
		final String newUid = getNewUid();
		entry.setRemoteId(newUid);
		source.setUid(newUid);

		Document xml = Utils.newDocument("event");
		writeXml(xml, source, lastChanged);

		return Utils.getXml(xml);
	}

	private CalendarEntry getLocalItem(SyncContext sync) throws SyncException
	{
		if (sync.getLocalItem() != null) return (CalendarEntry) sync
				.getLocalItem();
		CalendarEntry c = localItemsCache
				.get(sync.getCacheEntry().getLocalId());
		if (c != null)
		{
			c.setUid(sync.getCacheEntry().getRemoteId());
		}
		sync.setLocalItem(c);
		return c;
	}

	@Override
	protected String getMessageBodyText(SyncContext sync) throws SyncException
	{
		CalendarEntry cal = getLocalItem(sync);
		StringBuilder sb = new StringBuilder();

		sb.append(cal.getTitle());
		sb.append("\n");

		sb.append("Location: ");
		sb.append(cal.getEventLocation());
		sb.append("\n");

		sb.append("Start: ");
		sb.append(cal.getDtstart().format("%c")); // TODO: Change format for
													// allDay events
		sb.append("\n");

		sb.append("End: ");
		sb.append(cal.getDtend().format("%c"));// TODO: Change format for allDay
												// events
		sb.append("\n");

		sb.append("Recurrence: ");
		sb.append(cal.getrRule());
		sb.append("\n");

		sb.append("-----\n");
		sb.append(cal.getDescription());
		return sb.toString();
	}

	@Override
	public String getItemText(SyncContext sync) throws MessagingException
	{
		if (sync.getLocalItem() != null)
		{
			CalendarEntry item = (CalendarEntry) sync.getLocalItem();
			return item.getTitle() + ": " + item.getDtstart().toString();
		}
		else
		{
			return sync.getMessage().getSubject();
		}
	}
}
