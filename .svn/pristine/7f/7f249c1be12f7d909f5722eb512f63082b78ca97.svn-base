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

package at.dasz.KolabDroid.ContactsContract;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Flags.Flag;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.Contacts.People;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.util.Log;
import at.dasz.KolabDroid.Utils;
import at.dasz.KolabDroid.Provider.LocalCacheProvider;
import at.dasz.KolabDroid.Settings.Settings;
import at.dasz.KolabDroid.Sync.AbstractSyncHandler;
import at.dasz.KolabDroid.Sync.CacheEntry;
import at.dasz.KolabDroid.Sync.SyncContext;
import at.dasz.KolabDroid.Sync.SyncException;

public class SyncContactsHandler extends AbstractSyncHandler
{
	// private static final String[] PEOPLE_ID_PROJECTION = new String[] {
	// People._ID };

	/*
	 * private static final String[] PHONE_PROJECTION = new String[] {
	 * Contacts.Phones.TYPE, Contacts.Phones.NUMBER }; private static final
	 * String[] EMAIL_PROJECTION = new String[] { Contacts.ContactMethods.TYPE,
	 * Contacts.ContactMethods.DATA }; //private static final String[]
	 * PEOPLE_NAME_PROJECTION = new String[] { People.NAME }; private static
	 * final String[] CONTACT_NAME_PROJECTION = new String[] {
	 * CommonDataKinds.StructuredName.DISPLAY_NAME }; private static final
	 * String[] ID_PROJECTION = new String[] { "_id" }; private static final
	 * String EMAIL_FILTER = Contacts.ContactMethods.KIND + "=" +
	 * Contacts.KIND_EMAIL;
	 */

	private final String				defaultFolderName;
	private final LocalCacheProvider	cacheProvider;
	private final ContentResolver		cr;
	private HashMap<Integer, Contact>	localItemsCache;

	// private Account syncAccount;

	public SyncContactsHandler(Context context)
	{
		super(context);
		Settings s = new Settings(context);
		settings = s;
		defaultFolderName = s.getContactsFolder();
		cacheProvider = new LocalCacheProvider.ContactsCacheProvider(context);
		cr = context.getContentResolver();
		status.setTask("Contacts");

		// syncAccount =
		// KolabAccountAuthenticatorService.getKolabDroidAccount(context);
	}

	public String getDefaultFolderName()
	{
		return defaultFolderName;
	}

	public boolean shouldProcess()
	{
		boolean hasFolder = (defaultFolderName != null && !""
				.equals(defaultFolderName));
		return settings.getSyncContacts() && hasFolder;
	}

	public LocalCacheProvider getLocalCacheProvider()
	{
		return cacheProvider;
	}

	public Set<Integer> getAllLocalItemsIDs()
	{
		return localItemsCache.keySet();
	}

	public void fetchAllLocalItems() throws SyncException
	{
		localItemsCache = new HashMap<Integer, Contact>();
		Cursor personCursor = getAllLocalItemsCursor();
		try
		{
			while (personCursor.moveToNext())
			{
				Contact result = loadItem(personCursor);
				if(result != null) 
				{
					localItemsCache.put(result.getId(), result);
				}
			}
		}
		finally
		{
			if (personCursor != null) personCursor.close();
		}
	}

	public Cursor getAllLocalItemsCursor()
	{
		// return only those which are not deleted by other programs
		// String where = ContactsContract.RawContacts.DELETED+"='0'";

		// return all again
		return cr.query(ContactsContract.RawContacts.CONTENT_URI, null, null,
				null, null);
	}

	public int getIdColumnIndex(Cursor c)
	{
		return c.getColumnIndex(ContactsContract.RawContacts._ID);
	}

	@Override
	public void createLocalItemFromServer(Session session, Folder targetFolder,
			SyncContext sync) throws MessagingException,
			ParserConfigurationException, IOException, SyncException
	{
		Log.d("sync", "Downloading item ...");
		try
		{
			InputStream xmlinput = extractXml(sync.getMessage());
			Document doc = Utils.getDocument(xmlinput);
			updateLocalItemFromServer(sync, doc);
			updateCacheEntryFromMessage(sync, doc);

			if (this.settings.getMergeContactsByName())
			{
				Log.d("ConH", "Preparing upload of Contact after merge");
				sync.setLocalItem(null);
				getLocalItem(sync); // fetch updates which were just done

				Log.d("ConH",
						"Fetched data after merge for "
								+ ((Contact) sync.getLocalItem()).getFullName());

				updateServerItemFromLocal(sync, doc);

				Log.d("ConH", "Server item updated after merge");

				// Create & Upload new Message
				// IMAP needs a new Message uploaded
				String xml = Utils.getXml(doc);
				Message newMessage = wrapXmlInMessage(session, sync, xml);
				targetFolder.appendMessages(new Message[] { newMessage });
				newMessage.saveChanges();

				// Delete old message
				sync.getMessage().setFlag(Flag.DELETED, true);
				// Replace sync context with new message
				sync.setMessage(newMessage);

				Log.d("ConH", "IMAP Message replaced after merge");

				updateCacheEntryFromMessage(sync, doc);
			}

		}
		catch (SAXException ex)
		{
			throw new SyncException(getItemText(sync),
					"Unable to extract XML Document", ex);
		}
	}

