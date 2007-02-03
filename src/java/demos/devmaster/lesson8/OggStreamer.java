/**
 * Copyright (c) 2003 Sun Microsystems, Inc. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * -Redistribution of source code must retain the above copyright notice, 
 * this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduce the above copyright notice, 
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of Sun Microsystems, Inc. or the names of contributors may 
 * be used to endorse or promote products derived from this software without 
 * specific prior written permission.
 * 
 * This software is provided "AS IS," without a warranty of any kind.
 * ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN MICROSYSTEMS, INC. ("SUN") AND ITS
 * LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS A
 * RESULT OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 * IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT
 * OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR
 * PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY,
 * ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF SUN HAS
 * BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that this software is not designed or intended for use in the
 * design, construction, operation or maintenance of any nuclear facility.
 *
 */

package demos.devmaster.lesson8;

import java.io.File;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import net.java.games.joal.AL;
import net.java.games.joal.ALException;
import net.java.games.joal.ALFactory;
import net.java.games.joal.util.ALut;
/**
 *
 * This is a translation of the OggVorbis streamer OpenAL tutorial 
 * at http://www.devmaster.net/articles/openal-tutorials/lesson8.php
 *
 * It uses the Java Ogg library from http://www.j-ogg.de to do the Ogg
 * file decoding...
 *
 * @author Krishna K Gadepalli
 */
public class OggStreamer {
    
    static AL al = null;

    static {
        // Initialize OpenAL and clear the error bit.
        try {
            ALut.alutInit();
            al = ALFactory.getAL();
            al.alGetError();
        } catch (ALException e) {
            System.err.println("Error initializing OpenAL");
            e.printStackTrace();
        }
    }
    
    private OggDecoder oggDecoder;
    
    // The size of a chunk from the stream that we want to read for each update.
    private static int BUFFER_SIZE = 4096*8;
    
    // Buffers hold sound data. There are two of them (front/back)
    private int[] buffers = new int[2];
    
    // Sources are points emitting sound.
    private int[] source = new int[1];
    
    private int format;	// OpenAL data format
    private int rate;	// sample rate
    
    // Position, Velocity, Direction of the source sound.
    private float[] sourcePos = { 0.0f, 0.0f, 0.0f };
    private float[] sourceVel = { 0.0f, 0.0f, 0.0f };
    private float[] sourceDir = { 0.0f, 0.0f, 0.0f };
    
    private URL url;

    /** Creates a new instance of OggStreamer */
    public OggStreamer(URL url) {
	this.url = url;
    }
    
    /**
     * Open the Ogg/Vorbis stream and initialize OpenAL based
     * on the stream properties
     */
    public boolean open() {
	oggDecoder = new OggDecoder(url);

        if (!oggDecoder.initialize()) {
            System.err.println("Error initializing ogg stream...");
            return false;
        }
        
        if (oggDecoder.numChannels() == 1)
	    format = AL.AL_FORMAT_MONO16;
	else
	    format = AL.AL_FORMAT_STEREO16;
        
	rate = oggDecoder.sampleRate();

	System.err.println("format  = 0x" + Integer.toString(format, 16));

	// TODO: I am not if this is the right way to fix the endian
	// problems I am having... but this seems to fix it on Linux
	oggDecoder.setSwap(true);

        al.alGenBuffers(2, buffers, 0); check();
        al.alGenSources(1, source, 0); check();

	al.alSourcefv(source[0], AL.AL_POSITION , sourcePos, 0);
	al.alSourcefv(source[0], AL.AL_VELOCITY , sourceVel, 0);
	al.alSourcefv(source[0], AL.AL_DIRECTION, sourceDir, 0);
        
        al.alSourcef(source[0], AL.AL_ROLLOFF_FACTOR,  0.0f    );
        al.alSourcei(source[0], AL.AL_SOURCE_RELATIVE, AL.AL_TRUE);
        
	// System.err.println("buffers = " + Arrays.toString(buffers));
	// System.err.println("source  = " + Arrays.toString(source ));
	//
        return true;
    }
    
    /**
     * OpenAL cleanup
     */
    public void release() {
	al.alSourceStop(source[0]);
	empty();

	al.alDeleteSources(1, source, 0); check();
	al.alDeleteBuffers(2, buffers, 0); check();
    }

    /**
     * Play the Ogg stream
     */
    public boolean playback() {
	if (playing())
	    return true;
        
	if (!stream(buffers[0]))
	    return false;
        
	if(!stream(buffers[1]))
	    return false;
    
	al.alSourceQueueBuffers(source[0], 2, buffers, 0);
	al.alSourcePlay(source[0]);
    
        return true;
    }
    
    /**
     * Check if the source is playing
     */
    public boolean playing() {
	int[] state = new int[1];
    
	al.alGetSourcei(source[0], AL.AL_SOURCE_STATE, state, 0);
    
	return (state[0] == AL.AL_PLAYING);
    }
    
    /**
     * Update the stream if necessary
     */
    public boolean update() {
	int[] processed = new int[1];
	boolean active = true;

	al.alGetSourcei(source[0], AL.AL_BUFFERS_PROCESSED, processed, 0);

	while (processed[0] > 0)
	{
	    int[] buffer = new int[1];
	    
	    al.alSourceUnqueueBuffers(source[0], 1, buffer, 0); check();

	    active = stream(buffer[0]);

	    al.alSourceQueueBuffers(source[0], 1, buffer, 0); check();

	    processed[0]--;
	}

	return active;
    }
    
    /**
     * Reloads a buffer (reads in the next chunk)
     */
    public boolean stream(int buffer) {
	byte[] pcm = new byte[BUFFER_SIZE];
	int    size = 0;

	try {
	    if ((size = oggDecoder.read(pcm)) <= 0)
		return false;
	} catch (Exception e) {
	    e.printStackTrace();
	    return false;
	}

	ByteBuffer data = ByteBuffer.wrap(pcm, 0, size);
	al.alBufferData(buffer, format, data, size, rate);
	check();
	
	return true;
    }

    /**
     * Empties the queue
     */
    protected void empty() {
	int[] queued = new int[1];
	
	al.alGetSourcei(source[0], AL.AL_BUFFERS_QUEUED, queued, 0);
	
	while (queued[0] > 0)
	{
	    int[] buffer = new int[1];
	
	    al.alSourceUnqueueBuffers(source[0], 1, buffer, 0);
	    check();

	    queued[0]--;
	}

	oggDecoder = null;
    }

    /**
     * Check for OpenAL errors...
     */
    protected void check() {
        if (al.alGetError() != AL.AL_NO_ERROR)
            throw new ALException("OpenAL error raised...");
    }
    
    /**
     * The main loop to initialize and play the entire stream
     */
    public boolean playstream() {
        if (!open())
            return false;
        
        oggDecoder.dump();
        
        if (!playback())
            return false;
        
        while (update()) {
            if (playing()) continue;
            
            if (!playback())
                return false;
        }
        
        return true;
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (al == null)
            return;
        
	URL url;

        try {
	    if (args.length == 0) {
		url = OggStreamer.class.getClassLoader().getResource("demos/data/broken_glass.ogg");
		(new OggStreamer(url)).playstream();
	    }

            for (int i = 0; i < args.length; i++) {
                System.err.println("Playing Ogg stream : " + args[i]);
                
                url = ((new File(args[i])).exists()) ?
                    new URL("file:" + args[i]) : new URL(args[i]);
                
                if ((new OggStreamer(url)).playstream()) continue;
                
                System.err.println("ERROR!!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
}
