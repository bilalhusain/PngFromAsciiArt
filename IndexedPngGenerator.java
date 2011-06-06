import java.io.IOException;
import java.io.OutputStream;;
import java.io.FileOutputStream;

import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.CRC32;

/**
 * The class will generate an indexed png which contains
 *
 * MAGIC_HEADER (8 bytes)
 * IHDR chunk (4 + 4 + 13 + 4 bytes)
 * PLTE chunk (4 + 4 + 6 + 4 bytes)
 * IDAT chunk (4 + 4 + len + 4 bytes) // len depends on the output of deflate*
 * IEND chunk (4 + 4 + 0 + 4 bytes)
 *
 * assumptions:
 * - only two palettes black and white
 * - filter type 0 is assummed, so, deflate takes the data as (width + 1) * height
 */
public class IndexedPngGenerator
{
	private static final byte[] MAGIC_HEADER = {
		(byte) 0x89,
		(byte) 0x50,
		(byte) 0x4E,
	 	(byte) 0x47,
		(byte) 0x0D,
		(byte) 0x0A,
		(byte) 0x1A,
		(byte) 0x0A
	};

	private static final byte[] IHDR = {
		(byte) 0x49,
		(byte) 0x48,
		(byte) 0x44,
		(byte) 0x52
	};

	// saving computations (would you classify as flyweight?)
	private byte[] IHDRContents = {
		(byte) 0x00, 
		(byte) 0x00, 
		(byte) 0x00, 
		(byte) 0x00, // width (4 bytes)
		(byte) 0x00, 
		(byte) 0x00, 
		(byte) 0x00, 
		(byte) 0x00, // height(4 bytes)
		(byte) 0x08, // depth
		(byte) 0x03, // color
		(byte) 0x00, // compression
		(byte) 0x00, // filter
		(byte) 0x00  // interlace
	};

	private static final byte[] PLTE = {
		(byte) 0x50,
		(byte) 0x4C,
		(byte) 0x54,
		(byte) 0x45
	};

	private byte[] PLTEContents = {
		(byte) 0x00,
		(byte) 0x00,
		(byte) 0x00, // black
		(byte) 0xff,
		(byte) 0xff,
		(byte) 0xff  // white
	};

	private static final byte[] IDAT = {
		(byte) 0x49,
		(byte) 0x44,
		(byte) 0x41,
		(byte) 0x54
	};

	private static final byte[] IEND = {
		(byte) 0x49,
		(byte) 0x45,
		(byte) 0x4e,
		(byte) 0x44
	};

	public long width = 0L;
	public long height = 0L;

	public boolean[] imageData;

	public IndexedPngGenerator(long width, long height) {
		setWidth(width);
		setHeight(height);
		this.imageData = new boolean[(int) ((width + 1) * height)];

		//printBytes(IHDRContents, 0, 13);
	}

	public void setWidth(long w)
	{
		this.width = w;

		// set the IHDR contents
		IHDRContents[3] = (byte) (w & 0xff);
		IHDRContents[2] = (byte) ((w >> 8) & 0xff);
		IHDRContents[1] = (byte) ((w >> 16) & 0xff);
		IHDRContents[0] = (byte) ((w >> 24) & 0xff);
	}

	public void setHeight(long h)
	{
		this.height = h;

		// set the IHDR contents
		IHDRContents[7] = (byte) (h & 0xff);
		IHDRContents[6] = (byte) ((h >> 8) & 0xff);
		IHDRContents[5] = (byte) ((h >> 16) & 0xff);
		IHDRContents[4] = (byte) ((h >> 24) & 0xff);
	}

	public void setImageData(String representation) {
		// currently dummy
		setWidth(4);
		setHeight(2);
		this.imageData = new boolean[]{false, true, true, false, false, false, false, false, true, true};
	}

	public void printBytes(byte[] b, int offset, int len) {
		for (int i = offset; i < offset + len; i++) {
			System.out.print(Integer.toHexString(b[i] & 0x000000ff).toString() + " ");
		}
		System.out.println();
	}

