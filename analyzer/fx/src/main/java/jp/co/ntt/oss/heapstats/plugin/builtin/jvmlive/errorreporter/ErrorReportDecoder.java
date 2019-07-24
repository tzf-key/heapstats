/*
 * Copyright (C) 2014-2015 Yasumasa Suenaga
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package jp.co.ntt.oss.heapstats.plugin.builtin.jvmlive.errorreporter;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;

/**
 * Error report decoder.
 * This class can treat data which is generated by -XX:+TransmitErrorReport and -XX:ErrorReportServer=&lt;address&gt;:&lt;port&gt; .
 * 
 * @author Yasumasa Suenaga
 */
public class ErrorReportDecoder extends Task<Void>{
    
    /** Buffer size for receive buffer. */
    public static final int BUFFER_SIZE = 1024;
    
    /**
     * Created time of this instance.
     * This time equals JVM crashed.
     */
    private LocalDateTime crashedTime;
    
    /** IP address for crash JVM. */
    private InetAddress localIP;
    
    /** User name for crash Java process. */
    private String user;
    
    private boolean crash;
    
    private boolean debug;
    
    private int procCount;
    
    private int buildVer;
    
    private int[] resolveTiming;
    
    private int resolvePlatform;
    
    /** JDK version for crash JVM. */
    private String jdkVersion;
    
    /** VM version for crash JVM. */
    private String vmVersion;
    
    /** Length of this error report. */
    private int reportLength;
    
    /**
     * File instance for this hs_err report.
     * This file is temporary. So we add "deleteOnExit" to this field.
     */
    private File hsErrFile;
    
    private final ObservableList<ErrorReportDecoder> crashList;
    
    /** Async channel to crash JVM. */
    private final AsynchronousSocketChannel ch;
    
    /** InetSocketAddress for crash JVM. */
    private final InetSocketAddress sockAddr;

    /**
     * Constructor of ErrorReportDecorder.
     * 
     * @param crashList List of crash jvms.
     * @param ch AsynchronousSocketChannel to crash jvm.
     * 
     * @throws IOException If an I/O error occurs
     */
    public ErrorReportDecoder(ObservableList<ErrorReportDecoder> crashList, AsynchronousSocketChannel ch) throws IOException{
        crashedTime = LocalDateTime.now();
        
        localIP = null;
        user = null;
        crash = false;
        debug = false;
        procCount = -1;
        buildVer = -1;
        resolveTiming = new int[2];
        resolvePlatform = -1;
        jdkVersion = null;
        vmVersion = null;
        reportLength = -1;
        hsErrFile = null;
        
        this.crashList = crashList;
        this.ch = ch;
        this.sockAddr = (InetSocketAddress)ch.getRemoteAddress();
    }

