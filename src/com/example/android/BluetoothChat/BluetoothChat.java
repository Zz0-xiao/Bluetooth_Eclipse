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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * This is the main Activity that displays the current chat session.
 */
@TargetApi(Build.VERSION_CODES.ECLAIR)
public class BluetoothChat extends Activity {
	// 调试以排除故障
	private static final String TAG = "BluetoothChat";
	private static final boolean D = true;

	// 消息类型从蓝牙发送聊天服务处理程序
	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_TOAST = 5;

	// 键名收到蓝牙聊天服务处理程序
	public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";

	// 意图请求代码
	private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;

	// Layout Views
	private TextView mTitle;
	private ListView mConversationView;
	private EditText mOutEditText;
	private Button mSendButton;

	// 连接设备的名称
	private String mConnectedDeviceName = null;
	// 数组适配器对话线程
	private ArrayAdapter<String> mConversationArrayAdapter;
	// 字符串缓冲区传出消息
	private StringBuffer mOutStringBuffer;
	// 本地蓝牙适配器
	private BluetoothAdapter mBluetoothAdapter = null;
	// 成员对象聊天服务
	private BluetoothChatService mChatService = null;

	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (D)
			Log.e(TAG, "+++ ON CREATE +++");

		// 设置窗口的布局
		requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
		setContentView(R.layout.main);
		getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE,
				R.layout.custom_title);

		// 设置自定义标题
		mTitle = (TextView) findViewById(R.id.title_left_text);
		mTitle.setText(R.string.app_name);
		mTitle = (TextView) findViewById(R.id.title_right_text);

		// 获取本地蓝牙适配器
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		// 如果适配器是null,那么不支持蓝牙
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, "Bluetooth is not available",
					Toast.LENGTH_LONG).show();
			finish();
			return;
		}
	}

	@SuppressLint("NewApi")
	@Override
	public void onStart() {
		super.onStart();
		if (D)
			Log.e(TAG, "++ ON START ++");

		// 如果不是BT,请求它被启用。
		// setupChat() will then be called during onActivityResult
		// setupChat在onActivityResult()将被调用 上面的翻译
		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			// 否则,设置聊天会话
		} else {
			if (mChatService == null)
				setupChat();
		}
	}

	@Override
	public synchronized void onResume() {
		super.onResume();
		if (D)
			Log.e(TAG, "+ ON RESUME +");

		// 执行这种检查在onResume()覆盖的情况下BT
		// 未启用期间onStart(),所以我们停下来启用它……
		// onResume ACTION_REQUEST_ENABLE活动时()将返回。
		if (mChatService != null) {
			// 只有状态是STATE_NONE,我们已经知道,我们还没有开始
			if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
				// 启动蓝牙聊天服务
				mChatService.start();
			}
		}
	}

	@SuppressLint("NewApi")
	private void setupChat() {
		Log.d(TAG, "setupChat()");

		// 数组初始化适配器对话线程
		mConversationArrayAdapter = new ArrayAdapter<String>(this,
				R.layout.message);
		mConversationView = (ListView) findViewById(R.id.in);
		mConversationView.setAdapter(mConversationArrayAdapter);

		// 初始化组合字段返回键的一个侦听器
		mOutEditText = (EditText) findViewById(R.id.edit_text_out);
		mOutEditText.setOnEditorActionListener(mWriteListener);

		// 初始化发送按钮单击事件的监听器
		mSendButton = (Button) findViewById(R.id.button_send);
		mSendButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				// 发送消息使用的内容编辑文本小部件
				TextView view = (TextView) findViewById(R.id.edit_text_out);
				String message = view.getText().toString();
				sendMessage(message);
			}
		});

		// 初始化蓝牙聊天服务进行蓝牙连接
		mChatService = new BluetoothChatService(this, mHandler);

		// 初始化缓冲传出消息
		mOutStringBuffer = new StringBuffer("");
	}

	@Override
	public synchronized void onPause() {
		super.onPause();
		if (D)
			Log.e(TAG, "- ON PAUSE -");
	}

	@Override
	public void onStop() {
		super.onStop();
		if (D)
			Log.e(TAG, "-- ON STOP --");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		// 停止蓝牙聊天服务
		if (mChatService != null)
			mChatService.stop();
		if (D)
			Log.e(TAG, "--- ON DESTROY ---");
	}

	private void ensureDiscoverable() {
		if (D)
			Log.d(TAG, "ensure discoverable");
		if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
			Intent discoverableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			discoverableIntent.putExtra(
					BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
			startActivity(discoverableIntent);
		}
	}

	/**
	 * 发送一条消息。
	 * 
	 * @param消息字符串的文本发送。
	 */
	private void sendMessage(String message) {
		// 检查我们之前任何连接
		if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
			Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT)
					.show();
			return;
		}

		// 检查是否有东西送
		if (message.length() > 0) {
			// 得到消息的字节,告诉BluetoothChatService写
			byte[] send = message.getBytes();
			mChatService.write(send);

			// 重置了字符串缓冲区为零并清除编辑文本字段
			mOutStringBuffer.setLength(0);
			mOutEditText.setText(mOutStringBuffer);
		}
	}

	// EditText部件的动作侦听器,侦听返回键
	private TextView.OnEditorActionListener mWriteListener = new TextView.OnEditorActionListener() {
		public boolean onEditorAction(TextView view, int actionId,
				KeyEvent event) {
			// 如果操作是一个激励的事件返回键,发送消息
			if (actionId == EditorInfo.IME_NULL
					&& event.getAction() == KeyEvent.ACTION_UP) {
				String message = view.getText().toString();
				sendMessage(message);
			}
			if (D)
				Log.i(TAG, "END onEditorAction");
			return true;
		}
	};

	// 处理程序从BluetoothChatService回来的信息
	private final Handler mHandler = new Handler() {
		@Override 
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_STATE_CHANGE:
				if (D)
					Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
				switch (msg.arg1) {
				case BluetoothChatService.STATE_CONNECTED:
					mTitle.setText(R.string.title_connected_to);
					mTitle.append(mConnectedDeviceName);
					mConversationArrayAdapter.clear();
					break;
				case BluetoothChatService.STATE_CONNECTING:
					mTitle.setText(R.string.title_connecting);
					break;
				case BluetoothChatService.STATE_LISTEN:
				case BluetoothChatService.STATE_NONE:
					mTitle.setText(R.string.title_not_connected);
					break;
				}
				break;
			case MESSAGE_WRITE:
				byte[] writeBuf = (byte[]) msg.obj;
				// 构造一个字符串缓冲区
				String writeMessage = new String(writeBuf);
				mConversationArrayAdapter.add("Me:  " + writeMessage);
				break;
			case MESSAGE_READ:
				byte[] readBuf = (byte[]) msg.obj;
				// 构造一个字符串有效字节的缓冲区
				String readMessage = new String(readBuf, 0, msg.arg1);
				mConversationArrayAdapter.add(mConnectedDeviceName + ":  "
						+ readMessage);
				break;
			case MESSAGE_DEVICE_NAME:
				// 保存连接设备的名字
				mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
				Toast.makeText(getApplicationContext(),
						"Connected to " + mConnectedDeviceName,
						Toast.LENGTH_SHORT).show();
				break;
			case MESSAGE_TOAST:
				Toast.makeText(getApplicationContext(),
						msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
						.show();
				break;
			}
		}
	};

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (D)
			Log.d(TAG, "onActivityResult " + resultCode);
		switch (requestCode) {
		case REQUEST_CONNECT_DEVICE:
			// 当DeviceListActivity返回设备连接
			if (resultCode == Activity.RESULT_OK) {
				// 设备的MAC地址
				String address = data.getExtras().getString(
						DeviceListActivity.EXTRA_DEVICE_ADDRESS);
				// 得到BLuetoothDevice对象
				BluetoothDevice device = mBluetoothAdapter
						.getRemoteDevice(address);
				// 试图连接到设备
				mChatService.connect(device);
			}
			break;
		case REQUEST_ENABLE_BT:
			// 当请求启用蓝牙的回报
			if (resultCode == Activity.RESULT_OK) {
				// 蓝牙现在启用,所以建立一个聊天会话
				setupChat();
			} else {
				// 用户不启用蓝牙或发生一个错误
				Log.d(TAG, "BT not enabled");
				Toast.makeText(this, R.string.bt_not_enabled_leaving,
						Toast.LENGTH_SHORT).show();
				finish();
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.option_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.scan:
			// 启动DeviceListActivity看到设备和做扫描
			Intent serverIntent = new Intent(this, DeviceListActivity.class);
			startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
			return true;
		case R.id.discoverable:
			// 确保该设备容易被别人
			ensureDiscoverable();
			return true;
		}
		return false;
	}

}