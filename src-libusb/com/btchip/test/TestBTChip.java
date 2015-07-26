package com.btchip.test;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Scanner;

import com.btchip.BTChipDongle;
import com.btchip.BTChipDongle.BTChipInput;
import com.btchip.BitcoinTransaction;
import com.btchip.comm.winusb.BTChipTransportWinUSB;
import com.btchip.utils.Dump;

public class TestBTChip {
	
	public static void testDongleCall() throws Exception {
		BTChipTransportWinUSB device = BTChipTransportWinUSB.openDevice();
		device.setDebug(true);
		BTChipDongle dongle = new BTChipDongle(device);
		System.out.println(dongle.getFirmwareVersion());
		dongle.verifyPin("1234".getBytes());
		System.out.println(dongle.getWalletPublicKey("0'/1'/1"));		
	}
	
	public static void testTransaction() throws Exception {
		byte[] transaction = Dump.hexToBin("0100000001cfebd86aa5f760001e679c4c5a483f5a6a21f6cbbdafb591b4dbbd47785d921c000000006b483045022100fc4d6406082dbd6fcb98427dfc02a201a5560bdc4dbfe33e7b0c170541d29abf022062d3a93f84c9d0bb1aecdcd799ef7a81ab216499e7336c10bafb1044641c965f012102ea33616a624df52a95747c8604b704aacbcf1f09e21933ed57e0cc4ff23f22deffffffff06c2b7fa07000000001976a9143b2cd3958f457ff5832b0df3002b394ac110b86788acb0076402000000001976a914e80384eb58aaba35091cb8a856cc5d20e1b420d588ac986b4e03000000001976a914924ee95ea484581bd94a73a9430995b63518776d88ac50fd1300000000001976a91494d0d17e995b871825df01fa0422f4450ac6cc7d88acd044cf02000000001976a914e29d2ae6dc7fa04884b0dd8c3b79f212b77a262988ac80841e00000000001976a914d5c48ba41679670bd500c9681f64bb1f5d1d18bb88ac00000000");
		BitcoinTransaction transactionParsed = new BitcoinTransaction(new ByteArrayInputStream(transaction));
		System.out.println(transactionParsed);		
		byte[] transactionSerialized = transactionParsed.serialize(false);
		System.out.println(Dump.dump(transactionSerialized));
		if (!Arrays.equals(transaction, transactionSerialized)) {
			throw new RuntimeException("Serialized transaction fail");
		}
	}
	
	public static void testTransactionFull() throws Exception {
		BTChipTransportWinUSB device = BTChipTransportWinUSB.openDevice();
		device.setDebug(true);
		BTChipDongle dongle = new BTChipDongle(device);
		dongle.verifyPin("1234".getBytes());
		byte[] transaction = Dump.hexToBin("01000000014ea60aeac5252c14291d428915bd7ccd1bfc4af009f4d4dc57ae597ed0420b71010000008a47304402201f36a12c240dbf9e566bc04321050b1984cd6eaf6caee8f02bb0bfec08e3354b022012ee2aeadcbbfd1e92959f57c15c1c6debb757b798451b104665aa3010569b49014104090b15bde569386734abf2a2b99f9ca6a50656627e77de663ca7325702769986cf26cc9dd7fdea0af432c8e2becc867c932e1b9dd742f2a108997c2252e2bdebffffffff0281b72e00000000001976a91472a5d75c8d2d0565b656a5232703b167d50d5a2b88aca0860100000000001976a9144533f5fb9b4817f713c48f0bfe96b9f50c476c9b88ac00000000");
		String outputAddress = "1BTChipvU14XH6JdRiK9CaenpJ2kJR9RnC";
		String amount = "0.0009";
		String fees = "0.0001";
		String changePath = "0'/1/0";
		String keyPath = "0'/0/0";
		BitcoinTransaction transactionParsed = new BitcoinTransaction(new ByteArrayInputStream(transaction));
		byte[] outputScript = transactionParsed.getOutputs().get(1).getScript();
		for (;;) {
			BTChipInput input = dongle.getTrustedInput(transactionParsed, 1);
			dongle.startUntrustedTransaction(true, 0, new BTChipInput[] { input }, outputScript);
		}
		/*
		dongle.finalizeInput(outputAddress, amount, fees, changePath);
		device.close();
		System.out.println("Enter second factor");
		String secondFactor = new Scanner(System.in).nextLine();
		device = BTChipTransportWinUSB.openDevice();
		device.setDebug(true);
		dongle = new BTChipDongle(device);		
		dongle.startUntrustedTransction(false, 0, new BTChipInput[] { input }, outputScript);
		dongle.finalizeInput(outputAddress, amount, fees, changePath);
		dongle.untrustedHashSign(keyPath, secondFactor.substring(secondFactor.length() - 4));
		*/
	}
	
	public static void main(String args[]) throws Exception {
		testTransactionFull();
	}

}
