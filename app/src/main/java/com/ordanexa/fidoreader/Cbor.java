package com.ordanexa.fidoreader;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Bounded, definite-length CTAP CBOR. No tags, floating point or indefinite objects. */
final class Cbor {
    static Map<Object,Object> map(Object... pairs) {
        Map<Object,Object> m = new LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2) m.put(key(pairs[i]),pairs[i+1]);
        return m;
    }
    static Object key(Object k) { return k instanceof Number ? ((Number)k).longValue() : k; }
    static Object get(Object m,Object k) throws IOException {
        if(!(m instanceof Map)) throw new IOException("CBOR: map expected");
        return ((Map<?,?>)m).get(key(k));
    }
    static byte[] bytes(Object v) throws IOException {
        if(!(v instanceof byte[])) throw new IOException("CBOR: bytes expected");
        return (byte[])v;
    }
    static long number(Object v) throws IOException {
        if(!(v instanceof Long || v instanceof Integer)) throw new IOException("CBOR: integer expected");
        return ((Number)v).longValue();
    }
    static byte[] encode(Object o) throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream(); write(b,o,0); return b.toByteArray();
    }
    static void head(ByteArrayOutputStream b,int major,long n) throws IOException {
        if(n<0) throw new IOException("CBOR overflow");
        if(n<24) b.write((major<<5)|(int)n);
        else { int size=n<=255?1:n<=65535?2:n<=0xffffffffL?4:8;
            b.write((major<<5)|(size==1?24:size==2?25:size==4?26:27));
            for(int i=size-1;i>=0;i--) b.write((int)(n>>>(i*8))&255);
        }
    }
    static void write(ByteArrayOutputStream b,Object o,int depth) throws IOException {
        if(depth>16) throw new IOException("CBOR nesting");
        if(o==null) b.write(246);
        else if(o instanceof Boolean) b.write((Boolean)o?245:244);
        else if(o instanceof Number) {long n=((Number)o).longValue(); head(b,n<0?1:0,n<0?-1-n:n);}
        else if(o instanceof byte[]) {byte[] v=(byte[])o;head(b,2,v.length);b.write(v);}
        else if(o instanceof String) {byte[] v=((String)o).getBytes(StandardCharsets.UTF_8);head(b,3,v.length);b.write(v);}
        else if(o instanceof List) {List<?> v=(List<?>)o;head(b,4,v.size());for(Object x:v)write(b,x,depth+1);}
        else if(o instanceof Map) {
            List<Map.Entry<?,?>> entries=new ArrayList<>(((Map<?,?>)o).entrySet());
            // CTAP canonical key ordering: length first, then unsigned lexicographic.
            entries.sort((a,c)-> {try {byte[] x=encode(a.getKey()),y=encode(c.getKey());
                if(x.length!=y.length)return x.length-y.length;
                for(int i=0;i<x.length;i++)if(x[i]!=y[i])return (x[i]&255)-(y[i]&255);return 0;
            }catch(IOException e){throw new IllegalArgumentException(e);}});
            head(b,5,entries.size()); for(Map.Entry<?,?> e:entries){write(b,e.getKey(),depth+1);write(b,e.getValue(),depth+1);}
        } else throw new IOException("CBOR unsupported object");
    }
    static Object decode(byte[] b) throws IOException { return decodeAt(b,0); }
    static Object decodeAt(byte[] b,int offset) throws IOException {
        Reader r=new Reader(b,offset);Object o=r.read(0);if(r.p!=b.length)throw new IOException("CBOR trailing data");return o;
    }
    static final class Reader {
        final byte[] b; int p;
        Reader(byte[] b,int p)throws IOException{if(b.length>1048576||p<0||p>b.length)throw new IOException("CBOR size");this.b=b;this.p=p;}
        int next() throws IOException {if(p>=b.length)throw new IOException("CBOR truncated");return b[p++]&255;}
        Object read(int depth)throws IOException {
            if(depth>16)throw new IOException("CBOR nesting");
            int h=next(),major=h>>>5,ai=h&31;
            if(major==7){if(ai==20)return false;if(ai==21)return true;if(ai==22)return null;throw new IOException("CBOR simple value");}
            long n=ai;
            if(ai>=24){int count=ai==24?1:ai==25?2:ai==26?4:ai==27?8:0;if(count==0)throw new IOException("CBOR indefinite");n=0;for(int i=0;i<count;i++)n=(n<<8)|next();if(n<0)throw new IOException("CBOR overflow");}
            if(major==0)return n;if(major==1)return -1-n;
            if(n>1048576)throw new IOException("CBOR size");int len=(int)n;
            if(major==2||major==3){if(len>b.length-p)throw new IOException("CBOR truncated");byte[] v=Arrays.copyOfRange(b,p,p+len);p+=len;
                if(major==2)return v;
                try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(v)).toString();}
                catch(CharacterCodingException e){throw new IOException("CBOR UTF8");}}
            if(len>4096)throw new IOException("CBOR collection size");
            if(major==4){List<Object> l=new ArrayList<>();for(int i=0;i<len;i++)l.add(read(depth+1));return l;}
            if(major==5){Map<Object,Object> m=new LinkedHashMap<>();for(int i=0;i<len;i++){Object k=read(depth+1);if(!(k instanceof Long||k instanceof String)||m.containsKey(k))throw new IOException("CBOR map key");m.put(k,read(depth+1));}return m;}
            throw new IOException("CBOR type");
        }
    }
}
