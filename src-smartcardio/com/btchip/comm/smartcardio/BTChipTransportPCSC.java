package com.btchip.comm.smartcardio;

import java.util.List;
import java.util.Vector;

import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardTerminals;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.TerminalFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.btchip.BTChipException;
import com.btchip.comm.BTChipTransport;
import com.btchip.utils.Dump;

public class BTChipTransportPCSC implements BTChipTransport {
	
	private CardChannel channel;
	private boolean debug;
	
	private static Logger log = LoggerFactory.getLogger(BTChipTransportPCSC.class);
	
	public BTChipTransportPCSC(CardChannel channel) {
		this.channel = channel;
	}

	@Override
	public byte[] exchange(byte[] command) throws BTChipException {
		if (debug) {
			log.debug("=> {}", Dump.dump(command));
		}
		try {
			byte[] response = channel.transmit(new CommandAPDU(command)).getBytes();
			if (debug) {
				log.debug("<= {}", Dump.dump(response));
			}			
			return response;
		}
		catch(CardException e) {
			throw new BTChipException("Communication error", e);
		}
	}

	@Override
	public void close() throws BTChipException {
	}

	@Override
	public void setDebug(boolean debugFlag) {
		this.debug = debugFlag;
	}
	
	public static String[] listDevices() throws BTChipException {
		try {
			TerminalFactory factory = TerminalFactory.getDefault();
			List<CardTerminal> terminals = factory.terminals().list(CardTerminals.State.CARD_PRESENT);
			Vector<String> terminalsNames = new Vector<String>();
			for (CardTerminal terminal : terminals) {
				terminalsNames.add(terminal.getName());
			}
			return terminalsNames.toArray(new String[0]);
		}
		catch(Exception e) {
			throw new BTChipException("Couldn't list terminals", e);
		}        
	}
	
	public static BTChipTransportPCSC openDevice(String terminalName) throws BTChipException {
		try {
			TerminalFactory factory = TerminalFactory.getDefault();
			List<CardTerminal> terminals = factory.terminals().list(CardTerminals.State.CARD_PRESENT);
			CardTerminal targetTerminal = null;
			for (CardTerminal terminal : terminals) {
				if ((terminalName == null) || (terminalName.length() == 0)) {
					targetTerminal = terminal;
					break;
				}
				if (terminal.getName().equalsIgnoreCase(terminalName)) {
					targetTerminal = terminal;
					break;
				}
			}
			if (targetTerminal == null) {
				throw new BTChipException("Terminal not found");
			}
			Card card = targetTerminal.connect("*");
			return new BTChipTransportPCSC(card.getBasicChannel());
		}
		catch(BTChipException e) {
			throw e;
		}
		catch(Exception e) {
			throw new BTChipException("Couldn't open terminal", e);
		}        		
	}
	
	public static BTChipTransportPCSC openDevice() throws BTChipException {
		return openDevice(null);
	}
	
	public static void main(String args[]) throws Exception {
		BTChipTransportPCSC device = openDevice();
		device.setDebug(true);
		byte[] result = device.exchange(Dump.hexToBin("e0c4000000"));
		System.out.println(Dump.dump(result));
		device.close();		
	}
}
