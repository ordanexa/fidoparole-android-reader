package com.ordanexa.fidoreader;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.math.BigInteger;
import java.util.*;
import java.util.zip.*;
import javax.crypto.*;
import javax.crypto.spec.*;

final class Crypto {
    static final SecureRandom RNG=new SecureRandom();
    static byte[] utf8(String s){return s.getBytes(StandardCharsets.UTF_8);}
    static byte[] random(int n){byte[] b=new byte[n];RNG.nextBytes(b);return b;}
    static void wipe(byte[] b){if(b!=null)Arrays.fill(b,(byte)0);}
    static byte[] cat(byte[]... arrays){int n=0;for(byte[] a:arrays)n+=a.length;byte[] b=new byte[n];int p=0;for(byte[] a:arrays){System.arraycopy(a,0,b,p,a.length);p+=a.length;}return b;}
    static byte[] sha(byte[] b)throws GeneralSecurityException{return MessageDigest.getInstance("SHA-256").digest(b);}
    static byte[] hmac(byte[] key,byte[] b)throws GeneralSecurityException{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));return m.doFinal(b);}
    static byte[] hkdf(byte[] ikm,byte[] salt,String info)throws GeneralSecurityException{byte[] prk=hmac(salt,ikm);try{return hmac(prk,cat(utf8(info),new byte[]{1}));}finally{wipe(prk);}}
    static byte[] gcm(byte[] key,byte[] iv,byte[] aad,byte[] ct)throws GeneralSecurityException{
        if(key.length!=32||iv.length!=12)throw new GeneralSecurityException("Invalid AES-GCM parameters");
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));c.updateAAD(aad);return c.doFinal(ct);
    }
    static byte[] inflate(byte[] b,int max)throws IOException{
        Inflater in=new Inflater(true);in.setInput(b);byte[] buffer=new byte[4096];ByteArrayOutputStream out=new ByteArrayOutputStream();
        try {while(!in.finished()){int n=in.inflate(buffer);if(n==0&&!in.finished())throw new IOException("Invalid DEFLATE stream");if(out.size()+n>max)throw new IOException("Decompressed data exceeds limit");out.write(buffer,0,n);}if(in.getRemaining()!=0)throw new IOException("DEFLATE trailing data");return out.toByteArray();}
        catch(DataFormatException e){throw new IOException("Invalid DEFLATE stream");}finally{in.end();wipe(buffer);}
    }
    static byte[] openVault(byte[] blob,byte[] secret)throws Exception{
        if(blob.length<50||!Arrays.equals(Arrays.copyOf(blob,5),utf8("T2VLT"))||blob[5]!=1)throw new IOException("Unsupported FidoParole vault header");
        byte[] key=hkdf(secret,Arrays.copyOfRange(blob,6,22),"Token2 fido2-vault v1 / AES-256-GCM"),compressed=null;
        try{compressed=gcm(key,Arrays.copyOfRange(blob,22,34),Arrays.copyOf(blob,34),Arrays.copyOfRange(blob,34,blob.length));return inflate(compressed,1048576);}finally{wipe(key);wipe(compressed);}
    }
    static byte[] unwrapBlob(byte[] key,Object array)throws Exception{
        if(!(array instanceof List))throw new IOException("Invalid largeBlob array");
        for(Object entry:(List<?>)array){
            byte[] ct=Cbor.bytes(Cbor.get(entry,1)),nonce=Cbor.bytes(Cbor.get(entry,2));long size=Cbor.number(Cbor.get(entry,3));
            if(size<0||size>1048576)throw new IOException("Invalid largeBlob size");
            byte[] aad=cat(utf8("blob"),ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(size).array()), compressed=null;
            try {compressed=gcm(key,nonce,aad,ct);byte[] blob=inflate(compressed,1048576);if(blob.length!=size){wipe(blob);throw new IOException("largeBlob length mismatch");}return blob;}
            catch(AEADBadTagException ignored){} finally{wipe(compressed);}
        }
        throw new IOException("No saved blob for this vault credential");
    }
    static final class Agreement implements AutoCloseable {
        final int version;final Map<Object,Object> publicKey;byte[] shared;
        Agreement(int version,Object peer)throws Exception{
            this.version=version;
            if(Cbor.number(Cbor.get(peer,1))!=2||Cbor.number(Cbor.get(peer,-1))!=1)throw new IOException("Unsupported key agreement curve");
            KeyPairGenerator gen=KeyPairGenerator.getInstance("EC");gen.initialize(new ECGenParameterSpec("secp256r1"));KeyPair pair=gen.generateKeyPair();
            ECPublicKey pub=(ECPublicKey)pair.getPublic();byte[] x=Cbor.bytes(Cbor.get(peer,-2)),y=Cbor.bytes(Cbor.get(peer,-3));
            if(x.length!=32||y.length!=32)throw new IOException("Invalid P-256 point");
            PublicKey other=KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(new BigInteger(1,x),new BigInteger(1,y)),pub.getParams()));
            KeyAgreement ka=KeyAgreement.getInstance("ECDH");ka.init(pair.getPrivate());ka.doPhase(other,true);byte[] z=ka.generateSecret();
            try{shared=version==1?sha(z):cat(hkdf(z,new byte[32],"CTAP2 HMAC key"),hkdf(z,new byte[32],"CTAP2 AES key"));}finally{wipe(z);}
            publicKey=Cbor.map(1,2,3,-25,-1,1,-2,fixed(pub.getW().getAffineX()),-3,fixed(pub.getW().getAffineY()));
        }
        static byte[] fixed(BigInteger n){byte[] b=n.toByteArray(),out=new byte[32];System.arraycopy(b,Math.max(0,b.length-32),out,Math.max(0,32-b.length),Math.min(32,b.length));return out;}
        byte[] encrypt(byte[] plain)throws Exception{return crypt(plain,true);}
        byte[] decrypt(byte[] ct)throws Exception{return crypt(ct,false);}
        byte[] crypt(byte[] data,boolean encrypt)throws Exception{
            byte[] key=version==1?shared.clone():Arrays.copyOfRange(shared,32,64);
            byte[] iv=version==1?new byte[16]:encrypt?random(16):Arrays.copyOf(data,16);
            byte[] input=version==2&&!encrypt?Arrays.copyOfRange(data,16,data.length):data;
            try{Cipher c=Cipher.getInstance("AES/CBC/NoPadding");c.init(encrypt?Cipher.ENCRYPT_MODE:Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new IvParameterSpec(iv));byte[] out=c.doFinal(input);return version==2&&encrypt?cat(iv,out):out;}finally{wipe(key);}
        }
        byte[] authenticate(byte[] message)throws Exception{byte[] key=version==1?shared.clone():Arrays.copyOf(shared,32);try{byte[] mac=hmac(key,message);return version==1?Arrays.copyOf(mac,16):mac;}finally{wipe(key);}}
        public void close(){wipe(shared);}
    }
}
