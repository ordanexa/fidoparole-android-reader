package com.ordanexa.fidoreader;
import android.hardware.usb.*;
import java.io.*;
import java.nio.*;
import java.util.concurrent.TimeoutException;

final class UsbIo implements Hid.Io {
    final UsbDeviceConnection connection;final UsbInterface intf;final UsbEndpoint in,out;volatile boolean closed;
    UsbIo(UsbManager manager,UsbDevice device)throws IOException{
        UsbInterface found=null;UsbEndpoint input=null,output=null;
        for(int i=0;i<device.getInterfaceCount();i++){
            UsbInterface f=device.getInterface(i);if(f.getInterfaceClass()!=3||f.getInterfaceSubclass()!=0||f.getInterfaceProtocol()!=0)continue;
            UsbEndpoint a=null,b=null;
            for(int j=0;j<f.getEndpointCount();j++){UsbEndpoint e=f.getEndpoint(j);if(e.getType()!=UsbConstants.USB_ENDPOINT_XFER_INT||e.getMaxPacketSize()!=64)continue;if(e.getDirection()==UsbConstants.USB_DIR_IN)a=e;else b=e;}
            if(a!=null&&b!=null){if(found!=null)throw new IOException("Mehrere HID-Interfaces; Auswahl nicht eindeutig");found=f;input=a;output=b;}
        }
        if(found==null)throw new IOException("Kein 64-Byte FIDO-HID Interface gefunden");
        intf=found;in=input;out=output;connection=manager.openDevice(device);
        if(connection==null)throw new IOException("USB-Gerät konnte nicht geöffnet werden");
        if(!connection.claimInterface(intf,true)){connection.close();throw new IOException("USB-Interface belegt");}
    }
    void transfer(UsbEndpoint endpoint,ByteBuffer buffer,long deadline)throws Exception{
        if(closed)throw new IOException("USB-Verbindung geschlossen");
        UsbRequest request=new UsbRequest();
        try{
            if(!request.initialize(connection,endpoint)||!request.queue(buffer))throw new IOException("USB-Anfrage fehlgeschlagen");
            try{while(!closed&&System.nanoTime()<deadline){
                try{UsbRequest done=connection.requestWait(500);if(done==request){if(buffer.position()!=64)throw new IOException("Unvollständiges USB-Paket");return;}if(done==null)throw new IOException("USB-Verbindung unterbrochen");}
                catch(TimeoutException ignored){}
            }}finally{request.cancel();}
        }finally{request.close();}
        throw new IOException(closed?"USB-Verbindung geschlossen":"USB Timeout");
    }
    public void write(byte[] packet,long deadline)throws Exception{ByteBuffer b=ByteBuffer.allocateDirect(64);b.put(packet).flip();transfer(out,b,deadline);}
    public byte[] read(long deadline)throws Exception{ByteBuffer b=ByteBuffer.allocateDirect(64);transfer(in,b,deadline);byte[] data=new byte[64];b.flip();b.get(data);return data;}
    public void close(){if(!closed){closed=true;connection.close();}}
}
