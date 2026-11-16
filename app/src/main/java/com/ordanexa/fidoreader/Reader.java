package com.ordanexa.fidoreader;
import java.io.*;
import java.util.*;

/** Only discovery, PIN authentication, assertions and largeBlob GET are allowed. */
final class Reader {
    static final String RP="fido2-vault.token2";
    interface Progress {void update(String text);}
    final Hid hid;final Progress progress;
    Reader(Hid hid,Progress progress){this.hid=hid;this.progress=progress;}
    static void requireReadOnly(int command,Map<Object,Object> args)throws Exception{
        if(command==4||command==2||command==8)return;
        if(command==6){long sub=Cbor.number(Cbor.get(args,2));if(sub==1||sub==2||sub==5||sub==9){
            if(sub==9&&(Cbor.number(Cbor.get(args,9))!=2||!RP.equals(Cbor.get(args,10))))throw new IOException("PIN permissions not read-only");return;}}
        if(command==12&&args.size()==2&&args.containsKey(1L)&&args.containsKey(3L)&&Cbor.number(args.get(1L))>0&&Cbor.number(args.get(3L))>=0)return;
        throw new IOException("Write operation blocked");
    }
    Object command(int cmd,Map<Object,Object> args)throws Exception{
        requireReadOnly(cmd,args);byte[] payload=args.isEmpty()?new byte[]{(byte)cmd}:Crypto.cat(new byte[]{(byte)cmd},Cbor.encode(args));
        byte[] response=hid.exchange(0x10,payload);
        if(response.length==0)throw new IOException("Empty CTAP response");int status=response[0]&255;
        if(status!=0)throw new IOException(error(status));
        return response.length==1?Cbor.map():Cbor.decode(Arrays.copyOfRange(response,1,response.length));
    }
    static String error(int code){String reason;
        switch(code){case 0x31:reason="Falsche PIN. Keine automatische Wiederholung.";break;case 0x32:reason="PIN blockiert. Nicht zurücksetzen: Reset löscht Credentials.";break;case 0x34:reason="PIN vorübergehend blockiert. Token abziehen und erneut anschließen.";break;case 0x2e:reason="Kein FidoParole Credential auf diesem Token.";break;case 0x2f:reason="Berührung abgebrochen oder Timeout.";break;case 0x35:reason="PIN noch nicht gesetzt. Bitte Windows-App verwenden.";break;case 0x36:reason="PIN/UV Token erforderlich.";break;case 0x33:reason="PIN-Authentifizierung ungültig; Protokolltest erforderlich.";break;default:reason="Token hat die Anfrage abgewiesen.";}
        return String.format("CTAP 0x%02x: %s",code,reason);
    }
    static final class Vault implements AutoCloseable {
        final String name;byte[] json;
        Vault(String name,byte[] json){this.name=name;this.json=json;}
        public void close(){Crypto.wipe(json);json=null;}
    }
    List<Vault> unlock(byte[] pin)throws Exception{
        List<Vault> result=new ArrayList<>();byte[] token=null,hash=null;
        try{
            hid.init();Object info=command(4,Cbor.map());Object options=Cbor.get(info,4),extensions=Cbor.get(info,2),protocols=Cbor.get(info,6);
            if(!Boolean.TRUE.equals(Cbor.get(options,"clientPin")))throw new IOException("PIN nicht eingerichtet");
            if(!(extensions instanceof List)||!((List<?>)extensions).contains("hmac-secret")||!((List<?>)extensions).contains("largeBlobKey")||!Boolean.TRUE.equals(Cbor.get(options,"largeBlobs")))throw new IOException("Token unterstützt hmac-secret/largeBlob nicht");
            if(!(protocols instanceof List))throw new IOException("Kein PIN-Protokoll");
            int version=((List<?>)protocols).contains(2L)?2:((List<?>)protocols).contains(1L)?1:0;
            if(version==0)throw new IOException("Nicht unterstütztes PIN-Protokoll");
            Object retries=command(6,Cbor.map(1,version,2,1));long left=Cbor.number(Cbor.get(retries,3));
            if(left<=2)throw new IOException("Nur noch "+left+" PIN-Versuche. Bitte PIN zuerst mit Windows-App prüfen.");
            progress.update("PIN prüfen · "+left+" Versuche verbleibend");
            Object peer=Cbor.get(command(6,Cbor.map(1,version,2,2)),1);
            try(Crypto.Agreement agreement=new Crypto.Agreement(version,peer)){
                hash=Crypto.sha(pin);byte[] pinHash=Arrays.copyOf(hash,16),encrypted=null;
                try{encrypted=agreement.encrypt(pinHash);}finally{Crypto.wipe(pinHash);Crypto.wipe(pin);Crypto.wipe(hash);hash=null;}
                boolean scoped=Boolean.TRUE.equals(Cbor.get(options,"pinUvAuthToken"));
                Map<Object,Object> args=Cbor.map(1,version,2,scoped?9:5,3,agreement.publicKey,6,encrypted);
                if(scoped){args.put(9L,2L);args.put(10L,RP);}
                token=agreement.decrypt(Cbor.bytes(Cbor.get(command(6,args),2)));
                if((version==2&&token.length!=32)||(version==1&&token.length!=16&&token.length!=32))throw new IOException("Invalid PIN token");
            }
            // A fresh agreement for hmac-secret, as in the CTAP reference client.
            peer=Cbor.get(command(6,Cbor.map(1,version,2,2)),1);
            List<Object> assertions=new ArrayList<>();
            try(Crypto.Agreement agreement=new Crypto.Agreement(version,peer)){
                byte[] salt=Crypto.sha(Crypto.cat(Crypto.utf8("WebAuthn PRF"),new byte[]{0},Crypto.utf8("Token2 fido2-vault v1 / hmac-secret salt")));
                byte[] enc=agreement.encrypt(salt),clientHash=Crypto.random(32),auth=Crypto.hmac(token,clientHash);
                if(version==1)auth=Arrays.copyOf(auth,16);
                progress.update("Token berühren, wenn er blinkt …");
                Object first=command(2,Cbor.map(1,RP,2,clientHash,4,Cbor.map("hmac-secret",Cbor.map(1,agreement.publicKey,2,enc,3,agreement.authenticate(enc),4,version),"largeBlobKey",true),5,Cbor.map("up",true),6,auth,7,version));
                Crypto.wipe(token);token=null;
                Object countValue=Cbor.get(first,5);long count=countValue==null?1:Cbor.number(countValue);
                if(count<1||count>64)throw new IOException("Unexpected credential count");assertions.add(first);
                // getNextAssertion must immediately follow getAssertion.
                for(int i=1;i<count;i++)assertions.add(command(8,Cbor.map()));
                progress.update("Vault lesen und entschlüsseln …");
                Object maxMsg=Cbor.get(info,5);int fragment=maxMsg==null?960:(int)Math.min(960,Cbor.number(maxMsg)-64);
                if(fragment<64)throw new IOException("Invalid maxMsgSize");
                Object blobs=readBlobs(fragment);
                for(Object assertion:assertions){
                    byte[] ad=Cbor.bytes(Cbor.get(assertion,2));
                    if(ad.length<37||!Arrays.equals(Arrays.copyOf(ad,32),Crypto.sha(Crypto.utf8(RP)))||(ad[32]&0x85)!=0x85||(ad[32]&0x40)!=0)throw new IOException("Invalid assertion RP/UP/UV/extensions");
                    Object ext=Cbor.decodeAt(ad,37);byte[] secret=null,blob=null;
                    try {secret=agreement.decrypt(Cbor.bytes(Cbor.get(ext,"hmac-secret")));if(secret.length!=32)throw new IOException("Invalid hmac-secret output");
                        byte[] blobKey=Cbor.bytes(Cbor.get(assertion,7));if(blobKey.length!=32)throw new IOException("Invalid largeBlobKey");
                        try{blob=Crypto.unwrapBlob(blobKey,blobs);}finally{Crypto.wipe(blobKey);}
                        Object user=Cbor.get(assertion,4);byte[] handle=user==null?new byte[0]:Cbor.bytes(Cbor.get(user,"id"));
                        String name=handle.length>9&&handle[0]==1?new String(handle,9,handle.length-9,java.nio.charset.StandardCharsets.UTF_8):"Vault";
                        result.add(new Vault(name,Crypto.openVault(blob,secret)));
                    } finally{Crypto.wipe(secret);Crypto.wipe(blob);Crypto.wipe(ad);}
                }
            }
            return result;
        }catch(Exception e){for(Vault v:result)v.close();throw e;}finally{Crypto.wipe(pin);Crypto.wipe(hash);Crypto.wipe(token);}
    }
    Object readBlobs(int fragment)throws Exception{
        ByteArrayOutputStream data=new ByteArrayOutputStream();
        while(true){byte[] part=Cbor.bytes(Cbor.get(command(12,Cbor.map(1,fragment,3,data.size())),1));
            if(part.length>fragment||data.size()+part.length>1048576)throw new IOException("Invalid largeBlob response");data.write(part);if(part.length<fragment)break;}
        byte[] all=data.toByteArray();if(all.length<17)throw new IOException("Empty largeBlob storage");byte[] array=Arrays.copyOf(all,all.length-16);
        if(!java.security.MessageDigest.isEqual(Arrays.copyOf(Crypto.sha(array),16),Arrays.copyOfRange(all,all.length-16,all.length)))throw new IOException("largeBlob checksum mismatch");return Cbor.decode(array);
    }
}
