/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.BluetoothChat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * 这门课做所有的工作对蓝牙的设置和管理 与其他设备连接。它有一个线程,监听 传入的连接、螺纹连接的设备, 当连接线程来执行数据传输。
 */
public class BluetoothChatService {
	// Debugging
	private static final String TAG = "BluetoothChatService";
	private static final boolean D = true;

	// SDP的名字记录在创建服务器套接字
	private static final String NAME = "BluetoothChat";

	// 独特的UUID对于这个应用程序

	private static final UUID MY_UUID = UUID
			.fromString("00001101-0000-1000-8000-00805F9B34FB");

	// 成员字段
	private final BluetoothAdapter mAdapter;
	private final Handler mHandler;
	private AcceptThread mAcceptThread;
	private ConnectThread mConnectThread;
	private ConnectedThread mConnectedThread;
	private int mState;

	// 常量,显示当前的连接状态
	public static final int STATE_NONE = 0; // 我们什么都不做
	public static final int STATE_LISTEN = 1; // 现在监听传入的连接
	public static final int STATE_CONNECTING = 2; // 现在开始一个即将离任的连接
	public static final int STATE_CONNECTED = 3; // 现在连接到一个远程设备

	/**
	 * 构造函数。准备一个新的BluetoothChat会话。
	 * 
	 * @param上下文UI活动上下文
	 * @param处理程序处理程序发送消息回UI的活动
	 */
	public BluetoothChatService(Context context, Handler handler) {
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mState = STATE_NONE;
		mHandler = handler;
	}

	/**
	 * 设置的当前状态连接聊天
	 * 
	 * @param状态整数定义当前的连接状态
	 */
	private synchronized void setState(int state) {
		if (D)
			Log.d(TAG, "setState() " + mState + " -> " + state);
		mState = state;

		// 给新状态处理程序的UI活动可以更新
		mHandler.obtainMessage(BluetoothChat.MESSAGE_STATE_CHANGE, state, -1)
				.sendToTarget();
	}

	/**
	 * 返回当前连接状态。
	 */
	public synchronized int getState() {
		return mState;
	}

	/**
	 * 开始聊天服务。具体开始AcceptThread开始 会话在听(服务器)模式。由活动onResume调用()
	 */
	public synchronized void start() {
		if (D)
			Log.d(TAG, "start");

		// 取消任何线程试图建立连接
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}

