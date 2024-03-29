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

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Date;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.BodyPart;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.MultipartDataSource;
import javax.mail.Session;
import javax.mail.Flags.Flag;
import javax.mail.Message.RecipientType;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import android.content.Context;
import android.text.format.Time;
import android.util.Log;
import at.dasz.KolabDroid.Utils;
import at.dasz.KolabDroid.Settings.Settings;

/**
 * This class contains common code for all local data stores and defines the
 * store-specific functionality that has to be implemented by the store.
 */
public abstract class AbstractSyncHandler implements SyncHandler
{
	protected AbstractSyncHandler(Context context)
	{
		this.context = context;
		status = new StatusEntry();
		Time t = new Time();
		t.setToNow();
		status.setTime(t);
	}

	protected StatusEntry	status;
	protected Context		context;

	protected Settings		settings;

	protected abstract String getMimeType();

	/**
	 * Create the XML in kolab format to describe the specified item.
	 * 
	 * @param sync
	 * @return String containing the XML describing the item
	 * @throws ParserConfigurationException
	 * @throws SyncException
	 * @throws MessagingException
	 */
	protected abstract String writeXml(SyncContext sync)
			throws ParserConfigurationException, SyncException,
			MessagingException;

	/**
	 * Create a human readable description of the specified item to be displayed
	 * as text/plain part in the mail.
	 * 
	 * @param sync
	 * @return
	 * @throws SyncException
	 * @throws MessagingException
	 */
	protected abstract String getMessageBodyText(SyncContext sync)
			throws SyncException, MessagingException;

	/**
	 * Create a short human readable string describing an item for log messages.
	 * 
	 * @param sync
	 * @return
	 * @throws MessagingException
	 */
	public abstract String getItemText(SyncContext sync)
			throws MessagingException;

	/**
	 * Update the local item from the specified XML Document.
	 * 
	 * @param sync
	 * @param xml
	 * @throws SyncException
	 */
	protected abstract void updateLocalItemFromServer(SyncContext sync,
			Document xml) throws SyncException;

	/**
	 * Update the specified XML Document from the local item.
	 * 
	 * @param sync
	 * @param xml
	 * @throws SyncException
	 * @throws MessagingException
	 */
	protected abstract void updateServerItemFromLocal(SyncContext sync,
			Document xml) throws SyncException, MessagingException;

	/**
	 * Delete the local item with the specified ID.
	 * 
	 * @param localId
	 * @throws SyncException
	 */
	public abstract void deleteLocalItem(int localId) throws SyncException;

	public StatusEntry getStatus()
	{
		return status;
	}

	// public void createLocalItemFromServer(SyncContext sync)
	public void createLocalItemFromServer(Session session, Folder folder,
			SyncContext sync) throws MessagingException,
			ParserConfigurationException, IOException, SyncException
	{
		Log.d("sync", "Downloading item ...");
		try
		{
			InputStream xmlinput = extractXml(sync.getMessage());
			if (null == xmlinput)
			{
				// skip non kolab message instead of crashing
				return;
			}
			Document doc = Utils.getDocument(xmlinput);
			updateLocalItemFromServer(sync, doc);
			updateCacheEntryFromMessage(sync, doc);
		}
		catch (SAXException ex)
		{
			throw new SyncException(getItemText(sync),
					"Unable to extract XML Document", ex);
		}
	}

	public void updateLocalItemFromServer(SyncContext sync)
			throws MessagingException, ParserConfigurationException,
			IOException, SyncException
	{
		if (hasLocalItem(sync))
		{
			Log.d("sync", "Updating without conflict check: "
					+ sync.getCacheEntry().getLocalId());
			try
			{
				InputStream xmlinput = extractXml(sync.getMessage());
				Document doc = Utils.getDocument(xmlinput);
				updateLocalItemFromServer(sync, doc);
				updateCacheEntryFromMessage(sync, doc);
			}
			catch (SAXException ex)
			{
				throw new SyncException(getItemText(sync),
						"Unable to extract XML Document", ex);
			}
		}
	}

