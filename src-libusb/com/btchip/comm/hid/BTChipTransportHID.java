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

package com.btchip.comm.hid;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.usb4java.ConfigDescriptor;
import org.usb4java.Device;
import org.usb4java.DeviceHandle;
import org.usb4java.EndpointDescriptor;
import org.usb4java.Interface;
import org.usb4java.InterfaceDescriptor;
import org.usb4java.LibUsb;

import com.btchip.BTChipException;
import com.btchip.comm.BTChipTransport;
import com.btchip.comm.LedgerHelper;
import com.btchip.comm.usb.DesktopUsbUtils;
import com.btchip.utils.Dump;

public class BTChipTransportHID implements BTChipTransport {

	private DeviceHandle handle;
	private int timeout;
	private ByteBuffer responseBuffer;
	private IntBuffer sizeBuffer;
	private boolean debug;
	private byte outEndpoint;
	private byte inEndpoint;
	private boolean ledger;
	
	private static Logger log = LoggerFactory.getLogger(BTChipTransportHID.class);
	
	BTChipTransportHID(DeviceHandle handle, int timeout, byte inEndpoint, byte outEndpoint, boolean ledger) {
		this.handle = handle;
		this.timeout = timeout;
		this.inEndpoint = inEndpoint;
		this.outEndpoint = outEndpoint;
		this.ledger = ledger;
		 // Compatibility with old prototypes, to be removed		
		if (!this.ledger) {
			this.ledger = (inEndpoint & 0x7f) != (outEndpoint & 0x7f); 
		}
		responseBuffer = ByteBuffer.allocateDirect(HID_BUFFER_SIZE);
		sizeBuffer = IntBuffer.allocate(1);
	}

	@Override
	public byte[] exchange(byte[] command) throws BTChipException {
		ByteArrayOutputStream response = new ByteArrayOutputStream();
		byte[] responseData = null;
		int offset = 0;
		int responseSize;
		int result;
		if (debug) {
			log.debug("=> {}", Dump.dump(command));
		}
		if (ledger) {
			command = LedgerHelper.wrapCommandAPDU(LEDGER_DEFAULT_CHANNEL, command, HID_BUFFER_SIZE);
		}
		ByteBuffer commandBuffer = ByteBuffer.allocateDirect(HID_BUFFER_SIZE);
		byte[] chunk = new byte[HID_BUFFER_SIZE];
		while(offset != command.length) {
			int blockSize = (command.length - offset > HID_BUFFER_SIZE ? HID_BUFFER_SIZE : command.length - offset);
			System.arraycopy(command, offset, chunk, 0, blockSize);
			sizeBuffer.clear();
			commandBuffer.put(chunk);
			result = LibUsb.interruptTransfer(handle, outEndpoint, commandBuffer, sizeBuffer, timeout);
			if (result != LibUsb.SUCCESS) {
				throw new BTChipException("Write failed");
			}
			offset += blockSize;
			commandBuffer.clear();
			sizeBuffer.clear();
		}
		if (!ledger) {
			responseBuffer.clear();
			result = LibUsb.interruptTransfer(handle, inEndpoint, responseBuffer, sizeBuffer, timeout);
			if (result != LibUsb.SUCCESS) {
				throw new BTChipException("Read failed");
			}
			int sw1 = (int)(responseBuffer.get() & 0xff);
			int sw2 = (int)(responseBuffer.get() & 0xff);
			if (sw1 != SW1_DATA_AVAILABLE) {
				response.write(sw1);
				response.write(sw2);
			}
			else {
				responseSize = sw2 + 2;
				offset = 0;
				int blockSize = (responseSize > HID_BUFFER_SIZE - 2 ? HID_BUFFER_SIZE - 2 : responseSize);
				responseBuffer.get(chunk, 0, blockSize);
				response.write(chunk, 0, blockSize);
				offset += blockSize;
				responseBuffer.clear();
				sizeBuffer.clear();
				while (offset != responseSize) {
					result = LibUsb.interruptTransfer(handle, inEndpoint, responseBuffer, sizeBuffer, timeout);
					if (result != LibUsb.SUCCESS) {
						throw new BTChipException("Read failed");
					}
					blockSize = (responseSize - offset > HID_BUFFER_SIZE ? HID_BUFFER_SIZE : responseSize - offset);
					responseBuffer.get(chunk, 0, blockSize);
					response.write(chunk, 0, blockSize);
					offset += blockSize;				
				}
				responseBuffer.clear();
				sizeBuffer.clear();
			}
			responseData = response.toByteArray();
		}
		else {			
			while ((responseData = LedgerHelper.unwrapResponseAPDU(LEDGER_DEFAULT_CHANNEL, response.toByteArray(), HID_BUFFER_SIZE)) == null) {
				responseBuffer.clear();
				sizeBuffer.clear();
				result = LibUsb.interruptTransfer(handle, inEndpoint, responseBuffer, sizeBuffer, timeout);
				if (result != LibUsb.SUCCESS) {
					throw new BTChipException("Read failed");
				}
				responseBuffer.get(chunk, 0, HID_BUFFER_SIZE);
				response.write(chunk, 0, HID_BUFFER_SIZE);				
			}						
		}		
		if (debug) {
			log.debug("<= {}", Dump.dump(responseData));
		}
		return responseData;		
	}
	