	@Override
	protected void updateLocalItemFromServer(SyncContext sync, Document xml)
			throws SyncException
	{
		Contact contact = (Contact) sync.getLocalItem();
		if (contact == null)
		{
			contact = new Contact();
		}
		Element root = xml.getDocumentElement();

		contact.setUid(Utils.getXmlElementString(root, "uid"));

		Element name = Utils.getXmlElement(root, "name");
		if (name != null)
		{
			// contact.setFullName(Utils.getXmlElementString(name,
			// "full-name"));
			String fullName = Utils.getXmlElementString(name, "full-name");
			if (fullName != null)
			{
				String[] names = fullName.split(" ");
				if (names.length == 2)
				{
					contact.setGivenName(names[0]);
					contact.setFamilyName(names[1]);
				}
			}
		}

		contact.setBirthday(Utils.getXmlElementString(root, "birthday"));

		contact.getContactMethods().clear();
		NodeList nl = Utils.getXmlElements(root, "phone");
		for (int i = 0; i < nl.getLength(); i++)
		{
			ContactMethod cm = new PhoneContact();
			cm.fromXml((Element) nl.item(i));
			contact.getContactMethods().add(cm);
		}
		nl = Utils.getXmlElements(root, "email");
		for (int i = 0; i < nl.getLength(); i++)
		{
			ContactMethod cm = new EmailContact();
			cm.fromXml((Element) nl.item(i));
			contact.getContactMethods().add(cm);
		}

		byte[] photo = getPhotoFromMessage(sync.getMessage(), xml);
		contact.setPhoto(photo);

		contact.setNote(Utils.getXmlElementString(root, "body"));

		sync.setCacheEntry(saveContact(contact));
	}

	@Override
	protected void updateServerItemFromLocal(SyncContext sync, Document xml)
			throws SyncException, MessagingException
	{
		Contact source = getLocalItem(sync);
		CacheEntry entry = sync.getCacheEntry();

		entry.setLocalHash(source.getLocalHash());
		final Date lastChanged = new Date();
		entry.setRemoteChangedDate(lastChanged);

		writeXml(sync, xml, source, lastChanged);
	}

	private void writeXml(SyncContext sync, Document xml, Contact source,
			final Date lastChanged)
	{
		Element root = xml.getDocumentElement();

		// TODO: needs to be above contact information (Kmail bug?)
		// Kmail seems to be picky about <phone> and <email> elements they
		// should be right after each other

		// remove it for now
		Utils.deleteXmlElements(root, "last-modification-date");
		// we do not need this one for now
		// if we need it, put below contact methods (otherwise kmail
		// complains)...
		// TODO: what shall we do with this entry? :)
		Utils.deleteXmlElements(root, "preferred-address");

		/*
		 * Utils.setXmlElementValue(xml, root, "last-modification-date", Utils
		 * .toUtc(lastChanged));
		 */
		Utils.setXmlElementValue(xml, root, "uid", source.getUid());

		Element name = Utils.getOrCreateXmlElement(xml, root, "name");
		Utils.setXmlElementValue(xml, name, "full-name", source.getFullName());
		Utils.setXmlElementValue(xml, name, "given-name", source.getGivenName());
		Utils.setXmlElementValue(xml, name, "last-name", source.getFamilyName());

		Utils.setXmlElementValue(xml, root, "birthday", source.getBirthday());

		Utils.setXmlElementValue(xml, root, "body", source.getNotes());

		// TODO The method call below is not yet functional because the method
		// implementation is not yet complete
		storePhotoInMessage(sync.getMessage(), xml, source.getPhoto());

		Utils.deleteXmlElements(root, "phone");
		Utils.deleteXmlElements(root, "email");

		for (ContactMethod cm : source.getContactMethods())
		{
			cm.toXml(xml, root, source.getFullName());
		}
	}

	@Override
	protected String writeXml(SyncContext sync)
			throws ParserConfigurationException, SyncException,
			MessagingException
	{
		Contact source = getLocalItem(sync);
		CacheEntry entry = sync.getCacheEntry();

		entry.setLocalHash(source.getLocalHash());
		final Date lastChanged = new Date();
		entry.setRemoteChangedDate(lastChanged);
		final String newUid = getNewUid();
		entry.setRemoteId(newUid);
		source.setUid(newUid);

		Document xml = Utils.newDocument("contact");
		writeXml(sync, xml, source, lastChanged);

		return Utils.getXml(xml);
	}

	@Override
	protected String getMimeType()
	{
		return "application/x-vnd.kolab.contact";
	}

