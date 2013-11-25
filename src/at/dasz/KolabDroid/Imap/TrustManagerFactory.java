/*
 * Copyright 2010 Sönke Schwardt-Krummrich <soenke@schwardtnet.de>
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
 *  
 *  This code contains some code snippets of the k9mail project that is licensed 
 *  under Apache License 2.0: http://k9mail.googlecode.com/svn/k9mail/trunk (SVN Rev 2337)
 *  Thanks to the k9mail project for their great work!
 */

package at.dasz.KolabDroid.Imap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import android.content.Context;
import android.util.Log;

public class TrustManagerFactory
{
	private static KeyStore mKeystore;
	private static File fileKeystore;
	private static X509TrustManager	mSpecialKeystoreTrustManager;
	private static X509TrustManager	mDefaultTrustManager;
	private static X509Certificate[] mLastUsedChain = null;
	private static final String LOG_TAG = at.dasz.KolabDroid.Utils.LOG_TAG_TRUSTMANAGERFACTORY;
	
	private static class SpecialX509TrustManager implements X509TrustManager {
		private SpecialX509TrustManager() {
		}
		
		public static X509TrustManager getInstance() {
			return new SpecialX509TrustManager();
		}

		public void checkClientTrusted(X509Certificate[] arg0, String arg1)
				throws CertificateException
		{
			Log.w(LOG_TAG, "checkClientTrusted() not implemented yet");
		}

		public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException
		{
			mLastUsedChain = chain;
			try {
				Log.v(LOG_TAG, "checkServerTrusted(): test against default trust manager");
				mDefaultTrustManager.checkServerTrusted(chain, authType);
				Log.v(LOG_TAG, "checkServerTrusted(): certificate chain is trustworthy");
			}
			catch (CertificateException ce) {
				Log.v(LOG_TAG, "checkServerTrusted(): test against special trust manager");
				mSpecialKeystoreTrustManager.checkServerTrusted(new X509Certificate[] {chain[0]}, authType);
				Log.v(LOG_TAG, "checkServerTrusted(): user said that this certificate chain is trustworthy");
			}
		}

		public X509Certificate[] getAcceptedIssuers()
		{
			return new X509Certificate[] {};
		}
	}
	
	public static X509Certificate[] getLastUsedChain() {
		if (mLastUsedChain == null) {
			Log.w(LOG_TAG, "getLastUsedChain(): last used chain is empty");
		}
		return mLastUsedChain;
	}
	
	public static void addCertificateChainToKeystore(Context ctx, X509Certificate[] chain) throws CertificateException {
		loadLocalKeystore(ctx);
        try
        {
            for (X509Certificate element : chain)
            {
                mKeystore.setCertificateEntry
                (element.getSubjectDN().toString(), element);
            }

            // recreate TrustManager with special keystore after adding new certificate chain
            javax.net.ssl.TrustManagerFactory trustmgrfactory = javax.net.ssl.TrustManagerFactory.getInstance("X509");
            trustmgrfactory.init(mKeystore);
            mSpecialKeystoreTrustManager = createTrustManager( mKeystore );
            
            java.io.FileOutputStream keyStoreStream;
            try
            {
                keyStoreStream = new java.io.FileOutputStream(fileKeystore);
                mKeystore.store(keyStoreStream, "".toCharArray());
                keyStoreStream.close();
            }
            catch (FileNotFoundException e)
            {
            	Log.e(LOG_TAG, "FileNotFoundException while writing KeyStore: ", e);
                throw new CertificateException("FileNotFoundException while writing KeyStore: " + e.getMessage());
            }
            catch (CertificateException e)
            {
            	Log.e(LOG_TAG, "CertificateException while adding new chain to KeyStore: ", e);
                throw new CertificateException("CertificateException while adding new chain to KeyStore: " + e.getMessage());
            }
            catch (IOException e)
            {
            	Log.e(LOG_TAG, "IOException while adding new chain to KeyStore: ", e);
                throw new CertificateException("IOException while adding new chain to KeyStore: " + e.getMessage());
            }

        }
		catch (KeyStoreException e)
        {
            Log.e(LOG_TAG, "KeyStoreException while adding new chain to keystore: ", e);
        }
		catch (NoSuchAlgorithmException e)
        {
            Log.e(LOG_TAG, "NoSuchAlgorithmException while adding new chain to keystore: ", e);
        }
	}

	private static X509TrustManager createTrustManager(KeyStore ks) {
		try {
			javax.net.ssl.TrustManagerFactory trustmgrfactory = javax.net.ssl.TrustManagerFactory.getInstance("X509");
			trustmgrfactory.init( ks );
			TrustManager[] trustmgrlist = trustmgrfactory.getTrustManagers();
			if (trustmgrlist != null)
			{
				for (TrustManager trustmgr : trustmgrlist)
				{
					if (trustmgr instanceof X509TrustManager)
					{
						return (X509TrustManager)trustmgr;
					}
				}
			}
		}
		catch (KeyStoreException e)
        {
            Log.e(LOG_TAG, "KeyStoreException while loading/initialising trustmanagers: ", e);
        }
		catch (NoSuchAlgorithmException e)
        {
            Log.e(LOG_TAG, "NoSuchAlgorithmException while getting X509 instance: ", e);
        }
		return (X509TrustManager)null;
	}
    
	
	public static void loadLocalKeystore(Context ctx) throws CertificateException {
		if(mKeystore != null) return; // loaded, nothing to do
		try {
	    	java.io.FileInputStream filestream;
	  
	    	fileKeystore = new File(ctx.getDir("keystore", Context.MODE_PRIVATE) + File.separator + "kolabdroid.bks");
	    	// get KeyStore instance that will be loaded with our own local KeyStore
	    	mKeystore = KeyStore.getInstance(KeyStore.getDefaultType());

            try
            {
                filestream = new java.io.FileInputStream(fileKeystore);
            }
            catch (FileNotFoundException e1)
            {
                filestream = null;
            }
            try
            {
                mKeystore.load(filestream, "".toCharArray());
            }
            catch (IOException e)
            {
                Log.e(LOG_TAG, "IOException in while loading keystore: ", e);
                mKeystore = null;
            }
            catch (CertificateException e)
            {
                Log.e(LOG_TAG, "CertificateException while loading keystore: ", e);
                mKeystore = null;
            }

            // create and initialize TMFactory with our own keystore 
            // ==> our keystore should accept certificates added by user
			mSpecialKeystoreTrustManager = createTrustManager(mKeystore);
			
            // create and initialise a default TMFactory with system wide keystore 
            // ==> this TM will used by default and falls back to mSpecialKeystoreTrustManager 
            mDefaultTrustManager = createTrustManager( (KeyStore)null );
		}
		catch (KeyStoreException e)
        {
            Log.e(LOG_TAG, "KeyStoreException while loading local keystore: ", e);
        }
		catch (NoSuchAlgorithmException e)
        {
            Log.e(LOG_TAG, "NoSuchAlgorithmException while loading local keystore: ", e);
        }
    }
	
	public static X509TrustManager get() {
		return SpecialX509TrustManager.getInstance();
	}
}