	@Override
	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	@Override
	public void close() throws BTChipException {
		LibUsb.releaseInterface(handle, 0);
		LibUsb.attachKernelDriver(handle, 0);		
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

	public static Device matchDevice(String deviceName, int vid, int pid) throws BTChipException {
		Device devices[] = DesktopUsbUtils.enumDevices(vid, pid);
		Device targetDevice = null;
                for (Device device : devices) {
                        if ((deviceName == null) || (deviceName.length() == 0) || (DesktopUsbUtils.getDeviceId(device).equals(deviceName))) {
                                targetDevice = device;
                                break;
                        }
                }
		return targetDevice;
	}
	
	public static BTChipTransportHID openDevice(String deviceName) throws BTChipException {
		byte inEndpoint = (byte)0xff;
		byte outEndpoint = (byte)0xff;
		boolean ledger = false;
		Device targetDevice = matchDevice(deviceName, VID, PID);
		if (targetDevice == null) {
			targetDevice = matchDevice(deviceName, VID, PID_LEDGER);
			if (targetDevice != null) {
				ledger = true;
			}
			else {
				targetDevice = matchDevice(deviceName, VID, PID_LEDGER_PROTON);
				if (targetDevice != null) {
					ledger = true;
				}
			}
		}
		if (targetDevice == null) {
			throw new BTChipException("Device not found");
		}
		ConfigDescriptor configDescriptor = new ConfigDescriptor();
		int result = LibUsb.getActiveConfigDescriptor(targetDevice, configDescriptor);
		if (result != LibUsb.SUCCESS) {
			throw new BTChipException("Failed to get config descriptor");
		}
		Interface[] interfaces = configDescriptor.iface();
		for (Interface deviceInterface : interfaces) {
			for (InterfaceDescriptor interfaceDescriptor : deviceInterface.altsetting()) {
				for (EndpointDescriptor endpointDescriptor : interfaceDescriptor.endpoint()) {
					if ((endpointDescriptor.bEndpointAddress() & LibUsb.ENDPOINT_IN) != 0) {
						inEndpoint = endpointDescriptor.bEndpointAddress();
					}
					else {
						outEndpoint = endpointDescriptor.bEndpointAddress();
					}					
				}
			}
		}
		if (inEndpoint == (byte)0xff) {
			throw new BTChipException("Couldn't find IN endpoint");
		}
		if (outEndpoint == (byte)0xff) {
			throw new BTChipException("Couldn't find OUT endpoint");
		}
		
		DeviceHandle handle = new DeviceHandle();
		result = LibUsb.open(targetDevice, handle);
		if (result != LibUsb.SUCCESS) {
			throw new BTChipException("Failed to open device");
		}
		LibUsb.detachKernelDriver(handle, 0);
		LibUsb.claimInterface(handle, 0);					
		return new BTChipTransportHID(handle, TIMEOUT, inEndpoint, outEndpoint, ledger);
	}
	
	public static BTChipTransportHID openDevice() throws BTChipException {
		return openDevice(null);
	}
	
	public static void main(String args[]) throws Exception {
		BTChipTransportHID device = openDevice();
		device.setDebug(true);
		byte[] result = device.exchange(Dump.hexToBin("e0c4000000"));
		System.out.println(Dump.dump(result));
		device.close();
	}
	
	private static final int VID = 0x2581;
	private static final int PID = 0x2b7c;
	private static final int PID_LEDGER = 0x3b7c;
	private static final int PID_LEDGER_PROTON = 0x4b7c;
	private static final int HID_BUFFER_SIZE = 64;
	private static final int SW1_DATA_AVAILABLE = 0x61;
	private static final int LEDGER_DEFAULT_CHANNEL = 1;
	private static final int TIMEOUT = 20000;
}
