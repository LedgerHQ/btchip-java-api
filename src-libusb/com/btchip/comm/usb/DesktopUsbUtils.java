/*
*******************************************************************************    
*   BTChip Bitcoin Hardware Wallet Java API
*   (c) 2014 BTChip - 1BTChip7VfTnrPra5jqci7ejnMguuHogTn
*   
*  Licensed under the Apache License, Version 2.0 (the "License");
*  you may not use this file except in compliance with the License.
*  You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
*   Unless required by applicable law or agreed to in writing, software
*   distributed under the License is distributed on an "AS IS" BASIS,
*   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*  See the License for the specific language governing permissions and
*   limitations under the License.
********************************************************************************
*/

package com.btchip.comm.usb;

import java.nio.ByteBuffer;
import java.util.Vector;

import org.usb4java.Context;
import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;
import org.usb4java.DeviceList;
import org.usb4java.LibUsb;

import com.btchip.BTChipException;

public class DesktopUsbUtils {
	
	private static Context context;
		
	public static Device[] enumDevices(int vid, int pid) throws BTChipException {
		Vector<Device> devices = new Vector<Device>();
		DeviceList list = new DeviceList();
		int result = LibUsb.getDeviceList(null, list);
		if (result < 0) {
			throw new BTChipException("Unable to get device list");
		}
		for (Device device : list) {
			DeviceDescriptor descriptor = new DeviceDescriptor();
			result = LibUsb.getDeviceDescriptor(device, descriptor);
			if (result < 0) {
				continue;
			}
			if ((descriptor.idVendor() == vid) && (descriptor.idProduct() == pid)) {
				devices.add(device);
			}
		}
		return devices.toArray(new Device[0]);
	}
	
	public static String getDeviceId(Device device) {
		StringBuffer result = new StringBuffer();
		ByteBuffer deviceListBuffer = ByteBuffer.allocateDirect(7);
		int size = LibUsb.getPortNumbers(device, deviceListBuffer);
		for (int i=0; i<size; i++) {
			result.append(deviceListBuffer.get());
			if (i != (size - 1)) {
				result.append('/');
			}
		}
		return result.toString();		
	}
	
	public static void exit() {
		LibUsb.exit(context);
	}
	
	static {
		context = new Context();
		LibUsb.init(context);
	}
}
