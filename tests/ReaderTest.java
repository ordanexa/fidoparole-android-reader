package com.ordanexa.fidoreader;
import java.io.*;
import java.nio.*;
import java.util.*;

public final class ReaderTest {
    static int checks;
    static void check(boolean ok,String msg){checks++;if(!ok)throw new AssertionError(msg);}
    interface Attempt{void run()throws Exception;}
    static void rejects(Attempt a,String label)throws Exception{try{a.run();}catch(Exception expected){checks++;return;}throw new AssertionError(label);}
    static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format("%02x",v&255));return s.toString();}
    static byte[] unhex(String s){if(s==null)throw new AssertionError("Simulator stopped");byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(2*i,2*i+2),16);return b;}
    static final class Sim implements Hid.Io{
        final Process process;final BufferedReader read;final PrintWriter write;final Queue<byte[]> packets=new ArrayDeque<>();
        ByteArrayOutputStream request;int length,command,channel,sequence;
        Sim(String mode)throws Exception{process=new ProcessBuilder("python3","tests/authenticator.py",mode).redirectError(ProcessBuilder.Redirect.INHERIT).start();read=new BufferedReader(new InputStreamReader(process.getInputStream()));write=new PrintWriter(process.getOutputStream(),true);}
        public void write(byte[] packet,long deadline)throws Exception{
            int tag=packet[4]&255;int cid=ByteBuffer.wrap(packet).getInt();
            if((tag&128)!=0){channel=cid;command=tag&127;length=((packet[5]&255)<<8)|(packet[6]&255);request=new ByteArrayOutputStream();sequence=0;request.write(packet,7,Math.min(57,length));}
            else{check(cid==channel&&tag==sequence++,"request framing sequence");request.write(packet,5,Math.min(59,length-request.size()));}
            if(request.size()==length){byte[] response;
                if(command==6)response=Crypto.cat(request.toByteArray(),ByteBuffer.allocate(4).putInt(0x12345678).array(),new byte[]{2,1,0,0,4});
                else{check(command==16,"only INIT/CBOR");write.println(hex(request.toByteArray()));response=unhex(read.readLine());}
                // Exercise keepalive packets and HID fragmentation for each response.
                byte[] keep=new byte[64];ByteBuffer.wrap(keep).putInt(channel);keep[4]=(byte)0xbb;keep[6]=1;keep[7]=2;packets.add(keep);
                int p=0,seq=0;byte[] out=new byte[64];ByteBuffer.wrap(out).putInt(channel);out[4]=(byte)(command|128);out[5]=(byte)(response.length>>>8);out[6]=(byte)response.length;int n=Math.min(57,response.length);System.arraycopy(response,0,out,7,n);p=n;packets.add(out);
                while(p<response.length){out=new byte[64];ByteBuffer.wrap(out).putInt(channel);out[4]=(byte)seq++;n=Math.min(59,response.length-p);System.arraycopy(response,p,out,5,n);p+=n;packets.add(out);}
            }
        }
        public byte[] read(long deadline){return packets.remove();}
        public void close(){write.close();try{if(!process.waitFor(2,java.util.concurrent.TimeUnit.SECONDS))process.destroyForcibly();}catch(InterruptedException e){process.destroyForcibly();}}
    }
    public static void main(String[] args)throws Exception{
        check(hex(Cbor.encode(Cbor.map("z",true,1,2,-1,new byte[]{3}))).equals("a30102204103617af5"),"canonical CBOR");
        rejects(()->Cbor.decode(new byte[]{(byte)0xa2,1,2,1,3}),"duplicate map");
        rejects(()->Cbor.decode(new byte[]{(byte)0x9f,(byte)0xff}),"indefinite");
        rejects(()->Cbor.decode(new byte[]{0,0}),"trailing data");
        rejects(()->Cbor.decode(new byte[]{0x5a,0x7f,(byte)0xff,(byte)0xff,(byte)0xff}),"oversized CBOR");
        for(int cmd:new int[]{1,7,10,13})rejects(()->Reader.requireReadOnly(cmd,Cbor.map()),"write guard");
        rejects(()->Reader.requireReadOnly(12,Cbor.map(2,new byte[]{1},3,0)),"blob write");
        rejects(()->Reader.requireReadOnly(6,Cbor.map(1,2,2,4)),"PIN change");
        rejects(()->Reader.requireReadOnly(6,Cbor.map(1,2,2,9,9,16,10,Reader.RP)),"write permission");
        for(String mode:new String[]{"v1","v2","format1","wrong_pin","low_retries","checksum","no_uv","vault_tamper","wrong_secret","touch_timeout"}){
            try(Hid hid=new Hid(new Sim(mode))){
                List<Boolean> touchEvents=new ArrayList<>();
                Reader reader=new Reader(hid,new Reader.Progress(){
                    public void update(String message){}
                    public void awaitingTouch(boolean waiting){touchEvents.add(waiting);}
                });boolean success=mode.equals("v1")||mode.equals("v2")||mode.equals("format1");byte[] pin=Crypto.utf8("123456");
                if(success){List<Reader.Vault> vaults=reader.unlock(pin);check(vaults.size()==2,"multiple assertions");for(Reader.Vault v:vaults){check(new String(v.json,java.nio.charset.StandardCharsets.UTF_8).contains("dummy-copy-password"),"independent decryption "+mode);v.close();}}
                else rejects(()->reader.unlock(pin),"must reject "+mode);
                check(Arrays.equals(pin,new byte[pin.length]),"PIN wiped "+mode);
                check(touchEvents.isEmpty()||touchEvents.equals(Arrays.asList(true,false)),"balanced touch prompt "+mode);
                if(mode.equals("wrong_pin")||mode.equals("low_retries"))check(touchEvents.isEmpty(),"no touch before PIN verification");
                if(success||mode.equals("touch_timeout"))check(touchEvents.equals(Arrays.asList(true,false)),"touch prompt completes "+mode);
            }
            System.out.println("PASS "+mode);
        }
        System.out.println("PASS "+checks+" checks");
    }
}
