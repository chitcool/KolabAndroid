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

import java.util.Date;

import android.content.ContentValues;
import android.database.Cursor;
import at.dasz.KolabDroid.Provider.DatabaseHelper;
import at.dasz.KolabDroid.Provider.LocalCacheProvider;

/**
 * This class creates and keeps the connections between local and remote items.
 * The contained information can be used to recognize changes of items.
 */
public class CacheEntry
{

	private long	id;
	private int	localId, remoteSize;
	private long		remoteChangedDate;
	private String	localHash, remoteId, remoteImapUid;
	private byte[]	remoteHash;

	public CacheEntry()
	{

	}

	public CacheEntry(Cursor c)
	{
		setId(c.getInt(DatabaseHelper.COL_IDX_ID));
		setLocalId(c.getInt(LocalCacheProvider.COL_IDX_LOCAL_ID));
		setRemoteChangedDate(c
				.getLong(LocalCacheProvider.COL_IDX_REMOTE_CHANGEDDATE));
		setRemoteSize(c.getInt(LocalCacheProvider.COL_IDX_REMOTE_SIZE));
		setLocalHash(c.getString(LocalCacheProvider.COL_IDX_LOCAL_HASH));
		setRemoteId(c.getString(LocalCacheProvider.COL_IDX_REMOTE_ID));
		setRemoteImapUid(c
				.getString(LocalCacheProvider.COL_IDX_REMOTE_IMAP_UID));
		setRemoteHash(c.getBlob(LocalCacheProvider.COL_IDX_REMOTE_HASH));
	}

	public void setId(long rowId)
	{
		this.id = rowId;
	}

	public long getId()
	{
		return id;
	}

	public void setLocalId(int localId)
	{
		this.localId = localId;
	}

	public int getLocalId()
	{
		return localId;
	}

	public void setRemoteChangedDate(long remoteChangedDate)
	{
		this.remoteChangedDate = remoteChangedDate;
	}

	public void setRemoteChangedDate(Date remoteChangedDate)
	{
		this.remoteChangedDate = remoteChangedDate.getTime();
	}

	public Date getRemoteChangedDate()
	{
		return new Date(remoteChangedDate);
	}

	public void setRemoteSize(int remoteSize)
	{
		this.remoteSize = remoteSize;
	}

	public int getRemoteSize()
	{
		return remoteSize;
	}

	public void setLocalHash(String localHash)
	{
		this.localHash = localHash;
	}

	public String getLocalHash()
	{
		return localHash;
	}

	public void setRemoteId(String remoteId)
	{
		this.remoteId = remoteId;
	}

	public String getRemoteId()
	{
		return remoteId;
	}

	public void setRemoteImapUid(String remoteImapUid)
	{
		this.remoteImapUid = remoteImapUid;
	}

	public String getRemoteImapUid()
	{
		return remoteImapUid;
	}
	
	public byte[] getRemoteHash()
	{
		return remoteHash;
	}

	public void setRemoteHash(byte[] remoteHash)
	{
		this.remoteHash = remoteHash;
	}

	public ContentValues toContentValues()
	{
		ContentValues result = new ContentValues();
		if (getId() != 0)
		{
			result.put(DatabaseHelper.COL_ID, getId());
		}
		result.put(LocalCacheProvider.COL_LOCAL_ID, getLocalId());
		result.put(LocalCacheProvider.COL_REMOTE_CHANGEDDATE,
				getRemoteChangedDate().getTime());
		result.put(LocalCacheProvider.COL_REMOTE_SIZE, getRemoteSize());
		result.put(LocalCacheProvider.COL_LOCAL_HASH, getLocalHash());
		result.put(LocalCacheProvider.COL_REMOTE_ID, getRemoteId());
		result.put(LocalCacheProvider.COL_REMOTE_IMAP_UID, getRemoteImapUid());
		result.put(LocalCacheProvider.COL_REMOTE_HASH, getRemoteHash());
		return result;
	}
}
