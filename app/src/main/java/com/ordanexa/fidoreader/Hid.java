package com.ordanexa.fidoreader;
import java.io.*;
import java.nio.*;
import java.util.*;

/** CTAPHID with bounded packets and a per-operation deadline. */
final class Hid implements AutoCloseable {
    interface Io extends AutoCloseable {void write(byte[] packet,long deadline)throws Exception;byte[] read(long deadline)throws Exception;void close();}
    final Io io;int channel=-1;
    Hid(Io io){this.io=io;}
    void init()throws Exception{
        byte[] nonce=Crypto.random(8),reply=exchange(0x06,nonce);
        if(reply.length!=17||!Arrays.equals(nonce,Arrays.copyOf(reply,8)))throw new IOException("Invalid CTAPHID INIT");
        channel=ByteBuffer.wrap(reply,8,4).getInt();
        if(channel==0||channel==-1||(reply[16]&4)==0)throw new IOException("Token does not support CTAP2 HID");
    }
    byte[] exchange(int command,byte[] data)throws Exception{
        if(data.length>7609)throw new IOException("CTAPHID request too large");
        long deadline=System.nanoTime()+60000000000L;
        int pos=0,seq=0;byte[] packet=new byte[64];ByteBuffer.wrap(packet).putInt(channel);packet[4]=(byte)(command|0x80);packet[5]=(byte)(data.length>>>8);packet[6]=(byte)data.length;
        int n=Math.min(57,data.length);System.arraycopy(data,0,packet,7,n);pos=n;io.write(packet,deadline);
        while(pos<data.length){packet=new byte[64];ByteBuffer.wrap(packet).putInt(channel);packet[4]=(byte)seq++;n=Math.min(59,data.length-pos);System.arraycopy(data,pos,packet,5,n);pos+=n;io.write(packet,deadline);}
        ByteArrayOutputStream reply=null;int total=0;seq=0;
        while(System.nanoTime()<deadline){packet=io.read(deadline);if(packet.length!=64)throw new IOException("Unexpected HID packet size");if(ByteBuffer.wrap(packet).getInt()!=channel)continue;
            int tag=packet[4]&255;
            if(tag==0xbf)throw new IOException(String.format("HID error 0x%02x",packet[7]&255));
            if(tag==0xbb)continue;
            if(reply==null){if(tag!=(command|0x80))throw new IOException("Unexpected HID command");total=((packet[5]&255)<<8)|(packet[6]&255);if(total>7609)throw new IOException("HID response too large");reply=new ByteArrayOutputStream();reply.write(packet,7,Math.min(total,57));}
            else{if(tag!=seq++)throw new IOException("HID sequence mismatch");reply.write(packet,5,Math.min(59,total-reply.size()));}
            if(reply.size()==total)return reply.toByteArray();
        }
        throw new IOException("Token timeout; reconnect and try again");
    }
    public void close(){io.close();}
}