	protected void updateCacheEntryFromMessage(SyncContext sync, Document doc)
			throws MessagingException
	{
		CacheEntry c = sync.getCacheEntry();
		Message m = sync.getMessage();
		Date dt = Utils.getMailDate(m);
		c.setRemoteChangedDate(dt);
		c.setRemoteId(m.getSubject());
		c.setRemoteSize(m.getSize());
		try
		{
			if (doc == null)
			{
				// set correct remote hash
				InputStream is = extractXml(m);
				if (is != null)
				{
					doc = Utils.getDocument(is);
				}
			}

			if (doc != null)
			{
				String docText = Utils.getXml(doc.getDocumentElement());
				byte[] remoteHash = Utils.sha1Hash(docText);
				c.setRemoteHash(remoteHash);
			}
		}
		catch (Exception ex)
		{
			Log.e("EE", ex.toString());
		}
		getLocalCacheProvider().saveEntry(c);
	}

	public void createServerItemFromLocal(Session session, Folder targetFolder,
			SyncContext sync, int localId) throws MessagingException,
			ParserConfigurationException, SyncException
	{
		Log.d("sync", "Uploading: #" + localId);

		// initialize cache entry with values that should go
		// into the new server item
		CacheEntry entry = new CacheEntry();
		entry.setLocalId(localId);
		sync.setCacheEntry(entry);

		String xml = writeXml(sync);
		Message m = wrapXmlInMessage(session, sync, xml);
		targetFolder.appendMessages(new Message[] { m });
		m.saveChanges();
		sync.setMessage(m);
		// TODO: Improve that
		updateCacheEntryFromMessage(sync, null);
	}

	public void updateServerItemFromLocal(Session session, Folder targetFolder,
			SyncContext sync) throws MessagingException, IOException,
			SyncException, ParserConfigurationException
	{
		Log.d("sync", "Update item on Server: #"
				+ sync.getCacheEntry().getLocalId());

		InputStream xmlinput = extractXml(sync.getMessage());
		try
		{
			// Parse XML
			Document doc = Utils.getDocument(xmlinput);

			// Update
			updateServerItemFromLocal(sync, doc);

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

			updateCacheEntryFromMessage(sync, doc);
		}
		catch (SAXException ex)
		{
			throw new SyncException(getItemText(sync),
					"Unable to extract XML Document", ex);
		}
		finally
		{
			if (xmlinput != null) xmlinput.close();
		}
	}

	public void deleteLocalItem(SyncContext sync) throws SyncException
	{
		Log.d("sync", "Deleting locally: "
				+ sync.getCacheEntry().getLocalHash());
		deleteLocalItem(sync.getCacheEntry().getLocalId());
		getLocalCacheProvider().deleteEntry(sync.getCacheEntry());
	}

	public void deleteServerItem(SyncContext sync) throws MessagingException,
			SyncException
	{
		Log.d("sync", "Deleting from server: " + sync.getMessage().getSubject());
		sync.getMessage().setFlag(Flag.DELETED, true);
		// remove contents too, to avoid confusing the butchered JAF
		// message.setContent("", "text/plain");
		// message.saveChanges();
		getLocalCacheProvider().deleteEntry(sync.getCacheEntry());
	}

	// private InputStream extractXml(Message message)
	protected InputStream extractXml(Message message)
	{
		try
		{
			DataSource mainDataSource = message.getDataHandler()
					.getDataSource();
			if ((mainDataSource instanceof MultipartDataSource))
			{
				MultipartDataSource multipart = (MultipartDataSource) mainDataSource;
				for (int idx = 0; idx < multipart.getCount(); idx++)
				{
					BodyPart p = multipart.getBodyPart(idx);

					if (p.isMimeType(getMimeType())) { return p
							.getInputStream(); }
				}
			}
			else
			{
				// What's the difference?
				MimeMultipart multipart = (MimeMultipart) message.getContent();

				for (int idx = 0; idx < multipart.getCount(); idx++)
				{
					BodyPart p = multipart.getBodyPart(idx);

					if (p.isMimeType(getMimeType())) { return p
							.getInputStream(); }
				}
			}

			return null;
		}
		catch (MessagingException ex)
		{
			ex.printStackTrace();
		}
		catch (IOException ex)
		{
			ex.printStackTrace();
		}
		return null;
	}