    /**
     * Parse header information in error report data.
     * 
     * @param buf Received data
     */
    private void parseHeader(ByteBuffer buf){
        
        /* Check magic number */
        if(buf.getInt() != 0xcafebabe){
            throw new IllegalArgumentException("Magic number of ErrorReport is incorrected: from " + sockAddr.toString());
        }
        
        /* Get unknown data 1 (do nothing) */
        buf.getInt();
        
        /* IP address which error is occurred */
        byte[] rawIP = new byte[4];
        buf.get(rawIP);
        try {
            localIP = InetAddress.getByAddress(rawIP);
        } catch (UnknownHostException ex) {
            Logger.getLogger(ErrorReportServer.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        /* User name */
        byte[] rawUserName = new byte[32];
        buf.get(rawUserName);
        user = new String(rawUserName, StandardCharsets.UTF_8);
        
        /* Flags */
        int flags = buf.getInt();
        procCount = flags & 0x07;
        buildVer = (flags >> 0x05) & 0x3;
        if((flags & 0x80) != 0) crash = true;
        if((flags & 0x03) != 0) debug = true;
        
        /* Resolve timing */
        resolveTiming[0] = buf.getInt();
        resolveTiming[1] = buf.getInt();
        
        /* Resolve platform */
        resolvePlatform = buf.getInt();
        
        /* Get unknown data 2 (do nothing) */
        buf.getInt();

        /* Get unknown data 3 (do nothing) */
        buf.getInt();

        /* Get unknown data 4 (do nothing) */
        buf.getInt();

        /* Get unknown data 5 (do nothing) */
        buf.getInt();

        /* Get unknown data 6 (do nothing) */
        buf.getInt();

        /* Get unknown data 7 (do nothing) */
        buf.getInt();

        /* Get unknown data 8 (do nothing) */
        buf.getInt();

        /* Get unknown data 9 (do nothing) */
        buf.getInt();

        /* Get unknown data 10 (do nothing) */
        buf.getInt();

        /* JDK version */
        byte[] rawJDKVersion = new byte[buf.getInt()];
        buf.get(rawJDKVersion);
        jdkVersion = new String(rawJDKVersion, StandardCharsets.UTF_8);
        
        /* VM version */
        byte[] rawVMVersion = new byte[buf.getInt()];
        buf.get(rawVMVersion);
        vmVersion = new String(rawVMVersion, StandardCharsets.UTF_8);
        
        /* Length of hs_err report */
        reportLength = buf.getInt();
    }

    public InetAddress getLocalIP() {
        return localIP;
    }

    public void setLocalIP(InetAddress localIP) {
        this.localIP = localIP;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }
    
    public void setCrash(){
        this.crash = true;
    }
    
    public boolean isCrash(){
        return this.crash;
    }
    
    public void setDebug(){
        this.debug = true;
    }
    
    public boolean isDebug(){
        return this.debug;
    }

    public int getProcCount() {
        return procCount;
    }

    public void setProcCount(int procCount) {
        this.procCount = procCount;
    }

    public int getBuildVer() {
        return buildVer;
    }

    public void setBuildVer(int buildVer) {
        this.buildVer = buildVer;
    }

    public int[] getResolveTiming() {
        return resolveTiming;
    }

    public void setResolveTiming(int[] resolveTiming) {
        this.resolveTiming = resolveTiming;
    }

    public int getResolvePlatform() {
        return resolvePlatform;
    }

    public void setResolvePlatform(int resolvePlatform) {
        this.resolvePlatform = resolvePlatform;
    }

    public String getJdkVersion() {
        return jdkVersion;
    }

    public void setJdkVersion(String jdkVersion) {
        this.jdkVersion = jdkVersion;
    }

    public String getVmVersion() {
        return vmVersion;
    }

    public void setVmVersion(String vmVersion) {
        this.vmVersion = vmVersion;
    }

    public int getReportLength() {
        return reportLength;
    }

    public void setReportLength(int reportLength) {
        this.reportLength = reportLength;
    }

    public File getHsErrFile() {
        return hsErrFile;
    }

    public void setHsErrFile(File hsErrFile) {
        this.hsErrFile = hsErrFile;
    }

    public LocalDateTime getCrashedTime() {
        return crashedTime;
    }

    public void setCrashedTime(LocalDateTime crashedTime) {
        this.crashedTime = crashedTime;
    }

    @Override
    public String toString() {
        return localIP.toString();
    }

    @Override
    protected Void call() throws Exception {
        
        try{
            ByteBuffer buf = ByteBuffer.allocateDirect(BUFFER_SIZE);
            Future<Integer> bytes = ch.read(buf);
                    
            if(bytes.get() <= 0){
                return null;
            }
                
            buf.flip();
            parseHeader(buf);
            
            if(reportLength > 0){
                int remaining = reportLength;
                File hs_err_log = File.createTempFile("hs_err", sockAddr.getAddress().getHostAddress());
                hs_err_log.deleteOnExit();
                        
                try(SeekableByteChannel hs_err = Files.newByteChannel(hs_err_log.toPath(),
                                                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)){

                    while(remaining > 0){
                        int sizeShouldWrite = buf.limit() - buf.position();
                        if(sizeShouldWrite > remaining){
                            sizeShouldWrite = remaining;
                            buf.limit(sizeShouldWrite);
                        }
                        remaining -= sizeShouldWrite;
                        
                        hs_err.write(buf);
                        buf.flip();
                        ch.read(buf).get();
                        buf.flip();
                    }
                            
                }
                        
                hsErrFile = hs_err_log;
                Platform.runLater(() -> crashList.add(this));
            }
            
        }
        finally{
            ch.close();
        }

        return null;
    }
    
}