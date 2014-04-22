# BluetoothFramework #
This library provides a stable, packet-based Bluetooth communication including framing using generic octet stuffing. It is aimed at being very stable, but at the same time easily understandable and usable.

The main body of code was released by [R. Muil](https://github.com/robertmuil/senhancelib) as part of the feelSpace project at the University of Osnabrück and has been slimmed down to incorporate only the Bluetooth-relevant functions. It is re-released under LGPLv3.

## Bluetooth Communication ##
A sample application that demonstrates the use of this library is available under https://github.com/sharst/BluetoothFrameworkDemo

The client can connect to the other device via BluetoothPacketConnection or FramedPacketConnection. BluetoothPacketConnection treats each received byte as a packet and delegates it to the client via the interface PacketConnectionHandler, which the client has to implement. FramedPacketConnection can be used for packets with variable length. They are bounded by a start- and end-byte. These bytes can also be part of the packet content when an escape byte is used. 

For the creation of a connection, only the address and port of the remote device are needed. A connection can be established as simple as:

`mBluetoothPacketConnection = new FramedPacketConnection(address, connHandler)
mBluetoothPacketConnection.connect(PORT);`

BluetoothFramework also contains an Activity template for polling the addresses of close-by devices. You can try it out by starting the Activity with an intent:

`Intent intent = new Intent(this, de.uos.nbp.senhance.bluetooth.DeviceListActivity.class);
startActivityForResult(intent, REQUEST_DEVICE_ADDRESS);`

and reading out the address in the onActivityResult method:
`mAddress = data.getStringExtra(DeviceListActivity.INTENT_DEVICEADDRESS);`

