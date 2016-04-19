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

import java.util.Set;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

/**
 * 这个活动是一个对话框。它列出任何配对设备和设备检测后发现。由用户选择设备时,设备的MAC地址被发送回父母的活动 结果意图。
 */
public class DeviceListActivity extends Activity {
	// Debugging
	private static final String TAG = "DeviceListActivity";
	private static final boolean D = true;

	// 返回额外的意图
	public static String EXTRA_DEVICE_ADDRESS = "device_address";

	// 成员字段
	private BluetoothAdapter mBtAdapter;
	private ArrayAdapter<String> mPairedDevicesArrayAdapter;
	private ArrayAdapter<String> mNewDevicesArrayAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// 设置窗口
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.device_list);

		// 结果取消了装进箱用户支持
		setResult(Activity.RESULT_CANCELED);

		// 初始化按钮执行设备发现
		Button scanButton = (Button) findViewById(R.id.button_scan);
		scanButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				doDiscovery();
				v.setVisibility(View.GONE);
			}
		});

		// 数组初始化适配器。一个已配对设备和一个新发现的设备

		mPairedDevicesArrayAdapter = new ArrayAdapter<String>(this,
				R.layout.device_name);
		mNewDevicesArrayAdapter = new ArrayAdapter<String>(this,
				R.layout.device_name);

		// 找到并设置为配对设备列表视图
		ListView pairedListView = (ListView) findViewById(R.id.paired_devices);
		pairedListView.setAdapter(mPairedDevicesArrayAdapter);
		pairedListView.setOnItemClickListener(mDeviceClickListener);

		// 找到并设置为新发现的设备列表视图
		ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
		newDevicesListView.setAdapter(mNewDevicesArrayAdapter);
		newDevicesListView.setOnItemClickListener(mDeviceClickListener);

		// 注册广播设备时发现
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		this.registerReceiver(mReceiver, filter);

		// 注册广播时发现已经完成
		filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		this.registerReceiver(mReceiver, filter);

		// 本地蓝牙适配器
		mBtAdapter = BluetoothAdapter.getDefaultAdapter();

		// 得到一组目前配对设备
		Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();

		// 如果有配对的设备,每一个添加到ArrayAdapter
		if (pairedDevices.size() > 0) {
			findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
			for (BluetoothDevice device : pairedDevices) {
				mPairedDevicesArrayAdapter.add(device.getName() + "\n"
						+ device.getAddress());
			}
		} else {
			String noDevices = getResources().getText(R.string.none_paired)
					.toString();
			mPairedDevicesArrayAdapter.add(noDevices);
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		// 确保我们不做发现了
		if (mBtAdapter != null) {
			mBtAdapter.cancelDiscovery();
		}

		// 注销广播听众
		this.unregisterReceiver(mReceiver);
	}

	/**
	 * 开始与BluetoothAdapter设备发现
	 */
	private void doDiscovery() {
		if (D)
			Log.d(TAG, "doDiscovery()");

		// 显示扫描标题中
		setProgressBarIndeterminateVisibility(true);
		setTitle(R.string.scanning);

		// 为新设备打开字幕
		findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);

		// 如果我们已经发现,阻止它
		if (mBtAdapter.isDiscovering()) {
			mBtAdapter.cancelDiscovery();
		}

		// 从BluetoothAdapter请求发现
		mBtAdapter.startDiscovery();
	}

	// 点击监听器列表视图中的所有设备
	private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {
		public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
			//取消的发现,因为它是昂贵的和我们联系
			mBtAdapter.cancelDiscovery();

			//设备的MAC地址,最后17个字符
			// View
			String info = ((TextView) v).getText().toString();
			String address = info.substring(info.length() - 17);

			// 创建结果意图,包括MAC地址
			Intent intent = new Intent();
			intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

			// 结果和完成这个活动
			setResult(Activity.RESULT_OK, intent);
			finish();
		}
	};

	// BroadcastReceiver侦听发现设备和
	//当发现完成更改标题
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			// 当发现设备
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				// 从意图BluetoothDevice对象
				BluetoothDevice device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				// 如果它已经配对,跳过它,因为它已经上市
				//已经
				
				if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
					mNewDevicesArrayAdapter.add(device.getName() + "\n"
							+ device.getAddress());
				}
				// 当完成发现,改变活动名称
			} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED
					.equals(action)) {
				setProgressBarIndeterminateVisibility(false);
				setTitle(R.string.select_device);
				if (mNewDevicesArrayAdapter.getCount() == 0) {
					String noDevices = getResources().getText(
							R.string.none_found).toString();
					mNewDevicesArrayAdapter.add(noDevices);
				}
			}
		}
	};

}