	public boolean hasLocalItem(SyncContext sync) throws SyncException,
			MessagingException
	{
		return getLocalItem(sync) != null;
	}

	public boolean hasLocalChanges(SyncContext sync) throws SyncException,
			MessagingException
	{
		CacheEntry e = sync.getCacheEntry();
		Contact contact = getLocalItem(sync);;
		String entryHash = e.getLocalHash();
		String contactHash = contact != null ? contact.getLocalHash() : "";
		return !entryHash.equals(contactHash);
	}

	@Override
	public void deleteLocalItem(int localId)
	{
		ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

		// normal delete first, then with syncadapter flag
		Uri rawUri = ContactsContract.RawContacts.CONTENT_URI;
		ops.add(ContentProviderOperation
				.newDelete(rawUri)
				.withSelection(ContactsContract.RawContacts._ID + "=?",
						new String[] { String.valueOf(localId) }).build());

		// remove contact from raw_contact table (this time with syncadapter
		// flag set)
		rawUri = ContactsContract.RawContacts.CONTENT_URI
				.buildUpon()
				.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER,
						"true").build();
		ops.add(ContentProviderOperation
				.newDelete(rawUri)
				.withSelection(ContactsContract.RawContacts._ID + "=?",
						new String[] { String.valueOf(localId) }).build());

