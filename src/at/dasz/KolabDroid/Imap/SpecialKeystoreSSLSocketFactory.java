/*
 * Copyright 2010 Sönke Schwardt-Krummrich <soenke@schwardtnet.de>; all rights reserved
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

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import android.util.Log;

public class SpecialKeystoreSSLSocketFactory extends SSLSocketFactory
{
	private static final String LOG_TAG = at.dasz.KolabDroid.Utils.LOG_TAG_SPECIAL_KEYSTORE_SSL_SOCKETFACTORY;

	SSLSocketFactory mSocketFactory = null;
	
	public SpecialKeystoreSSLSocketFactory() {
		Log.v(LOG_TAG, "Constructor");
	}
	
	private void initSocketFactory() {
		if (mSocketFactory != null) {
			Log.v(LOG_TAG, "socket factory has been already initialised");
			return;
		}
		try {
			Log.v(LOG_TAG, "local keystore has been loaded");
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, new TrustManager[] {
                            	TrustManagerFactory.get()
                        	}, new SecureRandom());
			mSocketFactory = sslContext.getSocketFactory();
			Log.v(LOG_TAG, "created socket factory");
		}
		catch (NoSuchAlgorithmException e) {
			Log.e(LOG_TAG, "NoSuchAlgorithmException in initSocketFactory: ", e);
		}
		catch (KeyManagementException e) {
			Log.e(LOG_TAG, "KeyManagementException in initSocketFactory: ", e);
		}
	}

	public Socket createSocket() throws IOException
    {
		initSocketFactory();
	    return mSocketFactory.createSocket();
    }

	
	@Override
	public Socket createSocket(Socket arg0, String arg1, int arg2, boolean arg3) throws IOException
	{
		initSocketFactory();
		return mSocketFactory.createSocket(arg0, arg1, arg2, arg3);
	}

	@Override
	public String[] getSupportedCipherSuites()
	{
		return mSocketFactory.getSupportedCipherSuites();
	}

	@Override
	public Socket createSocket(String host, int port) throws IOException, UnknownHostException
	{
		initSocketFactory();
		return mSocketFactory.createSocket(host, port);
	}

	@Override
	public Socket createSocket(InetAddress host, int port) throws IOException
	{
		initSocketFactory();
		return mSocketFactory.createSocket(host, port);
	}

	@Override
	public Socket createSocket(String host, int port, InetAddress localHost,
			int localPort) throws IOException, UnknownHostException
	{
		initSocketFactory();
		return mSocketFactory.createSocket(host, port, localHost, localPort);
	}

	@Override
	public Socket createSocket(InetAddress address, int port,
			InetAddress localAddress, int localPort) throws IOException
	{
		initSocketFactory();
		return mSocketFactory.createSocket(address, port, localAddress, localPort);
	}

	@Override
	public String[] getDefaultCipherSuites()
	{
		if (mSocketFactory != null) {
			return mSocketFactory.getDefaultCipherSuites();
		}
		return null;
	}

}
