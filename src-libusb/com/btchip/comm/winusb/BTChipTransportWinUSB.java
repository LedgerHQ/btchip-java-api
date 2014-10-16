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

package com.btchip.comm.winusb;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.usb4java.Device;
import org.usb4java.DeviceHandle;
import org.usb4java.LibUsb;

import com.btchip.BTChipException;
import com.btchip.comm.BTChipTransport;
import com.btchip.comm.usb.DesktopUsbUtils;
import com.btchip.utils.Dump;

public class BTChipTransportWinUSB implements BTChipTransport {

	private DeviceHandle handle;
	private int timeout;
	private ByteBuffer responseBuffer;
	private IntBuffer sizeBuffer;
	private boolean debug;
	
	private static Logger log = LoggerFactory.getLogger(BTChipTransportWinUSB.class);
	
	BTChipTransportWinUSB(DeviceHandle handle, int timeout) {
		this.handle = handle;
		this.timeout = timeout;
		responseBuffer = ByteBuffer.allocateDirect(260);
		sizeBuffer = IntBuffer.allocate(1);
	}

	@Override
	public byte[] exchange(byte[] command) throws BTChipException {
		byte[] response;
		if (debug) {
			log.debug("=> {}", Dump.dump(command));
		}
		ByteBuffer commandBuffer = ByteBuffer.allocateDirect(command.length);
		sizeBuffer.clear();
		commandBuffer.put(command);
		int result = LibUsb.bulkTransfer(handle, ENDPOINT_OUT, commandBuffer, sizeBuffer, timeout);
		if (result != LibUsb.SUCCESS) {
			throw new BTChipException("Write failed");
		}
		responseBuffer.clear();
		sizeBuffer.clear();
		result = LibUsb.bulkTransfer(handle, ENDPOINT_IN, responseBuffer, sizeBuffer, timeout);
		if (result != LibUsb.SUCCESS) {
			throw new BTChipException("Read failed");
		}
		int sw1 = (int)(responseBuffer.get() & 0xff);
		int sw2 = (int)(responseBuffer.get() & 0xff);
		if (sw1 != SW1_DATA_AVAILABLE) {
			response = new byte[2];
			response[0] = (byte)sw1;
			response[1] = (byte)sw2;						
		}
		else {
			response = new byte[sw2 + 2];
			responseBuffer.get(response);
		}
		if (debug) {
			log.debug("<= {}", Dump.dump(response));
		}
		return response;		
	}
	
	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	@Override
	public void close() throws BTChipException {
		LibUsb.close(handle);
	}
	
	public static String[] listDevices() throws BTChipException {
		Device devices[] = DesktopUsbUtils.enumDevices(VID, PID);
		Vector<String> result = new Vector<String>();
		for (Device device : devices) {
			result.add(DesktopUsbUtils.getDeviceId(device));
		}
		return result.toArray(new String[0]);
	}
	
	public static BTChipTransportWinUSB openDevice(String deviceName) throws BTChipException {
		Device devices[] = DesktopUsbUtils.enumDevices(VID, PID);
		Device targetDevice = null;
		for (Device device : devices) {
			if ((deviceName == null) || (deviceName.length() == 0) || (DesktopUsbUtils.getDeviceId(device).equals(deviceName))) {
				targetDevice = device;
				break;
			}
		}
		if (targetDevice == null) {
			throw new BTChipException("Device not found");
		}
		DeviceHandle handle = new DeviceHandle();
		int result = LibUsb.open(targetDevice, handle);
		if (result != LibUsb.SUCCESS) {
			throw new BTChipException("Failed to open device");
		}
		return new BTChipTransportWinUSB(handle, TIMEOUT);
	}
	
	public static BTChipTransportWinUSB openDevice() throws BTChipException {
		return openDevice(null);
	}
	
	public static void main(String args[]) throws Exception {
		BTChipTransportWinUSB device = openDevice();
		byte[] result = device.exchange(Dump.hexToBin("e0c4000000"));
		System.out.println(Dump.dump(result));
		device.close();
	}
	
	private static final int VID = 0x2581;
	private static final int PID = 0x1b7c;
	private static final byte ENDPOINT_OUT = (byte)0x02;
	private static final byte ENDPOINT_IN = (byte)0x82;
	private static final int SW1_DATA_AVAILABLE = 0x61;
	private static final int TIMEOUT = 20000;
}