	protected Message wrapXmlInMessage(Session session, SyncContext sync,
			String xml) throws MessagingException, SyncException
	{
		Message result = new MimeMessage(session);
		result.setSubject(sync.getCacheEntry().getRemoteId());
		result.setSentDate(sync.getCacheEntry().getRemoteChangedDate());
		result.setFrom(new InternetAddress("kolab-android@dasz.at"));
		result.setRecipient(RecipientType.TO, new InternetAddress(
				"kolab-android@dasz.at"));
		result.setHeader("User-Agent", "kolab-android 0.1");
		result.setHeader("X-Kolab-Type", getMimeType());
		MimeMultipart mp = new MimeMultipart();
		MimeBodyPart txt = new MimeBodyPart();
		txt.setText(getMessageBodyText(sync), "utf-8");
		mp.addBodyPart(txt);

		BodyPart messageBodyPart = new MimeBodyPart();
		DataSource source;
		try
		{
			source = new ByteArrayDataSource(xml.getBytes("UTF-8"),
					getMimeType());
		}
		catch (UnsupportedEncodingException ex)
		{
			ex.printStackTrace();
			throw new RuntimeException(ex);
		}
		messageBodyPart.setDataHandler(new DataHandler(source));
		messageBodyPart.setFileName("kolab.xml");
		mp.addBodyPart(messageBodyPart);

		result.setContent(mp);

		// avoid later change in timestamp when the SEEN flag would be updated
		result.setFlag(Flags.Flag.SEEN, true);

		return result;
	}

	public Settings getSettings()
	{
		return this.settings;
	}

	public void setSettings(Settings settings)
	{
		this.settings = settings;
	}

	public boolean isSameRemoteHash(CacheEntry entry, Message message)
			throws MessagingException
	{
		Date dt = null;
		if (message != null)
		{
			dt = Utils.getMailDate(message);
		}

		InputStream is = extractXml(message);
		boolean remoteHashIsSame = false;

		if (is != null)
		{
			Document doc = null;
			try
			{
				doc = Utils.getDocument(is);
				String docText = Utils.getXml(doc.getDocumentElement());
				byte[] remoteHash = Utils.sha1Hash(docText);
				if (Arrays.equals(remoteHash, entry.getRemoteHash()))
				{
					remoteHashIsSame = true;
				}
				else
				{
					remoteHashIsSame = false;
				}
			}
			catch (Exception ex)
			{
				Log.e("EE", ex.toString());
			}
		}

		boolean result = entry != null && message != null
				&& entry.getRemoteChangedDate().equals(dt)
				&& entry.getRemoteId().equals(message.getSubject())
				&& remoteHashIsSame;

		if (!result)
		{
			if (entry == null) Log.d("syncisSame", "entry == null");
			if (message == null) Log.d("syncisSame", "message == null");
			if (entry != null && message != null)
			{
				if (!entry.getRemoteChangedDate().equals(dt))
				{
					Log.d("syncisSame",
							"getRemoteChangedDate="
									+ entry.getRemoteChangedDate()
									+ ", getReceived/SentDate=" + dt);
				}
				if (!entry.getRemoteId().equals(message.getSubject()))
				{
					Log.d("syncisSame", "getRemoteId=" + entry.getRemoteId()
							+ ", getSubject=" + message.getSubject());
				}
			}
		}

		return result;
	}

	public boolean isSame(CacheEntry entry, Message message)
			throws MessagingException
	{
		Date dt = null;
		if (message != null)
		{
			dt = Utils.getMailDate(message);
		}

		boolean result = entry != null && message != null
				&& entry.getRemoteChangedDate().equals(dt)
				&& entry.getRemoteId().equals(message.getSubject());

		if (!result)
		{
			if (entry == null) Log.d("syncisSame", "entry == null");
			if (message == null) Log.d("syncisSame", "message == null");
			if (entry != null && message != null)
			{
				if (!entry.getRemoteChangedDate().equals(dt))
				{
					Log.d("syncisSame",
							"getRemoteChangedDate="
									+ entry.getRemoteChangedDate()
									+ ", getReceived/SentDate=" + dt);
				}
				if (!entry.getRemoteId().equals(message.getSubject()))
				{
					Log.d("syncisSame", "getRemoteId=" + entry.getRemoteId()
							+ ", getSubject=" + message.getSubject());
				}
			}
		}

		return result;
	}
}