	public void generate() {
		// run deflate on the image data
		byte[] imageDataAsBytes = new byte[this.imageData.length];
		for (int i = 0;  i < this.imageData.length; i++) {
			imageDataAsBytes[i] = imageData[i] ? (byte) 0x01 : (byte) 0x00;
		}
		byte[] deflatedBytes = getDeflatedBytes(imageDataAsBytes);

		// not we can get the size of the generated png
		// 8 + (4 + 4 + 13 + 4) + (4 + 4 + 6 + 4) + (4 + 4 + x + 4) + (4 + 4 + 0 + 4)
		byte[] pngData = new byte[deflatedBytes.length + 75];
		copyBytes(pngData, 0, MAGIC_HEADER);

		int position = 0;

		copyLength(pngData, 8, 13);
		copyBytes(pngData, 8 + 4, IHDR);
		copyBytes(pngData, 8 + 4 + 4, IHDRContents);
		putCRC32(pngData, 8 + 4, 13 + 4, 8 + 4 + 4 + 13);

		copyLength(pngData, 8 + 4 + 4 + 13 + 4, 6);
		copyBytes(pngData, 8 + 4 + 4 + 13 + 4 + 4, PLTE);
		copyBytes(pngData, 8 + 4 + 4 + 13 + 4 + 4 + 4, PLTEContents);
		putCRC32(pngData, 8 + 4 + 4 + 13 + 4 + 4, 6 + 4, 8 + 4 + 4 + 13 + 4 + 4 + 4 + 6);

		position = 8 + 4 + 4 + 13 + 4 + 4 + 4 + 6 + 4;
		copyLength(pngData, position, deflatedBytes.length);
		copyBytes(pngData, position + 4, IDAT);
		copyBytes(pngData, position + 8, deflatedBytes);
		putCRC32(pngData, position + 4, deflatedBytes.length + 4, position + 8 + deflatedBytes.length);

		position = position + 8 + deflatedBytes.length + 4;
		copyLength(pngData, position, 0);
		copyBytes(pngData, position + 4, IEND);
		putCRC32(pngData, position + 4, 4, position + 8);

		printBytes(pngData, 0, pngData.length);

		// save as png file
		try {
			OutputStream os = new FileOutputStream("./static/img.png");
			os.write(pngData);
			os.flush();
			os.close();
		} catch (IOException ioe) {
			System.err.println("could not save the file");
		}
	}

	private static void copyLength(byte[] buf, int offset, int length) {
		buf[offset + 3] = (byte) (length & 0xff);
		buf[offset + 2] = (byte) ((length >> 8) & 0xff);
		buf[offset + 1] = (byte) ((length >> 16) & 0xff);
		buf[offset + 0] = (byte) ((length >> 24) & 0xff);
	}

	private static void copyBytes(byte[] buf, int offset, byte[] data) {
		for (int i = 0; i < data.length; i++) {
			buf[offset + i] = data[i];
		}
	}

	private static byte[] getDeflatedBytes(byte[] imageDataAsBytes) {
		Deflater compresser = new Deflater();
		compresser.setInput(imageDataAsBytes);
		compresser.finish();

		byte[] buf = new byte[1024 * 8];
		int len = compresser.deflate(buf);

		byte[] output = new byte[len];
		for (int i = 0; i < len; i++) {
			output[i] = buf[i];
		}
		return output;
	}

	public static void main(String args[]) {
		//System.out.println(MAGIC_HEADER[0]);
		IndexedPngGenerator png = new IndexedPngGenerator(699, 870);
		png.setImageData("dummy");
		png.generate();
	}

	/**
	 * reads the data [offset, offset + length) for computing crc32
	 * and fills at the specified position in the same buffer
	 */
	public void putCRC32(byte[] buf, int offset, int length, int fillAt) {
		// call the java code to compute the crc32
		// TODO optimize, use one instance of CRC32 and reset
		// to avoid generation of tables each time this method is called
		CRC32 crc32 = new CRC32();
		crc32.update(buf, offset, length);

		long val = crc32.getValue();
		buf[fillAt + 3] = (byte) (val & 0xff);
		buf[fillAt + 2] = (byte) ((val >> 8) & 0xff);
		buf[fillAt + 1] = (byte) ((val >> 16) & 0xff);
		buf[fillAt + 0] = (byte) ((val >> 24) & 0xff);
	}
}