		// 取消任何线程正在运行一个连接
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		// 启动线程BluetoothServerSocket听
		if (mAcceptThread == null) {
			mAcceptThread = new AcceptThread();
			mAcceptThread.start();
		}
		setState(STATE_LISTEN);
	}

	/**
	 * 启动ConnectThread发起一个连接到远程设备。
	 * 
	 * @param BluetoothDevice连接装置
	 */
	public synchronized void connect(BluetoothDevice device) {
		if (D)
			Log.d(TAG, "connect to: " + device);

		// 取消任何线程试图建立连接
		if (mState == STATE_CONNECTING) {
			if (mConnectThread != null) {
				mConnectThread.cancel();
				mConnectThread = null;
			}
		}

		// 取消任何线程正在运行一个连接
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		//启动线程与给定的设备
		mConnectThread = new ConnectThread(device);
		mConnectThread.start();
		setState(STATE_CONNECTING);
	}

	/**
	 * 启动ConnectedThread开始管理一个蓝牙连接
	 * 
	 * @param插座连接的BluetoothSocket
	 * @param设备的BluetoothDevice连接
	 */
	public synchronized void connected(BluetoothSocket socket,
			BluetoothDevice device) {
		if (D)
			Log.d(TAG, "connected");

		// 取消的线程完成连接
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}

		// 取消任何线程正在运行一个连接
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		// 取消接受线程,因为我们只希望连接到一个设备
		if (mAcceptThread != null) {
			mAcceptThread.cancel();
			mAcceptThread = null;
		}

		// 启动线程管理连接和执行传输
		mConnectedThread = new ConnectedThread(socket);
		mConnectedThread.start();

		// 连接设备的名称发送回UI的活动
		Message msg = mHandler.obtainMessage(BluetoothChat.MESSAGE_DEVICE_NAME);
		Bundle bundle = new Bundle();
		bundle.putString(BluetoothChat.DEVICE_NAME, device.getName());
		msg.setData(bundle);
		mHandler.sendMessage(msg);

		setState(STATE_CONNECTED);
	}

	/**
	 * 停止所有线程
	 */
	public synchronized void stop() {
		if (D)
			Log.d(TAG, "stop");
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}
		if (mAcceptThread != null) {
			mAcceptThread.cancel();
			mAcceptThread = null;
		}
		setState(STATE_NONE);
	}

	/**
	 * 以非同步方式ConnectedThread写
	 * 
	 * @param字节写
	 * @see ConnectedThread #写(byte[])
	 */
	public void write(byte[] out) {
		// 创建临时对象
		ConnectedThread r;
		// 同步连接线程的一个副本
		synchronized (this) {
			if (mState != STATE_CONNECTED)
				return;
			r = mConnectedThread;
		}
		// 执行写同步
		r.write(out);
	}

	/**
	 * 表明,该连接请求失败并通知UI的活动。
	 */
	private void connectionFailed() {
		setState(STATE_LISTEN);

		// 发送失败消息回的活动
		Message msg = mHandler.obtainMessage(BluetoothChat.MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(BluetoothChat.TOAST, "Unable to connect device");
		msg.setData(bundle);
		mHandler.sendMessage(msg);
	}

	/**
	 * 表明,网络连接失败了并通知UI的活动。
	 */
	private void connectionLost() {
		setState(STATE_LISTEN);

		// 发送失败消息回的活动
		Message msg = mHandler.obtainMessage(BluetoothChat.MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(BluetoothChat.TOAST, "Device connection was lost");
		msg.setData(bundle);
		mHandler.sendMessage(msg);
	}

	/**
	 * 这个线程运行,监听传入的连接。它像一个服务器端端。 它运行到一个连接被接受(或者直到取消)。
	 */
	private class AcceptThread extends Thread {
		// The local server socket
		private final BluetoothServerSocket mmServerSocket;

		public AcceptThread() {
			BluetoothServerSocket tmp = null;

			// 创建一个新的监听服务器套接字
			try {
				tmp = mAdapter
						.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
			} catch (IOException e) {
				Log.e(TAG, "listen() failed", e);
			}
			mmServerSocket = tmp;
		}

		public void run() {
			if (D)
				Log.d(TAG, "BEGIN mAcceptThread" + this);
			setName("AcceptThread");
			BluetoothSocket socket = null;

			// 听服务器套接字如果我们不联系
			while (mState != STATE_CONNECTED) {
				try {
					// 这是一个阻塞调用,只返回一个成功连接上或一个例外
					socket = mmServerSocket.accept();
				} catch (IOException e) {
					Log.e(TAG, "accept() failed", e);
					break;
				}

				// 如果一个连接被接受
				if (socket != null) {
					synchronized (BluetoothChatService.this) {
						switch (mState) {
						case STATE_LISTEN:
						case STATE_CONNECTING:
							// 情况正常。开始连接线程。
							connected(socket, socket.getRemoteDevice());
							break;
						case STATE_NONE:
						case STATE_CONNECTED:
							// 没有准备好或已经连接。终止新的套接字。.
							try {
								socket.close();
							} catch (IOException e) {
								Log.e(TAG, "Could not close unwanted socket", e);
							}
							break;
						}
					}
				}
			}
			if (D)
				Log.i(TAG, "END mAcceptThread");
		}

		public void cancel() {
			if (D)
				Log.d(TAG, "cancel " + this);
			try {
				mmServerSocket.close();
			} catch (IOException e) {
				Log.e(TAG, "close() of server failed", e);
			}
		}
	}

	/**
	 * 这个线程运行时试图做一个外向的连接 一个设备。它运行直通;连接 成功或失败。
	 */
	private class ConnectThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final BluetoothDevice mmDevice;

		public ConnectThread(BluetoothDevice device) {
			mmDevice = device;
			BluetoothSocket tmp = null;

			// 得到一个BluetoothSocket连接的
			// 鉴于BluetoothDevice
			try {
				tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
			} catch (IOException e) {
				Log.e(TAG, "create() failed", e);
			}
			mmSocket = tmp;
		}

		public void run() {
			Log.i(TAG, "BEGIN mConnectThread");
			setName("ConnectThread");

			// 总是取消发现因为它会减慢一个连接
			mAdapter.cancelDiscovery();

			// BluetoothSocket连接
			try {
				// 这是一个阻塞调用,只会回报
				// 成功的连接或一个例外
				mmSocket.connect();
			} catch (IOException e) {
				connectionFailed();
				// 关闭套接字
				try {
					mmSocket.close();
				} catch (IOException e2) {
					Log.e(TAG,
							"unable to close() socket during connection failure",
							e2);
				}
				// 在重新启动监听模式启动该服务
				BluetoothChatService.this.start();
				return;
			}

			// 重置ConnectThread因为做完了
			synchronized (BluetoothChatService.this) {
				mConnectThread = null;
			}

			// 开始连接线程
			connected(mmSocket, mmDevice);
		}

		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) {
				Log.e(TAG, "close() of connect socket failed", e);
			}
		}
	}

	/**
	 * 这个线程运行在一个远程设备的连接。它处理所有 传入和传出的传输。
	 */
	private class ConnectedThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;

		public ConnectedThread(BluetoothSocket socket) {
			Log.d(TAG, "create ConnectedThread");
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			// 得到BluetoothSocket输入和输出流
			try {
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			} catch (IOException e) {
				Log.e(TAG, "temp sockets not created", e);
			}

			mmInStream = tmpIn;
			mmOutStream = tmpOut;
		}

		public void run() {
			Log.i(TAG, "BEGIN mConnectedThread");
			byte[] buffer = new byte[1024];
			int bytes;

			// 继续听InputStream同时连接
			while (true) {
				try {
					// 从InputStream读取
					bytes = mmInStream.read(buffer);

					// 将获得的字节发送到UI的活动
					mHandler.obtainMessage(BluetoothChat.MESSAGE_READ, bytes,
							-1, buffer).sendToTarget();
				} catch (IOException e) {
					Log.e(TAG, "disconnected", e);
					connectionLost();
					break;
				}
			}
		}

		/**
		 * 写OutStream相连。
		 * 
		 * @param缓冲 字节写
		 */
		public void write(byte[] buffer) {
			try {
				mmOutStream.write(buffer);

				// 把消息送回UI活动分享
				mHandler.obtainMessage(BluetoothChat.MESSAGE_WRITE, -1, -1,
						buffer).sendToTarget();
			} catch (IOException e) {
				Log.e(TAG, "Exception during write", e);
			}
		}

		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) {
				Log.e(TAG, "close() of connect socket failed", e);
			}
		}
	}
}
