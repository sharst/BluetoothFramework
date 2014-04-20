package de.uos.nbp.senhance.bluetooth;

import de.uos.nbp.senhance.bluetooth.PacketConnection.Packet;

public interface PacketConnectionHandler {
	
	
	void connectAttemptFailed(String message);
	void connectFailed(String message);
	void connected();
	void connectionLost(String message);
	void connectionClosed();
	
	void packetReceived(Packet receivedPacket);
}
