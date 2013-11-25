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

package at.dasz.KolabDroid.Imap;

import java.util.ArrayList;

import javax.mail.Folder;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;

import android.util.Log;

public class ImapClient
{
	private static final String LOG_TAG = at.dasz.KolabDroid.Utils.LOG_TAG_IMAPCLIENT;
	
	public static Store openServer(Session session, String hostname,
			String username, String password) throws MessagingException
	{
		Log.v(LOG_TAG, "Opening session to " + hostname + " with user " + username);
		Store store = session.getStore("imap");
		store.connect(hostname, username, password);
		return store;
	}

	public static Session getDefaultImapSession(int port, boolean useSsl)
	{
		java.security.Security.setProperty("ssl.SocketFactory.provider", "at.dasz.KolabDroid.Imap.SpecialKeystoreSSLSocketFactory");

		Log.v(LOG_TAG, "useSSL=" + useSsl);
		java.util.Properties props = new java.util.Properties();
		if (useSsl)
		{
			props.setProperty("mail.imap.socketFactory.fallback", "false");
			props.setProperty("mail.imap.ssl.enable", "true");
			props.put("mail.imap.ssl.socketFactory", new SpecialKeystoreSSLSocketFactory());
		} else {
			props.setProperty("mail.imap.ssl.enable", "false");
		}
		
		props.setProperty("mail.imap.socketFactory.fallback", "false");
		props.setProperty("mail.imap.port", Integer.toString(port));

		Session session = Session.getDefaultInstance(props);
		return session;
	}
	
	
	public static String[] updateFolderList(Store store, String namespace) throws MessagingException {
		Folder rootfolder = null;
		ArrayList<String> folderlist = new ArrayList<String>();
		if (namespace.length() != 0) {
			Log.d(LOG_TAG, "using IMAP namespace '" + namespace + "' to retrieve folder list");
			rootfolder = store.getFolder(namespace);
		} else {
			Log.d(LOG_TAG, "no namespace given - retrieving folderlist with default folder");
			rootfolder = store.getDefaultFolder();
		}
		folderlist.add("");
		updateFolderListRecursive(folderlist, rootfolder);
		
		return ((String[]) folderlist.toArray(new String[folderlist.size()]));
	}
		
	private static void updateFolderListRecursive(ArrayList<String> folderlist, Folder folder) throws MessagingException {
		if (folder.getFullName().length() > 0) {
			folderlist.add(folder.getFullName());
		}
		if ((folder.getType() & Folder.HOLDS_FOLDERS) != 0) {
	         Folder[] subfolders = folder.list();
	         for (int i = 0; i < subfolders.length; i++)
	            updateFolderListRecursive(folderlist, subfolders[i]);
	      }
	}
	
}