		try
		{
			cr.applyBatch(ContactsContract.AUTHORITY, ops);
		}
		catch (Exception e)
		{
			Log.e("EE", e.toString());
		}

	}

	private void deleteLocalItemFinally(int localId)
	{
		ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

		// remove contact from raw_contact table (with syncadapter flag set)
		Uri rawUri = ContactsContract.RawContacts.CONTENT_URI
				.buildUpon()
				.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER,
						"true").build();
		ops.add(ContentProviderOperation
				.newDelete(rawUri)
				.withSelection(ContactsContract.RawContacts._ID + "=?",
						new String[] { String.valueOf(localId) }).build());

		try
		{
			cr.applyBatch(ContactsContract.AUTHORITY, ops);
		}
		catch (Exception e)
		{
			Log.e("EE", e.toString());
		}

	}

	@Override
	public void deleteServerItem(SyncContext sync) throws MessagingException,
			SyncException
	{
		Log.d("sync", "Deleting from server: " + sync.getMessage().getSubject());
		sync.getMessage().setFlag(Flag.DELETED, true);
		// remove contents too, to avoid confusing the butchered JAF
		// message.setContent("", "text/plain");
		// message.saveChanges();
		getLocalCacheProvider().deleteEntry(sync.getCacheEntry());

		// make sure it gets flushed from the raw_contacts table on the phone as
		// well
		deleteLocalItemFinally(sync.getCacheEntry().getLocalId());
	}

	private CacheEntry saveContact(Contact contact) throws SyncException
	{
		Uri uri = null;

		String name = contact.getFullName();
		String firstName = contact.getGivenName();
		String lastName = contact.getFamilyName();

		String email = "";
		String phone = "";

		Log.d("ConH", "Saving Contact: \"" + name + "\"");

		ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

		boolean doMerge = false;

		if (contact.getId() == 0 && this.settings.getMergeContactsByName())
		{
			// find raw_contact by name
			String w = CommonDataKinds.StructuredName.DISPLAY_NAME + "='"
					+ name + "'";

			// Cursor c = cr.query(ContactsContract.RawContacts.CONTENT_URI,
			// null, w, null, null);
			Cursor c = cr.query(ContactsContract.Data.CONTENT_URI, null, w,
					null, null);

			if (c == null)
			{
				Log.d("ConH", "SC: faild to query for merge with contact: "
						+ name);
			}

			if (c.getCount() > 0)
			{
				c.moveToFirst();
				int rawIdIdx = c
						.getColumnIndex(ContactsContract.Data.RAW_CONTACT_ID);
				int rawID = c.getInt(rawIdIdx);
				contact.setId(rawID);
				doMerge = true;

				Log.d("ConH", "SC: Found Entry ID: " + rawID + " for contact: "
						+ name + " -> will merge now");
			}

			if (c != null) c.close();
		}

		if (contact.getId() == 0)
		{
			Log.d("ConH", "SC: Contact " + name + " is NEW -> insert");

			String accountName = settings.getAccountName();
			if ("".equals(accountName)) accountName = null;
			String accountType = settings.getAccountType();
			if ("".equals(accountType)) accountType = null;

			ops.add(ContentProviderOperation
					.newInsert(ContactsContract.RawContacts.CONTENT_URI)
					.withValue(ContactsContract.RawContacts.ACCOUNT_TYPE,
							accountType)
					.withValue(ContactsContract.RawContacts.ACCOUNT_NAME,
							accountName).build());

			ops.add(ContentProviderOperation
					.newInsert(ContactsContract.Data.CONTENT_URI)
					.withValueBackReference(
							ContactsContract.Data.RAW_CONTACT_ID, 0)
					.withValue(ContactsContract.Data.MIMETYPE,
							CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
					.withValue(CommonDataKinds.StructuredName.DISPLAY_NAME,
							name)
					.withValue(CommonDataKinds.StructuredName.GIVEN_NAME,
							firstName)
					.withValue(CommonDataKinds.StructuredName.FAMILY_NAME,
							lastName).build());

			if (contact.getBirthday() != null) ops.add(ContentProviderOperation
					.newInsert(ContactsContract.Data.CONTENT_URI)
					.withValueBackReference(
							ContactsContract.Data.RAW_CONTACT_ID, 0)
					.withValue(ContactsContract.Data.MIMETYPE,
							CommonDataKinds.Event.CONTENT_ITEM_TYPE)
					.withValue(CommonDataKinds.Event.START_DATE,
							contact.getBirthday())
					.withValue(CommonDataKinds.Event.TYPE,
							CommonDataKinds.Event.TYPE_BIRTHDAY).build());

			if (contact.getPhoto() != null) ops.add(ContentProviderOperation
					.newInsert(ContactsContract.Data.CONTENT_URI)
					.withValueBackReference(
							ContactsContract.Data.RAW_CONTACT_ID, 0)
					.withValue(ContactsContract.Data.MIMETYPE,
							CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
					.withValue(Photo.PHOTO, contact.getPhoto()).build());

			if (contact.getNotes() != null) ops.add(ContentProviderOperation
					.newInsert(ContactsContract.Data.CONTENT_URI)
					.withValueBackReference(
							ContactsContract.Data.RAW_CONTACT_ID, 0)
					.withValue(ContactsContract.Data.MIMETYPE,
							Note.CONTENT_ITEM_TYPE)
					.withValue(Note.NOTE, contact.getNotes()).build());

			for (ContactMethod cm : contact.getContactMethods())
			{
				if (cm instanceof EmailContact)
				{
					email = cm.getData();

					ops.add(ContentProviderOperation
							.newInsert(ContactsContract.Data.CONTENT_URI)
							.withValueBackReference(
									ContactsContract.Data.RAW_CONTACT_ID, 0)
							.withValue(ContactsContract.Data.MIMETYPE,
									CommonDataKinds.Email.CONTENT_ITEM_TYPE)
							.withValue(CommonDataKinds.Email.DATA, email)
							.withValue(CommonDataKinds.Email.TYPE, cm.getType())
							.build());
				}

				if (cm instanceof PhoneContact)
				{
					phone = cm.getData();

					ops.add(ContentProviderOperation
							.newInsert(ContactsContract.Data.CONTENT_URI)
							.withValueBackReference(
									ContactsContract.Data.RAW_CONTACT_ID, 0)
							.withValue(ContactsContract.Data.MIMETYPE,
									CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
							.withValue(CommonDataKinds.Phone.NUMBER, phone)
							.withValue(CommonDataKinds.Phone.TYPE, cm.getType())
							.build());
				}
			}
		}
		else
		{
			Log.d("ConH", "SC. Contact " + name
					+ " already in Android book, MergeFlag: " + doMerge);

			Uri updateUri = ContactsContract.Data.CONTENT_URI;

			List<ContactMethod> cms = null;
			List<ContactMethod> mergedCms = new ArrayList<ContactMethod>();

			// first remove stuff that is in addressbook
			Cursor queryCursor;

			// update name (broken at the moment :()
			/*
			 * ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.
			 * CONTENT_URI) .withValue(ContactsContract.Data.RAW_CONTACT_ID,
			 * contact.getId()) .withValue(ContactsContract.Data.MIMETYPE,
			 * CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
			 * .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, name)
			 * .withValue(CommonDataKinds.StructuredName.GIVEN_NAME, firstName)
			 * .withValue(CommonDataKinds.StructuredName.FAMILY_NAME, lastName)
			 * .build());
			 */

			// TODO All merge operations below should not delete the row if one
			// exists and re-insert it afterwards but instead run an update.
			// http://developer.android.com/reference/android/provider/ContactsContract.Data.html
			// states:
			//
			// RowID: Sync adapter should try to preserve row IDs during
			// updates. In other words, it would
			// be a bad idea to delete and reinsert a data row. A sync adapter
			// should always do an update instead.

			// birthday
			if (contact.getBirthday() != null
					&& !contact.getBirthday().equals(""))
			{

				String w = ContactsContract.Data.RAW_CONTACT_ID + "='"
						+ contact.getId() + "' AND "
						+ ContactsContract.Contacts.Data.MIMETYPE + " = '"
						+ CommonDataKinds.Event.CONTENT_ITEM_TYPE + "' AND "
						+ CommonDataKinds.Event.TYPE + " = '"
						+ CommonDataKinds.Event.TYPE_BIRTHDAY + "'";

				// Log.i("II", "w: " + w);

				queryCursor = cr.query(updateUri,
						new String[] { BaseColumns._ID }, w, null, null);

				if (queryCursor == null) throw new SyncException("EE",
						"cr.query returned null");

				if (queryCursor.moveToFirst()) // otherwise no events
				{
					int idCol = queryCursor.getColumnIndex(BaseColumns._ID);
					long id = queryCursor.getLong(idCol);

					ops.add(ContentProviderOperation
							.newUpdate(ContactsContract.Data.CONTENT_URI)
							.withSelection(BaseColumns._ID + "= ?",
									new String[] { String.valueOf(id) })
							.withValue(CommonDataKinds.Event.START_DATE,
									contact.getBirthday()).withExpectedCount(1)
							.build());

					Log.d("ConH", "Updating birthday: " + contact.getBirthday()
							+ " for contact " + name);
				}
				else
				{
					ops.add(ContentProviderOperation
							.newInsert(ContactsContract.Data.CONTENT_URI)
							.withValue(ContactsContract.Data.RAW_CONTACT_ID,
									contact.getId())
							.withValue(ContactsContract.Data.MIMETYPE,
									CommonDataKinds.Event.CONTENT_ITEM_TYPE)
							.withValue(CommonDataKinds.Event.START_DATE,
									contact.getBirthday())
							.withValue(CommonDataKinds.Event.TYPE,
									CommonDataKinds.Event.TYPE_BIRTHDAY)
							.build());

					Log.d("ConH",
							"Inserting birthday: " + contact.getBirthday()
									+ " for contact " + name);
				}
			}

			// contact notes
			if (contact.getNotes() != null && !contact.getNotes().equals(""))
			{
				String w = ContactsContract.Data.RAW_CONTACT_ID + "='"
						+ contact.getId() + "' AND "
						+ ContactsContract.Contacts.Data.MIMETYPE + " = '"
						+ CommonDataKinds.Note.CONTENT_ITEM_TYPE + "'";

				queryCursor = cr.query(updateUri,
						new String[] { BaseColumns._ID }, w, null, null);

				if (queryCursor.moveToFirst())
				{
					long id = queryCursor.getLong(queryCursor
							.getColumnIndex(BaseColumns._ID));

					ops.add(ContentProviderOperation
							.newUpdate(ContactsContract.Data.CONTENT_URI)
							.withSelection(BaseColumns._ID + "= ?",
									new String[] { String.valueOf(id) })
							.withValue(CommonDataKinds.Note.NOTE,
									contact.getNotes()).withExpectedCount(1)
							.build());

					Log.d("ConH", "Updating notes for contact " + name);
				}
				else
				{
					ops.add(ContentProviderOperation
							.newInsert(ContactsContract.Data.CONTENT_URI)
							.withValue(ContactsContract.Data.RAW_CONTACT_ID,
									contact.getId())
							.withValue(ContactsContract.Data.MIMETYPE,
									Note.CONTENT_ITEM_TYPE)
							.withValue(CommonDataKinds.Note.NOTE,
									contact.getNotes()).build());

					Log.d("ConH", "Inserting notes for contact " + name);
				}
			}

			// contact photo
			if (contact.getPhoto() != null)
			{
				String w = ContactsContract.Data.RAW_CONTACT_ID + "='"
						+ contact.getId() + "' AND "
						+ ContactsContract.Contacts.Data.MIMETYPE + " = '"
						+ CommonDataKinds.Photo.CONTENT_ITEM_TYPE + "'";

				queryCursor = cr.query(updateUri,
						new String[] { BaseColumns._ID }, w, null, null);

				if (queryCursor == null) throw new SyncException("EE",
						"cr.query returned null");

				if (queryCursor.moveToFirst()) // otherwise no photo
				{
					int colIdx = queryCursor.getColumnIndex(BaseColumns._ID);
					long id = queryCursor.getLong(colIdx);

					ops.add(ContentProviderOperation
							.newUpdate(ContactsContract.Data.CONTENT_URI)
							.withSelection(BaseColumns._ID + "= ?",
									new String[] { String.valueOf(id) })
							.withValue(CommonDataKinds.Photo.PHOTO,
									contact.getPhoto()).withExpectedCount(1)
							.build());

					Log.d("ConH", "Updating photo for contact " + name);
				}
				else
				{
					ops.add(ContentProviderOperation
							.newInsert(ContactsContract.Data.CONTENT_URI)
							.withValue(ContactsContract.Data.RAW_CONTACT_ID,
									contact.getId())
							.withValue(ContactsContract.Data.MIMETYPE,
									CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
							.withValue(CommonDataKinds.Photo.PHOTO,
									contact.getPhoto()).build());
				}

				Log.d("ConH", "Inserting photo for contact " + name);
			}

			// phone
			{
				String w = ContactsContract.Data.RAW_CONTACT_ID + "='"
						+ contact.getId() + "' AND "
						+ ContactsContract.Contacts.Data.MIMETYPE + " = '"
						+ CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "'";

				// Log.i("II", "w: " + w);

				queryCursor = cr.query(updateUri, null, w, null, null);

				if (queryCursor == null) throw new SyncException("EE",
						"cr.query returned null");

				if (queryCursor.getCount() > 0) // otherwise no phone numbers
				{
					if (!queryCursor.moveToFirst()) return null;
					int idCol = queryCursor
							.getColumnIndex(ContactsContract.Data._ID);
					int numberCol = queryCursor
							.getColumnIndex(CommonDataKinds.Phone.NUMBER);
					int typeCol = queryCursor
							.getColumnIndex(CommonDataKinds.Phone.TYPE);

					if (!doMerge)
					{
						do
						{
							ops.add(ContentProviderOperation
									.newDelete(
											ContactsContract.Data.CONTENT_URI)
									.withSelection(
											ContactsContract.Data._ID + "=?",
											new String[] { String
													.valueOf(queryCursor
															.getInt(idCol)) })
									.build());
						} while (queryCursor.moveToNext());
					}
					else
					{
						for (ContactMethod cm : contact.getContactMethods())
						{
							if (!(cm instanceof PhoneContact)) continue;

							boolean found = false;
							String newNumber = cm.getData();
							int newType = cm.getType();

							do
							{
								String numberIn = queryCursor
										.getString(numberCol);
								int typeIn = queryCursor.getInt(typeCol);

								if (typeIn == newType
										&& numberIn.equals(newNumber))
								{
									Log.d("ConH", "SC: Found phone: "
											+ numberIn + " for contact " + name
											+ " -> wont add");
									found = true;
									break;
								}

							} while (queryCursor.moveToNext());

							if (!found)
							{
								mergedCms.add(cm);
							}
						}
					}
				}
				else
				{
					if (doMerge)
					{
						Log.d("ConH", "SC: No numbers in android for contact "
								+ name + " -> adding all");
						// we can add all new Numbers
						for (ContactMethod cm : contact.getContactMethods())
						{
							if (!(cm instanceof PhoneContact)) continue;
							mergedCms.add(cm);
						}
					}
				}
			}

			// mail
			{
				String w = ContactsContract.Data.RAW_CONTACT_ID + "='"
						+ contact.getId() + "' AND "
						+ ContactsContract.Contacts.Data.MIMETYPE + " = '"
						+ CommonDataKinds.Email.CONTENT_ITEM_TYPE + "'";

				queryCursor = cr.query(updateUri, null, w, null, null);

				if (queryCursor == null) throw new SyncException("EE",
						"cr.query returned null");

				if (queryCursor.getCount() > 0) // otherwise no email addresses
				{
					if (!queryCursor.moveToFirst()) return null;
					int idCol = queryCursor
							.getColumnIndex(ContactsContract.Data._ID);
					int mailCol = queryCursor
							.getColumnIndex(CommonDataKinds.Email.DATA);
					int typeCol = queryCursor
							.getColumnIndex(CommonDataKinds.Email.TYPE);

					if (!doMerge)
					{
						do
						{
							ops.add(ContentProviderOperation
									.newDelete(
											ContactsContract.Data.CONTENT_URI)
									.withSelection(
											ContactsContract.Data._ID + "=?",
											new String[] { String
													.valueOf(queryCursor
															.getInt(idCol)) })
									.build());
						} while (queryCursor.moveToNext());
					}
					else
					{
						for (ContactMethod cm : contact.getContactMethods())
						{
							if (!(cm instanceof EmailContact)) continue;

							boolean found = false;
							String newMail = cm.getData();
							int newType = cm.getType();

							do
							{
								String emailIn = queryCursor.getString(mailCol);
								int typeIn = queryCursor.getInt(typeCol);

								if (typeIn == newType
										&& emailIn.equals(newMail))
								{
									Log.d("ConH", "SC. Found email: " + emailIn
											+ " for contact " + name
											+ " -> wont add");
									found = true;
									break;
								}

							} while (queryCursor.moveToNext());

							if (!found)
							{
								mergedCms.add(cm);
							}
						}
					}
				}
				else
				{
					if (doMerge)
					{
						Log.d("ConH", "SC: No email in android for contact "
								+ name + " -> adding all");
						// we can add all new Numbers
						for (ContactMethod cm : contact.getContactMethods())
						{
							if (!(cm instanceof EmailContact)) continue;
							mergedCms.add(cm);
						}
					}
				}
			}

			// insert again
			if (doMerge)
			{
				cms = mergedCms;
			}
			else
			{
				cms = contact.getContactMethods();
			}

			// for (ContactMethod cm : contact.getContactMethods())
			for (ContactMethod cm : cms)
			{
				if (cm instanceof EmailContact)
				{
					email = cm.getData();
					Log.d("ConH", "SC: Writing mail: " + email
							+ " for contact " + name);

					ops.add(ContentProviderOperation
							.newInsert(ContactsContract.Data.CONTENT_URI)
							.withValue(ContactsContract.Data.RAW_CONTACT_ID,
									contact.getId())
							.withValue(ContactsContract.Data.MIMETYPE,
									CommonDataKinds.Email.CONTENT_ITEM_TYPE)
							.withValue(CommonDataKinds.Email.DATA, email)
							.withValue(CommonDataKinds.Email.TYPE, cm.getType())
							.build());
				}

				if (cm instanceof PhoneContact)
				{
					phone = cm.getData();
					Log.d("ConH", "Writing phone: " + phone + " for contact "
							+ name);

					ops.add(ContentProviderOperation
							.newInsert(ContactsContract.Data.CONTENT_URI)
							.withValue(ContactsContract.Data.RAW_CONTACT_ID,
									contact.getId())
							.withValue(ContactsContract.Data.MIMETYPE,
									CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
							.withValue(CommonDataKinds.Phone.NUMBER, phone)
							.withValue(CommonDataKinds.Phone.TYPE, cm.getType())
							.build());
				}
			}

			if (queryCursor != null) queryCursor.close();
		}

		// Log.i("II", "Creating contact: " + firstName + " " + lastName);
		try
		{
			ContentProviderResult[] results = cr.applyBatch(
					ContactsContract.AUTHORITY, ops);
			// store the first result: it contains the uri of the raw contact
			// with its ID
			if (contact.getId() == 0)
			{
				uri = results[0].uri;
				//TODO: get ID from uri (this is just a dirty hack)
				String tmp = results[0].uri.toString();
				String[] a = tmp.split("/");
				int idx = a.length -1;
				contact.setId(Integer.parseInt(a[idx]));
			}
			else
			{
				uri = ContentUris.withAppendedId(
						ContactsContract.RawContacts.CONTENT_URI,
						contact.getId());
			}
			Log.d("ConH", "SC: Affected Uri was: " + uri);

		}
		catch (Exception e)
		{
			// Log exception
			Log.e("EE",
					"Exception encountered while inserting contact: "
							+ e.getMessage() + e.getStackTrace());
		}

		CacheEntry result = new CacheEntry();
		result.setLocalId((int) ContentUris.parseId(uri));
		result.setLocalHash(contact.getLocalHash());
		result.setRemoteId(contact.getUid());

		localItemsCache.put(contact.getId(), contact);
		return result;
	}

	private Contact getLocalItem(SyncContext sync) throws SyncException,
			MessagingException
	{
		if (sync.getLocalItem() != null) return (Contact) sync.getLocalItem();

		Contact c = localItemsCache.get(sync.getCacheEntry().getLocalId());
		if (c != null)
		{
			c.setUid(sync.getCacheEntry().getRemoteId());
		}
		sync.setLocalItem(c);
		return c;
	}

	private Contact loadItem(Cursor personCursor) throws SyncException
	{
		Cursor queryCursor = null;
		try
		{
			int idxID = personCursor
					.getColumnIndex(CommonDataKinds.StructuredName._ID);
			int id = personCursor.getInt(idxID);

			String where = ContactsContract.Data.RAW_CONTACT_ID + "=?";

			// Log.i("II", "where: " + where);
			
			String[] projection = new String[] {
					Contacts.Data.MIMETYPE,
					StructuredName.GIVEN_NAME,
					StructuredName.FAMILY_NAME,
					Phone.NUMBER, 
					Phone.TYPE,
					Email.DATA,
					Event.START_DATE,
					Photo.PHOTO,
					Note.NOTE
			};

			queryCursor = cr.query(ContactsContract.Data.CONTENT_URI, projection,
					where, new String[] { Integer.toString(id) }, null);

			if (queryCursor == null) throw new SyncException("",
					"cr.query returned null");
			if (!queryCursor.moveToFirst()) return null;

			Contact result = new Contact();
			result.setId(id);

			int idxMimeType = queryCursor.getColumnIndex(ContactsContract.Contacts.Data.MIMETYPE);
			String mimeType;
			do
			{
				mimeType = queryCursor.getString(idxMimeType);
				if (mimeType
						.equals(StructuredName.CONTENT_ITEM_TYPE))
				{
					int idxFirst = queryCursor
							.getColumnIndex(StructuredName.GIVEN_NAME);
					int idxLast = queryCursor
							.getColumnIndex(StructuredName.FAMILY_NAME);

					result.setGivenName(queryCursor.getString(idxFirst));
					result.setFamilyName(queryCursor.getString(idxLast));
				}
				else if (mimeType.equals(Phone.CONTENT_ITEM_TYPE))
				{
					int numberIdx = queryCursor.getColumnIndex(Phone.NUMBER);
					int typeIdx = queryCursor.getColumnIndex(Phone.TYPE);
					PhoneContact pc = new PhoneContact();
					pc.setData(queryCursor.getString(numberIdx));
					pc.setType(queryCursor.getInt(typeIdx));
					result.getContactMethods().add(pc);

				}
				else if (mimeType.equals(Email.CONTENT_ITEM_TYPE))
				{
					int dataIdx = queryCursor.getColumnIndex(Email.DATA);
					// int typeIdx =
					// emailCursor.getColumnIndex(CommonDataKinds.Email.TYPE);
					EmailContact pc = new EmailContact();
					pc.setData(queryCursor.getString(dataIdx));
					// pc.setType(emailCursor.getInt(typeIdx));
					result.getContactMethods().add(pc);

				}
				else if (mimeType.equals(Event.CONTENT_ITEM_TYPE))
				{
					int dateIdx = queryCursor.getColumnIndex(Event.START_DATE);
					String bday = queryCursor.getString(dateIdx);
					result.setBirthday(bday);

				}
				else if (mimeType.equals(Photo.CONTENT_ITEM_TYPE))
				{
					int colIdx = queryCursor.getColumnIndex(Photo.PHOTO);
					byte[] photo = queryCursor.getBlob(colIdx);
					result.setPhoto(photo);

				}
				else if (mimeType.equals(Note.CONTENT_ITEM_TYPE))
				{
					int colIdx = queryCursor.getColumnIndex(Note.NOTE);
					String note = queryCursor.getString(colIdx);
					result.setNote(note);
				}
			} while (queryCursor.moveToNext());

			return result;
		}
		finally
		{
			if (queryCursor != null) queryCursor.close();
		}
	}

	private String getNewUid()
	{
		// Create Application and Type specific id
		// kd == Kolab Droid, ct = contact
		return "kd-ct-" + UUID.randomUUID().toString();
	}

	@Override
	protected String getMessageBodyText(SyncContext sync) throws SyncException,
			MessagingException
	{
		Contact contact = getLocalItem(sync);
		StringBuilder sb = new StringBuilder();

		String fullName = contact.getFullName();
		sb.append(fullName == null ? "(no name)" : fullName);
		sb.append("\n");
		sb.append("----- Contact Methods -----\n");
		for (ContactMethod cm : contact.getContactMethods())
		{
			sb.append(cm.getData());
			sb.append("\n");
		}

		return sb.toString();
	}

	@Override
	public String getItemText(SyncContext sync) throws MessagingException
	{
		if (sync.getLocalItem() != null)
		{
			Contact item = (Contact) sync.getLocalItem();
			return item.getFullName();
		}
		else
		{
			return sync.getMessage().getSubject();
		}
	}

	/**
	 * Extracts the contact photo from the given message if one exists and
	 * returns it as byte array.
	 * 
	 * @param message
	 *            The message whose contact photo is to be returned.
	 * @return A byte array of the contact photo of the given message or null if
	 *         no photo exists.
	 */
	private byte[] getPhotoFromMessage(Message message, Document messageXml)
	{
		Element root = messageXml.getDocumentElement();
		String photoFileName = Utils.getXmlElementString(root, "picture");
		try
		{
			Multipart multipart = (Multipart) message.getContent();

			for (int i = 0, n = multipart.getCount(); i < n; i++)
			{
				Part part = multipart.getBodyPart(i);
				String disposition = part.getDisposition();

				if ((part.getFileName() != null)
						&& (part.getFileName().equals(photoFileName))
						&& (disposition != null)
						&& ((disposition.equals(Part.ATTACHMENT) || (disposition
								.equals(Part.INLINE))))) {

				return inputStreamToBytes(part.getInputStream()); }
			}
		}
		catch (IOException ex)
		{
			Log.w("ConH", ex);
		}
		catch (MessagingException ex)
		{
			Log.w("ConH", ex);
		}

		return null;
	}

	/**
	 * Stores the photo in the given byte array as attachment of the given
	 * {@link Message} with the filename 'kolab-picture.png' and removes an
	 * existing contact photo if it exists.
	 * 
	 * @param message
	 *            The {@link Message} where the attachment is to be stored.
	 * @param messageXml
	 *            The xml document of the kolab message.
	 * @param photo
	 *            a byte array of the photo to be stored or <code>null</code> if
	 *            no photo is to be stored.
	 */
	private void storePhotoInMessage(Message message, Document messageXml,
			byte[] photo)
	{
		Element root = messageXml.getDocumentElement();
		Utils.setXmlElementValue(messageXml, root, "picture",
				"kolab-picture.png");

		// TODO complete this method

		// delete existing photo if any

		// create new attachment for new photo
		// http://java.sun.com/developer/onlineTraining/JavaMail/contents.html#SendingAttachments
		// explains how
	}

	/**
	 * Reads the given {@link InputStream} and returns its contents as byte
	 * array.
	 * 
	 * @param in
	 *            The {@link InputStream} to be read.
	 * @return a byte array with the contents of the given {@link InputStream}.
	 * @throws IOException
	 */
	private byte[] inputStreamToBytes(InputStream in) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
		byte[] buffer = new byte[1024];
		int len;

		while ((len = in.read(buffer)) >= 0)
			out.write(buffer, 0, len);

		in.close();
		out.close();
		return out.toByteArray();
	}
}
