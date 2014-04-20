This library provides a set of modules to do a few things: packet-based Bluetooth communications, including framing using generic octet stuffing and checksum calculations; definition of DataSource and DataSinks which can be used to channel data (for example from Bluetooth-based DataSource to a DataSink. Currently, a single DataSink is implemented: it is a frequency modulator, using the incoming data as the envelope. This can be used to turn, for example, ECG into sound (or tactile vibration).

The source is the only reference guaranteed to be up-to-date, but the JavaDoc can help to understand, and the diagrams in the main folder give a decent conceptual overview even if they are sometimes somewhat out of date.

The `SenhanceLib` software is primarily under the `de.uos.nbp.senhance` namespace. It was originally written at the University of Osnabrueck for the heartFelt project.

I have taken this directly from the SVN, and so a lot of the links will be broken. I've left them in though to show which diagrams are relevant.

If this is useful to anyone, then pull requests for fixing it up (establishing the API properly, including test code, including examples of usage) would be very much appreciated indeed.

This library is released under LGPLv3.

Author: rmuil@uos.de

# SenhanceLib #

||[[Image(source:software/android/SenhanceLib/DataSourceSink.png, 800px, align=left)]]||


This includes common code that will be used by all or many of the software projects under the Senhance (''previously VIPSE'') project, including heartFelt. At the moment, the only other software package is the `CompassSimulator` which is used to control the ISEP belt over an RFCOMM bluetooth connection by simulating the GX1 compass.

`SenhanceLib` at the moment includes:
 * `de.uos.nbp.Utils` which are just a collection of miscellaneous useful functions such as hex-string output of byte arrays.
 * `de.uos.nbp.senhance.datasource.DataSink`
 * `de.uos.nbp.senhance.datasource.DataSource`
 * `de.uos.nbp.senhance.datasource.ControlSource`
 * `de.uos.nbp.senhance.bluetooth.DeviceListActivity` which is an Activity used to select a bluetooth device and supports filtering by device name.
 * `de.uos.nbp.senhance.bluetooth.BluetoothService`
 * `de.uos.nbp.senhance.bluetooth.BluetoothPacketConnection`
 * `de.uos.nbp.senhance.bluetooth.FramedPacketConnection`
 * `de.uos.nbp.senhance.EventLoggingActivity` which allows a user to add event logs to the current `DataLogger`.
 * `de.uos.nbp.senhance.DataLogger`
 * `de.uos.nbp.senhance.SDLogger`

At the moment, device connection is abstracted in the `DataSource` class. This may in the future be further factored such that all of `DataSource`, `ControlSource` and `DataSink` implement something like a ''`DeviceConnection`'' class. Currently, this is not necessary.

Device state consists of:

[[Image(source:software/android/SenhanceLib/DeviceConnectionStates.png, 700px, align=right)]]
 1. Disconnected
 1. Connecting
 1. Connected
 1. Ready
 1. Transmitting

These are the high-level states of the device connection. Within these, there may be subsidiary states in various implementations: for example, in the `Disconnected` state, the software may or may not have a connection thread spawned. However, these are implementation details which do not need to be enforced by the 'DataSource' interface.

Currently, the implementations of `DataSource` which exist and thus implement this device state are:

1. `CorscienceBT3/6` - providing connection to the real ECG device
1. `DummySourceThread` - basic abstract class that provides artificial (dummy/fake/constant) heartbeat as a data source. is itself an abstract class with the following subclasses:
 1. `DummyECG` - simulates a single channel of ECG data (only R-Wave and T-Wave)
 1. `DummyBPM` - simulates simple pulse recorded - just generates single beat events
1. `ExperimentalComputer` - is actually a `ControlSource` also, and doesn't actually provide data - just allows a PC to attach via Bluetooth and gain some control of the heartFelt app.


## Bluetooth Communication ##

SenhanceLib contains utilities which encapsulate the bluetooth communication and offer a packet based API. 

[[Image(source:software/android/SenhanceLib/Bluetooth_architecture.png, 800px)]]

The client can connect to the other device via BluetoothPacketConnection or FramedPacketConnection. BluetoothPacketConnection treats each received byte as a packet and delegates it to the client via the interface PacketConnectionHandler, which the client has to implement. FramedPacketConnection can be used for packets with variable length. They are bounded by a start- and end-byte. These bytes can also be part of the packet content when an escape byte is used. The next figure shows how the packet is processed.

[[Image(source:software/android/SenhanceLib/PacketConnectionStates.png, 800px)]]

# Tactile Output #

[[Image(source:software/android/heartFelt/HeartOut_FM.svg, 500px, align=right)]]


Implemented in the `HeartOut` class of the `heartFelt` application, tactile output is generated with frequency modulation using the ECG signal as the baseband (envelope). In the experiments, the frequency range of the carrier is between 1Hz and 100Hz, which manifests as a tactile signal that is barely audible. The carrier can though be made much higher, with a peak at 12000Hz and thereby in the audible range. This manifests then in an audible representation of the raw ECG, similar to what one hears in a hospital.

The artificial ECG for the fake/dummy/constant condition is generated by `DummyECG`, using two half sine-waves for an R-Wave and a T-Wave. The amplitudes and R-T interval are fixed.

The image here shows the artificial ECG together with the corresponding tactile output.

## Transient Responses ##

The rate of artificial ECG data is controlled just by changing the R-R intervals. The `DummySourceThread` class, which is the superclass of `DummyECG` also allows transient changes in the rate.

All classes based on `DummySourceThread` have noise and transient response implemented. A transient response can be started and ended and in between the heart-rate is simulated according to the model depicted here:

[[Image(source:software/android/SenhanceLib/CardiacTransientResponseModel.svg, 800px)]]

Here is an example of the heartrate output of the DummyECG implementing this model, showing 3 subsequent transient responses (W stage is not currently implemented):

||[[Image(source:software/android/SenhanceLib/CardiacTransientResponseExample-v0.1.svg, 800px)]]||
||'''u_length''': 1500msecs ||
||'''transition_rate''': 10 bpm/sec ||

