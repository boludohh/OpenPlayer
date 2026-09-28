/*
	BASSOPUS 2.4 Java class
	Copyright (c) 2012-2024 Un4seen Developments Ltd.

	See the BASSOPUS.CHM file for more detailed documentation
*/

package com.un4seen.bass;

import java.nio.ByteBuffer;
import android.os.ParcelFileDescriptor;

@SuppressWarnings({"all"})
public class BASSOPUS
{
	// BASS_CHANNELINFO type
	public static final int BASS_CTYPE_STREAM_OPUS = 0x11200;

	// Additional attributes
	public static final int BASS_ATTRIB_OPUS_ORIGFREQ = 0x13000;
	public static final int BASS_ATTRIB_OPUS_GAIN = 0x13001;

	public static class BASS_OPUS_HEAD {
		public byte version;
		public byte channels;
		public short preskip;
		public int inputrate;
		public short gain;
		public byte mapping;
		public byte streams;
		public byte coupled;
		public byte[] chanmap;
	}

	public static final int BASS_STREAMPROC_OPUS_LOSS = 0x40000000;

	public static native int BASS_OPUS_StreamCreate(BASS_OPUS_HEAD head, int flags, BASS.STREAMPROC proc, Object user);
	public static native int BASS_OPUS_StreamCreate(BASS_OPUS_HEAD head, int flags, int proc, Object user);
	public static native int BASS_OPUS_StreamCreateFile(String file, long offset, long length, int flags);
	public static native int BASS_OPUS_StreamCreateFile(ByteBuffer file, long offset, long length, int flags);
	public static native int BASS_OPUS_StreamCreateFile(BASS.Asset file, long offset, long length, int flags);
	public static native int BASS_OPUS_StreamCreateFile(ParcelFileDescriptor file, long offset, long length, int flags);
	public static native int BASS_OPUS_StreamCreateURL(String url, int offset, int flags, BASS.DOWNLOADPROC proc, Object user);
	public static native int BASS_OPUS_StreamCreateFileUser(int system, int flags, BASS.BASS_FILEPROCS procs, Object user);
	public static native int BASS_OPUS_StreamPutData(int handle, ByteBuffer buffer, int length);
	
	static {
		System.loadLibrary("bassopus");
	}
}
